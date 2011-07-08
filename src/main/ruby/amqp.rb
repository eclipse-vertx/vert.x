include Java

require "composition"

module Amqp
  class AmqpClient

    attr_accessor :host, :port, :username, :password, :virtual_host

    def AmqpClient.create_client
      java_client = org.nodex.core.amqp.AmqpClient.createClient
      AmqpClient.new(java_client)
    end

    def initialize(java_client)
      @java_client = java_client
    end

    def connect(proc = nil, &connect_block)
      connect_block = proc if proc
      @java_client.connect(ConnectionCallback.new(connect_block))
    end

    private :initialize

    class ConnectionCallback < org.nodex.core.amqp.AmqpConnectHandler
      def initialize(connect_block)
        super()
        @connect_block = connect_block
      end

      def onConnect(connection)
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

    class ChannelCallback < org.nodex.core.amqp.ChannelHandler
      def initialize(channel_block)
        super()
        @channel_block = channel_block
      end

      def onCreate(channel)
        @channel_block.call(Channel.new(channel))
      end

      private :initialize
    end
  end

  class Channel

    def initialize(java_channel)
      @java_channel = java_channel
    end

    def publish_with_props(exchange, routing_key, props, message)
      @java_channel.publish(exchange, routing_key, props.to_java_props, message.to_s)
    end

    def publish(exchange, routing_key, message)
      publish_with_props(exchange, routing_key, nil, message)
    end

    def request(exchange, routing_key, props, message, proc = nil, &response_block)
      response_block = proc if proc
      java_completion = @java_channel.request(exchange, routing_key, props.to_java_props, message.to_s, MessageHandler.new(response_block))
      Completion.create_from_java_completion(java_completion)
    end

    def subscribe(queue_name, auto_ack, proc = nil, &message_handler)
      message_handler = proc if proc
      @java_channel.subscribe(queue_name, auto_ack, MessageHandler.new(message_handler))
    end

    def declare_queue(queue_name, durable, exclusive, auto_delete, proc = nil, &complete_block)
      complete_block = proc if proc
      @java_channel.declareQueue(queue_name, durable, exclusive, auto_delete, CompleteCallback.new(complete_block))
    end

    def close
      @java_channel.close
    end

    private :initialize

    class MessageHandler < org.nodex.core.amqp.AmqpMsgCallback
      def initialize(messageHandler)
        super()
        @messageHandler = messageHandler
      end

      def onMessage(props, body)
        java_string = java.lang.String.new(body, "UTF-8")
        @messageHandler.call(Props.from_java_props(props), java_string)
      end

      private :initialize
    end

    class CompleteCallback < org.nodex.core.DoneHandler
      def initialize(callback)
        super()
        @callback = callback
      end

      def onDone()
        @callback.call
      end

      private :initialize
    end
  end

  class Props

    attr_accessor :app_id, :class_id, :class_name, :cluster_id, :content_encoding, :content_type,
                  :correlation_id, :delivery_mode, :expiration, :headers, :message_id, :priority,
                  :reply_to, :timestamp, :type, :user_id

    def initialize
      @headers = {}
      @delivery_mode = 1

    end

    def Props.from_java_props(java_props)
      props = Props.new
      props.app_id = java_props.appId;
      props.class_id = java_props.classId;
      props.cluster_id = java_props.clusterId;
      props.content_encoding = java_props.contentEncoding;
      props.content_type = java_props.contentType;
      props.correlation_id = java_props.correlationId;
      props.delivery_mode = java_props.deliveryMode;
      props.expiration = java_props.expiration;
      props.headers = java_props.headers
      props.message_id = java_props.messageId
      props.priority = java_props.priority
      props.reply_to = java_props.replyTo
      props.timestamp = java_props.timestamp
      props.type = java_props.type
      props.user_id = java_props.userId
      props
    end

    def to_java_props
      java_props = org.nodex.core.amqp.AmqpProps.new
      java_props.appId = app_id
      java_props.classId = class_id
      java_props.clusterId = cluster_id
      java_props.contentEncoding = content_encoding
      java_props.correlationId = correlation_id
      java_props.deliveryMode = delivery_mode
      java_props.expiration = expiration
      java_props.headers = headers
      java_props.messageId = message_id
      java_props.replyTo = reply_to
      java_props.timestamp = timestamp
      java_props.type = type
      java_props.userId = user_id
      java_props
    end

  end

  class ChannelPool
    attr_accessor :host, :port, :username, :password, :virtual_host

    def initialize(java_pool)
      @java_pool = java_pool
    end

    def ChannelPool.create_pool
      java_pool = org.nodex.core.amqp.ChannelPool.createPool
      ChannelPool.new(java_pool)
    end

    def get_channel(proc = nil, &channel_handler)
      channel_handler = proc if proc
      @java_pool.getChannel(ChannelHandler.new(channel_handler))
    end

    class ChannelHandler < org.nodex.core.amqp.ChannelHandler

      def initialize(channel_handler)
        super()
        @channel_handler = channel_handler
      end

      def onCreate(java_channel)
        @channel_handler.call(Channel.new(java_channel))
      end

      private :initialize
    end

    private :initialize
  end
end