include Java

require "composition"

module Redis
  class RedisClient

    class ConnectionCallback < org.nodex.core.redis.RedisConnectHandler
      def initialize(connect_block)
        super()
        @connect_block = connect_block
      end

      def onConnect(connection)
        @connect_block.call(Connection.new(connection))
      end

      private :initialize
    end

    def RedisClient.create_client
      RedisClient.new(org.nodex.core.redis.RedisClient.createClient)
    end

    def initialize(java_client)
      @java_client = java_client
    end

    def connect(port, host, proc = nil, &connect_block)
      connect_block = proc if proc
      @java_client.connect(port, host, ConnectionCallback.new(connect_block))
    end

    private :initialize
  end

  class Connection

    class OnCompleteCallback < org.nodex.core.DoneHandler
      def initialize(complete_block)
        super()
        @complete_block = complete_block
      end
      def onDone()
        @complete_block.call
      end
      private :initialize
    end

    class ResultCallback < org.nodex.core.redis.ResultHandler
      def initialize(result_block)
        super()
        @result_block = result_block
      end
      def onResult(value)
        @result_block.call(value)
      end
      private :initialize
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
      Completion.create_from_java_completion(java_completion)
    end

    def close
      @java_connection.close
    end
  end
end