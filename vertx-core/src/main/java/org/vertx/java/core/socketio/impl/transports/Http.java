package org.vertx.java.core.socketio.impl.transports;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.socketio.impl.ClientData;
import org.vertx.java.core.socketio.impl.Manager;
import org.vertx.java.core.socketio.impl.Transport;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

/**
 * @see <a href="https://github.com/LearnBoost/socket.io/blob/master/lib/transports/http.js">http.js</a>
 * @author Keesun Baik
 */
public abstract class Http extends Transport {

	private static final Logger log = LoggerFactory.getLogger(Http.class);

	protected HttpServerResponse response;

	public Http(Manager manager, ClientData clientData) {
		super(manager, clientData);
	}

	/**
	 * Handles a request.
	 *
	 * @see "HTTPTransport.prototype.handleRequest"
	 */
	@Override
	protected void handleRequest() {

		// Always set the response in case an error is returned to the client
		this.response = request.response;

		if(!request.method.toUpperCase().equals("POST")) {
			super.handleRequest();
		} else {
			final Buffer buffer = new Buffer(0);
			final HttpServerResponse res = request.response;
			String origin = request.headers().get("ORIGIN");
			Map<String, Object> resHeaders = res.headers();
			resHeaders.put("Content-Length", 1);
			resHeaders.put("Content-Type", "text/plain; charset=UTF-8");

			if (origin != null) {
				// https://developer.mozilla.org/En/HTTP_Access_Control
				resHeaders.put("Access-Control-Allow-Origin", origin);
				resHeaders.put("Access-Control-Allow-Credentials", "true");
			}

			request.dataHandler(new Handler<Buffer>() {
				public void handle(Buffer data) {
					buffer.appendBuffer(data);
					if(buffer.length() >= manager.getSettings().getDestryBufferSize()) {
						resetBuffer(buffer);
						request.response.end();
					}
				}
			});

			request.endHandler(new Handler<Void>() {
				public void handle(Void event) {
					res.statusCode = 200;
					res.end("1");

					onData(isPostEncoded() ? parseeData(buffer) : buffer);
				}
			});

			// req.on('close', function () {
			request.exceptionHandler(new Handler<Exception>() {
				public void handle(Exception event) {
					resetBuffer(buffer);
					onClose();
				}
			});
		}

	}

	private Buffer parseeData(Buffer buffer) {
		String d = null;
		try {
			d = URLDecoder.decode(buffer.toString(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException(e);
		}
		d = d.replaceFirst("d=\"", "");
		d = d.substring(0, d.length() - 1);
		d = d.replace("\\\\", "\\");
		d = d.replace("\\\"", "\"");
		return new Buffer(d);
	}

	protected abstract boolean isPostEncoded();

	/**
	 * Handles data payload.
	 *
	 * @see "HTTPTransport.prototype.onData"
	 * @param data
	 */
	protected void onData(Buffer data) {
		List<JsonObject> messages = parser.decodePayload(data);
		if(log.isInfoEnabled()) log.info(getName() + " received data packet " + data);
		for(JsonObject message : messages) {
			onMessage(message);
		}
	}

	private void resetBuffer(Buffer buffer) {
		buffer = new Buffer(0);
	}

	/**
	 * Writes a payload of messages
	 *
	 * @see "HTTPTransport.prototype.payload"
	 * @param buffers
	 */
	@Override
	public void payload(List<Buffer> buffers) {
		this.write(parser.encodePayload(buffers));
	}

	/**
	 * Closes the request-response cycle
	 *
	 * @see "HTTPTransport.prototype.doClose"
	 */
	@Override
	protected void doClose() {
		this.response.end();
	}
}
