package io.vertx.core.eventbus;

/**
 *
 */
public class BridgeInterceptor extends FilteringInterceptor {

  public BridgeInterceptor(String startsWith) {
    super(startsWith);
  }

  @Override
  protected void handleContext(SendContext sendContext) {

  }
}
