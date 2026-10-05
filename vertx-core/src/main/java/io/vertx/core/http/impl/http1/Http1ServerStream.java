/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http1;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.stream.ChunkedInput;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.headers.Http1xHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.core.net.HostAndPort;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http1ServerStream extends Http1ServerRequest implements HttpServerStream, FlowController {

  private boolean headersSent;
  private Handler<HttpRequestHead> headHandler;
  private Handler<MultiMap> trailersHandler;
  private Handler<Void> closeHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean requestEnded;
  private boolean responseEnded;
  private boolean closed;

  public Http1ServerStream(Http1ServerConnection conn, HttpRequest request, ContextInternal context) {
    super(conn, request, context);
  }

  void init() {
    super.endHandler(this::handleEnd);
  }

  void handleHead() {
    Handler<HttpRequestHead> h = headHandler;
    if (h != null) {
      h.handle(new HttpRequestHead(scheme(), method(), uri(), headers(), authority(), null));
    }
  }

  @Override
  void handleException(Throwable t) {
    super.handleException(t);
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      handler.handle(t);
    }
    if (t instanceof HttpClosedException) {
      handleClose();
    }
  }

  private void handleEnd(Void v) {
    super.exceptionHandler(null);
    requestEnded = true;
    Handler<MultiMap> handler = trailersHandler;
    if (handler != null) {
      context.dispatch(Http1xHeaders.caseInsensitive(), handler);
    }
    checkCloseHandler();
  }

  private void handleClose() {
    if (!closed) {
      closed = true;
      Handler<Void> handler = closeHandler;
      if (handler != null) {
        handler.handle(null);
      }
    }
  }

  private void checkCloseHandler() {
    if (!closed && requestEnded && responseEnded) {
      closed = true;
      Handler<Void> handler = closeHandler;
      if (handler != null) {
        handler.handle(null);
      }
    }
  }

  @Override
  public Future<Void> writeHead(HttpResponseHead head, Buffer chunk, boolean end) {
    if (headersSent) {
      return context.failedFuture("Headers already sent");
    }
    if (responseEnded) {
      throw new IllegalStateException("Response already ended");
    }
    headersSent = true;
    try {
      Http1ServerResponse response = response();
      response.setStatusCode(head.statusCode);
      response.headers().setAll(head.headers);
      responseEnded = end;
      if (chunk.length() > 0) {
        if (end) {
          return response.end(chunk);
        } else {
          return response.write(chunk);
        }
      } else {
        if (end) {
          return response.end();
        } else {
          return response.writeHead();
        }
      }
    } finally {
      checkCloseHandler();
    }
  }

  @Override
  public Future<Void> write(Buffer buf, boolean end) {
    if (!headersSent) {
      return context.failedFuture("Headers must be sent prior sending a message");
    }
    if (responseEnded) {
      throw new IllegalStateException("Response already ended");
    }
    try {
      Http1ServerResponse response = response();
      if (buf.length() == 0) {
        if (end) {
          responseEnded = true;
          return response.end();
        } else {
          return context.succeededFuture();
        }
      } else {
        if (end) {
          responseEnded = true;
          return response.end(buf);
        } else {
          return response.write(buf);
        }
      }
    } finally {
      checkCloseHandler();
    }
  }

  @Override
  public Future<Void> end(MultiMap headers) {
    if (!headersSent) {
      return context.failedFuture("Headers must be sent prior sending a message");
    }
    if (responseEnded) {
      throw new IllegalStateException("Response already ended");
    }
    try {
      responseEnded = true;
      response().trailers().setAll(headers);
      return response().end();
    } finally {
      checkCloseHandler();
    }
  }

  @Override
  public HttpServerStream headHandler(Handler<HttpRequestHead> handler) {
    this.headHandler = handler;
    return this;
  }

  @Override
  public HttpServerStream resetHandler(Handler<Long> handler) {
    return this;
  }

  @Override
  public HttpServerStream trailersHandler(Handler<MultiMap> handler) {
    this.trailersHandler = handler;
    return this;
  }

  @Override
  public HttpServerStream closeHandler(Handler<Void> handler) {
    this.closeHandler = handler;
    return this;
  }

  @Override
  public Http1ServerStream exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public HttpServerStream drainHandler(Handler<Void> handler) {
    response().drainHandler(handler);
    return this;
  }

  @Override
  public HttpServerStream setWriteQueueMaxSize(int maxSize) {
    response().setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public void sendFile(ChunkedInput<ByteBuf> file, Promise<Void> promise) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerStream updatePriority(StreamPriority streamPriority) {
    return this;
  }

  @Override
  public Future<Void> writeReset(long code) {
    return response().reset(code);
  }

  @Override
  public Future<Boolean> cancel() {
    return writeReset(0x8).map(true);
  }

  @Override
  public boolean isWritable() {
    return !response().writeQueueFull();
  }

  @Override
  public StreamPriority priority() {
    return streamPriority();
  }

  @Override
  public Future<Void> writeFrame(int type, int flags, Buffer payload) {
    return context.failedFuture("HTTP/1 does not support custom frames");
  }

  @Override
  public Future<HttpServerStream> sendPush(HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority priority) {
    return context.failedFuture("HTTP/1 does not support server push");
  }

  @Override
  public HttpServerStream priorityChangeHandler(Handler<StreamPriority> handler) {
    return (HttpServerStream) super.streamPriorityHandler(handler);
  }

  @Override
  public Http1ServerStream resume() {
    return (Http1ServerStream)super.resume();
  }

  @Override
  public Http1ServerStream pause() {
    return (Http1ServerStream)super.pause();
  }

  @Override
  public Http1ServerStream fetch(long amount) {
    return (Http1ServerStream)super.fetch(amount);
  }

  @Override
  public Http1ServerStream customFrameHandler(Handler<HttpFrame> handler) {
    return (Http1ServerStream)super.customFrameHandler(handler);
  }

  @Override
  public Http1ServerStream contentDecoder(ContentDecoder decoder) {
    return (Http1ServerStream) super.contentDecoder(decoder);
  }

  @Override
  public Http1ServerStream routed(String route) {
    return (Http1ServerStream)super.routed(route);
  }
}
