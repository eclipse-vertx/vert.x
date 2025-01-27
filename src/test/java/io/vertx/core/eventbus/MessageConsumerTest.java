package io.vertx.core.eventbus;

import io.vertx.core.*;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class MessageConsumerTest extends VertxTestBase {


  @Test
  public void testMessageConsumptionStayOnWorkerThreadAfterResume() throws Exception {
    TestVerticle verticle = new TestVerticle(2);
    Future<String> deployVerticle = vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));

    CountDownLatch startLatch = new CountDownLatch(1);
    deployVerticle.onComplete(onSuccess(cf -> startLatch.countDown()));
    awaitLatch(startLatch);

    vertx.eventBus().send("testAddress", "message1");
    vertx.eventBus().send("testAddress", "message2");

    awaitLatch(verticle.msgLatch);

    assertEquals(2, verticle.messageArrivedOnWorkerThread.size());
    assertTrue("message1 should be processed on worker thread", verticle.messageArrivedOnWorkerThread.get("message1"));
    assertTrue("message2 should be processed on worker thread", verticle.messageArrivedOnWorkerThread.get("message2"));
  }


  private static class TestVerticle extends AbstractVerticle {

    private final CountDownLatch msgLatch;

    private final Map<String, Boolean> messageArrivedOnWorkerThread = new HashMap<>();

    private TestVerticle(Integer numberOfExpectedMessages) {
      this.msgLatch = new CountDownLatch(numberOfExpectedMessages);
    }

    @Override
    public void start() {
      MessageConsumer<String> consumer = vertx.eventBus().localConsumer("testAddress");
      handleMessages(consumer);
    }

    private void handleMessages(MessageConsumer<String> consumer) {
      consumer.handler(msg -> {
        consumer.pause();
        messageArrivedOnWorkerThread.putIfAbsent(msg.body(), Context.isOnWorkerThread());
        msgLatch.countDown();
        vertx.setTimer(20, id -> {
          consumer.resume();
        });
      });
    }
  }
}
