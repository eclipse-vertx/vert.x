include Java
require "buffer"

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

  def Server.create_server(&connect_block)
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

class Socket
  @data_block = nil

  class DataCallback < org.nodex.core.Callback
    def initialize(data_block)
      super()
      @data_block = data_block
    end

    def onEvent(java_buffer)
      buf = Buffer.new(java_buffer)
      @data_block.call(buf) if @data_block
    end
  end

  def initialize(java_socket)
    super()
    @java_socket = java_socket
  end

  def write(data)
    @java_socket.write(data._to_java_buffer)
  end

  def data(&data_block)
    @java_socket.data(DataCallback.new(data_block))
  end

end

