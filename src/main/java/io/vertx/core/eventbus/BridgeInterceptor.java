package io.vertx.core.eventbus;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BridgeInterceptor extends FilteringInterceptor {

  public BridgeInterceptor(String startsWith) {
    super(startsWith);
  }

  @Override
  protected void handleContext(SendContext sendContext) {

  }
}
