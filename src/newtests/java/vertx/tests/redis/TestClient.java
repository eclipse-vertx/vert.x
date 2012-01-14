package vertx.tests.redis;

import org.vertx.java.addons.redis.RedisConnection;
import org.vertx.java.addons.redis.RedisException;
import org.vertx.java.addons.redis.RedisPool;
import org.vertx.java.core.Deferred;
import org.vertx.java.core.DeferredAction;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleAction;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.composition.Composer;
import org.vertx.java.newtests.TestClientBase;
import org.vertx.tests.Utils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {


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

  @Override
  public void start() {
    super.start();
    comp = new Composer();
    pool = new RedisPool();
    connection = pool.connection();
    clearKeys();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testAppend() {
    System.out.println("in test append");
    comp.series(connection.set(key1, val1));
    comp.series(connection.append(key1, val2));
    assertKey(key1, Buffer.create(0).appendBuffer(val1).appendBuffer(val2));
    done();
  }

  public void testBgWriteAOF() {
    Future<Void> res = comp.series(connection.bgRewriteAOF());
    assertResult(res, null);
    done();
  }

  public void testBGSave() {
    Future<Void> res = comp.series(connection.bgSave());
    assertResult(res, null);
    done();
  }

  public void testBLPop() {
    Future<Integer> res1 = comp.series(connection.lPush(keyl1, val1));
    Future<Integer> res2 = comp.series(connection.lPush(keyl2, val2));
    Future<Buffer[]> res = comp.series(connection.bLPop(10, keyl1, keyl2));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res, new Buffer[]{keyl1, val1});
    done();
  }

  public void testBRPop() {
    Future<Integer> res1 = comp.series(connection.lPush(keyl1, val1));
    Future<Integer> res2 = comp.series(connection.lPush(keyl2, val2));
    Future<Buffer[]> res = comp.series(connection.bRPop(10, keyl1, keyl2));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res, new Buffer[]{keyl1, val1});
    done();
  }

  public void testBRPopLPush() {
    Future<Integer> res1 = comp.series(connection.lPush(keyl1, val1));
    Future<Buffer> res = comp.series(connection.bRPopLPush(keyl1, keyl2, 10));
    assertResult(res1, 1);
    assertResult(res, val1);
    done();
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

  public void testDBSize() {
    int num = 10;
    for (int i = 0; i < num; i++) {
      comp.parallel(connection.set(Buffer.create("key" + i), val1));
    }
    Future<Integer> res = comp.series(connection.dbSize());
    assertResult(res, num);
    done();
  }

  public void testDecr() {
    int num = 10;
    comp.series(connection.set(key1, Buffer.create(String.valueOf(num))));
    Future<Integer> res = comp.series(connection.decr(key1));
    assertResult(res, num - 1);
    done();
  }

  public void testDecrBy() {
    int num = 10;
    int decr = 4;
    comp.series(connection.set(key1, Buffer.create(String.valueOf(num))));
    Future<Integer> res = comp.series(connection.decrBy(key1, decr));
    assertResult(res, num - decr);
    done();
  }

  public void testDel() {
    comp.parallel(connection.set(key1, val1));
    comp.parallel(connection.set(key2, val2));
    comp.parallel(connection.set(key3, val3));
    Future<Integer> res = comp.series(connection.dbSize());
    assertResult(res, 3);
    comp.series(connection.del(key1, key2));
    res = comp.series(connection.dbSize());
    assertResult(res, 1);
    done();
  }

  public void testMultiDiscard() {
    Future<Void> res1 = comp.series(connection.multi());
    Future<Void> res2 = comp.series(connection.discard());
    assertResult(res1, null);
    assertResult(res2, null);
    done();
  }

  public void testEcho() {
    Buffer msg = Buffer.create("foo");
    Future<Buffer> res = comp.series(connection.echo(msg));
    assertResult(res, msg);
    done();
  }

  public void testTransactionExec() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Integer> res2 = comp.series(connection.incr(key1));
    Future<Integer> res3 = comp.parallel(connection.incr(key1));
    Future<Void> res4 = comp.parallel(connection.exec());
    assertResult(res4, null);
    assertResult(res2, 1);
    assertResult(res3, 2);
    done();
  }

  public void testTransactionWithMultiBulkExec() {
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
    done();
  }

  public void testTransactionMixed() {
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
    done();
  }

  public void testTransactionDiscard() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Integer> res2 = comp.series(connection.incr(key1));
    Future<Integer> res3 = comp.parallel(connection.incr(key1));
    Future<Void> res4 = comp.parallel(connection.discard());
    assertException(res2, "Transaction discarded");
    assertException(res3, "Transaction discarded");
    assertResult(res4, null);
    done();
  }

  public void testEmptyTransactionExec() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Void> res2 = comp.parallel(connection.exec());
    assertResult(res2, null);
    done();
  }

  public void testEmptyTransactionDiscard() {
    comp.series(connection.set(key1, Buffer.create(String.valueOf(0))));
    Future<Void> res1 = comp.series(connection.multi());
    assertResult(res1, null);
    Future<Void> res2 = comp.parallel(connection.discard());
    assertResult(res2, null);
    done();
  }

  private void pubsub(boolean patterns) {
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
    done();
  }

  public void testPubSub() {
    pubsub(false);
  }

  public void testPubSubPatterns() {
    pubsub(true);
  }

  public void testPubSubOnlySubscribe() {
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
    done();
  }

  public void testPubSubSubscribeMultiple() {
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
    done();
  }

  public void testPooling1() {
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

        tu.azzert(expected == tot);
      }
    });
    done();
  }

  public void testPooling2() {
    RedisPool pool = new RedisPool().setMaxPoolSize(10);

    int numConnections = 100;

    for (int i = 0; i < numConnections; i++) {
      // Get connection and return immediately
      final RedisConnection conn = pool.connection();
      comp.parallel(conn.closeDeferred());
    }
    done();
  }

  public void testExists() {
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    comp.series(connection.set(key1, val1));
    Future<Boolean> res2 = comp.series(connection.exists(key1));
    assertResult(res1, false);
    assertResult(res2, true);
    done();
  }

  public void testExpire() {
    comp.series(connection.set(key1, val1));
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    assertResult(res1, true);
    comp.series(connection.expire(key1, 0));
    Future<Boolean> res2 = comp.series(connection.exists(key1));
    assertResult(res2, false);
    done();
  }

  public void testExpireAt() {
    comp.series(connection.set(key1, val1));
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    assertResult(res1, true);
    comp.series(connection.expireAt(key1, (int) (System.currentTimeMillis() / 1000)));
    Future<Boolean> res2 = comp.series(connection.exists(key1));
    assertResult(res2, false);
    done();
  }

  public void testFlushAll() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.flushAll());
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    assertResult(res1, false);
    done();
  }

  public void testFlushDB() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.flushDB());
    Future<Boolean> res1 = comp.series(connection.exists(key1));
    assertResult(res1, false);
    done();
  }

  public void testGet() {
    comp.series(connection.set(key1, val1));
    Future<Buffer> res = comp.series(connection.get(key1));
    assertResult(res, val1);
    done();
  }

  public void testSetGetBit() {
    comp.series(connection.setBit(key1, 10, 1));
    Future<Integer> res = comp.series(connection.getBit(key1, 10));
    assertResult(res, 1);
    done();
  }

  public void testGetRange() {
    comp.series(connection.set(key1, val1));
    Future<Buffer> res = comp.series(connection.getRange(key1, 0, 1));
    assertResult(res, val1.copy(0, 2));
    done();
  }

  public void testGetSet() {
    comp.series(connection.set(key1, val1));
    Future<Buffer> res = comp.series(connection.getSet(key1, val2));
    assertResult(res, val1);
    done();
  }

  public void testHdel() {
    comp.series(connection.hSet(key1, field1, val1));
    Future<Integer> res1 = comp.series(connection.hDel(key1, field1));
    assertResult(res1, 1);
    done();
  }

  public void testHExists() {
    comp.series(connection.hSet(key1, field1, val1));
    Future<Boolean> res1 = comp.series(connection.hExists(key1, field1));
    Future<Boolean> res2 = comp.series(connection.hExists(key1, field2));
    assertResult(res1, true);
    assertResult(res2, false);
    done();
  }

  public void testHGet() {
    comp.series(connection.hSet(key1, field1, val1));
    Future<Buffer> res1 = comp.series(connection.hGet(key1, field1));
    Future<Buffer> res2 = comp.series(connection.hGet(key1, field2));
    assertResult(res1, val1);
    assertResult(res2, null);
    done();
  }

  public void testHGetAll() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res1 = comp.series(connection.hGetAll(key1));
    assertResult(res1, new Buffer[]{field1, val1, field2, val2});
    done();
  }

  public void testHIncrBy() {
    comp.series(connection.hSet(key1, field1, buffFromInt(5)));
    Future<Integer> res1 = comp.series(connection.hIncrBy(key1, field1, 2));
    Future<Integer> res2 = comp.series(connection.hIncrBy(key1, field1, -1));
    assertResult(res1, 7);
    assertResult(res2, 6);
    done();
  }

  public void testHKeys() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res1 = comp.series(connection.hKeys(key1));
    assertResult(res1, new Buffer[]{field1, field2});
    done();
  }

  public void testHLen() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Integer> res1 = comp.series(connection.hLen(key1));
    assertResult(res1, 2);
    done();
  }

  public void testHMGet() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res1 = comp.series(connection.hmGet(key1, field1, field2, field3));
    assertResult(res1, new Buffer[]{val1, val2, null});
    done();
  }

  public void testHMSet() {
    Map<Buffer, Buffer> map = new HashMap<>();
    map.put(field1, val1);
    map.put(field2, val2);
    Future<Void> res1 = comp.series(connection.hmSet(key1, map));
    Future<Buffer> res2 = comp.series(connection.hGet(key1, field1));
    Future<Buffer> res3 = comp.series(connection.hGet(key1, field2));
    assertResult(res1, null);
    assertResult(res2, val1);
    assertResult(res3, val2);
    done();
  }

  public void testHSet() {
    Future<Boolean> res1 = comp.series(connection.hSet(key1, field1, val1));
    Future<Buffer> res2 = comp.series(connection.hGet(key1, field1));
    assertResult(res1, true);
    assertResult(res2, val1);
    done();
  }

  public void testHSetNX() {
    Future<Boolean> res1 = comp.series(connection.hSetNx(key1, field1, val1));
    Future<Boolean> res2 = comp.series(connection.hSetNx(key1, field1, val2));
    Future<Buffer> res3 = comp.series(connection.hGet(key1, field1));
    assertResult(res1, true);
    assertResult(res2, false);
    assertResult(res3, val1);
    done();
  }

  public void testHVals() {
    comp.series(connection.hSet(key1, field1, val1));
    comp.series(connection.hSet(key1, field2, val2));
    Future<Buffer[]> res1 = comp.series(connection.hVals(key1));
    assertResult(res1, new Buffer[]{val1, val2});
    done();
  }

  public void testIncr() {
    comp.series(connection.set(key1, buffFromInt(5)));
    Future<Integer> res1 = comp.series(connection.incr(key1));
    assertResult(res1, 6);
    done();
  }

  public void testIncrBy() {
    comp.series(connection.set(key1, buffFromInt(5)));
    Future<Integer> res1 = comp.series(connection.incrBy(key1, 4));
    assertResult(res1, 9);
    done();
  }

  public void testInfo() {
    final Future<Buffer> res = comp.series(connection.info());
    comp.series(new SimpleAction() {
      public void act() {
        tu.azzert(res.result() != null);
        //TODO could check all the fields returned
      }
    });
    done();
  }

  public void testKeys() {
    Map<Buffer, Buffer> map = new HashMap<>();
    map.put(key1, val1);
    map.put(key2, val2);
    map.put(key3, val3);
    map.put(key4, val4);
    map.put(Buffer.create("otherkey"), val5);
    comp.series(connection.mset(map));
    final Future<Buffer[]> res = comp.series(connection.keys(Buffer.create("key*")));
    assertSameElements(res, new Buffer[]{key1, key2, key3, key4});
    done();
  }

  public void testLastSave() {
    final Future<Integer> res = comp.series(connection.lastSave());
    comp.series(new SimpleAction() {
      public void act() {
        tu.azzert(res.result() <= System.currentTimeMillis() / 1000);
      }
    });
    done();
  }

  public void testLIndex() {
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
    done();
  }

  public void testLInsert() {
    Future<Integer> res1 = comp.series(connection.rPush(key1, val1));
    Future<Integer> res2 = comp.series(connection.rPush(key1, val2));
    Future<Integer> res3 = comp.series(connection.lInsert(key1, true, val2, val3));
    Future<Buffer[]> res4 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res1, 1);
    assertResult(res2, 2);
    assertResult(res3, 3);
    assertResult(res4, new Buffer[]{val1, val3, val2});
    done();
  }

  public void testLLen() {
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPush(key1, val2));
    Future<Integer> res = comp.series(connection.lLen(key1));
    assertResult(res, 2);
    done();
  }

  public void testLPop() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Buffer> res = comp.series(connection.lPop(key1));
    assertResult(res, val1);
    done();
  }

  public void testLPush() {
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPush(key1, val2));
    comp.series(connection.lPush(key1, val3));
    Future<Buffer[]> res = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res, new Buffer[]{val3, val2, val1});
    done();
  }

  public void testLPushX() {
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPushX(key1, val2));
    comp.series(connection.lPushX(key1, val3));
    comp.series(connection.lPushX(key2, val1));
    Future<Buffer[]> res1 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res1, new Buffer[]{val3, val2, val1});
    Future<Buffer[]> res2 = comp.series(connection.lRange(key2, 0, -1));
    assertResult(res2, new Buffer[]{});
    done();
  }

  public void testLRange() {
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPush(key1, val2));
    comp.series(connection.lPush(key1, val3));
    Future<Buffer[]> res = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res, new Buffer[]{val3, val2, val1});
    done();
  }

  public void testLRem() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val1));
    Future<Integer> res1 = comp.series(connection.lRem(key1, -2, val1));
    Future<Buffer[]> res2 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res2, new Buffer[]{val1, val2});
    done();
  }

  public void testLSet() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Void> res1 = comp.series(connection.lSet(key1, 0, val4));
    Future<Buffer[]> res2 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res1, null);
    assertResult(res2, new Buffer[]{val4, val2, val3});
    done();
  }

  public void testLTrim() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Void> res1 = comp.series(connection.lTrim(key1, 1, -1));
    Future<Buffer[]> res2 = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res1, null);
    assertResult(res2, new Buffer[]{val2, val3});
    done();
  }

  public void testMGet() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.set(key2, val2));
    comp.series(connection.set(key3, val3));
    Future<Buffer[]> res2 = comp.series(connection.mget(key1, key2));
    assertResult(res2, new Buffer[]{val1, val2});
    done();
  }

//  public void testMove() {
//    comp.series(connection.set(key1, val1));
//    Future<Boolean> res1 = comp.series(connection.move(key1, Buffer.create("somedb")));
//    assertResult(res1, true);
//  }

  public void testMSet() {
    Map<Buffer, Buffer> map = new HashMap<>();
    map.put(key1, val1);
    map.put(key2, val2);
    Future<Void> res1 = comp.series(connection.mset(map));
    Future<Buffer> res2 = comp.series(connection.get(key1));
    Future<Buffer> res3 = comp.series(connection.get(key2));
    assertResult(res1, null);
    assertResult(res2, val1);
    assertResult(res3, val2);
    done();
  }

  public void testMSetNx() {
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
    done();
  }

  public void testPersist() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.expire(key1, 10));
    Future<Boolean> res1 = comp.series(connection.persist(key1));
    Future<Boolean> res2 = comp.series(connection.exists(key1));
    assertResult(res1, true);
    assertResult(res2, true);
    done();
  }

  public void testPing() {
    Future<Void> res = comp.series(connection.ping());
    assertResult(res, null);
    done();
  }

  public void testRandomKey() {
    comp.series(connection.set(key1, val1));
    Future<Buffer> res = comp.series(connection.randomKey());
    assertResult(res, key1);
    done();
  }

  public void testRename() {
    comp.series(connection.set(key1, val1));
    Future<Void> res1 = comp.series(connection.rename(key1, key2));
    Future<Buffer> res2 = comp.series(connection.get(key2));
    assertResult(res1, null);
    assertResult(res2, val1);
    done();
  }

  public void testRenameNx() {
    comp.series(connection.set(key1, val1));
    comp.series(connection.set(key2, val2));
    Future<Boolean> res1 = comp.series(connection.renameNX(key1, key2));
    Future<Buffer> res2 = comp.series(connection.get(key2));
    assertResult(res1, false);
    assertResult(res2, val2);
    done();
  }

  public void testRPop() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Buffer> res = comp.series(connection.rPoplPush(key1, key2));
    assertResult(res, val3);
    done();
  }

  public void testRPopLPush() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    comp.series(connection.rPush(key1, val3));
    Future<Buffer> res = comp.series(connection.rPop(key1));
    assertResult(res, val3);
    done();
  }

  public void testRPush() {
    comp.series(connection.rPush(key1, val1));
    comp.series(connection.rPush(key1, val2));
    Future<Buffer[]> res = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res, new Buffer[]{val1, val2});
    done();
  }

  public void testRPushX() {
    comp.series(connection.rPushX(key1, val1));
    comp.series(connection.rPushX(key1, val2));
    Future<Buffer[]> res = comp.series(connection.lRange(key1, 0, -1));
    assertResult(res, new Buffer[]{});
    done();
  }

  public void testSAdd() {
    Future<Integer> res1 = comp.series(connection.sAdd(key1, val1));
    Future<Integer> res2 = comp.series(connection.sAdd(key1, val2));
    Future<Integer> res3 = comp.series(connection.sAdd(key1, val2));
    Future<Buffer[]> res4 = comp.series(connection.sMembers(key1));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res3, 0);
    assertSameElements(res4, new Buffer[]{val1, val2});
    done();
  }

  public void testSave() {
    Future<Void> res = comp.series(connection.save());
    assertResult(res, null);
    done();
  }

  public void testSCard() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    Future<Integer> res = comp.series(connection.sCard(key1));
    assertResult(res, 2);
    done();
  }

  public void testSDiff() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Buffer[]> res = comp.series(connection.sDiff(key1, key2));
    assertSameElements(res, new Buffer[]{val1});
    done();
  }

  public void testSDiffStore() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Integer> res1 = comp.series(connection.sDiffStore(key3, key1, key2));
    assertResult(res1, 1);
    done();
  }

  public void testSelect() {
    Future<Void> res = comp.series(connection.select(1));
    assertResult(res, null);
    done();
  }

  public void testSet() {
    Future<Void> res = comp.series(connection.set(key1, val1));
    assertResult(res, null);
    assertKey(key1, val1);
    done();
  }

  public void testSetEx() {
    Future<Void> res1 = comp.series(connection.setEx(key1, 1, val1));
    assertResult(res1, null);
    Future<Buffer> res2 = comp.series(connection.get(key1));
    assertResult(res2, val1);
    done();
  }

  public void testSetNx() {
    Future<Boolean> res1 = comp.series(connection.setNx(key1, val1));
    Future<Boolean> res2 = comp.series(connection.setNx(key1, val2));
    assertResult(res1, true);
    assertResult(res2, false);
    Future<Buffer> res3 = comp.series(connection.get(key1));
    assertResult(res3, val1);
    done();
  }

  public void testSetRange() {
    comp.series(connection.set(key1, Buffer.create("Hello world")));
    Future<Integer> res1 = comp.series(connection.setRange(key1, 6, Buffer.create("aardvarks")));
    assertResult(res1, 15);
    Future<Buffer> res2 = comp.series(connection.get(key1));
    assertResult(res2, Buffer.create("Hello aardvarks"));
    done();
  }

  public void testSInter() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Buffer[]> res = comp.series(connection.sInter(key1, key2));
    assertSameElements(res, new Buffer[]{val2, val3});
    done();
  }

  public void testSInterStore() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Integer> res = comp.series(connection.sInterStore(key3, key1, key2));
    assertResult(res, 2);
    done();
  }

  public void testSIsMember() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    Future<Boolean> res1 = comp.series(connection.sIsMember(key1, val1));
    Future<Boolean> res2 = comp.series(connection.sIsMember(key1, val4));
    assertResult(res1, true);
    assertResult(res2, false);
    done();
  }

  public void testSMembers() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    Future<Buffer[]> res = comp.series(connection.sMembers(key1));
    assertSameElements(res, new Buffer[]{val1, val2, val3});
    done();
  }

  public void testSMove() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key2, val3));
    Future<Boolean> res1 = comp.series(connection.sMove(key1, key2, val1));
    Future<Buffer[]> res2 = comp.series(connection.sMembers(key2));
    assertResult(res1, true);
    assertSameElements(res2, new Buffer[]{val1, val3});
    done();
  }

  public void testSortAscending() {
    comp.series(connection.lPush(key1, buffFromInt(3)));
    comp.series(connection.lPush(key1, buffFromInt(1)));
    comp.series(connection.lPush(key1, buffFromInt(2)));
    Future<Buffer[]> res = comp.series(connection.sort(key1));
    assertResult(res, new Buffer[]{buffFromInt(1), buffFromInt(2), buffFromInt(3)});
    done();
  }

  public void testSortDescending() {
    comp.series(connection.lPush(key1, buffFromInt(3)));
    comp.series(connection.lPush(key1, buffFromInt(1)));
    comp.series(connection.lPush(key1, buffFromInt(2)));
    Future<Buffer[]> res = comp.series(connection.sort(key1, false));
    assertResult(res, new Buffer[]{buffFromInt(3), buffFromInt(2), buffFromInt(1)});
    done();
  }

  public void testSortAlpha() {
    comp.series(connection.lPush(key1, val3));
    comp.series(connection.lPush(key1, val1));
    comp.series(connection.lPush(key1, val2));
    Future<Buffer[]> res = comp.series(connection.sort(key1, true, true));
    assertResult(res, new Buffer[]{val1, val2, val3});
    done();
  }

  public void testSortLimit() {
    comp.series(connection.lPush(key1, buffFromInt(3)));
    comp.series(connection.lPush(key1, buffFromInt(1)));
    comp.series(connection.lPush(key1, buffFromInt(2)));
    Future<Buffer[]> res = comp.series(connection.sort(key1, 1, 1, true, false));
    assertResult(res, new Buffer[]{buffFromInt(2)});
    done();
  }

  public void testSPop() {
    comp.series(connection.sAdd(key1, val1));
    Future<Buffer> res1 = comp.series(connection.sPop(key1));
    assertResult(res1, val1);
    Future<Buffer[]> res2 = comp.series(connection.sMembers(key1));
    assertSameElements(res2, new Buffer[]{});
    done();
  }

  public void testSRandMember() {
    comp.series(connection.sAdd(key1, val1));
    Future<Buffer> res1 = comp.series(connection.sRandMember(key1));
    assertResult(res1, val1);
    Future<Buffer[]> res2 = comp.series(connection.sMembers(key1));
    assertSameElements(res2, new Buffer[]{val1});
    done();
  }

  public void testSRem() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    Future<Integer> res1 = comp.series(connection.sRem(key1, val1));
    Future<Buffer[]> res2 = comp.series(connection.sMembers(key1));
    assertResult(res1, 1);
    assertSameElements(res2, new Buffer[]{val2, val3});
    done();
  }

  public void testStrLen() {
    comp.series(connection.set(key1, val1));
    Future<Integer> res = comp.series(connection.strLen(key1));
    assertResult(res, val1.length());
    done();
  }

  public void testSUnion() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Buffer[]> res = comp.series(connection.sUnion(key1, key2));
    assertSameElements(res, new Buffer[]{val1, val2, val3, val4});
    done();
  }

  public void testSUnionStore() {
    comp.series(connection.sAdd(key1, val1));
    comp.series(connection.sAdd(key1, val2));
    comp.series(connection.sAdd(key1, val3));
    comp.series(connection.sAdd(key2, val2));
    comp.series(connection.sAdd(key2, val3));
    comp.series(connection.sAdd(key2, val4));
    Future<Integer> res = comp.series(connection.sUnionStore(key3, key1, key2));
    assertResult(res, 4);
    done();
  }

  public void testTTL() {
    comp.series(connection.setEx(key1, 10, val1));
    final Future<Integer> res = comp.series(connection.ttl(key1));
    comp.series(new SimpleAction() {
      public void act() {
        tu.azzert(res.result() <= 10);
      }
    });
    done();
  }

  public void testType() {
    comp.series(connection.set(key1, val1));
    Future<String> res = comp.series(connection.type(key1));
    assertResult(res, "string");
    done();
  }

  public void testWatchUnWatch() {
    Future<Void> res1 = comp.parallel(connection.watch(key1));
    Future<Void> res2 = comp.parallel(connection.unwatch());
    assertResult(res1, null);
    assertResult(res2, null);
    done();
  }

  public void testZAdd() {
    Future<Integer> res1 = comp.series(connection.zAdd(key1, 1.0, val1));
    Future<Integer> res2 = comp.series(connection.zAdd(key1, 2.0, val2));
    Future<Integer> res3 = comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res4 = comp.series(connection.zRange(key1, 0, -1, true));
    assertResult(res1, 1);
    assertResult(res2, 1);
    assertResult(res3, 1);
    assertResult(res4, new Buffer[]{val1, buffFromInt(1), val2, buffFromInt(2), val3, buffFromInt(3)});
    done();
  }

  public void testZCard() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res4 = comp.series(connection.zCard(key1));
    assertResult(res4, 3);
    done();
  }

  public void testZCount() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res4 = comp.series(connection.zCount(key1, 2.0, 3.0));
    assertResult(res4, 2);
    done();
  }

  public void testZIncrBy() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    Future<Double> res1 = comp.series(connection.zIncrBy(key1, 2.5, val1));
    Future<Buffer[]> res2 = comp.series(connection.zRange(key1, 0, -1, true));
    assertResult(res1, 3.5);
    assertSameElements(res2, new Buffer[]{val1, buffFromDouble(3.5), val2, buffFromInt(2)});
    done();
  }

  public void testZInterStore() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key2, 1.0, val1));
    comp.series(connection.zAdd(key2, 2.0, val2));
    comp.series(connection.zAdd(key2, 3.0, val3));
    Future<Integer> res = comp.series(connection.zInterStore(key3, 2, new Buffer[]{key1, key2}, new double[]{2.0, 3.0}, RedisConnection.AggregateType.SUM));
    assertResult(res, 2);
    Future<Buffer[]> res2 = comp.series(connection.zRange(key3, 0, -1, true));
    assertSameElements(res2, new Buffer[]{val1, buffFromInt(5), val2, buffFromInt(10)});
    done();
  }

  public void testZRange() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res = comp.series(connection.zRange(key1, 0, -1, true));
    assertResult(res, new Buffer[]{val1, buffFromInt(1), val2, buffFromInt(2), val3, buffFromInt(3)});
    done();
  }

  public void testZRangeByScore() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res = comp.series(connection.zRangeByScore(key1, 2.0, 3.0, true, 0, -1));
    assertResult(res, new Buffer[]{val2, buffFromInt(2), val3, buffFromInt(3)});
    done();
  }

  public void testZRank() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res = comp.series(connection.zRank(key1, val2));
    assertResult(res, 1);
    done();
  }

  public void testZRem() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res1 = comp.series(connection.zRem(key1, val1));
    Future<Buffer[]> res2 = comp.series(connection.zRange(key1, 0, -1, false));
    assertResult(res1, 1);
    assertSameElements(res2, new Buffer[]{val2, val3});
    done();
  }

  public void testZRemRangeByRank() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res1 = comp.series(connection.zRemRangeByRank(key1, 0, 1));
    Future<Buffer[]> res2 = comp.series(connection.zRange(key1, 0, -1, false));
    assertResult(res1, 2);
    assertSameElements(res2, new Buffer[]{val3});
    done();
  }

  public void testZRevRange() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res = comp.series(connection.zRevRange(key1, 0, -1, true));
    assertResult(res, new Buffer[]{val3, buffFromInt(3), val2, buffFromInt(2), val1, buffFromInt(1)});
    done();
  }

  public void testZRevRangeByScore() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Buffer[]> res = comp.series(connection.zRevRangeByScore(key1, 3.0, 2.0, true, 0, -1));
    assertResult(res, new Buffer[]{val3, buffFromInt(3), val2, buffFromInt(2)});
    done();
  }

  public void testZRevRank() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key1, 3.0, val3));
    Future<Integer> res = comp.series(connection.zRevRank(key1, val3));
    assertResult(res, 0);
    done();
  }

  public void testZScore() {
    comp.series(connection.zAdd(key1, 1.5, val1));
    Future<Double> res = comp.series(connection.zScore(key1, val1));
    assertResult(res, 1.5);
    done();
  }

  public void testZUnionStore() {
    comp.series(connection.zAdd(key1, 1.0, val1));
    comp.series(connection.zAdd(key1, 2.0, val2));
    comp.series(connection.zAdd(key2, 1.0, val1));
    comp.series(connection.zAdd(key2, 2.0, val2));
    comp.series(connection.zAdd(key2, 3.0, val3));
    Future<Integer> res = comp.series(connection.zUnionStore(key3, 2, new Buffer[]{key1, key2}, new double[]{2.0, 3.0}, RedisConnection.AggregateType.SUM));
    assertResult(res, 3);
    Future<Buffer[]> res2 = comp.series(connection.zRange(key3, 0, -1, true));
    assertSameElements(res2, new Buffer[]{val1, buffFromInt(5), val2, buffFromInt(10), val3, buffFromInt(9)});
    done();
  }

  public void testTestWithPassword() {
    RedisPool pool2 = new RedisPool().setPassword("whatever");
    RedisConnection conn2 = pool2.connection();
    comp.series(conn2.ping());
    conn2.close();
    pool2.close();
    done();
  }

  private void setup() {
    comp = new Composer();
    pool = new RedisPool();
    connection = pool.connection();
    clearKeys();
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
        tu.azzert(future.failed());
        tu.azzert(future.exception() instanceof RedisException);
        tu.azzert(error.equals(future.exception().getMessage()), "Expected:" + error + " Actual:" + future.exception().getMessage());
      }
    });
  }

  private void assertResult(final Future<?> future, final Object value) {
    comp.series(new TestAction() {
      protected void doAct() {
        if (!future.succeeded()) {
          future.exception().printStackTrace();
        }
        tu.azzert(future.succeeded());
        Object res = future.result();
        if (res == null) {
          tu.azzert(value == null, "Expected: " + value + " Actual: " + res);
        } else {
          if (res instanceof Buffer) {
            tu.azzert(Utils.buffersEqual((Buffer) value, (Buffer) res), "Expected: " + value + " Actual: " + res);
          } else if (res instanceof Buffer[]) {
            Buffer[] mb = (Buffer[]) res;
            Buffer[] expected = (Buffer[]) value;
            for (int i = 0; i < expected.length; i++) {
              if (expected[i] != null) {
                tu.azzert(Utils.buffersEqual(expected[i], mb[i]), "Buffer not equal: " + expected[i] + " " + mb[i]);
              } else {
                tu.azzert(mb[i] == null);
              }
            }
          } else {
            tu.azzert(value.equals(future.result()), "Expected: " + value + " Actual: " + future.result());
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
        tu.azzert(future.succeeded());
        Buffer[] res = future.result();
        tu.azzert(value.length == res.length, "Expected length: " + value.length + " Actual length: " + res.length);
        for (int i = 0; i < value.length; i++) {
          Buffer b = value[i];
          boolean found = false;
          for (int j = 0; j < res.length; j++) {
            if (Utils.buffersEqual(b, res[j])) {
              found = true;
              break;
            }
          }
          tu.azzert(found);
        }
      }
    });
  }

  private void assertKey(Buffer key, final Buffer value) {
    final Future<Buffer> res = comp.series(connection.get(key));
    comp.series(new TestAction() {
      protected void doAct() {
        tu.azzert(Utils.buffersEqual(value, res.result()));
      }
    });
  }

  private void done() {
    comp.series(connection.closeDeferred());
    comp.series(new TestAction() {
      protected void doAct() {
        pool.close();
        tu.testComplete();
      }
    });
    comp.execute();
  }

  private abstract class TestAction extends SimpleAction {
    public void act() {
      try {
        doAct();
      } catch (Exception e) {
        e.printStackTrace();
        tu.azzert(false, e.getMessage());
      }
    }

    protected abstract void doAct();
  }


}
