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
import io.vertx.core.http.impl.HttpFrameImpl;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.impl.VertxHandler;

public abstract class Http3Stream<S extends Http3Stream<S, C>, C extends Http3Connection> {

  // Uses HTTP/2 STUFF FOR NOW
  static final HttpResponseHeaders EMPTY = new HttpResponseHeaders(EmptyHttp2Headers.INSTANCE);

  final ContextInternal context;
  final C connection;
  final QuicStreamInternal stream;

  private boolean headReceived;
  private boolean resetSent;
  private Http3Headers headers;
  private Handler<MultiMap> trailersHandler;
  private Handler<Buffer> dataHandler;
  private Handler<HttpFrame> unknownFrameHandler;
  private Handler<Long> resetHandler;
  private Handler<Void> closeHandler;
  private Handler<Throwable> exceptionHandler;

  public Http3Stream(C connection, QuicStreamInternal stream, ContextInternal context) {
    this.stream = stream;
    this.context = context;
    this.connection = connection;
  }

//  private boolean abruptlyClosed() {
//    // todo : check stream reset status as well
//    return connection.isClosed();
//  }

  protected int handleHead(Http3Headers headers) {
    return 0;
  }

  protected void handleHeaders(Http3Headers headers) {
  }

  protected void handleUnknownFrame(long type, Buffer buffer) {
    Handler<HttpFrame> handler = unknownFrameHandler;
    if (handler != null) {
      handler.handle(new HttpFrameImpl((int)type, 0, buffer));
    }
  }

  protected void handleData(Buffer buffer) {
    Handler<Buffer> handler = dataHandler;
    if (handler != null) {
      handler.handle(buffer);
    }
  }

  protected void handleReset(int code) {
    Handler<Long> handler = resetHandler;
    if (handler != null) {
      handler.handle((long)code);
    }
  }

  protected void handleEnd() {
//    boolean closed = abruptlyClosed();
//    if (!closed) {
    Handler<MultiMap> handler = trailersHandler;
    if (handler != null) {
      MultiMap trailers;
      if (headers != null) {
        trailers = new HttpHeaders(headers);
      } else {
        trailers = EMPTY;
      }
      handler.handle(trailers);
    }
//    }
  }

  protected void handleClose() {
//    boolean closed = abruptlyClosed();
//    if (closed) {
//      handleException(HttpUtils.CONNECTION_CLOSED_EXCEPTION);
//    }
    Handler<Void> handler = closeHandler;
    if (handler != null) {
      handler.handle(null);
    }
  }

  protected void handleException(Throwable e) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      handler.handle(e);
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
                switch (handleHead(http3HeadersFrame.headers())) {
                  case 0:
                    // Invalid - not yet implemented
                    headReceived = true;
                    break;
                  case 1:
                    // 1xx
                    break;
                  case 2:
                    headReceived = true;
                    break;
                }
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
    stream.resetHandler(code -> {
      handleReset(code);
    });
    stream.endHandler(v -> {
      handleEnd();
    });
    stream.closeHandler(v -> {
      handleClose();
    });
    stream.shutdownHandler(v -> {
      // Not used at the moment
    });
    stream.closeHandler(v -> {
      connection.unregisterStream(this);
    });
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

  Future<Void> writeHeaders(Http3Headers headers, Buffer chunk, boolean end) {
    Http3HeadersFrame frame = new DefaultHttp3HeadersFrame(headers);
    Future<Void> fut = stream.writeMessage(frame);
    if (chunk != null) {
      fut = stream.writeMessage(new DefaultHttp3DataFrame(((BufferInternal)chunk).getByteBuf()));
    }
    if (end) {
      fut = stream.end();
    }
    return fut;
  }

  public Future<Void> writeChunk(Buffer chunk, boolean end) {
    Future<Void> fut;
    if (chunk != null) {
      fut = stream.writeMessage(new DefaultHttp3DataFrame(((BufferInternal)chunk).getByteBuf()));
    } else {
      fut = null;
    }
    if (end) {
      fut = stream.end();
    }
    return fut;
  }

  public Future<Void> writeReset(long code) {
    if (resetSent) {
      return context.failedFuture("Stream already reset");
    }
    resetSent = true;
    // Should we call the exception handler ????
    return stream.reset((int)code);
  }

  public final int id() {
    return (int)stream.id();
  }

  public final ContextInternal context() {
    return context;
  }
}
