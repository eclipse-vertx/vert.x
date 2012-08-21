package org.vertx.java.core.socketio.impl.handlers;

import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.socketio.impl.ClientData;
import org.vertx.java.core.socketio.impl.Manager;

import java.util.Arrays;
import java.util.List;

/**
 * @author Keesun Baik
 */
public class StaticHandler {

	private static final List<String> STAIC_FLIE_NAMES = Arrays.asList(new String[]{
			"/static/flashsocket/WebSocketMainInsecure.swf",
			"/static/flashsocket/WebSocketMain.swf",
			"/socket.io.js",
			"/socket.io.min.js"
	});

	private Manager manager;

	public StaticHandler(Manager manager) {
		this.manager = manager;
	}

	public void handle(ClientData clientData) {
		HttpServerRequest req = clientData.getRequest();
		HttpServerResponse res = req.response;
		String fileName = clientData.getPath();

		switch (fileName) {
			case "/static/flashsocket/WebSocketMainInsecure.swf":
				res.sendFile("./src/dist/client/socket.io/WebSocketMainInsecure.swf");
				break;
			case "/static/flashsocket/WebSocketMain.swf":
				res.sendFile("./src/dist/client/socket.io/WebSocketMain.swf");
				break;
			case "/socket.io.js":
				res.sendFile("./src/dist/client/socket.io/socket.io.js");
				break;
			case "/socket.io.min.js":
				res.sendFile("./src/dist/client/socket.io/socket.io.min.js");
				break;
			default:
				throw new IllegalArgumentException(fileName);
		}
	}

	public static boolean has(String path) {
		return STAIC_FLIE_NAMES.contains(path);
	}

}
