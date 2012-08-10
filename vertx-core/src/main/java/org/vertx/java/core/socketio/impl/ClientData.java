package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.http.HttpServerRequest;

import java.util.Map;

/**
 * @author Keesun Baik
 */
public class ClientData {

	private String query;
	private Map<String, String> headers;
	private Map<String, String> params;
	private String path;
	private int protocol;
	private String transport;
	private String id;
	private boolean isStatic;
	private HttpServerRequest request;

	public ClientData(String namespace, HttpServerRequest req) {
		this.request = req;
		this.query = req.query;
		this.headers = req.headers();
		this.params = req.params();
		this.path = req.path.substring(namespace.length());

		String[] pieces = path.substring(1).split("/");

		if(pieces.length > 0) this.protocol = Integer.parseInt(pieces[0]);
		if(pieces.length > 1) this.transport = pieces[1];
		if(pieces.length > 2) this.id = pieces[2];
//	TODO	this.isStatic = StaticHandler.has(this.path);
	}

	public String getQuery() {
		return query;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public String getPath() {
		return path;
	}

	public int getProtocol() {
		return protocol;
	}

	public String getTransport() {
		return transport;
	}

	public String getId() {
		return id;
	}

	public boolean isStatic() {
		return isStatic;
	}

	public Map<String, String> getParams() {
		return params;
	}

	public HttpServerRequest getRequest() {
		return request;
	}

	@Override
	public String toString() {
		return "ClientData{" +
				"query='" + query + '\'' +
				", headers=" + headers +
				", params=" + params +
				", path='" + path + '\'' +
				", protocol=" + protocol +
				", transport='" + transport + '\'' +
				", id='" + id + '\'' +
				", isStatic=" + isStatic +
				'}';
	}
}