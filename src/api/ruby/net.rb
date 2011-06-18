require "java"

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

  def listen(port, host)
  
    @java_server.listen(port, host)
  
    puts "Listening on #{host}:#{port}"
    self
  end
  
  def on_connect(java_socket)
    socket = Socket.new(java_socket)
    @connect_block.call(socket)
    socket
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
    puts "data block is #{data_block}"
  end
  
  def data_received(data)
    puts "data received in ruby #{data}"
    @data_block.call(data) if @data_block
  end
  
  def write(data)
    puts "Writing data #{data}"
    @java_socket.write(data)
  end
  
end

module Net

  def Net.create_server(&connect_block)
    NetServer.new(connect_block)  
  end

end


