package org.vertx.java.core.jmx;

public interface HttpServerMXBean {

  String getHost();

  long getRequestCount();

  int getPort();

  String getObjectName();

}
