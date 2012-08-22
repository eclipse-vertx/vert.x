package org.vertx.java.core.socketio;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.socketio.impl.AuthorizationHandler;
import org.vertx.java.core.socketio.impl.Configurer;
import org.vertx.java.core.socketio.impl.Namespace;

/**
 * @author Keesun Baik
 */
public interface SocketIOServer {

	SocketIOServer configure(String env, Configurer configurer);

	SocketIOServer configure(String env, JsonObject newConfig);

	SocketIOServer configure(Configurer configurer);

	SocketIOServer configure(JsonObject newConfig);

	SocketIOServer setAuthHandler(AuthorizationHandler globalAuthorizationCallback);

	Namespace sockets();
	
	Namespace of(String name);

}
