package io.vertx.test.core;

import io.vertx.core.*;
import org.junit.Test;

/**
 * please note that this test class does not assert anything about the log output (this would require a kind of log
 * mock), it just runs the different methods to get coverage
 */
public class BlockedThreadCheckerTest extends VertxTestBase {

  @Test
  public void testBlockCheckDefault() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(6000);
        testComplete();
      }
    };
    vertx.deployVerticle(verticle);
    await();
  }

  @Test
  public void testBlockCheckExceptionTimeLimit() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(2000);
        testComplete();
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxEventLoopExecuteTime(1000000000);
    vertxOptions.setWarningExceptionTime(1000000000);
    Vertx newVertx = vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
  }

  @Test
  public void testBlockCheckWorker() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(2000);
        testComplete();
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(1000000000);
    vertxOptions.setWarningExceptionTime(1000000000);
    Vertx newVertx = vertx(vertxOptions);
    DeploymentOptions depolymentOptions = new DeploymentOptions();
    depolymentOptions.setWorker(true);
    newVertx.deployVerticle(verticle, depolymentOptions);
    await();
  }
}
