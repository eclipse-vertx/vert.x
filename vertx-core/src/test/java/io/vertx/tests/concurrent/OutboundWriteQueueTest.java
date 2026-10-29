package io.vertx.tests.concurrent;

import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.concurrent.OutboundWriteQueue;
import io.vertx.core.internal.concurrent.WriteOp;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.VertxRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

@RunWith(VertxRunner.class)
public class OutboundWriteQueueTest {

  static class Element extends WriteOp {

    boolean written;
    volatile int drained;
    volatile int disposed;
    Throwable failure;
    Runnable listener;

    @Override
    public void cancel(Throwable cause) {
      failure = cause;
    }

    int depth() {
      return WriteOp.depthOf(this);
    }

    int seq() {
      return WriteOp.seqOf(this);
    }

    @Override
    protected void handleDrain() {
      drained++;
      Runnable l = listener;
      if (l != null) {
        l.run();
      }
    }

    @Override
    protected void handleDispose() {
      disposed++;
    }
  }

  private boolean writable;
  private Vertx vertx;
  private EventExecutor eventLoop;

  abstract static class Write extends OutboundWriteQueue<Element> {
    public Write(EventExecutor consumer) {
      super(consumer);
    }

    public Write(EventExecutor consumer, int lowWaterMark, int highWaterMark) {
      super(consumer, lowWaterMark, highWaterMark);
    }

    void write(Element element) {
      add(element);
      element.drainHandlerSet();
      element.disposeHandlerSet();
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
      public boolean handleWrite(Element msg) {
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
      while (elt.isWritable());
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
      public boolean handleWrite(Element msg) {
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
      while (elt.isWritable());
      elt.listener = () -> {
        writable = false;
        Thread thread = new Thread(() -> {
          while (true) {
            Element elt2 = new Element();
            owq.write(elt2);
            if (!elt2.isWritable()) {
              elt2.listener = () -> {
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
      public boolean handleWrite(Element msg) {
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
      while (elt.isWritable());
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

  @Test
  public void testCollectedSurvivesRefusedDrain(Checkpoint checkpoint) {
    AtomicInteger accept = new AtomicInteger();
    Write owq = new Write(eventLoop, 2, 4) {
      @Override
      public boolean handleWrite(Element msg) {
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
      do {
        elt = new Element();
        owq.write(elt);
      }
      while (elt.isWritable());
      Element collected = elt;
      Element refused = new Element();
      owq.write(refused);
      accept.set(4);
      owq.tryDrain();
      Assert.assertTrue(collected.written);
      Assert.assertFalse(refused.written);
      Assert.assertEquals(0, collected.drained);
      accept.set(1);
      owq.tryDrain();
      Assert.assertTrue(refused.written);
      Assert.assertEquals(1, collected.drained);
      Assert.assertEquals(1, refused.drained);
      checkpoint.succeed();
    });
  }

  @Test
  public void testWitnessPromotesHeldBatch(Checkpoint checkpoint) {
    AtomicInteger accept = new AtomicInteger();
    Write owq = new Write(eventLoop, 3, 4) {
      @Override
      public boolean handleWrite(Element msg) {
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
      do {
        elt = new Element();
        owq.write(elt);
      }
      while (elt.isWritable());
      Element collected1 = elt;
      Element collected2 = new Element();
      owq.write(collected2);
      accept.set(4);
      owq.tryDrain();
      Assert.assertTrue(collected1.written);
      Assert.assertFalse(collected2.written);
      Assert.assertEquals(0, collected1.drained);
      Element witness = new Element();
      Thread thread = new Thread(() -> owq.write(witness));
      thread.start();
      try {
        thread.join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      Assert.assertTrue(witness.depth() < 3);
      accept.set(1);
      owq.tryDrain();
      Assert.assertTrue(collected2.written);
      Assert.assertFalse(witness.written);
      Assert.assertEquals(1, collected1.drained);
      Assert.assertEquals(1, collected2.drained);
      accept.set(1);
      owq.tryDrain();
      Assert.assertTrue(witness.written);
      Assert.assertEquals(1, collected1.drained);
      Assert.assertEquals(1, collected2.drained);
      Assert.assertEquals(0, witness.drained);
      checkpoint.succeed();
    });
  }

  @Test
  public void testWriteFailureCancelsMessage(Checkpoint checkpoint) {
    RuntimeException failure = new RuntimeException();
    Element boom = new Element();
    Write owq = new Write(eventLoop) {
      @Override
      public boolean handleWrite(Element msg) {
        if (msg == boom) {
          throw failure;
        }
        msg.written = true;
        return true;
      }
    };
    eventLoop.execute(() -> {
      owq.write(boom);
      Assert.assertSame(failure, boom.failure);
      Assert.assertFalse(boom.written);
      Assert.assertEquals(0, boom.drained);
      Element next = new Element();
      owq.write(next);
      Assert.assertTrue(next.written);
      checkpoint.succeed();
    });
  }

  @Test
  public void testWriteFailureCountsAsWritten(Checkpoint checkpoint) {
    RuntimeException failure = new RuntimeException();
    Element boom = new Element();
    AtomicInteger accept = new AtomicInteger();
    Write owq = new Write(eventLoop, 2, 4) {
      @Override
      public boolean handleWrite(Element msg) {
        if (msg == boom) {
          throw failure;
        }
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
      do {
        elt = new Element();
        owq.write(elt);
      }
      while (elt.isWritable());
      Element collected = elt;
      owq.write(boom);
      accept.set(4);
      owq.tryDrain();
      Assert.assertTrue(collected.written);
      Assert.assertSame(failure, boom.failure);
      Assert.assertFalse(boom.written);
      Assert.assertEquals(1, boom.drained);
      Assert.assertEquals(1, collected.drained);
      checkpoint.succeed();
    });
  }

  @Test
  public void testWriteFailureAboveHighWaterMark(Checkpoint checkpoint) {
    RuntimeException failure = new RuntimeException();
    Element boom = new Element();
    AtomicInteger accept = new AtomicInteger();
    Write owq = new Write(eventLoop, 2, 4) {
      @Override
      public boolean handleWrite(Element msg) {
        if (msg == boom) {
          throw failure;
        }
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
      Element m1 = new Element();
      owq.write(m1);
      Element m2 = new Element();
      owq.write(m2);
      Element m3 = new Element();
      owq.write(m3);
      owq.write(boom);
      Assert.assertFalse(boom.isWritable());
      accept.set(3);
      owq.tryDrain();
      Assert.assertTrue(m3.written);
      Assert.assertSame(failure, boom.failure);
      Assert.assertFalse(boom.written);
      Assert.assertEquals(1, boom.drained);
      checkpoint.succeed();
    });
  }

  @Test
  public void testCloseFiresHeldBatch(Checkpoint checkpoint) {
    AtomicInteger accept = new AtomicInteger();
    Write owq = new Write(eventLoop, 2, 4) {
      @Override
      public boolean handleWrite(Element msg) {
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
      do {
        elt = new Element();
        owq.write(elt);
      }
      while (elt.isWritable());
      Element collected = elt;
      Element refused = new Element();
      owq.write(refused);
      accept.set(4);
      owq.tryDrain();
      Assert.assertTrue(collected.written);
      Assert.assertEquals(0, collected.drained);
      owq.close();
      Assert.assertEquals(0, collected.drained);
      Assert.assertEquals(1, collected.disposed);
      Assert.assertFalse(refused.written);
      Assert.assertEquals(1, refused.disposed);
      checkpoint.succeed();
    });
  }

  @Test
  public void testCloseDuringDrainFiresHeldBatch(Checkpoint checkpoint) {
    AtomicInteger accept = new AtomicInteger();
    Element[] closer = new Element[1];
    Write[] ref = new Write[1];
    Write owq = new Write(eventLoop, 2, 4) {
      @Override
      public boolean handleWrite(Element msg) {
        int val = accept.get();
        if (val > 0) {
          accept.set(--val);
          msg.written = true;
          if (msg == closer[0]) {
            ref[0].close();
          }
          return true;
        } else {
          return false;
        }
      }
    };
    ref[0] = owq;
    eventLoop.execute(() -> {
      Element elt;
      do {
        elt = new Element();
        owq.write(elt);
      }
      while (elt.isWritable());
      Element collected = elt;
      closer[0] = collected;
      Element refused = new Element();
      owq.write(refused);
      accept.set(4);
      owq.tryDrain();
      Assert.assertTrue(collected.written);
      Assert.assertEquals(0, collected.drained);
      Assert.assertEquals(1, collected.disposed);
      Assert.assertFalse(refused.written);
      checkpoint.succeed();
    });
  }

  @Test
  public void testConcurrentInversion(Checkpoint checkpoint) {
    int numThreads = 8;
    int numAdds = 1000;
    AtomicInteger written = new AtomicInteger();
    Write owq = new Write(eventLoop, 2, 3) {
      int attempts;
      @Override
      public boolean handleWrite(Element msg) {
        if (++attempts % 7 == 0) {
          return false;
        }
        msg.written = true;
        written.incrementAndGet();
        return true;
      }
      @Override
      public void stamp(Element element, int depth, int seq) {
        super.stamp(element, depth, seq);
        // Deschedule some publications to invert the poll order against the stamps
        if ((seq & 255) == 0) {
          LockSupport.parkNanos(1_000_000);
        } else if ((seq & 7) == 0) {
          LockSupport.parkNanos(1_000);
        }
      }
    };
    Element[][] elements = new Element[numThreads][numAdds];
    for (int t = 0;t < numThreads;t++) {
      Element[] slice = elements[t];
      new Thread(() -> {
        for (int i = 0;i < numAdds;i++) {
          Element elt = new Element();
          slice[i] = elt;
          owq.write(elt);
        }
      }).start();
    }
    eventLoop.execute(new Runnable() {
      @Override
      public void run() {
        owq.tryDrain();
        if (written.get() < numThreads * numAdds) {
          eventLoop.execute(this);
        } else {
          int collected = 0;
          for (Element[] slice : elements) {
            for (Element elt : slice) {
              Assert.assertTrue(elt.written);
              Assert.assertEquals(elt.depth() >= 3 ? 1 : 0, elt.drained);
              if (elt.depth() >= 3) {
                collected++;
              }
            }
          }
          Assert.assertTrue(collected > 0);
          checkpoint.succeed();
        }
      }
    });
  }

  @Test
  public void testStaleWitnessIsIgnored(Checkpoint checkpoint) {
    AtomicInteger accept = new AtomicInteger();
    Element stale = new Element();
    CountDownLatch parked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Write owq = new Write(eventLoop, 3, 4) {
      @Override
      public boolean handleWrite(Element msg) {
        int val = accept.get();
        if (val > 0) {
          accept.set(--val);
          msg.written = true;
          return true;
        } else {
          return false;
        }
      }
      @Override
      public void stamp(Element element, int depth, int seq) {
        super.stamp(element, depth, seq);
        if (element == stale) {
          parked.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      }
    };
    eventLoop.execute(() -> {
      Element first = new Element();
      owq.write(first);
      Thread thread = new Thread(() -> owq.write(stale));
      thread.start();
      try {
        parked.await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      Element elt = null;
      Element[] collected = new Element[3];
      for (int i = 0;i < 4;i++) {
        elt = new Element();
        owq.write(elt);
        if (i > 0) {
          collected[i - 1] = elt;
        }
      }
      release.countDown();
      try {
        thread.join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      Assert.assertTrue(stale.depth() < 3);
      Assert.assertTrue(stale.seq() < collected[2].seq());
      accept.set(5);
      owq.tryDrain();
      Assert.assertFalse(stale.written);
      for (Element c : collected) {
        Assert.assertTrue(c.written);
        Assert.assertEquals(0, c.drained);
      }
      accept.set(1);
      owq.tryDrain();
      Assert.assertTrue(stale.written);
      for (Element c : collected) {
        Assert.assertEquals(1, c.drained);
      }
      Assert.assertEquals(0, stale.drained);
      checkpoint.succeed();
    });
  }
}
