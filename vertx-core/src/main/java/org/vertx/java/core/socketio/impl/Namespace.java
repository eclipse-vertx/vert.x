package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.shareddata.Shareable;
import org.vertx.java.core.socketio.SocketIOSocket;

import java.util.Map;

/**
 * Namespace
 *
 * @see <a href="https://github.com/LearnBoost/socket.io/blob/master/lib/namespace.js">namespace.js</a>
 * @author Keesun Baik
 */
public class Namespace implements Shareable {

	private static final Logger log = LoggerFactory.getLogger(Namespace.class);

	private String name;
	private Manager manager;
	private Map<String, SocketIOSocket> sockets;
	private EventBus eventBus;
	private JsonObject flags;
	private Parser parser;
	private AuthorizationHandler authHandler;

	public Namespace(Manager manager, String name) {
		this.manager = manager;
		this.name = name;
		this.sockets = manager.getMap("nsp");
		this.eventBus = manager.getVertx().eventBus();
		this.parser = new Parser();
		setFlags();
	}

	/**
	 * Sets the default flags.
	 *
	 * @see "SocketNamespace.prototype.setFlags"
	 */
	private void setFlags() {
		this.flags = new JsonObject();
		flags.putString("endpoint", this.name);
		flags.putArray("exceptions", new JsonArray());
	}

	/**
	 * Retrieves or creates a write-only socket for a client, unless specified.
	 *
	 * @see "SocketNamespace.prototype.socket"
	 * @param sid
	 * @param readable whether the socket will be readable when initialized
	 * @param socketHandler
	 * @return
	 */
	public SocketIOSocket socket(String sid, boolean readable, Handler<SocketIOSocket> socketHandler) {
		if (!this.sockets.containsKey(sid)) {
			this.sockets.put(sid, new DefaultSocketIOSocket(this.manager, sid, this, readable, socketHandler));
		}
		return this.sockets.get(sid);
	}

	/**
	 * Handles a packet.
	 *
	 * @see "SocketNamespace.prototype.handlePacket"
	 * @param sessionId
	 * @param packet
	 * @param socketHandler
	 */
	public void handlePacket(final String sessionId, JsonObject packet, Handler<SocketIOSocket> socketHandler) {
		final SocketIOSocket socket = socket(sessionId, true, socketHandler);
		boolean isDataAck = false;
		String ack = packet.getString("ack");
		if(ack != null && ack.equals("data")) {
			isDataAck = true;
		}

		String type = packet.getString("type");
		if(type.equals("connect"));

		switch (type) {
			case "connect":
				String endpoint = packet.getString("endpoint", "");
				if(endpoint.equals("")) {
					connect(socket);
				} else {
					final HandshakeData handshakeData  = manager.getHandshaken().get(sessionId);

					this.authorize(handshakeData, new AuthorizationCallback(){
						@Override
						public void handle(Exception e, boolean isAuthorized) {
							if(e != null) {
								error(socket, e);
								return;
							}

							if(isAuthorized) {
								manager.onHandshake(sessionId, handshakeData);
//								self.store.publish('handshake', sessid, newData || handshakeData);
								connect(socket);
							} else {
								error(socket, e);
							}
						}
					});
				}
				break;

			case "ack":
				Map<String, Handler<JsonArray>> acks = socket.getAcks();
				if(acks.size() > 0) {
					Handler ackHandler = acks.get(packet.getString("ackId"));
					if(ackHandler != null) {
						ackHandler.handle(packet.getArray("args"));
					} else {
						if(log.isInfoEnabled()) log.info("unknown ack packet");
					}
				}
				break;

			case "event":
				// check if the emitted event is not blacklisted
				if(manager.getSettings().getBlacklist().indexOf(packet.getString("name")) != -1) {
					if(log.isInfoEnabled()) log.info("ignoring blacklisted event \'" + packet.getString("name") + "\'");
				} else {
					JsonObject params = new JsonObject();
					params.putArray("args", packet.getArray("args"));
					params.putString("name", packet.getString("name"));
					if(isDataAck) {
						params.putString("ack", packet.getString("ack"));
					}
					socket.emit(params);
				}
				break;
			case "disconnect":
				this.manager.onLeave(sessionId, this.name);
//				this.store.publish('leave', sessid, this.name);
				socket.emitDisconnect(packet.getString("reason", "packet"));
				break;
			case "json":
			case "message":
				JsonObject params = new JsonObject();
				params.putString("message", packet.getString("data"));
				if (isDataAck) {
					params.putString("ack", ack);
				}
				socket.emit(params);
		}
	}

	/**
	 * @see "SocketNamespace.prototype.handlePacket ack"
	 * @param socket
	 * @param jsonObject
	 */
	private void ack(SocketIOSocket socket, JsonObject jsonObject) {
		if(log.isDebugEnabled()) log.debug("sending data ack packet");
		JsonObject packet = new JsonObject();
		packet.putString("type", "ack");
		packet.putArray("args", jsonObject.getArray("args"));
		packet.putString("ackId", jsonObject.getString("ackId"));
		socket.packet(packet);
	}

	/**
	 * @see "SocketNamespace.prototype.handlePacket error"
	 * @param socket
	 * @param e
	 */
	private void error(SocketIOSocket socket, Exception e) {
		if(log.isDebugEnabled()) log.debug("hnadshake error " + e.getMessage() + " for " + this.name);
		JsonObject packet = new JsonObject();
		packet.putString("type", "error");
		packet.putString("reason", e.getMessage());
		socket.packet(packet);
	}

	/**
	 * @see "SocketNamespace.prototype.handlePacket connect"
	 * @param socket
	 */
	private void connect(SocketIOSocket socket) {
		this.manager.onJoin(socket.getId(), this.name);
//		self.store.publish('join', sessid, self.name);

		// packet echo
		JsonObject packet = new JsonObject();
		packet.putString("type", "connect");
		socket.packet(packet);

		// emit connection event
		socket.onConnection();
	}

	/**
	 * Performs authentication.
	 *
	 * @see "SocketNamespace.prototype.authorize"
	 * @param handshakeData
	 * @param authCallback
	 */
	private void authorize(HandshakeData handshakeData, final AuthorizationCallback authCallback) {
		if(this.authHandler != null) {
			authHandler.handle(handshakeData, new AuthorizationCallback() {
				public void handle(Exception e, boolean isAuthorized) {
					if(log.isInfoEnabled()) log.info("client " + (isAuthorized ? "" : "un") + "authorized for " + name);
					authCallback.handle(e, isAuthorized);
				}
			});
		} else {
			if(log.isInfoEnabled()) log.info("client authorized for " + this.getName());
			authCallback.handle(null, true);
		}
	}

	/**
	 * Overrides the room to relay messages to (flag).
	 *
	 * @see "SocketNamespace.prototype.in"
	 * @param room
	 * @return
	 */
	public Namespace in(String room) {
		this.flags.putString("endpoint", this.getName() + (room != null ? "/" + room : ""));
		return this;
	}

	/**
	 * Adds a session id we should prevent relaying messages to (flag).
	 *
	 * @see "SocketNamespace.prototype.except"
	 * @param id
	 * @return
	 */
	public Namespace except(String id) {
		this.flags.getArray("exceptions").add(id);
		return this;
	}

	/**
	 * Sends out a packet.
	 *
	 * @see "SocketNamespace.prototype.packet"
	 * @param packet
	 * @return
	 */
	public Namespace packet(JsonObject packet) {
		packet.putString("endpoint", this.getName());
		Store store = this.manager.getStore();
		boolean isVolatile = this.flags.getBoolean("volatile", false);
		JsonArray exceptions = this.flags.getArray("exceptions");
		String encodedPacket = parser.encodePacket(packet);

		this.manager.onDispatch(this.flags.getString("endpoint"), encodedPacket, isVolatile, exceptions);
//		this.store.publish('dispatch', this.flags.endpoint, packet, volatile, exceptions);
		this.setFlags();

		return this;
	}

	/**
	 * Called when a socket disconnects entirely.
	 *
	 * @see "SocketNamespace.prototype.handleDisconnect"
	 * @param sessionId
	 * @param reason
	 * @param raiseOnDisconnect
	 */
	public void handleDisconnect(String sessionId, String reason, boolean raiseOnDisconnect) {
		SocketIOSocket socket = sockets.get(sessionId);
		if(socket != null && socket.isReadable()) {
			if(raiseOnDisconnect) socket.onDisconnect(reason);
			sockets.remove(sessionId);
		}
	}

	public String getName() {
		return name;
	}

}
