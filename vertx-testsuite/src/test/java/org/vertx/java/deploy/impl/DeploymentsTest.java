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
package org.vertx.java.deploy.impl;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.deploy.impl.cli.ExtendedDefaultVertx;

public class DeploymentsTest {

	static ExtendedDefaultVertx vertx;
	
	Deployments deps;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		vertx = new ExtendedDefaultVertx();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		vertx.stop();
		vertx = null;
	}

	@Before
	public void setUp() throws Exception {
		deps = new Deployments();
	}

	@After
	public void tearDown() throws Exception {
		deps = null;
	}

	@Test
	public void testDefault() {
		assertTrue(deps.isEmpty());
		assertFalse(deps.iterator().hasNext());
		assertEquals(0, deps.size());
	}

	@Test
	public void testAddNoParent() {
		String name = "d1";
		assertNull(deps.get(name));
		deps.add(newDeployment(name, null));
		assertFalse(deps.isEmpty());
		assertTrue(deps.iterator().hasNext());
		assertNotNull(deps.get(name));
		assertEquals(1, deps.size());
	}

	@Test
	public void testWithParent() {
		String parent = "p1";
		assertNull(deps.get(parent));
		deps.add(newDeployment(parent, null));
		assertFalse(deps.isEmpty());
		assertTrue(deps.iterator().hasNext());
		assertNotNull(deps.get(parent));
		assertEquals(1, deps.size());

		String name = "d1";
		assertNull(deps.get(name));
		deps.add(newDeployment(name, parent));
		assertFalse(deps.isEmpty());
		assertTrue(deps.iterator().hasNext());
		assertNotNull(deps.get(name));
		assertEquals(2, deps.size());
	}

	@Test
	public void testRemoveNotFounf() {
		assertNull(deps.remove("xxx"));
	}
	
	@Test
	public void testRemoveNoParent() {
		String name = "d1";
		assertNull(deps.get(name));
		deps.add(newDeployment(name, null));
		assertFalse(deps.isEmpty());
		assertTrue(deps.iterator().hasNext());
		assertNotNull(deps.get(name));
		assertEquals(1, deps.size());
		
		deps.remove(name);
		assertNull(deps.get(name));
		assertTrue(deps.isEmpty());
		assertFalse(deps.iterator().hasNext());
		assertNull(deps.get(name));
		assertEquals(0, deps.size());
	}

	@Test
	public void testRemoveWithParent() {
		String parent = "p1";
		deps.add(newDeployment(parent, null));

		String name = "d1";
		assertNull(deps.get(name));
		deps.add(newDeployment(name, parent));
		assertFalse(deps.isEmpty());
		assertTrue(deps.iterator().hasNext());
		assertNotNull(deps.get(name));
		assertEquals(2, deps.size());
		
		assertNotNull(deps.remove(name));
		assertFalse(deps.isEmpty());
		assertTrue(deps.iterator().hasNext());
		assertNull(deps.get(name));
		assertNotNull(deps.get(parent));
		assertEquals(1, deps.size());
		
		assertNotNull(deps.remove(parent));
		assertTrue(deps.isEmpty());
		assertFalse(deps.iterator().hasNext());
		assertNull(deps.get(parent));
		assertEquals(0, deps.size());
	}

	@Test
	public void testRemoveParentFirst() {
		String parent = "p1";
		deps.add(newDeployment(parent, null));

		String name = "d1";
		assertNull(deps.get(name));
		deps.add(newDeployment(name, parent));
		assertFalse(deps.isEmpty());
		assertTrue(deps.iterator().hasNext());
		assertNotNull(deps.get(name));
		assertEquals(2, deps.size());
		
		assertNotNull(deps.remove(parent));
		assertFalse(deps.isEmpty());
		assertTrue(deps.iterator().hasNext());
		assertNotNull(deps.get(name));
		assertEquals(1, deps.size());
		
		assertNotNull(deps.remove(name));
		assertTrue(deps.isEmpty());
		assertFalse(deps.iterator().hasNext());
		assertNull(deps.get(name));
		assertEquals(0, deps.size());
	}
	
	private Deployment newDeployment(String name, String parent) {
		VertxModule module = new VertxModule(vertx.moduleManager(null), null);
		return new Deployment(name, module, 1, null, parent);
	}
}
