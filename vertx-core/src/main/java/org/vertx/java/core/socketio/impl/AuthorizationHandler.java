package org.vertx.java.core.socketio.impl;

/**
 * @author Keesun Baik
 */
public interface AuthorizationHandler {

	public void handle(HandshakeData handshakeData, AuthorizationCallback callback);
}
