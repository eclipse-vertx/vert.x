package org.vertx.java.core.sockjs;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface TransportListener {

  void sendFrame(StringBuffer payload);
}
