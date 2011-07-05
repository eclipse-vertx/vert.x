include Java

module Amqp
  class Client

    attr_accessor :host, :port, :username, :password, :virtual_host

    def Client.create_client
      java_client = org.nodex.core.amqp.Client.createClient
      Client.new(java_client)
    end

    def initialize(java_client)
      @java_client = java_client
    end

    def connect(proc = nil, &connect_block)
      connect_block = proc if proc
      @java_client.connect(ConnectionCallback.new(connect_block))
    end

    private :initialize

    class ConnectionCallback < org.nodex.core.Callback
      def initialize(connect_block)
        super()
        @connect_block = connect_block
      end

      def onEvent(connection)
        @connect_block.call(Connection.new(connection))
      end

      private :initialize
    end
  end

  class Connection
    def initialize(java_connection)
      @java_connection = java_connection
    end

    def create_channel(proc = nil, &channel_block)
      channel_block = proc if proc
      @java_connection.createChannel(ChannelCallback.new(channel_block))
    end

    def close
      @java_connection.close
    end

    private :initialize

    class ChannelCallback < org.nodex.core.Callback
      def initialize(channel_block)
        super()
        @channel_block = channel_block
      end

      def onEvent(channel)
        @channel_block.call(Channel.new(channel))
      end

      private :initialize
    end
  end

  class Channel

    def initialize(java_channel)
      @java_channel = java_channel
    end

    def publish(exchange, routing_key, message)
      @java_channel.publish(exchange, routing_key, message)
    end

    def subscribe(queue_name, auto_ack, proc = nil, &message_handler)
      message_handler = proc if proc
      @java_channel.subscribe(queue_name, auto_ack, MessageHandler.new(message_handler))
    end

    def declare(queue_name, durable, exclusive, auto_delete, proc = nil, &complete_block)
      complete_block = proc if proc
      @java_channel.declare(queue_name, durable, exclusive, auto_delete, CompleteCallback.new(complete_block))
    end

    def close
      @java_channel.close
    end

    private :initialize

    class MessageHandler < org.nodex.core.Callback
      def initialize(messageHandler)
        super()
        @messageHandler = messageHandler
      end

      def onEvent(msg)
        @messageHandler.call(msg)
      end

      private :initialize
    end

    class CompleteCallback < org.nodex.core.NoArgCallback
      def initialize(callback)
        super()
        @callback = callback
      end

      def onEvent()
        @callback.call
      end

      private :initialize
    end
  end
end