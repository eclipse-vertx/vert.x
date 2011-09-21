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
import org.nodex.java.addons.redis.RedisConnection;
import org.nodex.java.addons.redis.RedisException;
import org.nodex.java.core.Future;
import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.SimpleAction;
import org.nodex.java.core.SimpleDeferred;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.composition.Composer;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
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
  private RedisConnection conn;
  private CountDownLatch latch;

  // Tests -----------------------------

  @Test
  public void testAll() throws Exception {
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
  }

  public void doTestAppend() {
    comp.series(conn.set(key1, val1));
    comp.series(conn.append(key1, val2));
    assertKey(key1, Buffer.create(0).appendBuffer(val1).appendBuffer(val2));
  }

  public void doTestAuth() {
    Future<Void> res = comp.series(conn.auth(Buffer.create("whatever")));
    assertResult(res, null);
  }

  public void doTestBgWriteAOF() {
    Future<Void> res = comp.series(conn.bgRewriteAOF());
    assertResult(res, null);
  }

  public void doBGSave() {
    Future<Void> res = comp.series(conn.bgSave());
    assertResult(res, null);
  }

  public void doBLPop() {
    final Future<Integer> res1 = comp.series(conn.lPush(keyl1, val1));
    final Future<Integer> res2 = comp.series(conn.lPush(keyl2, val2));
    final Future<Buffer[]> res = comp.series(conn.bLPop(10, keyl1, keyl2));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res, new Buffer[] { keyl1, val1});
  }

  public void doBRPop() {
    final Future<Integer> res1 = comp.series(conn.lPush(keyl1, val1));
    final Future<Integer> res2 = comp.series(conn.lPush(keyl2, val2));
    final Future<Buffer[]> res = comp.series(conn.bRPop(10, keyl1, keyl2));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res, new Buffer[] { keyl1, val1});
  }

  public void doBRPopLPush() {
    final Future<Integer> res1 = comp.series(conn.lPush(keyl1, val1));
    final Future<Buffer> res = comp.series(conn.bRPopLPush(keyl1, keyl2, 10));
    assertResult(res1, 1);
    assertResult(res, val1);
  }

  // Private -------------------------

  private void setup() {
    System.out.println("setup");
    comp = new Composer();
    latch = new CountDownLatch(1);
  }

  private void teardown() {
    System.out.println("teardown");
    comp = null;
    latch = null;
  }

  private void clearKeys() {
    comp.series(conn.flushDB());
  }

  private void assertException(final Future<?> future, final String error) {
    comp.series(new TestAction() {
      protected void doAct() {
        azzert(future.failed());
        azzert(future.exception() instanceof RedisException);
        System.out.println("exc: " + future.exception().getMessage());
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
        System.out.println("Result is:" + future.result());
        Object res = future.result();
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
    });
  }


  private void assertKey(Buffer key, final Buffer value) {
    final Future<Buffer> res = comp.series(conn.get(key));
    comp.series(new TestAction() {
      protected void doAct() {
        System.out.println("Val is " + res.result());
        azzert(Utils.buffersEqual(value, res.result()));
      }
    });
  }

  private void done() {
    comp.series(new TestAction() {
      protected void doAct() {
        conn.close();
        latch.countDown();
      }
    });
    comp.execute();
  }

  private void runTest(final Method method) throws Exception {

    Nodex.instance.go(new Runnable() {
      public void run() {
        RedisClient client = new RedisClient();
        client.connect("localhost", new Handler<RedisConnection>() {
          public void handle(RedisConnection theConn) {
            try {
              conn = theConn;
              clearKeys();
              method.invoke(RedisTest.this);
              done();
            } catch (Exception e) {
              azzert(false, "Exception thrown " + e.getMessage());
            }
          }
        });
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
