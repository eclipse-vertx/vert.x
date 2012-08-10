package org.vertx.java.core.socketio.impl.transports;

import org.vertx.java.core.socketio.impl.ClientData;
import org.vertx.java.core.socketio.impl.Manager;
import org.vertx.java.core.socketio.impl.Transport;

/**
 * @author Keesun Baik
 */
public abstract class WebSocketTransport extends Transport {

	protected WebSocketTransport(Manager manager, ClientData clientData) {
		super(manager, clientData);
	}
}
