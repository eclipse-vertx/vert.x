package io.vertx.core.http.impl.http3;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http3.DefaultHttp3GoAwayFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ControlStreamFrame;
import io.netty.handler.codec.http3.Http3GoAwayFrame;
import io.netty.handler.codec.quic.QuicStreamChannel;
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
import io.vertx.core.net.QuicConnectionClose;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLSession;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class Http3Connection implements HttpConnection {

  final ContextInternal context;
  final QuicConnectionInternal connection;
  private QuicStreamInternal controlStream;
  private Future<Void> shutdownFuture;
  private long mostRecentRemoteStreamId;
  private long remoteGoAway;
  private long localGoAway;
  private Handler<Void> shutdownHandler;


  public Http3Connection(QuicConnectionInternal connection) {
    this.context = connection.context();
    this.connection = connection;
    this.remoteGoAway = -1L;
    this.localGoAway = -1L;
  }

  void handleHttpStream(QuicStreamInternal quicStream) {
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
        mostRecentRemoteStreamId = stream.id();
        handleHttpStream(quicStream);
      } else {
        controlStream = quicStream;
        handleControlStream(quicStream);
      }
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
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      goAway(lastStreamId, context.promise());
    } else {
      eventLoop.execute(() -> goAway(errorCode, lastStreamId, (Buffer)null));
    }
    return this;
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
    Promise<Void> promise = context.promise();
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      shutdown(Duration.ofMillis(unit.toMillis(timeout)), promise);
    } else {
      eventLoop.execute(() -> shutdown(Duration.ofMillis(unit.toMillis(timeout)), promise));
    }
    return promise.future();
  }

  private void shutdown(Duration timeout, Promise<Void> promise) {
    if (shutdownFuture != null) {
      shutdownFuture.onComplete(promise);
      return;
    }
    QuicStreamChannel localControlStream = Http3.getLocalControlStream(connection.channelHandlerContext().channel());
    if (localControlStream != null) {
      shutdownFuture = promise.future();
      if (localGoAway != -1L) {
        // Go away already sent
        connection
          .shutdown(timeout)
          .onComplete(promise);
      } else {
        PromiseInternal<Void> p = context.promise();
        goAway(mostRecentRemoteStreamId, p);
        p.onComplete(ar -> {
          connection
            .shutdown(timeout)
            .onComplete(promise);
        });
      }
    } else {
      promise.fail("No control stream");
    }
  }

  public void goAway(long lastStreamId, PromiseInternal<Void> promise) {
    QuicStreamChannel localControlStream = Http3.getLocalControlStream(connection.channelHandlerContext().channel());
    if (localControlStream != null) {
      if (localGoAway == -1 || lastStreamId < localGoAway) {
        localGoAway = lastStreamId;
        Http3GoAwayFrame frame = new DefaultHttp3GoAwayFrame(lastStreamId);
        ChannelFuture fut = localControlStream.writeAndFlush(frame);
        fut.addListener(promise);
      } else {
        promise.fail("Last stream id " + lastStreamId + " must be < " + localGoAway);
      }
    } else {
      promise.fail("No control stream");
    }
  }


  @Override
  public HttpConnection closeHandler(Handler<Void> handler) {
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
