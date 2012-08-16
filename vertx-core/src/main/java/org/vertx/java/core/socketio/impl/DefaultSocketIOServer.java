package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.socketio.SocketIOServer;

/**
 * @author Keesun Baik
 */
public class DefaultSocketIOServer implements SocketIOServer {

	private static final Logger log = LoggerFactory.getLogger(DefaultSocketIOServer.class);

	private final VertxInternal vertx;
	private Manager manager;
	private JsonObject config;
	private HttpServer httpServer;

	private RouteMatcher rm;

	public DefaultSocketIOServer(final VertxInternal vertx, final HttpServer httpServer) {
		this.vertx = vertx;
		this.config = new JsonObject();
		this.config.putString("namespace", "/socket.io");
		this.manager = new Manager(this.vertx, httpServer);
		this.httpServer = httpServer;

		this.rm = new RouteMatcher();
		rm.noMatch(this.httpServer.requestHandler());
		httpServer.requestHandler(rm);

		setupRequestMatcher();
	}

	private void setupRequestMatcher() {
		Settings settings = this.manager.buildSettings(this.config);
		String namespace = settings.getNamespace();

		this.rm.allWithRegEx(namespace + ".*", manager.requestHandler());
		this.httpServer.websocketHandler(manager.webSocketHandler());
	}

	public SocketIOServer configure(String env, Configurer configurer) {
		if (env == null || env.equals(this.config.getString("env", "development"))) {
			configurer.configure(this.config);
		}
		setupRequestMatcher();
		return this;
	}

	public SocketIOServer configure(Configurer configurer) {
		return configure(null, configurer);
	}

	public SocketIOServer configure(String env, JsonObject newConfig) {
		if (env == null || env.equals(this.config.getString("env", "development"))) {
			this.config.mergeIn(newConfig);
		}
		setupRequestMatcher();
		return this;
	}

	public SocketIOServer configure(JsonObject newConfig) {
		return configure(null, newConfig);
	}

	public Namespace sockets() {
		return this.manager.sockets();
	}

	public Namespace of(final String name) {
		return this.manager.of(name);
	}

	public SocketIOServer setAuthrizationCallback(AuthorizationHandler globalAuthHandler) {
		this.manager.setGlobalAuthorizationHandler(globalAuthHandler);
		return this;
	}

}