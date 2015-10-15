package io.vertx.core.spi;

import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.HAManager;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface EventBusFactory {

  EventBus createEventBus(VertxInternal vertx, VertxOptions options, ClusterManager clusterManager, HAManager haManager);

}
