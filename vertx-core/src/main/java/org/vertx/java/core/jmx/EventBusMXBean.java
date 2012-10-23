package org.vertx.java.core.jmx;

public interface EventBusMXBean {

  long getSentMessageCount();

  long getReceivedMessageCount();

}
