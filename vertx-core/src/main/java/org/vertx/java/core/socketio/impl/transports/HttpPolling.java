package org.vertx.java.core.socketio.impl.transports;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.socketio.impl.ClientData;
import org.vertx.java.core.socketio.impl.Manager;

/**
 * @see <a href="https://github.com/LearnBoost/socket.io/blob/master/lib/transports/http-polling.js">http-polling.js</a>
 * @author Keesun Baik
 */
public abstract class HttpPolling extends Http {

	private static final Logger log = LoggerFactory.getLogger(HttpPolling.class);
	private String name = "httppolling";

	protected HttpPolling(Manager manager, ClientData clientData) {
		super(manager, clientData);
	}

	/**
	 * Performs a write.
	 *
	 * @see "HTTPPolling.prototype.write"
	 * @param encodedPacket
	 */
	@Override
	public void write(String encodedPacket) {
		this.doWrite(encodedPacket);
		this.response.end();
		this.onClose();
	}

	/**
	 * doWrite to clear poll timeout
	 *
	 * @see "HTTPPolling.prototype.doWrite"
	 * @param encodedPacket
	 */
	protected void doWrite(String encodedPacket) {
		this.clearPollTimeout();
	}

	/**
	 * Clears polling timeout
	 *
	 * @see "HTTPPolling.prototype.clearPollTimeout"
	 */
	protected void clearPollTimeout() {
		if(pollTimeout != -1l) {
			vertx.cancelTimer(pollTimeout);
			this.pollTimeout = -1l;
			if(log.isInfoEnabled()) log.info("clearing poll timeout");
		}
	}

	/**
	 * Override clear timeouts to clear the poll timeout
	 *
	 * @see "HTTPPolling.prototype.clearTimeouts"
	 */
	@Override
	protected void clearTimeouts() {
		super.clearTimeouts();
		this.clearPollTimeout();
	}

	/**
	 * Handles a request
	 *
	 * @see "HTTPPolling.prototype.handleRequest"
	 * @param req
	 */
	@Override
	protected void handleRequest(HttpServerRequest req) {
		super.handleRequest(req);

		if(req.method.equals("GET")) {
			this.pollTimeout = vertx.setTimer(manager.getSettings().getPollingDuration() * 1000, new Handler<Long>() {
				public void handle(Long event) {
					JsonObject packet = new JsonObject();
					packet.putString("type", "noop");
					packet(packet);
					if(log.isDebugEnabled()) log.debug(name + " closed due to exceeded duration");
				}
			});

			if(log.isInfoEnabled()) log.info("setting poll timeout");
		}
	}

	@Override
	protected String getName() {
		return "httppolling";
	}
}
