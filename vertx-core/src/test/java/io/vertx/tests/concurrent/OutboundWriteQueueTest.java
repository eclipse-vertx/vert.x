package io.vertx.tests.concurrent;

import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.concurrent.OutboundWriteQueue;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.VertxRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(VertxRunner.class)
public class OutboundWriteQueueTest {

  static class Element {

    boolean writable;
    boolean written;
    int drained;
    Runnable listener;

  }

  private boolean writable;
  private Vertx vertx;
  private EventExecutor eventLoop;

  abstract static class Write extends OutboundWriteQueue<Element> {
    public Write(EventExecutor consumer) {
      super(consumer);
    }

    void write(Element element) {
      element.writable = add(element);
    }

    @Override
    protected void fireDrains(Deque<Element> toDrain) {
      for (Element element : toDrain) {
        element.drained++;
        Runnable listener = element.listener;
        if (listener != null) {
          listener.run();
        }
      }
    }
  }

  @Before
  public void setUp(Vertx vertx) throws Exception {
    this.vertx = vertx;
    this.eventLoop = ((ContextInternal)vertx.getOrCreateContext()).executor();
  }

  @Test
  public void testDrain(Checkpoint checkpoint1) {
    writable = false;
    Write owq = new Write(eventLoop) {
      @Override
      public boolean writeImpl(Element msg) {
        if (writable) {
          msg.written = true;
          return true;
        } else {
          return false;
        }
      }
    };
    eventLoop.execute(() -> {
      Element elt;
      do {
        elt = new Element();
        owq.write(elt);
      }
      while (elt.writable);
      Element last = elt;
      last.listener = () -> {
        if (last.written) {
          checkpoint1.succeed();
        }
      };
      writable = true;
      owq.tryDrain();
    });
  }

  @Test
  public void testBug(Checkpoint checkpoint1) {
    writable = false;
    AtomicBoolean drained = new AtomicBoolean();
    Write owq = new Write(eventLoop) {
      @Override
      public boolean writeImpl(Element msg) {
        if (writable) {
          msg.written = true;
          return true;
        } else {
          return false;
        }
      }
      @Override
      protected void fireDrains(Deque<Element> toDrain) {
        writable = false;
        Thread thread = new Thread(() -> {
          while (true) {
            Element elt = new Element();
            write(elt);
            if (!elt.writable) {
              elt.listener = () -> {
                drained.set(true);
              };
              break;
            }
          }
        });
        thread.start();
        try {
          thread.join();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        super.fireDrains(toDrain);
      }
    };
    eventLoop.execute(() -> {
      Element elt;
      do {
        elt = new Element();
        owq.write(elt);
      }
      while (elt.writable);
      elt.listener = () -> {
        eventLoop.execute(() -> {
          if (!drained.get()) {
            checkpoint1.succeed();
          }
        });
      };
      writable = true;
      owq.tryDrain();
    });
  }

  @Test
  public void testBouncedWritableDoesNotTriggerDrainHandler(Checkpoint checkpoint) {
    writable = false;
    AtomicInteger accept = new AtomicInteger();
    Write owq = new Write(eventLoop) {
      @Override
      public boolean writeImpl(Element msg) {
        int val = accept.get();
        if (val > 0) {
          accept.set(--val);
          msg.written = true;
          return true;
        } else {
          return false;
        }
      }
    };
    eventLoop.execute(() -> {
      Element elt;
      int written = 0;
      do {
        elt = new Element();
        owq.write(elt);
        written++;
      }
      while (elt.writable);
      accept.set(written - 1);
      owq.tryDrain();
      Assert.assertEquals(0, elt.drained);
      owq.tryDrain();
      Assert.assertEquals(0, elt.drained);
      accept.set(1);
      owq.tryDrain();
      Assert.assertEquals(1, elt.drained);
      checkpoint.succeed();
    });
  }
}
