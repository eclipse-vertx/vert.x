require "java"
require "buffer"

include_class "org.nodex.core.net.JavaNetServer"
include_class "org.nodex.core.net.JavaNetSocket"
include_class "org.nodex.core.net.ServerCallback"
include_class "org.nodex.core.net.SocketCallback"

class NetServer < ServerCallback

  def initialize(connect_block)
    super()
    @connect_block = connect_block
    @java_server = JavaNetServer.new(self)
  end

  def listen(port, host = "0.0.0.0")
    @java_server.listen(port, host)
    puts "Listening on #{host}:#{port}"
    self
  end
  
  def on_connect(java_socket)
    socket = Socket.new(java_socket)
    @connect_block.call(socket)
    socket
  end

  def on_close
  end

  def on_exception(exception)
    puts "Exception occurred #{exception}"
  end

end

class Socket < SocketCallback
  @data_block = nil
  
  def initialize(java_socket)
    super()
    @java_socket = java_socket
    @java_socket.set_callback(self)
  end
  
  def data(&data_block)
    @data_block = data_block
  end

  def end(&end_block)
    @end_block = end_block
  end
  
  def data_received(data)
    @data_block.call(Buffer.new(data)) if @data_block
  end
  
  def write(data)
    @java_socket.write(data._to_java_buffer)
  end
end

module Net
  def Net.create_server(&connect_block)
    NetServer.new(connect_block)  
  end
end


