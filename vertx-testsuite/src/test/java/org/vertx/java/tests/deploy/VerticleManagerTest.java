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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.DefaultModuleRepository;
import org.vertx.java.deploy.impl.VerticleManager;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * @author <a href="http://www.laufer-online.com">Jens Laufer</a>
 */
public class VerticleManagerTest {

  private static final Logger log = LoggerFactory.getLogger(VerticleManagerTest.class);

	private static VertxInternal vertx;
  private static String oldVertxModFolder;
  
  private VerticleManager verticleManager;
  private static final String VERTX_MOD_PROPERTY_NAME = "vertx.mods";
  private static final String HTTP_PROXY_HOST_PROP_NAME = "http.proxyHost";
  private static final String HTTP_PROXY_PORT_PROP_NAME = "http.proxyPort";
  private static final String TEST_MODULE1 = "vertx.web-server-v1.0";
  private static final String TEST_MODULE2 = "vertx.work-queue-v1.2";

  @BeforeClass
  public static void beforeClass() throws Exception {
    new RepoServer(TEST_MODULE1, TEST_MODULE2);
    vertx = new DefaultVertx();
    
    if(System.getProperty("vertx.mods")!= null){
      oldVertxModFolder = System.getProperty(VERTX_MOD_PROPERTY_NAME);
      System.clearProperty(VERTX_MOD_PROPERTY_NAME);
    }
  }

  @AfterClass
  public static void afterClass() throws Exception {
    if(oldVertxModFolder != null){
      System.setProperty(VERTX_MOD_PROPERTY_NAME, oldVertxModFolder);
    }
  }

  @Before
  public void before() throws Exception {
    clearProxyProperties();
    delete(new File("mods"));
  }

  @After
  public void after() throws Exception {
    clearProxyProperties();
    delete(new File("mods"));
  }
  
  @Test
  public void testDoInstallModuleWithRepo() throws Exception {
    verticleManager = new VerticleManager(vertx, null, new DefaultModuleRepository(vertx, "localhost:9093"));
    AsyncResult<Void> res = verticleManager.moduleManager().installOne(TEST_MODULE2);
    assertNotNull(res);
    assertTrue(res.succeeded());
    assertTrue(new File("mods/" + TEST_MODULE2 + "/mod.json").exists());
  }

  @Test
  public void testDoInstallModuleWithProxy() throws Exception {
    System.getProperties().setProperty(HTTP_PROXY_HOST_PROP_NAME, "localhost");
    System.getProperties().setProperty(HTTP_PROXY_PORT_PROP_NAME, "9093");
    verticleManager = new VerticleManager(vertx);
    AsyncResult<Void> res = verticleManager.moduleManager().installOne(TEST_MODULE1);
    assertTrue(res.succeeded());
    assertTrue(new File("mods/" + TEST_MODULE1 + "/mod.json").exists());
  }

  private void clearProxyProperties() {
    System.clearProperty(HTTP_PROXY_HOST_PROP_NAME);
    System.clearProperty(HTTP_PROXY_PORT_PROP_NAME);
  }

  private void delete(File f) {
  	// At least my windows box is causing sometime trouble ...
		for(int i=0; i < 5; i++) {
	  	if (!f.exists()) {
	  		break;
	  	}
	    try {
				vertx.fileSystem().deleteSync(f.getAbsolutePath(), true);
			} catch (Exception ex) {
				log.error(ex);
				
				try {
					Thread.sleep(200);
				} catch (InterruptedException ignore) {
				}
			}
		}
  }
}
