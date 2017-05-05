package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.ProxyOptions;

/**
 * The logic for connecting to an host, this implementations performs a connection
 * to the host after resolving its internet address.
 * <p>
 * See if we can replace that by a Netty handler sometimes.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
// TODO: 17/1/1 by zmyer
public class ChannelProvider {

    //通道提供者对象
    public static final ChannelProvider INSTANCE = new ChannelProvider();

    protected ChannelProvider() {
    }

    // TODO: 17/1/1 by zmyer
    public void connect(VertxInternal vertx, Bootstrap bootstrap, ProxyOptions options, String host, int port,
                        Handler<Channel> channelInitializer, Handler<AsyncResult<Channel>> channelHandler) {
        bootstrap.resolver(vertx.nettyAddressResolverGroup());
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                //初始化通道对象
                channelInitializer.handle(channel);
            }
        });

        //开始连接服务器
        ChannelFuture fut = bootstrap.connect(host, port);
        fut.addListener(res -> {
            if (res.isSuccess()) {
                //连接成功
                channelHandler.handle(Future.succeededFuture(fut.channel()));
            } else {
                //连接失败
                channelHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }
}
