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

	/**
	 * @return sessionId
	 */
	String getId();

	/**
	 * Emit override for custom events.
	 *
	 * @see "Socket.prototype.emit"
	 * @param event
	 * @param message
	 * @return
	 */
	void emit(String event, JsonObject message);

	/**
	 * emit disconnection
	 * @param reason
	 */
	void emitDisconnect(String reason);

	/**
	 * register handler to an event.
	 *
	 * @param event
	 * @param handler
	 */
	void on(String event, Handler<JsonObject> handler);

	/**
	 * Transmits a packet.
	 *
	 * @see "Socket.prototype.packet"
	 * @param packet
	 */
	void packet(JsonObject packet);

	/**
	 * Triggered on disconnect
	 *
	 * @see "Socket.prototype.onDisconnect"
	 * @param reason
	 */
	void onDisconnect(String reason);

	// $emit
	void emit(JsonObject packet);

	// get Acks
	Map<String,Handler<JsonArray>> getAcks();

	// start socket handling
	void onConnection();

	boolean isReadable();

}
