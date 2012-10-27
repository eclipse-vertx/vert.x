package org.vertx.java.core.jmx;

import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.impl.DefaultHttpServer;


public class HttpServerProxy implements HttpServerMXBean {

  private DefaultHttpServer delegate;

  private int port;

  private String host;

  public HttpServerProxy(DefaultHttpServer delegate, String host, int port) {
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
  public long getRequestCount() {
    return delegate.getRequestCount();
  }

  @Override
  public String getObjectName() {
    return String.format("org.vertx:type=HttpServer,name=%s[%s]", host, port);
  }

}
