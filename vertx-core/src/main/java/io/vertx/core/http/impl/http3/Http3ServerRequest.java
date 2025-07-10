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

package io.vertx.core.http.impl.http3;

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
import io.vertx.core.http.*;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpEventHandler;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.NettyFileUpload;
import io.vertx.core.http.impl.NettyFileUploadDataFactory;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http3ServerRequest extends HttpServerRequestInternal implements Http3ServerStreamHandler {

  protected final ContextInternal context;
  protected final Http3ServerStream stream;
  protected final Http3ServerConnection connection;
  protected final Http3ServerResponse response;
  private final boolean handle100ContinueAutomatically;
  private final String serverOrigin;
  private final int maxFormAttributeSize;
  private final int maxFormFields;
  private final int maxFormBufferedBytes;

  public Handler<HttpServerRequest> handler;

  // Accessed on context thread
  private MultiMap headersMap;
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

  public Http3ServerRequest(Http3ServerStream stream,
                            ContextInternal context,
                            boolean handle100ContinueAutomatically,
                            int maxFormAttributeSize,
                            int maxFormFields,
                            int maxFormBufferedBytes,
                            String serverOrigin) {
    this.context = context;
    this.stream = stream;
    this.connection = stream.connection();
    this.response = new Http3ServerResponse(stream, context,false);
    this.serverOrigin = serverOrigin;
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    this.maxFormAttributeSize = maxFormAttributeSize;
    this.maxFormFields = maxFormFields;
    this.maxFormBufferedBytes = maxFormBufferedBytes;
  }

  private HttpEventHandler eventHandler(boolean create) {
    if (eventHandler == null && create) {
      eventHandler = new HttpEventHandler(context);
    }
    return eventHandler;
  }

  @Override
  public void handleHeaders(Http3HeadersMultiMap headers) {
    this.headersMap = headers;

    // Check expect header and implement 100 continue automatically
    CharSequence value = headers.get(HttpHeaderNames.EXPECT);
    if (handle100ContinueAutomatically &&
      ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
        headers.contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE, true))) {
      response.writeContinue();
    }

    context.dispatch(this, handler);
  }

  @Override
  public void handleException(Throwable cause) {
    boolean notify;
    synchronized (connection) {
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
    synchronized (connection) {
      if (postRequestDecoder != null) {
        upload = postRequestDecoder.currentPartialHttpData();
      }
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleException(failure);
    }
    if (upload instanceof NettyFileUpload) {
      ((NettyFileUpload)upload).handleException(failure);
    }
  }

  @Override
  public void handleDrained() {
    response.handleWriteQueueDrained();
  }

  @Override
  public void handleClose() {
    response.handleClose();
  }

  @Override
  public void handleCustomFrame(HttpFrame frame) {
    if (customFrameHandler != null) {
      context.dispatch(frame, customFrameHandler);
    }
  }

  public void handleData(Buffer data) {
    if (postRequestDecoder != null) {
      try {
        postRequestDecoder.offer(new DefaultHttpContent(((BufferInternal)data).getByteBuf()));
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

  public void handleTrailers(MultiMap trailers) {
    HttpEventHandler handler;
    synchronized (connection) {
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
    synchronized (connection) {
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
    return stream.method();
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
    synchronized (connection) {
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.exceptionHandler(handler);
      }
    }
    return this;
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    synchronized (connection) {
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
    synchronized (connection) {
      checkEnded();
      stream.pause();
    }
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public HttpServerRequest fetch(long amount) {
    synchronized (connection) {
      checkEnded();
      stream.fetch(amount);
    }
    return this;
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
    synchronized (connection) {
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
    return stream.uri();
  }

  @Override
  public String path() {
    synchronized (connection) {
      return stream.uri() != null ? HttpUtils.parsePath(stream.uri()) : null;
    }
  }

  @Override
  public String query() {
    synchronized (connection) {
      if (stream.uri() == null) {
        return null;
      } else {
        return HttpUtils.parseQuery(stream.uri());
      }
    }
  }

  @Override
  public String scheme() {
    return stream.scheme();
  }

  @Override
  public @Nullable HostAndPort authority() {
    return stream.authority();
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
    synchronized (connection) {
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
    if (stream.method() == HttpMethod.CONNECT) {
      return null;
    }
    synchronized (connection) {
      if (absoluteURI == null) {
        absoluteURI = HttpUtils.absoluteURI(serverOrigin, this);
      }
      return absoluteURI;
    }
  }

  @Override
  public Future<NetSocket> toNetSocket() {
    return response.netSocket(this);
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    synchronized (connection) {
      checkEnded();
      expectMultipart = expect;
      if (expect) {
        if (postRequestDecoder == null) {
          String contentType = headersMap.get(HttpHeaderNames.CONTENT_TYPE);
          if (contentType == null) {
            throw new IllegalStateException("Request must have a content-type header to decode a multipart request");
          }
          if (!HttpUtils.isValidMultipartContentType(contentType)) {
            throw new IllegalStateException("Request must have a valid content-type header to decode a multipart request");
          }
          if (!HttpUtils.isValidMultipartMethod(stream.method().toNetty())) {
            throw new IllegalStateException("Request method must be one of POST, PUT, PATCH or DELETE to decode a multipart request");
          }
          HttpRequest req = new DefaultHttpRequest(
            io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
            stream.method().toNetty(),
            stream.uri());
          req.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
          NettyFileUploadDataFactory factory = new NettyFileUploadDataFactory(context, this, () -> uploadHandler);
          factory.setMaxLimit(maxFormAttributeSize);
          postRequestDecoder = new HttpPostRequestDecoder(factory, req, HttpConstants.DEFAULT_CHARSET, maxFormFields, maxFormBufferedBytes);
        }
      } else {
        postRequestDecoder = null;
      }
    }
    return this;
  }

  @Override
  public boolean isExpectMultipart() {
    synchronized (connection) {
      return expectMultipart;
    }
  }

  @Override
  public HttpServerRequest uploadHandler(@Nullable Handler<HttpServerFileUpload> handler) {
    synchronized (connection) {
      if (handler != null) {
        checkEnded();
      }
      uploadHandler = handler;
      return this;
    }
  }

  @Override
  public MultiMap formAttributes() {
    synchronized (connection) {
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
    synchronized (connection) {
      return ended;
    }
  }

  @Override
  public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
    synchronized (connection) {
      customFrameHandler = handler;
    }
    return this;
  }

  @Override
  public HttpConnection connection() {
    return (HttpConnection) connection;
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
    synchronized (connection) {
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
    synchronized (connection) {
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
