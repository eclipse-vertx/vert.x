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

package io.vertx.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.traffic.AbstractTrafficShapingHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.internal.net.SslHandshakeCompletionHandler;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for connections managed by a vertx instance. This base implementation does not handle
 * inbound/outbound message flow with a Netty channel.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ConnectionBase {

  private static final long METRICS_REPORTED_BYTES_LOW_MASK = 0xFFF; // 4K
  private static final long METRICS_REPORTED_BYTES_HIGH_MASK = ~METRICS_REPORTED_BYTES_LOW_MASK; // 4K

  /**
   * An exception used to signal a closed connection to an exception handler. Exception are
   * expensive to create, this instance can be used for this purpose. It does not capture a stack
   * trace to not be misleading.
   */
  public static final VertxException CLOSED_EXCEPTION = NetSocketInternal.CLOSED_EXCEPTION;
  public static final AttributeKey<SocketAddress> REMOTE_ADDRESS_OVERRIDE = AttributeKey.valueOf("RemoteAddressOverride");
  public static final AttributeKey<SocketAddress> LOCAL_ADDRESS_OVERRIDE = AttributeKey.valueOf("LocalAddressOverride");
  private static final Logger log = LoggerFactory.getLogger(ConnectionBase.class);

  protected final VertxInternal vertx;
  protected final ChannelHandlerContext chctx;
  public final ContextInternal context;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Object metric;
  private SocketAddress remoteAddress;
  private SocketAddress realRemoteAddress;
  private SocketAddress localAddress;
  private SocketAddress realLocalAddress;
  private Future<Void> closeFuture;
  private long remainingBytesRead;
  private long remainingBytesWritten;

  // State accessed exclusively from the event loop thread
  private ChannelPromise closeInitiated;
  private boolean closeFinished;

  protected ConnectionBase(ContextInternal context, ChannelHandlerContext chctx) {

    PromiseInternal<Void> f = context.promise();
    chctx
      .channel()
      .closeFuture()
      .addListener(f);

    this.vertx = context.owner();
    this.chctx = chctx;
    this.context = context;
    this.closeFuture = f;
  }

  /**
   * @return a promise that will be completed when the connection becomes closed
   */
  public Future<Void> closeFuture() {
    return closeFuture;
  }

  /**
   * Fail the connection, the {@code error} will be sent to the pipeline and the connection will
   * stop processing any further message.
   *
   * @param error the {@code Throwable} to propagate
   */
  public void fail(Throwable error) {
    chctx.pipeline().fireExceptionCaught(error);
  }

  protected final ChannelPromise wrap(FutureListener<Void> handler) {
    ChannelPromise promise = chctx.newPromise();
    promise.addListener(handler);
    return promise;
  }

  /**
   * Close the connection
   */
  public final Future<Void> close() {
    return close((Object) null);
  }

  static class CloseChannelPromise extends DefaultChannelPromise {
    final Object reason;
    final long timeout;
    final TimeUnit unit;
    public CloseChannelPromise(Channel channel, Object reason, long timeout, TimeUnit unit) {
      super(channel);
      this.reason = reason;
      this.timeout = timeout;
      this.unit = unit;
    }
  }

  /**
   * Close the connection
   */
  public final Future<Void> close(Object reason) {
    return close(reason, 0L, TimeUnit.SECONDS);
  }

  /**
   * Close the connection
   */
  public final Future<Void> close(Object reason, long timeout, TimeUnit unit) {
    EventExecutor exec = chctx.executor();
    CloseChannelPromise promise = new CloseChannelPromise(chctx.channel(), reason, timeout, unit);
    if (exec.inEventLoop()) {
      close(promise);
    } else {
      exec.execute(() -> close(promise));
    }
    PromiseInternal<Void> p = context.promise();
    promise.addListener(p);
    return p.future();
  }

  private void close(CloseChannelPromise promise) {
    chctx
      .channel()
      .close(promise);
  }

  final void handleClose(ChannelPromise promise) {
    if (closeInitiated != null) {
      long timeout;
      Object closeReason;
      if (promise instanceof CloseChannelPromise) {
        timeout = ((CloseChannelPromise)promise).timeout;
        closeReason = ((CloseChannelPromise)promise).reason;
      } else {
        timeout = 0L;
        closeReason = null;
      }
      if (timeout == 0L && !closeFinished) {
        closeFinished = true;
        closeInitiated = promise;
        handleClose(closeReason, promise);
      } else {
        chctx
          .channel()
          .closeFuture()
          .addListener(future -> {
            if (future.isSuccess()) {
              promise.setSuccess();
            } else {
              promise.setFailure(future.cause());
            }
          });
      }
    } else {
      closeInitiated = promise;
      if (promise instanceof CloseChannelPromise) {
        CloseChannelPromise closeChannelPromise = (CloseChannelPromise) promise;
        handleClose(closeChannelPromise.reason, closeChannelPromise.timeout, closeChannelPromise.unit, promise);
      } else {
        handleClose(null, 0L, TimeUnit.SECONDS, promise);
      }
    }
  }

  void handleClose(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
    if (closeFinished) {
      // Need to add too "promise" to closeInitiated promise to ensure proper report of the flow
      return;
    }
    closeFinished = true;
    handleClose(reason, promise);
  }

  protected void handleClose(Object reason, ChannelPromise promise) {
    chctx.close(promise);
  }

  public synchronized ConnectionBase closeHandler(Handler<Void> handler) {
    this.closeHandler = handler;
    return this;
  }

  public synchronized ConnectionBase exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  /**
   * @return the Netty channel - for internal usage only
   */
  public final Channel channel() {
    return chctx.channel();
  }

  public final ChannelHandlerContext channelHandlerContext() {
    return chctx;
  }

  public final ContextInternal context() {
    return context;
  }

  public final synchronized void metric(Object metric) {
    this.metric = metric;
  }

  public final synchronized Object metric() {
    return metric;
  }

  public NetworkMetrics metrics() {
    return null;
  }

  protected void handleException(Throwable t) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      metrics.exceptionOccurred(metric, remoteAddress(), t);
    }
    context.execute(t, err -> {
      Handler<Throwable> handler;
      synchronized (ConnectionBase.this) {
        handler = exceptionHandler;
      }
      if (handler != null) {
        context.dispatch(err, handler);
      } else {
        if (log.isDebugEnabled()) {
          log.error(t.getMessage(), t);
        } else {
          log.error(t.getMessage());
        }
      }
    });
  }

  protected void handleClosed() {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      flushBytesRead();
      flushBytesWritten();
      if (metrics instanceof TCPMetrics) {
        ((TCPMetrics) metrics).disconnected(metric(), remoteAddress());
      }
    }
    context.execute(() -> {
      Handler<Void> handler;
      synchronized (ConnectionBase.this) {
        handler = closeHandler;
      }
      if (handler != null) {
        context.dispatch(handler);
      }
    });
  }

  public final void reportBytesRead(Object msg) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      doReportBytesRead(msg, metrics);
    }
  }

  private void doReportBytesRead(Object msg, NetworkMetrics metrics) {
    long bytes = remainingBytesRead;
    long numberOfBytes = sizeof(msg);
    bytes += numberOfBytes;
    long val = bytes & METRICS_REPORTED_BYTES_HIGH_MASK;
    if (val > 0) {
      bytes &= METRICS_REPORTED_BYTES_LOW_MASK;
      metrics.bytesRead(metric(), remoteAddress(), val);
    }
    remainingBytesRead = bytes;
  }

  protected long sizeof(Object msg) {
    if (msg instanceof ByteBuf) {
      return ((ByteBuf)msg).readableBytes();
    }
    return 0L;
  }

  public final void reportBytesRead(long numberOfBytes) {
    if (numberOfBytes < 0L) {
      throw new IllegalArgumentException();
    }
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long bytes = remainingBytesRead;
      bytes += numberOfBytes;
      long val = bytes & METRICS_REPORTED_BYTES_HIGH_MASK;
      if (val > 0) {
        bytes &= METRICS_REPORTED_BYTES_LOW_MASK;
        metrics.bytesRead(metric(), remoteAddress(), val);
      }
      remainingBytesRead = bytes;
    }
  }

  public final void reportsBytesWritten(Object msg) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long numberOfBytes = sizeof(msg);
      long bytes = remainingBytesWritten;
      bytes += numberOfBytes;
      long val = bytes & METRICS_REPORTED_BYTES_HIGH_MASK;
      if (val > 0) {
        bytes &= METRICS_REPORTED_BYTES_LOW_MASK;
        metrics.bytesWritten(metric, remoteAddress(), val);
      }
      remainingBytesWritten = bytes;
    }
  }

  public final void reportBytesWritten(long numberOfBytes) {
    if (numberOfBytes < 0L) {
      throw new IllegalArgumentException();
    }
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long bytes = remainingBytesWritten;
      bytes += numberOfBytes;
      long val = bytes & METRICS_REPORTED_BYTES_HIGH_MASK;
      if (val > 0) {
        bytes &= METRICS_REPORTED_BYTES_LOW_MASK;
        metrics.bytesWritten(metric, remoteAddress(), val);
      }
      remainingBytesWritten = bytes;
    }
  }

  public void flushBytesRead() {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long val = remainingBytesRead;
      if (val > 0L) {
        remainingBytesRead = 0L;
        metrics.bytesRead(metric(), remoteAddress(), val);
      }
    }
  }

  public void flushBytesWritten() {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long val = remainingBytesWritten;
      if (val > 0L) {
        remainingBytesWritten = 0L;
        metrics.bytesWritten(metric(), remoteAddress(), val);
      }
    }
  }

  public boolean isSsl() {
    return chctx.pipeline().get(SslHandler.class) != null || isHttp3SslHandler(chctx);
  }

  private boolean isHttp3SslHandler(ChannelHandlerContext chctx) {
    Channel channel = chctx.channel();
    ChannelPipeline pipeline = getDatagramChannelPipeline(channel);
    return pipeline != null && pipeline.names().contains(ChannelProvider.CLIENT_SSL_HANDLER_NAME);
  }

  private ChannelPipeline getDatagramChannelPipeline(Channel channel) {
    channel = channel != null ? channel.parent() : null;
    return channel instanceof DatagramChannel ? channel.pipeline() : null;
  }

  public boolean isTrafficShaped() {
    return chctx.pipeline().get(AbstractTrafficShapingHandler.class) != null;
  }

  public SSLSession sslSession() {
    //TODO: should return sslSession for http3.
    ChannelHandlerContext sslHandlerContext = chctx.pipeline().context(SslHandler.class);
    if (sslHandlerContext != null) {
      SslHandler sslHandler = (SslHandler) sslHandlerContext.handler();
      return sslHandler.engine().getSession();
    } else {
      return null;
    }
  }

  public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
    SSLSession session = sslSession();
    if (session != null) {
      return Arrays.asList(session.getPeerCertificates());
    } else {
      return null;
    }
  }

  public String indicatedServerName() {
    if (chctx.channel().hasAttr(SslHandshakeCompletionHandler.SERVER_NAME_ATTR)) {
      return chctx.channel().attr(SslHandshakeCompletionHandler.SERVER_NAME_ATTR).get();
    } else {
      return null;
    }
  }

  public ChannelPromise channelFuture() {
    return chctx.newPromise();
  }

  public String remoteName() {
    java.net.SocketAddress addr = chctx.channel().remoteAddress();
    if (addr instanceof InetSocketAddress) {
      // Use hostString that does not trigger a DNS resolution
      return ((InetSocketAddress)addr).getHostString();
    }
    return null;
  }

  private SocketAddress channelRemoteAddress() {
    java.net.SocketAddress addr = chctx.channel().remoteAddress();

    if (chctx.channel() instanceof QuicChannel) {
      addr = ((QuicChannel) chctx.channel()).remoteSocketAddress();
    }

    return addr != null ? vertx.transport().convert(addr) : null;
  }

  private SocketAddress socketAdressOverride(AttributeKey<SocketAddress> key) {
    Channel ch = chctx.channel();
    return ch.hasAttr(key) ? ch.attr(key).getAndSet(null) : null;
  }

  public SocketAddress remoteAddress() {
    SocketAddress address = remoteAddress;
    if (address == null) {
      address = socketAdressOverride(REMOTE_ADDRESS_OVERRIDE);
      if (address == null) {
        address = channelRemoteAddress();
        if (address != null && address.isDomainSocket() && address.path().isEmpty()) {
          address = channelLocalAddress();
        }
      }
      if (address != null) {
        remoteAddress = address;
      }
    }
    return address;
  }

  public SocketAddress remoteAddress(boolean real) {
    if (real) {
      SocketAddress address = realRemoteAddress;
      if (address == null) {
        address = channelRemoteAddress();
      }
      if (address != null) {
        realRemoteAddress = address;
      }
      return address;
    } else {
      return remoteAddress();
    }
  }

  private SocketAddress channelLocalAddress() {
    java.net.SocketAddress addr = chctx.channel().localAddress();

    if (chctx.channel() instanceof QuicChannel) {
      addr = ((QuicChannel) chctx.channel()).remoteSocketAddress();
    }

    return addr != null ? vertx.transport().convert(addr) : null;
  }

  public SocketAddress localAddress() {
    SocketAddress address = localAddress;
    if (address == null) {
      address = socketAdressOverride(LOCAL_ADDRESS_OVERRIDE);
      if (address == null) {
        address = channelLocalAddress();
        if (address != null && address.isDomainSocket() && address.path().isEmpty()) {
          address = channelRemoteAddress();
        }
      }
      if (address != null) {
        localAddress = address;
      }
    }
    return address;
  }

  public SocketAddress localAddress(boolean real) {
    if (real) {
      SocketAddress address = realLocalAddress;
      if (address == null) {
        address = channelLocalAddress();
      }
      if (address != null) {
        realLocalAddress = address;
      }
      return address;
    } else {
      return localAddress();
    }
  }

  public VertxInternal vertx() {
    return vertx;
  }
}
