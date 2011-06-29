require "buffer"

module Stomp
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
      @java_connection.send(dest, body)
    end

    def send(dest, headers, body)
      @java_connection.send(dest, headers, body)
    end

    def send(dest, body, proc = nil, &receipt_block)
      receipt_block = proc if proc
      @java_connection.send(dest, body, ReceiptCallback.new(receipt_block))
    end

    def send(dest, headers, body, proc = nil, &receipt_block)
      receipt_block = proc if proc
      @java_connection.send(dest, headers, body, ReceiptCallback.new(receipt_block))
    end

    def subscribe(dest, proc = nil, &message_block)
      message_block = proc if proc
      @java_connection.subscribe(dest, MessageCallback.new(message_block))
    end

    class MessageCallback < org.nodex.core.Callback
      def initialize(message_block)
        super()
        @message_block = message_block
      end

      def onEvent(msg)
        # FIXME - convert message to Ruby wrapper?
        @message_block.call(msg)
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