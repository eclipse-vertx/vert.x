package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.shareddata.Shareable;

import java.util.Date;
import java.util.Map;

/**
 *
 * Refered
 * - Manager.prototype.handshakeData
 * - https://github.com/LearnBoost/socket.io/wiki/Authorizing
 *
 * @author Keesun Baik
 */
public class HandshakeData implements Shareable {

	// headers: req.headers       // <Object> the headers of the request
	private Map<String, String> headers;
	// time: (new Date) +''       // <String> date time of the connection
	private String time;
	// address: socket.address()  // <Object> remoteAddress and remotePort object
	private Object address;
	// xdomain: !!headers.origin  // <Boolean> was it a cross domain request?
	private boolean xdomain;
	// secure: socket.secure      // <Boolean> https connection
	private boolean secure;
	// issued: +date              // <Number> EPOCH of when the handshake was created
	private long issued;
	// url: request.url          // <String> the entrance path of the request
	private String url;
	// query: data.query          // <Object> the result of url.parse().query or a empty object
	private Map<String, String> queryParams;

	public HandshakeData(ClientData clientData) {
		Date now = new Date();
		this.headers = clientData.getHeaders();
		this.time = now.toString();
		this.xdomain = clientData.getHeaders().get("Origin") != null;
		this.secure = clientData.getRequest().uri.contains("https");
		this.issued = now.getTime();
		this.url = clientData.getRequest().uri;
		this.queryParams = clientData.getParams();
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public String getTime() {
		return time;
	}

	public Object getAddress() {
		return address;
	}

	public boolean isXdomain() {
		return xdomain;
	}

	public boolean isSecure() {
		return secure;
	}

	public long getIssued() {
		return issued;
	}

	public String getUrl() {
		return url;
	}

	public Map<String, String> getQueryParams() {
		return queryParams;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public void setTime(String time) {
		this.time = time;
	}

	public void setAddress(Object address) {
		this.address = address;
	}

	public void setXdomain(boolean xdomain) {
		this.xdomain = xdomain;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public void setIssued(long issued) {
		this.issued = issued;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void setQueryParams(Map<String, String> queryParams) {
		this.queryParams = queryParams;
	}
}
