/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.http.impl;

public class HttpClientConnectException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String proxy;
	private final String host;
	private final int port;

	private final String message;
	
	public HttpClientConnectException(final String proxy, final String host, final int port, final Throwable cause) {
		super(cause);
		this.proxy = proxy;
		this.host = host;
		this.port = port;
		
		StringBuffer buf = new StringBuffer(300);
		buf.append("HTTP client connection error. Server: " + host + ":" + port + " [proxy: " + proxy + "]");
		
		message = buf.toString();
	}
	
	@Override
	public String getMessage() {
		return message;
	}

	public String getProxy() {
		return proxy;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}
}
