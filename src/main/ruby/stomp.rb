include Java
require "buffer"

module Stomp
  class Client
    class ConnectCallback < org.nodex.core.Callback

      def initialize(connect_block)
        super()
        @connect_block = connect_block
      end

      def onEvent(java_connection)
        conn = Connection.new(java_connection)
        @connect_block.call(conn)
      end
    end

    #Can take either a proc or a block
    def Client.connect(port, host = "localhost", proc = nil, &connect_block)
      connect_block = proc if proc
      Client.new(port, host, connect_block)
    end

    def initialize(port, host, connect_block)
      super()
      @java_client = org.nodex.core.stomp.Client.connect(port, host, ConnectCallback.new(connect_block))
    end

    private :initialize
  end

  class Connection

    def initialize(java_connection)
      @java_connection = java_connection
    end

    def message(proc = nil, &message_block)
      message_block = proc if proc
      @message_block = message_block
    end

    def error(proc = nil, &error_block)
      error_block = proc if proc
      @error_block = error_block
    end

    def close
      @java_connection.close
    end

    def send(dest, body)
      @java_connection.send(dest, body._to_java_buffer)
    end

    def send_with_headers(dest, headers, body)
      @java_connection.send(dest, headers, body._to_java_buffer)
    end

    def subscribe(dest, proc = nil, &message_block)
      message_block = proc if proc
      @java_connection.subscribe(dest, MsgCallback.new(message_block))
    end

    class MsgCallback < org.nodex.core.stomp.MessageCallback
      def initialize(message_block)
        super()
        @message_block = message_block
      end

      def onMessage(java_headers, java_body)
        @message_block.call(java_headers, Buffer.new(java_body))
      end
    end

    class ReceiptCallback < org.nodex.core.Callback
      def initialize(receipt_block)
        super()
        @receipt_block = receipt_block
      end

      def onEvent
        @receipt_block.call()
      end
    end

    private :initialize

  end
end

