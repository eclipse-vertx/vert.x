package org.vertx.java.core.impl;


public interface ExecutorServiceMXBean {

  void setCorePoolSize(int corePoolSize);

  int getCorePoolSize();

  void setMaximumPoolSize(int maximumPoolSize);

  int getMaximumPoolSize();

  int getPoolSize();

  int getActiveCount();

  int getLargestPoolSize();

  int getWorkQueueSize();

  long getWaitingTasks();

  long getTaskCount();

  long getCompletedTaskCount();

}
