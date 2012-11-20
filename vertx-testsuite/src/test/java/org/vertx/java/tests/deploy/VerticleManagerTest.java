package org.vertx.java.tests.deploy;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.deploy.impl.VerticleManager;

/**
 * @author <a href="http://www.laufer-online.com">Jens Laufer</a>
 */
public class VerticleManagerTest {
  private VerticleManager verticleManager;
  private VertxInternal vertxInternal;
  private static final String VERTX_MOD_PROPERTY_NAME = "vertx.mods";
  private static final String HTTP_PROXY_HOST_PROP_NAME = "http.proxyHost";
  private static final String HTTP_PROXY_PORT_PROP_NAME = "http.proxyPort";
  private static final String TEST_MODULE1 = "vertx.web-server-v1.0";
  private static final String TEST_MODULE2 = "vertx.work-queue-v1.2";
  private static String oldVertxModFolder;

  @BeforeClass
  public static void beforeClass() throws Exception {
    new RepoServer(TEST_MODULE1, TEST_MODULE2);
  }


  @Before
  public void before() throws Exception {
    if(System.getProperty("vertx.mods")!= null){
      oldVertxModFolder = System.getProperty(VERTX_MOD_PROPERTY_NAME);
      System.clearProperty(VERTX_MOD_PROPERTY_NAME);
    }
    vertxInternal = new DefaultVertx();
    this.clearProxyProperties();
    this.delete(new File("mods"));
  }

  @After
  public void after() throws Exception {
    this.clearProxyProperties();
    this.delete(new File("mods"));
    if(oldVertxModFolder != null){
      System.setProperty(VERTX_MOD_PROPERTY_NAME, oldVertxModFolder);
    }
  }

  @Test
  public void testDoInstallModuleWithRepo() throws Exception {
    verticleManager = new VerticleManager(vertxInternal, "localhost:9093");
    verticleManager.installMod(TEST_MODULE2);
    assertTrue(new File("mods/" + TEST_MODULE2 + "/mod.json").exists());
  }

  @Test
  public void testDoInstallModuleWithProxy() throws Exception {
    System.getProperties().setProperty(HTTP_PROXY_HOST_PROP_NAME,
        "localhost");
    System.getProperties().setProperty(HTTP_PROXY_PORT_PROP_NAME, "9093");
    verticleManager = new VerticleManager(vertxInternal);
    verticleManager.installMod(TEST_MODULE1);
    assertTrue(new File("mods/" + TEST_MODULE1 + "/mod.json").exists());
  }

  private void clearProxyProperties() {
    System.clearProperty(HTTP_PROXY_HOST_PROP_NAME);
    System.clearProperty(HTTP_PROXY_PORT_PROP_NAME);
  }

  private void delete(File f) throws IOException {
    if (f.isDirectory()) {
      for (File c : f.listFiles()) {
        delete(c);
      }
      f.delete();
    } else {
      f.delete();
    }
  }
  
}
