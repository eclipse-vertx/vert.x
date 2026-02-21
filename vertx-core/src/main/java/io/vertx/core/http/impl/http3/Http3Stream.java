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
package io.vertx.core.http.impl.http3;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http3.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpFrameImpl;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.http.impl.observability.StreamObserver;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.impl.VertxHandler;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Http3Stream<S extends Http3Stream<S, C>, C extends Http3Connection> {

  // Uses HTTP/2 instance for the moment
  static final HttpResponseHeaders EMPTY = new HttpResponseHeaders(EmptyHttp2Headers.INSTANCE);

  private final StreamObserver observer;
  final ContextInternal context;
  final C connection;
  final QuicStreamInternal stream;

  private boolean headReceived;
  private boolean headWritten;
  private boolean inboundReceived;
  private boolean outboundReceived;
  private boolean reset;
  private boolean aborted;
  private int bytesRead;
  private int bytesWritten;
  private Http3Headers headers;
  private Handler<MultiMap> trailersHandler;
  private Handler<Buffer> dataHandler;
  private Handler<HttpFrame> unknownFrameHandler;
  private Handler<Long> resetHandler;
  private Handler<Void> closeHandler;
  private Handler<Throwable> exceptionHandler;

  public Http3Stream(C connection, QuicStreamInternal stream, ContextInternal context, StreamObserver observer) {
    this.stream = stream;
    this.context = context;
    this.connection = connection;
    this.observer = observer;
  }

  protected HttpHeaders headOf(Http3Headers headers) {
    return null;
  }

  protected boolean handleHead(HttpHeaders headers) {
    if (headers.validate()) {
      if (observer != null) {
        observer.observeInboundHeaders(headers);
      }
      return true;
    } else {
      throw new UnsupportedOperationException();
    }
  }

  protected void handleHeaders(Http3Headers headers) {
  }

  protected void handleUnknownFrame(long type, Buffer buffer) {
    Handler<HttpFrame> handler = unknownFrameHandler;
    if (handler != null) {
      context.dispatch(new HttpFrameImpl((int)type, 0, buffer), handler);
    }
  }

  protected void handleData(Buffer buffer) {
    bytesRead += buffer.length();
    Handler<Buffer> handler = dataHandler;
    if (handler != null) {
      context.dispatch(buffer,  handler);
    }
  }

  protected void handleReset(long code) {
    Handler<Long> handler = resetHandler;
    if (handler != null) {
      context.dispatch(code, handler);
    }
  }

  protected void handleEnd() {
    inboundReceived = true;
    if (observer != null) {
      observer.observeInboundTrailers(bytesRead());
    }
    Handler<MultiMap> handler = trailersHandler;
    if (handler != null) {
      MultiMap trailers;
      if (headers != null) {
        trailers = new HttpHeaders(headers);
      } else {
        trailers = EMPTY;
      }
      context.dispatch(trailers, handler);
    }
  }

  protected void handleClose() {
    if ((headWritten || headReceived) && (!outboundReceived || !inboundReceived) && observer != null) {
      observer.observeReset();
    }
    if (outboundReceived) {
      if (aborted) {
        handleException(HttpUtils.CONNECTION_CLOSED_EXCEPTION);
      } else if (inboundReceived) {
        //
      } else {
        //
      }
    } else {
      handleException(HttpUtils.CONNECTION_CLOSED_EXCEPTION);
    }
    Handler<Void> handler = closeHandler;
    if (handler != null) {
      context.dispatch(null, handler);
    }
  }

  protected void handleException(Throwable e) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      context.dispatch(e, handler);
    }
  }

  void init() {
    stream.messageHandler(msg -> {
      ByteBuf buffer;
      ByteBuf content;
      if (msg instanceof Http3Frame) {
        Http3Frame http3Frame = (Http3Frame) msg;
        try {
          switch ((int)http3Frame.type()) {
            case 0x01:
              // Headers frame
              Http3HeadersFrame http3HeadersFrame = (Http3HeadersFrame) http3Frame;
              if (!headReceived) {
                HttpHeaders headers = headOf(http3HeadersFrame.headers());
                headReceived = handleHead(headers);
              } else {
                headers = http3HeadersFrame.headers();
                handleHeaders(http3HeadersFrame.headers());
              }
              break;
            case 0x00:
              // Data frame
              Http3DataFrame http3DataFrame = (Http3DataFrame) http3Frame;
              content = http3DataFrame.content();
              buffer = VertxHandler.copyBuffer(content);
              handleData(BufferInternal.buffer(buffer));
              break;
            default:
              if (http3Frame instanceof Http3UnknownFrame) {
                Http3UnknownFrame unknownFrame = (Http3UnknownFrame)http3Frame;
                content = unknownFrame.content();
                buffer = VertxHandler.copyBuffer(content);
                handleUnknownFrame(unknownFrame.type(), BufferInternal.buffer(buffer));
              } else {
                System.out.println("Frame type " + http3Frame.type() + " not implemented");
              }
              break;
          }
        } finally {
          ReferenceCountUtil.release(http3Frame);
        }
      }
    });
    stream.idleHandler(event -> {
      stream.idleHandler(event2 -> {});
      stream.shutdownHandler(null);
      cancel();
      stream.close();
    });
    stream.resetHandler(code -> {
      handleReset(code);
    });
    stream.endHandler(v -> {
      handleEnd();
    });
    stream.closeHandler(v -> {
      connection.unregisterStream(this);
      handleClose();
    });
    stream.shutdownHandler(v -> {
      // Not used at the moment
    });

    connection.setupFrameLogger(stream);
  }

  public final S customFrameHandler(Handler<HttpFrame> handler) {
    unknownFrameHandler = handler;
    return (S)this;
  }

  public final S trailersHandler(Handler<MultiMap> handler) {
    this.trailersHandler = handler;
    return (S)this;
  }

  public final S dataHandler(Handler<Buffer> handler) {
    this.dataHandler = handler;
    return (S)this;
  }

  public S resetHandler(Handler<Long> handler) {
    this.resetHandler = handler;
    return (S)this;
  }

  public S exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return (S)this;
  }

  public S closeHandler(Handler<Void> handler) {
    this.closeHandler = handler;
    return (S)this;
  }

  public final boolean isWritable() {
    return !stream.writeQueueFull();
  }

  public final S drainHandler(Handler<Void> handler) {
    stream.drainHandler(handler);
    return (S)this;
  }

  public final S setWriteQueueMaxSize(int maxSize) {
    stream.setWriteQueueMaxSize(maxSize);
    return (S)this;
  }

  public final S pause() {
    stream.pause();
    return (S)this;
  }

  public final S fetch(long amount) {
    stream.fetch(amount);
    return (S)this;
  }

  Future<Void> writeHeaders(HttpHeaders headers, Buffer chunk, boolean end) {
    if (outboundReceived) {
      throw new UnsupportedOperationException("handle me");
    }
    if (observer != null) {
      observer.observeOutboundHeaders(headers);
    }
    headWritten = true;
    Http3HeadersFrame frame = new DefaultHttp3HeadersFrame((Http3Headers) headers.unwrap());
    Future<Void> fut = stream.writeMessage(frame);
    if (chunk != null) {
      bytesWritten += chunk.length();
      fut = stream.writeMessage(new DefaultHttp3DataFrame(((BufferInternal)chunk).getByteBuf()));
    }
    if (end) {
      if (observer != null) {
        observer.observeOutboundTrailers(bytesWritten);
      }
      outboundReceived = true;
      fut = stream.end();
    }
    return fut;
  }

  public Future<Void> writeChunk(Buffer chunk, boolean end) {
    if (outboundReceived) {
      throw new UnsupportedOperationException("handle me");
    }
    Future<Void> fut;
    if (chunk != null) {
      bytesWritten += chunk.length();
      fut = stream.writeMessage(new DefaultHttp3DataFrame(((BufferInternal)chunk).getByteBuf()));
    } else {
      fut = null;
    }
    if (end) {
      outboundReceived = true;
      fut = stream.end();
      if (observer != null) {
        observer.observeOutboundTrailers(bytesWritten);
      }
    }
    return fut;
  }

  public Future<Boolean> cancel() {
    if (outboundReceived) {
      // Check inbound
      if (!aborted) {
        aborted = true;
        return stream.abort(Http3ErrorCode.H3_REQUEST_CANCELLED.code()).map(true);
      }
    } else {
      reset = true;
      return stream.reset(Http3ErrorCode.H3_REQUEST_CANCELLED.code()).map(true);
    }
    return context.succeededFuture(false);
  }

  public Future<Void> writeReset(long code) {
    if (outboundReceived) {
      return context.failedFuture("Stream already ended");
    }
    if (reset) {
      return context.failedFuture("Stream already reset");
    }
    reset = true;
    // Should we call the exception handler ????
    return stream.reset((int)code);
  }

  public final Object metric() {
    return observer != null ? observer.metric() : null;
  }

  public final HttpVersion version() {
    return HttpVersion.HTTP_3;
  }

  public final long id() {
    return stream.id();
  }

  public final long bytesWritten() {
    return bytesWritten;
  }

  public final long bytesRead() {
    return bytesRead;
  }

  public final ContextInternal context() {
    return context;
  }
}
