package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.impl.codecs.PingMessageCodec;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetClientImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.spi.metrics.EventBusMetrics;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/25 by zmyer
class ConnectionHolder {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHolder.class);
    //ping地址
    private static final String PING_ADDRESS = "__vertx_ping";
    //集群事件总线对象
    private final ClusteredEventBus eventBus;
    //底层网络客户端对象
    private final NetClient client;
    //服务器Id
    private final ServerID serverID;
    //vertx节点对象
    private final Vertx vertx;
    //事件总线统计对象
    private final EventBusMetrics metrics;
    //挂起的集群消息队列
    private Queue<ClusteredMessage> pending;
    //socket对象
    private NetSocket socket;
    //是否连接
    private boolean connected;
    private long timeoutID = -1;
    private long pingTimeoutID = -1;

    // TODO: 16/12/25 by zmyer
    ConnectionHolder(ClusteredEventBus eventBus, ServerID serverID, EventBusOptions options) {
        this.eventBus = eventBus;
        this.serverID = serverID;
        this.vertx = eventBus.vertx();
        this.metrics = eventBus.getMetrics();
        NetClientOptions clientOptions = new NetClientOptions(options.toJson());
        ClusteredEventBus.setCertOptions(clientOptions, options.getKeyCertOptions());
        ClusteredEventBus.setTrustOptions(clientOptions, options.getTrustOptions());
        client = new NetClientImpl(eventBus.vertx(), clientOptions, false);
    }

    // TODO: 16/12/25 by zmyer
    synchronized void connect() {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        //开始连接服务器
        client.connect(serverID.port, serverID.host, res -> {
            if (res.succeeded()) {
                //连接成功
                connected(res.result());
            } else {
                //连接失败,则关闭
                close();
            }
        });
    }

    // TODO: 16/12/25 by zmyer
    // TODO optimise this (contention on monitor)
    synchronized void writeMessage(ClusteredMessage message) {
        if (connected) {
            //首先将需要发送的消息进行编码
            Buffer data = message.encodeToWire();
            //更新统计日志
            metrics.messageWritten(message.address(), data.length());
            //从socket对象中发送消息
            socket.write(data);
        } else {
            if (pending == null) {
                //如果挂起的消息队列为空,则需要重新创建
                pending = new ArrayDeque<>();
            }
            //将待发送的消息插入到挂起队列中
            pending.add(message);
        }
    }

    // TODO: 16/12/25 by zmyer
    void close() {
        if (timeoutID != -1) {
            //取消定时器
            vertx.cancelTimer(timeoutID);
        }
        if (pingTimeoutID != -1) {
            //取消ping定时器
            vertx.cancelTimer(pingTimeoutID);
        }
        try {
            //关闭客户端
            client.close();
        } catch (Exception ignore) {
        }
        // The holder can be null or different if the target server is restarted with same serverid
        // before the cleanup for the previous one has been processed
        if (eventBus.connections().remove(serverID, this)) {
            log.debug("Cluster connection closed: " + serverID + " holder " + this);
        }
    }

    // TODO: 16/12/25 by zmyer
    private void schedulePing() {
        //获取事件总线配置项
        EventBusOptions options = eventBus.options();
        //创建ping定时器对象
        pingTimeoutID = vertx.setTimer(options.getClusterPingInterval(), id1 -> {
            // If we don't get a pong back in time we close the connection
            timeoutID = vertx.setTimer(options.getClusterPingReplyInterval(), id2 -> {
                // Didn't get pong in time - consider connection dead
                log.warn("No pong from server " + serverID + " - will consider it dead");
                close();
            });
            //创建ping消息
            ClusteredMessage pingMessage =
                    new ClusteredMessage<>(serverID, PING_ADDRESS, null, null, null, new PingMessageCodec(), true, eventBus);
            Buffer data = pingMessage.encodeToWire();
            //开始发送ping消息
            socket.write(data);
        });
    }

    // TODO: 16/12/25 by zmyer
    private synchronized void connected(NetSocket socket) {
        //设置连接成功的socket对象
        this.socket = socket;
        //设置成功连接标记
        connected = true;
        //设置异常处理对象
        socket.exceptionHandler(t -> close());
        //设置关闭处理对象
        socket.closeHandler(v -> close());
        //设置socket的处理对象
        socket.handler(data -> {
            // Got a pong back
            //取消指定的定时器
            vertx.cancelTimer(timeoutID);
            //开始调度ping消息
            schedulePing();
        });
        // Start a pinger
        //调度ping消息
        schedulePing();
        for (ClusteredMessage message : pending) {
            Buffer data = message.encodeToWire();
            metrics.messageWritten(message.address(), data.length());
            //开始发送集群消息
            socket.write(data);
        }
        pending.clear();
    }

}
