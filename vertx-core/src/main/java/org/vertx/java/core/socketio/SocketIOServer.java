package org.vertx.java.core.socketio;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.socketio.impl.AuthorizationHandler;
import org.vertx.java.core.socketio.impl.Configurer;

/**
 * @author Keesun Baik
 */
public interface SocketIOServer {

	SocketIOServer configure(String production, Configurer configurer);

	SocketIOServer configure(String env, JsonObject newConfig);

	SocketIOServer setAuthrizationCallback(AuthorizationHandler globalAuthorizationCallback);

	SocketIOServer sockets();

	SocketIOServer onConnect(Handler<SocketIOSocket> handler);
}
