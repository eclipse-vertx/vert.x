package io.vertx.core.eventbus;

import io.vertx.core.Handler;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class FilteringInterceptor implements Handler<SendContext> {

  private final String startsWith;

  public FilteringInterceptor(String startsWith) {
    this.startsWith = startsWith;
  }

  // TODO regex

  @Override
  public void handle(SendContext sendContext) {
    if (sendContext.message().address().startsWith(startsWith)) {
      handleContext(sendContext);
    } else {
      sendContext.next();
    }
  }

  protected abstract void handleContext(SendContext sendContext);

}
