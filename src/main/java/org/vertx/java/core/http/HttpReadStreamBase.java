package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.ReadStream;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class HttpReadStreamBase implements ReadStream {

  /**
   * Convenience method for receiving the entire request body in one piece. This saves the user having to manually
   * set a data and end handler and append the chunks of the body until the whole body received.
   * Don't use this if your request body is large - you could potentially run out of RAM.
   *
   * @param bodyHandler This handler will be called after all the body has been received
   */
  public void bodyHandler(final Handler<Buffer> bodyHandler) {
    final Buffer body = Buffer.create(0);
    dataHandler(new Handler<Buffer>() {
      public void handle(Buffer buff) {
        body.appendBuffer(buff);
      }
    });
    endHandler(new SimpleHandler() {
      public void handle() {
        bodyHandler.handle(body);
      }
    });
  }
}
