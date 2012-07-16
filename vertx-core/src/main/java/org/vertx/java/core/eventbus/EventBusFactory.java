package org.vertx.java.core.eventbus;

import org.vertx.java.core.impl.VertxInternal;

public interface EventBusFactory {

	public EventBus createEventBus(VertxInternal v);

	public EventBus createEventBus(VertxInternal v, String hostname);

	public EventBus createEventBus(VertxInternal v, int port, String hostname);

}
