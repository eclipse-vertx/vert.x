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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http3.*;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.Http3Settings;
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

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Http3Connection implements HttpConnection {

  private final LongObjectMap<Http3Stream<?, ?>> streams;
  private final Http3FrameLogger frameLogger;
  final ContextInternal context;
  final QuicConnectionInternal connection;
  private QuicStreamInternal controlStream;
  private long mostRecentRemoteStreamId;
  private long remoteGoAway;
  private long localGoAway;
  private Handler<Void> shutdownHandler;
  private Handler<Void> closeHandler;
  private Http3Settings localSettings;
  private Http3Settings remoteSettings;
  private Handler<HttpSettings> remoteSettingsHandler;

  public Http3Connection(QuicConnectionInternal connection, Http3Settings localSettings, Http3FrameLogger frameLogger) {
    this.streams = new LongObjectHashMap<>();
    this.frameLogger = frameLogger;
    this.context = connection.context();
    this.connection = connection;
    this.remoteGoAway = -1L;
    this.localGoAway = -1L;
    this.localSettings = localSettings;
  }

  io.netty.handler.codec.http3.Http3Settings nettyLocalSettings() {
    io.netty.handler.codec.http3.Http3Settings nSettings = new io.netty.handler.codec.http3.Http3Settings();
    Long val;
    if ((val = localSettings.get(Http3Settings.MAX_FIELD_SECTION_SIZE)) != null) {
      nSettings.maxFieldSectionSize(val);
    }
    if ((val = localSettings.get(Http3Settings.QPACK_BLOCKED_STREAMS)) != null) {
      nSettings.qpackBlockedStreams(val);
    }
    if ((val = localSettings.get(Http3Settings.QPACK_MAX_TABLE_CAPACITY)) != null) {
      nSettings.qpackMaxTableCapacity(val);
    }
    return nSettings;
  }

  void registerStream(Http3Stream<?, ?> stream) {
    streams.put(stream.id(), stream);
  }

  void unregisterStream(Http3Stream<?, ?> stream) {
    streams.remove(stream.id());
  }

  void setupFrameLogger(QuicStreamInternal quicStream) {
    if (frameLogger != null) {
      ChannelHandlerContext chctx = quicStream.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("handler", "logging", frameLogger);
    }
  }

  void handleRequestStream(QuicStreamInternal quicStream) {
  }

  protected void handleOutboundControlStream(QuicStreamChannel  quicStream) {
    if (frameLogger != null) {
      ChannelPipeline pipeline = quicStream.pipeline();
      for (Map.Entry<String, ChannelHandler> handler : pipeline) {
        String name = handler.getValue().getClass().getName();
        if (name.equals("io.netty.handler.codec.http3.Http3FrameCodec")) {
          pipeline.addAfter(handler.getKey(), "logging", frameLogger);
          break;
        }
      }
    }
  }

  protected void handleInboundControlStream(QuicStreamInternal  quicStream) {
    setupFrameLogger(quicStream);
    quicStream.idleHandler(idle -> {
      // Keep stream alive
    });
    quicStream.messageHandler(msg -> {
      if (msg instanceof Http3ControlStreamFrame) {
        if (msg instanceof Http3GoAwayFrame) {
          Http3GoAwayFrame goAwayFrame = (Http3GoAwayFrame)msg;
          handleGoAway(goAwayFrame.id());
        } else if (msg instanceof Http3SettingsFrame) {
          Http3SettingsFrame settingsFrame = (Http3SettingsFrame)msg;
          handleSettings(settingsFrame.settings());
        }
      } else {
        System.out.println("Unhandled message " + msg);
      }
    });
  }

  public void init() {
    connection.handler(stream -> {
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
          handleRequestStream(quicStream);
        } else {
          quicStream.reset(Http3ErrorCode.H3_REQUEST_REJECTED.code());
        }
      } else {
        controlStream = quicStream;
        handleInboundControlStream(quicStream);
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
      handleClosed();
    });

    QuicStreamChannel inboundControlStream = Http3.getLocalControlStream(connection.channelHandlerContext().channel());
    handleOutboundControlStream(inboundControlStream);
  }

  @Override
  public HttpVersion protocolVersion() {
    return HttpVersion.HTTP_3;
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
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    throw new UnsupportedOperationException("This method should not be called");
  }

  private void handleGoAway(long id) {
    remoteGoAway = id;
    // Should cancel streams...
    Handler<Void> handler = shutdownHandler;
    if (handler != null) {
      context.dispatch(handler);
    }
  }

  private void handleSettings(io.netty.handler.codec.http3.Http3Settings nSettings) {
    Http3Settings vSettings = new Http3Settings();
    for (Map.Entry<Long, Long> setting : nSettings) {
      Http3SettingIdentifier wellKnownSetting = Http3SettingIdentifier.fromId(setting.getKey());
      if (wellKnownSetting != null) {
        HttpSetting<?> vSetting;
        switch (wellKnownSetting) {
          case HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS:
            vSetting = Http3Settings.QPACK_BLOCKED_STREAMS;
            break;
          case HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY:
            vSetting = Http3Settings.QPACK_MAX_TABLE_CAPACITY;
            break;
          case HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE:
            vSetting = Http3Settings.MAX_FIELD_SECTION_SIZE;
            break;
          default:
            continue;
        }
        vSettings.setLong(vSetting, setting.getValue());
      }
    }
    remoteSettings = vSettings;
    Handler<HttpSettings> handler = remoteSettingsHandler;
    if (handler != null) {
      context.dispatch(vSettings, handler);
    }
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("Timeout must be >= 0");
    }
    return connection.shutdown(timeout);
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

  protected void handleClosed() {
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
      stream.cancel();
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
  public HttpSettings settings() {
    return localSettings.copy();
  }

  @Override
  public Future<Void> updateSettings(HttpSettings settings) {
    return context.failedFuture("HTTP/3 settings cannot be updated");
  }

  @Override
  public HttpSettings remoteSettings() {
    Http3Settings settings = remoteSettings;
    return settings != null ? settings.copy() : null;
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<HttpSettings> handler) {
    remoteSettingsHandler = handler;
    return this;
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
