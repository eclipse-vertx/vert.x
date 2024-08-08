package io.vertx.it.transport;

import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.vertx.core.impl.transports.JDKTransport;
import io.vertx.core.spi.transport.Transport;

public class CustomTransport implements Transport {

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public Throwable unavailabilityCause() {
    return null;
  }

  @Override
  public IoHandlerFactory ioHandlerFactory() {
    return JDKTransport.INSTANCE.ioHandlerFactory();
  }

  @Override
  public DatagramChannel datagramChannel() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
    throw new UnsupportedOperationException();
  }
}
