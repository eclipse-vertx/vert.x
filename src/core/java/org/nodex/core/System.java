package org.nodex.core;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:39
 * To change this template use File | Settings | File Templates.
 */
public class System {
  public static int setTimeout(Callback<?> callback, long delay) {
    return -1;
  }

  public static int setPeriodic(Callback<?> callback, long delay, long period) {
    return -1;
  }

  public static void cancelTimeout(int timeoutID) {

  }

  public static void nextTick(Callback<?> callback) {

  }

  public static void setCoreThreadPoolSize(int size) {

  }

  public void setBackgroundThreadPoolSize(int size) {

  }
}
