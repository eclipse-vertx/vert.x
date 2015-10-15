package io.vertx.core.eventbus.impl;

import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.spi.EventBusFactory;
import io.vertx.core.eventbus.impl.clustered.ClusteredEventBus;
import io.vertx.core.impl.HAManager;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusFactoryImpl implements EventBusFactory {

  @Override
  public EventBus createEventBus(VertxInternal vertx, VertxOptions options, ClusterManager clusterManager, HAManager haManager) {
    EventBus eb;
    if (options.isClustered()) {
      eb = new ClusteredEventBus(vertx, options, clusterManager, haManager);
    } else {
      eb = new EventBusImpl(vertx);
    }
    return eb;
  }
}
