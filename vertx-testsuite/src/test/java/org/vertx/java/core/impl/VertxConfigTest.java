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
package org.vertx.java.core.impl;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.codehaus.jackson.JsonNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.http.impl.ProxyConfig;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

public class VertxConfigTest {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(VertxConfigTest.class);

  private Map<String, String> env;
  private Properties system;
  
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		env = new HashMap<>(System.getenv());
		system = new Properties(System.getProperties());
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void emptyJson() throws IOException {
		new VertxConfigFactory().load("{}");
	}

	@Test
	public void loadSystemDefault() {
		new VertxConfigFactory().load();
	}
	
	@Test
	public void emptyFileWithComments() throws IOException {
		VertxConfig cfg = newVertxConfigFromFile("test-1.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(0, cfg.root().size());
	}

	@Test
	public void noPlaceholder() throws IOException {
		VertxConfig cfg = newVertxConfigFromFile("test-2.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.ENABLED).getBooleanValue());
		assertEquals(123, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path("number").getIntValue());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isArray());
		assertEquals(3, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).size());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.USER).isTextual());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.PASSWORD).isTextual());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).isArray());
		assertEquals(0, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).size());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).isArray());
		assertEquals(1, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).size());
	}

	@Test
	public void withPlaceholder() throws IOException {
		VertxConfig cfg = newVertxConfigFromFile("test-3.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.ENABLED).getBooleanValue());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isArray());
		assertEquals(1, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).size());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.USER).isNull());
		assertNull(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.USER).getTextValue());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.PASSWORD).isNull());
		assertNull(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.PASSWORD).getTextValue());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).isArray());
		assertEquals(0, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).size());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).isArray());
		assertEquals(1, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).size());
	}

	@Test
	public void withProperties() throws IOException {
		system.setProperty(ProxyConfig.HTTP_PROXY_HOST_PROP_NAME, "www.test1.com:1234");
		env.put(ProxyConfig.HTTP_PROXY_HOST_ENV_NAME, "www.test2.com:9876");
		
		system.setProperty(ProxyConfig.HTTP_PROXY_USER_PROP_NAME, "user1");
		env.put(ProxyConfig.HTTP_PROXY_USER_ENV_NAME, "user2");

		system.setProperty(ProxyConfig.HTTP_PROXY_PASSWORD_PROP_NAME, "password1");
		env.put(ProxyConfig.HTTP_PROXY_PASSWORD_ENV_NAME, "password2");

		VertxConfig cfg = newVertxConfigFromFile("test-3.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isArray());
		assertEquals(3, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).size());
		assertEquals("www.test1.com:1234", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).get(0).asText());
		assertEquals("www.test2.com:9876", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).get(1).asText());
		assertEquals("http://www.mycompany.com:8181", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).get(2).asText());
		assertEquals("user2", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.USER).getTextValue());
		assertEquals("password2", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.PASSWORD).getTextValue());
	}

	@Test
	public void firstAlternativeTrueTrue() throws IOException {
		system.setProperty(ProxyConfig.HTTP_PROXY_HOST_PROP_NAME, "www.test1.com:1234");
		env.put(ProxyConfig.HTTP_PROXY_HOST_ENV_NAME, "www.test2.com:9876");

		VertxConfig cfg = newVertxConfigFromFile("test-4.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isTextual());
		assertEquals("www.test1.com:1234", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).getTextValue());
	}

	@Test
	public void firstAlternativeFalseTrue() throws IOException {
		// system.setProperty(ProxyConfig.HTTP_PROXY_HOST_PROP_NAME, "www.test1.com:1234");
		env.put(ProxyConfig.HTTP_PROXY_HOST_ENV_NAME, "www.test2.com:9876");

		VertxConfig cfg = newVertxConfigFromFile("test-4.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isTextual());
		assertEquals("www.test2.com:9876", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).getTextValue());
	}

	@Test
	public void firstAlternativeTrueFalse() throws IOException {
		system.setProperty(ProxyConfig.HTTP_PROXY_HOST_PROP_NAME, "www.test1.com:1234");
		// env.put(ProxyConfig.HTTP_PROXY_HOST_ENV_NAME, "www.test2.com:9876");

		VertxConfig cfg = newVertxConfigFromFile("test-4.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isTextual());
		assertEquals("www.test1.com:1234", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).getTextValue());
	}

	@Test
	public void firstAlternativeFalseFalse() throws IOException {
		// system.setProperty(ProxyConfig.HTTP_PROXY_HOST_PROP_NAME, "www.test1.com:1234");
		// env.put(ProxyConfig.HTTP_PROXY_HOST_ENV_NAME, "www.test2.com:9876");

		VertxConfig cfg = newVertxConfigFromFile("test-4.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isTextual());
		assertEquals("http://www.mycompany.com:8181", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).getTextValue());
	}

	@Test
	public void insertArray0() throws IOException {
		// system.setProperty(ProxyConfig.HTTP_PROXY_INCLUDE_PROP_NAME, "include-1");
		// system.setProperty(ProxyConfig.HTTP_PROXY_EXCLUDE_PROP_NAME, "exclude-1");

		VertxConfig cfg = newVertxConfigFromFile("test-4.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isTextual());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).isArray());
		assertEquals(0, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).size());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).isArray());
		assertEquals(1, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).size());
		assertEquals("*.mycompany.com", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).get(0).getTextValue());
	}

	@Test
	public void insertArray1() throws IOException {
		system.setProperty(ProxyConfig.HTTP_PROXY_EXTERNALS_PROP_NAME, "include-1");
		system.setProperty(ProxyConfig.HTTP_PROXY_INTERNALS_PROP_NAME, "exclude-1");

		VertxConfig cfg = newVertxConfigFromFile("test-4.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isTextual());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).isArray());
		assertEquals(1, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).size());
		assertEquals("include-1", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).get(0).getTextValue());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).isArray());
		assertEquals(2, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).size());
		assertEquals("exclude-1", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).get(0).getTextValue());
		assertEquals("*.mycompany.com", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).get(1).getTextValue());
	}

	@Test
	public void insertArray2() throws IOException {
		system.setProperty(ProxyConfig.HTTP_PROXY_EXTERNALS_PROP_NAME, "include-1, include-2, include-3");
		system.setProperty(ProxyConfig.HTTP_PROXY_INTERNALS_PROP_NAME, "exclude-1, exclude-2, exclude-3");

		VertxConfig cfg = newVertxConfigFromFile("test-4.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(1, cfg.root().size());
		assertFalse(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).isMissingNode());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.HOST).isTextual());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).isArray());
		assertEquals(3, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).size());
		assertEquals("include-1", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).get(0).getTextValue());
		assertEquals("include-2", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).get(1).getTextValue());
		assertEquals("include-3", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.EXTERNAL).get(2).getTextValue());
		assertTrue(cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).isArray());
		assertEquals(4, cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).size());
		assertEquals("exclude-1", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).get(0).getTextValue());
		assertEquals("exclude-2", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).get(1).getTextValue());
		assertEquals("exclude-3", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).get(2).getTextValue());
		assertEquals("*.mycompany.com", cfg.root().path(ProxyConfig.JSON_PROXY_NODE).path(ProxyConfig.INTERNAL).get(3).getTextValue());
	}

	@Test
	public void insertJson() throws IOException {
		system.setProperty("MERGE_JSON", "{ abc: 123 }");

		VertxConfig cfg = newVertxConfigFromFile("test-5.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(2, cfg.root().size());
		assertFalse(cfg.root().path("merge").isMissingNode());
		assertTrue(cfg.root().path("merge").isObject());
		assertEquals(123, cfg.root().path("merge").path("abc").asInt(-1));
	}

	@Test
	public void insertFile() throws IOException {
		VertxConfig cfg = newVertxConfigFromFile("test-5.json");
		assertNotNull(cfg);
		assertNotNull(cfg.root());
		assertEquals(2, cfg.root().size());
		assertFalse(cfg.root().path("whatever").isMissingNode());
		assertTrue(cfg.root().path("whatever").isObject());
		assertEquals(true, cfg.root().path("whatever").path("abc").asBoolean());
	}
	
	@Test
	public void loadDefaults() {
		VertxConfigFactory fac = new VertxConfigFactory();
		assertNotNull(fac.loadDefault());
	}
	
	@Test
	public void mergeJson() throws IOException {
		VertxConfigFactory fac = new VertxConfigFactory();
		
		JsonNode n1 = newVertxConfig("{}").root();
		JsonNode def = newVertxConfig("{a:1}").root();
		assertEquals("{\"a\":1}", fac.merge(n1, def).toString());
		
		n1 = newVertxConfig("{a:1}").root();
		def = newVertxConfig("{a:2}").root();
		assertEquals("{\"a\":1}", fac.merge(n1, def).toString());
		
		n1 = newVertxConfig("{}").root();
		def = newVertxConfig("{a:1,b:{b1:2,b2:3}}").root();
		assertEquals("{\"a\":1,\"b\":{\"b1\":2,\"b2\":3}}", fac.merge(n1, def).toString());
		
		n1 = newVertxConfig("{a:2}").root();
		def = newVertxConfig("{a:1,b:{b1:2,b2:3}}").root();
		assertEquals("{\"a\":2,\"b\":{\"b1\":2,\"b2\":3}}", fac.merge(n1, def).toString());
		
		n1 = newVertxConfig("{a:2,b:{b1:4}}").root();
		def = newVertxConfig("{a:1,b:{b1:2,b2:3}}").root();
		assertEquals("{\"a\":2,\"b\":{\"b1\":4,\"b2\":3}}", fac.merge(n1, def).toString());
		
		n1 = newVertxConfig("{}").root();
		def = newVertxConfig("{a:1,b:[1,2]}").root();
		assertEquals("{\"a\":1,\"b\":[1,2]}", fac.merge(n1, def).toString());
		
		n1 = newVertxConfig("{b:3}").root();
		def = newVertxConfig("{a:1,b:[1,2]}").root();
		assertEquals("{\"b\":3,\"a\":1}", fac.merge(n1, def).toString());
	}
	
	private VertxConfig newVertxConfigFromFile(String name) throws IOException {
		return newVertxConfig(getFileContent(name));
	}

	private VertxConfig newVertxConfig(String data) throws IOException {
		return new VertxConfigFactory() {
			@Override
			protected String systemProperty(String key) {
				return system.getProperty(key);
			}
			@Override
			protected String envVariable(String key) {
				return env.get(key);
			}
			@Override
			protected JsonNode merge(JsonNode node, JsonNode defs) {
				// ignore the defaults during the test. They only distract.
				return node;
			}
		}.load(data);
	}
	
	private String getFileContent(String name) {
		try (InputStream in = getClass().getResourceAsStream(name)) {
			return new Scanner(in).useDelimiter("\\A").next();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
}
