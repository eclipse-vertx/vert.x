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

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.impl.VertxConfig;
import org.vertx.java.core.impl.VertxConfigFactory;

public class ProxyConfigTest {

	// System env maps can't be modified => simulate
  private Map<String, String> env = new HashMap<>(System.getenv());

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		cleanup();
	}

	@After
	public void tearDown() throws Exception {
		cleanup();
	}

	private void cleanup() {
	  System.clearProperty(ProxyConfig.HTTP_PROXY_HOST__OLD__PROP_NAME);
	  System.clearProperty(ProxyConfig.HTTP_PROXY_PORT__OLD__PROP_NAME);

	  System.clearProperty(ProxyConfig.HTTP_PROXY_HOST_PROP_NAME);

	  System.clearProperty(ProxyConfig.HTTP_PROXY_USER_PROP_NAME);
	  System.clearProperty(ProxyConfig.HTTP_PROXY_PASSWORD_PROP_NAME);

	  System.clearProperty(ProxyConfig.HTTP_PROXY_INTERNALS_PROP_NAME);
	  System.clearProperty(ProxyConfig.HTTP_PROXY_EXTERNALS_PROP_NAME);

	  env.remove(ProxyConfig.HTTP_PROXY_HOST_ENV_NAME);
	  env.remove(ProxyConfig.HTTP_PROXY_PORT_ENV_NAME);

	  env.remove(ProxyConfig.HTTP_PROXY_USER_ENV_NAME);
	  env.remove(ProxyConfig.HTTP_PROXY_PASSWORD_ENV_NAME);
	  
	  env.remove(ProxyConfig.HTTP_PROXY_INTERNALS_ENV_NAME);
	  env.remove(ProxyConfig.HTTP_PROXY_EXTERNALS_ENV_NAME);
	}

	private VertxConfig newVertxConfig() {
		return new VertxConfig(VertxConfigFactory.createEmptyJson());	
	}
	
	@Test
	public void testUpdateTargetUri() {
		ProxyConfig cfg = newVertxConfig().proxyConfig();
		assertEquals("https://myhost/test.zip", cfg.updateTargetUri("/test.zip", "https", "myhost", 0, null, null));
		assertEquals("https://myhost/test.zip", cfg.updateTargetUri("test.zip", "https", "myhost", 0, null, null));
		assertEquals("https://myhost:8181/test.zip", cfg.updateTargetUri("/test.zip", "https", "myhost", 8181, null, null));
		assertEquals("https://tester@myhost:8181/test.zip", cfg.updateTargetUri("/test.zip", "https", "myhost", 8181, "tester", null));
		assertEquals("https://tester:password@myhost:8181/test.zip", cfg.updateTargetUri("/test.zip", "https", "myhost", 8181, "tester", "password"));
		assertEquals("http://whatever/test.zip", cfg.updateTargetUri("http://whatever/test.zip", "https", "myhost", 8181, "tester", "password"));
	}
	
	@Test
	public void emptyProxyHostDefaultToInternal() {
		ProxyConfig cfg = newVertxConfig().proxyConfig();
		assertNull(cfg.proxy());
		cfg.defaultToInternal(true);
		assertFalse(cfg.requiresProxy("http://www.github.com", false));
		assertFalse(cfg.requiresProxy("http://www.github.com", true));
	}
	
	@Test
	public void emptyProxyHostDefaultToExternal() {
		ProxyConfig cfg = newVertxConfig().proxyConfig();
		assertNull(cfg.proxy());
		cfg.defaultToInternal(false);
		assertTrue(cfg.requiresProxy("http://www.github.com", false));
		assertTrue(cfg.requiresProxy("http://www.github.com", true));
	}
	
	@Test
	public void emptyProxyHostDisabled() {
		ProxyConfig cfg = newVertxConfig().proxyConfig();
		assertNull(cfg.proxy());
		cfg.defaultToInternal(false);
		cfg.enabled(false);
		assertFalse(cfg.requiresProxy("http://www.github.com", false));
		assertFalse(cfg.requiresProxy("http://www.github.com", true));
	}
	
	@Test
	public void setProxyManuallyNotIncludesOrExcludes() {
		ProxyConfig cfg = newVertxConfig().proxyConfig();
		cfg.setProxyHost("http://www.myproxy.com");
		assertNotNull(cfg.proxy());
		assertFalse(cfg.requiresProxy("http://www.github.com", false));
		assertFalse(cfg.requiresProxy("http://www.github.com", true));
		
		cfg.defaultToInternal(false);
		assertTrue(cfg.requiresProxy("http://www.github.com", false));
		assertTrue(cfg.requiresProxy("http://www.github.com", true));
	}
	
	@Test
	public void includes() {
		ProxyConfig cfg = newVertxConfig().proxyConfig();
		cfg.setProxyHost("http://www.myproxy.com");
		assertNotNull(cfg.proxy());

		cfg.externalServers(null, true);
		assertFalse(cfg.matchesExternals("http://www.github.com"));

		cfg.externalServers("*", true);
		assertTrue(cfg.matchesExternals("http://www.github.com"));

		cfg.externalServers("*.github.com", true);
		assertTrue(cfg.matchesExternals("http://www.github.com"));
		assertTrue(cfg.matchesExternals("http://name@www.github.com"));
		assertTrue(cfg.matchesExternals("https://name:password@www.github.com"));
		assertFalse(cfg.matchesExternals("http://www.test.com"));

		cfg.externalServers("*", true);
		assertTrue(cfg.matchesExternals("http://www.github.com"));
		assertTrue(cfg.matchesExternals("http://www.test.com"));

		cfg.externalServers("*.github.com,*.amazon.com", true);
		assertTrue(cfg.matchesExternals("http://www.github.com"));
		assertTrue(cfg.matchesExternals("http://aws.amazon.com"));
		assertFalse(cfg.matchesExternals("http://www.test.com"));
		
		cfg.externalServers("*.github.com,*.amazon.com, www.*.murx.com", true);
		assertTrue(cfg.matchesExternals("http://www.github.com"));
		assertTrue(cfg.matchesExternals("http://aws.amazon.com"));
		assertTrue(cfg.matchesExternals("http://www.myserver.murx.com"));
		assertFalse(cfg.matchesExternals("http://www.murx.com"));
		assertFalse(cfg.matchesExternals("http://false.myserver.murx.com"));
		assertTrue(cfg.matchesExternals("http://user@www.myserver.murx.com"));
		assertFalse(cfg.matchesExternals("http://www.test.com"));
	}
	
	@Test
	public void excludes() {
		ProxyConfig cfg = newVertxConfig().proxyConfig();
		cfg.setProxyHost("http://www.myproxy.com");
		assertNotNull(cfg.proxy());

		cfg.internalServers(null, true);
		assertTrue(cfg.matchesInternals("http://www.github.com"));

		cfg.internalServers("*", true);
		assertTrue(cfg.matchesInternals("http://www.github.com"));

		cfg.internalServers("*.github.com", true);
		assertTrue(cfg.matchesInternals("http://www.github.com"));
		assertTrue(cfg.matchesInternals("http://name@www.github.com"));
		assertTrue(cfg.matchesInternals("https://name:password@www.github.com"));
		assertFalse(cfg.matchesInternals("http://www.test.com"));

		cfg.internalServers("*", true);
		assertTrue(cfg.matchesInternals("http://www.github.com"));
		assertTrue(cfg.matchesInternals("http://www.test.com"));

		cfg.internalServers("*.github.com,*.amazon.com", true);
		assertTrue(cfg.matchesInternals("http://www.github.com"));
		assertTrue(cfg.matchesInternals("http://aws.amazon.com"));
		assertFalse(cfg.matchesInternals("http://www.test.com"));
		
		cfg.internalServers("*.github.com,*.amazon.com, www.*.murx.com", true);
		assertTrue(cfg.matchesInternals("http://www.github.com"));
		assertTrue(cfg.matchesInternals("http://aws.amazon.com"));
		assertTrue(cfg.matchesInternals("http://www.myserver.murx.com"));
		assertFalse(cfg.matchesInternals("http://www.murx.com"));
		assertFalse(cfg.matchesInternals("http://false.myserver.murx.com"));
		assertTrue(cfg.matchesInternals("http://user@www.myserver.murx.com"));
		assertFalse(cfg.matchesInternals("http://www.test.com"));
	}
	
	@Test
	public void requiresProxy() {
		ProxyConfig cfg = newVertxConfig().proxyConfig();
		cfg.setProxyHost("http://www.myproxy.com");
		assertNotNull(cfg.proxy());
		
		cfg.externalServers(null, true); // true
		cfg.internalServers(null, true); // false
		assertFalse(cfg.requiresProxy("http://www.github.com", true));
		
		cfg.externalServers("*", true); // true
		cfg.internalServers(null, true); // false
		assertTrue(cfg.requiresProxy("http://www.github.com", true));

		cfg.externalServers(null, true); // true
		cfg.internalServers("*", true);  // true
		assertFalse(cfg.requiresProxy("http://www.github.com", true));
		assertFalse(cfg.requiresProxy("http://www.github.com", false));

		cfg.externalServers("*", true);  // true
		cfg.internalServers("*", true);  // true
		assertTrue(cfg.requiresProxy("http://www.github.com", true));
		assertFalse(cfg.requiresProxy("http://www.github.com", false));

		cfg.externalServers("*.github.com", true);
		assertTrue(cfg.requiresProxy("http://www.github.com", true));
		assertTrue(cfg.requiresProxy("http://name@www.github.com", true));
		assertTrue(cfg.requiresProxy("https://name:password@www.github.com", true));
		assertFalse(cfg.requiresProxy("http://www.test.com", true));
		assertFalse(cfg.requiresProxy("http://www.test.com", false));

		cfg.externalServers("*.github.com", true);
		cfg.internalServers("*.github.com", true);
		assertTrue(cfg.requiresProxy("http://www.github.com", true));
		assertTrue(cfg.requiresProxy("http://name@www.github.com", true));
		assertTrue(cfg.requiresProxy("https://name:password@www.github.com", true));
		assertTrue(cfg.requiresProxy("http://www.test.com", true));
		assertFalse(cfg.requiresProxy("http://www.test.com", false));
	}
}
