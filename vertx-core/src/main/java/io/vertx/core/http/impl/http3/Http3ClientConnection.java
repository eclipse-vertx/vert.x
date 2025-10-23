package io.vertx.core.http.impl.http3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3RequestStreamInitializer;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpClientRequestImpl;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.HostAndPort;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class Http3ClientConnection extends Http3Connection implements HttpClientConnection {

  public Http3ClientConnection(QuicConnectionInternal connection) {
    super(connection);
  }

  public void init() {

    Http3ClientConnectionHandler http3Handler = new Http3ClientConnectionHandler();

    ChannelPipeline pipeline = connection.channelHandlerContext().pipeline();

    pipeline.addBefore("handler", "http3", http3Handler);

  }

  @Override
  public MultiMap newHttpRequestHeaders() {
    return new HttpRequestHeaders(new DefaultHttp3Headers());
  }

  @Override
  public long activeStreams() {
    return 0;
  }

  @Override
  public long concurrency() {
    // For now hardcode
    return 10;
  }

  @Override
  public HostAndPort authority() {
    return HostAndPort.authority("localhost", 8443);
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    return null;
  }

  @Override
  public HttpClientConnection invalidMessageHandler(Handler<Object> handler) {
    return null;
  }

  @Override
  public HttpClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    return null;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return null;
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    return connection.createStream(true, new Function<Consumer<QuicStreamChannel>, ChannelInitializer<QuicStreamChannel>>() {
      @Override
      public ChannelInitializer<QuicStreamChannel> apply(Consumer<QuicStreamChannel> quicStreamChannelConsumer) {
        return new Http3RequestStreamInitializer() {
          @Override
          protected void initRequestStream(QuicStreamChannel ch) {
            quicStreamChannelConsumer.accept(ch);
          }
        };
      }
    }).map(stream -> {
      QuicStreamInternal streamInternal = (QuicStreamInternal) stream;
      Http3ClientStream http3Stream = new Http3ClientStream(this, streamInternal);
      http3Stream.init();
      return http3Stream;
    });
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public Object metric() {
    return null;
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return 0;
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
