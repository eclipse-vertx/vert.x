package org.vertx.java.core.eventbus.impl;

import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.EventBusFactory;
import org.vertx.java.core.impl.VertxInternal;

public class DefaultEventBusFactory implements EventBusFactory {

	@Override
	public EventBus createEventBus(VertxInternal v) {
		return new DefaultEventBus(v);
	}

	@Override
	public EventBus createEventBus(VertxInternal v, String hostname) {
		return new DefaultEventBus(v, hostname);
	}

	@Override
	public EventBus createEventBus(VertxInternal v, int port, String hostname) {
		return new DefaultEventBus(v, port, hostname);
	}

}