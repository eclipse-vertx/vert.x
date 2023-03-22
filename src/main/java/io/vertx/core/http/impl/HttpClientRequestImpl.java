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
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;

import java.util.Objects;

import static io.vertx.core.http.HttpHeaders.CONTENT_LENGTH;

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
  private Handler<MultiMap> earlyHintsHandler;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean ended;
  private Throwable reset;
  private int followRedirects;
  private HeadersMultiMap headers;
  private StreamPriority priority;
  private boolean headWritten;
  private boolean isConnect;
  private String traceOperation;

  HttpClientRequestImpl(HttpClientImpl client, HttpClientStream stream, PromiseInternal<HttpClientResponse> responsePromise, boolean ssl, HttpMethod method,
                        SocketAddress server, String host, int port, String requestURI, String traceOperation) {
    super(client, stream, responsePromise, ssl, method, server, host, port, requestURI);
    this.chunked = false;
    this.endPromise = context.promise();
    this.endFuture = endPromise.future();
    this.priority = HttpUtils.DEFAULT_STREAM_PRIORITY;
    this.traceOperation = traceOperation;

    //
    stream.continueHandler(this::handleContinue);
    stream.earlyHintsHandler(this::handleEarlyHints);
    stream.drainHandler(this::handleDrained);
    stream.exceptionHandler(this::handleException);
  }

  @Override
  void handleException(Throwable t) {
    super.handleException(t);
    if (endPromise.tryFail(t)) {
      Handler<Throwable> handler = exceptionHandler();
      if (handler != null) {
        context.emit(t, handler);
      } else {
        if (log.isDebugEnabled()) {
          log.error(t.getMessage(), t);
        } else {
          log.error(t.getMessage());
        }
      }
    }
  }

  @Override
  public synchronized HttpClientRequest setFollowRedirects(boolean followRedirects) {
    checkEnded();
    if (followRedirects) {
      this.followRedirects = client.options().getMaxRedirects() - 1;
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
    if (client.options().getProtocolVersion() != io.vertx.core.http.HttpVersion.HTTP_1_0) {
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
  public HttpClientRequest earlyHintsHandler(@Nullable Handler<MultiMap> handler) {
    if (handler != null) {
      checkEnded();
    }
    this.earlyHintsHandler = handler;
    return this;
  }

  @Override
  public Future<Void> sendHead() {
    checkEnded();
    return doWrite(null, false, false);
  }

  @Override
  public Future<HttpClientResponse> connect() {
    if (client.options().isPipelining()) {
      return context.failedFuture("Cannot upgrade a pipe-lined request");
    }
    doWrite(null, false, true);
    return response();
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
    next.response().onComplete(handler);
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

  private void handleEarlyHints(MultiMap headers) {
    Handler<MultiMap> handler;
    synchronized (this) {
      handler = earlyHintsHandler;
    }
    if (handler != null) {
      handler.handle(headers);
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
        resp
          .end()
          .compose(v -> next, err -> next)
          .onComplete(ar1 -> {
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
    return write(Buffer.buffer(chunk).getByteBuf(), true);
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    Objects.requireNonNull(enc, "no null encoding accepted");
    return write(Buffer.buffer(chunk, enc).getByteBuf(), true);
  }

  @Override
  public Future<Void> end(Buffer chunk) {
    return write(chunk.getByteBuf(), true);
  }

  @Override
  public Future<Void> end() {
    return write(null, true);
  }

  @Override
  public Future<Void> write(Buffer chunk) {
    ByteBuf buf = chunk.getByteBuf();
    return write(buf, false);
  }

  @Override
  public Future<Void> write(String chunk) {
    return write(Buffer.buffer(chunk).getByteBuf(), false);
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    Objects.requireNonNull(enc, "no null encoding accepted");
    return write(Buffer.buffer(chunk, enc).getByteBuf(), false);
  }

  private boolean requiresContentLength() {
    return !chunked && (headers == null || !headers.contains(CONTENT_LENGTH)) && !isConnect;
  }

  private Future<Void> write(ByteBuf buff, boolean end) {
    if (end) {
      if (buff != null && requiresContentLength()) {
        headers().set(CONTENT_LENGTH, String.valueOf(buff.readableBytes()));
      }
    } else if (requiresContentLength()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
        + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }
    return doWrite(buff, end, false);
  }

  private Future<Void> doWrite(ByteBuf buff, boolean end, boolean connect) {
    boolean writeHead;
    boolean writeEnd;
    synchronized (this) {
      if (ended) {
        return context.failedFuture(new IllegalStateException("Request already complete"));
      }
      checkResponseHandler();
      if (!headWritten) {
        headWritten = true;
        isConnect = connect;
        writeHead = true;
      } else {
        writeHead = false;
      }
      writeEnd = !isConnect && end;
      ended = end;
    }

    Future<Void> future;
    if (writeHead) {
      HttpMethod method = getMethod();
      String uri = getURI();
      if (uri.isEmpty()) {
        uri = "/";
      }
      HttpRequestHead head = new HttpRequestHead(method, uri, headers, authority(), absoluteURI(), traceOperation);
      future = stream.writeHead(head, chunked, buff, writeEnd, priority, connect);
    } else {
      if (buff == null && !end) {
        throw new IllegalArgumentException();
      }
      future = stream.writeBuffer(buff, writeEnd);
    }
    if (end) {
      tryComplete();
    }
    return future;
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
