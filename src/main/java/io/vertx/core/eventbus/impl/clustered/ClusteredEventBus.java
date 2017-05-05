/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.CodecManager;
import io.vertx.core.eventbus.impl.EventBusImpl;
import io.vertx.core.eventbus.impl.HandlerHolder;
import io.vertx.core.eventbus.impl.MessageImpl;
import io.vertx.core.impl.HAManager;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import io.vertx.core.spi.cluster.ClusterManager;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An event bus implementation that clusters with other Vert.x nodes
 *
 * @author <a href="http://tfox.org">Tim Fox</a>   7                                                                                     T
 */
// TODO: 16/12/25 by zmyer
public class ClusteredEventBus extends EventBusImpl {

    private static final Logger log = LoggerFactory.getLogger(ClusteredEventBus.class);

    public static final String CLUSTER_PUBLIC_HOST_PROP_NAME = "vertx.cluster.public.host";
    public static final String CLUSTER_PUBLIC_PORT_PROP_NAME = "vertx.cluster.public.port";

    private static final Buffer PONG = Buffer.buffer(new byte[]{(byte) 1});
    private static final String SERVER_ID_HA_KEY = "server_id";
    private static final String SUBS_MAP_NAME = "__vertx.subs";

    //集群管理对象
    private final ClusterManager clusterManager;
    //HA管理器对象
    private final HAManager haManager;
    //服务器连接管理列表
    private final ConcurrentMap<ServerID, ConnectionHolder> connections = new ConcurrentHashMap<>();
    //
    private final Context sendNoContext;

    //事件总线配置项
    private EventBusOptions options;
    //订阅关系映射表
    private AsyncMultiMap<String, ServerID> subs;
    //服务器Id
    private ServerID serverID;
    //底层网络服务器对象
    private NetServer server;

    // TODO: 16/12/25 by zmyer
    public ClusteredEventBus(VertxInternal vertx,
                             VertxOptions options,
                             ClusterManager clusterManager,
                             HAManager haManager) {
        super(vertx);
        this.options = options.getEventBusOptions();
        this.clusterManager = clusterManager;
        this.haManager = haManager;
        this.sendNoContext = vertx.getOrCreateContext();
        setNodeCrashedHandler(haManager);
    }

    // TODO: 16/12/25 by zmyer
    private NetServerOptions getServerOptions() {
        NetServerOptions serverOptions = new NetServerOptions(this.options.toJson());
        setCertOptions(serverOptions, options.getKeyCertOptions());
        setTrustOptions(serverOptions, options.getTrustOptions());

        return serverOptions;
    }

    // TODO: 16/12/25 by zmyer
    static void setCertOptions(TCPSSLOptions options, KeyCertOptions keyCertOptions) {
        if (keyCertOptions == null) {
            return;
        }
        if (keyCertOptions instanceof JksOptions) {
            options.setKeyStoreOptions((JksOptions) keyCertOptions);
        } else if (keyCertOptions instanceof PfxOptions) {
            options.setPfxKeyCertOptions((PfxOptions) keyCertOptions);
        } else {
            options.setPemKeyCertOptions((PemKeyCertOptions) keyCertOptions);
        }
    }

    // TODO: 16/12/25 by zmyer
    static void setTrustOptions(TCPSSLOptions sslOptions, TrustOptions options) {
        if (options == null) {
            return;
        }

        if (options instanceof JksOptions) {
            sslOptions.setTrustStoreOptions((JksOptions) options);
        } else if (options instanceof PfxOptions) {
            sslOptions.setPfxTrustOptions((PfxOptions) options);
        } else {
            sslOptions.setPemTrustOptions((PemTrustOptions) options);
        }
    }

    // TODO: 16/12/25 by zmyer
    @Override
    public void start(Handler<AsyncResult<Void>> resultHandler) {
        clusterManager.<String, ServerID>getAsyncMultiMap(SUBS_MAP_NAME, ar2 -> {
            if (ar2.succeeded()) {
                subs = ar2.result();
                //根据服务器配置创建网络服务器对象
                server = vertx.createNetServer(getServerOptions());
                //设置服务器连接处理对象
                server.connectHandler(getServerHandler());
                //服务器开始进入监听流程
                server.listen(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        //获取服务器端口号
                        int serverPort = getClusterPublicPort(options, server.actualPort());
                        //获取服务器的主机地址
                        String serverHost = getClusterPublicHost(options);
                        //根据服务器端口号与主机地址,创建服务器Id
                        serverID = new ServerID(serverPort, serverHost);
                        //将服务器信息插入到HA管理器对象中
                        haManager.addDataToAHAInfo(SERVER_ID_HA_KEY, new JsonObject().put("host", serverID.host).put("port", serverID.port));
                        if (resultHandler != null) {
                            //设置启动标记
                            started = true;
                            //开始进入到成功启动后续流程
                            resultHandler.handle(Future.succeededFuture());
                        }
                    } else {
                        if (resultHandler != null) {
                            //启动失败处理
                            resultHandler.handle(Future.failedFuture(asyncResult.cause()));
                        } else {
                            log.error(asyncResult.cause());
                        }
                    }
                });
            } else {
                if (resultHandler != null) {
                    //启动失败处理
                    resultHandler.handle(Future.failedFuture(ar2.cause()));
                } else {
                    log.error(ar2.cause());
                }
            }
        });
    }

    // TODO: 16/12/25 by zmyer
    @Override
    public void close(Handler<AsyncResult<Void>> completionHandler) {
        super.close(ar1 -> {
            if (server != null) {
                //关闭服务器对象
                server.close(ar -> {
                    if (ar.failed()) {
                        log.error("Failed to close server", ar.cause());
                    }
                    // Close all outbound connections explicitly - don't rely on context hooks
                    for (ConnectionHolder holder : connections.values()) {
                        //关闭所有的链接
                        holder.close();
                    }
                    if (completionHandler != null) {
                        completionHandler.handle(ar);
                    }
                });
            } else {
                if (completionHandler != null) {
                    completionHandler.handle(ar1);
                }
            }
        });

    }

    // TODO: 16/12/25 by zmyer
    @Override
    protected MessageImpl createMessage(boolean send, String address, MultiMap headers, Object body, String codecName) {
        Objects.requireNonNull(address, "no null address accepted");
        //查找消息编码器对象
        MessageCodec codec = codecManager.lookupCodec(body, codecName);
        //创建集群消息对象
        @SuppressWarnings("unchecked")
        ClusteredMessage msg = new ClusteredMessage(serverID, address, null, headers, body, codec, send, this);
        //返回消息对象
        return msg;
    }

    // TODO: 16/12/25 by zmyer
    @Override
    protected <T> void addRegistration(boolean newAddress, String address,
                                       boolean replyHandler, boolean localOnly,
                                       Handler<AsyncResult<Void>> completionHandler) {
        if (newAddress && subs != null && !replyHandler && !localOnly) {
            // Propagate the information
            subs.add(address, serverID, completionHandler);
        } else {
            completionHandler.handle(Future.succeededFuture());
        }
    }

    // TODO: 16/12/25 by zmyer
    @Override
    protected <T> void removeRegistration(HandlerHolder lastHolder, String address,
                                          Handler<AsyncResult<Void>> completionHandler) {
        if (lastHolder != null && subs != null && !lastHolder.isLocalOnly()) {
            removeSub(address, serverID, completionHandler);
        } else {
            callCompletionHandlerAsync(completionHandler);
        }
    }

    // TODO: 16/12/25 by zmyer
    @Override
    protected <T> void sendReply(SendContextImpl<T> sendContext, MessageImpl replierMessage) {
        //返回应答
        clusteredSendReply(((ClusteredMessage) replierMessage).getSender(), sendContext);
    }

    // TODO: 16/12/25 by zmyer
    @Override
    protected <T> void sendOrPub(SendContextImpl<T> sendContext) {
        String address = sendContext.message.address();
        Handler<AsyncResult<ChoosableIterable<ServerID>>> resultHandler = asyncResult -> {
            if (asyncResult.succeeded()) {
                ChoosableIterable<ServerID> serverIDs = asyncResult.result();
                if (serverIDs != null && !serverIDs.isEmpty()) {
                    sendToSubs(serverIDs, sendContext);
                } else {
                    metrics.messageSent(address, !sendContext.message.send(), true, false);
                    deliverMessageLocally(sendContext);
                }
            } else {
                log.error("Failed to send message", asyncResult.cause());
            }
        };
        if (Vertx.currentContext() == null) {
            // Guarantees the order when there is no current context
            sendNoContext.runOnContext(v -> {
                subs.get(address, resultHandler);
            });
        } else {
            subs.get(address, resultHandler);
        }
    }

    // TODO: 16/12/25 by zmyer
    @Override
    protected String generateReplyAddress() {
        // The address is a cryptographically secure id that can't be guessed
        return UUID.randomUUID().toString();
    }

    // TODO: 16/12/25 by zmyer
    @Override
    protected boolean isMessageLocal(MessageImpl msg) {
        ClusteredMessage clusteredMessage = (ClusteredMessage) msg;
        return !clusteredMessage.isFromWire();
    }

    // TODO: 16/12/25  by zmyer
    private void setNodeCrashedHandler(HAManager haManager) {
        haManager.setNodeCrashedHandler((failedNodeID, haInfo, failed) -> {
            JsonObject jsid = haInfo.getJsonObject(SERVER_ID_HA_KEY);
            if (jsid != null) {
                ServerID sid = new ServerID(jsid.getInteger("port"), jsid.getString("host"));
                if (subs != null) {
                    subs.removeAllForValue(sid, res -> {
                    });
                }
            }
        });
    }

    // TODO: 16/12/25 by zmyer
    private int getClusterPublicPort(EventBusOptions options, int actualPort) {
        // We retain the old system property for backwards compat
        int publicPort = Integer.getInteger(CLUSTER_PUBLIC_PORT_PROP_NAME, options.getClusterPublicPort());
        if (publicPort == -1) {
            // Get the actual port, wildcard port of zero might have been specified
            publicPort = actualPort;
        }
        return publicPort;
    }

    // TODO: 16/12/25 by zmyer
    private String getClusterPublicHost(EventBusOptions options) {
        // We retain the old system property for backwards compat
        String publicHost = System.getProperty(CLUSTER_PUBLIC_HOST_PROP_NAME, options.getClusterPublicHost());
        if (publicHost == null) {
            publicHost = options.getHost();
        }
        return publicHost;
    }

    // TODO: 16/12/25 by zmyer
    private Handler<NetSocket> getServerHandler() {
        return socket -> {
            RecordParser parser = RecordParser.newFixed(4, null);
            Handler<Buffer> handler = new Handler<Buffer>() {
                int size = -1;

                public void handle(Buffer buff) {
                    if (size == -1) {
                        size = buff.getInt(0);
                        parser.fixedSizeMode(size);
                    } else {
                        //创建集群消息对象
                        ClusteredMessage received = new ClusteredMessage();
                        //开始接收消息
                        received.readFromWire(buff, codecManager);
                        metrics.messageRead(received.address(), buff.length());
                        parser.fixedSizeMode(4);
                        size = -1;
                        if (received.codec() == CodecManager.PING_MESSAGE_CODEC) {
                            // Just send back pong directly on connection
                            //如果接受到的消息是ping,则需要应答pong
                            socket.write(PONG);
                        } else {
                            //开始在本地分发接收的消息
                            deliverMessageLocally(received);
                        }
                    }
                }
            };
            //设置消息输出处理对象
            parser.setOutput(handler);
            //设置socket对象的解析器
            socket.handler(parser);
        };
    }

    // TODO: 16/12/25 by zmyer
    private <T> void sendToSubs(ChoosableIterable<ServerID> subs, SendContextImpl<T> sendContext) {
        String address = sendContext.message.address();
        if (sendContext.message.send()) {
            // Choose one
            ServerID sid = subs.choose();
            if (!sid.equals(serverID)) {  //We don't send to this node
                metrics.messageSent(address, false, false, true);
                //向远端对象发送消息
                sendRemote(sid, sendContext.message);
            } else {
                metrics.messageSent(address, false, true, false);
                //直接本地处理消息
                deliverMessageLocally(sendContext);
            }
        } else {
            // Publish
            boolean local = false;
            boolean remote = false;
            //依次遍历每个远程的服务器节点,并发送指定的消息
            for (ServerID sid : subs) {
                if (!sid.equals(serverID)) {  //We don't send to this node
                    remote = true;
                    //发送消息
                    sendRemote(sid, sendContext.message);
                } else {
                    //本地模式
                    local = true;
                }
            }
            metrics.messageSent(address, true, local, remote);
            if (local) {
                //本地开始处理指定的消息
                deliverMessageLocally(sendContext);
            }
        }
    }

    // TODO: 16/12/25 by zmyer
    private <T> void clusteredSendReply(ServerID replyDest, SendContextImpl<T> sendContext) {
        MessageImpl message = sendContext.message;
        String address = message.address();
        if (!replyDest.equals(serverID)) {
            metrics.messageSent(address, false, false, true);
            //向发送端反馈应答消息
            sendRemote(replyDest, message);
        } else {
            metrics.messageSent(address, false, true, false);
            //本地处理应答消息
            deliverMessageLocally(sendContext);
        }
    }

    // TODO: 16/12/25 by zmyer
    private void sendRemote(ServerID theServerID, MessageImpl message) {
        // We need to deal with the fact that connecting can take some time and is async, and we cannot
        // block to wait for it. So we add any sends to a pending list if not connected yet.
        // Once we connect we send them.
        // This can also be invoked concurrently from different threads, so it gets a little
        // tricky
        //根据给定的服务器id,获取对应的连接对象
        ConnectionHolder holder = connections.get(theServerID);
        if (holder == null) {
            // When process is creating a lot of connections this can take some time
            // so increase the timeout
            //如果不存在该连接,则需要重新创建,并注册
            holder = new ConnectionHolder(this, theServerID, options);
            //将创建连接注册到连接列表中
            ConnectionHolder prevHolder = connections.putIfAbsent(theServerID, holder);
            if (prevHolder != null) {
                // Another one sneaked in
                holder = prevHolder;
            } else {
                //开始连接
                holder.connect();
            }
        }
        //在连接上发送消息
        holder.writeMessage((ClusteredMessage) message);
    }

    // TODO: 16/12/25 by zmyer
    private void removeSub(String subName, ServerID theServerID,
                           Handler<AsyncResult<Void>> completionHandler) {
        subs.remove(subName, theServerID, ar -> {
            if (!ar.succeeded()) {
                log.error("Failed to remove sub", ar.cause());
            } else {
                if (ar.result()) {
                    if (completionHandler != null) {
                        completionHandler.handle(Future.succeededFuture());
                    }
                } else {
                    if (completionHandler != null) {
                        completionHandler.handle(Future.failedFuture("sub not found"));
                    }
                }
            }
        });
    }

    // TODO: 16/12/25 by zmyer
    ConcurrentMap<ServerID, ConnectionHolder> connections() {
        return connections;
    }

    VertxInternal vertx() {
        return vertx;
    }

    EventBusOptions options() {
        return options;
    }
}

