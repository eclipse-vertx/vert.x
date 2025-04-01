package io.vertx.tests.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.impl.Http3ProxyProvider;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.proxy.Socks4Proxy;
import io.vertx.test.proxy.SocksProxy;
import io.vertx.tests.http.HttpOptionsFactory;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import static io.vertx.test.http.HttpTestBase.*;

public class Http3ProxyProviderTest extends ProxyProviderTest {

  protected NetServerOptions createNetServerOptions() {
    return HttpOptionsFactory.createH3NetServerOptions().setHost(testAddress.hostAddress()).setPort(testAddress.port());
  }

  protected NetClientOptions createNetClientOptions() {
    return HttpOptionsFactory.createH3NetClientOptions();
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createH3HttpServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createH3HttpClientOptions();
  }


  @Category(Http3ProxyProvider.class)
  @Test
  public void testVertxBasedSocks5Proxy() throws Exception {
    Http3ProxyProvider.IS_NETTY_BASED_PROXY = false;
    testProxy_(ProxyType.SOCKS5);
  }

  //TODO: This method is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  @Category(Http3ProxyProvider.class)
  @Test
  public void testNettyBasedSocks5Proxy() throws Exception {
    Http3ProxyProvider.IS_NETTY_BASED_PROXY = true;
    testProxy_(ProxyType.SOCKS5);
  }

  @Category(Http3ProxyProvider.class)
  @Test
  public void testVertxBasedSocks4Proxy() throws Exception {
    Http3ProxyProvider.IS_NETTY_BASED_PROXY = false;
    testProxy_(ProxyType.SOCKS4);
  }

  //TODO: This method is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  @Category(Http3ProxyProvider.class)
  @Test
  public void testNettyBasedSocks4Proxy() throws Exception {
    Http3ProxyProvider.IS_NETTY_BASED_PROXY = true;
    testProxy_(ProxyType.SOCKS4);
  }

  @Category(Http3ProxyProvider.class)
  @Test
  public void testVertxBasedHttpProxy() throws Exception {
    Http3ProxyProvider.IS_NETTY_BASED_PROXY = false;
    testProxy_(ProxyType.HTTP);
  }

  //TODO: This method is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  @Category(Http3ProxyProvider.class)
  @Ignore("It is not possible to use an HTTP proxy without modifying Netty.")
  @Test
  public void testNettyBasedHttpProxy() throws Exception {
    Http3ProxyProvider.IS_NETTY_BASED_PROXY = true;
    testProxy_(ProxyType.HTTP);
  }

  /**
   * This test case simulates the server, proxy server, and client, establishes connections between them, and verifies
   * their functionality. It directly uses Http3ProxyProvider.
   */
  private void testProxy_(ProxyType proxyType) throws Exception {
    log.info("Proxy test is running with proxyType: " + proxyType);
    waitFor(4);
    String clientText = "Hi, I'm client!";
    String serverText = "Hi, I'm server";

    CountDownLatch latch = new CountDownLatch(2);

    // Start of server part
    server.connectHandler((NetSocket sock) -> {
      log.info("Created socket on server!");
      complete();
      sock.handler(buffer -> {
        log.info("Client msg is: " + buffer.toString(StandardCharsets.UTF_8));
        assertEquals(clientText, buffer.toString(StandardCharsets.UTF_8));
        sock.write(serverText);
        complete();
      });
    });
    server.listen(1234, "localhost").onComplete(onSuccess(v -> {
      log.info("Server started!");
      latch.countDown();
    }));


    // Start of proxy server part
    switch (proxyType){
      case HTTP:
        proxy = new HttpProxy().http3(true);
        break;
      case SOCKS4:
        proxy = new Socks4Proxy().http3(true);
        break;
      case SOCKS5:
        proxy = new SocksProxy().http3(true);
        break;
      default:
        throw new RuntimeException("Not Supported!");
    }

    proxy.startAsync(vertx).onComplete(onSuccess(v -> {
      latch.countDown();
      log.info("Proxy started!");
    }));

    awaitLatch(latch);

    // Start of client part
    Http3ProxyProvider proxyProvider = new Http3ProxyProvider(((VertxInternal)vertx).getOrCreateContext().nettyEventLoop());

    InetSocketAddress proxyAddress = new InetSocketAddress("localhost", proxy.port());
    InetSocketAddress remoteAddress = new InetSocketAddress("localhost", server.actualPort());

    ProxyOptions proxyOptions = new ProxyOptions().setType(proxyType);
    proxyProvider.createProxyQuicChannel(proxyAddress, remoteAddress, proxyOptions)
      .addListener((GenericFutureListener<Future<Channel>>) channelFuture -> {
        if (!channelFuture.isSuccess()) {
          fail(channelFuture.cause());
        }
        Channel quicChannel = channelFuture.get();
        quicChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
          @Override
          public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf msg0 = (ByteBuf) msg;
            byte[] arr = new byte[msg0.readableBytes()];
            msg0.copy().readBytes(arr);
            log.info("Server msg is : " + new String(arr));
            assertEquals(serverText, new String(arr));
            assertTrue("localhost:1234".equals(proxy.getLastUri()) || "127.0.0.1:1234".equals(proxy.getLastUri()) );
            complete();
            super.channelRead(ctx, msg);
          }
        });
        quicChannel.writeAndFlush(Unpooled.copiedBuffer(clientText.getBytes(StandardCharsets.UTF_8)))
          .addListener(future -> {
            assertTrue(future.isSuccess());
            log.info("Sending a message to proxy server...");
            complete();
          });
      });
    await();
  }

}
