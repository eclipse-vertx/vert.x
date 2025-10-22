/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.Http1xHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.ClientMetrics;

import javax.net.ssl.SSLSession;
import java.util.concurrent.TimeUnit;

/**
 * A connection that attempts to perform a protocol upgrade to H2C. The connection might use HTTP/1 or H2C
 * depending on the initial server response.
 */
public class Http2UpgradeClientConnection implements HttpClientConnection {

  // Try to remove that
  public static final Object SEND_BUFFERED_MESSAGES_EVENT = new Object();

  private static final Logger log = LoggerFactory.getLogger(Http2UpgradeClientConnection.class);

  private final long maxLifetimeMillis;
  private final Http2ChannelUpgrade upgrade;
  private final ClientMetrics<?, ?, ?> metrics;
  private HttpClientConnection current;
  private boolean upgradeProcessed;

  private Handler<Void> closeHandler;
  private Handler<Void> shutdownHandler;
  private Handler<GoAway> goAwayHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Buffer> pingHandler;
  private Handler<Void> evictionHandler;
  private Handler<Object> invalidMessageHandler;
  private Handler<Long> concurrencyChangeHandler;
  private Handler<Http2Settings> remoteSettingsHandler;

  Http2UpgradeClientConnection(Http1xClientConnection connection, long maxLifetimeMillis, ClientMetrics<?, ?, ?> metrics, Http2ChannelUpgrade upgrade) {
    this.current = connection;
    this.maxLifetimeMillis = maxLifetimeMillis;
    this.upgrade = upgrade;
    this.metrics = metrics;
  }

  public HttpClientConnection unwrap() {
    return current;
  }

  @Override
  public MultiMap newHttpRequestHeaders() {
    return Http1xHeaders.httpHeaders();
  }

  @Override
  public HostAndPort authority() {
    return current.authority();
  }

  @Override
  public long concurrency() {
    return upgradeProcessed ? current.concurrency() : 1L;
  }

  @Override
  public long activeStreams() {
    return current.concurrency();
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return current.channelHandlerContext();
  }

  @Override
  public Object metric() {
    return current.metric();
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return current.lastResponseReceivedTimestamp();
  }

  private static class DelegatingStream implements HttpClientStream {

    private final Http2UpgradeClientConnection connection;
    private final HttpClientStream delegate;

    DelegatingStream(Http2UpgradeClientConnection connection, HttpClientStream delegate) {
      this.connection = connection;
      this.delegate = delegate;
    }

    @Override
    public int id() {
      return delegate.id();
    }

    @Override
    public Object metric() {
      return delegate.metric();
    }

    @Override
    public Object trace() {
      return delegate.trace();
    }

    @Override
    public HttpVersion version() {
      return delegate.version();
    }

    @Override
    public HttpClientConnection connection() {
      return connection;
    }

    @Override
    public ContextInternal context() {
      return delegate.context();
    }

    @Override
    public Future<Void> writeHead(HttpRequestHead request, boolean chunked, Buffer buf, boolean end, StreamPriority priority, boolean connect) {
      return delegate.writeHead(request, chunked, buf, end, priority, connect);
    }

    @Override
    public Future<Void> writeChunk(Buffer buf, boolean end) {
      return delegate.writeChunk(buf, end);
    }

    @Override
    public Future<Void> writeFrame(int type, int flags, Buffer payload) {
      return delegate.writeFrame(type, flags, payload);
    }

    @Override
    public HttpClientStream continueHandler(Handler<Void> handler) {
      delegate.continueHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream earlyHintsHandler(Handler<MultiMap> handler) {
      delegate.earlyHintsHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream pushHandler(Handler<HttpClientPush> handler) {
      delegate.pushHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream customFrameHandler(Handler<HttpFrame> handler) {
      delegate.customFrameHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream headHandler(Handler<HttpResponseHead> handler) {
      delegate.headHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream dataHandler(Handler<Buffer> handler) {
      delegate.dataHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream trailersHandler(Handler<MultiMap> handler) {
      delegate.trailersHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream priorityChangeHandler(Handler<StreamPriority> handler) {
      delegate.priorityChangeHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream closeHandler(Handler<Void> handler) {
      delegate.closeHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream pause() {
      delegate.pause();
      return this;
    }

    @Override
    public HttpClientStream fetch(long amount) {
      delegate.fetch(amount);
      return this;
    }

    @Override
    public Future<Void> writeReset(long code) {
      return delegate.writeReset(code);
    }

    @Override
    public StreamPriority priority() {
      return delegate.priority();
    }

    @Override
    public HttpClientStream updatePriority(StreamPriority streamPriority) {
      delegate.updatePriority(streamPriority);
      return this;
    }

    @Override
    public HttpClientStream resetHandler(Handler<Long> handler) {
      delegate.resetHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream exceptionHandler(@Nullable Handler<Throwable> handler) {
      delegate.exceptionHandler(handler);
      return this;
    }

    @Override
    public HttpClientStream setWriteQueueMaxSize(int maxSize) {
      delegate.setWriteQueueMaxSize(maxSize);
      return this;
    }

    @Override
    public boolean isWritable() {
      return delegate.isWritable();
    }

    @Override
    public HttpClientStream drainHandler(@Nullable Handler<Void> handler) {
      delegate.drainHandler(handler);
      return this;
    }
  }

  /**
   * The first stream that will send the request using HTTP/1, upgrades the connection when the protocol
   * switches and receives the response with HTTP/2 frames.
   */
  private static class UpgradingStream implements HttpClientStream {

    void handleUpgrade(HttpClientConnection conn, HttpClientStream stream) {
      upgradedStream = stream;
      upgradedStream.headHandler(headHandler);
      upgradedStream.dataHandler(chunkHandler);
      upgradedStream.trailersHandler(trailersHandler);
      upgradedStream.priorityChangeHandler(priorityHandler);
      upgradedStream.exceptionHandler(exceptionHandler);
      upgradedStream.resetHandler(resetHandler);
      upgradedStream.drainHandler(drainHandler);
      upgradedStream.continueHandler(continueHandler);
      upgradedStream.earlyHintsHandler(earlyHintsHandler);
      upgradedStream.pushHandler(pushHandler);
      upgradedStream.customFrameHandler(unknownFrameHandler);
      upgradedStream.closeHandler(closeHandler);
      upgradingStream.headHandler(null);
      upgradingStream.dataHandler(null);
      upgradingStream.trailersHandler(null);
      upgradingStream.priorityChangeHandler(null);
      upgradingStream.exceptionHandler(null);
      upgradingStream.drainHandler(null);
      upgradingStream.continueHandler(null);
      upgradingStream.earlyHintsHandler(null);
      upgradingStream.pushHandler(null);
      upgradingStream.customFrameHandler(null);
      upgradingStream.closeHandler(null);
      headHandler = null;
      chunkHandler = null;
      trailersHandler = null;
      priorityHandler = null;
      exceptionHandler = null;
      resetHandler = null;
      drainHandler = null;
      continueHandler = null;
      earlyHintsHandler = null;
      pushHandler = null;
      closeHandler = null;
      upgradedConnection.current = conn;
      conn.closeHandler(upgradedConnection.closeHandler);
      conn.exceptionHandler(upgradedConnection.exceptionHandler);
      conn.pingHandler(upgradedConnection.pingHandler);
      conn.goAwayHandler(upgradedConnection.goAwayHandler);
      conn.shutdownHandler(upgradedConnection.shutdownHandler);
      conn.remoteSettingsHandler(upgradedConnection.remoteSettingsHandler);
      conn.evictionHandler(upgradedConnection.evictionHandler);
      conn.concurrencyChangeHandler(upgradedConnection.concurrencyChangeHandler);
      conn.invalidMessageHandler(upgradedConnection.invalidMessageHandler);
      Handler<Long> concurrencyChangeHandler = upgradedConnection.concurrencyChangeHandler;
      upgradedConnection.closeHandler = null;
      upgradedConnection.exceptionHandler = null;
      upgradedConnection.pingHandler = null;
      upgradedConnection.goAwayHandler = null;
      upgradedConnection.shutdownHandler = null;
      upgradedConnection.remoteSettingsHandler = null;
      upgradedConnection.evictionHandler = null;
      upgradedConnection.concurrencyChangeHandler = null;
      upgradedConnection.invalidMessageHandler = null;
      concurrencyChangeHandler.handle(conn.concurrency());
    }

    private final Http1xClientConnection upgradingConnection;
    private final Http2ChannelUpgrade upgrade;
    private final HttpClientStream upgradingStream;
    private final Http2UpgradeClientConnection upgradedConnection;
    private final long maxLifetimeMillis;
    private final ClientMetrics<?, ?, ?> metrics;
    private HttpClientStream upgradedStream;
    private Handler<HttpResponseHead> headHandler;
    private Handler<Buffer> chunkHandler;
    private Handler<MultiMap> trailersHandler;
    private Handler<StreamPriority> priorityHandler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Long> resetHandler;
    private Handler<Void> drainHandler;
    private Handler<Void> continueHandler;
    private Handler<MultiMap> earlyHintsHandler;
    private Handler<HttpClientPush> pushHandler;
    private Handler<HttpFrame> unknownFrameHandler;
    private Handler<Void> closeHandler;

    UpgradingStream(HttpClientStream stream, Http2UpgradeClientConnection upgradedConnection, long maxLifetimeMillis, ClientMetrics<?, ?, ?> metrics, Http2ChannelUpgrade upgrade, Http1xClientConnection upgradingConnection) {
      this.maxLifetimeMillis = maxLifetimeMillis;
      this.upgradedConnection = upgradedConnection;
      this.upgradingConnection = upgradingConnection;
      this.upgradingStream = stream;
      this.upgrade = upgrade;
      this.metrics = metrics;
    }

    @Override
    public HttpClientConnection connection() {
      return upgradedConnection;
    }

    /**
     * HTTP/2 clear text upgrade here.
     */
    @Override
    public Future<Void> writeHead(HttpRequestHead request,
                                  boolean chunked,
                                  Buffer buf,
                                  boolean end,
                                  StreamPriority priority,
                                  boolean connect) {
      UpgradeResult blah = new UpgradeResult() {
        @Override
        public void upgradeAccepted(HttpClientConnection connection, HttpClientStream upgradedStream) {
          UpgradingStream.this.handleUpgrade(connection, upgradedStream);
        }
        @Override
        public void upgradeRejected() {
          UpgradingStream.this.upgradedConnection.upgradeProcessed = true;
        }
        @Override
        public void upgradeFailure(Throwable cause) {
          upgradingConnection.closeHandler(null);
          upgradingConnection.exceptionHandler(null);
          upgradingConnection.evictionHandler(null);
          upgradingConnection.concurrencyChangeHandler(null);
          upgradingConnection.invalidMessageHandler(null);
          log.error(cause.getMessage(), cause);
        }
      };
      upgrade.upgrade(upgradingStream, request, buf, end,
        upgradingConnection.channelHandlerContext().channel(), maxLifetimeMillis, metrics, blah);
      PromiseInternal<Void> promise = upgradingStream.context().promise();
      writeHead(request, chunked, buf, end, priority, connect, promise);
      return promise.future();
    }

    private void writeHead(HttpRequestHead head,
                           boolean chunked,
                           Buffer buf,
                           boolean end,
                           StreamPriority priority,
                           boolean connect,
                           Promise<Void> promise) {
      EventExecutor exec = upgradingConnection.channelHandlerContext().executor();
      if (exec.inEventLoop()) {
        upgradingStream.writeHead(head, chunked, buf, end, priority, connect);
        if (end) {
          ChannelPipeline pipeline = upgradingConnection.channelHandlerContext().pipeline();
          pipeline.fireUserEventTriggered(SEND_BUFFERED_MESSAGES_EVENT);
        }
      } else {
        exec.execute(() -> writeHead(head, chunked, buf, end, priority, connect, promise));
      }
    }

    @Override
    public int id() {
      return 1;
    }

    @Override
    public Object metric() {
      return upgradingStream.metric();
    }

    @Override
    public Object trace() {
      return upgradingStream.trace();
    }

    @Override
    public HttpVersion version() {
      HttpClientStream s = upgradedStream;
      if (s == null) {
        s = upgradingStream;
      }
      return s.version();
    }

    @Override
    public ContextInternal context() {
      return upgradingStream.context();
    }

    @Override
    public HttpClientStream continueHandler(Handler<Void> handler) {
      if (upgradedStream != null) {
        upgradedStream.continueHandler(handler);
      } else {
        upgradingStream.continueHandler(handler);
        continueHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream earlyHintsHandler(Handler<MultiMap> handler) {
      if (upgradedStream != null) {
        upgradedStream.earlyHintsHandler(handler);
      } else {
        upgradingStream.earlyHintsHandler(handler);
        earlyHintsHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream pushHandler(Handler<HttpClientPush> handler) {
      if (upgradedStream != null) {
        upgradedStream.pushHandler(handler);
      } else {
        upgradingStream.pushHandler(handler);
        pushHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream closeHandler(Handler<Void> handler) {
      if (upgradedStream != null) {
        upgradedStream.closeHandler(handler);
      } else {
        upgradingStream.closeHandler(handler);
        closeHandler = handler;
      }
      return this;
    }

    @Override
    public UpgradingStream drainHandler(Handler<Void> handler) {
      if (upgradedStream != null) {
        upgradedStream.drainHandler(handler);
      } else {
        upgradingStream.drainHandler(handler);
        drainHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream resetHandler(Handler<Long> handler) {
      if (upgradedStream != null) {
        upgradedStream.resetHandler(handler);
      } else {
        upgradingStream.resetHandler(handler);
        resetHandler = handler;
      }
      return this;
    }

    @Override
    public UpgradingStream exceptionHandler(Handler<Throwable> handler) {
      if (upgradedStream != null) {
        upgradedStream.exceptionHandler(handler);
      } else {
        upgradingStream.exceptionHandler(handler);
        exceptionHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream headHandler(Handler<HttpResponseHead> handler) {
      if (upgradedStream != null) {
        upgradedStream.headHandler(handler);
      } else {
        upgradingStream.headHandler(handler);
        headHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream dataHandler(Handler<Buffer> handler) {
      if (upgradedStream != null) {
        upgradedStream.dataHandler(handler);
      } else {
        upgradingStream.dataHandler(handler);
        chunkHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream trailersHandler(Handler<MultiMap> handler) {
      if (upgradedStream != null) {
        upgradedStream.trailersHandler(handler);
      } else {
        upgradingStream.trailersHandler(handler);
        trailersHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream customFrameHandler(Handler<HttpFrame> handler) {
      if (upgradedStream != null) {
        upgradedStream.customFrameHandler(handler);
      } else {
        upgradingStream.customFrameHandler(handler);
        unknownFrameHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream priorityChangeHandler(Handler<StreamPriority> handler) {
      if (upgradedStream != null) {
        upgradedStream.priorityChangeHandler(handler);
      } else {
        upgradingStream.priorityChangeHandler(handler);
        priorityHandler = handler;
      }
      return this;
    }

    @Override
    public HttpClientStream setWriteQueueMaxSize(int maxSize) {
      if (upgradedStream != null) {
        upgradedStream.setWriteQueueMaxSize(maxSize);
      } else {
        upgradingStream.setWriteQueueMaxSize(maxSize);
      }
      return this;
    }

    @Override
    public boolean isWritable() {
      if (upgradedStream != null) {
        return upgradedStream.isWritable();
      } else {
        return upgradingStream.isWritable();
      }
    }

    @Override
    public Future<Void> writeChunk(Buffer buf, boolean end) {
      EventExecutor exec = upgradingConnection.channelHandlerContext().executor();
      if (exec.inEventLoop()) {
        Future<Void> future = upgradingStream.writeChunk(buf, end);
        if (end) {
          ChannelPipeline pipeline = upgradingConnection.channelHandlerContext().pipeline();
          future = future.andThen(ar -> {
            if (ar.succeeded()) {
              pipeline.fireUserEventTriggered(SEND_BUFFERED_MESSAGES_EVENT);
            }
          });
        }
        return future;
      } else {
        Promise<Void> promise = upgradingStream.context().promise();
        exec.execute(() -> {
          Future<Void> future = writeChunk(buf, end);
          future.onComplete(promise);
        });
        return promise.future();
      }
    }

    @Override
    public Future<Void> writeFrame(int type, int flags, Buffer payload) {
      if (upgradedStream != null) {
        return upgradedStream.writeFrame(type, flags, payload);
      } else {
        return upgradingStream.writeFrame(type, flags, payload);
      }
    }

    @Override
    public HttpClientStream pause() {
      if (upgradedStream != null) {
        upgradedStream.pause();
      } else {
        upgradingStream.pause();
      }
      return this;
    }

    @Override
    public HttpClientStream fetch(long amount) {
      if (upgradedStream != null) {
        upgradedStream.fetch(amount);
      } else {
        upgradingStream.fetch(amount);
      }
      return this;
    }

    @Override
    public Future<Void> writeReset(long code) {
      if (upgradedStream != null) {
        return upgradedStream.writeReset(code);
      } else {
        return upgradingStream.writeReset(code);
      }
    }

    @Override
    public StreamPriority priority() {
      if (upgradedStream != null) {
        return upgradedStream.priority();
      } else {
        return upgradingStream.priority();
      }
    }

    @Override
    public HttpClientStream updatePriority(StreamPriority streamPriority) {
      if (upgradedStream != null) {
        upgradedStream.updatePriority(streamPriority);
      } else {
        upgradingStream.updatePriority(streamPriority);
      }
      return this;
    }
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    if (current instanceof Http1xClientConnection && !upgradeProcessed) {
      return current
        .createStream(context)
        .map(stream -> new UpgradingStream(stream, this, maxLifetimeMillis, metrics, upgrade, (Http1xClientConnection) current));
    } else {
      return current
        .createStream(context)
        .map(stream -> new DelegatingStream(this, stream));
    }
  }

  @Override
  public ContextInternal context() {
    return current.context();
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    if (current instanceof Http1xClientConnection) {
      remoteSettingsHandler = handler;
    } else {
      current.remoteSettingsHandler(handler);
    }
    return this;
  }

  @Override
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    if (current instanceof Http1xClientConnection) {
      pingHandler = handler;
    } else {
      current.pingHandler(handler);
    }
    return this;
  }

  @Override
  public HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler) {
    if (current instanceof Http1xClientConnection) {
      goAwayHandler = handler;
    } else {
      current.goAwayHandler(handler);
    }
    return this;
  }

  @Override
  public HttpConnection shutdownHandler(@Nullable Handler<Void> handler) {
    if (current instanceof Http1xClientConnection) {
      shutdownHandler = handler;
    } else {
      current.shutdownHandler(handler);
    }
    return this;
  }

  @Override
  public HttpConnection closeHandler(Handler<Void> handler) {
    if (current instanceof Http1xClientConnection) {
      closeHandler = handler;
    }
    current.closeHandler(handler);
    return this;
  }

  @Override
  public HttpConnection exceptionHandler(Handler<Throwable> handler) {
    if (current instanceof Http1xClientConnection) {
      exceptionHandler = handler;
    }
    current.exceptionHandler(handler);
    return this;
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    if (current instanceof Http1xClientConnection) {
      evictionHandler = handler;
    }
    current.evictionHandler(handler);
    return this;
  }

  @Override
  public HttpClientConnection invalidMessageHandler(Handler<Object> handler) {
    if (current instanceof Http1xClientConnection) {
      invalidMessageHandler = handler;
    }
    current.invalidMessageHandler(handler);
    return this;
  }

  @Override
  public HttpClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    if (current instanceof Http1xClientConnection) {
      concurrencyChangeHandler = handler;
    }
    current.concurrencyChangeHandler(handler);
    return this;
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    return current.goAway(errorCode, lastStreamId, debugData);
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    return current.shutdown(timeout, unit);
  }

  @Override
  public Future<Void> updateSettings(Http2Settings settings) {
    return current.updateSettings(settings);
  }

  @Override
  public Http2Settings settings() {
    return current.settings();
  }

  @Override
  public Http2Settings remoteSettings() {
    return current.remoteSettings();
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    return current.ping(data);
  }

  @Override
  public SocketAddress remoteAddress() {
    return current.remoteAddress();
  }

  @Override
  public SocketAddress remoteAddress(boolean real) {
    return current.remoteAddress(real);
  }

  @Override
  public SocketAddress localAddress() {
    return current.localAddress();
  }

  @Override
  public SocketAddress localAddress(boolean real) {
    return current.localAddress(real);
  }

  @Override
  public boolean isSsl() {
    return current.isSsl();
  }

  @Override
  public SSLSession sslSession() {
    return current.sslSession();
  }

  @Override
  public boolean isValid() {
    return current.isValid();
  }

  @Override
  public String indicatedServerName() {
    return current.indicatedServerName();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[current=" + current.getClass().getSimpleName() + "]";
  }

  /**
   * The outcome of the upgrade signalled by the upgrade.
   */
  public interface UpgradeResult {

    /**
     * Upgrade is successful.
     *
     * @param connection the upgraded HTTP/2 connection
     * @param upgradedStream the upgraded HTTP/2 stream
     */
    void upgradeAccepted(HttpClientConnection connection, HttpClientStream upgradedStream);

    /**
     * Signals the server rejected the HTTP upgrade.
     */
    void upgradeRejected();

    /**
     * Something when wrong during the upgrade.
     *
     * @param cause the error
     */
    void upgradeFailure(Throwable cause);
  }

  /**
   * Plugin to upgrade a Netty HTTP/1.1 channel to a Netty HTTP/2 channel.
   */
  public interface Http2ChannelUpgrade {

    void upgrade(HttpClientStream upgradingStream,
                 HttpRequestHead request,
                 Buffer content,
                 boolean end,
                 Channel channel,
                 long maxLifetimeMillis,
                 ClientMetrics<?, ?, ?> clientMetrics, UpgradeResult result);
  }
}
