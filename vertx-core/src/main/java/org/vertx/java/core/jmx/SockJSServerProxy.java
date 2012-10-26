package org.vertx.java.core.jmx;

import org.vertx.java.core.sockjs.impl.DefaultSockJSServer;

public class SockJSServerProxy implements SockJSServerMXBean {

  private DefaultSockJSServer server;
  private String host;
  private int port;

  public SockJSServerProxy(DefaultSockJSServer server, String host, int port) {
    this.server = server;
    this.host = host;
    this.port = port;
  }

  @Override
  public String getObjectName() {
    return String.format("org.vertx:type=SockJSServer,name=%s[%s]", host, port);
  }

}
