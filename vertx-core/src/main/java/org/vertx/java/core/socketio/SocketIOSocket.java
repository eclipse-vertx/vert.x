package org.vertx.java.core.socketio;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.shareddata.Shareable;

import java.util.Map;

/**
 * @author Keesun Baik
 */
public interface SocketIOSocket extends Shareable {
	String getId();

	void emit(String event, JsonObject message);

	void emit(String event, String reason);

	void on(String event, Handler<JsonObject> handler);

	Map<String,Handler<JsonArray>> getAcks();

	void packet(JsonObject packet);

	void onConnection();

	boolean isReadable();

	void onDisconnect(String reason);

	void emit(JsonObject packet);
}
