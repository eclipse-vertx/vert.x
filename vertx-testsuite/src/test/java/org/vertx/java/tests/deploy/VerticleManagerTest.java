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
package org.vertx.java.tests.deploy;

import static org.junit.Assert.*;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.ModuleRepository;
import org.vertx.java.deploy.impl.DefaultModuleRepository;
import org.vertx.java.deploy.impl.ModuleManager;
import org.vertx.java.deploy.impl.VerticleManager;

import java.io.File;

/**
 * @author <a href="http://www.laufer-online.com">Jens Laufer</a>
 */
public class VerticleManagerTest {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(VerticleManagerTest.class);

	private static VertxInternal vertx;
  private VerticleManager verticleManager;
  private static final String TEST_MODULE1 = "vertx.web-server-v1.0";
  private static final String TEST_MODULE2 = "vertx.work-queue-v1.2";

	@Rule
	public TemporaryFolder modDir = new TemporaryFolder();	

  @BeforeClass
  public static void beforeClass() throws Exception {
    new RepoServer(TEST_MODULE1, TEST_MODULE2);
    vertx = new DefaultVertx();
  }

  @AfterClass
  public static void afterClass() throws Exception {
  }

  @Before
  public void before() throws Exception {
    String dir = modDir.getRoot().getAbsolutePath();
    vertx.vertxConfig().modulesManagerConfig().modRoot(dir);
  }

  @After
  public void after() throws Exception {
  }
  
  @Test
  public void testDoInstallModuleWithRepo() throws Exception {
		ModuleRepository repository = new DefaultModuleRepository(vertx, "http://localhost:9093");
		ModuleManager moduleManager = new ModuleManager(vertx, null, repository);
    verticleManager = new VerticleManager(vertx, moduleManager);
    
    AsyncResult<String> res = verticleManager.moduleManager().installOne(TEST_MODULE2, null).get(30, TimeUnit.SECONDS);
    assertNotNull(res);
    assertTrue(res.succeeded());
    File f = new File(new File(modDir.getRoot().getAbsolutePath(), TEST_MODULE2), "mod.json");
    assertTrue(f.exists());
  }

  @Test
  public void testDoInstallModuleWithProxy() throws Exception {
    vertx.vertxConfig().proxyConfig().setProxyHost("http://localhost:9093");
//  	vertx.vertxConfig().proxyConfig().setProxyHost(null);
//  vertx.vertxConfig().proxyConfig().setProxyHost("mycompany:8080");
    verticleManager = new VerticleManager(vertx);
    AsyncResult<String> res = verticleManager.moduleManager().installOne(TEST_MODULE1, null).get(30, TimeUnit.SECONDS);
    assertNotNull("Timeout", res);
    assertTrue(res.succeeded());
    File f = new File(new File(modDir.getRoot().getAbsolutePath(), TEST_MODULE1), "mod.json");
    assertTrue(f.exists());
  }

  @Test
  public void installTwice() throws Exception {
    verticleManager = new VerticleManager(vertx);
    AsyncResult<Void> res = verticleManager.moduleManager().install(TEST_MODULE1, null).get(30, TimeUnit.SECONDS);
    assertNotNull("Timeout", res);
    assertTrue(res.succeeded());
    File f = new File(new File(modDir.getRoot().getAbsolutePath(), TEST_MODULE1), "mod.json");
    assertTrue(f.exists());
    
    res = verticleManager.moduleManager().install(TEST_MODULE1, null).get(30, TimeUnit.SECONDS);
    assertNotNull("Timeout", res);
    assertTrue(res.succeeded());
  }
}
