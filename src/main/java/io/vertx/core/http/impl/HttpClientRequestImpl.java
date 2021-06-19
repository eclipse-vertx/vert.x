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
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;

import java.util.Objects;

import static io.vertx.core.http.HttpHeaders.*;

/**
 * This class is optimised for performance when used on the same event loop that is passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * This class uses {@code this} for synchronization purpose. The {@link #client}  or{@link #stream} instead are
 * called must not be called under this lock to avoid deadlocks.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientRequestImpl extends HttpClientRequestBase implements HttpClientRequest {

  static final Logger log = LoggerFactory.getLogger(HttpClientRequestImpl.class);

  private final Promise<Void> endPromise;
  private final Future<Void> endFuture;
  private boolean chunked;
  private Handler<Void> continueHandler;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean ended;
  private Throwable reset;
  private int followRedirects;
  private HeadersMultiMap headers;
  private StreamPriority priority;
  private boolean headWritten;
  private boolean isConnect;

  HttpClientRequestImpl(HttpClientImpl client, HttpClientStream stream, PromiseInternal<HttpClientResponse> responsePromise, boolean ssl, HttpMethod method,
                        SocketAddress server, String host, int port, String requestURI) {
    super(client, stream, responsePromise, ssl, method, server, host, port, requestURI);
    this.chunked = false;
    this.endPromise = context.promise();
    this.endFuture = endPromise.future();
    this.priority = HttpUtils.DEFAULT_STREAM_PRIORITY;

    //
    stream.continueHandler(this::handleContinue);
    stream.drainHandler(this::handleDrained);
    stream.exceptionHandler(this::handleException);
  }

  @Override
  void handleException(Throwable t) {
    super.handleException(t);
    Handler<Throwable> handler;
    synchronized (this) {
      handler = exceptionHandler;
      if (handler == null || endFuture.isComplete()) {
        log.error(t);
        return;
      }
    }
    // Might be called from non vertx thread
    context.emit(t, handler);
    endPromise.tryFail(t);
  }

  @Override
  public synchronized HttpClientRequest setFollowRedirects(boolean followRedirects) {
    checkEnded();
    if (followRedirects) {
      this.followRedirects = client.getOptions().getMaxRedirects() - 1;
    } else {
      this.followRedirects = 0;
    }
    return this;
  }

  @Override
  public synchronized HttpClientRequest setMaxRedirects(int maxRedirects) {
    Arguments.require(maxRedirects >= 0, "Max redirects must be >= 0");
    checkEnded();
    followRedirects = maxRedirects;
    return this;
  }

  @Override
  public synchronized HttpClientRequestImpl setChunked(boolean chunked) {
    checkEnded();
    if (headWritten) {
      throw new IllegalStateException("Cannot set chunked after data has been written on request");
    }
    // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
    if (client.getOptions().getProtocolVersion() != io.vertx.core.http.HttpVersion.HTTP_1_0) {
      this.chunked = chunked;
    }
    return this;
  }

  @Override
  public synchronized boolean isChunked() {
    return chunked;
  }

  @Override
  public synchronized MultiMap headers() {
    if (headers == null) {
      headers = HeadersMultiMap.httpHeaders();
    }
    return headers;
  }

  @Override
  public synchronized HttpClientRequest putHeader(String name, String value) {
    checkEnded();
    headers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpClientRequest putHeader(String name, Iterable<String> values) {
    checkEnded();
    headers().set(name, values);
    return this;
  }

  @Override
  public synchronized HttpClientRequest setWriteQueueMaxSize(int maxSize) {
    checkEnded();
    stream.doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    synchronized (this) {
      checkEnded();
    }
    return stream.isNotWritable();
  }

  @Override
  public HttpVersion version() {
    return stream.version();
  }

  private synchronized Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  public synchronized HttpClientRequest exceptionHandler(Handler<Throwable> handler) {
    if (handler != null) {
      checkEnded();
      this.exceptionHandler = handler;
    } else {
      this.exceptionHandler = null;
    }
    return this;
  }

  @Override
  public synchronized HttpClientRequest drainHandler(Handler<Void> handler) {
    if (handler != null) {
      checkEnded();
    }
    checkEnded();
    drainHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpClientRequest continueHandler(Handler<Void> handler) {
    if (handler != null) {
      checkEnded();
    }
    this.continueHandler = handler;
    return this;
  }

  @Override
  public Future<Void> sendHead() {
    Promise<Void> promise = context.promise();
    sendHead(promise);
    return promise.future();
  }

  @Override
  public HttpClientRequest sendHead(Handler<AsyncResult<Void>> headersHandler) {
    checkEnded();
    doWrite(null, false, false, headersHandler);
    return this;
  }

  @Override
  public Future<HttpClientResponse> connect() {
    if (client.getOptions().isPipelining()) {
      return context.failedFuture("Cannot upgrade a pipe-lined request");
    }
    doWrite(null, false, true, ar -> {});
    return response();
  }

  @Override
  public void connect(Handler<AsyncResult<HttpClientResponse>> handler) {
    Future<HttpClientResponse> fut = connect();
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  @Override
  public synchronized HttpClientRequest putHeader(CharSequence name, CharSequence value) {
    checkEnded();
    headers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values) {
    checkEnded();
    headers().set(name, values);
    return this;
  }

  @Override
  boolean reset(Throwable cause) {
    synchronized (this) {
      if (reset != null) {
        return false;
      }
      reset = cause;
    }
    stream.reset(cause);
    return true;
  }

  private void tryComplete() {
    endPromise.tryComplete();
  }

  @Override
  public synchronized HttpConnection connection() {
    return stream.connection();
  }

  @Override
  public HttpClientRequest writeCustomFrame(int type, int flags, Buffer payload) {
    synchronized (this) {
      checkEnded();
    }
    stream.writeFrame(type, flags, payload.getByteBuf());
    return this;
  }

  private void handleDrained(Void v) {
    Handler<Void> handler;
    synchronized (this) {
      handler =  drainHandler;
      if (handler == null || endFuture.isComplete()) {
        return;
      }
    }
    context.dispatch(handler);
  }

  private void handleNextRequest(HttpClientRequest next, Handler<AsyncResult<HttpClientResponse>> handler, long timeoutMs) {
    next.response(handler);
    next.exceptionHandler(exceptionHandler());
    exceptionHandler(null);
    next.pushHandler(pushHandler());
    next.setMaxRedirects(followRedirects - 1);
    endFuture.onComplete(ar -> {
      if (ar.succeeded()) {
        if (timeoutMs > 0) {
          next.setTimeout(timeoutMs);
        }
        next.end();
      } else {
        next.reset(0);
      }
    });
  }

  private void handleContinue(Void v) {
    Handler<Void> handler;
    synchronized (this) {
      handler = continueHandler;
    }
    if (handler != null) {
      handler.handle(null);
    }
  }

  void handleResponse(Promise<HttpClientResponse> promise, HttpClientResponse resp, long timeoutMs) {
    if (reset != null) {
      return;
    }
    int statusCode = resp.statusCode();
    if (followRedirects > 0 && statusCode >= 300 && statusCode < 400) {
      Future<RequestOptions> next = client.redirectHandler().apply(resp);
      if (next != null) {
        next.onComplete(ar1 -> {
          if (ar1.succeeded()) {
            RequestOptions options = ar1.result();
            Future<HttpClientRequest> f = client.request(options);
            f.onComplete(ar2 -> {
              if (ar2.succeeded()) {
                handleNextRequest(ar2.result(), promise, timeoutMs);
              } else {
                fail(ar2.cause());
              }
            });
          } else {
            fail(ar1.cause());
          }
        });
        return;
      }
    }
    promise.complete(resp);
  }

  @Override
  public Future<Void> end(String chunk) {
    Promise<Void> promise = context.promise();
    end(chunk, promise);
    return promise.future();
  }

  @Override
  public void end(String chunk, Handler<AsyncResult<Void>> handler) {
    end(Buffer.buffer(chunk), handler);
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    Promise<Void> promise = context.promise();
    end(chunk, enc, promise);
    return promise.future();
  }

  @Override
  public void end(String chunk, String enc, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(enc, "no null encoding accepted");
    end(Buffer.buffer(chunk, enc), handler);
  }

  @Override
  public Future<Void> end(Buffer chunk) {
    Promise<Void> promise = context.promise();
    write(chunk.getByteBuf(), true, promise);
    return promise.future();
  }

  @Override
  public void end(Buffer chunk, Handler<AsyncResult<Void>> handler) {
    write(chunk.getByteBuf(), true, handler);
  }

  @Override
  public Future<Void> end() {
    Promise<Void> promise = context.promise();
    end(promise);
    return promise.future();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    write(null, true, handler);
  }

  @Override
  public Future<Void> write(Buffer chunk) {
    Promise<Void> promise = context.promise();
    write(chunk, promise);
    return promise.future();
  }

  @Override
  public void write(Buffer chunk, Handler<AsyncResult<Void>> handler) {
    ByteBuf buf = chunk.getByteBuf();
    write(buf, false, handler);
  }

  @Override
  public Future<Void> write(String chunk) {
    Promise<Void> promise = context.promise();
    write(chunk, promise);
    return promise.future();
  }

  @Override
  public void write(String chunk, Handler<AsyncResult<Void>> handler) {
    write(Buffer.buffer(chunk).getByteBuf(), false, handler);
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    Promise<Void> promise = context.promise();
    write(chunk, enc, promise);
    return promise.future();
  }

  @Override
  public void write(String chunk, String enc, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(enc, "no null encoding accepted");
    write(Buffer.buffer(chunk, enc).getByteBuf(), false, handler);
  }

  private boolean requiresContentLength() {
    return !chunked && (headers == null || !headers.contains(CONTENT_LENGTH)) && !isConnect;
  }

  private void write(ByteBuf buff, boolean end, Handler<AsyncResult<Void>> completionHandler) {
    if (end) {
      if (buff != null && requiresContentLength()) {
        headers().set(CONTENT_LENGTH, String.valueOf(buff.readableBytes()));
      }
    } else if (requiresContentLength()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
        + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }
    doWrite(buff, end, false, completionHandler);
  }

  private void doWrite(ByteBuf buff, boolean end, boolean connect, Handler<AsyncResult<Void>> completionHandler) {
    boolean writeHead;
    synchronized (this) {
      if (ended) {
        completionHandler.handle(Future.failedFuture(new IllegalStateException("Request already complete")));
        return;
      }
      checkResponseHandler();
      if (!headWritten) {
        headWritten = true;
        isConnect = connect;
        writeHead = true;
      } else {
        writeHead = false;
      }
      ended = end;
    }

    if (writeHead) {
      HttpMethod method = getMethod();
      String uri = getURI();
      HttpRequestHead head = new HttpRequestHead(method, uri, headers, authority(), absoluteURI());
      stream.writeHead(head, chunked, buff, ended, priority, connect, completionHandler);
    } else {
      if (buff == null && !end) {
        throw new IllegalArgumentException();
      }
      stream.writeBuffer(buff, end, completionHandler);
    }
    if (end) {
      tryComplete();
    }
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Request already complete");
    }
  }

  private void checkResponseHandler() {
/*
    if (stream == null && !connecting && responsePromise.future().getHandler() == null) {
      throw new IllegalStateException("You must set a response handler before connecting to the server");
    }
*/
  }

  @Override
  public synchronized HttpClientRequest setStreamPriority(StreamPriority priority) {
    if (headWritten) {
      stream.updatePriority(priority);
    } else {
      this.priority = priority;
    }
    return this;
  }

  @Override
  public synchronized StreamPriority getStreamPriority() {
    return stream.priority();
  }
}
