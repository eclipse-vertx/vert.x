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
package org.vertx.java.deploy.impl.cli;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.deploy.impl.VertxModule;

/**
 * 
 * @author Juergen Donnerstag
 */
public class StarterTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDisplayHelp() throws IOException {
		try (StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw)) {
			new Starter().displaySyntax(pw);
			
			assertTrue(sw.toString().contains("vertx"));
			assertTrue(sw.toString().contains("version"));
			assertTrue(sw.toString().contains("-cp"));
			assertTrue(sw.toString().contains("-includes"));
		}
	}

	@Test
	public void testVersion() throws Exception {
		MyStarter starter = new MyStarter();
		starter.run(new String[] { "version" });
		assertTrue(starter.version);
	}

	@Test
	public void testNoArgs() throws Exception {
		MyStarter starter = new MyStarter();
		assertFalse(starter.run(new String[] {}));
	}

	@Test
	public void testAnyOneArg() throws Exception {
		MyStarter starter = new MyStarter();
		assertFalse(starter.run(new String[] { "notVersion" }));
	}

	@Test
	public void testAnyTwoArg() throws Exception {
		MyStarter starter = new MyStarter();
		assertFalse(starter.run(new String[] { "arg1", "arg2" }));
	}

	@Test
	public void testInstall() throws Exception {
		MyStarter starter = new MyStarter();
		assertTrue(starter.run(new String[] { "install", "myMod" }));
		assertEquals("myMod", starter.modName);
	}

	@Test
	public void testUninstall() throws Exception {
		MyStarter starter = new MyStarter();
		assertTrue(starter.run(new String[] { "uninstall", "myMod2" }));
		assertEquals("myMod2", starter.modName);
	}

	@Test
	public void testRun() throws Exception {
		MyStarter starter = new MyStarter();
		assertTrue(starter.run(new String[] { "run", "myMod3" }));
		assertEquals("myMod3", starter.module.config().main());
		assertEquals(1, starter.module.classPath().size());
		assertNull(starter.module.configFile());
		assertTrue(starter.module.exists());
		assertNull(starter.module.modDir());
		assertFalse(starter.module.config().autoRedeploy());
		assertNull(starter.module.config().configFile2());
		assertEquals(0, starter.module.config().includes().size());
		assertEquals(2, starter.module.config().json().getFieldNames().size());
		assertEquals("myMod3", starter.module.config().main());
		assertNull(starter.module.config().modDir());
		assertNull(starter.module.config().modName());
		assertFalse(starter.module.config().preserveCwd());
		assertFalse(starter.module.config().worker());
	}
	
	public class MyStarter extends Starter {
		public String modName;
		public VertxModule module;
		public int instances;
		public boolean version = false;
		public boolean help = false;
		
		@Override
		protected AsyncResult<Void> doInstall(ExtendedDefaultVertx vertx, String modName) {
			assertNotNull(vertx);
			this.modName = modName;
			vertx.verticleManager().unblock();
			return new AsyncResult<>();
		}
		
		@Override
		protected void doUninstall(ExtendedDefaultVertx vertx, String modName) {
			assertNotNull(vertx);
			this.modName = modName;
			vertx.verticleManager().unblock();
		}
		
		@Override
		protected AsyncResult<String> doRun(ExtendedDefaultVertx vertx, VertxModule module, int instances) {
			assertNotNull(vertx);
			this.module = module;
			this.instances = instances;
			vertx.verticleManager().unblock();
			return new AsyncResult<>(module.name());
		}
		
		@Override
		protected void doVersion() {
			this.version = true;
			super.doVersion();
		}

		@Override
		protected void displaySyntax(PrintWriter out) {
			this.help = true;
			super.displaySyntax(out);
		}
	}
}
