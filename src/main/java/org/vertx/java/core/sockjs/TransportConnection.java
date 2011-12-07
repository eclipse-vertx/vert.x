package org.vertx.java.core.sockjs;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface TransportConnection {
  void write(Session session);
}
