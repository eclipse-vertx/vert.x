package org.vertx.java.core.jmx;

import org.vertx.java.core.http.HttpServer;


public class HttpServerProxy implements HttpServerMXBean {

  private HttpServer delegate;

  private int port;

  private String host;

  public HttpServerProxy(HttpServer delegate, String host, int port) {
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
