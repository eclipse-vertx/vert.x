package io.vertx.core.eventbus;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/18 by zmyer
public class BridgeInterceptor extends FilteringInterceptor {

    public BridgeInterceptor(String startsWith) {
        super(startsWith);
    }

    @Override
    protected void handleContext(SendContext sendContext) {

    }
}
