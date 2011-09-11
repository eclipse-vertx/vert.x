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

include Java

require "composition"

module Redis
  class RedisClient

    class ConnectionCallback < org.nodex.java.core.redis.RedisConnectHandler
      def initialize(connect_block)
        super()
        @connect_block = connect_block
      end

      def onConnect(connection)
        @connect_block.call(Connection.new(connection))
      end


    end

    def RedisClient.create_client
      RedisClient.new(org.nodex.java.core.redis.RedisClient.createClient)
    end

    def initialize(java_client)
      @java_client = java_client
    end

    def connect(port, host, proc = nil, &connect_block)
      connect_block = proc if proc
      @java_client.connect(port, host, ConnectionCallback.new(connect_block))
    end


  end

  class Connection

    class OnCompleteCallback < org.nodex.java.core.Runnable
      def initialize(complete_block)
        super()
        @complete_block = complete_block
      end

      def run()
        @complete_block.call
      end


    end

    class ResultCallback < org.nodex.java.core.redis.ResultHandler
      def initialize(result_block)
        super()
        @result_block = result_block
      end

      def onResult(value)
        @result_block.call(value)
      end


    end

    def initialize(java_connection)
      @java_connection = java_connection
    end

    def set(key, value, proc = nil, &on_complete)
      on_complete = proc if proc
      @java_connection.set(key, value, OnCompleteCallback.new(on_complete))
    end

    def get(key, proc = nil, &result_callback)
      result_callback = proc if proc
      java_completion = @java_connection.get(key, ResultCallback.new(result_callback))
      Composition::Completion.create_from_java_completion(java_completion)
    end

    def close
      @java_connection.close
    end
  end
end