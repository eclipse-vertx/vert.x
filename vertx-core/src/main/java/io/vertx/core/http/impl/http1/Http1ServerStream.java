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
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.streams.FailedWrite;
import io.vertx.core.internal.streams.WriteResult;
import io.vertx.core.internal.http.ContentEncoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.core.net.HostAndPort;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http1ServerStream extends Http1ServerRequest implements HttpServerStream, ContentEncoder, FlowController {

  private boolean headersSent;
  private HttpResponseHead head;
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
  void onException(Throwable t) {
    super.onException(t);
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      context.dispatch(t, handler);
    }
  }

  private void handleEnd(Void v) {
    super.exceptionHandler(null);
    requestEnded = true;
    Handler<MultiMap> handler = trailersHandler;
    if (handler != null) {
      context.dispatch(trailers(), handler);
    }
    checkCloseHandler();
  }

  @Override
  void onClosed() {
    super.onClosed();
    if (!closed) {
      closed = true;
      Handler<Void> handler = closeHandler;
      if (handler != null) {
        context.dispatch(null, handler);
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
  public Http1ServerStream head(HttpResponseHead head) {
    if (this.head != null || headersSent) {
      throw new IllegalStateException("Head already set");
    }
    this.head = head;
    return this;
  }

  private boolean sendHead() {
    HttpResponseHead head = this.head;
    if (head == null) {
      return false;
    }
    this.head = null;
    headersSent = true;
    Http1ServerResponse response = response();
    response.setStatusCode(head.statusCode);
    response.headers().setAll(head.headers);
    return true;
  }

  @Override
  public ContentEncoder contentEncoder() {
    return this;
  }

  @Override
  public WriteResult write(ByteBuf buf) {
    return write(buf, false);
  }

  @Override
  public WriteResult end(ByteBuf buf) {
    return write(buf, true);
  }

  /**
   * Ends the stream outbound content, as {@link ContentEncoder#end()}. This deliberately overrides the unrelated
   * {@link Http1ServerRequest#end()} (the end of the inbound request): this object is exposed either as a stream or
   * as a request, never as both. To be fixed eventually, by not inheriting the request.
   */
  @Override
  public WriteResult end() {
    return write(null, true);
  }

  private WriteResult write(ByteBuf buf, boolean end) {
    boolean written = false;
    try {
      if (!headersSent && head == null) {
        return new FailedWrite(context, "Headers must be sent prior sending a message");
      }
      if (responseEnded) {
        return new FailedWrite(context, "Response already ended");
      }
      try {
        boolean headOnly = sendHead() && !end && !buf.isReadable();
        Http1ServerResponse response = response();
        WriteResult result;
        if (end) {
          responseEnded = true;
          result = buf != null ? response.end(buf) : response.end();
        } else if (headOnly) {
          return response.writeHead();
        } else {
          result = response.write(buf);
        }
        written = true;
        return result;
      } finally {
        checkCloseHandler();
      }
    } finally {
      if (!written) {
        ReferenceCountUtil.release(buf);
      }
    }
  }

  @Override
  public WriteResult end(MultiMap headers) {
    if (!headersSent && head == null) {
      return new FailedWrite(context, "Headers must be sent prior sending a message");
    }
    if (responseEnded) {
      throw new IllegalStateException("Response already ended");
    }
    if (sendHead() && !headers.isEmpty()) {
      response().setChunked(true);
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
  public Http1ServerStream routed(String route) {
    return (Http1ServerStream)super.routed(route);
  }
}
