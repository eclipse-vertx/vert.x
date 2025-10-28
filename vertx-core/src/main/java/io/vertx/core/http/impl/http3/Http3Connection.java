package io.vertx.core.http.impl.http3;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http3.*;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLSession;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class Http3Connection implements HttpConnection {

  private final LongObjectMap<Http3Stream<?, ?>> streams;
  final ContextInternal context;
  final QuicConnectionInternal connection;
  private QuicStreamInternal controlStream;
//  private boolean closed;
  private long mostRecentRemoteStreamId;
  private long remoteGoAway;
  private long localGoAway;
  private Handler<Void> shutdownHandler;
  private Handler<Void> closeHandler;

  public Http3Connection(QuicConnectionInternal connection) {
    this.streams = new LongObjectHashMap<>();
    this.context = connection.context();
    this.connection = connection;
    this.remoteGoAway = -1L;
    this.localGoAway = -1L;
  }

//  boolean isClosed() {
//    return closed;
//  }

  void handleStream(QuicStreamInternal quicStream) {
  }

  void registerStream(Http3Stream<?, ?> stream) {
    streams.put(stream.id(), stream);
  }

  void unregisterStream(Http3Stream<?, ?> stream) {
    streams.remove(stream.id());
  }

  private void handleControlStream(QuicStreamInternal  quicStream) {
    quicStream.messageHandler(msg -> {
      if (msg instanceof Http3ControlStreamFrame) {
        if (msg instanceof Http3GoAwayFrame) {
          Http3GoAwayFrame goAwayFrame = (Http3GoAwayFrame)msg;
          handleGoAwayFrame(goAwayFrame.id());
        }
      } else {
        System.out.println("Unhandled message " + msg);
      }
    });
  }

  public void init() {
    connection.streamHandler(stream -> {
      QuicStreamInternal quicStream = (QuicStreamInternal) stream;

      boolean isStream = false;
      for (Map.Entry<String, ?> e : quicStream.channelHandlerContext().pipeline()) {
        if (e.getValue().getClass().getSimpleName().equals("Http3FrameCodec")) {
          isStream = true;
          break;
        }
      }
      if (isStream) {
        if (localGoAway == -1L) {
          mostRecentRemoteStreamId = stream.id();
          handleStream(quicStream);
        } else {
          quicStream.reset(Http3ErrorCode.H3_REQUEST_REJECTED.code());
        }
      } else {
        controlStream = quicStream;
        handleControlStream(quicStream);
      }
    });
    connection.shutdownHandler(timeout -> {
      QuicStreamChannel localControlStream = Http3.getLocalControlStream(connection.channelHandlerContext().channel());
      if (localControlStream != null) {
        handleShutdown(localControlStream, timeout);
      }
    });
    connection.graceHandler(qcc -> {
      QuicStreamChannel localControlStream = Http3.getLocalControlStream(connection.channelHandlerContext().channel());
      if (localControlStream != null) {
        handleGrace(localControlStream);
      }
    });
    connection.closeHandler(v -> {
      handleClose();
    });
  }

  @Override
  public final SocketAddress remoteAddress() {
    return connection.remoteAddress();
  }

  @Override
  public final SocketAddress remoteAddress(boolean real) {
    return connection.remoteAddress();
  }

  @Override
  public final SocketAddress localAddress() {
    return connection.localAddress();
  }

  @Override
  public final SocketAddress localAddress(boolean real) {
    return connection.localAddress();
  }

  @Override
  public final boolean isSsl() {
    return true;
  }

  @Override
  public final SSLSession sslSession() {
    return connection.sslSession();
  }

  @Override
  public HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler) {
    return null;
  }

  @Override
  public HttpConnection shutdownHandler(@Nullable Handler<Void> handler) {
    shutdownHandler = handler;
    return this;
  }

  @Override
  public HttpConnection goAway(long errorCode) {
    return goAway(errorCode, -1);
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId) {
    return goAway(errorCode, lastStreamId, (Buffer)null);
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    throw new UnsupportedOperationException("This method should not be called");
//    EventLoop eventLoop = context.nettyEventLoop();
//    if (eventLoop.inEventLoop()) {
//      goAway(lastStreamId, context.promise());
//    } else {
//      eventLoop.execute(() -> goAway(errorCode, lastStreamId, null));
//    }
//    return this;
  }

  private void handleGoAwayFrame(long id) {
    remoteGoAway = id;
    // Should cancel streams...
    Handler<Void> handler = shutdownHandler;
    if (handler != null) {
      context.dispatch(handler);
    }
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    if (timeout < 0) {
      throw new IllegalArgumentException("Timeout must be >= 0");
    }
    return connection.shutdown(Duration.ofMillis(unit.toMillis(timeout)));
  }

  private void handleShutdown(QuicStreamChannel localControlStream, Duration timeout) {
    localGoAway = mostRecentRemoteStreamId + 4;
    PromiseInternal<Void> p = context.promise();
    if (remoteGoAway == -1L) {
      Handler<Void> handler = shutdownHandler;
      if (handler != null) {
        context.emit(null, handler);
      }
    }
    sendGoAway(localControlStream, mostRecentRemoteStreamId + 4, p);
  }

  private void handleGrace(QuicStreamChannel localControlStream) {
    if (localGoAway == -1L || localGoAway > 0L) {
      localGoAway = 0L;
      sendGoAway(localControlStream, 0, context.promise());
    }
  }

  private void handleClose() {
    Handler<Void> handler = closeHandler;
    if (handler != null) {
      context.emit(null, handler);
    }
  }

  private void sendGoAway(QuicStreamChannel controlStream, long streamId, PromiseInternal<Void> promise) {
    Iterator<LongObjectMap.PrimitiveEntry<Http3Stream<?, ?>>> iterator = streams.entries().iterator();
    List<Http3Stream<?, ?>> toCancel = new ArrayList<>();
    while (iterator.hasNext()) {
      LongObjectMap.PrimitiveEntry<Http3Stream<?, ?>> entry = iterator.next();
      if (entry.key() >= streamId) {
        toCancel.add(entry.value());
        // Should we remove ????
      }
    }
    for (Http3Stream<?, ?> stream : toCancel) {
      stream.cancel(Http3ErrorCode.H3_REQUEST_CANCELLED.code());
    }

    Http3GoAwayFrame frame = new DefaultHttp3GoAwayFrame(streamId);
    ChannelFuture fut = controlStream.writeAndFlush(frame);
    fut.addListener(promise);
  }


  @Override
  public HttpConnection closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return this;
  }

  @Override
  public Http2Settings settings() {
    throw new UnsupportedOperationException("Implement me");
  }

  @Override
  public Future<Void> updateSettings(Http2Settings settings) {
    return context.failedFuture("HTTP/3 settings cannot be updated");
  }

  @Override
  public Http2Settings remoteSettings() {
    throw new UnsupportedOperationException("Implement me");
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    return null;
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    return context.failedFuture("Ping not supported");
  }

  @Override
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    return this;
  }

  @Override
  public HttpConnection exceptionHandler(Handler<Throwable> handler) {
    return this;
  }
}
