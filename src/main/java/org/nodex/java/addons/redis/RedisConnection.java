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

package org.nodex.java.addons.redis;

import org.nodex.java.core.*;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Map;

/**
 * <p>Represents a connection to a Redis server.</p>
 *
 * <p>Instances of this class are obtained from {@link RedisPool#connection}. The actual TCP connections to Redis
 * are obtained lazily fron the pool when actions are executed. You can have many instances of RedisConnection
 * sharing a limited pool of actual TCP connections. Once you have done writing requests you should call {@link #close}
 * to return the underlying TCP connection to the pool. You do not have to wait for all your responses to return before calling
 * close. The class supports pipelining, i.e. you can safely send many requests in succession before any of the results have returned.</p>
 *
 * A couple of caveats:
 *
 * <ul>
 *   <li>If you have subscribed to any Redis channels, the connection will be put into subscription mode and you
 *   cannot send any commands apart from SUBSCRIBE and UNSUBSCRIBE, or close the connection, until you have unsubscribed from all channels.</li>
 *   <li>If you have started a transaction with MULTI you cannot close the connection until the transaction has been completed using
 *   EXEC or DISCARD.</li>
 * </ul>
 *
 * <p>Actions are returned as instances of {@link Deferred}. The actual actions won't be executed until the
 * {@link Deferred#execute} method is called. This allows multiple Deferred instances to be composed together
 * using the {@link org.nodex.java.core.composition.Composer} class.</p>
 *
 * <p>An example of using this class directly would be:</p>
 *
 * <pre>
 * final RedisConnection conn = pool.connection();
 * conn.set(Buffer.create("key1"), Buffer.create("value1").handler(new CompletionHandler() {
 *   public void handle(Future&lt;Void&gt; f) {
 *     System.out.println("The value has been successfully set");
 *     conn.close();
 *   }
 * }
 * </pre>
 *
 * <p>Or using a {@link org.nodex.java.core.composition.Composer}</p>
 *
 * <pre>
 * RedisConnection conn = pool.connection();
 * Composer comp = new Composer();
 * comp.parallel(conn.set(Buffer.create("key1"), Buffer.create("value1")));
 * comp.parallel(conn.set(Buffer.create("key2"), Buffer.create("value2")));
 * Future&lt;Buffer&gt; result1 = comp.series(conn.get(Buffer.create("key1")));
 * Future&lt;Buffer&gt; result2 = comp.parallel(conn.get(Buffer.create("key2")));
 * comp.series(new DeferredAction&lt;Void&gt;() {
 *   protected void run() {
 *     conn.set(Buffer.create("key3"), Buffer.create(result1.result + result2.result)).handler(new SimpleHandler() {
 *       public void handle() {
 *         DeferredAction.this.setResult(null);
 *       }
 *     }
 *   }
 * }
 * comp.parallel(conn.closeDeferred());
 * </pre>
 *
 * <p>For a full description of the various Redis commands, please see the <a href="http://redis.io/commands">Redis documentation</a>.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedisConnection {

  private static final Charset UTF8 = Charset.forName("UTF-8");

  // The commands
  private static final byte[] APPEND_COMMAND = "APPEND".getBytes(UTF8);
  private static final byte[] AUTH_COMMAND = "AUTH".getBytes(UTF8);
  private static final byte[] BGREWRITEAOF_COMMAND = "BGREWRITEAOF".getBytes(UTF8);
  private static final byte[] BGSAVE_COMMAND = "BGSAVE".getBytes(UTF8);
  private static final byte[] BLPOP_COMMAND = "BLPOP".getBytes(UTF8);
  private static final byte[] BRPOP_COMMAND = "BRPOP".getBytes(UTF8);
  private static final byte[] BRPOPLPUSH_COMMAND = "BRPOPLPUSH".getBytes(UTF8);
  private static final byte[] CONFIG_GET_COMMAND = "CONFIG GET".getBytes(UTF8);
  private static final byte[] CONFIG_SET_COMMAND = "CONFIG SET".getBytes(UTF8);
  private static final byte[] CONFIG_RESET_STAT_COMMAND = "CONFIG RESET STAT".getBytes(UTF8);
  private static final byte[] DB_SIZE_COMMAND = "DBSIZE".getBytes(UTF8);
  private static final byte[] DEBUG_OBJECT_COMMAND = "DEBUG OBJECT".getBytes(UTF8);
  private static final byte[] DEBUG_SEG_FAULT_COMMAND = "DEBUG SEGFAULT".getBytes(UTF8);
  private static final byte[] DECR_COMMAND = "DECR".getBytes(UTF8);
  private static final byte[] DECRBY_COMMAND = "DECRBY".getBytes(UTF8);
  private static final byte[] DEL_COMMAND = "DEL".getBytes(UTF8);
  private static final byte[] DISCARD_COMMAND = "DISCARD".getBytes(UTF8);
  private static final byte[] ECHO_COMMAND = "ECHO".getBytes(UTF8);
  private static final byte[] EXEC_COMMAND = "EXEC".getBytes(UTF8);
  private static final byte[] EXISTS_COMMAND = "EXISTS".getBytes(UTF8);
  private static final byte[] EXPIRE_COMMAND = "EXPIRE".getBytes(UTF8);
  private static final byte[] EXPIREAT_COMMAND = "EXPIREAT".getBytes(UTF8);
  private static final byte[] FLUSHALL_COMMAND = "FLUSHALL".getBytes(UTF8);
  private static final byte[] FLUSHDB_COMMAND = "FLUSHDB".getBytes(UTF8);
  private static final byte[] GET_COMMAND = "GET".getBytes(UTF8);
  private static final byte[] GETBIT_COMMAND = "GETBIT".getBytes(UTF8);
  private static final byte[] GETRANGE_COMMAND = "GETRANGE".getBytes(UTF8);
  private static final byte[] GETSET_COMMAND = "GETSET".getBytes(UTF8);
  private static final byte[] HDEL_COMMAND = "HDEL".getBytes(UTF8);
  private static final byte[] HEXISTS_COMMAND = "HEXISTS".getBytes(UTF8);
  private static final byte[] HGET_COMMAND = "HGET".getBytes(UTF8);
  private static final byte[] HGETALL_COMMAND = "HGETALL".getBytes(UTF8);
  private static final byte[] HINCRBY_COMMAND = "HINCRBY".getBytes(UTF8);
  private static final byte[] HKEYS_COMMAND = "HKEYS".getBytes(UTF8);
  private static final byte[] HLEN_COMMAND = "HLEN".getBytes(UTF8);
  private static final byte[] HMGET_COMMAND = "HMGET".getBytes(UTF8);
  private static final byte[] HMSET_COMMAND = "HMSET".getBytes(UTF8);
  private static final byte[] HSET_COMMAND = "HSET".getBytes(UTF8);
  private static final byte[] HSETNX_COMMAND = "HSETNX".getBytes(UTF8);
  private static final byte[] HVALS_COMMAND = "HVALS".getBytes(UTF8);
  private static final byte[] INCR_COMMAND = "INCR".getBytes(UTF8);
  private static final byte[] INCRBY_COMMAND = "INCRBY".getBytes(UTF8);
  private static final byte[] INFO_COMMAND = "INFO".getBytes(UTF8);
  private static final byte[] KEYS_COMMAND = "KEYS".getBytes(UTF8);
  private static final byte[] LASTSAVE_COMMAND = "LASTSAVE".getBytes(UTF8);
  private static final byte[] LINDEX_COMMAND = "LINDEX".getBytes(UTF8);
  private static final byte[] LINSERT_COMMAND = "LINSERT".getBytes(UTF8);
  private static final byte[] LLEN_COMMAND = "LLEN".getBytes(UTF8);
  private static final byte[] LPOP_COMMAND = "LPOP".getBytes(UTF8);
  private static final byte[] LPUSH_COMMAND = "LPUSH".getBytes(UTF8);
  private static final byte[] LPUSHX_COMMAND = "LPUSHX".getBytes(UTF8);
  private static final byte[] LRANGE_COMMAND = "LRANGE".getBytes(UTF8);
  private static final byte[] LREM_COMMAND = "LREM".getBytes(UTF8);
  private static final byte[] LSET_COMMAND = "LSET".getBytes(UTF8);
  private static final byte[] LTRIM_COMMAND = "LTRIM".getBytes(UTF8);
  private static final byte[] MGET_COMMAND = "MGET".getBytes(UTF8);
  private static final byte[] MOVE_COMMAND = "MOVE".getBytes(UTF8);
  private static final byte[] MSET_COMMAND = "MSET".getBytes(UTF8);
  private static final byte[] MSETNX_COMMAND = "MSETNX".getBytes(UTF8);
  private static final byte[] MULTI_COMMAND = "MULTI".getBytes(UTF8);
  private static final byte[] PERSIST_COMMAND = "PERSIST".getBytes(UTF8);
  private static final byte[] PING_COMMAND = "PING".getBytes(UTF8);
  private static final byte[] PSUBSCRIBE_COMMAND = "PSUBSCRIBE".getBytes(UTF8);
  private static final byte[] PUNSUBSCRIBE_COMMAND = "PUNSUBSCRIBE".getBytes(UTF8);
  private static final byte[] PUBLISH_COMMAND = "PUBLISH".getBytes(UTF8);
  private static final byte[] QUIT_COMMAND = "QUIT".getBytes(UTF8);
  private static final byte[] RANDOMKEY_COMMAND = "RANDOMKEY".getBytes(UTF8);
  private static final byte[] RENAME_COMMAND = "RENAME".getBytes(UTF8);
  private static final byte[] RENAMENX_COMMAND = "RENAMENX".getBytes(UTF8);
  private static final byte[] RPOP_COMMAND = "RPOP".getBytes(UTF8);
  private static final byte[] RPOPLPUSH_COMMAND = "RPOPLPUSH".getBytes(UTF8);
  private static final byte[] RPUSH_COMMAND = "RPUSH".getBytes(UTF8);
  private static final byte[] RPUSHX_COMMAND = "RPUSHX".getBytes(UTF8);
  private static final byte[] SADD_COMMAND = "SADD".getBytes(UTF8);
  private static final byte[] SAVE_COMMAND = "SAVE".getBytes(UTF8);
  private static final byte[] SCARD_COMMAND = "SCARD".getBytes(UTF8);
  private static final byte[] SDIFF_COMMAND = "SDIFF".getBytes(UTF8);
  private static final byte[] SDIFFSTORE_COMMAND = "SDIFFSTORE".getBytes(UTF8);
  private static final byte[] SELECT_COMMAND = "SELECT".getBytes(UTF8);
  private static final byte[] SET_COMMAND = "SET".getBytes(UTF8);
  private static final byte[] SETBIT_COMMAND = "SETBIT".getBytes(UTF8);
  private static final byte[] SETEX_COMMAND = "SETEX".getBytes(UTF8);
  private static final byte[] SETNX_COMMAND = "SETNX".getBytes(UTF8);
  private static final byte[] SETRANGE_COMMAND = "SETRANGE".getBytes(UTF8);
  private static final byte[] SHUTDOWN_COMMAND = "SHUTDOWN".getBytes(UTF8);
  private static final byte[] SINTER_COMMAND = "SINTER".getBytes(UTF8);
  private static final byte[] SINTERSTORE_COMMAND = "SINTERSTORE".getBytes(UTF8);
  private static final byte[] SISMEMBER_COMMAND = "SISMEMBER".getBytes(UTF8);
  private static final byte[] SLAVEOF_COMMAND = "SLAVEOF".getBytes(UTF8);
  private static final byte[] SMEMBERS_COMMAND = "SMEMBERS".getBytes(UTF8);
  private static final byte[] SMOVE_COMMAND = "SMOVE".getBytes(UTF8);
  private static final byte[] SORT_COMMAND = "SORT".getBytes(UTF8);
  private static final byte[] SPOP_COMMAND = "SPOP".getBytes(UTF8);
  private static final byte[] SRANDMEMBER_COMMAND = "SRANDMEMBER".getBytes(UTF8);
  private static final byte[] SREM_COMMAND = "SREM".getBytes(UTF8);
  private static final byte[] STRLEN_COMMAND = "STRLEN".getBytes(UTF8);
  private static final byte[] SUBSCRIBE_COMMAND = "SUBSCRIBE".getBytes(UTF8);
  private static final byte[] SUNION_COMMAND = "SUNION".getBytes(UTF8);
  private static final byte[] SUNIONSTORE_COMMAND = "SUNIONSTORE".getBytes(UTF8);
  private static final byte[] TTL_COMMAND = "TTL".getBytes(UTF8);
  private static final byte[] TYPE_COMMAND = "TYPE".getBytes(UTF8);
  private static final byte[] UNSUBSCRIBE_COMMAND = "UNSUBSCRIBE".getBytes(UTF8);
  private static final byte[] UNWATCH_COMMAND = "UNWATCH".getBytes(UTF8);
  private static final byte[] WATCH_COMMAND = "WATCH".getBytes(UTF8);
  private static final byte[] ZADD_COMMAND = "ZADD".getBytes(UTF8);
  private static final byte[] ZCARD_COMMAND = "ZCARD".getBytes(UTF8);
  private static final byte[] ZCOUNT_COMMAND = "ZCOUNT".getBytes(UTF8);
  private static final byte[] ZINCRBY_COMMAND = "ZINCRBY".getBytes(UTF8);
  private static final byte[] ZINTERSTORE_COMMAND = "ZINTERSTORE".getBytes(UTF8);
  private static final byte[] ZRANGE_COMMAND = "ZRANGE".getBytes(UTF8);
  private static final byte[] ZRANGEBYSCORE_COMMAND = "ZRANGEBYSCORE".getBytes(UTF8);
  private static final byte[] ZRANK_COMMAND = "ZRANK".getBytes(UTF8);
  private static final byte[] ZREM_COMMAND = "ZREM".getBytes(UTF8);
  private static final byte[] ZREMRANGEBYRANK_COMMAND = "ZREMRANGEBYRANK".getBytes(UTF8);
  private static final byte[] ZREMRANGEBYSCORE_COMMAND = "ZREMRANGEBYSCORE".getBytes(UTF8);
  private static final byte[] ZREVRANGE_COMMAND = "ZREVRANGE".getBytes(UTF8);
  private static final byte[] ZREVRANGEBYSCORE_COMMAND = "ZREVRANGEBYSCORE".getBytes(UTF8);
  private static final byte[] ZREVRANK_COMMAND = "ZREVRANK".getBytes(UTF8);
  private static final byte[] ZSCORE_COMMAND = "ZSCORE".getBytes(UTF8);
  private static final byte[] ZUNIONSTORE_COMMAND = "ZUNIONSTORE".getBytes(UTF8);

  // Various keywords used in commands
  private static final byte[] INSERT_BEFORE = "BEFORE".getBytes(UTF8);
  private static final byte[] INSERT_AFTER = "AFTER".getBytes(UTF8);
  private static final byte[] LIMIT = "LIMIT".getBytes(UTF8);
  private static final byte[] SORT_BY = "BY".getBytes(UTF8);
  private static final byte[] SORT_GET = "GET".getBytes(UTF8);
  private static final byte[] SORT_DESC = "DESC".getBytes(UTF8);
  private static final byte[] SORT_ALPHA = "ALPHA".getBytes(UTF8);
  private static final byte[] SORT_STORE = "STORE".getBytes(UTF8);
  private static final byte[] WEIGHTS = "WEIGHTS".getBytes(UTF8);
  private static final byte[] AGGREGRATE = "AGGREGATE".getBytes(UTF8);
  private static final byte[] WITHSCORES = "WITHSCORES".getBytes(UTF8);

  private final ConnectionPool<InternalConnection> pool;
  private final String password;
  InternalConnection conn;
  private final LinkedList<RedisDeferred<?>> pending = new LinkedList<>();
  private Handler<Buffer> subscriberHandler;
  private DeferredAction<Void> closeDeferred;
  private boolean connectionRequested;

  /**
   * Create a new RedisClient
   */
  RedisConnection(ConnectionPool<InternalConnection> pool, String password) {
    this.pool = pool;
    this.password = password;
  }

  /**
   * Close the connection asynchronously.<p>
   * This method must be called using the same event loop the connection was opened from.
   * @return a Future representing the future result of closing the file.
   */
  public Future<Void> close() {
    return closeDeferred().execute();
  }

  /**
   * The same as {@link #close} but the close does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> closeDeferred() {
    return new DeferredAction<Void>() {
      public void run() {
        if (conn != null) {
          conn.close(this);
          conn = null;
        } else if (pending.isEmpty()){
          //No internal connection and never going to get one
          setResult(null);
        } else {
          closeDeferred = this;
        }
      }
    };
  }

  /**
   * Set a handler which will receive messages when the connection is in subscribe mode.
   * @param handler
   */
  public void subscriberHandler(Handler<Buffer> handler) {
    if (conn != null) {
      conn.subscriberHandler = handler;
    } else {
      subscriberHandler = handler;
    }
  }

  void addToPending(RedisDeferred<?> deferred) {
    getConnection();
    pending.add(deferred);
  }

  public Deferred<Integer> append(Buffer key, Buffer value) {
    return createIntegerDeferred(APPEND_COMMAND, key, value);
  }

  private Deferred<Void> auth(Buffer password) {
    return createVoidDeferred(AUTH_COMMAND, password);
  }

  public Deferred<Void> bgRewriteAOF() {
    return createVoidDeferred(BGREWRITEAOF_COMMAND);
  }

  public Deferred<Void> bgSave() {
    return createVoidDeferred(BGSAVE_COMMAND);
  }

  public Deferred<Buffer[]> bLPop(int timeout, Buffer... keys) {
    Buffer[] args = new Buffer[keys.length + 1];
    System.arraycopy(keys, 0, args, 0, keys.length);
    args[args.length - 1] = intToBuffer(timeout);
    return createMultiBulkDeferred(BLPOP_COMMAND, args);
  }

  public Deferred<Buffer[]> bRPop(int timeout, Buffer... keys) {
    return createMultiBulkDeferred(BRPOP_COMMAND, toBufferArray(keys, intToBuffer(timeout)));
  }

  public Deferred<Buffer> bRPopLPush(Buffer source, Buffer destination, int timeout) {
    return createBulkDeferred(BRPOPLPUSH_COMMAND, source, destination, intToBuffer(timeout));
  }

  public Deferred<Buffer> configGet(Buffer parameter) {
    return createBulkDeferred(CONFIG_GET_COMMAND, parameter);
  }

  public Deferred<Void> configSet(Buffer parameter, Buffer value) {
    return createVoidDeferred(CONFIG_SET_COMMAND, parameter, value);
  }

  public Deferred<Integer> dbSize() {
    return createIntegerDeferred(DB_SIZE_COMMAND);
  }

  public Deferred<Integer> decr(Buffer key) {
    return createIntegerDeferred(DECR_COMMAND, key);
  }

  public Deferred<Integer> decrBy(Buffer key, int decrement) {
    return createIntegerDeferred(DECRBY_COMMAND, key, intToBuffer(decrement));
  }

  public Deferred<Integer> del(Buffer... keys) {
    return createIntegerDeferred(DEL_COMMAND, keys);
  }

  public Deferred<Void> discard() {
    RedisDeferred<Void> deferred = createVoidDeferred(DISCARD_COMMAND);
    deferred.commandType = RedisDeferred.TxCommandType.DISCARD;
    return deferred;
  }

  public Deferred<Buffer> echo(Buffer message) {
    return createBulkDeferred(ECHO_COMMAND, message);
  }

  public Deferred<Void> exec() {
    RedisDeferred<Void> deferred = createVoidDeferred(EXEC_COMMAND);
    deferred.commandType = RedisDeferred.TxCommandType.EXEC;
    return deferred;
  }

  public Deferred<Boolean> exists(Buffer key) {
    return createBooleanDeferred(EXISTS_COMMAND, key);
  }

  public Deferred<Boolean> expire(Buffer key, int seconds) {
    return createBooleanDeferred(EXPIRE_COMMAND, key, intToBuffer(seconds));
  }

  public Deferred<Boolean> expireAt(Buffer key, int timestamp) {
    return createBooleanDeferred(EXPIREAT_COMMAND, key, intToBuffer(timestamp));
  }

  public Deferred<Void> flushAll() {
    return createVoidDeferred(FLUSHALL_COMMAND);
  }

  public Deferred<Void> flushDB() {
    return createVoidDeferred(FLUSHDB_COMMAND);
  }

  public Deferred<Buffer> get(Buffer key) {
    return createBulkDeferred(GET_COMMAND, key);
  }

  public Deferred<Integer> getBit(Buffer key, int offset) {
    return createIntegerDeferred(GETBIT_COMMAND, key, intToBuffer(offset));
  }

  public Deferred<Buffer> getRange(Buffer key, int start, int end) {
    return createBulkDeferred(GETRANGE_COMMAND, key, intToBuffer(start), intToBuffer(end));
  }

  public Deferred<Buffer> getSet(Buffer key, Buffer value) {
    return createBulkDeferred(GETSET_COMMAND, key, value);
  }

  public Deferred<Integer> hDel(Buffer key, Buffer... fields) {
    return createIntegerDeferred(HDEL_COMMAND, toBufferArray(key, fields));
  }

  public Deferred<Boolean> hExists(Buffer key, Buffer field) {
    return createBooleanDeferred(HEXISTS_COMMAND, key, field);
  }

  public Deferred<Buffer> hGet(Buffer key, Buffer field) {
    return createBulkDeferred(HGET_COMMAND, key, field);
  }

  public Deferred<Buffer[]> hGetAll(Buffer key) {
    return createMultiBulkDeferred(HGETALL_COMMAND, key);
  }

  public Deferred<Integer> hIncrBy(Buffer key, Buffer field, int increment) {
    return createIntegerDeferred(HINCRBY_COMMAND, key, field, intToBuffer(increment));
  }

  public Deferred<Buffer[]> hKeys(Buffer key) {
    return createMultiBulkDeferred(HKEYS_COMMAND, key);
  }

  public Deferred<Integer> hLen(Buffer key) {
    return createIntegerDeferred(HLEN_COMMAND, key);
  }

  public Deferred<Buffer[]> hmGet(Buffer key, Buffer... fields) {
    return createMultiBulkDeferred(HMGET_COMMAND, toBufferArray(key, fields));
  }

  public Deferred<Void> hmSet(Buffer key, Map<Buffer, Buffer> map) {
    return createVoidDeferred(HMSET_COMMAND, toBufferArray(key, toBufferArray(map)));
  }

  public Deferred<Boolean> hSet(Buffer key, Buffer field, Buffer value) {
    return createBooleanDeferred(HSET_COMMAND, key, field, value);
  }

  public Deferred<Boolean> hSetNx(Buffer key, Buffer field, Buffer value) {
    return createBooleanDeferred(HSETNX_COMMAND, key, field, value);
  }

  public Deferred<Buffer[]> hVals(Buffer key) {
    return createMultiBulkDeferred(HVALS_COMMAND, key);
  }

  public Deferred<Integer> incr(Buffer key) {
    return createIntegerDeferred(INCR_COMMAND, key);
  }

  public Deferred<Integer> incrBy(Buffer key, int increment) {
    return createIntegerDeferred(INCRBY_COMMAND, key, intToBuffer(increment));
  }

  public Deferred<Buffer> info() {
    return createBulkDeferred(INFO_COMMAND);
  }

  public Deferred<Buffer[]> keys(Buffer pattern) {
    return createMultiBulkDeferred(KEYS_COMMAND, pattern);
  }

  public Deferred<Integer> lastSave() {
    return createIntegerDeferred(LASTSAVE_COMMAND);
  }

  public Deferred<Buffer> lIndex(Buffer key, int index) {
    return createBulkDeferred(LINDEX_COMMAND, key, intToBuffer(index));
  }

  public Deferred<Integer> lInsert(Buffer key, boolean before, Buffer pivot, Buffer value) {
    return createIntegerDeferred(LINSERT_COMMAND, key, Buffer.create(before ? INSERT_BEFORE : INSERT_AFTER), pivot, value);
  }

  public Deferred<Integer> lLen(Buffer key) {
    return createIntegerDeferred(LLEN_COMMAND, key);
  }

  public Deferred<Buffer> lPop(Buffer key) {
    return createBulkDeferred(LPOP_COMMAND, key);
  }

  public Deferred<Integer> lPush(Buffer key, Buffer value) {
    return createIntegerDeferred(LPUSH_COMMAND, key, value);
  }

  public Deferred<Integer> lPush(Buffer key, Buffer... values) {
    return createIntegerDeferred(LPUSH_COMMAND, toBufferArray(key, values));
  }

  public Deferred<Integer> lPushX(Buffer key, Buffer value) {
    return createIntegerDeferred(LPUSHX_COMMAND, key, value);
  }

  public Deferred<Buffer[]> lRange(Buffer key, int start, int stop) {
    return createMultiBulkDeferred(LRANGE_COMMAND, key, intToBuffer(start), intToBuffer(stop));
  }

  public Deferred<Integer> lRem(Buffer key, int count, Buffer value) {
    return createIntegerDeferred(LREM_COMMAND, key, intToBuffer(count), value);
  }

  public Deferred<Void> lSet(Buffer key, int index, Buffer value) {
    return createVoidDeferred(LSET_COMMAND, key, intToBuffer(index), value);
  }

  public Deferred<Void> lTrim(Buffer key, int start, int stop) {
    return createVoidDeferred(LTRIM_COMMAND, key, intToBuffer(start), intToBuffer(stop));
  }

  public Deferred<Buffer[]> mget(Buffer... keys) {
    return createMultiBulkDeferred(MGET_COMMAND, keys);
  }

  public Deferred<Boolean> move(Buffer key, Buffer db) {
    return createBooleanDeferred(MOVE_COMMAND, key, db);
  }

  public Deferred<Void> mset(Map<Buffer, Buffer> map) {
    return createVoidDeferred(MSET_COMMAND, toBufferArray(map));
  }

  public Deferred<Boolean> msetNx(Map<Buffer, Buffer> map) {
    return createBooleanDeferred(MSETNX_COMMAND, toBufferArray(map));
  }

  public Deferred<Void> multi() {
    RedisDeferred<Void> deferred = createVoidDeferred(MULTI_COMMAND);
    deferred.commandType = RedisDeferred.TxCommandType.MULTI;
    return deferred;
  }

  public Deferred<Boolean> persist(Buffer key) {
    return createBooleanDeferred(PERSIST_COMMAND, key);
  }

  public Deferred<Void> ping() {
    return createVoidDeferred(PING_COMMAND);
  }

  public Deferred<Void> pSubscribe(Buffer... patterns) {
    return doSubscribe(PSUBSCRIBE_COMMAND, patterns);
  }

  public Deferred<Integer> publish(Buffer channel, Buffer message) {
    return createIntegerDeferred(PUBLISH_COMMAND, channel, message);
  }

  public Deferred<Void> pUnsubscribe(Buffer... patterns) {
    return doUnsubscribe(PUNSUBSCRIBE_COMMAND, patterns);
  }

//  public Deferred<Void> quit() {
//    return createVoidDeferred(QUIT_COMMAND);
//  }

  public Deferred<Buffer> randomKey() {
    return createBulkDeferred(RANDOMKEY_COMMAND);
  }

  public Deferred<Void> rename(Buffer key, Buffer newKey) {
    return createVoidDeferred(RENAME_COMMAND, key, newKey);
  }

  public Deferred<Boolean> renameNX(Buffer key, Buffer newKey) {
    return createBooleanDeferred(RENAMENX_COMMAND, key, newKey);
  }

  public Deferred<Buffer> rPop(Buffer key) {
    return createBulkDeferred(RPOP_COMMAND, key);
  }

  public Deferred<Buffer> rPoplPush(Buffer source, Buffer destination) {
    return createBulkDeferred(RPOPLPUSH_COMMAND, source, destination);
  }

  public Deferred<Integer> rPush(Buffer key, Buffer... values) {
    return createIntegerDeferred(RPUSH_COMMAND, toBufferArray(key, values));
  }

  public Deferred<Integer> rPushX(Buffer key, Buffer value) {
    return createIntegerDeferred(RPUSHX_COMMAND, key, value);
  }

  public Deferred<Integer> sAdd(Buffer key, Buffer... members) {
    return createIntegerDeferred(SADD_COMMAND, toBufferArray(key, members));
  }

  public Deferred<Void> save() {
    return createVoidDeferred(SAVE_COMMAND);
  }

  public Deferred<Integer> sCard(Buffer key) {
    return createIntegerDeferred(SCARD_COMMAND, key);
  }

  public Deferred<Buffer[]> sDiff(Buffer key, Buffer... others) {
    return createMultiBulkDeferred(SDIFF_COMMAND, toBufferArray(key, others));
  }

  public Deferred<Integer> sDiffStore(Buffer destination, Buffer key, Buffer... others) {
    return createIntegerDeferred(SDIFFSTORE_COMMAND, toBufferArray(destination, toBufferArray(key, others)));
  }

  public Deferred<Void> select(int index) {
    return createVoidDeferred(SELECT_COMMAND, intToBuffer(index));
  }

  public Deferred<Void> set(Buffer key, Buffer value) {
    return createVoidDeferred(SET_COMMAND, key, value);
  }

  public Deferred<Integer> setBit(Buffer key, int offset, int value) {
    return createIntegerDeferred(SETBIT_COMMAND, key, intToBuffer(offset), intToBuffer(value));
  }

  public Deferred<Void> setEx(Buffer key, int seconds, Buffer value) {
    return createVoidDeferred(SETEX_COMMAND, key, intToBuffer(seconds), value);
  }

  public Deferred<Boolean> setNx(Buffer key, Buffer value) {
    return createBooleanDeferred(SETNX_COMMAND, key, value);
  }

  public Deferred<Integer> setRange(Buffer key, int offset, Buffer value) {
    return createIntegerDeferred(SETRANGE_COMMAND, key, intToBuffer(offset), value);
  }

  public Deferred<Void> shutdown() {
    return createVoidDeferred(SHUTDOWN_COMMAND);
  }

  public Deferred<Buffer[]> sInter(Buffer... keys) {
    return createMultiBulkDeferred(SINTER_COMMAND, keys);
  }

  public Deferred<Integer> sInterStore(Buffer destination, Buffer... keys) {
    return createIntegerDeferred(SINTERSTORE_COMMAND, toBufferArray(destination, keys));
  }

  public Deferred<Boolean> sIsMember(Buffer key, Buffer member) {
    return createBooleanDeferred(SISMEMBER_COMMAND, key, member);
  }

  public Deferred<Void> slaveOf(String host, int port) {
    return createVoidDeferred(SLAVEOF_COMMAND, Buffer.create(host), intToBuffer(port));
  }

  public Deferred<Buffer[]> sMembers(Buffer key) {
    return createMultiBulkDeferred(SMEMBERS_COMMAND, key);
  }

  public Deferred<Boolean> sMove(Buffer source, Buffer destination, Buffer member) {
    return createBooleanDeferred(SMOVE_COMMAND, source, destination, member);
  }

  public Deferred<Buffer[]> sort(Buffer key) {
    return sort(key, null, -1, -1, null, true, false, null);
  }

  public Deferred<Buffer[]> sort(Buffer key, boolean ascending) {
    return sort(key, null, -1, -1, null, ascending, false, null);
  }

  public Deferred<Buffer[]> sort(Buffer key, Buffer pattern) {
    return sort(key, pattern, -1, -1, null, true, false, null);
  }

  public Deferred<Buffer[]> sort(Buffer key, boolean ascending, boolean alpha) {
    return sort(key, null, -1, -1, null, ascending, alpha, null);
  }

  public Deferred<Buffer[]> sort(Buffer key, int offset, int count, boolean ascending, boolean alpha) {
    return sort(key, null, offset, count, null, ascending, alpha, null);
  }

  public Deferred<Buffer[]> sort(Buffer key, Buffer pattern, boolean ascending, boolean alpha) {
    return sort(key, pattern, -1, -1, null, ascending, alpha, null);
  }

  public Deferred<Buffer[]> sort(Buffer key, Buffer pattern, int offset, int count, Buffer[] getPatterns,
                   boolean ascending, boolean alpha, Buffer storeDestination) {
    int argsLen = 1 + (pattern == null ? 0 : 2) + (offset == -1 ? 0 : 3) + (getPatterns == null ? 0 : 2 * getPatterns.length) +
      (ascending ? 0 : 1) + (alpha ? 1: 0) + (storeDestination == null ? 0 : 2);
    Buffer[] args = new Buffer[argsLen];
    args[0] = key;
    int pos = 1;
    if (pattern != null) {
      args[pos++] = Buffer.create(SORT_BY);
      args[pos++] = pattern;
    }
    if (offset != -1) {
      args[pos++] = Buffer.create(LIMIT);
      args[pos++] = intToBuffer(offset);
      args[pos++] = intToBuffer(count);
    }
    if (getPatterns != null) {
      for (Buffer getPattern: getPatterns) {
        args[pos++] = Buffer.create(SORT_GET);
        args[pos++] = getPattern;
      }
    }
    if (!ascending) {
      args[pos++] = Buffer.create(SORT_DESC);
    }
    if (alpha) {
      args[pos++] = Buffer.create(SORT_ALPHA);
    }
    if (storeDestination != null) {
      args[pos++] = Buffer.create(SORT_STORE);
      args[pos++] = storeDestination;
    }
    return createMultiBulkDeferred(SORT_COMMAND, args);
  }

  public Deferred<Buffer> sPop(Buffer key) {
    return createBulkDeferred(SPOP_COMMAND, key);
  }

  public Deferred<Buffer> sRandMember(Buffer key) {
    return createBulkDeferred(SRANDMEMBER_COMMAND, key);
  }

  public Deferred<Integer> sRem(Buffer key, Buffer... members) {
    return createIntegerDeferred(SREM_COMMAND, toBufferArray(key, members));
  }

  public Deferred<Integer> strLen(Buffer key) {
    return createIntegerDeferred(STRLEN_COMMAND, key);
  }

  public Deferred<Void> subscribe(Buffer... channels) {
    return doSubscribe(SUBSCRIBE_COMMAND, channels);
  }

  public Deferred<Buffer[]> sUnion(Buffer... keys) {
    return createMultiBulkDeferred(SUNION_COMMAND, keys);
  }

  public Deferred<Integer> sUnionStore(Buffer destination, Buffer... keys) {
    return createIntegerDeferred(SUNIONSTORE_COMMAND, toBufferArray(destination, keys));
  }

  public Deferred<Integer> ttl(Buffer key) {
    return createIntegerDeferred(TTL_COMMAND, key);
  }

  public Deferred<String> type(Buffer key) {
    return createStringDeferred(TYPE_COMMAND, key);
  }

  public Deferred<Void> unsubscribe(Buffer... channels) {
    return doUnsubscribe(UNSUBSCRIBE_COMMAND, channels);
  }

  public Deferred<Void> unwatch() {
    return createVoidDeferred(UNWATCH_COMMAND);
  }

  public Deferred<Void> watch(Buffer... keys) {
    return createVoidDeferred(WATCH_COMMAND, keys);
  }

  public Deferred<Integer> zAdd(Buffer key, Map<Double, Buffer> map) {
    return createIntegerDeferred(ZADD_COMMAND, toBufferArray(key, toBufferArrayD(map)));
  }

  public Deferred<Integer> zAdd(Buffer key, double score, Buffer member) {
    return createIntegerDeferred(ZADD_COMMAND, key, doubleToBuffer(score), member);
  }

  public Deferred<Integer> zCard(Buffer key) {
    return createIntegerDeferred(ZCARD_COMMAND, key);
  }

  public Deferred<Integer> zCount(Buffer key, double min, double max) {
    return createIntegerDeferred(ZCOUNT_COMMAND, key, doubleToBuffer(min), doubleToBuffer(max));
  }

  public Deferred<Double> zIncrBy(Buffer key, double increment, Buffer member) {
    return createDoubleDeferred(ZINCRBY_COMMAND, key, doubleToBuffer(increment), member);
  }

  public enum AggregateType {
    SUM, MIN, MAX
  }

  public Deferred<Integer> zInterStore(Buffer destination, int numKeys, Buffer[] keys, double[] weights, AggregateType aggType) {
    int argsLen = 2 + keys.length + (weights != null ? 1 + weights.length : 0) + 2;
    Buffer[] args = new Buffer[argsLen];
    args[0] = destination;
    args[1] = intToBuffer(numKeys);
    int pos = 2;
    for (Buffer key: keys) {
      args[pos++] = key;
    }
    if (weights != null) {
      args[pos++] = Buffer.create(WEIGHTS);
      for (double weight: weights) {
        args[pos++] = doubleToBuffer(weight);
      }
    }
    args[pos++] = Buffer.create(AGGREGRATE);
    args[pos] = Buffer.create(aggType.toString());
    return createIntegerDeferred(ZINTERSTORE_COMMAND, args);
  }

  public Deferred<Buffer[]> zRange(Buffer key, int start, int stop, boolean withScores) {
    Buffer[] args = new Buffer[3 + (withScores ? 1 : 0)];
    args[0] = key;
    args[1] = intToBuffer(start);
    args[2] = intToBuffer(stop);
    if (withScores) {
      args[3] = Buffer.create(WITHSCORES);
    }
    return createMultiBulkDeferred(ZRANGE_COMMAND, args);
  }

  public Deferred<Buffer[]> zRangeByScore(Buffer key, double min, double max, boolean withScores, int offset, int count) {
    Buffer[] args = new Buffer[3 + (withScores ? 1 : 0) + (offset != -1 ? 3 : 0)];
    args[0] = key;
    args[1] = doubleToBuffer(min);
    args[2] = doubleToBuffer(max);
    int pos = 3;
    if (withScores) {
      args[pos++] = Buffer.create(WITHSCORES);
    }
    if (offset != -1) {
      args[pos++] = Buffer.create(LIMIT);
      args[pos++] = intToBuffer(offset);
      args[pos++] = intToBuffer(count);
    }
    return createMultiBulkDeferred(ZRANGEBYSCORE_COMMAND, args);
  }

  public Deferred<Integer> zRank(Buffer key, Buffer member) {
    return createIntegerDeferred(ZRANK_COMMAND, key, member);
  }

  public Deferred<Integer> zRem(Buffer key, Buffer... members) {
    return createIntegerDeferred(ZREM_COMMAND, toBufferArray(key, members));
  }

  public Deferred<Integer> zRemRangeByRank(Buffer key, int start, int stop) {
    return createIntegerDeferred(ZREMRANGEBYRANK_COMMAND, key, intToBuffer(start), intToBuffer(stop));
  }

  public Deferred<Integer> zRemRangeByScore(Buffer key, double min, double max) {
    return createIntegerDeferred(ZREMRANGEBYSCORE_COMMAND, key, doubleToBuffer(min), doubleToBuffer(max));
  }

  public Deferred<Buffer[]> zRevRange(Buffer key, int start, int stop, boolean withScores) {
    Buffer[] args = new Buffer[3 + (withScores ? 1 : 0)];
    args[0] = key;
    args[1] = intToBuffer(start);
    args[2] = intToBuffer(stop);
    if (withScores) {
      args[3] = Buffer.create(WITHSCORES);
    }
    return createMultiBulkDeferred(ZREVRANGE_COMMAND, args);
  }

  public Deferred<Buffer[]> zRevRangeByScore(Buffer key, double min, double max, boolean withScores, int offset, int count) {
    Buffer[] args = new Buffer[3 + (withScores ? 1 : 0) + (offset != -1 ? 3 : 0)];
    args[0] = key;
    args[1] = doubleToBuffer(min);
    args[2] = doubleToBuffer(max);
    int pos = 3;
    if (withScores) {
      args[pos++] = Buffer.create(WITHSCORES);
    }
    if (offset != -1) {
      args[pos++] = Buffer.create(LIMIT);
      args[pos++] = intToBuffer(offset);
      args[pos] = intToBuffer(count);
    }
    return createMultiBulkDeferred(ZREVRANGEBYSCORE_COMMAND, args);
  }

  public Deferred<Integer> zRevRank(Buffer key, Buffer member) {
    return createIntegerDeferred(ZREVRANK_COMMAND, key, member);
  }

  public Deferred<Double> zScore(Buffer key, Buffer member) {
    return createDoubleDeferred(ZSCORE_COMMAND, key, member);
  }

  public Deferred<Integer> zUnionStore(Buffer destination, int numKeys, Buffer[] keys, double[] weights, AggregateType aggType) {
    int argsLen = 2 + keys.length + (weights != null ? 1 + weights.length : 0) + 2;
    Buffer[] args = new Buffer[argsLen];
    args[0] = destination;
    args[1] = intToBuffer(numKeys);
    int pos = 2;
    for (Buffer key: keys) {
      args[pos++] = key;
    }
    if (weights != null) {
      args[pos++] = Buffer.create(WEIGHTS);
      for (double weight: weights) {
        args[pos++] = doubleToBuffer(weight);
      }
    }
    args[pos++] = Buffer.create(AGGREGRATE);
    args[pos] = Buffer.create(aggType.toString());
    return createIntegerDeferred(ZUNIONSTORE_COMMAND, args);
  }

  private Deferred<Void> doSubscribe(final byte[] command,  final Buffer... channels) {

    return new RedisDeferred<Void>(RedisDeferred.DeferredType.VOID, this) {
      public void run() {
        final Buffer buff = createCommand(command, channels);
        sendRequest(this, buff, true, contextID);
      }
      public void handleReply(RedisReply reply) {
        rc.conn.subscribe(contextID);
        setResult(null);
      }
    };
  }

  private Deferred<Void> doUnsubscribe(byte[] command,  Buffer... channels) {
    final Buffer buff = createCommand(command, channels);
    return new RedisDeferred<Void>(RedisDeferred.DeferredType.VOID, this) {
      public void run() {
        sendRequest(this, buff, true, contextID);
      }
      public void handleReply(RedisReply reply) {
        int num = reply.intResult;
        if (num == 0) {
          rc.conn.unsubscribe();
        }
        setResult(null);
      }
    };
  }

  private Buffer createCommand(byte[] command, Buffer... args) {
    Buffer buff = Buffer.create(64);
    buff.appendByte(ReplyParser.STAR);
    buff.appendString(String.valueOf(args.length + 1));
    buff.appendBytes(ReplyParser.CRLF);
    buff.appendByte(ReplyParser.DOLLAR);
    buff.appendString(String.valueOf(command.length));
    buff.appendBytes(ReplyParser.CRLF);
    buff.appendBytes(command);
    buff.appendBytes(ReplyParser.CRLF);
    for (int i = 0; i < args.length; i++) {
      buff.appendByte(ReplyParser.DOLLAR);
      Buffer arg = args[i];
      buff.appendString(String.valueOf(arg.length()));
      buff.appendBytes(ReplyParser.CRLF);
      buff.appendBuffer(arg);
      buff.appendBytes(ReplyParser.CRLF);
    }
    return buff;
  }

  private RedisDeferred<Double> createDoubleDeferred(final byte[] command, final Buffer... args) {
    return new RedisDeferred<Double>(RedisDeferred.DeferredType.DOUBLE, this) {
      public void run() {
        Buffer buff = createCommand(command, args);
        sendRequest(this, buff, contextID);
      }
    };
  }

  private RedisDeferred<Integer> createIntegerDeferred(final byte[] command, final Buffer... args) {
    return new RedisDeferred<Integer>(RedisDeferred.DeferredType.INTEGER, this) {
      public void run() {
        Buffer buff = createCommand(command, args);
        sendRequest(this, buff, contextID);
      }
    };
  }

  private RedisDeferred<Void> createVoidDeferred(final byte[] command, final Buffer... args) {
    return new RedisDeferred<Void>(RedisDeferred.DeferredType.VOID, this) {
      public void run() {
        Buffer buff = createCommand(command, args);
        sendRequest(this, buff, contextID);
      }
    };
  }

  private RedisDeferred<String> createStringDeferred(final byte[] command, final Buffer... args) {
    return new RedisDeferred<String>(RedisDeferred.DeferredType.STRING, this) {
      public void run() {
        Buffer buff = createCommand(command, args);
        sendRequest(this, buff, contextID);
      }
    };
  }

  private RedisDeferred<Boolean> createBooleanDeferred(final byte[] command, final Buffer... args) {
    return new RedisDeferred<Boolean>(RedisDeferred.DeferredType.BOOLEAN, this) {
      public void run() {
        Buffer buff = createCommand(command, args);
        sendRequest(this, buff, contextID);
      }
    };
  }

  private RedisDeferred<Buffer> createBulkDeferred(final byte[] command, final Buffer... args) {
    return new RedisDeferred<Buffer>(RedisDeferred.DeferredType.BULK, this) {
      public void run() {
        Buffer buff = createCommand(command, args);
        sendRequest(this, buff, contextID);
      }
    };
  }

  private RedisDeferred<Buffer[]> createMultiBulkDeferred(final byte[] command, final Buffer... args) {
    return new RedisDeferred<Buffer[]>(RedisDeferred.DeferredType.MULTI_BULK, this) {
      public void run() {
        Buffer buff = createCommand(command, args);
        sendRequest(this, buff, contextID);
      }
    };
  }

  private void sendRequest(final RedisDeferred<?> deferred, final Buffer buffer, long contextID) {
    sendRequest(deferred, buffer, false, contextID);
  }

  private void sendRequest(final RedisDeferred<?> deferred, final Buffer buffer, boolean subscribe, long contextID) {
    conn.sendRequest(deferred, buffer, subscribe, contextID);
  }

  private Buffer[] toBufferArray(Buffer[] buffers, Buffer... others) {
    Buffer[] args = new Buffer[buffers.length + others.length];
    System.arraycopy(buffers, 0, args, 0, buffers.length);
    System.arraycopy(others, 0, args, buffers.length, others.length);
    return args;
  }

  private Buffer[] toBufferArray(Buffer firstBuff, Buffer... others) {
    Buffer[] args = new Buffer[1 + others.length];
    args[0] = firstBuff;
    System.arraycopy(others, 0, args, 1, others.length);
    return args;
  }

  private Buffer[] toBufferArray(Map<Buffer, Buffer> map) {
    Buffer[] buffs = new Buffer[map.size() * 2];
    int pos = 0;
    for (Map.Entry<Buffer, Buffer> entry: map.entrySet()) {
      buffs[pos++] = entry.getKey();
      buffs[pos++] = entry.getValue();
    }
    return buffs;
  }

  private Buffer[] toBufferArrayD(Map<Double, Buffer> map) {
    Buffer[] buffs = new Buffer[map.size() * 2];
    int pos = 0;
    for (Map.Entry<Double, Buffer> entry: map.entrySet()) {
      buffs[pos++] = Buffer.create(entry.getKey().toString());
      buffs[pos++] = entry.getValue();
    }
    return buffs;
  }

  private Buffer[] toBufferArray(String[] strs) {
    Buffer[] buffs = new Buffer[strs.length];
    for (int i = 0; i < strs.length; i++) {
      buffs[i] = Buffer.create(strs[i]);
    }
    return buffs;
  }

  private Buffer intToBuffer(int i) {
    return Buffer.create(String.valueOf(i));
  }

  private Buffer doubleToBuffer(double d) {
    return Buffer.create(String.valueOf(d));
  }

  private void getConnection() {
    if (!connectionRequested) {
      pool.getConnection(new Handler<InternalConnection>() {
        public void handle(InternalConnection conn) {
          setConnection(conn);
        }
      }, NodexInternal.instance.getContextID());
      connectionRequested = true;
    }
  }

  private void setConnection(InternalConnection conn) {
    this.conn = conn;
    conn.closedHandler(new SimpleHandler() {
      public void handle() {
        RedisConnection.this.conn = null;
        connectionRequested = false;
      }
    });
    if (password != null) {
      auth(Buffer.create(password)).execute();
    }
    for (RedisDeferred<?> deff: pending) {
      deff.execute();
    }
    pending.clear();
    if (subscriberHandler != null) {
      conn.subscriberHandler = subscriberHandler;
    }
    //Might already be closed by the user before the internal connection was got
    if (closeDeferred != null) {
      conn.close(closeDeferred);
      closeDeferred = null;
    }
  }

}
