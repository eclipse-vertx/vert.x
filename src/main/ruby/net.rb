include Java
require "buffer"

module Net
  class Server
    class ConnectCallback < org.nodex.core.Callback

      def initialize(connect_block)
        super()
        @connect_block = connect_block
      end

      def onEvent(java_socket)
        sock = Socket.new(java_socket)
        @connect_block.call(sock)
      end
    end

    #Can take either a proc or a block
    def Server.create_server(proc = nil, &connect_block)
      connect_block = proc if proc
      Server.new(connect_block)
    end

    def initialize(connect_block)
      super()
      @java_server = org.nodex.core.net.Server.createServer(ConnectCallback.new(connect_block))
    end

    def listen(port, host = "0.0.0.0")
      @java_server.listen(port, host)
      self
    end

    def stop
      @java_server.stop
    end

    private :initialize
  end

  class Client
    class ConnectCallback < org.nodex.core.Callback

      def initialize(connect_block)
        super()
        @connect_block = connect_block
      end

      def onEvent(java_socket)
        sock = Socket.new(java_socket)
        @connect_block.call(sock)
      end
    end

    #Can take either a proc or a block
    def Client.connect(port, host = "localhost", proc = nil, &connect_block)
      connect_block = proc if proc
      Client.new(port, host, connect_block)
    end

    def initialize(port, host, connect_block)
      super()
      @java_client = org.nodex.core.net.Client.connect(port, host, ConnectCallback.new(connect_block))
    end

    private :initialize
  end

  class Socket
    @data_block = nil

    class DataCallback < org.nodex.core.Callback
      def initialize(data_block)
        super()
        @data_block = data_block
      end

      def onEvent(java_buffer)
        buf = Buffer.new(java_buffer)
        @data_block.call(buf)
      end
    end

    def initialize(java_socket)
      super()
      @java_socket = java_socket
    end

    def write(data)
      @java_socket.write(data._to_java_buffer)
    end

    #Can take either a proc or a block
    def data(proc = nil, &data_block)
      data_block = proc if proc
      @java_socket.data(DataCallback.new(data_block))
    end
  end
end

