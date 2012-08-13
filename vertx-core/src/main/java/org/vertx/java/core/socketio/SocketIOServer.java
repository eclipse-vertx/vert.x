package org.vertx.java.core.socketio;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.socketio.impl.AuthorizationHandler;
import org.vertx.java.core.socketio.impl.Configurer;

/**
 * @author Keesun Baik
 */
public interface SocketIOServer {

	SocketIOServer configure(String env, Configurer configurer);

	SocketIOServer configure(Configurer configurer);

	SocketIOServer configure(String env, JsonObject newConfig);

	SocketIOServer configure(JsonObject newConfig);

	SocketIOServer setAuthrizationCallback(AuthorizationHandler globalAuthorizationCallback);

	SocketIOServer sockets();

	SocketIOServer onConnection(Handler<SocketIOSocket> handler);
}
