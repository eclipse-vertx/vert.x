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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.impl.form.FormDecoder;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.core.internal.http.QueryParamDecoder;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import java.util.Set;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerRequestImpl extends HttpServerRequestBase implements ContentDecoder<Void> {

  private final ContextInternal context;
  private final HttpServerStream stream;
  private final HttpServerConnection connection;
  private final HttpServerResponseImpl response;
  private final boolean handle100ContinueAutomatically;
  private final String serverOrigin;
  private final int maxFormAttributeSize;
  private final int maxFormFields;
  private final int maxFormBufferedBytes;
  private final Handler<HttpServerRequest> handler;

  // Accessed on context thread
  private HttpMethod method;
  private String scheme;
  private String uri;
  private MultiMap headersMap;
  private HostAndPort authority;
  private HostAndPort realAuthority;
  private String absoluteURI;
  private MultiMap attributes;
  private HttpEventHandler eventHandler;
  private Throwable failure;
  private boolean ended;
  private Handler<HttpServerFileUpload> uploadHandler;
  private boolean expectMultipart;
  private FormDecoder formDecoder;
  private long bytesRead;
  private FlowController flowController;

  public HttpServerRequestImpl(Handler<HttpServerRequest> handler,
                               HttpServerStream stream,
                               ContextInternal context,
                               boolean handle100ContinueAutomatically,
                               int maxFormAttributeSize,
                               int maxFormFields,
                               int maxFormBufferedBytes,
                               QueryParamDecoder queryParamDecoder,
                               String serverOrigin) {
    super(queryParamDecoder);
    this.handler = handler;
    this.context = context;
    this.stream = stream;
    this.connection = stream.connection();
    this.response = new HttpServerResponseImpl(stream, context,false);
    this.serverOrigin = serverOrigin;
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    this.maxFormAttributeSize = maxFormAttributeSize;
    this.maxFormFields = maxFormFields;
    this.maxFormBufferedBytes = maxFormBufferedBytes;
  }

  public void init() {
    stream.headHandler(this::handleHeaders);
    stream.resetHandler(this::handleReset);
    stream.exceptionHandler(this::handleException);
    stream.closeHandler(this::handleClosed);
    stream.contentDecoder(this);
    stream.trailersHandler(this::handleTrailers);
    stream.drainHandler(response::handleWriteQueueDrained);
  }

  @Override
  public void init(FlowController flowController) {
    this.flowController = flowController;
  }

  @Override
  public void handle(ByteBuf chunk) {
    ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(chunk.readableBytes());
    buffer.writeBytes(chunk, chunk.readerIndex(), chunk.readableBytes());
    Buffer buff = BufferInternal.buffer(buffer);
    handleData(buff);
  }

  @Override
  public void handleClose(Throwable cause) {
  }

  @Override
  public <T> Future<T> contentDecoder(ContentDecoder<T> decoder) {
    return stream.contentDecoder(decoder);
  }

  private HttpEventHandler eventHandler(boolean create) {
    if (eventHandler == null && create) {
      eventHandler = new HttpEventHandler(context, failure);
    }
    return eventHandler;
  }

  public void handleHeaders(HttpRequestHead headers) {

    uri = headers.uri();
    scheme = headers.scheme; // TODO make this better
    method = headers.method();
    realAuthority = headers.authority;
    authority = headers.authority;
    if (authority == null) {
      String hostHeader = headers.headers.get(HttpHeaders.HOST);
      if (hostHeader != null) {
        headers.headers.remove(HttpHeaders.HOST);
        authority = HostAndPort.parseAuthority(hostHeader, -1);
      }
    }

    this.headersMap = headers.headers();

    response.init(headers);

    // Check expect header and implement 100 continue automatically
    CharSequence value = headersMap.get(HttpHeaderNames.EXPECT);
    if (handle100ContinueAutomatically &&
      ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
        headersMap.contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE, true))) {
      response.writeContinue();
    }

    handler.handle(this);
  }

  private void handleException(Throwable cause) {
    boolean notify;
    synchronized (connection) {
      notify = !ended;
    }
    if (notify) {
      notifyException(cause);
    }
    response.handleException(cause);
  }

  private void handleClosed(Void v) {
    response.handleClose(v);
  }

  private void notifyException(Throwable failure) {
    HttpEventHandler handler;
    synchronized (connection) {
      handler = eventHandler;
      if (handler == null && this.failure == null) {
        this.failure = failure;
      }
    }
    if (handler != null) {
      handler.handleException(failure);
    }
  }

  public void handleData(Buffer data) {
    bytesRead += data.length();
    HttpEventHandler handler = eventHandler;
    if (handler != null) {
      handler.handleChunk(data);
    }
  }

  public void handleTrailers(MultiMap trailers) {
  }

  public Void handleEnd() {
    HttpEventHandler handler;
    synchronized (connection) {
      ended = true;
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleEnd();
    }
    return null;
  }

  public void handleReset(long errorCode) {
    boolean notify;
    synchronized (connection) {
      notify = !ended;
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
    return method;
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
  public ByteBufAllocator allocator() {
    return stream.allocator();
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
    }
    flowController.pause();
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
    }
    flowController.fetch(amount);
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
    return stream.version();
  }

  @Override
  public String uri() {
    return uri;
  }

  @Override
  public String path() {
    synchronized (connection) {
      return uri != null ? HttpUtils.parsePath(uri) : null;
    }
  }

  @Override
  public String query() {
    synchronized (connection) {
      if (uri == null) {
        return null;
      } else {
        return HttpUtils.parseQuery(uri);
      }
    }
  }

  @Override
  public String scheme() {
    return scheme;
  }

  @Override
  public @Nullable HostAndPort authority() {
    return authority;
  }

  @Override
  public @Nullable HostAndPort authority(boolean real) {
    return real ? realAuthority : authority;
  }

  @Override
  public long bytesRead() {
    return bytesRead;
  }

  @Override
  public HttpServerResponseImpl response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    return headersMap;
  }

  @Override
  public SocketAddress remoteAddress() {
    return super.remoteAddress();
  }

  @Override
  public String absoluteURI() {
    if (method == HttpMethod.CONNECT) {
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
      if (expect) {
        if (formDecoder == null) {
          HttpRequest req = new DefaultHttpRequest(
            io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
            method.toNetty(),
            uri);
          String contentType = headersMap.get(HttpHeaderNames.CONTENT_TYPE);
          if (contentType != null) {
            req.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
          }
          FormDecoder decoder = new FormDecoder(context, req, maxFormAttributeSize, maxFormFields, maxFormBufferedBytes,
            () -> uploadHandler, formAttributes(), this::handleException, this::handleData, v -> handleEnd());
          stream.contentDecoder(decoder);
          formDecoder = decoder;
        }
      } else if (formDecoder != null) {
        stream.contentDecoder(this);
        formDecoder = null;
      }
      expectMultipart = expect;
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
  public long streamId() {
    return stream.id();
  }

  @Override
  public Future<ServerWebSocket> toWebSocket() {
    return context.failedFuture("HTTP/2 request cannot be upgraded to a WebSocket");
  }

  @Override
  public boolean isEnded() {
    synchronized (connection) {
      return ended;
    }
  }

  @Override
  public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
    stream.customFrameHandler(handler);
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

  public StreamPriority streamPriority() {
    return stream.priority();
  }

  @Override
  public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
    stream.priorityChangeHandler(handler);
    return this;
  }

  @Override
  public DecoderResult decoderResult() {
    return DecoderResult.SUCCESS;
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

  public HttpServerStream stream() {
    return stream;
  }
}
