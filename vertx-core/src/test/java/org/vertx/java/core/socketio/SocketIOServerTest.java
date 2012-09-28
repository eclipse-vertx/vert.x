package org.vertx.java.core.socketio;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.socketio.impl.Configurer;
import org.vertx.java.core.socketio.impl.DefaultSocketIOServer;

/**
 * @author Keesun Baik
 */
public class SocketIOServerTest {

	public static void main(String[] args) throws InterruptedException {
		int port = 9090;
		Vertx vertx = Vertx.newVertx();
		HttpServer httpServer = vertx.createHttpServer();

		final SocketIOServer io = new DefaultSocketIOServer((VertxInternal) vertx, httpServer);

		io.configure(new Configurer() {
			public void configure(JsonObject config) {
				config.putString("transports", "flashsocket");
			}
		});

		io.sockets().onConnection(new Handler<SocketIOSocket>() {
			public void handle(final SocketIOSocket socket) {
				socket.on("timer", new Handler<JsonObject>() {
					public void handle(JsonObject data) {
						socket.emit("timer", data);
					}
				});
			}
		});

		httpServer.listen(port);
		System.out.println("Server is running on http://localshot:" + port);
		Thread.sleep(Long.MAX_VALUE);
	}
}
