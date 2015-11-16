package io.vertx.test.core;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.MessageProducer;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusFlowControlTest extends VertxTestBase {

  protected EventBus eb;

  @Test
  public void testFlowControl() {

    MessageProducer<String> prod = eb.sender("some-address");
    int numBatches = 1000;
    int wqms = 2000;
    prod.setWriteQueueMaxSize(wqms);

    MessageConsumer<String> consumer = eb.consumer("some-address");
    AtomicInteger cnt = new AtomicInteger();
    consumer.handler(msg -> {
      int c = cnt.incrementAndGet();
      if (c == numBatches * wqms) {
        testComplete();
      }
    });

    sendBatch(prod, wqms, numBatches, 0);
    await();
  }

  private void sendBatch(MessageProducer<String> prod, int batchSize, int numBatches, int batchNumber) {
    boolean drainHandlerSet = false;
    for (int i = 0; i < batchSize; i++) {
      prod.send("message-" + i);
      if (prod.writeQueueFull() && !drainHandlerSet) {
        prod.drainHandler(v -> {
          if (batchNumber < numBatches - 1) {
            sendBatch(prod, batchSize, numBatches, batchNumber + 1);
          }
        });
        drainHandlerSet = true;
      }
    }
  }

  @Test
  public void testFlowControlPauseConsumer() {

    MessageProducer<String> prod = eb.sender("some-address");
    int numBatches = 10;
    int wqms = 100;
    prod.setWriteQueueMaxSize(wqms);

    MessageConsumer<String> consumer = eb.consumer("some-address");
    AtomicInteger cnt = new AtomicInteger();
    AtomicBoolean paused = new AtomicBoolean();
    consumer.handler(msg -> {
      assertFalse(paused.get());
      int c = cnt.incrementAndGet();
      if (c == numBatches * wqms) {
        testComplete();
      }
      if (c % 100 == 0) {
        consumer.pause();
        paused.set(true);
        vertx.setTimer(100, tid -> {
          paused.set(false);
          consumer.resume();
        });
      }
    });

    sendBatch(prod, wqms, numBatches, 0);
    await();
  }

  @Test
  public void testFlowControlNoConsumer() {

    MessageProducer<String> prod = eb.sender("some-address");
    int wqms = 2000;
    prod.setWriteQueueMaxSize(wqms);

    boolean drainHandlerSet = false;
    for (int i = 0; i < wqms * 2; i++) {
      prod.send("message-" + i);
      if (prod.writeQueueFull() && !drainHandlerSet) {
        prod.drainHandler(v -> {
          fail("Should not be called");
        });
        drainHandlerSet = true;
      }
    }
    assertTrue(drainHandlerSet);
    vertx.setTimer(500, tid -> testComplete());
    await();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    eb = vertx.eventBus();
  }
}
