package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface SockJSSocket {

  void write(Buffer buffer);

  void dataHandler(Handler<Buffer> handler);

  void close();
}
