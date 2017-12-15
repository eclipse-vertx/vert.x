package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.junit.Rule;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.*;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
public class BlockedThreadCheckerTest extends VertxTestBase {

  @Rule
  public BlockedThreadWarning blockedThreadWarning = new BlockedThreadWarning();

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
    blockedThreadWarning.expectMessage("vert.x-eventloop-thread", VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME);
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
    long maxEventLoopExecuteTime = NANOSECONDS.convert(1, SECONDS);
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxEventLoopExecuteTime(maxEventLoopExecuteTime);
    vertxOptions.setWarningExceptionTime(maxEventLoopExecuteTime);
    Vertx newVertx = vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
    blockedThreadWarning.expectMessage("vert.x-eventloop-thread", maxEventLoopExecuteTime);
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
    long maxWorkerExecuteTime = NANOSECONDS.convert(1, SECONDS);
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertxOptions.setWarningExceptionTime(maxWorkerExecuteTime);
    Vertx newVertx = vertx(vertxOptions);
    DeploymentOptions deploymentOptions = new DeploymentOptions();
    deploymentOptions.setWorker(true);
    newVertx.deployVerticle(verticle, deploymentOptions);
    await();
    blockedThreadWarning.expectMessage("vert.x-worker-thread", maxWorkerExecuteTime);
  }

  @Test
  public void testBlockCheckExecuteBlocking() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        vertx.executeBlocking(fut -> {
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            fail();
          }
          testComplete();
        }, ar -> {});
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxWorkerExecuteTime = NANOSECONDS.convert(1, SECONDS);
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertxOptions.setWarningExceptionTime(maxWorkerExecuteTime);
    Vertx newVertx = vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
    blockedThreadWarning.expectMessage("vert.x-worker-thread", maxWorkerExecuteTime);
  }
}
