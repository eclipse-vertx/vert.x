package io.vertx.tests.streams;

import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.concurrent.WriteOp;
import io.vertx.core.internal.streams.FailedWrite;
import io.vertx.core.internal.streams.OutboundWriteStream;
import io.vertx.core.internal.streams.WritableWrite;
import io.vertx.core.internal.streams.WritePromise;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.VertxRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxRunner.class)
public class WriteResultTest {

  private static class TestWrite extends WritePromise {

    int drains;
    int disposals;

    TestWrite(ContextInternal context) {
      super(context);
    }

    @Override
    public void write() {
    }

    TestWrite countDrains() {
      drainHandler(v -> drains++);
      return this;
    }

    TestWrite countDisposals() {
      disposeHandler(v -> disposals++);
      return this;
    }

  }

  private ContextInternal context;
  private EventExecutor executor;
  private volatile boolean accept;
  private OutboundWriteStream queue;

  @Before
  public void setUp(Vertx vertx) throws Exception {
    context = (ContextInternal) vertx.getOrCreateContext();
    executor = context.executor();
    queue = new OutboundWriteStream(executor, 1, 1) {
      @Override
      protected boolean write(WriteOp op) {
        if (accept) {
          op.write();
          return true;
        } else {
          return false;
        }
      }
    };
  }

  private TestWrite added() {
    TestWrite p = new TestWrite(context);
    queue.add(p);
    return p;
  }

  private TestWrite registered() {
    return added().countDrains().countDisposals();
  }

  @Test
  public void testDrainPendingRegistration(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      TestWrite p = registered();
      Assert.assertFalse(p.isWritable());
      accept = true;
      queue.tryDrain();
      context.runOnContext(v2 -> {
        Assert.assertEquals(1, p.drains);
        Assert.assertEquals(0, p.disposals);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testDrainLateRegistration(Checkpoint checkpoint) {
    accept = true;
    context.runOnContext(v -> {
      TestWrite p = registered();
      context.runOnContext(v2 -> {
        Assert.assertEquals(1, p.drains);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testDrainLateMismatchNeverCalled(Checkpoint checkpoint) {
    accept = true;
    context.runOnContext(v -> {
      TestWrite p = registered();
      context.runOnContext(v2 -> {
        Assert.assertEquals(0, p.disposals);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testDisposePendingRegistration(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      TestWrite p = registered();
      queue.close();
      context.runOnContext(v2 -> {
        Assert.assertEquals(1, p.disposals);
        Assert.assertEquals(0, p.drains);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testDisposeLateRegistration(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      queue.close();
      TestWrite p = registered();
      Assert.assertFalse(p.isWritable());
      context.runOnContext(v2 -> {
        Assert.assertEquals(1, p.disposals);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testDuplicateRegistrationFails(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      TestWrite p = registered();
      try {
        p.countDrains();
        Assert.fail();
      } catch (IllegalStateException expected) {
      }
      try {
        p.countDisposals();
        Assert.fail();
      } catch (IllegalStateException expected) {
      }
      checkpoint.succeed();
    });
  }

  @Test
  public void testRegistrationBeforeEnqueueFails(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      TestWrite p = new TestWrite(context);
      try {
        p.countDrains();
        Assert.fail();
      } catch (IllegalStateException expected) {
      }
      try {
        p.isWritable();
        Assert.fail();
      } catch (IllegalStateException expected) {
      }
      checkpoint.succeed();
    });
  }

  @Test
  public void testUnsetDrainHandler(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      TestWrite p = added().countDrains();
      p.drainHandler(null);
      accept = true;
      queue.tryDrain();
      context.runOnContext(v2 -> {
        Assert.assertEquals(0, p.drains);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testUnsetThenRegisterAgain(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      TestWrite p = added();
      p.drainHandler(x -> Assert.fail());
      p.drainHandler(null);
      p.countDrains();
      accept = true;
      queue.tryDrain();
      context.runOnContext(v2 -> {
        Assert.assertEquals(1, p.drains);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testUnsetAfterSignal(Checkpoint checkpoint) {
    accept = true;
    context.runOnContext(v -> {
      TestWrite p = added().countDrains();
      p.drainHandler(null);
      context.runOnContext(v2 -> {
        Assert.assertEquals(1, p.drains);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testSignalExactlyOnce(Checkpoint checkpoint) {
    accept = true;
    context.runOnContext(v -> {
      TestWrite p = registered();
      queue.close();
      context.runOnContext(v2 -> {
        Assert.assertEquals(1, p.drains);
        Assert.assertEquals(0, p.disposals);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testFailedWriteIsFailed(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      Throwable cause = new Throwable();
      FailedWrite w = new FailedWrite(context, cause);
      Assert.assertFalse(w.isWritable());
      Assert.assertTrue(w.failed());
      Assert.assertSame(cause, w.cause());
      FailedWrite w2 = new FailedWrite(context, "the-message");
      Assert.assertFalse(w2.isWritable());
      Assert.assertTrue(w2.failed());
      Assert.assertEquals("the-message", w2.cause().getMessage());
      checkpoint.succeed();
    });
  }

  @Test
  public void testFailedWriteDisposesRegistration(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      FailedWrite w = new FailedWrite(context, "failure");
      int[] disposals = new int[1];
      w.drainHandler(x -> Assert.fail());
      w.disposeHandler(x -> disposals[0]++);
      Assert.assertEquals(0, disposals[0]);
      context.runOnContext(v2 -> {
        Assert.assertEquals(1, disposals[0]);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testWritableWriteIsWritable(Checkpoint checkpoint) {
    context.runOnContext(v -> {
      WritableWrite w = new WritableWrite(context);
      Assert.assertTrue(w.isWritable());
      Assert.assertFalse(w.isComplete());
      w.drainHandler(x -> Assert.fail());
      w.disposeHandler(x -> Assert.fail());
      w.complete(null);
      Assert.assertTrue(w.succeeded());
      Assert.assertTrue(w.isWritable());
      context.runOnContext(v2 -> checkpoint.succeed());
    });
  }
}
