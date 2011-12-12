package org.vertx.java.core.sockjs;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
interface TransportListener {

  void sendFrame(String payload);
}
