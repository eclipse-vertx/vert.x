package org.vertx.java.core.http;

import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SwitchingHttpResponseDecoder extends HttpResponseDecoder {

  private static final Logger log = Logger.getLogger(SwitchingHttpResponseDecoder.class);

  private Runnable runnable;

  public void setSwitch(Runnable runnable) {
    this.runnable = runnable;
  }

  @Override
  protected HttpMessage createMessage(String[] initialLine) {
    HttpMessage msg = super.createMessage(initialLine);
    if (runnable != null) {
      runnable.run();
      runnable = null;
    }
    return msg;
  }
}
