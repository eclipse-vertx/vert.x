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
      puts "In ruby subscribe"
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

  class Parser < org.nodex.core.Callback
    def onEvent(java_frame)
      @java_parser.on_event(java_frame)
    end

    class OutputCallback < org.nodex.core.Callback
      def initialize(output_block)
        super()
        @output_block = output_block
      end

      def onEvent(frame)
        @output_block.call(frame)
      end
    end

    def initialize(proc = nil, &output_block)
      output_block = proc if proc
      @output_block = output_block
      @java_parser = org.nodex.core.stomp.Parser.new(OutputCallback.new(output_block))
    end
  end

  class Frame
    def initialize(java_frame)
      @java_frame = java_frame

      @command = java_frame.command
      @headers = org.jruby.RubyHash.new(java_frame.headers)
      @body = Buffer.new(java_frame.body)

      def to_buffer
        java_buf = @java_frame.toBuffer
        Buffer.new(java_buf)
      end

      def to_s
        @java_frame.toString
      end
    end
  end

end

