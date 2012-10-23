package org.vertx.java.core.jmx;

import org.vertx.java.core.net.NetServer;

public class NetServerProxy implements NetServerMXBean {

  private int port;

  private NetServer delegate;

  private String host;

  public NetServerProxy(NetServer delegate, String host, int port) {
    this.delegate = delegate;
    this.host = host;
    this.port = port;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void close() {
    delegate.close();
  }

}
