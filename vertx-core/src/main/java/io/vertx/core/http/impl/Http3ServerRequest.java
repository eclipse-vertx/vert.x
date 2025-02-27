/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3ServerRequest extends HttpServerRequestInternal implements Http3ServerStreamHandler,
  io.vertx.core.spi.observability.HttpRequest {

  private static final Logger log = LoggerFactory.getLogger(Http3ServerRequest.class);

  protected final ContextInternal context;
  protected final Http3ServerStream stream;
  protected final Http3ServerResponse response;
  private final String serverOrigin;
  private final MultiMap headersMap;

  // Accessed on context thread
  private Charset paramsCharset = StandardCharsets.UTF_8;
  private MultiMap params;
  private boolean semicolonIsNormalCharInParams;
  private String absoluteURI;
  private MultiMap attributes;
  private HttpEventHandler eventHandler;
  private boolean ended;
  private Handler<HttpServerFileUpload> uploadHandler;
  private boolean expectMultipart;
  private HttpPostRequestDecoder postRequestDecoder;
  private Handler<HttpFrame> customFrameHandler;
  private Handler<StreamPriorityBase> streamPriorityHandler;

  Http3ServerRequest(Http3ServerStream stream,
                     String serverOrigin,
                     VertxHttpHeaders headers) {
    this.context = stream.context;
    this.stream = stream;
    this.response = new Http3ServerResponse(stream.conn, stream, false);
    this.serverOrigin = serverOrigin;
    this.headersMap = headers;
  }

  private HttpEventHandler eventHandler(boolean create) {
    if (eventHandler == null && create) {
      eventHandler = new HttpEventHandler(context);
    }
    return eventHandler;
  }

  public void dispatch(Handler<HttpServerRequest> handler) {
    context.emit(this, handler);
  }

  @Override
  public void handleException(Throwable cause) {
    boolean notify;
    synchronized (stream.conn) {
      notify = !ended;
    }
    if (notify) {
      notifyException(cause);
    }
    response.handleException(cause);
  }

  private void notifyException(Throwable failure) {
    InterfaceHttpData upload = null;
    HttpEventHandler handler;
    synchronized (stream.conn) {
      if (postRequestDecoder != null) {
        upload = postRequestDecoder.currentPartialHttpData();
      }
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleException(failure);
    }
    if (upload instanceof NettyFileUpload) {
      ((NettyFileUpload) upload).handleException(failure);
    }
  }

  @Override
  public void handleClose() {
    response.handleClose();
  }

  @Override
  public void handleCustomFrame(HttpFrame frame) {
    if (customFrameHandler != null) {
      customFrameHandler.handle(frame);
    }
  }

  public void handleData(Buffer data) {
    if (postRequestDecoder != null) {
      try {
        postRequestDecoder.offer(new DefaultHttpContent(((BufferInternal) data).getByteBuf()));
      } catch (HttpPostRequestDecoder.ErrorDataDecoderException |
               HttpPostRequestDecoder.TooLongFormFieldException |
               HttpPostRequestDecoder.TooManyFormFieldsException e) {
        postRequestDecoder.destroy();
        postRequestDecoder = null;
        handleException(e);
      }
    }
    HttpEventHandler handler = eventHandler;
    if (handler != null) {
      handler.handleChunk(data);
    }
  }

  public void handleEnd(MultiMap trailers) {
    HttpEventHandler handler;
    synchronized (stream.conn) {
      ended = true;
      if (postRequestDecoder != null) {
        try {
          postRequestDecoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
          while (postRequestDecoder.hasNext()) {
            InterfaceHttpData data = postRequestDecoder.next();
            if (data instanceof Attribute) {
              Attribute attr = (Attribute) data;
              try {
                formAttributes().add(attr.getName(), attr.getValue());
              } catch (Exception e) {
                // Will never happen, anyway handle it somehow just in case
                handleException(e);
              } finally {
                attr.release();
              }
            }
          }
        } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
          // ignore this as it is expected
        } catch (Exception e) {
          handleException(e);
        } finally {
          postRequestDecoder.destroy();
          postRequestDecoder = null;
        }
      }
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleEnd();
    }
  }

  @Override
  public void handleReset(long errorCode) {
    boolean notify;
    synchronized (stream.conn) {
      notify = !ended;
      ended = true;
    }
    if (notify) {
      notifyException(new StreamResetException(errorCode));
    }
    response.handleReset(errorCode);
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Request has already been read");
    }
  }

  @Override
  public HttpMethod method() {
    return stream.method;
  }

  @Override
  public int id() {
    return stream.id();
  }

  @Override
  public Object metric() {
    return stream.metric();
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    synchronized (stream.conn) {
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.exceptionHandler(handler);
      }
    }
    return this;
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    synchronized (stream.conn) {
      if (handler != null) {
        checkEnded();
      }
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.chunkHandler(handler);
      }
    }
    return this;
  }

  @Override
  public HttpServerRequest pause() {
    synchronized (stream.conn) {
      checkEnded();
      stream.doPause();
    }
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public HttpServerRequest fetch(long amount) {
    synchronized (stream.conn) {
      checkEnded();
      stream.doFetch(amount);
    }
    return this;
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
    synchronized (stream.conn) {
      if (handler != null) {
        checkEnded();
      }
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.endHandler(handler);
      }
    }
    return this;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_3;
  }

  @Override
  public String uri() {
    return stream.uri;
  }

  @Override
  public String path() {
    synchronized (stream.conn) {
      return stream.uri != null ? HttpUtils.parsePath(stream.uri) : null;
    }
  }

  @Override
  public String query() {
    synchronized (stream.conn) {
      if (stream.uri == null) {
        return null;
      } else {
        return HttpUtils.parseQuery(stream.uri);
      }
    }
  }

  @Override
  public String scheme() {
    return stream.scheme;
  }

  @Override
  public @Nullable HostAndPort authority() {
    return stream.authority;
  }

  @Override
  public long bytesRead() {
    return stream.bytesRead();
  }

  @Override
  public Http3ServerResponse response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    return headersMap;
  }

  @Override
  public HttpServerRequest setParamsCharset(String charset) {
    Objects.requireNonNull(charset, "Charset must not be null");
    Charset current = paramsCharset;
    paramsCharset = Charset.forName(charset);
    if (!paramsCharset.equals(current)) {
      params = null;
    }
    return this;
  }

  @Override
  public String getParamsCharset() {
    return paramsCharset.name();
  }

  @Override
  public MultiMap params(boolean semicolonIsNormalChar) {
    synchronized (stream.conn) {
      if (params == null || semicolonIsNormalChar != semicolonIsNormalCharInParams) {
        params = HttpUtils.params(uri(), paramsCharset, semicolonIsNormalChar);
        semicolonIsNormalCharInParams = semicolonIsNormalChar;
      }
      return params;
    }
  }

  @Override
  public SocketAddress remoteAddress() {
    return super.remoteAddress();
  }

  @Override
  public String absoluteURI() {
    if (stream.method == HttpMethod.CONNECT) {
      return null;
    }
    synchronized (stream.conn) {
      if (absoluteURI == null) {
        absoluteURI = HttpUtils.absoluteURI(serverOrigin, this);
      }
      return absoluteURI;
    }
  }

  @Override
  public Future<NetSocket> toNetSocket() {
    return response.netSocket();
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    synchronized (stream.conn) {
      checkEnded();
      expectMultipart = expect;
      if (expect) {
        if (postRequestDecoder == null) {
          String contentType = headersMap.get(HttpHeaderNames.CONTENT_TYPE);
          if (contentType == null) {
            throw new IllegalStateException("Request must have a content-type header to decode a multipart request");
          }
          if (!HttpUtils.isValidMultipartContentType(contentType)) {
            throw new IllegalStateException("Request must have a valid content-type header to decode a multipart " +
              "request");
          }
          if (!HttpUtils.isValidMultipartMethod(stream.method.toNetty())) {
            throw new IllegalStateException("Request method must be one of POST, PUT, PATCH or DELETE to decode a " +
              "multipart request");
          }
          HttpRequest req = new DefaultHttpRequest(
            io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
            stream.method.toNetty(),
            stream.uri);
          req.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
          NettyFileUploadDataFactory factory = new NettyFileUploadDataFactory(context, this, () -> uploadHandler);
          HttpServerOptions options = stream.conn.options;
          factory.setMaxLimit(options.getMaxFormAttributeSize());
          int maxFields = options.getMaxFormFields();
          int maxBufferedBytes = options.getMaxFormBufferedBytes();
          postRequestDecoder = new HttpPostRequestDecoder(factory, req, HttpConstants.DEFAULT_CHARSET, maxFields,
            maxBufferedBytes);
        }
      } else {
        postRequestDecoder = null;
      }
    }
    return this;
  }

  @Override
  public boolean isExpectMultipart() {
    synchronized (stream.conn) {
      return expectMultipart;
    }
  }

  @Override
  public HttpServerRequest uploadHandler(@Nullable Handler<HttpServerFileUpload> handler) {
    synchronized (stream.conn) {
      if (handler != null) {
        checkEnded();
      }
      uploadHandler = handler;
      return this;
    }
  }

  @Override
  public MultiMap formAttributes() {
    synchronized (stream.conn) {
      // Create it lazily
      if (attributes == null) {
        attributes = MultiMap.caseInsensitiveMultiMap();
      }
      return attributes;
    }
  }

  @Override
  public String getFormAttribute(String attributeName) {
    return formAttributes().get(attributeName);
  }

  @Override
  public int streamId() {
    return stream.id();
  }

  @Override
  public Future<ServerWebSocket> toWebSocket() {
    return context.failedFuture("HTTP/3 request cannot be upgraded to a WebSocket");
  }

  @Override
  public boolean isEnded() {
    synchronized (stream.conn) {
      return ended;
    }
  }

  @Override
  public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
    synchronized (stream.conn) {
      customFrameHandler = handler;
    }
    return this;
  }

  @Override
  public HttpConnection connection() {
    return stream.conn;
  }

  @Override
  public synchronized Future<Buffer> body() {
    checkEnded();
    return eventHandler(true).body();
  }

  @Override
  public synchronized Future<Void> end() {
    checkEnded();
    return eventHandler(true).end();
  }

  public StreamPriorityBase streamPriority() {
    return stream.priority();
  }

  @Override
  public HttpServerRequest streamPriorityHandler(Handler<StreamPriorityBase> handler) {
    synchronized (stream.conn) {
      streamPriorityHandler = handler;
    }
    return this;
  }

  @Override
  public DecoderResult decoderResult() {
    return DecoderResult.SUCCESS;
  }

  @Override
  public void handlePriorityChange(StreamPriorityBase streamPriority) {
    Handler<StreamPriorityBase> handler;
    synchronized (stream.conn) {
      handler = streamPriorityHandler;
    }
    if (handler != null) {
      handler.handle(streamPriority);
    }
  }

  @Override
  public Set<Cookie> cookies() {
    return (Set) response.cookies();
  }

  @Override
  public Set<Cookie> cookies(String name) {
    return (Set) response.cookies().getAll(name);
  }

  @Override
  public Cookie getCookie(String name) {
    return response.cookies()
      .get(name);
  }

  @Override
  public Cookie getCookie(String name, String domain, String path) {
    return response.cookies()
      .get(name, domain, path);
  }

  @Override
  public HttpServerRequest routed(String route) {
    stream.routed(route);
    return this;
  }
}
