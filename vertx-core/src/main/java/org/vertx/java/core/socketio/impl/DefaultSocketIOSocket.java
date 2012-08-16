package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.socketio.SocketIOSocket;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @see <a href="https://github.com/LearnBoost/socket.io/blob/master/lib/socket.js">socket.js</a>
 * @author Keesun Baik
 */
public class DefaultSocketIOSocket implements SocketIOSocket {

	private static final Logger log = LoggerFactory.getLogger(DefaultSocketIOServer.class);

	private Manager manager;
	private String id;
	private Namespace namespace;
	private boolean readable;
	private JsonObject flags;
	private Parser parser;
	private VertxInternal vertx;
	private Handler<SocketIOSocket> socketHandler;
	private Map<String, Handler<JsonArray>> acks;
	private Map<String, Handler<JsonObject>> handlers;
	private boolean disconnected;

	public DefaultSocketIOSocket(Manager manager, String id, Namespace namespace, boolean readable, Handler<SocketIOSocket> socketHandler) {
		this.manager = manager;
		this.vertx = manager.getVertx();
		this.id = id;
		this.namespace = namespace;
		this.readable = readable;
		this.parser = new Parser();
		this.socketHandler = socketHandler;
		this.acks = new ConcurrentHashMap<>();
		this.handlers = new ConcurrentHashMap<>();
		setupFlags();

		// 기본 에러 핸들러 처리 필요함

		// socket에 저장되어도 되는 데이터: handshake data, transport, log ?
		// 추가가 필요한 기능, set, get, join, leave
	}

	/**
	 * Resets flags
	 *
	 * @see "Socket.prototype.setFlags"
	 */
	private synchronized void setupFlags() {
		this.flags = new JsonObject();
		flags.putString("endpoint", this.namespace.getName());
		flags.putString("room", Manager.DEFAULT_NSP);
	}

	/**
	 * JSON message flag.
	 */
	public SocketIOSocket json() {
		this.flags.putBoolean("json", true);
		return this;
	}

	public SocketIOSocket volatilize() {
		this.flags.putBoolean("volatile", true);
		return this;
	}

	public SocketIOSocket broadcast() {
		this.flags.putBoolean("broadcast", true);
		return this;
	}

	public SocketIOSocket to(final String room) {
		this.flags.putString("room", room);
		return this;
	}

	public SocketIOSocket in(final String room) {
		this.flags.putString("room", room);
		return this;
	}

	/**
	 * Transmits a packet.
	 *
	 * @see "Socktet.prototype.packet"
	 * @param packet
	 * @return
	 */
	public synchronized void packet(JsonObject packet) {
		if(this.flags.getBoolean("broadcast", false)) {
			if(log.isDebugEnabled())  log.debug("broadcasting packet");
			this.namespace.in(this.flags.getString("room")).except(this.getId()).packet(packet);
		} else {
			packet.putString("endpoint", this.flags.getString("endpoint"));
			String encodedPacket = parser.encodePacket(packet);
			this.dispatch(encodedPacket, this.flags.getBoolean("volatile", false));
		}

		this.setupFlags();
	}

	/**
	 * Dispatches a packet
	 *
	 * @see "Socket.prototype.dispatch"
	 * @param encodedPacket
	 * @param isVolatile
	 */
	private synchronized void dispatch(String encodedPacket, boolean isVolatile) {
		Transport transport = this.manager.getTranport(this.id);
		if( transport != null && transport.isOpen() ) {
			transport.onDispatch(encodedPacket, isVolatile);
		} else {
			if (!isVolatile) {
				this.manager.onClientDispatch(this.id, encodedPacket);
			}
//			this.manager.getStore().publich("dispatch:" + this.id, packet, isVolatile);
		}
	}

	/**
	 * Emit override for custom events.
	 *
	 * @see "Socket.prototype.emit"
	 * @param event
	 * @param jsonObject
	 * @return
	 */
	public synchronized void emit(String event, JsonObject jsonObject) {
//		if (ev == 'newListener') {
//			return this.$emit.apply(this, arguments);
//		}

		JsonObject packet = new JsonObject();
		packet.putString("type", "event");
		packet.putString("name", event);

//		if ('function' == typeof lastArg) {
//			packet.id = ++this.ackPackets;
//			packet.ack = lastArg.length ? 'data' : true;
//			this.acks[packet.id] = lastArg;
//			args = args.slice(0, args.length - 1);
//		}

		JsonArray args = new JsonArray();
		args.addObject(jsonObject);
		packet.putArray("args", args);
		this.packet(packet);
	}

	/**
	 * emit disconnection
	 *
	 * @param reason
	 */
	public synchronized void emitDisconnect(String reason) {
		JsonObject packet = new JsonObject();
		packet.putString("reason", reason);
//		emit("disconnect", packet);
		packet.putString("name", "disconnect");
		emit(packet);
	}

	/**
	 * Joins a user to a room.
	 *
	 * @see "Socket.prototype.join"
	 * @param room
	 */
	@Override
	public SocketIOSocket join(String room) {
		String roomName = namespace.getName() + "/" + room;
		this.manager.onJoin(this.id, roomName);
//		this.manager.store.publish('join', this.id, name);
		return this;
	}

	@Override
	public SocketIOSocket join(String room, Handler<Void> handler) {
		join(room);
		handler.handle(null);
		return this;
	}

	/**
	 * Un-joins a user from a room.
	 *
	 * @see "Socket.prototype.leave"
	 * @param room
	 */
	@Override
	public SocketIOSocket leave(String room) {
		String roomName = namespace.getName() + "/" + room;
		this.manager.onLeave(this.id, roomName);
//		this.manager.store.publish('leave', this.id, name);
		return this;
	}

	@Override
	public SocketIOSocket leave(String room, Handler<Void> handler) {
		leave(room);
		handler.handle(null);
		return this;
	}

	/**
	 * register handler to an event.
	 *
	 * @param event
	 * @param handler
	 */
	public synchronized void on(String event, Handler<JsonObject> handler) {
		handlers.put(event, handler);
	}

	/**
	 * execute socket handler.
	 */
	public synchronized void onConnection() {
		if(this.socketHandler != null) {
			socketHandler.handle(this);
		}
	}

	// $emit
	public synchronized void emit(JsonObject params) {
		String name = params.getString("name");
		Handler<JsonObject> handler = handlers.get(name);
		if(handler != null) {
			JsonObject packet = flatten(params.getArray("args"));
			if(name.equals("disconnect")) {
				packet.putString("reason", params.getString("reason"));
			}
			handler.handle(packet);
		} else {
			log.info("handler not found \'" + name + "\'");
		}
	}

	private JsonObject flatten(JsonArray jsonArray) {
		if(jsonArray == null) {
			return new JsonObject();
		}

		JsonObject result = new JsonObject();

		Iterator<Object> iterator = jsonArray.iterator();
		while (iterator.hasNext()) {
			Object o = iterator.next();
			if(o instanceof JsonObject) {
				result.mergeIn((JsonObject)o);
			}
		}

		return result;
	}

	/**
	 * Triggered on disconnect
	 *
	 * @see "Socket.prototype.onDisconnect"
	 * @param reason
	 */
	public synchronized void onDisconnect(String reason) {
		if(!this.disconnected) {
			emitDisconnect(reason);
			this.disconnected = true;
		}
	}

	public Map<String, Handler<JsonArray>> getAcks() {
		return acks;
	}

	public Manager getManager() {
		return manager;
	}

	public String getId() {
		return id;
	}

	public Namespace getNamespace() {
		return namespace;
	}

	public boolean isReadable() {
		return readable;
	}
}
