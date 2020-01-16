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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.vertx.core.http.HttpHeaders.*;

/**
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
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
  private String authority;
  private Handler<Void> continueHandler;
  private Handler<Void> drainHandler;
  private Handler<HttpClientRequest> pushHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean ended;
  private Throwable reset;
  private ByteBuf pendingChunks;
  private List<Handler<AsyncResult<Void>>> pendingHandlers;
  private int pendingMaxSize = -1;
  private int followRedirects;
  private VertxHttpHeaders headers;
  private StreamPriority priority;
  public HttpClientStream stream;
  private boolean connecting;
  private Promise<NetSocket> netSocketPromise;

  HttpClientRequestImpl(HttpClientImpl client, ContextInternal context, boolean ssl, HttpMethod method,
                        SocketAddress server, String host, int port, String requestURI) {
    super(client, context, ssl, method, server, host, port, requestURI);
    this.chunked = false;
    this.endPromise = context.promise();
    this.endFuture = endPromise.future();
    this.priority = HttpUtils.DEFAULT_STREAM_PRIORITY;
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
    context.emit(t, handler);
    endPromise.tryFail(t);
  }

  @Override
  public synchronized int streamId() {
    return stream == null ? -1 : stream.id();
  }

  @Override
  public synchronized Future<NetSocket> netSocket() {
    if (client.getOptions().isPipelining()) {
      return Future.failedFuture("Cannot upgrade a pipe-lined request");
    }
    if (netSocketPromise == null) {
      netSocketPromise = context.promise();
    }
    return netSocketPromise.future();
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
    if (stream != null) {
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
  public synchronized HttpClientRequest setAuthority(String authority) {
    this.authority = authority;
    return this;
  }

  @Override
  public synchronized String getAuthority() {
    return authority;
  }

  @Override
  public synchronized MultiMap headers() {
    if (headers == null) {
      headers = new VertxHttpHeaders();
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
    if (stream == null) {
      pendingMaxSize = maxSize;
    } else {
      stream.doSetWriteQueueMaxSize(maxSize);
    }
    return this;
  }

  @Override
  public synchronized boolean writeQueueFull() {
    checkEnded();
    synchronized (this) {
      checkEnded();
      if (stream == null) {
        // Should actually check with max queue size and not always blindly return false
        return false;
      }
    }
    return stream.isNotWritable();
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
  public Future<HttpVersion> sendHead() {
    Promise<HttpVersion> promise = context.promise();
    sendHead(promise);
    return promise.future();
  }

  @Override
  public synchronized HttpClientRequest sendHead(Handler<AsyncResult<HttpVersion>> headersHandler) {
    checkEnded();
    checkResponseHandler();
    if (stream != null) {
      throw new IllegalStateException("Head already written");
    } else {
      connect(headersHandler);
    }
    return this;
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
  public synchronized HttpClientRequest pushHandler(Handler<HttpClientRequest> handler) {
    pushHandler = handler;
    return this;
  }

  @Override
  boolean reset(Throwable cause) {
    HttpClientStream s;
    synchronized (this) {
      if (reset != null) {
        return false;
      }
      reset = cause;
      s = stream;
    }
    if (s != null) {
      s.reset(cause);
    } else {
      handleException(cause);
    }
    return true;
  }

  private void tryComplete() {
    endPromise.tryComplete();
  }

  @Override
  public synchronized HttpConnection connection() {
    return stream == null ? null : stream.connection();
  }

  @Override
  public HttpClientRequest writeCustomFrame(int type, int flags, Buffer payload) {
    HttpClientStream s;
    synchronized (this) {
      checkEnded();
      if ((s = stream) == null) {
        throw new IllegalStateException("Not yet connected");
      }
    }
    s.writeFrame(type, flags, payload.getByteBuf());
    return this;
  }

  void handleDrained() {
    Handler<Void> handler;
    synchronized (this) {
      handler =  drainHandler;
      if (handler == null || endFuture.isComplete()) {
        return;
      }
    }
    context.emit(handler);
  }

  private void handleNextRequest(HttpClientRequest next, Handler<AsyncResult<HttpClientResponse>> handler, long timeoutMs) {
    next.setHandler(handler);
    next.exceptionHandler(exceptionHandler());
    exceptionHandler(null);
    next.pushHandler(pushHandler);
    next.setMaxRedirects(followRedirects - 1);
    if (next.getAuthority() == null) {
      next.setAuthority(authority);
    }
    if (headers != null) {
      next.headers().addAll(headers);
    }
    endFuture.setHandler(ar -> {
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

  void handleContinue() {
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
      Future<HttpClientRequest> next = client.redirectHandler().apply(resp);
      if (next != null) {
        next.setHandler(ar -> {
          if (ar.succeeded()) {
            handleNextRequest(ar.result(), promise.future().getHandler(), timeoutMs);
          } else {
            fail(ar.cause());
          }
        });
        return;
      }
    }
    promise.complete(resp);
  }

  @Override
  protected String authority() {
    return authority != null ? authority : super.authority();
  }

  private synchronized void connect(Handler<AsyncResult<HttpVersion>> headersHandler) {
    if (!connecting) {
      SocketAddress peerAddress;
      if (authority != null) {
        int idx = authority.lastIndexOf(':');
        if (idx != -1) {
          peerAddress = SocketAddress.inetSocketAddress(Integer.parseInt(authority.substring(idx + 1)), authority.substring(0, idx));
        } else {
          peerAddress = SocketAddress.inetSocketAddress(80, authority);
        }
      } else {
        String peerHost = host;
        if (peerHost.endsWith(".")) {
          peerHost = peerHost.substring(0, peerHost.length() -  1);
        }
        peerAddress = SocketAddress.inetSocketAddress(port, peerHost);
      }

      // We defer actual connection until the first part of body is written or end is called
      // This gives the user an opportunity to set an exception handler before connecting so
      // they can capture any exceptions on connection
      connecting = true;
      client.getConnectionForRequest(context, peerAddress, this, netSocketPromise, ssl, server, ar -> {
        if (ar.succeeded()) {
          HttpClientStream stream = ar.result();
          // No need to synchronize as the thread is the same that set exceptionOccurred to true
          // exceptionOccurred=true getting the connection => it's a TimeoutException
          if (reset != null) {
            stream.reset(reset);
          } else {
            connected(headersHandler, stream);
          }
        } else {
          handleException(ar.cause());
        }
      });
    }
  }

  private void connected(Handler<AsyncResult<HttpVersion>> headersHandler, HttpClientStream stream) {
    synchronized (this) {
      this.stream = stream;

      // If anything was written or the request ended before we got the connection, then
      // we need to write it now

      if (pendingMaxSize != -1) {
        stream.doSetWriteQueueMaxSize(pendingMaxSize);
      }
      ByteBuf pending = pendingChunks;
      pendingChunks = null;
      Handler<AsyncResult<Void>> handler = null;
      if (pendingHandlers != null) {
        List<Handler<AsyncResult<Void>>> handlers = pendingHandlers;
        pendingHandlers = null;
        handler = ar -> {
          handlers.forEach(h -> h.handle(ar));
        };
      }
      if (headersHandler != null) {
        Handler<AsyncResult<Void>> others = handler;
        handler = ar -> {
          if (others != null) {
            others.handle(ar);
          }
          if (ar.succeeded()) {
            headersHandler.handle(Future.succeededFuture(stream.version()));
          } else {
            headersHandler.handle(Future.failedFuture(ar.cause()));
          }
        };
      }
      stream.writeHead(method, uri, headers, authority(), chunked, pending, ended, priority, handler);
      if (ended) {
        tryComplete();
      }
      this.connecting = false;
      this.stream = stream;
    }
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
    return !chunked && (headers == null || !headers.contains(CONTENT_LENGTH));
  }

  private void write(ByteBuf buff, boolean end, Handler<AsyncResult<Void>> completionHandler) {
    if (buff == null && !end) {
      return;
    }
    HttpClientStream s;
    synchronized (this) {
      if (ended) {
        completionHandler.handle(Future.failedFuture(new IllegalStateException("Request already complete")));
        return;
      }
      checkResponseHandler();
      if (end) {
        if (buff != null && requiresContentLength()) {
          headers().set(CONTENT_LENGTH, String.valueOf(buff.readableBytes()));
        }
      } else if (requiresContentLength()) {
        throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
      }
      ended |= end;
      if (stream == null) {
        if (buff != null) {
          if (pendingChunks == null) {
            pendingChunks = buff;
          } else {
            CompositeByteBuf pending;
            if (pendingChunks instanceof CompositeByteBuf) {
              pending = (CompositeByteBuf) pendingChunks;
            } else {
              pending = Unpooled.compositeBuffer();
              pending.addComponent(true, pendingChunks);
              pendingChunks = pending;
            }
            pending.addComponent(true, buff);
          }
        }
        if (completionHandler != null) {
          if (pendingHandlers == null) {
            pendingHandlers = new ArrayList<>();
          }
          pendingHandlers.add(completionHandler);
        }
        connect(null);
        return;
      }
      s = stream;
    }
    s.writeBuffer(buff, end, completionHandler);
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

  synchronized Handler<HttpClientRequest> pushHandler() {
    return pushHandler;
  }

  @Override
  public synchronized HttpClientRequest setStreamPriority(StreamPriority priority) {
    if (stream != null) {
      stream.updatePriority(priority);
    } else {
      this.priority = priority;
    }
    return this;
  }

  @Override
  public synchronized StreamPriority getStreamPriority() {
    HttpClientStream s = stream;
    return s != null ? s.priority() : priority;
  }
}
