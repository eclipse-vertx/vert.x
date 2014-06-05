package org.vertx.java.core.http.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.impl.DefaultContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NewPool {

  private int maxSockets = 5;
  private boolean keepAlive = true;
  private boolean pipelining = false;

  private final Map<TargetAddress, HttpPool> pools = new ConcurrentHashMap<>();

  public void getConnection(String host, int port, Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler, DefaultContext context) {

  }

  private static class TargetAddress {
    final String host;
    final int port;

    private TargetAddress(String host, int port) {
      this.host = host;
      this.port = port;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TargetAddress that = (TargetAddress) o;
      if (port != that.port) return false;
      if (host != null ? !host.equals(that.host) : that.host != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = host != null ? host.hashCode() : 0;
      result = 31 * result + port;
      return result;
    }
  }


}
