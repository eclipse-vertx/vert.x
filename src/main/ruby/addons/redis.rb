# Copyright 2011 VMware, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'core/composition'
require 'core/buffer'

module Nodex

  class RedisPool

    def initialize
      @j_del = org.nodex.java.addons.redis.RedisPool.new
    end

    def port=(port)
      @j_del.setPort(port)
    end

    def host=(host)
      @j_del.setHost(host)
    end

    def max_pool_size=(max)
      @j_del.setMaxPoolSize(max)
    end

    def password=(pwd)
      @j_del.setPassword(pwd)
    end

    def close
      @j_del.close
    end

    def connection
      RedisConnection.new(@j_del.connection)
    end

  end

  class RedisConnection

    # @private
    def initialize(j_del)
      @j_del = j_del
    end

    def close
      Future.new(@j_del.close, nil)
    end

    def close_deferred
      Deferred.new(@j_del.close, nil)
    end

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

    def bl_pop(timeout, *keys)
      Deferred.new(@j_del.bLPop(timeout, convert_buff_array(keys)))
    end

    def br_pop(timeout, *keys)
      Deferred.new(@j_del.bRPop(timeout, convert_buff_array(keys)))
    end








    def convert_buff_array(*buffs)
      # convert to Java array of Java buffs
    end

  end
end
