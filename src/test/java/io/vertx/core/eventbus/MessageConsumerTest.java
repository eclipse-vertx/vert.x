package io.vertx.core.eventbus;

import io.vertx.core.*;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageConsumerTest extends VertxTestBase {


  @Test
  public void testMessageConsumptionStayOnWorkerThreadAfterResumeAndOnlyDispatchOneMessageAtOneMoment() throws Exception {
    int numberOfExpectedMessages = 10;
    TestVerticle verticle = new TestVerticle(numberOfExpectedMessages);
    EchoVerticle echoVerticle = new EchoVerticle();
    Future<String> deployVerticle = vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    Future<String> deployEchoVerticle = vertx.deployVerticle(echoVerticle, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));

    CountDownLatch startLatch = new CountDownLatch(1);
    Future.all(deployVerticle, deployEchoVerticle)
      .onComplete(onSuccess(cf -> startLatch.countDown()));
    awaitLatch(startLatch);

    for (int i = 1; i <= numberOfExpectedMessages; i++) {
      vertx.eventBus().send("testAddress", "message" + i);
    }

    awaitLatch(verticle.msgLatch);

    assertEquals(numberOfExpectedMessages, verticle.messageArrivedOnWorkerThread.size());
    for (int i = 1; i <= numberOfExpectedMessages; i++) {
      assertTrue("message" + i + " should be processed on worker thread", verticle.messageArrivedOnWorkerThread.get("message" + i));
    }
  }


  private static class TestVerticle extends AbstractVerticle {

    private final CountDownLatch msgLatch;
    private final AtomicBoolean messageProcessingOngoing = new AtomicBoolean();

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
        if (messageProcessingOngoing.compareAndSet(false, true)) {
          msgLatch.countDown();
        } else {
          System.err.println("Received message while processing another message");
        }
        vertx.eventBus().request("echoAddress", 20)
          .onComplete(ar -> {
            messageProcessingOngoing.set(false);
            consumer.resume();
          });
      });
    }
  }

  private static class EchoVerticle extends AbstractVerticle {
    @Override
    public void start() {
      MessageConsumer<Integer> consumer = vertx.eventBus().localConsumer("echoAddress");
      handleMessages(consumer);
    }

    private void handleMessages(MessageConsumer<Integer> consumer) {
      consumer.handler(msg -> {
        vertx.setTimer(msg.body(), id -> {
          msg.reply(msg.body());
        });
      });
    }
  }

}
