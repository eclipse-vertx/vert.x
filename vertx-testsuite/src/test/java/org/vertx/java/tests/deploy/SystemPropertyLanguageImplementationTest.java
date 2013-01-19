package org.vertx.java.tests.deploy;

import java.io.File;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.impl.VerticleManager;

public class SystemPropertyLanguageImplementationTest {

  private VerticleManager verticleManager;
  private VertxInternal vertxInternal;

  @BeforeClass
  public static void beforeClass() {
    System.setProperty("vertx.langs.foo", "org.vertx.java.tests.deploy.FooLangVerticleFactory");
  }

  @Before
  public void before() throws Exception {
    vertxInternal = new DefaultVertx();
    verticleManager = new VerticleManager(vertxInternal);
  }

  @Test
  public void deployFooVerticle() {
    String main = "test.foo";

    JsonObject config = new JsonObject();
    config.putString("foo", "foo");

    URL[] urls = new URL[0];
    File currentModDir = new File(System.getProperty("java.io.tmpdir"));
    String includes = null;

    final CountDownLatch latch = new CountDownLatch(1);
    Handler<String> doneHandler = new Handler<String>() {
      @Override
      public void handle(String event) {
        // TODO Auto-generated method stub
        latch.countDown();
      }
    };

    verticleManager.deployVerticle(false, main, config, urls, 1, currentModDir, includes, doneHandler);

    boolean await = false;

    try {
      await = latch.await(5000L, TimeUnit.MILLISECONDS);

    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    if (!await)  {
      Assert.fail("Probably not deployed");
    }
  }

  @AfterClass
  public static void cleanup() {
    System.clearProperty("vertx.langs.foo");
  }
}
