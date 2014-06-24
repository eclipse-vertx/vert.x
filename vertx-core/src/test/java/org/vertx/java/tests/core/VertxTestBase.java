package org.vertx.java.tests.core;

import org.junit.After;
import org.junit.Before;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.file.impl.ClasspathPathResolver;

import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxTestBase extends AsyncTestBase {

  protected Vertx vertx;

  @Before
  public void beforeVertxTestBase() throws Exception {
    vertx = VertxFactory.newVertx();
  }

  @After
  public void afterVertxTestBase() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    vertx.close(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
  }

  protected String findFileOnClasspath(String fileName) {
    URL url = getClass().getClassLoader().getResource(fileName);
    if (url == null) {
      throw new IllegalArgumentException("Cannot find file " + fileName + " on classpath");
    }
    Path path = ClasspathPathResolver.urlToPath(url).toAbsolutePath();
    return path.toString();
  }

  protected <T> Handler<AsyncResult<T>> onSuccess(Consumer<T> consumer) {
    return result -> {
      if (result.failed()) {
        fail(result.cause().getMessage());
      } else {
        consumer.accept(result.result());
      }
    };
  }

  protected <T> Handler<AsyncResult<T>> onFailure(Consumer<T> consumer) {
    return result -> {
      assertFalse(result.succeeded());
      consumer.accept(result.result());
    };
  }

  protected void awaitLatch(CountDownLatch latch) throws InterruptedException {
    assertTrue(latch.await(10, TimeUnit.SECONDS));
  }

  protected void waitUntil(BooleanSupplier supplier) throws Exception {
    long start = System.currentTimeMillis();
    long timeout = 10000;
    while (true) {
      if (supplier.getAsBoolean()) {
        break;
      }
      Thread.sleep(10);
      long now = System.currentTimeMillis();
      if (now - start > timeout) {
        throw new IllegalStateException("Timed out");
      }
    }
  }

}
