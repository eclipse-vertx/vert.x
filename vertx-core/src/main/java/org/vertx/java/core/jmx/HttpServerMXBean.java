package org.vertx.java.core.jmx;

public interface HttpServerMXBean {

  String getHost();

  int getPort();

  void close();

}
