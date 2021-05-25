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
import io.netty.channel.Channel;
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
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.security.cert.Certificate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * An HTTP/2 connection in clear text that upgraded from an HTTP/1 upgrade.
 */
public class Http2UpgradedClientConnection implements HttpClientConnection {

  private static final Object SEND_BUFFERED_MESSAGES = new Object();

  private static final Logger log = LoggerFactory.getLogger(Http2UpgradedClientConnection.class);

  private HttpClientImpl client;
  private HttpClientConnection current;

  private Handler<Void> closeHandler;
  private Handler<Void> shutdownHandler;
  private Handler<GoAway> goAwayHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Buffer> pingHandler;
  private Handler<Void> evictionHandler;
  private Handler<Long> concurrencyChangeHandler;
  private Handler<Http2Settings> remoteSettingsHandler;

  Http2UpgradedClientConnection(HttpClientImpl client, Http1xClientConnection connection) {
    this.client = client;
    this.current = connection;
  }

  @Override
  public long concurrency() {
    return 1L;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return current.channelHandlerContext();
  }

  @Override
  public Channel channel() {
    return current.channel();
  }

  @Override
  public Future<Void> close() {
    return current.close();
  }

  @Override
  public Object metric() {
    return current.metric();
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return current.lastResponseReceivedTimestamp();
  }

  /**
   * The first stream that will send the request using HTTP/1, upgrades the connection when the protocol
   * switches and receives the response with HTTP/2 frames.
   */
  private static class UpgradingStream implements HttpClientStream {

    private final Http1xClientConnection upgradingConnection;
    private final HttpClientStream upgradingStream;
    private final Http2UpgradedClientConnection upgradedConnection;
    private HttpClientStream upgradedStream;
    private Handler<HttpResponseHead> headHandler;
    private Handler<Buffer> chunkHandler;
    private Handler<MultiMap> endHandler;
    private Handler<StreamPriority> priorityHandler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> drainHandler;
    private Handler<Void> continueHandler;
    private Handler<HttpClientPush> pushHandler;
    private Handler<HttpFrame> unknownFrameHandler;
    private Handler<Void> closeHandler;

    UpgradingStream(HttpClientStream stream, Http2UpgradedClientConnection upgradedConnection, Http1xClientConnection upgradingConnection) {
      this.upgradedConnection = upgradedConnection;
      this.upgradingConnection = upgradingConnection;
      this.upgradingStream = stream;
    }

    @Override
    public HttpClientConnection connection() {
      return upgradedConnection;
    }

    /**
     * HTTP/2 clear text upgrade here.
     */
    @Override
    public void writeHead(HttpRequestHead request,
                          boolean chunked,
                          ByteBuf buf,
                          boolean end,
                          StreamPriority priority,
                          boolean connect,
                          Handler<AsyncResult<Void>> handler) {
      ChannelPipeline pipeline = upgradingConnection.channel().pipeline();
      HttpClientCodec httpCodec = pipeline.get(HttpClientCodec.class);

      class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
          super.userEventTriggered(ctx, evt);
          ChannelPipeline pipeline = ctx.pipeline();
          if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
            // Upgrade handler will remove itself and remove the HttpClientCodec
            pipeline.remove(upgradingConnection.channelHandlerContext().handler());
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

      VertxHttp2ClientUpgradeCodec upgradeCodec = new VertxHttp2ClientUpgradeCodec(upgradedConnection.client.getOptions().getInitialSettings()) {
        @Override
        public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {

          // Now we need to upgrade this to an HTTP2
          VertxHttp2ConnectionHandler<Http2ClientConnection> handler = Http2ClientConnection.createHttp2ConnectionHandler(upgradedConnection.client, upgradingConnection.metrics, (EventLoopContext) upgradingConnection.getContext(), upgradedConnection.current.metric(), conn -> {
            conn.upgradeStream(upgradingStream.metric(), upgradingStream.getContext(), ar -> {
              upgradingConnection.closeHandler(null);
              upgradingConnection.exceptionHandler(null);
              upgradingConnection.evictionHandler(null);
              upgradingConnection.concurrencyChangeHandler(null);
              if (ar.succeeded()) {
                upgradedStream = ar.result();
                upgradedStream.headHandler(headHandler);
                upgradedStream.chunkHandler(chunkHandler);
                upgradedStream.endHandler(endHandler);
                upgradedStream.priorityHandler(priorityHandler);
                upgradedStream.exceptionHandler(exceptionHandler);
                upgradedStream.drainHandler(drainHandler);
                upgradedStream.continueHandler(continueHandler);
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
                Handler<Long> concurrencyChangeHandler = upgradedConnection.concurrencyChangeHandler;
                upgradedConnection.closeHandler = null;
                upgradedConnection.exceptionHandler = null;
                upgradedConnection.pingHandler = null;
                upgradedConnection.goAwayHandler = null;
                upgradedConnection.shutdownHandler = null;
                upgradedConnection.remoteSettingsHandler = null;
                upgradedConnection.evictionHandler = null;
                upgradedConnection.concurrencyChangeHandler = null;
                concurrencyChangeHandler.handle(conn.concurrency());
              } else {
                // Handle me
                log.error(ar.cause().getMessage(), ar.cause());
              }
            });
          });
          upgradingConnection.channel().pipeline().addLast(handler);
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
      doWriteHead(request, chunked, buf, end, priority, connect, handler);
    }

    private void doWriteHead(HttpRequestHead head,
                             boolean chunked,
                             ByteBuf buf,
                             boolean end,
                             StreamPriority priority,
                             boolean connect,
                             Handler<AsyncResult<Void>> handler) {
      EventExecutor exec = upgradingConnection.channelHandlerContext().executor();
      if (exec.inEventLoop()) {
        upgradingStream.writeHead(head, chunked, buf, end, priority, connect, handler);
        if (end) {
          ChannelPipeline pipeline = upgradingConnection.channelHandlerContext().pipeline();
          pipeline.fireUserEventTriggered(SEND_BUFFERED_MESSAGES);
        }
      } else {
        exec.execute(() -> doWriteHead(head, chunked, buf, end, priority, connect, handler));
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
    public void pushHandler(Handler<HttpClientPush> handler) {
      if (pushHandler != null) {
        upgradedStream.pushHandler(handler);
      } else {
        upgradingStream.pushHandler(handler);
        pushHandler = handler;
      }
    }

    @Override
    public void closeHandler(Handler<Void> handler) {
      if (closeHandler != null) {
        upgradedStream.closeHandler(handler);
      } else {
        upgradingStream.closeHandler(handler);
        closeHandler = handler;
      }
    }

    @Override
    public void drainHandler(Handler<Void> handler) {
      if (upgradedStream != null) {
        upgradedStream.drainHandler(handler);
      } else {
        upgradingStream.drainHandler(handler);
        drainHandler = handler;
      }
    }

    @Override
    public void exceptionHandler(Handler<Throwable> handler) {
      if (upgradedStream != null) {
        upgradedStream.exceptionHandler(handler);
      } else {
        upgradingStream.exceptionHandler(handler);
        exceptionHandler = handler;
      }
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
    public void priorityHandler(Handler<StreamPriority> handler) {
      if (upgradedStream != null) {
        upgradedStream.priorityHandler(handler);
      } else {
        upgradingStream.priorityHandler(handler);
        priorityHandler = handler;
      }
    }

    @Override
    public void writeBuffer(ByteBuf buf, boolean end, Handler<AsyncResult<Void>> handler) {
      EventExecutor exec = upgradingConnection.channelHandlerContext().executor();
      if (exec.inEventLoop()) {
        upgradingStream.writeBuffer(buf, end, handler);
        if (end) {
          ChannelPipeline pipeline = upgradingConnection.channelHandlerContext().pipeline();
          pipeline.fireUserEventTriggered(SEND_BUFFERED_MESSAGES);
        }
      } else {
        exec.execute(() -> writeBuffer(buf, end, handler));
      }
    }

    @Override
    public void writeFrame(int type, int flags, ByteBuf payload) {
      upgradingStream.writeFrame(type, flags, payload);
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
      upgradingStream.doSetWriteQueueMaxSize(size);
    }

    @Override
    public boolean isNotWritable() {
      return upgradingStream.isNotWritable();
    }

    @Override
    public void doPause() {
      upgradingStream.doPause();
    }

    @Override
    public void doFetch(long amount) {
      upgradingStream.doFetch(amount);
    }

    @Override
    public void reset(Throwable cause) {
      upgradingStream.reset(cause);
    }

    @Override
    public StreamPriority priority() {
      return upgradingStream.priority();
    }

    @Override
    public void updatePriority(StreamPriority streamPriority) {
      upgradingStream.updatePriority(streamPriority);
    }
  }

  @Override
  public void createStream(ContextInternal context, Handler<AsyncResult<HttpClientStream>> handler) {
    if (current instanceof Http1xClientConnection) {
      current.createStream(context, ar -> {
        if (ar.succeeded()) {
          HttpClientStream stream = ar.result();
          UpgradingStream upgradingStream = new UpgradingStream(stream, this, (Http1xClientConnection) current);
          handler.handle(Future.succeededFuture(upgradingStream));
        } else {
          handler.handle(ar);
        }
      });
    } else {
      current.createStream(context, handler);
    }
  }

  @Override
  public ContextInternal getContext() {
    return current.getContext();
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
  public void shutdown(long timeout, Handler<AsyncResult<Void>> handler) {
    current.shutdown(timeout, handler);
  }

  @Override
  public Future<Void> shutdown(long timeoutMs) {
    return current.shutdown(timeoutMs);
  }

  @Override
  public Future<Void> updateSettings(Http2Settings settings) {
    return current.updateSettings(settings);
  }

  @Override
  public HttpConnection updateSettings(Http2Settings settings, Handler<AsyncResult<Void>> completionHandler) {
    return current.updateSettings(settings, completionHandler);
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
  public HttpConnection ping(Buffer data, Handler<AsyncResult<Buffer>> pongHandler) {
    return current.ping(data, pongHandler);
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
  public SocketAddress localAddress() {
    return current.localAddress();
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
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return current.peerCertificateChain();
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
