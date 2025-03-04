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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.WriteStream;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A connection that attempts to perform a protocol upgrade to H2C. The connection might use HTTP/1 or H2C
 * depending on the initial server response.
 */
public class Http2UpgradeClientConnection implements HttpClientConnectionInternal {

  private static final Object SEND_BUFFERED_MESSAGES = new Object();

  private static final Logger log = LoggerFactory.getLogger(Http2UpgradeClientConnection.class);

  private HttpClientBase client;
  private HttpClientConnectionInternal current;
  private boolean upgradeProcessed;

  private Handler<Void> closeHandler;
  private Handler<Void> shutdownHandler;
  private Handler<GoAway> goAwayHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Buffer> pingHandler;
  private Handler<Void> evictionHandler;
  private Handler<Object> invalidMessageHandler;
  private Handler<Long> concurrencyChangeHandler;
  private Handler<HttpSettings> remoteHttpSettingsHandler;

  Http2UpgradeClientConnection(HttpClientBase client, Http1xClientConnection connection) {
    this.client = client;
    this.current = connection;
  }

  public HttpClientConnectionInternal unwrap() {
    return current;
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
  public boolean pooled() {
    return current.pooled();
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
    public HttpClientConnectionInternal connection() {
      return connection;
    }

    @Override
    public ContextInternal getContext() {
      return delegate.getContext();
    }

    @Override
    public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriorityBase priority, boolean connect) {
      return delegate.writeHead(request, chunked, buf, end, priority, connect);
    }

    @Override
    public Future<Void> writeBuffer(ByteBuf buf, boolean end) {
      return delegate.writeBuffer(buf, end);
    }

    @Override
    public Future<Void> writeFrame(int type, int flags, ByteBuf payload) {
      return delegate.writeFrame(type, flags, payload);
    }

    @Override
    public void continueHandler(Handler<Void> handler) {
      delegate.continueHandler(handler);
    }

    @Override
    public void earlyHintsHandler(Handler<MultiMap> handler) {
      delegate.earlyHintsHandler(handler);
    }

    @Override
    public void pushHandler(Handler<HttpClientPush> handler) {
      delegate.pushHandler(handler);
    }

    @Override
    public void unknownFrameHandler(Handler<HttpFrame> handler) {
      delegate.unknownFrameHandler(handler);
    }

    @Override
    public void headHandler(Handler<HttpResponseHead> handler) {
      delegate.headHandler(handler);
    }

    @Override
    public void chunkHandler(Handler<Buffer> handler) {
      delegate.chunkHandler(handler);
    }

    @Override
    public void endHandler(Handler<MultiMap> handler) {
      delegate.endHandler(handler);
    }

    @Override
    public void priorityHandler(Handler<StreamPriorityBase> handler) {
      delegate.priorityHandler(handler);
    }

    @Override
    public void closeHandler(Handler<Void> handler) {
      delegate.closeHandler(handler);
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
      delegate.doSetWriteQueueMaxSize(size);
    }

    @Override
    public boolean isNotWritable() {
      return delegate.isNotWritable();
    }

    @Override
    public void doPause() {
      delegate.doPause();
    }

    @Override
    public void doFetch(long amount) {
      delegate.doFetch(amount);
    }

    @Override
    public Future<Void> reset(Throwable cause) {
      return delegate.reset(cause);
    }

    @Override
    public StreamPriorityBase priority() {
      return delegate.priority();
    }

    @Override
    public void updatePriority(StreamPriorityBase streamPriority) {
      delegate.updatePriority(streamPriority);
    }

    @Override
    public WriteStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
      delegate.exceptionHandler(handler);
      return this;
    }

    @Override
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
      delegate.setWriteQueueMaxSize(maxSize);
      return this;
    }

    @Override
    public boolean writeQueueFull() {
      return delegate.writeQueueFull();
    }

    @Override
    public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
      delegate.drainHandler(handler);
      return this;
    }

    @Override
    public StreamPriorityBase createDefaultStreamPriority() {
      return HttpUtils.DEFAULT_STREAM_PRIORITY;
    }
  }

  /**
   * The first stream that will send the request using HTTP/1, upgrades the connection when the protocol
   * switches and receives the response with HTTP/2 frames.
   */
  private static class UpgradingStream implements HttpClientStream {

    private final Http1xClientConnection upgradingConnection;
    private final HttpClientStream upgradingStream;
    private final Http2UpgradeClientConnection upgradedConnection;
    private HttpClientStream upgradedStream;
    private Handler<HttpResponseHead> headHandler;
    private Handler<Buffer> chunkHandler;
    private Handler<MultiMap> endHandler;
    private Handler<StreamPriorityBase> priorityHandler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> drainHandler;
    private Handler<Void> continueHandler;
    private Handler<MultiMap> earlyHintsHandler;
    private Handler<HttpClientPush> pushHandler;
    private Handler<HttpFrame> unknownFrameHandler;
    private Handler<Void> closeHandler;

    UpgradingStream(HttpClientStream stream, Http2UpgradeClientConnection upgradedConnection, Http1xClientConnection upgradingConnection) {
      this.upgradedConnection = upgradedConnection;
      this.upgradingConnection = upgradingConnection;
      this.upgradingStream = stream;
    }

    @Override
    public HttpClientConnectionInternal connection() {
      return upgradedConnection;
    }

    /**
     * HTTP/2 clear text upgrade here.
     */
    @Override
    public Future<Void> writeHead(HttpRequestHead request,
                          boolean chunked,
                          ByteBuf buf,
                          boolean end,
                          StreamPriorityBase priority,
                          boolean connect) {
      ChannelPipeline pipeline = upgradingConnection.channel().pipeline();
      HttpClientCodec httpCodec = pipeline.get(HttpClientCodec.class);

      class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
          super.userEventTriggered(ctx, evt);
          ChannelPipeline pipeline = ctx.pipeline();
          if (evt instanceof HttpClientUpgradeHandler.UpgradeEvent) {
            switch ((HttpClientUpgradeHandler.UpgradeEvent)evt) {
              case UPGRADE_SUCCESSFUL:
                // Remove Http1xClientConnection handler
                pipeline.remove(upgradingConnection.channelHandlerContext().handler());
                // Go through
              case UPGRADE_REJECTED:
                // Remove this handler
                pipeline.remove(this);
                // Upgrade handler will remove itself and remove the HttpClientCodec
                UpgradingStream.this.upgradedConnection.upgradeProcessed = true;
                break;
            }
          }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          if (msg instanceof HttpResponseHead) {
            pipeline.remove(this);
            HttpResponseHead resp = (HttpResponseHead) msg;
            if (resp.statusCode != HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
              // Insert the close headers to let the HTTP/1 stream close the connection
              resp.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
          }
          super.channelRead(ctx, msg);
        }
      }

      VertxHttp2ClientUpgradeCodec upgradeCodec = new VertxHttp2ClientUpgradeCodec(upgradedConnection.client.options().getInitialSettings()) {
        @Override
        public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {

          // Now we need to upgrade this to an HTTP2
          VertxHttp2ConnectionHandler<Http2ClientConnection> handler = Http2ClientConnection.createHttp2ConnectionHandler(upgradedConnection.client, upgradingConnection.metrics, upgradingConnection.context(), true, upgradedConnection.current.metric(), upgradedConnection.current.authority(), upgradingConnection.pooled());
          upgradingConnection.channel().pipeline().addLast(handler);
          handler.connectFuture().addListener(future -> {
            if (!future.isSuccess()) {
              // Handle me
              log.error(future.cause().getMessage(), future.cause());
              return;
            }
            Http2ClientConnection conn = (Http2ClientConnection) future.getNow();
            try {
              try {
                upgradedStream = conn.upgradeStream(upgradingStream.metric(), upgradingStream.trace(), upgradingStream.getContext());
              } finally {
                upgradingConnection.closeHandler(null);
                upgradingConnection.exceptionHandler(null);
                upgradingConnection.evictionHandler(null);
                upgradingConnection.concurrencyChangeHandler(null);
              }
              upgradedStream.headHandler(headHandler);
              upgradedStream.chunkHandler(chunkHandler);
              upgradedStream.endHandler(endHandler);
              upgradedStream.priorityHandler(priorityHandler);
              upgradedStream.exceptionHandler(exceptionHandler);
              upgradedStream.drainHandler(drainHandler);
              upgradedStream.continueHandler(continueHandler);
              upgradedStream.earlyHintsHandler(earlyHintsHandler);
              upgradedStream.pushHandler(pushHandler);
              upgradedStream.unknownFrameHandler(unknownFrameHandler);
              upgradedStream.closeHandler(closeHandler);
              upgradingStream.headHandler(null);
              upgradingStream.chunkHandler(null);
              upgradingStream.endHandler(null);
              upgradingStream.priorityHandler(null);
              upgradingStream.exceptionHandler(null);
              upgradingStream.drainHandler(null);
              upgradingStream.continueHandler(null);
              upgradingStream.earlyHintsHandler(null);
              upgradingStream.pushHandler(null);
              upgradingStream.unknownFrameHandler(null);
              upgradingStream.closeHandler(null);
              headHandler = null;
              chunkHandler = null;
              endHandler = null;
              priorityHandler = null;
              exceptionHandler = null;
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
              conn.remoteHttpSettingsHandler(upgradedConnection.remoteHttpSettingsHandler);
              conn.evictionHandler(upgradedConnection.evictionHandler);
              conn.concurrencyChangeHandler(upgradedConnection.concurrencyChangeHandler);
              Handler<Long> concurrencyChangeHandler = upgradedConnection.concurrencyChangeHandler;
              upgradedConnection.closeHandler = null;
              upgradedConnection.exceptionHandler = null;
              upgradedConnection.pingHandler = null;
              upgradedConnection.goAwayHandler = null;
              upgradedConnection.shutdownHandler = null;
              upgradedConnection.remoteHttpSettingsHandler = null;
              upgradedConnection.evictionHandler = null;
              upgradedConnection.concurrencyChangeHandler = null;
              concurrencyChangeHandler.handle(conn.concurrency());
            } catch (Exception e) {
              // Handle me
              log.error(e.getMessage(), e);
            }
          });
          handler.clientUpgrade(ctx);
        }
      };
      HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(httpCodec, upgradeCodec, 65536) {

        private long bufferedSize = 0;
        private Deque<Object> buffered = new ArrayDeque<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          if (buffered != null) {
            // Buffer all messages received from the server until the HTTP request is fully sent.
            //
            // Explanation:
            //
            // It is necessary that the client only starts to process the response when the request
            // has been fully sent because the current HTTP2 implementation will not be able to process
            // the server preface until the client preface has been sent.
            //
            // Adding the VertxHttp2ConnectionHandler to the pipeline has two effects:
            // - it is required to process the server preface
            // - it will send the request preface to the server
            //
            // As we are adding this handler to the pipeline when we receive the 101 response from the server
            // this might send the client preface before the initial HTTP request (doing the upgrade) is fully sent
            // resulting in corrupting the protocol (the server might interpret it as an corrupted connection preface).
            //
            // Therefore we must buffer all pending messages until the request is fully sent.

            int maxContent = maxContentLength();
            boolean lower = bufferedSize < maxContent;
            if (msg instanceof ByteBufHolder) {
              bufferedSize += ((ByteBufHolder)msg).content().readableBytes();
            } else if (msg instanceof ByteBuf) {
              bufferedSize += ((ByteBuf)msg).readableBytes();
            }
            buffered.add(msg);

            if (bufferedSize >= maxContent && lower) {
              ctx.fireExceptionCaught(new TooLongFrameException("Max content exceeded " + maxContentLength() + " bytes."));
            }
          } else {
            super.channelRead(ctx, msg);
          }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
          if (SEND_BUFFERED_MESSAGES == evt) {
            Deque<Object> messages = buffered;
            buffered = null;
            Object msg;
            while ((msg = messages.poll()) != null) {
              super.channelRead(ctx, msg);
            }
          } else {
            super.userEventTriggered(ctx, evt);
          }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
          if (buffered != null) {
            Deque<Object> messages = buffered;
            buffered = null;
            Object msg;
            while ((msg = messages.poll()) != null) {
              ReferenceCountUtil.release(msg);
            }
          }
          super.handlerRemoved(ctx);
        }

      };
      pipeline.addAfter("codec", null, new UpgradeRequestHandler());
      pipeline.addAfter("codec", null, upgradeHandler);
      PromiseInternal<Void> promise = upgradingStream.getContext().promise();
      doWriteHead(request, chunked, buf, end, priority, connect, promise);
      return promise.future();
    }

    private void doWriteHead(HttpRequestHead head,
                             boolean chunked,
                             ByteBuf buf,
                             boolean end,
                             StreamPriorityBase priority,
                             boolean connect,
                             Promise<Void> promise) {
      EventExecutor exec = upgradingConnection.channelHandlerContext().executor();
      if (exec.inEventLoop()) {
        upgradingStream.writeHead(head, chunked, buf, end, priority, connect);
        if (end) {
          ChannelPipeline pipeline = upgradingConnection.channelHandlerContext().pipeline();
          pipeline.fireUserEventTriggered(SEND_BUFFERED_MESSAGES);
        }
      } else {
        exec.execute(() -> doWriteHead(head, chunked, buf, end, priority, connect, promise));
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
    public ContextInternal getContext() {
      return upgradingStream.getContext();
    }

    @Override
    public void continueHandler(Handler<Void> handler) {
      if (upgradedStream != null) {
        upgradedStream.continueHandler(handler);
      } else {
        upgradingStream.continueHandler(handler);
        continueHandler = handler;
      }
    }

    @Override
    public void earlyHintsHandler(Handler<MultiMap> handler) {
      if (upgradedStream != null) {
        upgradedStream.earlyHintsHandler(handler);
      } else {
        upgradingStream.earlyHintsHandler(handler);
        earlyHintsHandler = handler;
      }
    }

    @Override
    public void pushHandler(Handler<HttpClientPush> handler) {
      if (upgradedStream != null) {
        upgradedStream.pushHandler(handler);
      } else {
        upgradingStream.pushHandler(handler);
        pushHandler = handler;
      }
    }

    @Override
    public void closeHandler(Handler<Void> handler) {
      if (upgradedStream != null) {
        upgradedStream.closeHandler(handler);
      } else {
        upgradingStream.closeHandler(handler);
        closeHandler = handler;
      }
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
    public void headHandler(Handler<HttpResponseHead> handler) {
      if (upgradedStream != null) {
        upgradedStream.headHandler(handler);
      } else {
        upgradingStream.headHandler(handler);
        headHandler = handler;
      }
    }

    @Override
    public void chunkHandler(Handler<Buffer> handler) {
      if (upgradedStream != null) {
        upgradedStream.chunkHandler(handler);
      } else {
        upgradingStream.chunkHandler(handler);
        chunkHandler = handler;
      }
    }

    @Override
    public void endHandler(Handler<MultiMap> handler) {
      if (upgradedStream != null) {
        upgradedStream.endHandler(handler);
      } else {
        upgradingStream.endHandler(handler);
        endHandler = handler;
      }
    }

    @Override
    public void unknownFrameHandler(Handler<HttpFrame> handler) {
      if (upgradedStream != null) {
        upgradedStream.unknownFrameHandler(handler);
      } else {
        upgradingStream.unknownFrameHandler(handler);
        unknownFrameHandler = handler;
      }
    }

    @Override
    public void priorityHandler(Handler<StreamPriorityBase> handler) {
      if (upgradedStream != null) {
        upgradedStream.priorityHandler(handler);
      } else {
        upgradingStream.priorityHandler(handler);
        priorityHandler = handler;
      }
    }

    @Override
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
      if (upgradedStream != null) {
        upgradedStream.setWriteQueueMaxSize(maxSize);
      } else {
        upgradingStream.setWriteQueueMaxSize(maxSize);
      }
      return this;
    }

    @Override
    public boolean writeQueueFull() {
      if (upgradedStream != null) {
        return upgradedStream.writeQueueFull();
      } else {
        return upgradingStream.writeQueueFull();
      }
    }

    @Override
    public Future<Void> writeBuffer(ByteBuf buf, boolean end) {
      Promise<Void> promise = upgradingStream.getContext().promise();
      EventExecutor exec = upgradingConnection.channelHandlerContext().executor();
      if (exec.inEventLoop()) {
        upgradingStream.writeBuffer(buf, end);
        if (end) {
          ChannelPipeline pipeline = upgradingConnection.channelHandlerContext().pipeline();
          pipeline.fireUserEventTriggered(SEND_BUFFERED_MESSAGES);
        }
      } else {
        exec.execute(() -> writeBuffer(buf, end));
      }
      return promise.future();
    }

    @Override
    public Future<Void> writeFrame(int type, int flags, ByteBuf payload) {
      if (upgradedStream != null) {
        return upgradedStream.writeFrame(type, flags, payload);
      } else {
        return upgradingStream.writeFrame(type, flags, payload);
      }
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
      if (upgradedStream != null) {
        upgradedStream.doSetWriteQueueMaxSize(size);
      } else {
        upgradingStream.doSetWriteQueueMaxSize(size);
      }
    }

    @Override
    public boolean isNotWritable() {
      if (upgradedStream != null) {
        return upgradedStream.isNotWritable();
      } else {
        return upgradingStream.isNotWritable();
      }
    }

    @Override
    public void doPause() {
      if (upgradedStream != null) {
        upgradedStream.doPause();
      } else {
        upgradingStream.doPause();
      }
    }

    @Override
    public void doFetch(long amount) {
      if (upgradedStream != null) {
        upgradedStream.doFetch(amount);
      } else {
        upgradingStream.doFetch(amount);
      }
    }

    @Override
    public Future<Void> reset(Throwable cause) {
      if (upgradedStream != null) {
        return upgradedStream.reset(cause);
      } else {
        return upgradingStream.reset(cause);
      }
    }

    @Override
    public StreamPriorityBase priority() {
      if (upgradedStream != null) {
        return upgradedStream.priority();
      } else {
        return upgradingStream.priority();
      }
    }

    @Override
    public void updatePriority(StreamPriorityBase streamPriority) {
      if (upgradedStream != null) {
        upgradedStream.updatePriority(streamPriority);
      } else {
        upgradingStream.updatePriority(streamPriority);
      }
    }

    @Override
    public StreamPriorityBase createDefaultStreamPriority() {
      return HttpUtils.DEFAULT_STREAM_PRIORITY;
    }
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    if (current instanceof Http1xClientConnection && !upgradeProcessed) {
      return current
        .createStream(context)
        .map(stream -> new UpgradingStream(stream, this, (Http1xClientConnection) current));
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
  public HttpConnection remoteHttpSettingsHandler(Handler<HttpSettings> handler) {
    if (current instanceof Http1xClientConnection) {
      remoteHttpSettingsHandler = handler;
    } else {
      current.remoteHttpSettingsHandler(handler);
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
  public HttpClientConnectionInternal evictionHandler(Handler<Void> handler) {
    if (current instanceof Http1xClientConnection) {
      evictionHandler = handler;
    }
    current.evictionHandler(handler);
    return this;
  }

  @Override
  public HttpClientConnectionInternal invalidMessageHandler(Handler<Object> handler) {
    if (current instanceof Http1xClientConnection) {
      invalidMessageHandler = handler;
    }
    current.invalidMessageHandler(handler);
    return this;
  }

  @Override
  public HttpClientConnectionInternal concurrencyChangeHandler(Handler<Long> handler) {
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
  public Http2Settings httpSettings() {
    return (Http2Settings) current.httpSettings();
  }

  @Override
  public Future<Void> updateHttpSettings(HttpSettings settings) {
    return current.updateHttpSettings(settings);
  }

  @Override
  public HttpSettings remoteHttpSettings() {
    return current.remoteHttpSettings();
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
  public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
    return current.peerCertificates();
  }

  @Override
  public boolean isValid() {
    return current.isValid();
  }

  @Override
  public String indicatedServerName() {
    return current.indicatedServerName();
  }
}
