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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.ModuleRepository;
import org.vertx.java.deploy.impl.ModuleWalker.ModuleVisitResult;
import org.vertx.java.deploy.impl.ModuleWalker.ModuleVisitor;

/**
 * 
 * @author Juergen Donnerstag
 */
public class ModuleManagerTest {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(ModuleManagerTest.class);

	public static DefaultVertx vertx;
	public VerticleManager verticleManager;
	public MyModuleManager moduleManager;

	@Rule
	public TemporaryFolder modDir = new TemporaryFolder();	
	
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
		moduleManager = new MyModuleManager(vertx, modDir.getRoot());
		verticleManager = new VerticleManager(vertx, moduleManager);
		assertSame(MyModuleManager.class, verticleManager.moduleManager().getClass());

		moduleManager.moduleRepositories().clear();
    moduleManager.moduleRepositories().add(
    		new LocalModuleRepository(vertx, new File("src/test/mod-test")));
    
    assertEquals(1, moduleManager.moduleRepositories().size());
	}

	@After
	public void tearDown() throws Exception {
	}

  @Test
  public void testSimple() throws Exception {
    String modName = "testmod1-1";
    assertNotNull(moduleManager.installOne(modName, null).get(30, TimeUnit.SECONDS));
    assertTrue(modDir.newFile(modName).isDirectory());
  }

  @Test
  public void testInstallOne() throws Exception {
    String modName = "testmod8-1";
    assertNotNull(moduleManager.installOne(modName, null).get(30, TimeUnit.SECONDS));
    assertTrue(modDir.newFile(modName).isDirectory());
  }

  @Test
  public void testInstallAll() throws Exception {
    String modName = "testmod8-1";
    assertNotNull(moduleManager.install(modName, null).get(30, TimeUnit.SECONDS));
    assertTrue(modDir.newFile(modName).isDirectory());
    assertTrue(modDir.newFile("testmod8-2").isDirectory());
    assertTrue(modDir.newFile("testmod8-3").isDirectory());
  }
  
  @Test
  public void testWalker() throws Exception {
    String modName = "testmod8-1";
    assertNotNull(moduleManager.install(modName, null).get(30, TimeUnit.SECONDS));
    
    final List<String> list = new ArrayList<>();
    moduleManager.moduleWalker(modName, new ModuleVisitor<Void>() {
			@Override
			protected ModuleVisitResult visit(VertxModule module, ModuleWalker<Void> walker) {
				list.add(module.name());
				return ModuleVisitResult.CONTINUE;
			}});
    
    assertEquals(3, list.size());
    assertTrue(list.contains("testmod8-1"));
    assertTrue(list.contains("testmod8-2"));
    assertTrue(list.contains("testmod8-3"));
    
    moduleManager.printModuleTree(modName, System.out);
  }

  @Test
  public void testNullConfig() {
    String modName = "simumod1-1";
    VertxModule module = moduleManager.module(modName);
    assertFalse(module.exists());
    assertEquals(VertxModule.NULL_CONFIG, module.config());
    assertNull(module.config().main());
  }

  @Test
  public void testSimulatedModule() {
    VertxModule module1 = createModule("simumod1");
    module1.config().json().putString("main", "myMain");
    module1.config().json().putString("includes", "simumod1-1, simumod1-2");

    VertxModule module1_1 = createModule("simumod1-1");
    module1_1.config().json().putString("includes", "simumod1-1-1");

    VertxModule module1_2 = createModule("simumod1-2");

    VertxModule module1_1_1 = createModule("simumod1-1-1");
    module1_1_1.config().json().putString("includes", "simumod1-1-1-1");
    
    moduleManager.printModuleTree(module1.name(), System.out);
  }
  
  private VertxModule createModule(String name) {
    VertxModule module = new VertxModule(moduleManager, name) {
    	@Override
    	public boolean exists() {
    		return /* modDir.canRead() && */ config() != NULL_CONFIG;
    	}
    };
    
    module.config(new ModuleConfig());
    assertNotSame(VertxModule.NULL_CONFIG, module.config());
    
    moduleManager.sims.put(module.name(), module);
    return module;
  }
  
  public class MyModuleManager extends ModuleManager {

  	public final Map<String, VertxModule> sims = new HashMap<>();
  	
		public MyModuleManager(VertxInternal vertx, File modRoot, ModuleRepository... repos) {
			super(vertx, modRoot, repos);
		}

		@Override
		public VertxModule module(String modName) {
			VertxModule m = sims.get(modName);
			if (m != null) {
				return m;
			}
			return super.module(modName);
		}
  }
}
