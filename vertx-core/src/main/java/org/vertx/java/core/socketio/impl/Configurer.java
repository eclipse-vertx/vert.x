package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.json.JsonObject;

/**
 * @author Keesun Baik
 */
public interface Configurer {

	public void configure(JsonObject config);
}
