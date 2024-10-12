package io.vertx.tests.deployment;

import io.netty.channel.EventLoop;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.internal.ContextInternal;
import org.junit.Test;

/**
 * @author <a href="https://dreamlike-ocean.github.io/blog/">dremalike</a>
 */
public class CurrentEventLoopDeploymentTest extends AbstractVerticleTest {

  @Test
  public void testDeploy() throws InterruptedException {
    waitFor(1);
    ContextInternal currentContext = (ContextInternal) vertx.getOrCreateContext();
    EventLoop targetEventLoop = currentContext.nettyEventLoop();
    vertx.deployVerticle(new AbstractVerticle() {
        @Override
        public void start() {
          EventLoop currentEventLoop = ((ContextInternal) context).nettyEventLoop();
          assertEquals(targetEventLoop, currentEventLoop);
          assertNotSame(currentContext, context);
        }

      }, new DeploymentOptions().setReuseCurrentEventLoop(true))
      .onSuccess(this::assertNotNull)
      .onFailure(this::fail)
      .onComplete(s -> testComplete());

    await();
  }

  @Test
  public void testExecuteBlocking() {
    waitFor(1);
    vertx.deployVerticle(new AbstractVerticle() {
        @Override
        public void start(Promise<Void> startPromise) throws Exception {
          Thread eventLoopThread = Thread.currentThread();
          vertx.executeBlocking(() -> {
            assertNotSame(eventLoopThread, Thread.currentThread());
            startPromise.complete();
            return null;
          });
        }
      }, new DeploymentOptions().setReuseCurrentEventLoop(true))
      .onSuccess(this::assertNotNull)
      .onFailure(this::fail)
      .onComplete(s -> testComplete());
    await();
  }


}
