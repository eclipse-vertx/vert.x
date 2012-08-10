package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.socketio.Socket;
import org.vertx.java.core.socketio.SocketIOServer;

/**
 * @author Keesun Baik
 */
public class DefaultSocketIOServer implements SocketIOServer {

	private static final Logger log = LoggerFactory.getLogger(DefaultSocketIOServer.class);

	private final VertxInternal vertx;
	private RouteMatcher rm = new RouteMatcher();
	private Manager manager;
	private JsonObject config;

	public DefaultSocketIOServer(final VertxInternal vertx, final HttpServer httpServer) {
		this.vertx = vertx;
		this.config = new JsonObject();
		this.config.putString("namespace", "/socket.io");
		this.manager = new Manager(this.vertx);
		this.rm.noMatch(httpServer.requestHandler());
		httpServer.requestHandler(rm);
	}

	public SocketIOServer configure(String env, Configurer configurer) {
		if(env == null) {
			configurer.configure(this.config);
		} else if(env.equals(this.config.getString("env", "development"))) {
			configurer.configure(this.config);
		}
		return this;
	}

	public SocketIOServer configure(String env, JsonObject newConfig) {
		if(env == null) {
			this.config.mergeIn(newConfig);
		} else if(env.equals(this.config.getString("env", "development"))) {
			this.config.mergeIn(newConfig);
		}
		return this;
	}

	public SocketIOServer sockets() {
		this.manager.config(this.config);
		return this;
	}

	public SocketIOServer onConnect(Handler<Socket> handler) {
		this.manager.setSocketHandler(handler);

		Settings settings = manager.getSettings();
		final String namespace = settings.getNamespace();
		rm.allWithRegEx(namespace + ".*", manager.requestHandler());

		return this;
	}

	public SocketIOServer setAuthrizationCallback(AuthorizationHandler globalAuthHandler) {
		this.manager.setGlobalAuthorizationHandler(globalAuthHandler);
		return this;
	}

	public static void main(String[] args) throws InterruptedException {
		Vertx vertx = Vertx.newVertx();
		HttpServer httpServer = vertx.createHttpServer();
		installApplication((VertxInternal) vertx, httpServer);
		httpServer.listen(8081);
		System.out.println("Server is running on http://localshot:8081");
		Thread.sleep(Long.MAX_VALUE);
	}

	private static void installApplication(VertxInternal vertx, HttpServer httpServer) {
		/**
		 * ======================================================
		 * TODO http://socket.io/#how-to-use
		 * ======================================================
		 */
		final SocketIOServer io = new DefaultSocketIOServer(vertx, httpServer);

		io.configure("production", new Configurer() {
			public void configure(JsonObject config) {
				config.putBoolean("browser client etag", true);
				config.putNumber("log level", 1);
				config.putString("transports", "websocket,htmlfile,xhr-polling,jsonp-polling");
			}
		});

		io.configure("development", new Configurer() {
			public void configure(JsonObject config) {
				config.putString("transports", "jsonp-polling");
				config.putString("namespace", "/socket.io");
				config.putBoolean("authorization", true);
			}
		});

		AuthorizationHandler globalAuthorizationCallback = new AuthorizationHandler() {
			public void handle(HandshakeData handshakeData, AuthorizationCallback callback) {
				System.out.println("authorization callback!!!");
				callback.handle(null, true);
			}
		};

		io.setAuthrizationCallback(globalAuthorizationCallback);

		io.sockets().onConnect(new Handler<Socket>(){
			public void handle(final Socket socket) {
				System.out.println(socket.getId() + " Connected!");

				JsonObject message = new JsonObject();
				message.putString("hello", "world");
				socket.emit("news", message);

				socket.on("my other event", new Handler<JsonObject>(){
					public void handle(JsonObject data) {
						System.out.println("============ 왔어! =============");
						System.out.println(data);
					}
				});

				socket.on("timer", new Handler<JsonObject>(){
					public void handle(JsonObject data) {
						socket.emit("timer", data);
					}
				});
			}
		});
	}
}
