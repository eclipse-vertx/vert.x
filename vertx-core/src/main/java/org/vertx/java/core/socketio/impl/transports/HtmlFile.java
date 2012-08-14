package org.vertx.java.core.socketio.impl.transports;

import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Json;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.socketio.impl.ClientData;
import org.vertx.java.core.socketio.impl.Manager;

import java.util.Map;

/**
 * @see <a href="https://github.com/LearnBoost/socket.io/blob/master/lib/transports/htmlfile.js">htmlfile.js</a>
 * @author Keesun Baik
 */
public class HtmlFile extends Http {

	private static final Logger log = LoggerFactory.getLogger(HtmlFile.class);

	public HtmlFile(Manager manager, ClientData clientData) {
		super(manager, clientData);
	}

	@Override
	protected boolean isPostEncoded() {
		return false;
	}

	@Override
	protected String getName() {
		return "htmlfile";
	}

	/**
	 * Handles the request.
	 *
	 * @see "HTMLFile.prototype.handleRequest"
	 * @param req
	 */
	@Override
	protected void handleRequest(HttpServerRequest req) {
		super.handleRequest(req);

		if(req.method.equals("GET")) {
			response.statusCode = 200;
			Map<String, Object> headers = response.headers();
			headers.put("Content-Type", "text/html; charset=UTF-8");
			headers.put("Connection", "keep-alive");
			headers.put("Transfer-Encoding", "chunked");
		}

		String space173 = "";
		for(int i = 0 ; i < 173 ; i++) {
			space173 += " ";
		}

		response.write("<html><body><script>var _ = function (msg) { parent.s._(msg, document); };</script>" + space173);
	}

	/**
	 * Performs the write.
	 *
	 * @see "HTMLFile.prototype.write"
	 * @param encodedPacket
	 */
	@Override
	public void write(String encodedPacket) {
		String data = "<script>_(" + Json.encode(encodedPacket) + ");</script>";
		response.write(data, new SimpleHandler() {
			protected void handle() {
				isDrained = true;
			}
		});

		if(log.isDebugEnabled()) log.debug(this.getName() + " writing " + data);
	}
}
