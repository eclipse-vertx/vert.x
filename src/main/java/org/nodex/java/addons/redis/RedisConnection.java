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

import org.nodex.java.core.Completion;
import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Handler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.net.NetSocket;

import java.nio.charset.Charset;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RedisConnection {

private static final Charset UTF8 = Charset.forName("UTF-8");

  private static final byte[] APPEND_COMMAND = "APPEND".getBytes(UTF8);
  private static final byte[] AUTH_COMMAND = "APPEND".getBytes(UTF8);
  private static final byte[] BGREWRITEAOF_COMMAND = "BGREWRITEAOF".getBytes(UTF8);
  private static final byte[] BGSAVE_COMMAND = "BGSAVE".getBytes(UTF8);
  private static final byte[] BLPOP_COMMAND = "BLPOP".getBytes(UTF8);
  private static final byte[] BRPOP_COMMAND = "BRPOP".getBytes(UTF8);
  private static final byte[] BRPOPLPUSH_COMMAND = "BRPOPLPUSH_COMMAND".getBytes(UTF8);
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
  private static final byte[] PERSIST_COMMAND = "MULTI".getBytes(UTF8);
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
  private static final byte[] SDIFFSTORE_COMMAND = "SDIFF".getBytes(UTF8);
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


  private static final byte[] INSERT_BEFORE = "BEFORE".getBytes(UTF8);
  private static final byte[] INSERT_AFTER = "AFTER".getBytes(UTF8);


  private final NetSocket socket;
  private Queue<CompletionHandler<Object>> requests = new ConcurrentLinkedQueue<>();

  RedisConnection(NetSocket socket) {
    this.socket = socket;
    socket.dataHandler(new ReplyParser(new Handler<Completion<Object>>() {
      public void handle(Completion<Object> completion) {
        doHandle(completion);
      }
    }));
  }

  public void close() {
    socket.close();
  }

  public void append(String key, String value, CompletionHandler<Integer> handler) {
    append(key, value.getBytes(UTF8), handler);
  }

  public void append(String key, byte[] value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {APPEND_COMMAND, key.getBytes(UTF8), value}, convertIntegerHandler(handler));
  }

  public void auth(String password, CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {AUTH_COMMAND, password.getBytes(UTF8)}, convertVoidHandler(handler));
  }

  public void bgRewriteAOF(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {BGREWRITEAOF_COMMAND}, convertVoidHandler(handler));
  }

  public void bgSave(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {BGSAVE_COMMAND}, convertVoidHandler(handler));
  }

  public void bLPop(String keys[], int timeout, CompletionHandler<byte[][]> handler) {
    byte[][] args = new byte[keys.length + 2][];
    args[0] = BLPOP_COMMAND;
    System.arraycopy(keys, 0, args, 1, keys.length);
    args[args.length - 1] = intToBytes(timeout);
    sendCommand(args, convertMultiBulkHandler(handler));
  }

  public void bRPop(String keys[], int timeout, CompletionHandler<byte[][]> handler) {
    byte[][] args = new byte[keys.length + 2][];
    args[0] = BRPOP_COMMAND;
    System.arraycopy(keys, 0, args, 1, keys.length);
    args[args.length - 1] = intToBytes(timeout);
    sendCommand(args, convertMultiBulkHandler(handler));
  }

  public void bRPopLPush(String source, String destination, int timeout, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {BRPOPLPUSH_COMMAND, source.getBytes(UTF8), destination.getBytes(UTF8),
                String.valueOf(timeout).getBytes(UTF8)}, convertBulkHandler(handler));
  }

  public void configGet(String parameter, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {CONFIG_GET_COMMAND, parameter.getBytes(UTF8)}, convertBulkHandler(handler));
  }

  public void configSet(String parameter, byte[] value, CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {CONFIG_SET_COMMAND, parameter.getBytes(UTF8), value}, convertVoidHandler(handler));
  }

  public void configSet(String parameter, String value, CompletionHandler<Void> handler) {
    configSet(parameter, value.getBytes(UTF8), handler);
  }

  public void configResetStat(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {CONFIG_RESET_STAT_COMMAND}, convertVoidHandler(handler));
  }

  public void dbSize(CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {DB_SIZE_COMMAND}, convertIntegerHandler(handler));
  }

  public void debugObject(String key, CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {DEBUG_OBJECT_COMMAND}, convertVoidHandler(handler));
  }

  public void debugSegFault(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {DEBUG_SEG_FAULT_COMMAND}, convertVoidHandler(handler));
  }

  public void decr(String key, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {DECR_COMMAND, key.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void decrBy(String key, int decrement, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {DECRBY_COMMAND, key.getBytes(UTF8), intToBytes(decrement)}, convertIntegerHandler(handler));
  }

  public void del(String[] keys, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[keys.length + 1][];
    args[0] = DEL_COMMAND;
    System.arraycopy(keys, 0, args, 1, keys.length);
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void discard(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {DISCARD_COMMAND}, convertVoidHandler(handler));
  }

  public void echo(CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {ECHO_COMMAND}, convertBulkHandler(handler));
  }

  public void exec(CompletionHandler<byte[][]> handler) {
    sendCommand(new byte[][] {EXEC_COMMAND}, convertMultiBulkHandler(handler));
  }

  public void exists(String key, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {EXISTS_COMMAND, key.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void expire(String key, int seconds, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {EXPIRE_COMMAND, key.getBytes(UTF8), intToBytes(seconds)}, convertIntegerHandler(handler));
  }

  public void expireAt(String key, int timestamp, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {EXPIREAT_COMMAND, key.getBytes(UTF8), intToBytes(timestamp)}, convertIntegerHandler(handler));
  }

  public void flushAll(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {FLUSHALL_COMMAND}, convertVoidHandler(handler));
  }

  public void flushDB(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {FLUSHDB_COMMAND}, convertVoidHandler(handler));
  }

  public void get(String key, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {GET_COMMAND, key.getBytes(UTF8)}, convertBulkHandler(handler));
  }

  public void getBit(String key, int offset, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {GETBIT_COMMAND, key.getBytes(UTF8), intToBytes(offset)}, convertIntegerHandler(handler));
  }

  public void getRange(String key, int start, int end, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {GETRANGE_COMMAND, key.getBytes(UTF8), intToBytes(start), intToBytes(end)}, convertBulkHandler(handler));
  }

  public void getSet(String key, byte[] value, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {GETSET_COMMAND, key.getBytes(UTF8), value}, convertBulkHandler(handler));
  }

  public void getSet(String key, String value, CompletionHandler<byte[]> handler) {
    getSet(key, value.getBytes(UTF8), handler);
  }

  public void hDel(String key, String[] fields, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[fields.length + 2][];
    args[0] = HDEL_COMMAND;
    args[1] = key.getBytes(UTF8);
    for (int i = 0; i < fields.length; i++) {
      args[i + 2] = fields[i].getBytes(UTF8);
    }
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void hExists(String key, String field, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {HEXISTS_COMMAND, key.getBytes(UTF8), field.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void hGet(String key, String field, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {HGET_COMMAND, key.getBytes(UTF8), field.getBytes(UTF8)}, convertBulkHandler(handler));
  }

  public void hGetAll(String key, CompletionHandler<byte[][]> handler) {
    sendCommand(new byte[][] {HGETALL_COMMAND, key.getBytes(UTF8)}, convertMultiBulkHandler(handler));
  }

  public void hIncrBy(String key, String field, int increment, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {HINCRBY_COMMAND, key.getBytes(UTF8), field.getBytes(UTF8), intToBytes(increment)}, convertIntegerHandler(handler));
  }

  public void hKeys(String key, CompletionHandler<byte[][]> handler) {
    sendCommand(new byte[][] {HKEYS_COMMAND, key.getBytes(UTF8)}, convertMultiBulkHandler(handler));
  }

  public void hLen(String key, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {HLEN_COMMAND, key.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void hmGet(String key, String[] fields, CompletionHandler<byte[][]> handler) {
    byte[][] args = new byte[fields.length + 2][];
    args[0] = HMGET_COMMAND;
    args[1] = key.getBytes(UTF8);
    for (int i = 0; i < fields.length; i++) {
      args[i + 2] = fields[i].getBytes(UTF8);
    }
    sendCommand(args, convertMultiBulkHandler(handler));
  }

  public void hmSet(String key, String[] fields, String[] values, CompletionHandler<Void> handler) {
    byte[][] args = new byte[2 * fields.length + 2][];
    args[0] = HMSET_COMMAND;
    args[1] = key.getBytes(UTF8);
    for (int i = 0; i < fields.length; i ++) {
      args[2 * i + 2] = fields[i].getBytes(UTF8);
      args[2 * i + 3] = values[i].getBytes(UTF8);
    }
    sendCommand(args, convertVoidHandler(handler));
  }

  public void hSet(String key, String field, byte[] value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {HSET_COMMAND, key.getBytes(UTF8), field.getBytes(UTF8), value}, convertIntegerHandler(handler));
  }

  public void hSet(String key, String field, String value, CompletionHandler<Integer> handler) {
    hSet(key, field, value.getBytes(UTF8), handler);
  }

  public void hSetNx(String key, String field, byte[] value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {HSETNX_COMMAND, key.getBytes(UTF8), field.getBytes(UTF8), value}, convertIntegerHandler(handler));
  }

  public void hSetNx(String key, String field, String value, CompletionHandler<Integer> handler) {
    hSetNx(key, field, value.getBytes(UTF8), handler);
  }

  public void hVals(String key, CompletionHandler<byte[][]> handler) {
    sendCommand(new byte[][] {HVALS_COMMAND, key.getBytes(UTF8)}, convertMultiBulkHandler(handler));
  }

  public void incr(String key, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {INCR_COMMAND, key.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void incrBy(String key, int increment, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {INCRBY_COMMAND, key.getBytes(UTF8), intToBytes(increment)}, convertIntegerHandler(handler));
  }

  public void info(CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {INFO_COMMAND}, convertBulkHandler(handler));
  }

  public void keys(String pattern, CompletionHandler<byte[][]> handler) {
    sendCommand(new byte[][] {KEYS_COMMAND, pattern.getBytes(UTF8)}, convertMultiBulkHandler(handler));
  }

  public void lastSave(CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {LASTSAVE_COMMAND}, convertIntegerHandler(handler));
  }

  public void lIndex(String key, int index, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {LINDEX_COMMAND, key.getBytes(UTF8), intToBytes(index)}, convertBulkHandler(handler));
  }

  public void lInsert(String key, boolean before, byte[] pivot, byte[] value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {LINSERT_COMMAND, key.getBytes(UTF8),
                              before ? INSERT_BEFORE : INSERT_AFTER, pivot, value}, convertIntegerHandler(handler));
  }

  public void lInsert(String key, boolean before, String pivot, String value, CompletionHandler<Integer> handler) {
    lInsert(key, before, pivot.getBytes(UTF8), value.getBytes(UTF8), handler);
  }

  public void lLen(String key, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {LLEN_COMMAND, key.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void lPop(String key, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {LPOP_COMMAND, key.getBytes(UTF8)}, convertBulkHandler(handler));
  }

  public void lPush(String key, byte[][] values, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[values.length + 2][];
    args[0] = LPUSH_COMMAND;
    args[1] = key.getBytes(UTF8);
    System.arraycopy(values, 0, args, 2, values.length);
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void lPush(String key, String[] values, CompletionHandler<Integer> handler) {
    byte[][] vals = new byte[values.length][];
    for (int i = 0; i < values.length; i++) {
      vals[i] = values[i].getBytes(UTF8);
    }
    lPush(key, vals, handler);
  }

  public void lPush(String key, String value, CompletionHandler<Integer> handler) {
    lPush(key, new String[] {value}, handler);
  }

  public void lPush(String key, byte[] value, CompletionHandler<Integer> handler) {
    lPush(key, new byte[][] {value}, handler);
  }

  public void lPushX(String key, byte[] value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {LPUSHX_COMMAND, key.getBytes(UTF8), value}, convertIntegerHandler(handler));
  }

  public void lPushX(String key, String value, CompletionHandler<Integer> handler) {
    lPushX(key, value.getBytes(UTF8), handler);
  }

  public void lRange(String key, int start, int stop, CompletionHandler<byte[][]> handler) {
    sendCommand(new byte[][] {LRANGE_COMMAND, key.getBytes(UTF8), intToBytes(start), intToBytes(stop)}, convertMultiBulkHandler(handler));
  }

  public void lRem(String key, int count, byte[] value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {LREM_COMMAND, key.getBytes(UTF8), intToBytes(count), value}, convertIntegerHandler(handler));
  }

  public void lRem(String key, int count, String value, CompletionHandler<Integer> handler) {
    lRem(key, count, value.getBytes(UTF8), handler);
  }

  public void lSet(String key, int index, byte[] value, CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {LSET_COMMAND, key.getBytes(UTF8), intToBytes(index), value}, convertVoidHandler(handler));
  }

  public void lSet(String key, int index, String value, CompletionHandler<Void> handler) {
    lSet(key, index, value.getBytes(UTF8), handler);
  }

  public void lTrim(String key, int start, int stop, CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {LTRIM_COMMAND, key.getBytes(UTF8), intToBytes(start), intToBytes(stop)}, convertVoidHandler(handler));
  }

  public void mget(String keys[], CompletionHandler<byte[][]> handler) {
    byte[][] args = new byte[keys.length + 1][];
    args[0] = MGET_COMMAND;
    System.arraycopy(keys, 0, args, 1, keys.length);
    sendCommand(args, convertMultiBulkHandler(handler));
  }

  public void move(String key, String db, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {MOVE_COMMAND, key.getBytes(UTF8), db.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void mset(String[] keys, byte[][] values, CompletionHandler<Void> handler) {
    byte[][] args = new byte[2 * keys.length + 1][];
    args[0] = MSET_COMMAND;
    for (int i = 0; i < keys.length; i ++) {
      args[2 * i + 1] = keys[i].getBytes(UTF8);
      args[2 * i + 2] = values[i];
    }
    sendCommand(args, convertVoidHandler(handler));
  }

  public void mset(String[] keys, String[] values, CompletionHandler<Void> handler) {
    byte[][] vals = new byte[values.length][];
    for (int i = 0; i < values.length; i++) {
      vals[i] = values[i].getBytes(UTF8);
    }
    mset(keys, vals, handler);
  }

  public void msetNX(String[] keys, byte[][] values, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[2 * keys.length + 1][];
    args[0] = MSETNX_COMMAND;
    for (int i = 0; i < keys.length; i ++) {
      args[2 * i + 1] = keys[i].getBytes(UTF8);
      args[2 * i + 2] = values[i];
    }
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void msetNX(String[] keys, String[] values, CompletionHandler<Integer> handler) {
    byte[][] vals = new byte[values.length][];
    for (int i = 0; i < values.length; i++) {
      vals[i] = values[i].getBytes(UTF8);
    }
    msetNX(keys, vals, handler);
  }

  public void multi(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {MULTI_COMMAND}, convertVoidHandler(handler));
  }

  public void persist(String key, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {PERSIST_COMMAND, key.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void ping(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {PING_COMMAND}, convertVoidHandler(handler));
  }

  public void pSubscribe(String[] patterns, CompletionHandler<Void> handler) {
    byte[][] args = new byte[patterns.length + 1][];
    args[0] = PSUBSCRIBE_COMMAND;
    for (int i = 0; i < patterns.length; i++) {
      args[i + 1] = patterns[i].getBytes(UTF8);
    }
    sendCommand(args, convertVoidHandler(handler));
  }

  public void pSubscribe(String pattern, CompletionHandler<Void> handler) {
    pSubscribe(new String[] {pattern}, handler);
  }

  public void publish(String channel, byte[] message, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {PUBLISH_COMMAND, channel.getBytes(UTF8), message}, convertIntegerHandler(handler));
  }

  public void publish(String channel, String message, CompletionHandler<Integer> handler) {
    publish(channel, message.getBytes(UTF8), handler);
  }

  public void pUnsubscribe(String[] patterns, CompletionHandler<Void> handler) {
    byte[][] args = new byte[patterns.length + 1][];
    args[0] = PUNSUBSCRIBE_COMMAND;
    for (int i = 0; i < patterns.length; i++) {
      args[i + 1] = patterns[i].getBytes(UTF8);
    }
    sendCommand(args, convertVoidHandler(handler));
  }

  public void pUnsubscribe(String pattern, CompletionHandler<Void> handler) {
    pUnsubscribe(new String[] {pattern}, handler);
  }

  public void quit(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {QUIT_COMMAND}, convertVoidHandler(handler));
  }

  public void randomKey(CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {RANDOMKEY_COMMAND}, convertBulkHandler(handler));
  }

  public void rename(String key, String newKey, CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {RENAME_COMMAND, key.getBytes(UTF8), newKey.getBytes(UTF8)}, convertVoidHandler(handler));
  }

  public void renameNX(String key, String newKey, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {RENAMENX_COMMAND, key.getBytes(UTF8), newKey.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void rPop(String key, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {RPOP_COMMAND, key.getBytes(UTF8)}, convertBulkHandler(handler));
  }

  public void rPoplPush(String source, String destination, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {RPOPLPUSH_COMMAND, source.getBytes(UTF8), destination.getBytes(UTF8)}, convertBulkHandler(handler));
  }

  public void rPush(String key, byte[][] values, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[values.length + 1][];
    args[0] = RPUSH_COMMAND;
    for (int i = 0; i < values.length; i++) {
      args[i + 1] = values[i];
    }
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void rPush(String key, String[] values, CompletionHandler<Integer> handler) {
    byte[][] vals = new byte[values.length][];
    for (int i = 0; i < values.length; i++) {
      vals[i] = values[i].getBytes(UTF8);
    }
    rPush(key, vals, handler);
  }

  public void rPush(String key, String value, CompletionHandler<Integer> handler) {
    rPush(key, new String[] {value}, handler);
  }

  public void rPush(String key, byte[] value, CompletionHandler<Integer> handler) {
    rPush(key, new byte[][] {value}, handler);
  }

  public void rPushX(String key, byte[] value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {RPUSHX_COMMAND, key.getBytes(UTF8), value}, convertIntegerHandler(handler));
  }

  public void rPushX(String key, String value, CompletionHandler<Integer> handler) {
    rPushX(key, value.getBytes(UTF8), handler);
  }

  public void sAdd(String key, byte[][] members, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[members.length + 1][];
    args[0] = SADD_COMMAND;
    for (int i = 0; i < members.length; i++) {
      args[i + 1] = members[i];
    }
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void sAdd(String key, String[] members, CompletionHandler<Integer> handler) {
    byte[][] vals = new byte[members.length][];
    for (int i = 0; i < members.length; i++) {
      vals[i] = members[i].getBytes(UTF8);
    }
    sAdd(key, vals, handler);
  }

  public void sAdd(String key, String member, CompletionHandler<Integer> handler) {
    sAdd(key, new String[] {member}, handler);
  }

  public void sAdd(String key, byte[] member, CompletionHandler<Integer> handler) {
    sAdd(key, new byte[][] {member}, handler);
  }

  public void save(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {SAVE_COMMAND}, convertVoidHandler(handler));
  }

  public void sCard(String key, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {SCARD_COMMAND, key.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void sDiff(String key, String[] others, CompletionHandler<byte[][]> handler) {
    byte[][] args = new byte[others.length + 2][];
    args[0] = SDIFF_COMMAND;
    args[1] = key.getBytes(UTF8);
    for (int i = 0; i < others.length; i++) {
      args[i + 2] = others[i].getBytes(UTF8);
    }
    sendCommand(args, convertMultiBulkHandler(handler));
  }

  public void sDiff(String key, String other, CompletionHandler<byte[][]> handler) {
    sDiff(key, new String[] {other}, handler);
  }

  public void sDiffStore(String key, String[] others, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[others.length + 2][];
    args[0] = SDIFFSTORE_COMMAND;
    args[1] = key.getBytes(UTF8);
    for (int i = 0; i < others.length; i++) {
      args[i + 2] = others[i].getBytes(UTF8);
    }
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void sDiffStore(String key, String other, CompletionHandler<Integer> handler) {
    sDiffStore(key, new String[] {other}, handler);
  }

  public void select(int index, CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {SELECT_COMMAND, intToBytes(index)}, convertVoidHandler(handler));
  }

  public void set(String key, String value, CompletionHandler<String> handler) {
    set(key, value.getBytes(UTF8), handler);
  }

  public void set(String key, byte[] value, CompletionHandler<String> handler) {
    sendCommand(new byte[][] {SET_COMMAND, key.getBytes(UTF8), value}, convertStringHandler(handler));
  }

  public void setBit(String key, int offset, int value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {SETBIT_COMMAND, key.getBytes(UTF8), intToBytes(offset), intToBytes(value)}, convertIntegerHandler(handler));
  }

  public void setEx(String key, int seconds, String value, CompletionHandler<Void> handler) {
    setEx(key, seconds, value.getBytes(UTF8), handler);
  }

  public void setEx(String key, int seconds, byte[] value, CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {SETEX_COMMAND, key.getBytes(UTF8), intToBytes(seconds), value}, convertVoidHandler(handler));
  }

  public void setNx(String key, String value, CompletionHandler<Integer> handler) {
    setNx(key, value.getBytes(UTF8), handler);
  }

  public void setNx(String key, byte[] value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {SETNX_COMMAND, key.getBytes(UTF8), value}, convertIntegerHandler(handler));
  }

  public void setRange(String key, int offset, String value, CompletionHandler<Integer> handler) {
    setRange(key, offset, value.getBytes(UTF8), handler);
  }

  public void setRange(String key, int offset, byte[] value, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {SETRANGE_COMMAND, key.getBytes(UTF8), intToBytes(offset), value}, convertIntegerHandler(handler));
  }

  public void shutdown(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {SHUTDOWN_COMMAND}, convertVoidHandler(handler));
  }

  public void sInter(String[] keys, CompletionHandler<byte[][]> handler) {
    byte[][] args = new byte[keys.length + 1][];
    args[0] = SINTER_COMMAND;
    for (int i = 0; i < keys.length; i++) {
      args[i + 1] = keys[i].getBytes(UTF8);
    }
    sendCommand(args, convertMultiBulkHandler(handler));
  }

  public void sInterStore(String[] keys, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[keys.length + 1][];
    args[0] = SINTERSTORE_COMMAND;
    for (int i = 0; i < keys.length; i++) {
      args[i + 1] = keys[i].getBytes(UTF8);
    }
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void sIsMember(String key, byte[] member, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {SISMEMBER_COMMAND, key.getBytes(UTF8), member}, convertIntegerHandler(handler));
  }

  public void sIsMember(String key, String member, CompletionHandler<Integer> handler) {
    sIsMember(key, member.getBytes(UTF8), handler);
  }

  public void slaveOf(String host, int port, CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {SLAVEOF_COMMAND, host.getBytes(UTF8), intToBytes(port)}, convertVoidHandler(handler));
  }

  public void sMembers(String key, CompletionHandler<byte[][]> handler) {
    sendCommand(new byte[][] {SMEMBERS_COMMAND, key.getBytes(UTF8)}, convertMultiBulkHandler(handler));
  }

  public void sMove(String source, String destination, byte[] member, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {SMOVE_COMMAND, source.getBytes(UTF8), destination.getBytes(UTF8), member}, convertIntegerHandler(handler));
  }

  public void sMove(String source, String destination, String member, CompletionHandler<Integer> handler) {
    sMove(source, destination, member.getBytes(UTF8), handler);
  }

  public void sort(String key, CompletionHandler<byte[][]> handler) {
    sort(key, null, -1, -1, null, true, false, null, handler);
  }

  public void sort(String key, String pattern, CompletionHandler<byte[][]> handler) {
    sort(key, pattern, -1, -1, null, true, false, null, handler);
  }

  public void sort(String key, String pattern, boolean ascending, boolean alpha, CompletionHandler<byte[][]> handler) {
    sort(key, pattern, -1, -1, null, ascending, alpha, null, handler);
  }

  public void sort(String key, String pattern, int offset, int count, String[] getPatterns,
                   boolean ascending, boolean alpha, String storeDestination, CompletionHandler<byte[][]> handler) {
    int argsLen = 2 + (pattern == null ? 0 : 2) + (offset == -1 ? 0 : 3) + (getPatterns == null ? 0 : 2 * getPatterns.length) +
      (ascending ? 0 : 1) + (alpha ? 1: 0) + (storeDestination == null ? 0 : 2);
    byte[][] args = new byte[argsLen][];
    args[0] = SORT_COMMAND;
    args[1] = key.getBytes(UTF8);
    int pos = 2;
    if (pattern != null) {
      args[pos++] = "BY".getBytes(UTF8);
      args[pos++] = pattern.getBytes(UTF8);
    }
    if (offset != -1) {
      args[pos++] = "LIMIT".getBytes(UTF8);
      args[pos++] = intToBytes(offset);
      args[pos++] = intToBytes(count);
    }
    if (getPatterns != null) {
      for (String getPattern: getPatterns) {
        args[pos++] = "GET".getBytes(UTF8);
        args[pos++] = getPattern.getBytes(UTF8);
      }
    }
    if (!ascending) {
      args[pos++] = "DESC".getBytes(UTF8);
    }
    if (alpha) {
      args[pos++] = "ALPHA".getBytes(UTF8);
    }
    if (storeDestination != null) {
      args[pos++] = "STORE".getBytes(UTF8);
      args[pos++] = storeDestination.getBytes(UTF8);
    }
    sendCommand(args, convertMultiBulkHandler(handler));
  }

  public void sPop(String key, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {SPOP_COMMAND, key.getBytes(UTF8)}, convertBulkHandler(handler));
  }

  public void sRandMember(String key, CompletionHandler<byte[]> handler) {
    sendCommand(new byte[][] {SRANDMEMBER_COMMAND, key.getBytes(UTF8)}, convertBulkHandler(handler));
  }

  public void sRem(String key, byte[][] members, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[2 + members.length][];
    args[0] = SREM_COMMAND;
    args[1] = key.getBytes(UTF8);
    System.arraycopy(members, 0, args, 2, members.length);
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void sRem(String key, String[] members, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[members.length][];
    for (int i = 0; i < members.length; i++) {
      args[i] = members[i].getBytes(UTF8);
    }
    sRem(key, args, handler);
  }

  public void sRem(String key, String member, CompletionHandler<Integer> handler) {
    sRem(key, new String[] { member }, handler);
  }

  public void sRem(String key, byte[] member, CompletionHandler<Integer> handler) {
    sRem(key, new byte[][] { member }, handler);
  }

  public void strLen(String key, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {STRLEN_COMMAND, key.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void subscribe(String[] channels, CompletionHandler<Void> handler) {
    byte[][] args = new byte[channels.length + 1][];
    args[0] = SUBSCRIBE_COMMAND;
    for (int i = 0; i < channels.length; i++) {
      args[i + 1] = channels[i].getBytes(UTF8);
    }
    sendCommand(args, convertVoidHandler(handler));
  }

  public void subscribe(String channel, CompletionHandler<Void> handler) {
    subscribe(new String[] {channel}, handler);
  }

  public void sUnion(String[] keys, CompletionHandler<byte[][]> handler) {
    byte[][] args = new byte[keys.length + 1][];
    args[0] = SUNION_COMMAND;
    for (int i = 0; i < keys.length; i++) {
      args[i + 1] = keys[i].getBytes(UTF8);
    }
    sendCommand(args, convertMultiBulkHandler(handler));
  }

  public void sUnionStore(String[] keys, CompletionHandler<Integer> handler) {
    byte[][] args = new byte[keys.length + 1][];
    args[0] = SUNIONSTORE_COMMAND;
    for (int i = 0; i < keys.length; i++) {
      args[i + 1] = keys[i].getBytes(UTF8);
    }
    sendCommand(args, convertIntegerHandler(handler));
  }

  public void ttl(String key, CompletionHandler<Integer> handler) {
    sendCommand(new byte[][] {TTL_COMMAND, key.getBytes(UTF8)}, convertIntegerHandler(handler));
  }

  public void type(String key, CompletionHandler<String> handler) {
    sendCommand(new byte[][] {TYPE_COMMAND, key.getBytes(UTF8)}, convertStringHandler(handler));
  }

  public void unsubscribe(String[] channels, CompletionHandler<Void> handler) {
    byte[][] args = new byte[channels.length + 1][];
    args[0] = UNSUBSCRIBE_COMMAND;
    for (int i = 0; i < channels.length; i++) {
      args[i + 1] = channels[i].getBytes(UTF8);
    }
    sendCommand(args, convertVoidHandler(handler));
  }

  public void unsubscribe(String channel, CompletionHandler<Void> handler) {
    unsubscribe(new String[] {channel}, handler);
  }

  public void unwatch(CompletionHandler<Void> handler) {
    sendCommand(new byte[][] {UNWATCH_COMMAND}, convertVoidHandler(handler));
  }

  public void watch(String[] keys, CompletionHandler<Void> handler) {
    byte[][] args = new byte[1 + keys.length][];
    args[0] = WATCH_COMMAND;
    for (int i = 0; i < keys.length; i++) {
      args[i + 1] = keys[i].getBytes(UTF8);
    }
    sendCommand(args, convertVoidHandler(handler));
  }

  public void watch(String key, CompletionHandler<Void> handler) {
    watch(new String[] { key }, handler);
  }








  public void sendCommand(byte[][] args, CompletionHandler<Object> responseHandler) {
    Buffer buff = Buffer.create(0);
    buff.appendByte(ReplyParser.STAR);
    buff.appendString(String.valueOf(args.length));
    buff.appendBytes(ReplyParser.CRLF);
    for (int i = 0; i < args.length; i++) {
      buff.appendByte(ReplyParser.DOLLAR);
      byte[] arg = args[i];
      buff.appendString(String.valueOf(arg.length));
      buff.appendBytes(ReplyParser.CRLF);
      buff.appendBytes(arg);
      buff.appendBytes(ReplyParser.CRLF);
    }
    requests.add(responseHandler);
    socket.write(buff);
  }

  private byte[] intToBytes(int i) {
    return String.valueOf(i).getBytes(UTF8);
  }

  private void doHandle(Completion<Object> completion) {
    CompletionHandler<Object> handler = requests.poll();
    if (handler == null) {
      throw new IllegalStateException("Protocol out of sync, received response without request");
    }
    handler.handle(completion);
  }

  private CompletionHandler<Object> convertStringHandler(final CompletionHandler<String> handler) {
    return new CompletionHandler<Object>() {
      public void handle(Completion<Object> completion) {
        if (completion.succeeded()) {
          handler.handle(new Completion<>((String)completion.result));
        } else {
          handler.handle(new Completion<String>(completion.exception));
        }
      }
    };
  }

  private CompletionHandler<Object> convertIntegerHandler(final CompletionHandler<Integer> handler) {
    return new CompletionHandler<Object>() {
      public void handle(Completion<Object> completion) {
        if (completion.succeeded()) {
          handler.handle(new Completion<>((Integer)completion.result));
        } else {
          handler.handle(new Completion<Integer>(completion.exception));
        }
      }
    };
  }

  private CompletionHandler<Object> convertBulkHandler(final CompletionHandler<byte[]> handler) {
    return new CompletionHandler<Object>() {
      public void handle(Completion<Object> completion) {
        if (completion.succeeded()) {
          handler.handle(new Completion<>((byte[])completion.result));
        } else {
          handler.handle(new Completion<byte[]>(completion.exception));
        }
      }
    };
  }

  private CompletionHandler<Object> convertMultiBulkHandler(final CompletionHandler<byte[][]> handler) {
    return new CompletionHandler<Object>() {
      public void handle(Completion<Object> completion) {
        if (completion.succeeded()) {
          handler.handle(new Completion<>((byte[][])completion.result));
        } else {
          handler.handle(new Completion<byte[][]>(completion.exception));
        }
      }
    };
  }

  private CompletionHandler<Object> convertVoidHandler(final CompletionHandler<Void> handler) {
    return new CompletionHandler<Object>() {
      public void handle(Completion<Object> completion) {
        if (completion.succeeded()) {
          handler.handle(Completion.VOID_SUCCESSFUL_COMPLETION);
        } else {
          handler.handle(new Completion<Void>(completion.exception));
        }
      }
    };
  }

}
