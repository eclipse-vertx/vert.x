# Copyright 2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'core/composition'
require 'core/buffer'

module Nodex

  # Instances of this class maintain a pool of connections to a Redis Server and act as a factory for
  # {RedisConnection} instances via the {#connection} method.
  #
  # Once a RedisConnection has been done with, the {RedisConnection#close} method should be called to return its
  # underlying TCP connection to the pool.
  #
  # If Redis authentication is enabled on the server, a password should be set using the {#password=} method.
  #
  # @author {http://tfox.org Tim Fox}
  class RedisPool

    # Create a new RedisPool
    def initialize
      @j_del = org.nodex.java.addons.redis.RedisPool.new
    end

    # Set the port that the client will attempt to connect to on the server. If not set, the default value is 6379
    # @param port [FixNum] The port
    def port=(port)
      @j_del.setPort(port)
    end

    # Set the host or ip addresss that the client will attempt to connect to on the server. If not set, the default value is localhost
    # @param host [String] The host or ip address
    def host=(host)
      @j_del.setHost(host)
    end

    # Set the maximum pool size. The pool will maintain up to this value of Redis connections in an internal pool.
    # @param max [FixNum] The maximum number of connections in the pool
    def max_pool_size=(max)
      @j_del.setMaxPoolSize(max)
    end

    # Set the password used for authentication. If a password is set then every connection will initially send an AUTH
    # command. If no password is set, no AUTH command will be sent
    # @param pwd [String] The password
    def password=(pwd)
      @j_del.setPassword(pwd)
    end

    # Close the pool. Any open connections will be closed.
    def close
      @j_del.close
    end

    # Get a redis connection
    def connection
      RedisConnection.new(@j_del.connection)
    end

  end

  # Represents a connection to a Redis server.
  #
  # Instances of this class are obtained from the {RedisPool#connection} method. The actual TCP connections to Redis
  # are obtained lazily from an internal pool when actions are executed. You can have many instances of RedisConnection
  # sharing a limited pool of actual TCP connections. Once you have done writing requests you should call {#close}
  # to return the underlying TCP connection to the pool. You do not have to wait for all your responses to return before calling
  # close. The class supports pipelining, i.e. you can safely send many requests in succession before any of the results have returned.
  #
  # A couple of caveats:
  #
  # 1. If you have subscribed to any Redis channels, the connection will be put into subscription mode and you
  # cannot send any commands apart from SUBSCRIBE and UNSUBSCRIBE, or close the connection, until you have unsubscribed from all channels.
  #
  # 2. If you have started a transaction with MULTI you cannot close the connection until the transaction has been completed using
  # EXEC or DISCARD.
  #
  # Actions are returned as instances of {Deferred}. The actual actions won't be executed until the
  # {Deferred#execute} method is called. This allows multiple Deferred instances to be composed together
  # using the {Composer} class.
  #
  # An example of using this class directly would be:
  #
  # @example
  #   conn = pool.connection
  #   client.set(Buffer.create("key1"), Buffer.create("value1").handler{ puts "The value has been set"}
  #
  # Or using a {Composer} instance:
  #
  # @example
  #   conn = pool.connection
  #   comp = Composer.new
  #   comp.parallel(conn.set(Buffer.create("key1"), Buffer.create("value1")))
  #   comp.parallel(conn.set(Buffer.create("key2"), Buffer.create("value2")))
  #   future1 = comp.series(conn.get(Buffer.create("key1")))
  #   future2 = comp.parallel(conn.get(Buffer.create("key2")))
  #   comp.series(DeferredAction.new { conn.set(Buffer.create("key3"), Buffer.create(result1.result + result2.result)).handler { result= null })
  #   comp.parallel(conn.close_deferred)
  #
  # For a full description of the various Redis commands, please see the <a href="http://redis.io/commands">Redis documentation</a>.
  #
  # @author {http://tfox.org Tim Fox}
  class RedisConnection

    # @private
    def initialize(j_del)
      @j_del = j_del
    end

    # Close the connection asynchronously
    # This method must be called using the same event loop the connection was opened from.
    # @return [Future] a Future representing the future result of closing the file.
    def close
      Future.new(@j_del.close)
    end

    # Same as {#close} but the close does not start until the {Deferred#execute} method
    # is called on the Deferred instance returned by this method.
    # @return [Deferred] a Deferred representing the as-yet unexecuted action.
    def close_deferred
      Deferred.new(@j_del.closeDeferred)
    end

    # Set a handler which will receive messages when the connection is in subscribe mode.
    # The handler can be a Proc or a block
    def subscriber_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_del.subscriberHandler(Proc.new{ |j_buff| hndlr.call(Buffer.new(j_buff)) })
    end

    def append(key, value)
      Deferred.new(@j_del.append(key._to_java_buffer, value._to_java_buffer))
    end

    def bg_rewrite_aof
      Deferred.new(@j_del.bgRewriteAOF)
    end

    def bg_save
      Deferred.new(@j_del.bgSave)
    end

    def b_l_pop(timeout, *keys)
      Deferred.new(@j_del.bLPop(timeout, rbuff_arr_to_java(*keys))) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def b_r_pop(timeout, *keys)
      Deferred.new(@j_del.bRPop(timeout, rbuff_arr_to_java(*keys))) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def b_r_pop_lpush(source, destination, timeout)
      Deferred.new(@j_del.bRPopLPush(source._to_java_buffer, destination._to_java_buffer, timeout)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def config_get(param)
      Deferred.new(@j_del.configGet(param.to_java_buffer)) { |j_buff| Buffer.new(j_buff)}
    end

    def config_set(param, value)
      Deferred.new(@j_del.configSet(param.to_java_buffer, value._to_java_buffer))
    end

    def db_size
      Deferred.new(@j_del.dbSize)
    end

    def decr(key)
      Deferred.new(@j_del.decr(key._to_java_buffer))
    end

    def decr_by(key, decrement)
      Deferred.new(@j_del.decrBy(key._to_java_buffer, decrement))
    end

    def del(*keys)
      Deferred.new(@j_del.del(rbuff_arr_to_java(*keys)))
    end

    def discard
      Deferred.new(@j_del.discard)
    end

    def echo(message)
      Deferred.new(@j_del.echo(message._to_java_buffer)) { |j_buff| Buffer.new(j_buff)}
    end

    def exec
      Deferred.new(@j_del.exec)
    end

    def exists(key)
      Deferred.new(@j_del.exists(key._to_java_buffer))
    end

    def expire(key, seconds)
      Deferred.new(@j_del.expire(key._to_java_buffer, seconds))
    end

    def expire_at(key, timeout)
      Deferred.new(@j_del.expireAt(key._to_java_buffer, timeout))
    end

    def flush_all
      Deferred.new(@j_del.flushAll)
    end

    def flush_db
      Deferred.new(@j_del.flushDB)
    end

    def get(key)
      Deferred.new(@j_del.get(key._to_java_buffer)) { |j_buff| Buffer.new(j_buff)}
    end

    def get_bit(key, offset)
      Deferred.new(@j_del.getBit(key._to_java_buffer, offset))
    end

    def get_range(key, range_start, range_end)
      Deferred.new(@j_del.getRange(key._to_java_buffer, range_start, range_end)) { |j_buff| Buffer.new(j_buff)}
    end

    def get_set(key, value)
      Deferred.new(@j_del.getSet(key._to_java_buffer, value._to_java_buffer)) { |j_buff| Buffer.new(j_buff)}
    end

    def h_del(key, *fields)
      Deferred.new(@j_del.hDel(key._to_java_buffer, rbuff_arr_to_java(*fields)))
    end

    def h_exists(key, field)
      Deferred.new(@j_del.hExists(key._to_java_buffer, field._to_java_buffer))
    end

    def h_get(key, field)
      Deferred.new(@j_del.hGet(key._to_java_buffer, field._to_java_buffer)) { |j_buff| Buffer.new(j_buff)}
    end

    def h_get_all(key)
      Deferred.new(@j_del.hGetAll(key._to_java_buffer)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def h_incr_by(key, field, increment)
      Deferred.new(@j_del.hIncrBy(key._to_java_buffer, field._to_java_buffer, increment))
    end

    def h_keys(key)
      Deferred.new(@j_del.hKeys(key._to_java_buffer)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def h_len(key)
      Deferred.new(@j_del.hLen(key._to_java_buffer))
    end

    def h_mget(key, *fields)
      Deferred.new(@j_del.hmGet(key._to_java_buffer, rbuff_arr_to_java(*fields))) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def h_mset(key, hash)
      Deferred.new(@j_del.hmSet(key._to_java_buffer, rbuff_hash_to_java(hash)))
    end

    def h_set(key, field, value)
      Deferred.new(@j_del.hSet(key._to_java_buffer, field._to_java_buffer, value._to_java_buffer))
    end

    def h_set_nx(key, field, value)
      Deferred.new(@j_del.hSetNx(key._to_java_buffer, field._to_java_buffer, value._to_java_buffer))
    end

    def h_vals(key)
      Deferred.new(@j_del.hVals(key._to_java_buffer)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def incr(key)
      Deferred.new(@j_del.incr(key._to_java_buffer))
    end

    def incr_by(key)
      Deferred.new(@j_del.incrBy(key._to_java_buffer))
    end

    def info
      Deferred.new(@j_del.info) { |j_buff| Buffer.new(j_buff)}
    end

    def keys(pattern)
      Deferred.new(@j_del.keys(pattern._to_java_buffer)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def last_save
      Deferred.new(@j_del.lastSave)
    end

    def l_index(key, index)
      Deferred.new(@j_del.lIndex(key._to_java_buffer, index)) { |j_buff| Buffer.new(j_buff)}
    end

    def l_insert(key, before, pivot, value)
      Deferred.new(@j_del.lIndex(key._to_java_buffer, index, before, pivot._to_java_buffer, value._to_java_buffer))
    end

    def l_len(key)
      Deferred.new(@j_del.lLen(key._to_java_buffer))
    end

    def l_pop(key)
      Deferred.new(@j_del.lPop(key._to_java_buffer)) { |j_buff| Buffer.new(j_buff)}
    end

    def l_push(key, value)
      Deferred.new(@j_del.lPush(key._to_java_buffer, value._to_java_buffer))
    end

    def l_push_x(key, value)
      Deferred.new(@j_del.lPushX(key._to_java_buffer, value._to_java_buffer))
    end

    def l_range(key, range_start, range_stop)
      Deferred.new(@j_del.lRange(key._to_java_buffer, range_start, range_stop)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def l_rem(key, count, value)
      Deferred.new(@j_del.lRem(key._to_java_buffer, count, value._to_java_buffer))
    end

    def l_set(key, index, value)
      Deferred.new(@j_del.lSet(key._to_java_buffer, index, value._to_java_buffer))
    end

    def l_trim(key, range_start, range_stop)
      Deferred.new(@j_del.l_trim(key._to_java_buffer, range_start, range_stop))
    end

    def mget(key, *fields)
      Deferred.new(@j_del.mGet(key._to_java_buffer, rbuff_arr_to_java(*fields))) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def move(key, db)
      Deferred.new(@j_del.move(key._to_java_buffer, db._to_java_buffer))
    end

    def mset(hash)
      Deferred.new(@j_del.mSet(rbuff_hash_to_java(hash)))
    end

    def mset_nx(hash)
      Deferred.new(@j_del.mSetNx(rbuff_hash_to_java(hash)))
    end

    def multi
      Deferred.new(@j_del.multi)
    end

    def persist(key)
      Deferred.new(@j_del.persist(key._to_java_buffer))
    end

    def ping
      Deferred.new(@j_del.ping)
    end

    def psubscribe(*patterns)
      Deferred.new(@j_del.psubscribe(rbuff_arr_to_java(*patterns)))
    end

    def publish(channel, message)
      Deferred.new(@j_del.publish(channel._to_java_buffer, message._to_java_buffer))
    end

    def punsubscribe(*patterns)
      Deferred.new(@j_del.punsubscribe(rbuff_arr_to_java(*patterns)))
    end

    def random_key
      Deferred.new(@j_del.ping) { |j_buff| Buffer.new(j_buff)}
    end

    def rename(key, new_key)
      Deferred.new(@j_del.rename(key._to_java_buffer, new_key._to_java_buffer))
    end

    def rename_nx(key, new_key)
      Deferred.new(@j_del.renameNX(key._to_java_buffer, new_key._to_java_buffer))
    end

    def r_pop(key)
      Deferred.new(@j_del.rPop(key._to_java_buffer)) { |j_buff| Buffer.new(j_buff)}
    end

    def r_pop_l_push(source, destination)
      Deferred.new(@j_del.rPoplPush(source._to_java_buffer, destination._to_java_buffer)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def r_push(key, *values)
      Deferred.new(@j_del.rPush(key._to_java_buffer, rbuff_arr_to_java(*values)))
    end

    def r_push_x(key, value)
      Deferred.new(@j_del.rPushX(key._to_java_buffer, value._to_java_buffer))
    end

    def s_add(key, *members)
      Deferred.new(@j_del.sAdd(key._to_java_buffer, rbuff_arr_to_java(*members)))
    end

    def save
      Deferred.new(@j_del.save)
    end

    def s_card(key)
      Deferred.new(@j_del.sCard(key._to_java_buffer))
    end

    def s_diff(key, *others)
      Deferred.new(@j_del.sDiff(key._to_java_buffer, rbuff_arr_to_java(*others))) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def s_diff_store(destination, key, *others)
      Deferred.new(@j_del.sDiffStore(destination._to_java_buffer, key._to_java_buffer, rbuff_arr_to_java(*others)))
    end

    def select(index)
      Deferred.new(@j_del.select(index))
    end

    def set(key, value)
      Deferred.new(@j_del.set(key._to_java_buffer, value._to_java_buffer))
    end

    def set_bit(key, offset, value)
      Deferred.new(@j_del.setBit(key._to_java_buffer, offset, value._to_java_buffer))
    end

    def set_ex(key, seconds, value)
      Deferred.new(@j_del.setEx(key._to_java_buffer, seconds, value._to_java_buffer))
    end

    def set_nx(key, value)
      Deferred.new(@j_del.setNx(key._to_java_buffer, value._to_java_buffer))
    end

    def set_range(key, offset, value)
      Deferred.new(@j_del.setRange(key._to_java_buffer, offset, value._to_java_buffer))
    end

    def shutdown
      Deferred.new(@j_del.shutdown)
    end

    def s_inter(*keys)
      Deferred.new(@j_del.sInter(rbuff_arr_to_java(*keys))) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def s_inter_store(destination, *keys)
      Deferred.new(@j_del.sInterStore(destination._to_java_buffer, rbuff_arr_to_java(*keys)))
    end

    def s_is_member(key, value)
      Deferred.new(@j_del.setIsMember(key._to_java_buffer, value._to_java_buffer))
    end

    def slave_of(host, port)
      Deferred.new(@j_del.slaveOf(host, port))
    end

    def s_members(key)
      Deferred.new(@j_del.sMembers(key._to_java_buffer)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def s_move(source, destination, member)
      Deferred.new(@j_del.sMove(source._to_java_buffer, destination._to_java_buffer, member._to_java_buffer))
    end

    def sort(key, pattern = nil, offset = -1, count = -1, get_patterns = nil, ascending = true,
             alpha = false, store_destination = nil)
      Deferred.new(@j_del.sort(key._to_java_buffer, pattern = nil ? nil : pattern._to_java_buffer,
                               offset, count, get_patterns = nil ? nil : rbuff_arr_to_java(get_patterns),
                               ascending, alpha, store_destination = nil ? nil : store_destination._to_java_buffer))
    end

    def s_pop(key)
      Deferred.new(@j_del.sPop(key._to_java_buffer)) { |j_buff| Buffer.new(j_buff)}
    end

    def s_rand_member(key)
      Deferred.new(@j_del.sRandMember(key._to_java_buffer)) { |j_buff| Buffer.new(j_buff)}
    end

    def s_rem(key, *members)
      Deferred.new(@j_del.sRem(key._to_java_buffer, rbuff_arr_to_java(*members)))
    end

    def str_len(key)
      Deferred.new(@j_del.strLen(key._to_java_buffer))
    end

    def subscribe(*channels)
      Deferred.new(@j_del.subscribe(rbuff_arr_to_java(*channels)))
    end

    def s_union(*keys)
      Deferred.new(@j_del.sUnion(rbuff_arr_to_java(*keys))) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def s_union_store(destination, *keys)
      Deferred.new(@j_del.sUnionStore(destination._to_java_buffer, rbuff_arr_to_java(*keys)))
    end

    def ttl(key)
      Deferred.new(@j_del.ttl(key._to_java_buffer))
    end

    def type(key)
      Deferred.new(@j_del.type(key._to_java_buffer))
    end

    def unsubscribe(*channels)
      Deferred.new(@j_del.unsubscribe(rbuff_arr_to_java(*channels)))
    end

    def unwatch
      Deferred.new(@j_del.unwatch)
    end

    def watch(*keys)
      Deferred.new(@j_del.watch(rbuff_arr_to_java(*keys)))
    end

    def z_add(key, hash)
      Deferred.new(@j_del.zAdd(key._to_java_buffer, rbuff_hash_to_java(hash)))
    end

    def z_add_member(key, score, member)
      Deferred.new(@j_del.zAdd(key._to_java_buffer, score, member._to_java_buffer))
    end

    def z_card(key)
      Deferred.new(@j_del.zCard(key._to_java_buffer))
    end

    def z_count(key, min, max)
      Deferred.new(@j_del.zCard(key._to_java_buffer, min, max))
    end

    def z_incr_by(key, increment, member)
      Deferred.new(@j_del.zIncrBy(key._to_java_buffer, increment, member._to_java_buffer))
    end

    def z_inter_store(destination, num_keys, keys, weights = nil, aggregate_type = 'SUM')
      j_agg_type = case aggregate_type
                     when 'SUM'
                       org.nodex.java.addons.redis.RedisConnection.AggregateType::SUM
                     when 'MIN'
                       org.nodex.java.addons.redis.RedisConnection.AggregateType::MIN
                     when 'MAX'
                       org.nodex.java.addons.redis.RedisConnection.AggregateType::MAX
                     else
                       raise "Legal aggregate_type values are SUM, MIN or MAX"
                     end
      Deferred.new(@j_del.zInterStore(destination._to_java_buffer, num_keys, keys, weights, j_agg_type))
    end

    def z_range(key, range_start, range_stop, with_scores = false)
      Deferred.new(@j_del.zRange(key._to_java_buffer, range_start, range_stop, with_scores)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def z_range_by_score(key, min, max, with_scores = false, offset = -1, count = -1)
      Deferred.new(@j_del.zRangeByScore(key._to_java_buffer, min, max, with_scores, offset, count)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def z_rank(key, member)
      Deferred.new(@j_del.zRank(key._to_java_buffer, member._to_java_buffer))
    end

    def z_rem(key, *members)
      Deferred.new(@j_del.zRem(key._to_java_buffer, rbuff_arr_to_java(*members)))
    end

    def z_rem_range_by_rank(key, range_start, range_stop)
      Deferred.new(@j_del.zRemRangeByRank(key._to_java_buffer, range_start, range_stop))
    end

    def z_rem_range_by_score(key, min, max)
      Deferred.new(@j_del.zRemRangeByScore(key._to_java_buffer, min, max))
    end

    def z_rev_range(key, range_start, range_stop, with_scores = false)
      Deferred.new(@j_del.zRevRange(key._to_java_buffer, range_start, range_stop, with_scores)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def z_rev_range_by_score(key, min, max, with_scores = false, offset = -1, count = -1)
      Deferred.new(@j_del.zRevRangeByScore(key._to_java_buffer, min, max, with_scores, offset, count)) { |j_arr| jbuff_arr_to_ruby(j_arr) }
    end

    def z_rev_rank(key, member)
      Deferred.new(@j_del.zRevRank(key._to_java_buffer, member._to_java_buffer))
    end

    def z_score(key, member)
      Deferred.new(@j_del.zScore(key._to_java_buffer, member._to_java_buffer))
    end

    def z_union_store(destination, num_keys, keys, weights = nil, aggregate_type = 'SUM')
      j_agg_type = case aggregate_type
                     when 'SUM'
                       org.nodex.java.addons.redis.RedisConnection.AggregateType::SUM
                     when 'MIN'
                       org.nodex.java.addons.redis.RedisConnection.AggregateType::MIN
                     when 'MAX'
                       org.nodex.java.addons.redis.RedisConnection.AggregateType::MAX
                     else
                       raise "Legal aggregate_type values are SUM, MIN or MAX"
                     end
      Deferred.new(@j_del.zUnionStore(destination._to_java_buffer, num_keys, keys, weights, j_agg_type))
    end

    private

    # convert ruby hash of ruby buffers to map of Java buffers
    def rbuff_hash_to_java(hash)
      j_map = java.util.HashMap.new(hash.size)
      hash.each { |k, v| j_map.put(k._to_java_buffer, v._to_java_buffer) }
    end

    # convert array of Java buffers to array of ruby buffs
    def jbuff_arr_to_ruby(*buffs)
      rarr = []
      buffs.each {|j_buff| rarr << Buffer.new(j_buff) }
      rarr
    end

    # convert array of Ruby buffs to array of Java buffs
    def rbuff_arr_to_java(*buffs)
      j_arr = Java::OrgNodexJavaCoreBuffer::Buffer[buffs.size].new
      for i in 0...buffs.size do
        j_arr[i] = buffs[i]._to_java_buffer
      end
      j_arr
    end

  end
end
