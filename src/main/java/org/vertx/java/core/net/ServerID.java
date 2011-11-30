package org.vertx.java.core.net;

import java.io.Serializable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerID implements Serializable {
  public final int port;
  public final String host;

  public ServerID(int port, String host) {
    this.port = port;
    this.host = host;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ServerID serverID = (ServerID) o;

    if (port != serverID.port) return false;
    if (host != null ? !host.equals(serverID.host) : serverID.host != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = port;
    result = 31 * result + (host != null ? host.hashCode() : 0);
    return result;
  }
}
