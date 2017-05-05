package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.proxy.*;
import io.netty.resolver.NoopAddressResolverGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * A channel provider that connects via a Proxy : HTTP or SOCKS
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
// TODO: 17/1/1 by zmyer
public class ProxyChannelProvider extends ChannelProvider {
    //通道提供对象
    public static final ChannelProvider INSTANCE = new ProxyChannelProvider();

    private ProxyChannelProvider() {
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public void connect(VertxInternal vertx, Bootstrap bootstrap, ProxyOptions options, String host, int port,
                        Handler<Channel> channelInitializer, Handler<AsyncResult<Channel>> channelHandler) {

        //获取代理主机名
        final String proxyHost = options.getHost();
        //获取主机端口号
        final int proxyPort = options.getPort();
        //用户名
        final String proxyUsername = options.getUsername();
        //密码
        final String proxyPassword = options.getPassword();
        //代理类型
        final ProxyType proxyType = options.getType();

        vertx.resolveAddress(proxyHost, dnsRes -> {
            if (dnsRes.succeeded()) {
                InetAddress address = dnsRes.result();
                //代理服务器地址
                InetSocketAddress proxyAddr = new InetSocketAddress(address, proxyPort);
                //代理事件处理对象
                ProxyHandler proxy;

                switch (proxyType) {
                    default:
                    case HTTP:
                        //创建HTTP代理事件处理器
                        proxy = proxyUsername != null && proxyPassword != null
                                ? new HttpProxyHandler(proxyAddr, proxyUsername, proxyPassword) : new HttpProxyHandler(proxyAddr);
                        break;
                    case SOCKS5:
                        //创建SOCKS5代理事件处理器
                        proxy = proxyUsername != null && proxyPassword != null
                                ? new Socks5ProxyHandler(proxyAddr, proxyUsername, proxyPassword) : new Socks5ProxyHandler(proxyAddr);
                        break;
                    case SOCKS4:
                        //创建SOCK4
                        // SOCKS4 only supports a username and could authenticate the user via Ident
                        proxy = proxyUsername != null ? new Socks4ProxyHandler(proxyAddr, proxyUsername)
                                : new Socks4ProxyHandler(proxyAddr);
                        break;
                }

                bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
                InetSocketAddress targetAddress = InetSocketAddress.createUnresolved(host, port);

                bootstrap.handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        //将代理事件处理器注册到通道对象上
                        pipeline.addFirst("proxy", proxy);
                        pipeline.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (evt instanceof ProxyConnectionEvent) {
                                    pipeline.remove(proxy);
                                    pipeline.remove(this);
                                    channelInitializer.handle(ch);
                                    channelHandler.handle(Future.succeededFuture(ch));
                                }
                                ctx.fireUserEventTriggered(evt);
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                    throws Exception {
                                channelHandler.handle(Future.failedFuture(cause));
                            }
                        });
                    }
                });

                //开始连接目标服务器
                ChannelFuture future = bootstrap.connect(targetAddress);

                //注册监听器
                future.addListener(res -> {
                    if (!res.isSuccess()) {
                        //连接失败
                        channelHandler.handle(Future.failedFuture(res.cause()));
                    }
                });
            } else {
                //连接失败
                channelHandler.handle(Future.failedFuture(dnsRes.cause()));
            }
        });
    }
}
