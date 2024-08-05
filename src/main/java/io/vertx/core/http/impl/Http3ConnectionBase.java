package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.NetworkMetrics;

public class Http3ConnectionBase extends ConnectionBase implements HttpConnection {
  protected Http3ConnectionBase(ContextInternal context, ChannelHandlerContext chctx) {
    super(context, chctx);
  }

  @Override
  public int getWindowSize() {
    return HttpConnection.super.getWindowSize();
  }

  @Override
  public HttpConnection setWindowSize(int windowSize) {
    return HttpConnection.super.setWindowSize(windowSize);
  }

  @Override
  public HttpConnection goAway(long errorCode) {
    return HttpConnection.super.goAway(errorCode);
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId) {
    return HttpConnection.super.goAway(errorCode, lastStreamId);
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
  public void shutdown(Handler<AsyncResult<Void>> handler) {
    HttpConnection.super.shutdown(handler);
  }

  @Override
  public Future<Void> shutdown() {
    return HttpConnection.super.shutdown();
  }

  @Override
  public void shutdown(long timeout, Handler<AsyncResult<Void>> handler) {

  }

  @Override
  public Future<Void> shutdown(long timeoutMs) {
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
  public HttpConnection updateSettings(Http2Settings settings, Handler<AsyncResult<Void>> completionHandler) {
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
  public HttpConnection ping(Buffer data, Handler<AsyncResult<Buffer>> pongHandler) {
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
  public NetworkMetrics metrics() {
    return null;
  }

  @Override
  protected void handleInterestedOpsChanged() {

  }

  @Override
  public Http3ConnectionBase closeHandler(Handler<Void> handler) {
    return (Http3ConnectionBase)super.closeHandler(handler);
  }

  @Override
  public Http3ConnectionBase exceptionHandler(Handler<Throwable> handler) {
    return (Http3ConnectionBase)super.exceptionHandler(handler);
  }

  public VertxInternal vertx(){
    return this.vertx;
  }

  public void consumeCredits(QuicStreamChannel stream, int len) {
    throw new RuntimeException("Method not implemented");
  }
}
