/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.tests.addons.redis;

import org.testng.annotations.Test;
import org.vertx.java.addons.redis.RedisConnection;
import org.vertx.java.addons.redis.RedisException;
import org.vertx.java.addons.redis.RedisPool;
import org.vertx.java.core.Deferred;
import org.vertx.java.core.DeferredAction;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleAction;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.composition.Composer;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

  private static final Buffer field1 = Buffer.create("field1");
  private static final Buffer field2 = Buffer.create("field2");
  private static final Buffer field3 = Buffer.create("field3");

  private static final Buffer channel1 = Buffer.create("channel1");
  private static final Buffer channel2 = Buffer.create("channel2");

  private static final Buffer pattern1 = Buffer.create("channel*");

  private static final Buffer keyl1 = Buffer.create("keyl1");
  private static final Buffer keyl2 = Buffer.create("keyl2");

  private static final Buffer val1 = Buffer.create("val1");
  private static final Buffer val2 = Buffer.create("val2");
  private static final Buffer val3 = Buffer.create("val3");
  private static final Buffer val4 = Buffer.create("val4");
  private static final Buffer val5 = Buffer.create("val5");

  private Composer comp;
  private RedisPool pool;
  private RedisConnection connection;
  private CountDownLatch latch;

  // Tests -----------------------------

  @Test
  public void testBGSave() throws Exception {
    runTest("doBGSave");
  }

  @Test
  public void testBLPop() throws Exception {
    runTest("doBLPop");
  }

  @Test
  public void testBRPop() throws Exception {
    runTest("doBRPop");
  }

  @Test
  public void testBRPopLPush() throws Exception {
    runTest("doBRPopLPush");
  }

  @Test
  public void testDBSize() throws Exception {
    runTest("doDBSize");
  }

  @Test
  public void testDecr() throws Exception {
    runTest("doDecr");
  }

  @Test
  public void testDecrBy() throws Exception {
    runTest("doDecrBy");
  }

  @Test
  public void testDel() throws Exception {
    runTest("doDel");
  }

  @Test
  public void testEcho() throws Exception {
    runTest("doEcho");
  }

  @Test
  public void testEmptyTransactionDiscard() throws Exception {
    runTest("doEmptyTransactionDiscard");
  }

  @Test
  public void testEmptyTransactionExec() throws Exception {
    runTest("doEmptyTransactionExec");
  }

  @Test
  public void testExists() throws Exception {
    runTest("doExists");
  }

  @Test
  public void testExpire() throws Exception {
    runTest("doExpire");
  }

  @Test
  public void testExpireAt() throws Exception {
    runTest("doExpireAt");
  }

  @Test
  public void testFlushAll() throws Exception {
    runTest("doFlushAll");
  }

  @Test
  public void testFlushDB() throws Exception {
    runTest("doFlushDB");
  }

  @Test
  public void testGet() throws Exception {
    runTest("doGet");
  }

  @Test
  public void testGetRange() throws Exception {
    runTest("doGetRange");
  }

  @Test
  public void testGetSet() throws Exception {
    runTest("doGetSet");
  }

  @Test
  public void testHExists() throws Exception {
    runTest("doHExists");
  }

  @Test
  public void testHGet() throws Exception {
    runTest("doHGet");
  }

  @Test
  public void testHGetAll() throws Exception {
    runTest("doHGetAll");
  }

  @Test
  public void testHIncrBy() throws Exception {
    runTest("doHIncrBy");
  }

  @Test
  public void testHKeys() throws Exception {
    runTest("doHKeys");
  }

  @Test
  public void testHLen() throws Exception {
    runTest("doHLen");
  }

  @Test
  public void testHMGet() throws Exception {
    runTest("doHMGet");
  }

  @Test
  public void testHMSet() throws Exception {
    runTest("doHMSet");
  }

  @Test
  public void testHSet() throws Exception {
    runTest("doHSet");
  }

  @Test
  public void testHSetNX() throws Exception {
    runTest("doHSetNX");
  }

  @Test
  public void testHVals() throws Exception {
    runTest("doHVals");
  }

  @Test
  public void testHdel() throws Exception {
    runTest("doHdel");
  }

  @Test
  public void testIncr() throws Exception {
    runTest("doIncr");
  }

  @Test
  public void testIncrBy() throws Exception {
    runTest("doIncrBy");
  }

  @Test
  public void testInfo() throws Exception {
    runTest("doInfo");
  }

  @Test
  public void testKeys() throws Exception {
    runTest("doKeys");
  }

  @Test
  public void testLIndex() throws Exception {
    runTest("doLIndex");
  }

  @Test
  public void testLInsert() throws Exception {
    runTest("doLInsert");
  }

  @Test
  public void testLLen() throws Exception {
    runTest("doLLen");
  }

  @Test
  public void testLPop() throws Exception {
    runTest("doLPop");
  }

  @Test
  public void testLPush() throws Exception {
    runTest("doLPush");
  }

  @Test
  public void testLPushX() throws Exception {
    runTest("doLPushX");
  }

  @Test
  public void testLRange() throws Exception {
    runTest("doLRange");
  }

  @Test
  public void testLRem() throws Exception {
    runTest("doLRem");
  }

  @Test
  public void testLSet() throws Exception {
    runTest("doLSet");
  }

  @Test
  public void testLTrim() throws Exception {
    runTest("doLTrim");
  }

  @Test
  public void testLastSave() throws Exception {
    runTest("doLastSave");
  }

  @Test
  public void testMGet() throws Exception {
    runTest("doMGet");
  }

  @Test
  public void testMSet() throws Exception {
    runTest("doMSet");
  }

  @Test
  public void testMSetNx() throws Exception {
    runTest("doMSetNx");
  }

  @Test
  public void testMultiDiscard() throws Exception {
    runTest("doMultiDiscard");
  }

  @Test
  public void testPersist() throws Exception {
    runTest("doPersist");
  }

  @Test
  public void testPing() throws Exception {
    runTest("doPing");
  }

  @Test
  public void testPooling1() throws Exception {
    runTest("doPooling1");
  }

  @Test
  public void testPooling2() throws Exception {
    runTest("doPooling2");
  }

  @Test
  public void testPubSub() throws Exception {
    runTest("doPubSub");
  }

  @Test
  public void testPubSubOnlySubscribe() throws Exception {
    runTest("doPubSubOnlySubscribe");
  }

  @Test
  public void testPubSubPatterns() throws Exception {
    runTest("doPubSubPatterns");
  }

  @Test
  public void testPubSubSubscribeMultiple() throws Exception {
    runTest("doPubSubSubscribeMultiple");
  }

  @Test
  public void testRPop() throws Exception {
    runTest("doRPop");
  }

  @Test
  public void testRPopLPush() throws Exception {
    runTest("doRPopLPush");
  }

  @Test
  public void testRPush() throws Exception {
    runTest("doRPush");
  }

  @Test
  public void testRPushX() throws Exception {
    runTest("doRPushX");
  }

  @Test
  public void testRandomKey() throws Exception {
    runTest("doRandomKey");
  }

  @Test
  public void testRename() throws Exception {
    runTest("doRename");
  }

  @Test
  public void testRenameNx() throws Exception {
    runTest("doRenameNx");
  }

  @Test
  public void testSAdd() throws Exception {
    runTest("doSAdd");
  }

  @Test
  public void testSCard() throws Exception {
    runTest("doSCard");
  }

  @Test
  public void testSDiff() throws Exception {
    runTest("doSDiff");
  }

  @Test
  public void testSDiffStore() throws Exception {
    runTest("doSDiffStore");
  }

  @Test
  public void testSInter() throws Exception {
    runTest("doSInter");
  }

  @Test
  public void testSInterStore() throws Exception {
    runTest("doSInterStore");
  }

  @Test
  public void testSIsMember() throws Exception {
    runTest("doSIsMember");
  }

  @Test
  public void testSMembers() throws Exception {
    runTest("doSMembers");
  }

  @Test
  public void testSMove() throws Exception {
    runTest("doSMove");
  }

  @Test
  public void testSPop() throws Exception {
    runTest("doSPop");
  }

  @Test
  public void testSRandMember() throws Exception {
    runTest("doSRandMember");
  }

  @Test
  public void testSRem() throws Exception {
    runTest("doSRem");
  }

  @Test
  public void testSUnion() throws Exception {
    runTest("doSUnion");
  }

  @Test
  public void testSUnionStore() throws Exception {
    runTest("doSUnionStore");
  }

  @Test
  public void testSave() throws Exception {
    runTest("doSave");
  }

  @Test
  public void testSelect() throws Exception {
    runTest("doSelect");
  }

  @Test
  public void testSet() throws Exception {
    runTest("doSet");
  }

  @Test
  public void testSetEx() throws Exception {
    runTest("doSetEx");
  }

  @Test
  public void testSetGetBit() throws Exception {
    runTest("doSetGetBit");
  }

  @Test
  public void testSetNx() throws Exception {
    runTest("doSetNx");
  }

  @Test
  public void testSetRange() throws Exception {
    runTest("doSetRange");
  }

  @Test
  public void testSortAlpha() throws Exception {
    runTest("doSortAlpha");
  }

  @Test
  public void testSortAscending() throws Exception {
    runTest("doSortAscending");
  }

  @Test
  public void testSortDescending() throws Exception {
    runTest("doSortDescending");
  }

  @Test
  public void testSortLimit() throws Exception {
    runTest("doSortLimit");
  }

  @Test
  public void testStrLen() throws Exception {
    runTest("doStrLen");
  }

  @Test
  public void testTTL() throws Exception {
    runTest("doTTL");
  }

  @Test
  public void testTestAppend() throws Exception {
    runTest("doTestAppend");
  }

  @Test
  public void testTestBgWriteAOF() throws Exception {
    runTest("doTestBgWriteAOF");
  }

  @Test
  public void testTestWithPassword() throws Exception {
    runTest("doTestWithPassword");
  }

  @Test
  public void testTransactionDiscard() throws Exception {
    runTest("doTransactionDiscard");
  }

  @Test
  public void testTransactionExec() throws Exception {
    runTest("doTransactionExec");
  }

  @Test
  public void testTransactionMixed() throws Exception {
    runTest("doTransactionMixed");
  }

  @Test
  public void testTransactionWithMultiBulkExec() throws Exception {
    runTest("doTransactionWithMultiBulkExec");
  }

  @Test
  public void testType() throws Exception {
    runTest("doType");
  }

  @Test
  public void testWatchUnWatch() throws Exception {
    runTest("doWatchUnWatch");
  }

  @Test
  public void testZAdd() throws Exception {
    runTest("doZAdd");
  }

  @Test
  public void testZCard() throws Exception {
    runTest("doZCard");
  }

  @Test
  public void testZCount() throws Exception {
    runTest("doZCount");
  }

  @Test
  public void testZIncrBy() throws Exception {
    runTest("doZIncrBy");
  }

  @Test
  public void testZInterStore() throws Exception {
    runTest("doZInterStore");
  }

  @Test
  public void testZRange() throws Exception {
    runTest("doZRange");
  }

  @Test
  public void testZRangeByScore() throws Exception {
    runTest("doZRangeByScore");
  }

  @Test
  public void testZRank() throws Exception {
    runTest("doZRank");
  }

  @Test
  public void testZRem() throws Exception {
    runTest("doZRem");
  }

  @Test
  public void testZRemRangeByRank() throws Exception {
    runTest("doZRemRangeByRank");
  }

  @Test
  public void testZRevRange() throws Exception {
    runTest("doZRevRange");
  }

  @Test
  public void testZRevRangeByScore() throws Exception {
    runTest("doZRevRangeByScore");
  }

  @Test
  public void testZRevRank() throws Exception {
    runTest("doZRevRank");
  }

  @Test
  public void testZScore() throws Exception {
    runTest("doZScore");
  }

  @Test
  public void testZUnionStore() throws Exception {
    runTest("doZUnionStore");
  }


  public void doTestAppend() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.append(key1, val2));
    assertKey(key1, Buffer.create(0).appendBuffer(val1).appendBuffer(val2));
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
    assertResult(res, new Buffer[]{keyl1, val1});
  }

  public void doBRPop() {
    Future<Integer> res1 = comp.series(connection.lPush(keyl1, val1));
    Future<Integer> res2 = comp.series(connection.lPush(keyl2, val2));
    Future<Buffer[]> res = comp.series(connection.bRPop(10, keyl1, keyl2));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res, new Buffer[]{keyl1, val1});
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
    for (int i = 0; i < num; i++) {
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
    assertResult(res2, 1);
    assertResult(res3, 2);
  }

  public void doTransactionWithMultiBulkExec() {
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Boolean> res2 = comp.parallel(connection.hSet(key1, field1, val1));
    Future<Boolean> res3 = comp.parallel(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res4 = comp.parallel(connection.hGetAll(key1));
    Future<Void> res5 = comp.parallel(connection.exec());
    assertResult(res5, null);
    assertResult(res2, true);
    assertResult(res3, true);
    assertResult(res4, new Buffer[]{field1, val1, field2, val2});
  }

  public void doTransactionMixed() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Integer> res2 = comp.series(connection.incr(key1));
    Future<Integer> res3 = comp.parallel(connection.incr(key1));
    Future<Void> res4 = comp.parallel(connection.exec());
    assertResult(res4, null);
    assertResult(res2, 1);
    assertResult(res3, 2);
    Future<Integer> res5 = comp.series(connection.incr(key1));
    assertResult(res5, 3);
    Future<Void> res6 = comp.series(connection.multi());
    assertResult(res6, null);
    Future<Integer> res7 = comp.series(connection.incr(key1));
    Future<Integer> res8 = comp.parallel(connection.incr(key1));
    Future<Void> res9 = comp.parallel(connection.exec());
    assertResult(res9, null);
    assertResult(res7, 4);
    assertResult(res8, 5);
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

  public void doPubSub(boolean patterns) {
    RedisPool pool = new RedisPool().setMaxPoolSize(3);
    RedisConnection conn1 = pool.connection();
    final RedisConnection conn2 = pool.connection();
    final RedisConnection conn3 = pool.connection();

    Deferred<Void> d1 = patterns ? conn2.pSubscribe(pattern1) : conn2.subscribe(channel1);
    Future<Void> res1 = comp.series(d1);
    assertResult(res1, null);

    Deferred<Void> d2 = patterns ? conn3.pSubscribe(pattern1) : conn3.subscribe(channel1);
    Future<Void> res2 = comp.series(d2);
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

    Deferred<Void> d3 = patterns ? conn2.pUnsubscribe(pattern1) : conn2.unsubscribe(channel1);
    Future<Void> res6 = comp.series(d3);
    assertResult(res6, null);

    Deferred<Void> d4 = patterns ? conn3.pUnsubscribe(pattern1) : conn3.unsubscribe(channel1);
    Future<Void> res7 = comp.series(d4);
    assertResult(res7, null);

    comp.series(conn1.closeDeferred());
    comp.parallel(conn2.closeDeferred());
    comp.parallel(conn3.closeDeferred());
  }

  public void doPubSub() {
    doPubSub(false);
  }

  public void doPubSubPatterns() {
    doPubSub(true);
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
    for (int i = 0; i < numConnections; i++) {
      final RedisConnection conn = pool.connection();
      Future<Integer> res = comp.parallel(conn.incr(key1));
      comp.parallel(conn.closeDeferred());
      futures.add(res);
    }

    final int expected = (numConnections + 1) * numConnections / 2;

    comp.series(new SimpleAction() {
      public void act() {
        int tot = 0;
        for (Future<Integer> future : futures) {
          tot += future.result();
        }

        azzert(expected == tot);
      }
    });
  }

  public void doPooling2() {
    RedisPool pool = new RedisPool().setMaxPoolSize(10);

    int numConnections = 100;

    for (int i = 0; i < numConnections; i++) {
      // Get connection and return immediately
      final RedisConnection conn = pool.connection();
      comp.parallel(conn.closeDeferred());
    }
  }

  public void doExists() {
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    comp.series(connection.set(key1, val1));
    Future<Boolean> res2 = comp.series(connection.exists(key1));
    assertResult(res1, false);
    assertResult(res2, true);
  }

  public void doExpire() {
    comp.series(connection.set(key1, val1));
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    assertResult(res1, true);
    comp.series(connection.expire(key1, 0));
    Future<Boolean> res2 = comp.series(connection.exists(key1));
    assertResult(res2, false);
  }

  public void doExpireAt() {
    comp.series(connection.set(key1, val1));
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    assertResult(res1, true);
    comp.series(connection.expireAt(key1, (int) (System.currentTimeMillis() / 1000)));
    Future<Boolean> res2 = comp.series(connection.exists(key1));
    assertResult(res2, false);
  }

  public void doFlushAll() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.flushAll());
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    assertResult(res1, false);
  }

  public void doFlushDB() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.flushDB());
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    assertResult(res1, false);
  }

  public void doGet() {
    comp.series(connection.set(key1, val1));
    Future<Buffer> res = comp.series(connection.get(key1));
    assertResult(res, val1);
  }

  public void doSetGetBit() {
    comp.series(connection.setBit(key1, 10, 1));
    Future<Integer> res = comp.series(connection.getBit(key1, 10));
    assertResult(res, 1);
  }

  public void doGetRange() {
    comp.series(connection.set(key1, val1));
    Future<Buffer> res = comp.series(connection.getRange(key1, 0, 1));
    assertResult(res, val1.copy(0, 2));
  }

  public void doGetSet() {
    comp.series(connection.set(key1, val1));
    Future<Buffer> res = comp.series(connection.getSet(key1, val2));
    assertResult(res, val1);
  }

  public void doHdel() {
    comp.series(connection.hSet(key1, field1, val1));
    Future<Integer> res1 = comp.series(connection.hDel(key1, field1));
    assertResult(res1, 1);
  }

  public void doHExists() {
    comp.series(connection.hSet(key1, field1, val1));
    Future<Boolean> res1 = comp.series(connection.hExists(key1, field1));
    Future<Boolean> res2 = comp.series(connection.hExists(key1, field2));
    assertResult(res1, true);
    assertResult(res2, false);
  }

  public void doHGet() {
    comp.series(connection.hSet(key1, field1, val1));
    Future<Buffer> res1 = comp.series(connection.hGet(key1, field1));
    Future<Buffer> res2 = comp.series(connection.hGet(key1, field2));
    assertResult(res1, val1);
    assertResult(res2, null);
  }

  public void doHGetAll() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res1 = comp.series(connection.hGetAll(key1));
    assertResult(res1, new Buffer[]{field1, val1, field2, val2});
  }

  public void doHIncrBy() {
    comp.series(connection.hSet(key1, field1, buffFromInt(5)));
    Future<Integer> res1 = comp.series(connection.hIncrBy(key1, field1, 2));
    Future<Integer> res2 = comp.series(connection.hIncrBy(key1, field1, -1));
    assertResult(res1, 7);
    assertResult(res2, 6);
  }

  public void doHKeys() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res1 = comp.series(connection.hKeys(key1));
    assertResult(res1, new Buffer[]{field1, field2});
  }

  public void doHLen() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Integer> res1 = comp.series(connection.hLen(key1));
    assertResult(res1, 2);
  }

  public void doHMGet() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res1 = comp.series(connection.hmGet(key1, field1, field2, field3));
    assertResult(res1, new Buffer[]{val1, val2, null});
  }

  public void doHMSet() {
    Map<Buffer, Buffer> map = new HashMap<>();
    map.put(field1, val1);
    map.put(field2, val2);
    Future<Void> res1 = comp.series(connection.hmSet(key1, map));
    Future<Buffer> res2 = comp.series(connection.hGet(key1, field1));
    Future<Buffer> res3 = comp.series(connection.hGet(key1, field2));
    assertResult(res1, null);
    assertResult(res2, val1);
    assertResult(res3, val2);
  }

  public void doHSet() {
    Future<Boolean> res1 = comp.series(connection.hSet(key1, field1, val1));
    Future<Buffer> res2 = comp.series(connection.hGet(key1, field1));
    assertResult(res1, true);
    assertResult(res2, val1);
  }

  public void doHSetNX() {
    Future<Boolean> res1 = comp.series(connection.hSetNx(key1, field1, val1));
    Future<Boolean> res2 = comp.series(connection.hSetNx(key1, field1, val2));
    Future<Buffer> res3 = comp.series(connection.hGet(key1, field1));
    assertResult(res1, true);
    assertResult(res2, false);
    assertResult(res3, val1);
  }

  public void doHVals() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res1 = comp.series(connection.hVals(key1));
    assertResult(res1, new Buffer[]{val1, val2});
  }

  public void doIncr() {
    comp.series(connection.set(key1, buffFromInt(5)));
    Future<Integer> res1 = comp.series(connection.incr(key1));
    assertResult(res1, 6);
  }

  public void doIncrBy() {
    comp.series(connection.set(key1, buffFromInt(5)));
    Future<Integer> res1 = comp.series(connection.incrBy(key1, 4));
    assertResult(res1, 9);
  }

  public void doInfo() {
    final Future<Buffer> res = comp.series(connection.info());
    comp.series(new SimpleAction() {
      public void act() {
        azzert(res.result() != null);
        //TODO could check all the fields returned
      }
    });
  }

  public void doKeys() {
    Map<Buffer, Buffer> map = new HashMap<>();
    map.put(key1, val1);
    map.put(key2, val2);
    map.put(key3, val3);
    map.put(key4, val4);
    map.put(Buffer.create("otherkey"), val5);
    comp.series(connection.mset(map));
    final Future<Buffer[]> res = comp.series(connection.keys(Buffer.create("key*")));
    assertSameElements(res, new Buffer[]{key1, key2, key3, key4});
  }

  public void doLastSave() {
    final Future<Integer> res = comp.series(connection.lastSave());
    comp.series(new SimpleAction() {
      public void act() {
        azzert(res.result() <= System.currentTimeMillis() / 1000);
      }
    });
  }

  public void doLIndex() {
    Future<Integer> res1 = comp.series(connection.lPush(key1, val1));
    Future<Integer> res2 = comp.series(connection.lPush(key1, val2));
    Future<Buffer> res3 = comp.series(connection.lIndex(key1, 0));
    Future<Buffer> res4 = comp.series(connection.lIndex(key1, 1));
    Future<Buffer> res5 = comp.series(connection.lIndex(key1, 2));
    assertResult(res1, 1);
    assertResult(res2, 2);
    assertResult(res3, val2);
    assertResult(res4, val1);
    assertResult(res5, null);
  }

  public void doLInsert() {
    Future<Integer> res1 = comp.series(connection.rPush(key1, val1));
    Future<Integer> res2 = comp.series(connection.rPush(key1, val2));
    Future<Integer> res3 = comp.series(connection.lInsert(key1, true, val2, val3));
    Future<Buffer[]> res4 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res1, 1);
    assertResult(res2, 2);
    assertResult(res3, 3);
    assertResult(res4, new Buffer[]{val1, val3, val2});
  }

  public void doLLen() {
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPush(key1, val2));
    Future<Integer> res = comp.series(connection.lLen(key1));
    assertResult(res, 2);
  }

  public void doLPop() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Buffer> res = comp.series(connection.lPop(key1));
    assertResult(res, val1);
  }

  public void doLPush() {
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPush(key1, val2));
    comp.series(connection.lPush(key1, val3));
    Future<Buffer[]> res = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res, new Buffer[]{val3, val2, val1});
  }

  public void doLPushX() {
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPushX(key1, val2));
    comp.series(connection.lPushX(key1, val3));
    comp.series(connection.lPushX(key2, val1));
    Future<Buffer[]> res1 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res1, new Buffer[]{val3, val2, val1});
    Future<Buffer[]> res2 = comp.series(connection.lRange(key2, 0, -1));
    assertResult(res2, new Buffer[]{});
  }

  public void doLRange() {
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPush(key1, val2));
    comp.series(connection.lPush(key1, val3));
    Future<Buffer[]> res = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res, new Buffer[]{val3, val2, val1});
  }

  public void doLRem() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val1));
    Future<Integer> res1 = comp.series(connection.lRem(key1, -2, val1));
    Future<Buffer[]> res2 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res2, new Buffer[]{val1, val2});
  }

  public void doLSet() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Void> res1 = comp.series(connection.lSet(key1, 0, val4));
    Future<Buffer[]> res2 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res1, null);
    assertResult(res2, new Buffer[]{val4, val2, val3});
  }

  public void doLTrim() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Void> res1 = comp.series(connection.lTrim(key1, 1, -1));
    Future<Buffer[]> res2 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res1, null);
    assertResult(res2, new Buffer[]{val2, val3});
  }

  public void doMGet() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.set(key2, val2));
    comp.series(connection.set(key3, val3));
    Future<Buffer[]> res2 = comp.series(connection.mget(key1, key2));
    assertResult(res2, new Buffer[]{val1, val2});
  }

//  public void doMove() {
//    comp.series(connection.set(key1, val1));
//    Future<Boolean> res1 = comp.series(connection.move(key1, Buffer.create("somedb")));
//    assertResult(res1, true);
//  }

  public void doMSet() {
    Map<Buffer, Buffer> map = new HashMap<>();
    map.put(key1, val1);
    map.put(key2, val2);
    Future<Void> res1 = comp.series(connection.mset(map));
    Future<Buffer> res2 = comp.series(connection.get(key1));
    Future<Buffer> res3 = comp.series(connection.get(key2));
    assertResult(res1, null);
    assertResult(res2, val1);
    assertResult(res3, val2);
  }

  public void doMSetNx() {
    Map<Buffer, Buffer> map1 = new HashMap<>();
    map1.put(key1, val1);
    map1.put(key2, val2);
    Future<Boolean> res1 = comp.series(connection.msetNx(map1));
    Map<Buffer, Buffer> map2 = new HashMap<>();
    map2.put(key1, val1);
    map2.put(key3, val3);
    Future<Boolean> res2 = comp.series(connection.msetNx(map2));
    assertResult(res1, true);
    assertResult(res2, false);
    Future<Buffer[]> res3 = comp.series(connection.mget(key1, key2, key3));
    assertResult(res3, new Buffer[]{val1, val2, null});
  }

  public void doPersist() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.expire(key1, 10));
    Future<Boolean> res1 = comp.series(connection.persist(key1));
    Future<Boolean> res2 = comp.series(connection.exists(key1));
    assertResult(res1, true);
    assertResult(res2, true);
  }

  public void doPing() {
    Future<Void> res = comp.series(connection.ping());
    assertResult(res, null);
  }

  public void doRandomKey() {
    comp.series(connection.set(key1, val1));
    Future<Buffer> res = comp.series(connection.randomKey());
    assertResult(res, key1);
  }

  public void doRename() {
    comp.series(connection.set(key1, val1));
    Future<Void> res1 = comp.series(connection.rename(key1, key2));
    Future<Buffer> res2 = comp.series(connection.get(key2));
    assertResult(res1, null);
    assertResult(res2, val1);
  }

  public void doRenameNx() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.set(key2, val2));
    Future<Boolean> res1 = comp.series(connection.renameNX(key1, key2));
    Future<Buffer> res2 = comp.series(connection.get(key2));
    assertResult(res1, false);
    assertResult(res2, val2);
  }

  public void doRPop() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Buffer> res = comp.series(connection.rPoplPush(key1, key2));
    assertResult(res, val3);
  }

  public void doRPopLPush() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Buffer> res = comp.series(connection.rPop(key1));
    assertResult(res, val3);
  }

  public void doRPush() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    Future<Buffer[]> res = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res, new Buffer[]{val1, val2});
  }

  public void doRPushX() {
    comp.series(connection.rPushX(key1, val1));
    comp.series(connection.rPushX(key1, val2));
    Future<Buffer[]> res = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res, new Buffer[]{});
  }

  public void doSAdd() {
    Future<Integer> res1 = comp.series(connection.sAdd(key1, val1));
    Future<Integer> res2 = comp.series(connection.sAdd(key1, val2));
    Future<Integer> res3 = comp.series(connection.sAdd(key1, val2));
    Future<Buffer[]> res4 = comp.series(connection.sMembers(key1));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res3, 0);
    assertSameElements(res4, new Buffer[]{val1, val2});
  }

  public void doSave() {
    Future<Void> res = comp.series(connection.save());
    assertResult(res, null);
  }

  public void doSCard() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    Future<Integer> res = comp.series(connection.sCard(key1));
    assertResult(res, 2);
  }

  public void doSDiff() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Buffer[]> res = comp.series(connection.sDiff(key1, key2));
    assertSameElements(res, new Buffer[]{val1});
  }

  public void doSDiffStore() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Integer> res1 = comp.series(connection.sDiffStore(key3, key1, key2));
    assertResult(res1, 1);
  }

  public void doSelect() {
    Future<Void> res = comp.series(connection.select(1));
    assertResult(res, null);
  }

  public void doSet() {
    Future<Void> res = comp.series(connection.set(key1, val1));
    assertResult(res, null);
    assertKey(key1, val1);
  }

  public void doSetEx() {
    Future<Void> res1 = comp.series(connection.setEx(key1, 1, val1));
    assertResult(res1, null);
    Future<Buffer> res2 = comp.series(connection.get(key1));
    assertResult(res2, val1);
  }

  public void doSetNx() {
    Future<Boolean> res1 = comp.series(connection.setNx(key1, val1));
    Future<Boolean> res2 = comp.series(connection.setNx(key1, val2));
    assertResult(res1, true);
    assertResult(res2, false);
    Future<Buffer> res3 = comp.series(connection.get(key1));
    assertResult(res3, val1);
  }

  public void doSetRange() {
    comp.series(connection.set(key1, Buffer.create("Hello world")));
    Future<Integer> res1 = comp.series(connection.setRange(key1, 6, Buffer.create("aardvarks")));
    assertResult(res1, 15);
    Future<Buffer> res2 = comp.series(connection.get(key1));
    assertResult(res2, Buffer.create("Hello aardvarks"));
  }

  public void doSInter() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Buffer[]> res = comp.series(connection.sInter(key1, key2));
    assertSameElements(res, new Buffer[]{val2, val3});
  }

  public void doSInterStore() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Integer> res = comp.series(connection.sInterStore(key3, key1, key2));
    assertResult(res, 2);
  }

  public void doSIsMember() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    Future<Boolean> res1 = comp.series(connection.sIsMember(key1, val1));
    Future<Boolean> res2 = comp.series(connection.sIsMember(key1, val4));
    assertResult(res1, true);
    assertResult(res2, false);
  }

  public void doSMembers() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    Future<Buffer[]> res = comp.series(connection.sMembers(key1));
    assertSameElements(res, new Buffer[]{val1, val2, val3});
  }

  public void doSMove() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key2, val3));
    Future<Boolean> res1 = comp.series(connection.sMove(key1, key2, val1));
    Future<Buffer[]> res2 = comp.series(connection.sMembers(key2));
    assertResult(res1, true);
    assertSameElements(res2, new Buffer[]{val1, val3});
  }

  public void doSortAscending() {
    comp.series(connection.lPush(key1, buffFromInt(3)));
    comp.series(connection.lPush(key1, buffFromInt(1)));
    comp.series(connection.lPush(key1, buffFromInt(2)));
    Future<Buffer[]> res = comp.series(connection.sort(key1));
    assertResult(res, new Buffer[]{buffFromInt(1), buffFromInt(2), buffFromInt(3)});
  }

  public void doSortDescending() {
    comp.series(connection.lPush(key1, buffFromInt(3)));
    comp.series(connection.lPush(key1, buffFromInt(1)));
    comp.series(connection.lPush(key1, buffFromInt(2)));
    Future<Buffer[]> res = comp.series(connection.sort(key1, false));
    assertResult(res, new Buffer[]{buffFromInt(3), buffFromInt(2), buffFromInt(1)});
  }

  public void doSortAlpha() {
    comp.series(connection.lPush(key1, val3));
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPush(key1, val2));
    Future<Buffer[]> res = comp.series(connection.sort(key1, true, true));
    assertResult(res, new Buffer[]{val1, val2, val3});
  }

  public void doSortLimit() {
    comp.series(connection.lPush(key1, buffFromInt(3)));
    comp.series(connection.lPush(key1, buffFromInt(1)));
    comp.series(connection.lPush(key1, buffFromInt(2)));
    Future<Buffer[]> res = comp.series(connection.sort(key1, 1, 1, true, false));
    assertResult(res, new Buffer[]{buffFromInt(2)});
  }

  public void doSPop() {
    comp.series(connection.sAdd(key1, val1));
    Future<Buffer> res1 = comp.series(connection.sPop(key1));
    assertResult(res1, val1);
    Future<Buffer[]> res2 = comp.series(connection.sMembers(key1));
    assertSameElements(res2, new Buffer[]{});
  }

  public void doSRandMember() {
    comp.series(connection.sAdd(key1, val1));
    Future<Buffer> res1 = comp.series(connection.sRandMember(key1));
    assertResult(res1, val1);
    Future<Buffer[]> res2 = comp.series(connection.sMembers(key1));
    assertSameElements(res2, new Buffer[]{val1});
  }

  public void doSRem() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    Future<Integer> res1 = comp.series(connection.sRem(key1, val1));
    Future<Buffer[]> res2 = comp.series(connection.sMembers(key1));
    assertResult(res1, 1);
    assertSameElements(res2, new Buffer[]{val2, val3});
  }

  public void doStrLen() {
    comp.series(connection.set(key1, val1));
    Future<Integer> res = comp.series(connection.strLen(key1));
    assertResult(res, val1.length());
  }

  public void doSUnion() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Buffer[]> res = comp.series(connection.sUnion(key1, key2));
    assertSameElements(res, new Buffer[]{val1, val2, val3, val4});
  }

  public void doSUnionStore() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Integer> res = comp.series(connection.sUnionStore(key3, key1, key2));
    assertResult(res, 4);
  }

  public void doTTL() {
    comp.series(connection.setEx(key1, 10, val1));
    final Future<Integer> res = comp.series(connection.ttl(key1));
    comp.series(new SimpleAction() {
      public void act() {
        azzert(res.result() <= 10);
      }
    });
  }

  public void doType() {
    comp.series(connection.set(key1, val1));
    Future<String> res = comp.series(connection.type(key1));
    assertResult(res, "string");
  }

  public void doWatchUnWatch() {
    Future<Void> res1 = comp.parallel(connection.watch(key1));
    Future<Void> res2 = comp.parallel(connection.unwatch());
    assertResult(res1, null);
    assertResult(res2, null);
  }

  public void doZAdd() {
    Future<Integer> res1 = comp.series(connection.zAdd(key1, 1.0, val1));
    Future<Integer> res2 = comp.series(connection.zAdd(key1, 2.0, val2));
    Future<Integer> res3 = comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res4 = comp.series(connection.zRange(key1, 0, -1, true));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res3, 1);
    assertResult(res4, new Buffer[]{val1, buffFromInt(1), val2, buffFromInt(2), val3, buffFromInt(3)});
  }

  public void doZCard() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res4 = comp.series(connection.zCard(key1));
    assertResult(res4, 3);
  }

  public void doZCount() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res4 = comp.series(connection.zCount(key1, 2.0, 3.0));
    assertResult(res4, 2);
  }

  public void doZIncrBy() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    Future<Double> res1 = comp.series(connection.zIncrBy(key1, 2.5, val1));
    Future<Buffer[]> res2 = comp.series(connection.zRange(key1, 0, -1, true));
    assertResult(res1, 3.5);
    assertSameElements(res2, new Buffer[]{val1, buffFromDouble(3.5), val2, buffFromInt(2)});
  }

  public void doZInterStore() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key2, 1.0, val1));
    comp.series(connection.zAdd(key2, 2.0, val2));
    comp.series(connection.zAdd(key2, 3.0, val3));
    Future<Integer> res = comp.series(connection.zInterStore(key3, 2, new Buffer[]{key1, key2}, new double[]{2.0, 3.0}, RedisConnection.AggregateType.SUM));
    assertResult(res, 2);
    Future<Buffer[]> res2 = comp.series(connection.zRange(key3, 0, -1, true));
    assertSameElements(res2, new Buffer[]{val1, buffFromInt(5), val2, buffFromInt(10)});
  }

  public void doZRange() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res = comp.series(connection.zRange(key1, 0, -1, true));
    assertResult(res, new Buffer[]{val1, buffFromInt(1), val2, buffFromInt(2), val3, buffFromInt(3)});
  }

  public void doZRangeByScore() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res = comp.series(connection.zRangeByScore(key1, 2.0, 3.0, true, 0, -1));
    assertResult(res, new Buffer[]{val2, buffFromInt(2), val3, buffFromInt(3)});
  }

  public void doZRank() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res = comp.series(connection.zRank(key1, val2));
    assertResult(res, 1);
  }

  public void doZRem() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res1 = comp.series(connection.zRem(key1, val1));
    Future<Buffer[]> res2 = comp.series(connection.zRange(key1, 0, -1, false));
    assertResult(res1, 1);
    assertSameElements(res2, new Buffer[]{val2, val3});
  }

  public void doZRemRangeByRank() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res1 = comp.series(connection.zRemRangeByRank(key1, 0, 1));
    Future<Buffer[]> res2 = comp.series(connection.zRange(key1, 0, -1, false));
    assertResult(res1, 2);
    assertSameElements(res2, new Buffer[]{val3});
  }

  public void doZRevRange() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res = comp.series(connection.zRevRange(key1, 0, -1, true));
    assertResult(res, new Buffer[]{val3, buffFromInt(3), val2, buffFromInt(2), val1, buffFromInt(1)});
  }

  public void doZRevRangeByScore() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res = comp.series(connection.zRevRangeByScore(key1, 3.0, 2.0, true, 0, -1));
    assertResult(res, new Buffer[]{val3, buffFromInt(3), val2, buffFromInt(2)});
  }

  public void doZRevRank() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res = comp.series(connection.zRevRank(key1, val3));
    assertResult(res, 0);
  }

  public void doZScore() {
    comp.series(connection.zAdd(key1, 1.5, val1));
    Future<Double> res = comp.series(connection.zScore(key1, val1));
    assertResult(res, 1.5);
  }

  public void doZUnionStore() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key2, 1.0, val1));
    comp.series(connection.zAdd(key2, 2.0, val2));
    comp.series(connection.zAdd(key2, 3.0, val3));
    Future<Integer> res = comp.series(connection.zUnionStore(key3, 2, new Buffer[]{key1, key2}, new double[]{2.0, 3.0}, RedisConnection.AggregateType.SUM));
    assertResult(res, 3);
    Future<Buffer[]> res2 = comp.series(connection.zRange(key3, 0, -1, true));
    assertSameElements(res2, new Buffer[]{val1, buffFromInt(5), val2, buffFromInt(10), val3, buffFromInt(9)});
  }

  public void doTestWithPassword() {
    RedisPool pool2 = new RedisPool().setPassword("whatever");
    RedisConnection conn2 = pool2.connection();
    comp.series(conn2.ping());
    conn2.close();
    pool2.close();
  }

  private void setup() {
    comp = new Composer();
    latch = new CountDownLatch(1);
  }

  private void teardown() {
    pool = null;
    comp = null;
    latch = null;
  }

  private Buffer buffFromInt(int i) {
    return Buffer.create(String.valueOf(i));
  }

  private Buffer buffFromDouble(double d) {
    return Buffer.create(String.valueOf(d));
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
          azzert(value == null, "Expected: " + value + " Actual: " + res);
        } else {
          if (res instanceof Buffer) {
            azzert(Utils.buffersEqual((Buffer) value, (Buffer) res), "Expected: " + value + " Actual: " + res);
          } else if (res instanceof Buffer[]) {
            Buffer[] mb = (Buffer[]) res;
            Buffer[] expected = (Buffer[]) value;
            for (int i = 0; i < expected.length; i++) {
              if (expected[i] != null) {
                azzert(Utils.buffersEqual(expected[i], mb[i]), "Buffer not equal: " + expected[i] + " " + mb[i]);
              } else {
                azzert(mb[i] == null);
              }
            }
          } else {
            azzert(value.equals(future.result()), "Expected: " + value + " Actual: " + future.result());
          }
        }
      }
    });
  }

  private void assertSameElements(final Future<Buffer[]> future, final Buffer[] value) {
    comp.series(new TestAction() {
      protected void doAct() {
        if (!future.succeeded()) {
          future.exception().printStackTrace();
        }
        azzert(future.succeeded());
        Buffer[] res = future.result();
        azzert(value.length == res.length, "Expected length: " + value.length + " Actual length: " + res.length);
        for (int i = 0; i < value.length; i++) {
          Buffer b = value[i];
          boolean found = false;
          for (int j = 0; j < res.length; j++) {
            if (Utils.buffersEqual(b, res[j])) {
              found = true;
              break;
            }
          }
          azzert(found);
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

  private void runTest(final String methodName) throws Exception {
    final Method method = RedisTest.class.getMethod(methodName);
    setup();
    Vertx.instance.go(new Runnable() {
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
    teardown();
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


