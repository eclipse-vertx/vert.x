package org.vertx.java.core.socketio.impl.transports;

import org.vertx.java.core.json.impl.Json;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.socketio.impl.ClientData;
import org.vertx.java.core.socketio.impl.Manager;

import java.util.Map;

/**
 * @see <a href="https://github.com/LearnBoost/socket.io/blob/master/lib/transports/jsonp-polling.js">jsonp-polling.js</a>
 * @author Keesun Baik
 */
public class JsonpPolling extends HttpPolling {

	private static final Logger log = LoggerFactory.getLogger(JsonpPolling.class);

	private String head;
	private String foot;

	public JsonpPolling(Manager manager, ClientData clientData)  {
		super(manager, clientData);
		this.head = "io.j[0](";
		this.foot = ");";
	}

	@Override
	protected boolean isPostEncoded() {
		return true;
	}

	/**
	 * Performs the write.
	 *
	 * @see "JSONPPolling.prototype.doWrite"
	 * @param encodedPacket
	 */
	@Override
	protected void doWrite(String encodedPacket) {
		super.doWrite(encodedPacket);

		// JSON.stringfy(encodedPacket)
//		String result = JsonUtils.stringify(encodedPacket);
		String result = Json.encode(encodedPacket);
		String data = (encodedPacket == null) ? "" : this.head + result + this.foot;

		this.response.statusCode = 200;
		Map<String,Object> headers = this.response.headers();
		headers.put("Content-Type", "text/javascript; charset=UTF-8");
		headers.put("Content-Length", data.length());
		headers.put("Connection", "Keep-Alive");
		headers.put("X-XSS-Protection", "0");
		response.write(data);
		if(log.isInfoEnabled()) log.info(getName() + " writing " + data);
	}

	@Override
	protected String getName() {
		return "jsonppolling";
	}
}
