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

import static org.junit.Assert.*;
import jline.internal.Log;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;

public class DefaultHttpClientTest {

	private static VertxInternal vertx;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		vertx = new DefaultVertx();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		vertx.stop();
		vertx = null;
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testProxy() {
		DefaultHttpClient client = new DefaultHttpClient(vertx);
		// To enable the proxy either configure it via one of the many option, or remove the comment 
		// from the following line and set the proxy host appropriatly.
		// vertx.vertxConfig().proxyConfig().setProxyHost("http://www.myproxy.com:8181");
		HttpClientRequest req = client.get("http://www.heise.de", null);
		req.exceptionHandler(new Handler<Exception>() {
			@Override
			public void handle(Exception event) {
				// Improve error message
				assertTrue(event instanceof HttpClientConnectException);
				assertTrue(event.getMessage().contains("Server"));
				assertTrue(event.getMessage().contains("proxy"));
				Log.error(event.getMessage());
			}});
		req.end();
	}
}
