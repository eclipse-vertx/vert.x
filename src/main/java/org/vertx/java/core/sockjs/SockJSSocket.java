package org.vertx.java.core.sockjs;

import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

/**
 *
 * <p>You interact with SockJS clients through instances of SockJS socket.</p>
 *
 * <p>The API is very similar to {@link org.vertx.java.core.http.WebSocket}. It implements both
 * {@link ReadStream} and {@link WriteStream} so it can be used with {@link org.vertx.java.core.streams.Pump} to enable
 * flow control.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface SockJSSocket extends ReadStream, WriteStream {

  void close();
}
