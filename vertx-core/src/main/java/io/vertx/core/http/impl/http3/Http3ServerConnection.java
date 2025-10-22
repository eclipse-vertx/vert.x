package io.vertx.core.http.impl.http3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3ControlStreamFrame;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Http3ServerConnection extends Http3Connection implements HttpServerConnection {

  private Handler<HttpServerStream> streamHandler;

  public Http3ServerConnection(QuicConnectionInternal connection) {
    super(connection);
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
        Http3ServerStream http3Stream = new Http3ServerStream(this, quicStream);
        http3Stream.init();
        Handler<HttpServerStream> handler = streamHandler;
        handler.handle(http3Stream);
      } else {
        quicStream.messageHandler(msg -> {
          if (msg instanceof Http3ControlStreamFrame) {
            Http3ServerConnection.this.handle((Http3ControlStreamFrame) msg);
          } else {
            System.out.println("Unhandled message " + msg);
          }
        });
      }
    });

    Http3ServerConnectionHandler http3Handler = new Http3ServerConnectionHandler(
      new ChannelInitializer<QuicStreamChannel>() {
        @Override
        protected void initChannel(QuicStreamChannel ch) {
          // Nothing to do
        }
      });

    ChannelPipeline pipeline = connection.channelHandlerContext().pipeline();
    pipeline.addBefore("handler", "http3", http3Handler);
  }

  private void handle(Http3ControlStreamFrame  http3Frame) {

  }

  @Override
  public HttpServerConnection streamHandler(Handler<HttpServerStream> handler) {
    streamHandler = handler;
    return this;
  }

  @Override
  public Headers<CharSequence, CharSequence, ?> newHeaders() {
    return new DefaultHttp3Headers();
  }

  @Override
  public boolean supportsSendFile() {
    return false;
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return connection.channelHandlerContext();
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    return null;
  }

  @Override
  public HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler) {
    return null;
  }

  @Override
  public HttpConnection shutdownHandler(@Nullable Handler<Void> handler) {
    return null;
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    return null;
  }

  @Override
  public HttpConnection closeHandler(Handler<Void> handler) {
    return null;
  }

  @Override
  public Http2Settings settings() {
    return null;
  }

  @Override
  public Future<Void> updateSettings(Http2Settings settings) {
    return null;
  }

  @Override
  public Http2Settings remoteSettings() {
    return null;
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    return null;
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    return null;
  }

  @Override
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    return null;
  }

  @Override
  public HttpConnection exceptionHandler(Handler<Throwable> handler) {
    return null;
  }

  @Override
  public String indicatedServerName() {
    return "";
  }
}
