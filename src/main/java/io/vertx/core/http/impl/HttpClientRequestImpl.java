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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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

  private final VertxInternal vertx;
  private final Future<HttpClientResponse> respFut;
  private boolean chunked;
  private String hostHeader;
  private String rawMethod;
  private Handler<Void> continueHandler;
  private Handler<Void> drainHandler;
  private Handler<HttpClientRequest> pushHandler;
  private Handler<HttpConnection> connectionHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean completed;
  private Handler<Void> completionHandler;
  private Long reset;
  private ByteBuf pendingChunks;
  private int pendingMaxSize = -1;
  private int followRedirects;
  private long written;
  private VertxHttpHeaders headers;
  private StreamPriority priority;
  private HttpClientStream stream;
  private boolean connecting;

  // completed => drainHandler = null

  HttpClientRequestImpl(HttpClientImpl client, boolean ssl, HttpMethod method, String host, int port,
                        String relativeURI, VertxInternal vertx) {
    super(client, ssl, method, host, port, relativeURI);
    this.chunked = false;
    this.vertx = vertx;
    this.priority = HttpUtils.DEFAULT_STREAM_PRIORITY;
    this.respFut = Future.future();
  }

  @Override
  public void handleException(Throwable t) {
    super.handleException(t);
    Handler<Throwable> handler;
    synchronized (this) {
      exceptionOccurred = t;
      if (exceptionHandler != null) {
        handler = exceptionHandler;
      } else {
        handler = log::error;
      }
    }
    ContextInternal ctx;
    if (stream != null) {
      ctx = (ContextInternal) stream.getContext();
    } else {
      // Timeout callback
      // use this until we get a context per request
      ctx = (ContextInternal) Vertx.currentContext();
    }
    ctx.dispatch(t, handler);
    ctx.dispatch(t, respFut::tryFail);
  }

  @Override
  public int streamId() {
    HttpClientStream s;
    synchronized (this) {
      if ((s = stream) == null) {
        return -1;
      }
    }
    return s.id();
  }

  @Override
  public synchronized HttpClientRequest handler(Handler<AsyncResult<HttpClientResponse>> handler) {
    if (handler != null) {
      checkComplete();
    }
    respFut.setHandler(handler);
    return this;
  }

  @Override
  public HttpClientRequest setFollowRedirects(boolean followRedirects) {
    synchronized (this) {
      checkComplete();
      if (followRedirects) {
        this.followRedirects = client.getOptions().getMaxRedirects() - 1;
      } else {
        this.followRedirects = 0;
      }
      return this;
    }
  }

  @Override
  public HttpClientRequestImpl setChunked(boolean chunked) {
    synchronized (this) {
      checkComplete();
      if (written > 0) {
        throw new IllegalStateException("Cannot set chunked after data has been written on request");
      }
      // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
      if (client.getOptions().getProtocolVersion() != io.vertx.core.http.HttpVersion.HTTP_1_0) {
        this.chunked = chunked;
      }
      return this;
    }
  }

  @Override
  public synchronized boolean isChunked() {
    return chunked;
  }

  @Override
  public synchronized String getRawMethod() {
    return rawMethod;
  }

  @Override
  public synchronized HttpClientRequest setRawMethod(String method) {
    this.rawMethod = method;
    return this;
  }

  @Override
  public synchronized HttpClientRequest setHost(String host) {
    this.hostHeader = host;
    return this;
  }

  @Override
  public synchronized String getHost() {
    return hostHeader;
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
    checkComplete();
    headers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpClientRequest putHeader(String name, Iterable<String> values) {
    checkComplete();
    headers().set(name, values);
    return this;
  }

  @Override
  public HttpClientRequest setWriteQueueMaxSize(int maxSize) {
    HttpClientStream s;
    synchronized (this) {
      checkComplete();
      if ((s = stream) == null) {
        pendingMaxSize = maxSize;
        return this;
      }
    }
    s.doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    HttpClientStream s;
    synchronized (this) {
      checkComplete();
      if ((s = stream) == null) {
        // Should actually check with max queue size and not always blindly return false
        return false;
      }
    }
    return s.isNotWritable();
  }

  private synchronized Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  public synchronized HttpClientRequest exceptionHandler(Handler<Throwable> handler) {
    if (handler != null) {
      checkComplete();
      this.exceptionHandler = handler;
    } else {
      this.exceptionHandler = null;
    }
    return this;
  }

  @Override
  public HttpClientRequest drainHandler(Handler<Void> handler) {
    synchronized (this) {
      if (handler != null) {
        checkComplete();
        drainHandler = handler;
        HttpClientStream s;
        if ((s = stream) == null) {
          return this;
        }
        s.getContext().runOnContext(v -> {
          synchronized (HttpClientRequestImpl.this) {
            if (!stream.isNotWritable()) {
              handleDrained();
            }
          }
        });
      } else {
        drainHandler = null;
      }
      return this;
    }
  }

  @Override
  public synchronized HttpClientRequest continueHandler(Handler<Void> handler) {
    if (handler != null) {
      checkComplete();
    }
    this.continueHandler = handler;
    return this;
  }

  @Override
  public HttpClientRequest sendHead() {
    return sendHead(null);
  }

  @Override
  public synchronized HttpClientRequest sendHead(Handler<HttpVersion> headersHandler) {
    checkComplete();
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
    checkComplete();
    headers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values) {
    checkComplete();
    headers().set(name, values);
    return this;
  }

  @Override
  public synchronized HttpClientRequest pushHandler(Handler<HttpClientRequest> handler) {
    pushHandler = handler;
    return this;
  }

  @Override
  public boolean reset(long code) {
    HttpClientStream s;
    synchronized (this) {
      if (reset != null) {
        return false;
      }
      reset = code;
      if (tryComplete()) {
        if (completionHandler != null) {
          completionHandler.handle(null);
        }
      }
      s = stream;
    }
    if (s != null) {
      s.reset(code);
    }
    return true;
  }

  private boolean tryComplete() {
    if (!completed) {
      completed = true;
      drainHandler = null;
      exceptionHandler = null;
      return true;
    } else {
      return false;
    }
  }

  @Override
  public HttpConnection connection() {
    HttpClientStream s;
    synchronized (this) {
      if ((s = stream) == null) {
        return null;
      }
    }
    return s.connection();
  }

  @Override
  public synchronized HttpClientRequest connectionHandler(@Nullable Handler<HttpConnection> handler) {
    connectionHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpClientRequest writeCustomFrame(int type, int flags, Buffer payload) {
    HttpClientStream s;
    synchronized (this) {
      checkComplete();
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
      if ((handler = drainHandler) == null) {
        return;
      }
    }
    ((ContextInternal)stream.getContext()).dispatch(null, handler);
  }

  private void handleNextRequest(HttpClientRequestImpl next, long timeoutMs) {
    next.handler(respFut.getHandler());
    next.exceptionHandler(exceptionHandler());
    exceptionHandler(null);
    next.pushHandler = pushHandler;
    next.followRedirects = followRedirects - 1;
    next.written = written;
    if (next.hostHeader == null) {
      next.hostHeader = hostHeader;
    }
    if (headers != null && next.headers == null) {
      next.headers().addAll(headers);
    }
    Future<Void> fut = Future.future();
    fut.setHandler(ar -> {
      if (ar.succeeded()) {
        if (timeoutMs > 0) {
          next.setTimeout(timeoutMs);
        }
        next.end();
      } else {
        next.handleException(ar.cause());
      }
    });
    if (exceptionOccurred != null) {
      fut.fail(exceptionOccurred);
    }
    else if (completed) {
      fut.complete();
    } else {
      exceptionHandler(err -> {
        if (!fut.isComplete()) {
          fut.fail(err);
        }
      });
      completionHandler = v -> {
        if (!fut.isComplete()) {
          fut.complete();
        }
      };
    }
  }

  protected void doHandleResponse(HttpClientResponseImpl resp, long timeoutMs) {
    if (reset == null) {
      int statusCode = resp.statusCode();
      if (followRedirects > 0 && statusCode >= 300 && statusCode < 400) {
        Future<HttpClientRequest> next = client.redirectHandler().apply(resp);
        if (next != null) {
          next.setHandler(ar -> {
            if (ar.succeeded()) {
              handleNextRequest((HttpClientRequestImpl) ar.result(), timeoutMs);
            } else {
              handleException(ar.cause());
            }
          });
          return;
        }
      }
      if (statusCode == 100) {
        if (continueHandler != null) {
          ((ContextInternal)stream.getContext()).dispatch(continueHandler);
        }
      } else {
        ((ContextInternal)stream.getContext()).dispatch(Future.succeededFuture(resp), respFut);
      }
    }
  }

  @Override
  protected String hostHeader() {
    return hostHeader != null ? hostHeader : super.hostHeader();
  }

  private synchronized void connect(Handler<HttpVersion> headersHandler) {
    if (!connecting) {

      if (method == HttpMethod.OTHER && rawMethod == null) {
        throw new IllegalStateException("You must provide a rawMethod when using an HttpMethod.OTHER method");
      }

      String peerHost;
      if (hostHeader != null) {
        int idx = hostHeader.lastIndexOf(':');
        if (idx != -1) {
          peerHost = hostHeader.substring(0, idx);
        } else {
          peerHost = hostHeader;
        }
      } else {
        peerHost = host;
      }

      // Capture some stuff
      Handler<HttpConnection> h1 = connectionHandler;
      Handler<HttpConnection> h2 = client.connectionHandler();
      Handler<HttpConnection> initializer;
      if (h1 != null) {
        if (h2 != null) {
          initializer = conn -> {
            h1.handle(conn);
            h2.handle(conn);
          };
        } else {
          initializer = h1;
        }
      } else {
        initializer = h2;
      }
      ContextInternal connectCtx = vertx.getOrCreateContext();

      // We defer actual connection until the first part of body is written or end is called
      // This gives the user an opportunity to set an exception handler before connecting so
      // they can capture any exceptions on connection
      connecting = true;
      client.getConnectionForRequest(connectCtx, peerHost, ssl, port, host, ar1 -> {
        if (ar1.succeeded()) {
          HttpClientStream stream = ar1.result();
          ContextInternal ctx = (ContextInternal) stream.getContext();
          if (stream.id() == 1 && initializer != null) {
            ctx.dispatch(stream.connection(), initializer);
          }
          // No need to synchronize as the thread is the same that set exceptionOccurred to true
          // exceptionOccurred=true getting the connection => it's a TimeoutException
          if (exceptionOccurred != null || reset != null) {
            stream.reset(0);
          } else {
            connected(headersHandler, stream);
          }
        } else {
          connectCtx.executeFromIO(v -> {
            handleException(ar1.cause());
          });
        }
      });
    }
  }

  private void connected(Handler<HttpVersion> headersHandler, HttpClientStream stream) {
    synchronized (this) {
      this.stream = stream;
      stream.beginRequest(this);

      // If anything was written or the request ended before we got the connection, then
      // we need to write it now

      if (pendingMaxSize != -1) {
        stream.doSetWriteQueueMaxSize(pendingMaxSize);
      }

      if (pendingChunks != null) {
        ByteBuf pending = pendingChunks;
        pendingChunks = null;

        if (completed) {
          // we also need to write the head so optimize this and write all out in once
          stream.writeHead(method, rawMethod, uri, headers, hostHeader(), chunked, pending, true, priority);
          stream.reportBytesWritten(written);
          stream.endRequest();
        } else {
          stream.writeHead(method, rawMethod, uri, headers, hostHeader(), chunked, pending, false, priority);
        }
      } else {
        if (completed) {
          // we also need to write the head so optimize this and write all out in once
          stream.writeHead(method, rawMethod, uri, headers, hostHeader(), chunked, null, true, priority);
          stream.reportBytesWritten(written);
          stream.endRequest();
        } else {
          stream.writeHead(method, rawMethod, uri, headers, hostHeader(), chunked, null, false, priority);
        }
      }
      this.connecting = false;
      this.stream = stream;
    }
    if (headersHandler != null) {
      ContextInternal context = (ContextInternal) stream.getContext();
      context.dispatch(stream.version(), headersHandler);
    }
  }

  private boolean contentLengthSet() {
    return headers != null && headers().contains(CONTENT_LENGTH);
  }

  @Override
  public void end(String chunk) {
    end(Buffer.buffer(chunk));
  }

  @Override
  public void end(String chunk, String enc) {
    Objects.requireNonNull(enc, "no null encoding accepted");
    end(Buffer.buffer(chunk, enc));
  }

  @Override
  public void end(Buffer chunk) {
    write(chunk.getByteBuf(), true);
  }

  @Override
  public void end() {
    write(null, true);
  }

  @Override
  public HttpClientRequestImpl write(Buffer chunk) {
    ByteBuf buf = chunk.getByteBuf();
    write(buf, false);
    return this;
  }

  @Override
  public HttpClientRequestImpl write(String chunk) {
    return write(Buffer.buffer(chunk));
  }

  @Override
  public HttpClientRequestImpl write(String chunk, String enc) {
    Objects.requireNonNull(enc, "no null encoding accepted");
    return write(Buffer.buffer(chunk, enc));
  }

  private void write(ByteBuf buff, boolean end) {
    HttpClientStream s;
    synchronized (this) {
      checkComplete();
      checkResponseHandler();
      if (end) {
        if (buff != null && !chunked && !contentLengthSet()) {
          headers().set(CONTENT_LENGTH, String.valueOf(buff.readableBytes()));
        }
      } else {
        if (!chunked && !contentLengthSet()) {
          throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
            + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
        }
      }
      if (buff == null && !end) {
        // nothing to write to the connection just return
        return;
      }
      if (buff != null) {
        written += buff.readableBytes();
      }
      if ((s = stream) == null) {
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
        if (end) {
          tryComplete();
          if (completionHandler != null) {
            completionHandler.handle(null);
          }
        }
        connect(null);
        return;
      }
    }
    s.writeBuffer(buff, end);
    if (end) {
      s.reportBytesWritten(written); // MUST BE READ UNDER SYNCHRONIZATION
    }
    if (end) {
      Handler<Void> handler;
      synchronized (this) {
        tryComplete();
        s.endRequest();
        if ((handler = completionHandler) == null) {
          return;
        }
      }
      handler.handle(null);
    }
  }

  protected void checkComplete() {
    if (completed) {
      throw new IllegalStateException("Request already complete");
    }
  }

  private void checkResponseHandler() {
    if (stream == null && !connecting && respFut.getHandler() == null) {
      throw new IllegalStateException("You must set a response handler before connecting to the server");
    }
  }

  synchronized Handler<HttpClientRequest> pushHandler() {
    return pushHandler;
  }

  @Override
  public synchronized HttpClientRequest setStreamPriority(StreamPriority priority) {
    synchronized (this) {
      if (stream != null) {
        stream.updatePriority(priority);
      } else {
        this.priority = priority;
      }
    }
    return this;
  }

  @Override
  public synchronized StreamPriority getStreamPriority() {
    HttpClientStream s = stream;
    return s != null ? s.priority() : priority;
  }
}
