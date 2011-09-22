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

import org.nodex.java.addons.redis.RedisClient;
import org.nodex.java.addons.redis.RedisException;
import org.nodex.java.core.Future;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.SimpleAction;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.composition.Composer;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
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

  private static final Buffer keyl1 = Buffer.create("keyl1");
  private static final Buffer keyl2 = Buffer.create("keyl2");

  private static final Buffer val1 = Buffer.create("val1");
  private static final Buffer val2 = Buffer.create("val2");
  private static final Buffer val3 = Buffer.create("val3");
  private static final Buffer val4 = Buffer.create("val4");
  private static final Buffer val5 = Buffer.create("val5");

  private static final Charset UTF8 = Charset.forName("UTF-8");

  private Composer comp;
  private RedisClient client;
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
    }
  }

//  public void doTestAppend() {
//    comp.series(client.set(key1, val1));
//    comp.series(client.append(key1, val2));
//    assertKey(key1, Buffer.create(0).appendBuffer(val1).appendBuffer(val2));
//  }
//
//  public void doTestAuth() {
//    Future<Void> res = comp.series(client.auth(Buffer.create("whatever")));
//    assertResult(res, null);
//  }
//
//  public void doTestBgWriteAOF() {
//    Future<Void> res = comp.series(client.bgRewriteAOF());
//    assertResult(res, null);
//  }
//
//  public void doBGSave() {
//    Future<Void> res = comp.series(client.bgSave());
//    assertResult(res, null);
//  }
//
//  public void doBLPop() {
//    Future<Integer> res1 = comp.series(client.lPush(keyl1, val1));
//    Future<Integer> res2 = comp.series(client.lPush(keyl2, val2));
//    Future<Buffer[]> res = comp.series(client.bLPop(10, keyl1, keyl2));
//    assertResult(res1, 1);
//    assertResult(res2, 1);
//    assertResult(res, new Buffer[] { keyl1, val1});
//  }
//
//  public void doBRPop() {
//    Future<Integer> res1 = comp.series(client.lPush(keyl1, val1));
//    Future<Integer> res2 = comp.series(client.lPush(keyl2, val2));
//    Future<Buffer[]> res = comp.series(client.bRPop(10, keyl1, keyl2));
//    assertResult(res1, 1);
//    assertResult(res2, 1);
//    assertResult(res, new Buffer[] { keyl1, val1});
//  }
//
//  public void doBRPopLPush() {
//    Future<Integer> res1 = comp.series(client.lPush(keyl1, val1));
//    Future<Buffer> res = comp.series(client.bRPopLPush(keyl1, keyl2, 10));
//    assertResult(res1, 1);
//    assertResult(res, val1);
//  }

//  None of the config commands seem to be recognised by Redis
//
//  public void doConfigGet() {
//    final Future<Buffer> res = comp.series(client.configGet("*"));
//    assertResult(res, "foo");
//  }
//
//  public void doConfigSet() {
//    Future<Void> res = comp.series(client.configSet(key1, val1));
//    assertResult(res, null);
//  }
//
//  public void doConfigResetStat() {
//    Future<Void> res = comp.series(client.configResetStat());
//    assertResult(res, null);
//  }

//  public void doDBSize() {
//    int num = 10;
//    for (int i = 0; i < num; i ++) {
//      comp.parallel(client.set(Buffer.create("key" + i), val1));
//    }
//    Future<Integer> res = comp.series(client.dbSize());
//    assertResult(res, num);
//  }
//
//  public void doDecr() {
//    int num = 10;
//    comp.series(client.set(key1, Buffer.create(String.valueOf(num))));
//    Future<Integer> res = comp.series(client.decr(key1));
//    assertResult(res, num - 1);
//  }
//
//  public void doDecrBy() {
//    int num = 10;
//    int decr = 4;
//    comp.series(client.set(key1, Buffer.create(String.valueOf(num))));
//    Future<Integer> res = comp.series(client.decrBy(key1, decr));
//    assertResult(res, num - decr);
//  }
//
//  public void doDel() {
//    comp.parallel(client.set(key1, val1));
//    comp.parallel(client.set(key2, val2));
//    comp.parallel(client.set(key3, val3));
//    Future<Integer> res = comp.series(client.dbSize());
//    assertResult(res, 3);
//    comp.series(client.del(key1, key2));
//    res = comp.series(client.dbSize());
//    assertResult(res, 1);
//  }
//
//  public void doMultiDiscard() {
//    Future<Void> res1 = comp.series(client.multi());
//    Future<Void> res2 = comp.series(client.discard());
//    assertResult(res1, null);
//    assertResult(res2, null);
//  }
//
//  public void doEcho() {
//    Buffer msg = Buffer.create("foo");
//    Future<Buffer> res = comp.series(client.echo(msg));
//    assertResult(res, msg);
//  }

   public void doMultiExec() {
    comp.series(client.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(client.multi());
    assertResult(res1, null);
    Future<Integer> res2 = comp.series(client.incr(key1));
    Future<Integer> res3 = comp.series(client.incr(key1));
    Future<Buffer[]> res4 = comp.series(client.exec());
    //assertResult(res2, new Buffer[] {Buffer.create(String.valueOf(1)), Buffer.create(String.valueOf(2))});
  }

  // Private -------------------------

  private void setup() {
    comp = new Composer();
    latch = new CountDownLatch(1);
  }

  private void teardown() {
    comp = null;
    latch = null;
  }

  private void clearKeys() {
    comp.series(client.flushDB());
    comp.series(client.echo(val1)); // This is just a filler and makes sure the flush is complete before the test is run
  }

  private void assertException(final Future<?> future, final String error) {
    comp.series(new TestAction() {
      protected void doAct() {
        azzert(future.failed());
        azzert(future.exception() instanceof RedisException);
        azzert(error.equals(future.exception().getMessage()));
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
        System.out.println("Asserting expected: " + value + " actual " + res);

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
            azzert(value.equals(future.result()));
          }
        }
      }
    });
  }

  private void assertKey(Buffer key, final Buffer value) {
    final Future<Buffer> res = comp.series(client.get(key));
    comp.series(new TestAction() {
      protected void doAct() {
        azzert(Utils.buffersEqual(value, res.result()));
      }
    });
  }

  private void done() {
    comp.series(new TestAction() {
      protected void doAct() {
        client.close();
        latch.countDown();
      }
    });
    comp.execute();
  }

  private void runTest(final Method method) throws Exception {
    Nodex.instance.go(new Runnable() {
      public void run() {
        try {
          client = new RedisClient();
          clearKeys();
          method.invoke(RedisTest.this);
          done();
        } catch (Exception e) {
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
