package io.vertx.test.core;

import static org.junit.Assert.assertEquals;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.NettyTransportFactory;
import io.vertx.core.impl.Utils;
import io.vertx.core.impl.VertxInternal;

import org.junit.Test;

/**
 * test NettyTransportFactory class
 * 
 * @author <a href="http://vertxer.org">Mike Bai <lostitle@gmail.com></a>
 *
 */
public class NettyTransportFactoryTest {

  /**
   * test 4 method of NettyTransportFactory in GNU/Linux or other OS.
   * 
   * @throws Exception
   */
  @Test
  public void testFactory() throws Exception {

    NettyTransportFactory factory = NettyTransportFactory.getDefaultFactory();

    if (Utils.isLinux()) {
      factory.setNettyTransport(VertxOptions.NETTY_TRANSPORT_EPOLL);

      Class<? extends ServerChannel> serverSocketChannel = factory.chooseServerSocketChannel();
      Class<? extends Channel> socketChannel = factory.chooseSocketChannel();
      Class<? extends DatagramChannel> datagramChannel = factory.chooseDatagramChannel();
      DatagramChannel dcInstance = factory.instantiateDatagramChannel();
      Class<? extends EventLoopGroup> eventLoopGroup = factory.chooseEventLoopGroup();
      EventLoopGroup elgInstance = factory.instantiateEventLoopGroup(0, null);
      assertEquals(true, Utils.isLinux());
      assertEquals(EpollServerSocketChannel.class, serverSocketChannel);
      assertEquals(EpollSocketChannel.class, socketChannel);
      assertEquals(EpollDatagramChannel.class, datagramChannel);
      assertEquals(EpollDatagramChannel.class, dcInstance.getClass());
      assertEquals(EpollEventLoopGroup.class, eventLoopGroup);
      assertEquals(EpollEventLoopGroup.class, elgInstance.getClass());
    }

    {
      factory.setNettyTransport(VertxOptions.NETTY_TRANSPORT_NIO);

      Class<? extends ServerChannel> serverSocketChannel = factory.chooseServerSocketChannel();
      Class<? extends Channel> socketChannel = factory.chooseSocketChannel();
      Class<? extends DatagramChannel> datagramChannel = factory.chooseDatagramChannel();
      DatagramChannel dcInstance = factory.instantiateDatagramChannel();
      Class<? extends EventLoopGroup> eventLoopGroup = factory.chooseEventLoopGroup();
      EventLoopGroup elgInstance = factory.instantiateEventLoopGroup(0, null);
      assertEquals(NioServerSocketChannel.class, serverSocketChannel);
      assertEquals(NioSocketChannel.class, socketChannel);
      assertEquals(NioDatagramChannel.class, datagramChannel);
      assertEquals(NioDatagramChannel.class, dcInstance.getClass());
      assertEquals(NioEventLoopGroup.class, eventLoopGroup);
      assertEquals(NioEventLoopGroup.class, elgInstance.getClass());

    }
  }

  @Test
  public void testVertxWithOption() {
    VertxOptions options = new VertxOptions().setNettyTransport(VertxOptions.NETTY_TRANSPORT_EPOLL);
    Vertx vertx = Vertx.vertx(options);
    if (Utils.isLinux()) {
      assertEquals(VertxOptions.NETTY_TRANSPORT_EPOLL, ((VertxInternal) vertx).getNettyTransportFactory()
          .getNettyTransport());
    } else {
      assertEquals(VertxOptions.NETTY_TRANSPORT_NIO, ((VertxInternal) vertx).getNettyTransportFactory()
          .getNettyTransport());
    }
  }

}
