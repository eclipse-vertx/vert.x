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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.observability.HttpResponse;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.vertx.core.http.HttpHeaders.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1xServerResponse implements HttpServerResponse, HttpResponse {

  private static final Buffer EMPTY_BUFFER = BufferInternal.buffer(Unpooled.EMPTY_BUFFER);
  private static final String RESPONSE_WRITTEN = "Response has already been written";

  private final VertxInternal vertx;
  private final HttpRequest request;
  private final Http1xServerConnection conn;
  private final ContextInternal context;
  private HttpResponseStatus status;
  private final HttpVersion version;
  private final boolean keepAlive;
  private final boolean head;
  private final Object requestMetric;

  private boolean headWritten;
  private boolean written;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private Handler<Void> headersEndHandler;
  private Handler<Void> bodyEndHandler;
  private boolean closed;
  private final HeadersMultiMap headers;
  private CookieJar cookies;
  private MultiMap trailers;
  private io.netty.handler.codec.http.HttpHeaders trailingHeaders = EmptyHttpHeaders.INSTANCE;
  private String statusMessage;
  private long bytesWritten;
  private Future<NetSocket> netSocket;

  Http1xServerResponse(VertxInternal vertx,
                       ContextInternal context,
                       Http1xServerConnection conn,
                       HttpRequest request,
                       Object requestMetric,
                       boolean keepAlive) {
    this.vertx = vertx;
    this.conn = conn;
    this.context = context;
    this.version = request.protocolVersion();
    this.headers = HeadersMultiMap.httpHeaders();
    this.request = request;
    this.status = HttpResponseStatus.OK;
    this.requestMetric = requestMetric;
    this.keepAlive = keepAlive;
    this.head = request.method() == io.netty.handler.codec.http.HttpMethod.HEAD;
  }

  private void checkThread() {
    if (conn.strictThreadMode && !context.executor().inThread()) {
      throw new IllegalStateException("Only the context thread can write a message");
    }
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public MultiMap trailers() {
    if (trailers == null) {
      HeadersMultiMap v = HeadersMultiMap.httpHeaders();
      trailers = v;
      trailingHeaders = v;
    }
    return trailers;
  }

  @Override
  public int statusCode() {
    return status.code();
  }

  @Override
  public int getStatusCode() {
    return status.code();
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    synchronized (conn) {
      checkHeadWritten();
      status = statusMessage != null ? new HttpResponseStatus(statusCode, statusMessage) : HttpResponseStatus.valueOf(statusCode);
    }
    return this;
  }

  @Override
  public String getStatusMessage() {
    return status.reasonPhrase();
  }

  @Override
  public HttpServerResponse setStatusMessage(String statusMessage) {
    synchronized (conn) {
      checkHeadWritten();
      this.statusMessage = statusMessage;
      this.status = new HttpResponseStatus(status.code(), statusMessage);
      return this;
    }
  }

  @Override
  public Http1xServerResponse setChunked(boolean chunked) {
    synchronized (conn) {
      checkHeadWritten();
      // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
      if (version != HttpVersion.HTTP_1_0) {
        headers.set(HttpHeaders.TRANSFER_ENCODING, chunked ? "chunked" : null);
      }
      return this;
    }
  }

  @Override
  public boolean isChunked() {
    synchronized (conn) {
      return headers.contains(HttpHeaders.TRANSFER_ENCODING, HttpHeaders.CHUNKED, true);
    }
  }

  @Override
  public Http1xServerResponse putHeader(String key, String value) {
    synchronized (conn) {
      checkHeadWritten();
      headers.set(key, value);
      return this;
    }
  }

  @Override
  public Http1xServerResponse putHeader(String key, Iterable<String> values) {
    synchronized (conn) {
      checkHeadWritten();
      headers.set(key, values);
      return this;
    }
  }

  @Override
  public Http1xServerResponse putTrailer(String key, String value) {
    synchronized (conn) {
      checkValid();
      trailers().set(key, value);
      return this;
    }
  }

  @Override
  public Http1xServerResponse putTrailer(String key, Iterable<String> values) {
    synchronized (conn) {
      checkValid();
      trailers().set(key, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkHeadWritten();
      headers.set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    synchronized (conn) {
      checkHeadWritten();
      headers.set(name, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int size) {
    synchronized (conn) {
      checkValid();
      conn.doSetWriteQueueMaxSize(size);
      return this;
    }
  }

  @Override
  public boolean writeQueueFull() {
    synchronized (conn) {
      checkValid();
      return conn.writeQueueFull();
    }
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      drainHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      exceptionHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse closeHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      closeHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse endHandler(@Nullable Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      endHandler = handler;
      return this;
    }
  }

  @Override
  public Future<Void> writeHead() {
    checkThread();
    PromiseInternal<Void> promise = context.promise();
    synchronized (conn) {
      if (headWritten) {
        throw new IllegalStateException();
      }
      if (!headers.contains(HttpHeaders.TRANSFER_ENCODING) && !headers.contains(HttpHeaders.CONTENT_LENGTH)) {
        throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
      }
      VertxHttpObject msg;
      prepareHeaders(-1);
      msg = new VertxHttpResponse(head, version, status, headers);
      conn.write(msg, promise);
    }
    return promise.future();
  }

  @Override
  public Future<Void> write(Buffer chunk) {
    PromiseInternal<Void> promise = context.promise();
    write(((BufferInternal)chunk).getByteBuf(), promise);
    return promise.future();
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    PromiseInternal<Void> promise = context.promise();
    write(BufferInternal.buffer(chunk, enc).getByteBuf(), promise);
    return promise.future();
  }

  @Override
  public Future<Void> write(String chunk) {
    PromiseInternal<Void> promise = context.promise();
    write(BufferInternal.buffer(chunk).getByteBuf(), promise);
    return promise.future();
  }

  @Override
  public Future<Void> writeContinue() {
    checkThread();
    Promise<Void> promise = context.promise();
    conn.write100Continue(promise);
    return promise.future();
  }

  @Override
  public Future<Void> writeEarlyHints(MultiMap headers) {
    checkThread();
    PromiseInternal<Void> promise = context.promise();
    HeadersMultiMap headersMultiMap;
    if (headers instanceof HeadersMultiMap) {
      headersMultiMap = (HeadersMultiMap) headers;
    } else {
      headersMultiMap = HeadersMultiMap.httpHeaders();
      headersMultiMap.addAll(headers);
    }
    synchronized (conn) {
      checkHeadWritten();
    }
    conn.write103EarlyHints(headersMultiMap, promise);
    return promise.future();
  }

  @Override
  public Future<Void> end(String chunk) {
    return end(Buffer.buffer(chunk));
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    return end(Buffer.buffer(chunk, enc));
  }

  @Override
  public Future<Void> end(Buffer chunk) {
    PromiseInternal<Void> promise = context.promise();
    end(chunk, promise);
    return promise.future();
  }

  private void end(Buffer chunk, PromiseInternal<Void> listener) {
    checkThread();
    synchronized (conn) {
      if (written) {
        throw new IllegalStateException(RESPONSE_WRITTEN);
      }
      written = true;
      ByteBuf data = ((BufferInternal)chunk).getByteBuf();
      bytesWritten += data.readableBytes();
      VertxHttpObject msg;
      if (!headWritten) {
        // if the head was not written yet we can write out everything in one go
        // which is cheaper.
        prepareHeaders(bytesWritten);
        msg = new VertxFullHttpResponse(head, version, status, data, headers, trailingHeaders);
      } else {
        msg = new VertxLastHttpContent(data, trailingHeaders);
      }
      conn.write(msg, listener);
      if (bodyEndHandler != null) {
        bodyEndHandler.handle(null);
      }
      if (!closed && endHandler != null) {
        endHandler.handle(null);
      }
      if (!keepAlive) {
        closed = true; // ?????
      }
    }
  }

  void completeHandshake() {
    if (conn.metrics != null) {
      conn.metrics.responseBegin(requestMetric, this);
    }
    setStatusCode(101);
    synchronized (conn) {
      headWritten = true;
      written = true;
    }
    conn.responseComplete();
  }

  @Override
  public Future<Void> end() {
    return end(EMPTY_BUFFER);
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    File file = vertx.fileResolver().resolve(filename);
    RandomAccessFile raf;
    long size;
    try {
      raf = new RandomAccessFile(file, "r");
      size = raf.length();
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    if (!headers.contains(HttpHeaders.CONTENT_TYPE)) {
      CharSequence mimeType = MimeMapping.mimeTypeForFilename(filename);
      if (mimeType == null) {
        mimeType = APPLICATION_OCTET_STREAM;
      }
      headers.set(CONTENT_TYPE, mimeType);
    }
    return sendFileInternal(offset, length, size, raf, null, true);
  }

  @Override
  public Future<Void> sendFile(RandomAccessFile file, long offset, long length) {
    if (!headers.contains(HttpHeaders.CONTENT_TYPE)) {
      headers.set(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
    }
    long size;
    try {
      size = file.length();
    } catch (IOException e) {
      return context.failedFuture(e);
    }
    return sendFileInternal(offset, length, size, file, null, false);
  }

  @Override
  public Future<Void> sendFile(FileChannel channel, long offset, long length) {
    if (!headers.contains(HttpHeaders.CONTENT_TYPE)) {
      headers.set(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
    }
    long size;
    try {
      size = channel.size();
    } catch (IOException e) {
      return context.failedFuture(e);
    }
    return sendFileInternal(offset, length, size, null, channel, false);
  }

  private Future<Void> sendFileInternal(long offset, long length, long size, RandomAccessFile file, FileChannel fileChannel, boolean close) {
    Future<Void> ret = null;
    try {
      ContextInternal ctx = vertx.getOrCreateContext();
      if (offset < 0) {
        return ctx.failedFuture("offset : " + offset + " (expected: >= 0)");
      }
      if (length < 0) {
        return ctx.failedFuture("length : " + length + " (expected: >= 0)");
      }
      long actualLength = Math.min(length, size - offset);
      long actualOffset = Math.min(offset, size);
      if (actualLength < 0) {
        return ctx.failedFuture("offset : " + offset + " is larger than the requested file length : " + size);
      }
      synchronized (conn) {
        checkValid();
        if (headWritten) {
          throw new IllegalStateException("Head already written");
        }

        // fail early before status code/headers are written to the response
        prepareHeaders(actualLength);
        bytesWritten = actualLength;
        written = true;
        conn.write(new VertxAssembledHttpResponse(head, version, status, headers), null);
        FileChannel toSend = fileChannel == null ? file.getChannel() : fileChannel;
        ChannelFuture channelFuture = conn.sendFile(toSend, actualOffset, actualLength);
        PromiseInternal<Void> promise = context.promise();
        ret = promise.future();
        channelFuture.addListener(future -> {
          if (future.isSuccess()) {

            // signal body end handler
            Handler<Void> handler;
            synchronized (conn) {
              handler = bodyEndHandler;
            }
            if (handler != null) {
              ctx.emit(handler);
            }

            // signal end handler
            Handler<Void> end;
            synchronized (conn) {
              end = !closed ? endHandler : null;
            }
            if (null != end) {
              ctx.emit(end);
            }

            // write an empty last content to let the http encoder know the response is complete
            conn.write(new VertxLastHttpContent(Unpooled.buffer(0), DefaultHttpHeadersFactory.trailersFactory().newHeaders()), promise);
          } else {
            promise.fail(future.cause());
          }

          //
          if (close) {
            try {
              if (file != null) {
                file.close();
              } else {
                fileChannel.close();
              }
            } catch (IOException ignore) {
            }
          }
        });
      }
      return ret;
    } finally {
      if (ret == null && close) {
        try {
          if (file != null) {
            file.close();
          } else {
            fileChannel.close();
          }
        } catch (IOException ignore) {
        }
      }
    }
  }

  @Override
  public boolean ended() {
    synchronized (conn) {
      return written;
    }
  }

  @Override
  public boolean closed() {
    synchronized (conn) {
      return closed;
    }
  }

  @Override
  public boolean headWritten() {
    synchronized (conn) {
      return headWritten;
    }
  }

  @Override
  public long bytesWritten() {
    synchronized (conn) {
      return bytesWritten;
    }
  }

  @Override
  public HttpServerResponse headersEndHandler(Handler<Void> handler) {
    synchronized (conn) {
      this.headersEndHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse bodyEndHandler(Handler<Void> handler) {
    synchronized (conn) {
      this.bodyEndHandler = handler;
      return this;
    }
  }

  void handleWriteQueueDrained(Void v) {
    Handler<Void> handler;
    synchronized (conn) {
      handler = drainHandler;
    }
    if (handler != null) {
      context.dispatch(null, handler);
    }
  }

  void handleException(Throwable t) {
    if (t instanceof HttpClosedException) {
      handleClosed();
    } else {
      Handler<Throwable> handler;
      synchronized (conn) {
        handler = exceptionHandler;
        if (handler == null) {
          return;
        }
      }
      context.dispatch(t, handler);
    }
  }

  private void handleClosed() {
    Handler<Void> closedHandler;
    Handler<Void> endHandler;
    Handler<Throwable> exceptionHandler;
    synchronized (conn) {
      if (closed) {
        return;
      }
      closed = true;
      exceptionHandler = written ? null : this.exceptionHandler;
      endHandler = this.written ? null : this.endHandler;
      closedHandler = this.closeHandler;
    }
    if (exceptionHandler != null) {
      context.dispatch(HttpUtils.CONNECTION_CLOSED_EXCEPTION, exceptionHandler);
    }
    if (endHandler != null) {
      context.dispatch(null, endHandler);
    }
    if (closedHandler != null) {
      context.dispatch(null, closedHandler);
    }
  }

  private void checkValid() {
    if (written) {
      throw new IllegalStateException(RESPONSE_WRITTEN);
    }
  }

  private void checkHeadWritten() {
    if (headWritten) {
      throw new IllegalStateException("Response head already sent");
    }
  }

  private void prepareHeaders(long contentLength) {
    if (version == HttpVersion.HTTP_1_0 && keepAlive) {
      headers.set(HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE);
    } else if (version == HttpVersion.HTTP_1_1 && !keepAlive) {
      headers.set(HttpHeaders.CONNECTION, HttpHeaders.CLOSE);
    }
    if (head || status == HttpResponseStatus.NOT_MODIFIED) {
      // For HEAD request or NOT_MODIFIED response
      // don't set automatically the content-length
      // and remove the transfer-encoding
      headers.remove(HttpHeaders.TRANSFER_ENCODING);
    } else {
      // Set content-length header automatically
      if (contentLength >= 0 && !headers.contains(HttpHeaders.CONTENT_LENGTH) && !headers.contains(HttpHeaders.TRANSFER_ENCODING)) {
        headers.set(HttpHeaders.CONTENT_LENGTH, HttpUtils.positiveLongToString(contentLength));
      }
    }
    if (headersEndHandler != null) {
      headersEndHandler.handle(null);
    }
    if (cookies != null) {
      setCookies();
    }
    if (Metrics.METRICS_ENABLED) {
      // TODO : DONE SOMEWHERE ELSE FROM EVENT LOOP
      reportResponseBegin();
    }
    headWritten = true;
  }

  private void setCookies() {
    for (ServerCookie cookie: cookies) {
      if (cookie.isChanged()) {
        headers.add(SET_COOKIE, cookie.encode());
      }
    }
  }

  private void reportResponseBegin() {
    if (conn.metrics != null) {
      conn.metrics.responseBegin(requestMetric, this);
    }
  }

  private Http1xServerResponse write(ByteBuf chunk, PromiseInternal<Void> promise) {
    checkThread();
    synchronized (conn) {
      if (written) {
        throw new IllegalStateException("Response has already been written");
      } else if (!headWritten && !headers.contains(HttpHeaders.TRANSFER_ENCODING) && !headers.contains(HttpHeaders.CONTENT_LENGTH)) {
        if (version != HttpVersion.HTTP_1_0) {
          throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
            + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
        }
      }
      bytesWritten += chunk.readableBytes();
      VertxHttpObject msg;
      if (!headWritten) {
        prepareHeaders(-1);
        msg = new VertxAssembledHttpResponse(head, version, status, headers, chunk);
      } else {
        msg = new VertxHttpContent(chunk);
      }
      conn.write(msg, promise);
      return this;
    }
  }

  Future<NetSocket> netSocket(HttpMethod requestMethod, MultiMap requestHeaders) {
    checkThread();
    synchronized (conn) {
      if (netSocket == null) {
        if (headWritten) {
          return context.failedFuture("Response already sent");
        }
        if (!HttpUtils.isConnectOrUpgrade(requestMethod, requestHeaders)) {
          return context.failedFuture("HTTP method must be CONNECT or an HTTP upgrade to upgrade the connection to a TCP socket");
        }
        ChannelPipeline pipeline = conn.channel().pipeline();
        WebSocketServerExtensionHandler wsHandler = pipeline.get(WebSocketServerExtensionHandler.class);
        if (wsHandler != null) {
          pipeline.remove(wsHandler);
        }
        status = requestMethod == HttpMethod.CONNECT ? HttpResponseStatus.OK : HttpResponseStatus.SWITCHING_PROTOCOLS;
        prepareHeaders(-1);
        PromiseInternal<Void> upgradePromise = context.promise();
        conn.write(new VertxAssembledHttpResponse(head, version, status, headers), upgradePromise);
        written = true;
        Promise<NetSocket> promise = context.promise();
        netSocket = promise.future();
        conn.netSocket(promise);
      }
    }
    return netSocket;
  }

  @Override
  public int streamId() {
    return -1;
  }

  @Override
  public Future<Void> reset(long code) {
    synchronized (conn) {
      if (written) {
        return context.failedFuture("Response written");
      }
    }
    conn.close();
    return context.succeededFuture();
  }

  @Override
  public Future<HttpServerResponse> push(HttpMethod method, HostAndPort authority, String path, MultiMap headers) {
    return context.failedFuture("HTTP/1 does not support response push");
  }

  @Override
  public Future<Void> writeCustomFrame(int type, int flags, Buffer payload) {
    return context.failedFuture("HTTP/1 does not support custom frames");
  }

  CookieJar cookies() {
    synchronized (conn) {
      // avoid double parsing
      if (cookies == null) {
        String cookieHeader = request.headers().get(io.vertx.core.http.HttpHeaders.COOKIE);
        if (cookieHeader == null) {
          cookies = new CookieJar();
        } else {
          cookies = new CookieJar(cookieHeader);
        }
      }
    }
    return cookies;
  }

  @Override
  public HttpServerResponse addCookie(Cookie cookie) {
    synchronized (conn) {
      checkHeadWritten();
      cookies().add((ServerCookie) cookie);
    }
    return this;
  }

  @Override
  public @Nullable Cookie removeCookie(String name, boolean invalidate) {
    synchronized (conn) {
      checkHeadWritten();
      return cookies().removeOrInvalidate(name, invalidate);
    }
  }

  @Override
  public @Nullable Cookie removeCookie(String name, String domain, String path, boolean invalidate) {
    synchronized (conn) {
      checkHeadWritten();
      return cookies().removeOrInvalidate(name, domain, path, invalidate);
    }
  }

  @Override
  public @Nullable Set<Cookie> removeCookies(String name, boolean invalidate) {
    synchronized (conn) {
      checkHeadWritten();
      return (Set) cookies().removeOrInvalidateAll(name, invalidate);
    }
  }
}
