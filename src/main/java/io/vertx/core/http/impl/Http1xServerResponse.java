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
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.observability.HttpResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.Set;

import static io.vertx.core.http.HttpHeaders.*;

/**
 *
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * It's important we don't have different locks for connection and request/response to avoid deadlock conditions
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1xServerResponse implements HttpServerResponse, HttpResponse {

  private static final Buffer EMPTY_BUFFER = Buffer.buffer(Unpooled.EMPTY_BUFFER);
  private static final Logger log = LoggerFactory.getLogger(Http1xServerResponse.class);
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
  private boolean writable;
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
                       boolean writable) {
    this.vertx = vertx;
    this.conn = conn;
    this.context = context;
    this.version = request.protocolVersion();
    this.headers = HeadersMultiMap.httpHeaders();
    this.request = request;
    this.status = HttpResponseStatus.OK;
    this.requestMetric = requestMetric;
    this.writable = writable;
    this.keepAlive = (version == HttpVersion.HTTP_1_1 && !request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, HttpHeaders.CLOSE, true))
      || (version == HttpVersion.HTTP_1_0 && request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE, true));
    this.head = request.method() == io.netty.handler.codec.http.HttpMethod.HEAD;
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
      return !writable;
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
  public Future<Void> write(Buffer chunk) {
    PromiseInternal<Void> promise = context.promise();
    write(chunk.getByteBuf(), promise);
    return promise.future();
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    PromiseInternal<Void> promise = context.promise();
    write(Buffer.buffer(chunk, enc).getByteBuf(), promise);
    return promise.future();
  }

  @Override
  public Future<Void> write(String chunk) {
    PromiseInternal<Void> promise = context.promise();
    write(Buffer.buffer(chunk).getByteBuf(), promise);
    return promise.future();
  }

  @Override
  public HttpServerResponse writeContinue() {
    conn.write100Continue();
    return this;
  }

  @Override
  public Future<Void> writeEarlyHints(MultiMap headers) {
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
    synchronized (conn) {
      if (written) {
        throw new IllegalStateException(RESPONSE_WRITTEN);
      }
      written = true;
      ByteBuf data = chunk.getByteBuf();
      bytesWritten += data.readableBytes();
      HttpObject msg;
      if (!headWritten) {
        // if the head was not written yet we can write out everything in one go
        // which is cheaper.
        prepareHeaders(bytesWritten);
        msg = new AssembledFullHttpResponse(head, version, status, headers, data, trailingHeaders);
      } else {
        msg = new AssembledLastHttpContent(data, trailingHeaders);
      }
      conn.writeToChannel(msg, listener);
      conn.responseComplete();
      if (bodyEndHandler != null) {
        bodyEndHandler.handle(null);
      }
      if (!closed && endHandler != null) {
        endHandler.handle(null);
      }
      if (!keepAlive) {
        closeConnAfterWrite();
        closed = true;
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
  public void close() {
    synchronized (conn) {
      if (!closed) {
        if (headWritten) {
          closeConnAfterWrite();
        } else {
          conn.close();
        }
        closed = true;
      }
    }
  }

  @Override
  public Future<Void> end() {
    return end(EMPTY_BUFFER);
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    synchronized (conn) {
      checkValid();
      if (headWritten) {
        throw new IllegalStateException("Head already written");
      }
      File file = vertx.resolveFile(filename);
      ContextInternal ctx = vertx.getOrCreateContext();
      RandomAccessFile raf;
      try {
        raf = new RandomAccessFile(file, "r");
      } catch (Exception e) {
        return ctx.failedFuture(e);
      }
      long actualLength = Math.min(length, file.length() - offset);
      long actualOffset = Math.min(offset, file.length());
      if (!headers.contains(HttpHeaders.CONTENT_TYPE)) {
        String contentType = MimeMapping.getMimeTypeForFilename(filename);
        if (contentType != null) {
          headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        }
      }
      prepareHeaders(actualLength);
      bytesWritten = actualLength;
      written = true;

      conn.writeToChannel(new AssembledHttpResponse(head, version, status, headers));

      ChannelFuture channelFut = conn.sendFile(raf, actualOffset, actualLength);
      channelFut.addListener(future -> {

        // write an empty last content to let the http encoder know the response is complete
        if (future.isSuccess()) {
          ChannelPromise pr = conn.channelHandlerContext().newPromise();
          conn.writeToChannel(LastHttpContent.EMPTY_LAST_CONTENT, pr);
          if (!keepAlive) {
            pr.addListener(a -> {
              closeConnAfterWrite();
            });
          }
        }

        // signal body end handler
        Handler<Void> handler;
        synchronized (conn) {
          handler = bodyEndHandler;
        }
        if (handler != null) {
          context.emit(handler);
        }

        // allow to write next response
        conn.responseComplete();

        // signal end handler
        Handler<Void> end;
        synchronized (conn) {
          end = !closed ? endHandler : null;
        }
        if (null != end) {
          context.emit(end);
        }
      });

      PromiseInternal<Void> promise = ctx.promise();
      channelFut.addListener(promise);
      return promise.future();
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

  private void closeConnAfterWrite() {
    ChannelPromise channelFuture = conn.channelFuture();
    conn.writeToChannel(Unpooled.EMPTY_BUFFER, channelFuture);
    channelFuture.addListener(fut -> conn.close());
  }

  void handleWritabilityChanged(boolean writable) {
    Handler<Void> handler;
    synchronized (conn) {
      boolean skip = this.writable && !writable;
      this.writable = writable;
      handler = drainHandler;
      if (handler == null || skip) {
        return;
      }
    }
    context.dispatch(null, handler);
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
        String value = contentLength == 0 ? "0" : String.valueOf(contentLength);
        headers.set(HttpHeaders.CONTENT_LENGTH, value);
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
      HttpObject msg;
      if (!headWritten) {
        prepareHeaders(-1);
        msg = new AssembledHttpResponse(head, version, status, headers, chunk);
      } else {
        msg = new DefaultHttpContent(chunk);
      }
      conn.writeToChannel(msg, promise);
      return this;
    }
  }

  Future<NetSocket> netSocket(HttpMethod requestMethod, MultiMap requestHeaders) {
    synchronized (conn) {
      if (netSocket == null) {
        if (headWritten) {
          return context.failedFuture("Response already sent");
        }
        if (!HttpUtils.isConnectOrUpgrade(requestMethod, requestHeaders)) {
          return context.failedFuture("HTTP method must be CONNECT or an HTTP upgrade to upgrade the connection to a TCP socket");
        }
        status = requestMethod == HttpMethod.CONNECT ? HttpResponseStatus.OK : HttpResponseStatus.SWITCHING_PROTOCOLS;
        prepareHeaders(-1);
        PromiseInternal<Void> upgradePromise = context.promise();
        conn.writeToChannel(new AssembledHttpResponse(head, version, status, headers), upgradePromise);
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
  public boolean reset(long code) {
    synchronized (conn) {
      if (written) {
        return false;
      }
    }
    close();
    return true;
  }

  @Override
  public Future<HttpServerResponse> push(HttpMethod method, String host, String path, MultiMap headers) {
    return context.failedFuture("HTTP/1 does not support response push");
  }

  @Override
  public HttpServerResponse writeCustomFrame(int type, int flags, Buffer payload) {
    return this;
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
