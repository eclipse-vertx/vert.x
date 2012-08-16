package org.vertx.java.core.socketio.impl.transports;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.socketio.impl.ClientData;
import org.vertx.java.core.socketio.impl.Manager;

import java.util.Map;

/**
 * @see <a href="https://github.com/LearnBoost/socket.io/blob/master/lib/transports/xhr-polling.js">xhr-polling.js</a>
 * @author Keesun Baik
 */
public class XhrPolling extends HttpPolling {

	private static final Logger log = LoggerFactory.getLogger(XhrPolling.class);

	public XhrPolling(Manager manager, ClientData clientData) {
		super(manager, clientData);
	}

	@Override
	protected boolean isPostEncoded() {
		return false;
	}

	@Override
	protected String getName() {
		return "xhr-polling";
	}

	/**
	 * Frames data prior to write.
	 *
	 * @see "XHRPolling.prototype.doWrite"
	 * @param encodedPacket
	 */
	@Override
	protected void doWrite(String encodedPacket) {
		super.doWrite(encodedPacket);

		String origin = request.headers().get("Origin");
		Map<String, Object> resHeaders = response.headers();
		resHeaders.put("Content-Type", "text/plain; charset=UTF-8");
		resHeaders.put("Content-Length", encodedPacket == null ? 0 : encodedPacket.length());
		resHeaders.put("Connection", "Keep-Aplive");

		if(origin != null) {
			// https://developer.mozilla.org/En/HTTP_Access_Control
			resHeaders.put("Access-Control-Allow-Origin", origin);
			resHeaders.put("Access-Control-Allow-Credentials", "true");
		}

		response.statusCode = 200;
		response.write(encodedPacket);
		if(log.isDebugEnabled()) log.debug(this.getName() + " writing " + encodedPacket);
	}
}
