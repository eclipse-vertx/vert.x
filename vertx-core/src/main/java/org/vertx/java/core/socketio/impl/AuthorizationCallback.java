package org.vertx.java.core.socketio.impl;

/**
 * @author Keesun Baik
 */
public interface AuthorizationCallback {

	public void handle(Exception e, boolean isAuthorized);
}
