/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.net.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.*;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.NetSocketStream;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 17/1/1 by zmyer
public class NetServerImpl implements NetServer, Closeable, MetricsProvider {

    private static final Logger log = LoggerFactory.getLogger(NetServerImpl.class);
    //vertx节点对象
    private final VertxInternal vertx;
    //服务器配置项
    private final NetServerOptions options;
    //执行上下文对象
    private final ContextImpl creatingContext;
    //SSL
    private final SSLHelper sslHelper;
    //socket映射表
    private final Map<Channel, NetSocketImpl> socketMap = new ConcurrentHashMap<>();
    //
    private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
    //事件处理器管理器
    private final HandlerManager<Handler<NetSocket>> handlerManager = new HandlerManager<>(availableWorkers);
    //监听队列
    private final Queue<Runnable> bindListeners = new LinkedList<>();
    //连接流对象
    private final NetSocketStreamImpl connectStream = new NetSocketStreamImpl();
    //日志开关
    private final boolean logEnabled;
    //服务器通道组对象
    private ChannelGroup serverChannelGroup;
    //是否监听中
    private volatile boolean listening;
    //服务器ID
    private volatile ServerID id;
    //服务器对象
    private NetServerImpl actualServer;
    //
    private AsyncResolveConnectHelper bindFuture;
    //端口号
    private volatile int actualPort;
    //执行上下文对象
    private ContextImpl listenContext;
    //tcp统计对象
    private TCPMetrics metrics;

    // TODO: 17/1/1 by zmyer
    public NetServerImpl(VertxInternal vertx, NetServerOptions options) {
        this.vertx = vertx;
        this.options = new NetServerOptions(options);
        this.sslHelper = new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions());
        this.creatingContext = vertx.getContext();
        this.logEnabled = options.getLogActivity();
        if (creatingContext != null) {
            if (creatingContext.isMultiThreadedWorkerContext()) {
                throw new IllegalStateException("Cannot use NetServer in a multi-threaded worker verticle");
            }
            creatingContext.addCloseHook(this);
        }
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetServer connectHandler(Handler<NetSocket> handler) {
        //
        connectStream.handler(handler);
        return this;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public Handler<NetSocket> connectHandler() {
        return connectStream.handler();
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetSocketStream connectStream() {
        return connectStream;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetServer listen(int port, String host) {
        return listen(port, host, null);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetServer listen(int port) {
        return listen(port, "0.0.0.0", null);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetServer listen(int port, Handler<AsyncResult<NetServer>> listenHandler) {
        return listen(port, "0.0.0.0", listenHandler);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetServer listen() {
        listen(null);
        return this;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized NetServer listen(Handler<AsyncResult<NetServer>> listenHandler) {
        return listen(options.getPort(), options.getHost(), listenHandler);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized NetServer listen(int port, String host, Handler<AsyncResult<NetServer>> listenHandler) {
        if (connectStream.handler() == null) {
            throw new IllegalStateException("Set connect handler first");
        }
        if (listening) {
            throw new IllegalStateException("Listen already called");
        }

        //开始进入监听流程
        listening = true;

        //从vertx节点中创建监听执行上下文对象
        listenContext = vertx.getOrCreateContext();

        synchronized (vertx.sharedNetServers()) {
            //端口号
            this.actualPort = port; // Will be updated on bind for a wildcard port
            //根据端口号和主机名创建服务器ID
            id = new ServerID(port, host);
            //根据服务器Id,获取服务器对象
            NetServerImpl shared = vertx.sharedNetServers().get(id);
            if (shared == null || port == 0) { // Wildcard port will imply a new actual server each time
                //创建通道组对象
                serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels", GlobalEventExecutor.INSTANCE);
                //服务器引导对象
                ServerBootstrap bootstrap = new ServerBootstrap();
                //注册工作组对象
                bootstrap.group(availableWorkers);
                //注册通道类型
                bootstrap.channel(NioServerSocketChannel.class);
                sslHelper.validate(vertx);

                bootstrap.childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        //连接流暂停
                        if (connectStream.isPaused()) {
                            //关闭连接通道
                            ch.close();
                            return;
                        }
                        ChannelPipeline pipeline = ch.pipeline();
                        if (sslHelper.isSSL()) {
                            //注册SSL事件处理器
                            SslHandler sslHandler = sslHelper.createSslHandler(vertx);
                            pipeline.addLast("ssl", sslHandler);
                        }
                        if (logEnabled) {
                            //注册日志处理器
                            pipeline.addLast("logging", new LoggingHandler());
                        }
                        if (sslHelper.isSSL()) {
                            // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
                            pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
                        }
                        if (options.getIdleTimeout() > 0) {
                            //注册空闲状态事件处理器
                            pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
                        }
                        //注册服务器事件处理器
                        pipeline.addLast("handler", new ServerHandler(ch));
                    }
                });

                //设置连接配置属性
                applyConnectionOptions(bootstrap);

                if (connectStream.handler() != null) {
                    handlerManager.addHandler(connectStream.handler(), listenContext);
                }

                try {
                    //绑定服务器地址
                    bindFuture = AsyncResolveConnectHelper.doBind(vertx, port, host, bootstrap);
                    //为绑定异步对象设置监听器
                    bindFuture.addListener(res -> {
                        if (res.succeeded()) {
                            //获取通道对象
                            Channel ch = res.result();
                            log.trace("Net server listening on " + host + ":" + ch.localAddress());
                            // Update port to actual port - wildcard port 0 might have been used
                            //从通道对象中读取端口号
                            NetServerImpl.this.actualPort = ((InetSocketAddress) ch.localAddress()).getPort();
                            //创建服务器ID
                            NetServerImpl.this.id = new ServerID(NetServerImpl.this.actualPort, id.host);
                            //注册通道对象
                            serverChannelGroup.add(ch);
                            //在共享服务器列表中注册服务器对象
                            vertx.sharedNetServers().put(id, NetServerImpl.this);
                            metrics = vertx.metricsSPI().createMetrics(this, new SocketAddressImpl(id.port, id.host), options);
                        } else {
                            //绑定失败,则需要将其从共享服务器列表中删除
                            vertx.sharedNetServers().remove(id);
                        }
                    });

                } catch (Throwable t) {
                    // Make sure we send the exception back through the handler (if any)
                    if (listenHandler != null) {
                        vertx.runOnContext(v -> listenHandler.handle(Future.failedFuture(t)));
                    } else {
                        // No handler - log so user can see failure
                        log.error(t);
                    }
                    listening = false;
                    return this;
                }
                if (port != 0) {
                    //注册新创建的服务器
                    vertx.sharedNetServers().put(id, this);
                }
                actualServer = this;
            } else {
                // Server already exists with that host/port - we will use that
                actualServer = shared;
                this.actualPort = shared.actualPort();
                metrics = vertx.metricsSPI().createMetrics(this, new SocketAddressImpl(id.port, id.host), options);
                if (connectStream.handler() != null) {
                    //注册连接流事件处理器
                    actualServer.handlerManager.addHandler(connectStream.handler(), listenContext);
                }
            }

            // just add it to the future so it gets notified once the bind is complete
            //设置绑定监听器
            actualServer.bindFuture.addListener(res -> {
                if (listenHandler != null) {
                    AsyncResult<NetServer> ares;
                    if (res.succeeded()) {
                        ares = Future.succeededFuture(NetServerImpl.this);
                    } else {
                        listening = false;
                        ares = Future.failedFuture(res.cause());
                    }
                    // Call with expectRightThread = false as if server is already listening
                    // Netty will call future handler immediately with calling thread
                    // which might be a non Vert.x thread (if running embedded)
                    listenContext.runOnContext(v -> listenHandler.handle(ares));
                } else if (res.failed()) {
                    // No handler - log so user can see failure
                    log.error("Failed to listen", res.cause());
                    listening = false;
                }
            });
        }
        return this;
    }

    public synchronized void close() {
        close(null);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized void close(Handler<AsyncResult<Void>> done) {
        if (connectStream.endHandler() != null) {
            Handler<Void> endHandler = connectStream.endHandler;
            connectStream.endHandler = null;
            Handler<AsyncResult<Void>> next = done;
            done = new AsyncResultHandler<Void>() {
                @Override
                public void handle(AsyncResult<Void> event) {
                    if (event.succeeded()) {
                        endHandler.handle(event.result());
                    }
                    if (next != null) {
                        next.handle(event);
                    }
                }
            };
        }

        ContextImpl context = vertx.getOrCreateContext();
        if (!listening) {
            if (done != null) {
                executeCloseDone(context, done, null);
            }
            return;
        }
        listening = false;
        synchronized (vertx.sharedNetServers()) {

            if (actualServer != null) {
                actualServer.handlerManager.removeHandler(connectStream.handler(), listenContext);

                if (actualServer.handlerManager.hasHandlers()) {
                    // The actual server still has handlers so we don't actually close it
                    if (done != null) {
                        executeCloseDone(context, done, null);
                    }
                } else {
                    // No Handlers left so close the actual server
                    // The done handler needs to be executed on the context that calls close, NOT the context
                    // of the actual server
                    actualServer.actualClose(context, done);
                }
            }
        }
        if (creatingContext != null) {
            creatingContext.removeCloseHook(this);
        }
    }

    @Override
    public synchronized int actualPort() {
        return actualPort;
    }

    @Override
    public boolean isMetricsEnabled() {
        return metrics != null && metrics.isEnabled();
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    // TODO: 17/1/1 by zmyer
    private void applyConnectionOptions(ServerBootstrap bootstrap) {
        bootstrap.childOption(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
        if (options.getSendBufferSize() != -1) {
            bootstrap.childOption(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
        }
        if (options.getReceiveBufferSize() != -1) {
            bootstrap.childOption(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
            bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
        }
        if (options.getSoLinger() != -1) {
            bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
        }
        if (options.getTrafficClass() != -1) {
            bootstrap.childOption(ChannelOption.IP_TOS, options.getTrafficClass());
        }
        bootstrap.childOption(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);

        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
        bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
        if (options.getAcceptBacklog() != -1) {
            bootstrap.option(ChannelOption.SO_BACKLOG, options.getAcceptBacklog());
        }
    }

    // TODO: 17/1/1 by zmyer
    private void actualClose(ContextImpl closeContext, Handler<AsyncResult<Void>> done) {
        if (id != null) {
            vertx.sharedNetServers().remove(id);
        }

        ContextImpl currCon = vertx.getContext();

        for (NetSocketImpl sock : socketMap.values()) {
            sock.close();
        }

        // Sanity check
        if (vertx.getContext() != currCon) {
            throw new IllegalStateException("Context was changed");
        }

        ChannelGroupFuture fut = serverChannelGroup.close();
        fut.addListener(cg -> {
            if (metrics != null) {
                metrics.close();
            }
            executeCloseDone(closeContext, done, fut.cause());
        });

    }

    // TODO: 17/1/1 by zmyer
    private void executeCloseDone(ContextImpl closeContext, Handler<AsyncResult<Void>> done, Exception e) {
        if (done != null) {
            Future<Void> fut = e == null ? Future.succeededFuture() : Future.failedFuture(e);
            closeContext.runOnContext(v -> done.handle(fut));
        }
    }

    // TODO: 17/1/1 by zmyer
    private class ServerHandler extends VertxNetHandler {
        public ServerHandler(Channel ch) {
            super(ch, socketMap);
        }

        // TODO: 17/1/1 by zmyer
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            //读取通道对象
            Channel ch = ctx.channel();
            //读取事件循环对象
            EventLoop worker = ch.eventLoop();

            //Choose a handler
            //根据提供的事件循环对象读取对应的事件对象
            HandlerHolder<Handler<NetSocket>> handler = handlerManager.chooseHandler(worker);
            if (handler == null) {
                //Ignore
                return;
            }

            if (sslHelper.isSSL()) {
                //SSL事件处理器对象
                SslHandler sslHandler = ch.pipeline().get(SslHandler.class);

                io.netty.util.concurrent.Future<Channel> fut = sslHandler.handshakeFuture();
                //添加监听器
                fut.addListener(future -> {
                    if (future.isSuccess()) {
                        //连接成功
                        connected(ch, handler);
                    } else {
                        log.error("Client from origin " + ch.remoteAddress() + " failed to connect over ssl: " + future.cause());
                    }
                });
            } else {
                //连接成功
                connected(ch, handler);
            }
        }

        // TODO: 17/1/1 by zmyer
        private void connected(Channel ch, HandlerHolder<Handler<NetSocket>> handler) {
            // Need to set context before constructor is called as writehandler registration needs this
            //设置执行上下文对象
            ContextImpl.setContext(handler.context);
            //创建socket对象
            NetSocketImpl sock = new NetSocketImpl(vertx, ch, handler.context, sslHelper, metrics, null);
            //注册socket对象
            socketMap.put(ch, sock);
            VertxNetHandler netHandler = ch.pipeline().get(VertxNetHandler.class);
            netHandler.conn = sock;
            handler.context.executeFromIO(() -> {
                sock.setMetric(metrics.connected(sock.remoteAddress(), sock.remoteName()));
                handler.handler.handle(sock);
            });
        }
    }

    // TODO: 17/1/1 by zmyer
    @Override
    protected void finalize() throws Throwable {
        // Make sure this gets cleaned up if there are no more references to it
        // so as not to leave connections and resources dangling until the system is shutdown
        // which could make the JVM run out of file handles.
        close();
        super.finalize();
    }

    /*
      Needs to be protected using the NetServerImpl monitor as that protects the listening variable
      In practice synchronized overhead should be close to zero assuming most access is from the same thread due
      to biased locks
    */
    // TODO: 17/1/1 by zmyer
    private class NetSocketStreamImpl implements NetSocketStream {
        //事件处理器对象
        private Handler<NetSocket> handler;
        //是否暂停
        private boolean paused;
        //结束事件处理器对象
        private Handler<Void> endHandler;

        Handler<NetSocket> handler() {
            synchronized (NetServerImpl.this) {
                return handler;
            }
        }

        boolean isPaused() {
            synchronized (NetServerImpl.this) {
                return paused;
            }
        }

        Handler<Void> endHandler() {
            synchronized (NetServerImpl.this) {
                return endHandler;
            }
        }

        // TODO: 17/1/1 by zmyer
        @Override
        public NetSocketStreamImpl handler(Handler<NetSocket> handler) {
            synchronized (NetServerImpl.this) {
                if (listening) {
                    throw new IllegalStateException("Cannot set connectHandler when server is listening");
                }
                this.handler = handler;
                return this;
            }
        }

        @Override
        public NetSocketStreamImpl pause() {
            synchronized (NetServerImpl.this) {
                if (!paused) {
                    paused = true;
                }
                return this;
            }
        }

        @Override
        public NetSocketStreamImpl resume() {
            synchronized (NetServerImpl.this) {
                if (paused) {
                    paused = false;
                }
                return this;
            }
        }

        @Override
        public NetSocketStreamImpl endHandler(Handler<Void> endHandler) {
            synchronized (NetServerImpl.this) {
                this.endHandler = endHandler;
                return this;
            }
        }

        @Override
        public NetSocketStreamImpl exceptionHandler(Handler<Throwable> handler) {
            // Should we use it in the server close exception handler ?
            return this;
        }
    }
}
