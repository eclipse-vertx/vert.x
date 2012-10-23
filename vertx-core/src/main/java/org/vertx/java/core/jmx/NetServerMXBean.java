package org.vertx.java.core.jmx;

public interface NetServerMXBean {

  String getHost();

  int getPort();

  void close();

}
