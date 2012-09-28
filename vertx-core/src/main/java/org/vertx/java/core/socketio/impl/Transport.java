package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.shareddata.Shareable;

import java.util.List;

/**
 * Root abstract class for other transports.
 *
 * @see <a href="https://github.com/LearnBoost/socket.io/blob/master/lib/transport.js">transport.js</a>
 * @author Keesun Baik
 */
public abstract class Transport implements Shareable {

	private static final Logger log = LoggerFactory.getLogger(Transport.class);

	protected boolean isDisconnected;
	protected boolean isOpen;
	protected boolean isDrained;
	protected Manager manager;
	protected String sessionId;
	protected ClientData clientData;
	protected HttpServerRequest request;
	protected VertxInternal vertx;
	protected Parser parser;

	protected long heartbeatInterval = -1l;
	protected long closeTimeout = -1l;
	protected long heartbeatTimeout = -1l;
	protected long pollTimeout = -1l;
	protected boolean isDiscarded;

	protected Transport(Manager manager, ClientData clientData) {
		this.manager = manager;
		this.clientData = clientData;
		this.request = this.clientData.getRequest();
		this.sessionId = this.clientData.getId();
		this.isDisconnected = false;
		this.isDrained = true;
		this.vertx = manager.getVertx();
		this.parser = new Parser();

		this.handleRequest();
	}

	/**
	 * Handles a request when it's set.
	 *
	 * @see "Transport.prototype.handleRequest"
	 */
	protected void handleRequest() {
		if(request != null) {
			if(log.isInfoEnabled()) log.info("setting request " + request.method + " " + request.uri);
		}

		if(clientData.getSocket() != null || request.method.toUpperCase().equals("GET")) {
//			this.socket = req.socket;
			this.isOpen = true;
			this.isDrained = true;
			this.setHeartbeatInterval();
			this.setHandlers();
			this.onSocketConnect();
		}
	}

	/**
	 * Called when a connection is first set.
	 *
	 * @see "Transport.prototype.onSocketConnect"
	 */
	protected void onSocketConnect() {}

	/**
	 * TODO Sets transport handlers.
	 *
	 * @see "Transport.prototype.setHandlers"
	 */
	protected void setHandlers() {
//		EventBus eventBus = manager.getVertx().eventBus();
//
//		eventBus.registerHandler("heartbeat-clear" + this.sessionId, new Handler<Message>() {
//			@Override
//			public void handle(Message event) {
//				onHeartbeatClear();
//			}
//		});
//
//		eventBus.registerHandler("disconnect-force:" + this.sessionId, new Handler<Message>() {
//			@Override
//			public void handle(Message event) {
//				onForcedDisconnect();
//			}
//		});
//
//		eventBus.registerHandler("dispatch:" + this.sessionId, new Handler<Message<JsonObject>>() {
//			@Override
//			public void handle(Message<JsonObject> event) {
//				JsonObject body = event.body;
//				onDispatch(body.getString("packet"), body.getBoolean("volatile"));
//			}
//		});


//		this.bound = {
//				end: this.onSocketEnd.bind(this)
//				, close: this.onSocketClose.bind(this)
//				, error: this.onSocketError.bind(this)
//				, drain: this.onSocketDrain.bind(this)
//		};
//
//		this.socket.on('end', this.bound.end);
//		this.socket.on('close', this.bound.close);
//		this.socket.on('error', this.bound.error);
//		this.socket.on('drain', this.bound.drain);
//
//		this.handlersSet = true;

	}

	/**
	 * Called upon a forced disconnection.
	 * @see "Transport.prototype.onForcedDisconnect"
	 */
	public void onForcedDisconnect() {
		if(!this.isDisconnected()) {
			if(log.isInfoEnabled()) log.info("transport end by forced client disconnection");
			if(this.isOpen()) {
				JsonObject packet = new JsonObject();
				packet.putString("type", "disconnect");
				this.packet(packet);
			}
			this.end("booted");
		}
	}

	/**
	 * Called upon receiving a heartbeat packet.
	 *
	 * @see "Transport.prototype.onHeartbeatClear"
	 */
	protected void onHeartbeatClear() {
		this.clearHeartbeatTimeout();
		this.setHeartbeatInterval();
	}

	/**
	 * Sets the heartbeat interval. To be called when a connection opens and when
	 * a heartbeat is received.
	 *
	 * @see "Transport.prototype.setHeartbeatInterval"
	 */
	protected void setHeartbeatInterval() {
		if(this.heartbeatInterval == -1l && manager.getSettings().isHeartbeats()) {
			this.heartbeatInterval = vertx.setTimer(manager.getSettings().getHeartbeatInterval() * 1000, new Handler<Long>() {
				public void handle(Long event) {
					heartbeat();
					heartbeatInterval = -1l;
				}
			});

//			if(log.isDebugEnabled()) log.debug("set heartbeat interval for client " + this.sessionId);
			if(log.isInfoEnabled()) log.info("set heartbeat interval for client " + this.sessionId);
		}
	}

	/**
	 * Sends a heartbeat
	 *
	 * @see "Transport.prototype.heartbeat"
	 */
	private void heartbeat() {
		if(this.isOpen()) {
//			if(log.isDebugEnabled()) log.debug("emitting heartbeat for client " + this.sessionId);
			if(log.isInfoEnabled()) log.info("emitting heartbeat for client " + this.sessionId);
			JsonObject heartbeat = new JsonObject();
			heartbeat.putString("type", "heartbeat");
			this.packet(heartbeat);
			this.setHeartbeatTimeout();
		}
	}

	/**
	 * Sets the heartbeat timeout
	 *
	 * @see "Transport.prototype.setHeartbeatTimeout"
	 */
	private void setHeartbeatTimeout() {
		if(this.heartbeatTimeout == -1l && manager.getSettings().isHeartbeats()) {
			this.heartbeatTimeout = vertx.setTimer(manager.getSettings().getHeartbeatTimeout() * 1000, new Handler<Long>() {
				public void handle(Long event) {
					if(log.isDebugEnabled()) log.debug("fired heartbeat timeout for client " + sessionId);
					heartbeatTimeout = -1l;
					end("heartbeat timeout");
				}
			});
			if(log.isDebugEnabled()) log.debug("set heartbeat timeout for client " + sessionId);
		}
	}


	/**
	 * Writes an error packet with the specified reason and advice.
	 *
	 * @see "Transport.prototype.error"
	 * @param reason
	 * @param advice
	 */
	public void error(String reason, String advice) {
		JsonObject packet = new JsonObject();
		packet.putString("type", "eeror");
		packet.putString("reason", reason);
		packet.putString("advice", advice);

		if(log.isDebugEnabled()) log.debug(reason + ((advice != null) ? ("client should " + advice) : ""));
		this.end("error");
	}

	/**
	 * Cleans up the connection, considers the client disconnected.
	 *
	 * @see "Transport.prototype.end"
	 * @param reason
	 */
	protected void end(String reason) {
		if(!this.isDisconnected()) {
	   	    if(log.isInfoEnabled()) log.info("transport end (" + reason + ")");
			Transport local = manager.getTranport(sessionId);
			this.close();
			this.clearTimeouts();
			this.isDisconnected = true;

			if(local != null) {
				manager.onClientDisconnect(sessionId, reason, true);
			} else {
//				this.store.publish('disconnect:' + this.id, reason);
			}
		}
	}

	/**
	 * Closes the connection
	 *
	 * @see "Transport.prototype.close"
	 */
	protected void close() {
		if (this.isOpen) {
			this.doClose();
			this.onClose();
		}
	}

	abstract protected void doClose();

	/**
	 * Signals that the transport should pause and buffer data.
	 *
	 * @see "Transport.prototype.discard"
	 * @return
	 */
	public Transport discard() {
		if(log.isInfoEnabled()) log.info("discarding transport");
		this.isDiscarded = true;
		this.clearTimeouts();
		this.clearHandlers();
		return this;
	}

	public abstract void payload(List<Buffer> buffers);

	/**
	 * Dispatches a packet.
	 *
	 * @see "Transport.prototype.onDispatch"
	 * @param encodedPacket
	 * @param isVolatile
	 */
	public void onDispatch(String encodedPacket, boolean isVolatile) {
		if(isVolatile) {
			writeVolatile(encodedPacket);
		} else {
			write(encodedPacket);
		}
	}

	public abstract void write(String encodedPacket);

	/**
	 * Writes a volatile message.
	 *
	 * @see "Transport.prototype.writeVolatile"
	 * @param encodedPacket
	 */
	private void writeVolatile(String encodedPacket) {
		if(isOpen()) {
			if(isDrained()) {
				write(encodedPacket);
			} else {
				if(log.isDebugEnabled()) log.debug("'ignoring volatile packet, buffer not drained");
			}
		} else {
			if(log.isDebugEnabled()) log.debug("''ignoring volatile packet, transport not open");
		}
	}

	/**
	 * Called upon a connection close.
	 *
	 * @see "Transport.prototype.onClose"
	 */
	public void onClose() {
		if(this.isOpen()) {
			this.setCloseTimeout();
			this.clearHandlers();
			this.isOpen = false;
			this.manager.onClose(this.sessionId);
//			this.store.publish('close', this.sessionId);
		}
	}

	/**
	 * Sets the close timeout.
	 *
	 * TODO Transport.prototype.clearHandlers
	 */
	private void clearHandlers() {
//		if (this.handlersSet) {
//			this.store.unsubscribe('disconnect-force:' + this.id);
//			this.store.unsubscribe('heartbeat-clear:' + this.id);
//			this.store.unsubscribe('dispatch:' + this.id);
//
//			this.socket.removeListener('end', this.bound.end);
//			this.socket.removeListener('close', this.bound.close);
//			this.socket.removeListener('error', this.bound.error);
//			this.socket.removeListener('drain', this.bound.drain);
//		}
	}

	/**
	 * Sets the close timeout.
	 *
	 * @see "Transport.prototype.setCloseTimeout"
	 */
	private void setCloseTimeout() {
		if(this.closeTimeout != -1l) {
			this.closeTimeout = vertx.setTimer(this.manager.getSettings().getCloseTimeout() * 1000, new Handler<Long>() {
				public void handle(Long event) {
					if(log.isDebugEnabled()) log.debug("fired close timeout for client " + sessionId);
					closeTimeout = -1l;
					end("close timeout");
				}
			});

			if(log.isInfoEnabled()) log.info("set close timeout for client " + sessionId);
		}
	}

	public void packet(JsonObject packet) {
		this.write(parser.encodePacket(packet));
	}


	/**
	 * Clears all timeouts.
	 *
	 * @see "Transport.prototype.clearTimeouts"
	 */
	protected void clearTimeouts() {
		this.clearCloseTimeout();
		this.clearHeartbeatTimeout();
		this.clearHeartbeatInterval();
	}

	/**
	 * Clears the heartbeat timeout
	 *
	 * @see "Transport.prototype.clearHeartbeatTimeout"
	 */
	protected void clearHeartbeatTimeout() {
		if (this.heartbeatTimeout != -1l && this.manager.getSettings().isHeartbeats()) {
			vertx.cancelTimer(heartbeatTimeout);
			this.heartbeatTimeout = -1l;
			if(log.isInfoEnabled()) log.info("cleared heartbeat timeout for client " + this.sessionId);
		}
	}

	/**
	 * Clears the heartbeat interval
	 *
	 * @see "Transport.prototype.clearHeartbeatInterval"
	 */
	protected void clearHeartbeatInterval() {
		if(this.heartbeatInterval != -1l && this.manager.getSettings().isHeartbeats()) {
			vertx.cancelTimer(heartbeatInterval);
			this.heartbeatInterval = -1l;
			if(log.isInfoEnabled()) log.info("cleared heartbeat interval for client " + this.sessionId);
		}
	}

	/**
	 * Clears the close timeout
	 *
	 * @see "Transport.prototype.clearCloseTimeout"
	 */
	protected void clearCloseTimeout() {
		if(this.closeTimeout != -1l) {
			vertx.cancelTimer(this.closeTimeout);
			this.closeTimeout = -1l;
			if(log.isInfoEnabled()) log.info("cleared close timeout for client " + this.sessionId);
		}
	}

	/**
	 * Handles a message.
	 *
	 * @see "Transport.prototype.onMessage"
	 * @param packet packet object
	 */
	protected void onMessage(JsonObject packet) {
		Transport current = manager.getTranport(this.sessionId);

		if(packet.getString("type").equals("heartbeat")) {
			if(log.isInfoEnabled()) log.info("got heartbeat packet");

			if(current != null && current.isOpen()) {
				current.onHeartbeatClear();
			} else {
//				this.store.publish('heartbeat-clear:' + this.id);
			}
		} else {
			if(packet.getString("type").equals("disconnect") && packet.getString("endpoint").equals("")) {
				if(log.isInfoEnabled()) log.info("got disconnection packet");
				if(current != null) {
					current.onForcedDisconnect();
				} else {
//					this.store.publish('disconnect-force:' + this.id);
				}
				return;
			}

			if(packet.getString("id") != null && !packet.getString("ack").equals("data")) {
				if(log.isDebugEnabled()) log.debug("acknowledging packet automatically");
				JsonObject ackPacket = new JsonObject();
				ackPacket.putString("type", "ack");
				ackPacket.putString("ackId", packet.getString("id"));
				ackPacket.putString("endpoint", packet.getString("endpoint", ""));

				String ack = parser.encodePacket(ackPacket);

				if(current != null && current.isOpen()) {
					current.onDispatch(ack, false);
				} else {
					manager.onClientDispatch(this.sessionId, ack);
//					this.store.publish('dispatch:' + this.id, ack);
				}
			}

			// handle packet locally or publish it
			if (current != null) {
				manager.onClientMessage(this.sessionId, packet);
			} else {
//					this.store.publish('message:' + this.id, packet);
			}
		}
	}

	protected abstract String getName();

	public boolean isDisconnected() {
		return isDisconnected;
	}

	public void setDisconnected(boolean disconnected) {
		isDisconnected = disconnected;
	}

	public boolean isOpen() {
		return isOpen;
	}

	public void setOpen(boolean open) {
		isOpen = open;
	}

	public boolean isDrained() {
		return isDrained;
	}

	public void setDrained(boolean drained) {
		isDrained = drained;
	}
}
