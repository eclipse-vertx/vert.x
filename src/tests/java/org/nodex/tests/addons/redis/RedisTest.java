/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.tests.addons.redis;

import org.nodex.java.addons.redis.RedisConnection;
import org.nodex.java.addons.redis.RedisException;
import org.nodex.java.addons.redis.RedisPool;
import org.nodex.java.core.*;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.composition.Composer;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedisTest extends TestBase {

  private static final Buffer key1 = Buffer.create("key1");
  private static final Buffer key2 = Buffer.create("key2");
  private static final Buffer key3 = Buffer.create("key3");
  private static final Buffer key4 = Buffer.create("key4");
  private static final Buffer key5 = Buffer.create("key5");

  private static final Buffer field1 = Buffer.create("field1");
  private static final Buffer field2 = Buffer.create("field2");
  private static final Buffer field3 = Buffer.create("field3");
  private static final Buffer field4 = Buffer.create("field4");
  private static final Buffer field5 = Buffer.create("field5");

  private static final Buffer channel1 = Buffer.create("channel1");
  private static final Buffer channel2 = Buffer.create("channel2");
  private static final Buffer channel3 = Buffer.create("channel3");


  private static final Buffer keyl1 = Buffer.create("keyl1");
  private static final Buffer keyl2 = Buffer.create("keyl2");

  private static final Buffer val1 = Buffer.create("val1");
  private static final Buffer val2 = Buffer.create("val2");
  private static final Buffer val3 = Buffer.create("val3");
  private static final Buffer val4 = Buffer.create("val4");
  private static final Buffer val5 = Buffer.create("val5");

  private static final Charset UTF8 = Charset.forName("UTF-8");

  private Composer comp;
  private RedisPool pool;
  private RedisConnection connection;
  private CountDownLatch latch;

  // Tests -----------------------------

  @Test
  public void testAll() throws Exception {
    try {
      Method[] methods = RedisTest.class.getMethods();
      for (Method method: methods) {
        if (method.getName().startsWith("do")) {
          setup();
          System.out.println("RUNNING TEST: " + method.getName());
          runTest(method);
          System.out.println("TEST COMPLETE: " + method.getName());
          teardown();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      azzert(false, e.getMessage());
    }
  }

  public void doTestAppend() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.append(key1, val2));
    assertKey(key1, Buffer.create(0).appendBuffer(val1).appendBuffer(val2));
  }

  public void doTestAuth() {
    Future<Void> res = comp.series(connection.auth(Buffer.create("whatever")));
    assertResult(res, null);
  }

  public void doTestBgWriteAOF() {
    Future<Void> res = comp.series(connection.bgRewriteAOF());
    assertResult(res, null);
  }

  public void doBGSave() {
    Future<Void> res = comp.series(connection.bgSave());
    assertResult(res, null);
  }

  public void doBLPop() {
    Future<Integer> res1 = comp.series(connection.lPush(keyl1, val1));
    Future<Integer> res2 = comp.series(connection.lPush(keyl2, val2));
    Future<Buffer[]> res = comp.series(connection.bLPop(10, keyl1, keyl2));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res, new Buffer[] { keyl1, val1});
  }

  public void doBRPop() {
    Future<Integer> res1 = comp.series(connection.lPush(keyl1, val1));
    Future<Integer> res2 = comp.series(connection.lPush(keyl2, val2));
    Future<Buffer[]> res = comp.series(connection.bRPop(10, keyl1, keyl2));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res, new Buffer[] { keyl1, val1});
  }

  public void doBRPopLPush() {
    Future<Integer> res1 = comp.series(connection.lPush(keyl1, val1));
    Future<Buffer> res = comp.series(connection.bRPopLPush(keyl1, keyl2, 10));
    assertResult(res1, 1);
    assertResult(res, val1);
  }

//  None of the config commands seem to be recognised by Redis
//
//  public void doConfigGet() {
//    final Future<Buffer> res = comp.series(connection.configGet("*"));
//    assertResult(res, "foo");
//  }
//
//  public void doConfigSet() {
//    Future<Void> res = comp.series(connection.configSet(key1, val1));
//    assertResult(res, null);
//  }
//
//  public void doConfigResetStat() {
//    Future<Void> res = comp.series(connection.configResetStat());
//    assertResult(res, null);
//  }

  public void doDBSize() {
    int num = 10;
    for (int i = 0; i < num; i ++) {
      comp.parallel(connection.set(Buffer.create("key" + i), val1));
    }
    Future<Integer> res = comp.series(connection.dbSize());
    assertResult(res, num);
  }

  public void doDecr() {
    int num = 10;
    comp.series(connection.set(key1, Buffer.create(String.valueOf(num))));
    Future<Integer> res = comp.series(connection.decr(key1));
    assertResult(res, num - 1);
  }

  public void doDecrBy() {
    int num = 10;
    int decr = 4;
    comp.series(connection.set(key1, Buffer.create(String.valueOf(num))));
    Future<Integer> res = comp.series(connection.decrBy(key1, decr));
    assertResult(res, num - decr);
  }

  public void doDel() {
    comp.parallel(connection.set(key1, val1));
    comp.parallel(connection.set(key2, val2));
    comp.parallel(connection.set(key3, val3));
    Future<Integer> res = comp.series(connection.dbSize());
    assertResult(res, 3);
    comp.series(connection.del(key1, key2));
    res = comp.series(connection.dbSize());
    assertResult(res, 1);
  }

  public void doMultiDiscard() {
    Future<Void> res1 = comp.series(connection.multi());
    Future<Void> res2 = comp.series(connection.discard());
    assertResult(res1, null);
    assertResult(res2, null);
  }

  public void doEcho() {
    Buffer msg = Buffer.create("foo");
    Future<Buffer> res = comp.series(connection.echo(msg));
    assertResult(res, msg);
  }

  public void doTransactionExec() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Integer> res2 = comp.series(connection.incr(key1));
    Future<Integer> res3 = comp.parallel(connection.incr(key1));
    Future<Void> res4 = comp.parallel(connection.exec());
    assertResult(res4, null);
    assertResult(res2,  1);
    assertResult(res3,  2);
  }

  public void doTransactionWithMultiBulkExec() {
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Boolean> res2 = comp.parallel(connection.hSet(key1, field1, val1));
    Future<Boolean> res3 = comp.parallel(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res4 = comp.parallel(connection.hGetAll(key1));
    Future<Void> res5 = comp.parallel(connection.exec());
    assertResult(res5, null);
    assertResult(res2,  true);
    assertResult(res3,  true);
    assertResult(res4,  new Buffer[] {field1, val1, field2, val2});
  }

  public void doTransactionMixed() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Integer> res2 = comp.series(connection.incr(key1));
    Future<Integer> res3 = comp.parallel(connection.incr(key1));
    Future<Void> res4 = comp.parallel(connection.exec());
    assertResult(res4, null);
    assertResult(res2,  1);
    assertResult(res3,  2);
    Future<Integer> res5 = comp.series(connection.incr(key1));
    assertResult(res5, 3);
    Future<Void> res6 = comp.series(connection.multi());
    assertResult(res6, null);
    Future<Integer> res7 = comp.series(connection.incr(key1));
    Future<Integer> res8 = comp.parallel(connection.incr(key1));
    Future<Void> res9 = comp.parallel(connection.exec());
    assertResult(res9, null);
    assertResult(res7,  4);
    assertResult(res8,  5);
  }

  public void doTransactionDiscard() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Integer> res2 = comp.series(connection.incr(key1));
    Future<Integer> res3 = comp.parallel(connection.incr(key1));
    Future<Void> res4 = comp.parallel(connection.discard());
    assertException(res2, "Transaction discarded");
    assertException(res3, "Transaction discarded");
    assertResult(res4, null);
  }

  public void doEmptyTransactionExec() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Void> res2 = comp.parallel(connection.exec());
    assertResult(res2, null);
  }

  public void doEmptyTransactionDiscard() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Void> res2 = comp.parallel(connection.discard());
    assertResult(res2, null);
  }

  public void doPubSub() {
    RedisPool pool = new RedisPool().setMaxPoolSize(3);
    RedisConnection conn1 = pool.connection();
    final RedisConnection conn2 = pool.connection();
    final RedisConnection conn3 = pool.connection();

    Future<Void> res1 = comp.series(conn2.subscribe(channel1));
    assertResult(res1, null);

    Future<Void> res2 = comp.series(conn3.subscribe(channel1));
    assertResult(res2, null);

    final DeferredAction<Buffer> def1 = new DeferredAction<Buffer>() {
      public void run() {
        conn2.subscriberHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            setResult(buffer);
          }
        });
      }
    };

    final DeferredAction<Buffer> def2 = new DeferredAction<Buffer>() {
      public void run() {
        conn3.subscriberHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            setResult(buffer);
          }
        });
      }
    };

    Future<Buffer> res3 = comp.series(def1);
    Future<Buffer> res4 = comp.parallel(def2);

    Future<Integer> res5 = comp.parallel(conn1.publish(channel1, val1));

    assertResult(res5, 2);

    assertResult(res3, val1);
    assertResult(res4, val1);

    comp.series(conn1.closeDeferred());
    comp.parallel(conn2.closeDeferred());
    comp.parallel(conn3.closeDeferred());
  }

  public void doPubSubOnlySubscribe() {
    RedisPool pool = new RedisPool().setMaxPoolSize(3);
    RedisConnection conn1 = pool.connection();

    Future<Void> res1 = comp.series(conn1.subscribe(channel1));
    assertResult(res1, null);

    Future<Void> res2 = comp.series(conn1.set(key1, val1));
    assertException(res2, "It is not legal to send commands other than SUBSCRIBE and UNSUBSCRIBE when in subscribe mode");

    Future<Void> res3 = comp.series(conn1.unsubscribe(channel1));
    assertResult(res3, null);

    Future<Void> res4 = comp.series(conn1.set(key1, val1));
    assertResult(res4, null);

    comp.series(conn1.closeDeferred());
  }

  public void doPubSubSubscribeMultiple() {
    RedisPool pool = new RedisPool().setMaxPoolSize(3);
    RedisConnection conn1 = pool.connection();

    Future<Void> res1 = comp.series(conn1.subscribe(channel1));
    assertResult(res1, null);

    Future<Void> res2 = comp.series(conn1.subscribe(channel2));
    assertResult(res2, null);

    Future<Void> res3 = comp.series(conn1.set(key1, val1));
    assertException(res3, "It is not legal to send commands other than SUBSCRIBE and UNSUBSCRIBE when in subscribe mode");

    Future<Void> res4 = comp.series(conn1.unsubscribe(channel1));
    assertResult(res4, null);

    Future<Void> res5 = comp.series(conn1.unsubscribe(channel2));
    assertResult(res5, null);

    Future<Void> res6 = comp.series(conn1.set(key1, val1));
    assertResult(res6, null);

    comp.series(conn1.closeDeferred());
  }

  public void doPooling1() {
    RedisPool pool = new RedisPool().setMaxPoolSize(10);

    int numConnections = 100;

    final Set<Future<Integer>> futures = new HashSet<>();
    for (int i= 0; i < numConnections; i++) {
      final RedisConnection conn = pool.connection();
      Future<Integer> res = comp.parallel(conn.incr(key1));
      comp.parallel(conn.closeDeferred());
      futures.add(res);
    }

    final int expected = (numConnections + 1) * numConnections / 2;

    comp.series(new SimpleAction() {
      public void act() {
        int tot = 0;
        for (Future<Integer> future: futures) {
          tot += future.result();
        }

        azzert(expected == tot);
      }
    });
  }

  public void doPooling2() {
    RedisPool pool = new RedisPool().setMaxPoolSize(10);

    int numConnections = 100;

    for (int i= 0; i < numConnections; i++) {
      final RedisConnection conn = pool.connection();
      comp.parallel(conn.closeDeferred());
    }
  }

  // Private -------------------------

  private void setup() {
    comp = new Composer();
    latch = new CountDownLatch(1);
  }

  private void teardown() {
    pool = null;
    comp = null;
    latch = null;
  }

  private void clearKeys() {
    comp.series(connection.flushDB());
    comp.series(connection.echo(val1)); // This is just a filler and makes sure the flush is complete before the test is run
  }

  private void assertException(final Future<?> future, final String error) {
    comp.series(new TestAction() {
      protected void doAct() {
        azzert(future.failed());
        azzert(future.exception() instanceof RedisException);
        azzert(error.equals(future.exception().getMessage()), "Expected:" + error + " Actual:" + future.exception().getMessage());
      }
    });
  }

  private void assertResult(final Future<?> future, final Object value) {
    comp.series(new TestAction() {
      protected void doAct() {
        if (!future.succeeded()) {
          future.exception().printStackTrace();
        }
        azzert(future.succeeded());
        Object res = future.result();
        if (res == null) {
          azzert(value == null);
        } else {
          if (res instanceof Buffer) {
            azzert(Utils.buffersEqual((Buffer)value, (Buffer)res));
          } else if (res instanceof Buffer[]) {
            Buffer[] mb = (Buffer[])res;
            Buffer[] expected = (Buffer[])value;
            azzert(mb.length == expected.length);
            for (int i = 0; i < expected.length; i++) {
              azzert(Utils.buffersEqual(expected[i], mb[i]));
            }
          } else {
            azzert(value.equals(future.result()), "Expected: " + value + " Actual: " + future.result());
          }
        }
      }
    });
  }

  private void assertKey(Buffer key, final Buffer value) {
    final Future<Buffer> res = comp.series(connection.get(key));
    comp.series(new TestAction() {
      protected void doAct() {
        azzert(Utils.buffersEqual(value, res.result()));
      }
    });
  }

  private void done() {
    comp.series(connection.closeDeferred());
    comp.series(new TestAction() {
      protected void doAct() {
        pool.close();
        latch.countDown();
      }
    });
    comp.execute();
  }

  private void runTest(final Method method) throws Exception {
    Nodex.instance.go(new Runnable() {
      public void run() {
        try {
          pool = new RedisPool();
          connection = pool.connection();
          clearKeys();
          method.invoke(RedisTest.this);
          done();
        } catch (Exception e) {
          e.printStackTrace();
          azzert(false, "Exception thrown " + e.getMessage());
        }
      }
    });
    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

  private abstract class TestAction extends SimpleAction {
    public void act() {
      try {
        doAct();
      } catch (Exception e) {
        e.printStackTrace();
        azzert(false, e.getMessage());
      }
    }

    protected abstract void doAct();
  }
}
