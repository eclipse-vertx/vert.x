/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Deque;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected by using the connection as a lock. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * It's important we don't have different locks for connection and request/response to avoid deadlock conditions
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerRequestImpl implements HttpServerRequest {

  private static final Logger log = LoggerFactory.getLogger(HttpServerRequestImpl.class);

  private static final int MAX_QUEUE_SIZE = 8;
  private Deque<HttpContent> queue = new ArrayDeque<>(8);

  private final Vertx vertx;
  private final Http1xServerConnection conn;
  private final HttpRequest request;
  private final HttpServerResponse response;

  private io.vertx.core.http.HttpVersion version;
  private io.vertx.core.http.HttpMethod method;
  private String rawMethod;
  private String uri;
  private String path;
  private String query;

  private Handler<Buffer> dataHandler;
  private Handler<Throwable> exceptionHandler;

  //Cache this for performance
  private MultiMap params;
  private MultiMap headers;
  private String absoluteURI;

  private NetSocket netSocket;
  private Handler<HttpServerFileUpload> uploadHandler;
  private Handler<Void> endHandler;
  private MultiMap attributes;
  private HttpPostRequestDecoder decoder;
  private boolean ended;

  private Object requestMetric;
  private long bytesRead;

  private boolean paused;

  /* when set to false the request queues all incoming messages
  (when the connection should not be paused, but we need to process data later) */
  private boolean started = true;
  private boolean channelPaused;
  private boolean sentCheck;

  HttpServerRequestImpl(Vertx vertx,
                        Http1xServerConnection conn,
                        HttpRequest request,
                        HttpServerResponse response) {
    this.vertx = vertx;
    this.conn = conn;
    this.request = request;
    this.response = response;
    if (METRICS_ENABLED && conn.metrics() != null) {
      // FIXME probably we call this too early
      requestMetric = conn.metrics().requestBegin(conn.metric(), this);
    }
  }

  public Object getRequestMetric() {
    return requestMetric;
  }

  @Override
  public io.vertx.core.http.HttpVersion version() {
    if (version == null) {
      io.netty.handler.codec.http.HttpVersion nettyVersion = request.getProtocolVersion();
      if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
        version = HttpVersion.HTTP_1_0;
      } else if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_1) {
        version = HttpVersion.HTTP_1_1;
      } else {
        sendNotImplementedAndClose();
        throw new IllegalStateException("Unsupported HTTP version: " + nettyVersion);
      }
    }
    return version;
  }

  @Override
  public io.vertx.core.http.HttpMethod method() {
    if (method == null) {
      String sMethod = request.method().toString();
      try {
        method = io.vertx.core.http.HttpMethod.valueOf(sMethod);
      } catch (IllegalArgumentException e) {
        method = io.vertx.core.http.HttpMethod.OTHER;
      }
    }
    return method;
  }

  @Override
  public String rawMethod() {
    if (rawMethod == null) {
      rawMethod = request.method().toString();
    }
    return rawMethod;
  }

  @Override
  public String uri() {
    if (uri == null) {
      uri = request.uri();
    }
    return uri;
  }

  @Override
  public String path() {
    if (path == null) {
      path = HttpUtils.parsePath(uri());
    }
    return path;
  }

  @Override
  public String query() {
    if (query == null) {
      query = HttpUtils.parseQuery(uri());
    }
    return query;
  }

  @Override
  public @Nullable String host() {
    return getHeader(HttpHeaderNames.HOST);
  }

  @Override
  public HttpServerResponse response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    if (headers == null) {
      headers = new HeadersAdaptor(request.headers());
    }
    return headers;
  }

  @Override
  public String getHeader(String headerName) {
    return headers().get(headerName);
  }

  @Override
  public String getHeader(CharSequence headerName) {
    return headers().get(headerName);
  }

  @Override
  public MultiMap params() {
    if (params == null) {
      params = HttpUtils.params(uri());
    }
    return params;
  }

  @Override
  public String getParam(String paramName) {
    return params().get(paramName);
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      dataHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      this.exceptionHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerRequest pause() {
    synchronized (conn) {
      this.paused = true;
      conn.pause();
      return this;
    }
  }

  @Override
  public HttpServerRequest resume() {
    synchronized (conn) {
      if (paused) {
        conn.resume();
        this.paused = false;
        checkNextTick();
      }
      return this;
    }
  }

  boolean started() {
    synchronized (conn) {
      return started;
    }
  }

  void started(boolean started) {
    synchronized (conn) {
      if (this.started != started) {
        this.started = started;
        if (started) {
          checkNextTick();
        }
      }
    }
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      endHandler = handler;
      return this;
    }
  }

  @Override
  public String scheme() {
    return isSSL() ? "https" : "http";
  }

  @Override
  public boolean isSSL() {
    return conn.isSSL();
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public String absoluteURI() {
    if (absoluteURI == null) {
      try {
        absoluteURI = HttpUtils.absoluteURI(conn.getServerOrigin(), this);
      } catch (URISyntaxException e) {
        log.error("Failed to create abs uri", e);
      }
    }
    return absoluteURI;
  }

  @Override
  public SSLSession sslSession() {
    return conn.sslSession();
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.peerCertificateChain();
  }

  @Override
  public NetSocket netSocket() {
    if (netSocket == null) {
      netSocket = conn.createNetSocket();
    }
    return netSocket;
  }

  @Override
  public HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      uploadHandler = handler;
      return this;
    }
  }

  @Override
  public MultiMap formAttributes() {
    return attributes();
  }

  @Override
  public String getFormAttribute(String attributeName) {
    return formAttributes().get(attributeName);
  }

  @Override
  public ServerWebSocket upgrade() {
    return conn.upgrade(this, request);
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    synchronized (conn) {
      checkEnded();
      if (expect) {
        if (decoder == null) {
          String contentType = request.headers().get(HttpHeaders.Names.CONTENT_TYPE);
          if (contentType != null) {
            HttpMethod method = request.getMethod();
            String lowerCaseContentType = contentType.toLowerCase();
            boolean isURLEncoded = lowerCaseContentType.startsWith(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
            if ((lowerCaseContentType.startsWith(HttpHeaders.Values.MULTIPART_FORM_DATA) || isURLEncoded) &&
              (method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH)
                || method.equals(HttpMethod.DELETE))) {
              decoder = new HttpPostRequestDecoder(new NettyFileUploadDataFactory(conn.vertx(), this, () -> uploadHandler), request);
            }
          }
        }
      } else {
        decoder = null;
      }
      return this;
    }
  }

  @Override
  public boolean isExpectMultipart() {
    synchronized (conn) {
      return decoder != null;
    }
  }

  @Override
  public SocketAddress localAddress() {
    return conn.localAddress();
  }

  @Override
  public boolean isEnded() {
    synchronized (conn) {
      return ended;
    }
  }

  @Override
  public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
    return this;
  }

  @Override
  public HttpConnection connection() {
    return conn;
  }

  void handleData(HttpContent content) {
    synchronized (conn) {

      if (this.queue.size() > 0 || this.paused || !this.started) {
        enqueue(content);
      }

      if (!this.paused && this.started) {
        if (this.queue.isEmpty()) {
          handleChunk(content);
        } else {
          handleChunk(this.queue.pollFirst());
        }
      }
      checkNextTick();
    }
  }

  private void enqueue(HttpContent content) {
    this.queue.addLast(content);
    if (this.queue.size() >= MAX_QUEUE_SIZE) {
      conn.doPause();
      channelPaused = true;
    }
  }


  private void handleChunk(HttpContent content) {
    ByteBuf chunk = content.content();
    if (chunk.isReadable()) {
      Buffer buff = Buffer.buffer(chunk);
      if (decoder != null) {
        try {
          decoder.offer(content);
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
          handleException(e);
        }
      }

      if (dataHandler != null) {
        if (METRICS_ENABLED) {
          bytesRead += buff.length();
        }
        dataHandler.handle(buff);
      }

      //TODO chunk trailers?
      if (content instanceof LastHttpContent) {
        this.handleEnd();
      }
    } else if (content instanceof LastHttpContent) {
      this.handleEnd();
    }

    checkNextTick();
  }

  private void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!queue.isEmpty() && !sentCheck && !paused && started) {
      sentCheck = true;
      vertx.runOnContext(v -> {
        synchronized (conn) {
          sentCheck = false;
          HttpContent content = queue.pollFirst();
          if (content != null) {
            handleChunk(content);
          }
          if (channelPaused && queue.isEmpty()) {
            //Resume the actual channel
            conn.doResume();
            channelPaused = false;
          }
        }
      });
    }
  }

  void handleEnd() {
    synchronized (conn) {
      ended = true;
      if (decoder != null) {
        try {
          decoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
          while (decoder.hasNext()) {
            InterfaceHttpData data = decoder.next();
            if (data instanceof Attribute) {
              Attribute attr = (Attribute) data;
              try {
                attributes().add(attr.getName(), attr.getValue());
              } catch (Exception e) {
                // Will never happen, anyway handle it somehow just in case
                handleException(e);
              }
            }
          }
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
          handleException(e);
        } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
          // ignore this as it is expected
        } finally {
          decoder.destroy();
        }
      }
      // If there have been uploads then we let the last one call the end handler once any fileuploads are complete
      if (endHandler != null) {
        endHandler.handle(null);
      }

      if (METRICS_ENABLED) {
        conn.reportBytesRead(bytesRead);
      }
    }
  }

  void handleException(Throwable t) {
    synchronized (conn) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(t);
      }
    }
  }

  private void sendNotImplementedAndClose() {
    response().setStatusCode(501).end();
    response().close();
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Request has already been read");
    }
  }


  private MultiMap attributes() {
    // Create it lazily
    if (attributes == null) {
      attributes = new CaseInsensitiveHeaders();
    }
    return attributes;
  }


  private static String urlDecode(String str) {
    return QueryStringDecoder.decodeComponent(str, CharsetUtil.UTF_8);
  }

}
