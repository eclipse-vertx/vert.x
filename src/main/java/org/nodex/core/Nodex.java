package org.nodex.core;

/**
 * User: timfox
 * Date: 20/07/2011
 * Time: 18:28
 */
public interface Nodex {
  static Nodex instance = NodexInternal.instance;

  void setCoreThreadPoolSize(int size);

  int getCoreThreadPoolSize();

  void setBackgroundThreadPoolSize(int size);

  int getBackgroundThreadPoolSize();

  void executeInBackground(Runnable task);

  long setTimeout(long delay, DoneHandler handler);

  long setPeriodic(long delay, DoneHandler handler);

  boolean cancelTimeout(long id);

  <T> String registerActor(T t);

  <T> T getActorRef(String actorID);

  String getContextID();
}
