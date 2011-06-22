require "../../api/ruby/net"

Net.create_server{ |socket|
  (@sockets ||= []) << socket
  socket.data{
    |data| @sockets.each{
      |socket| socket.write(data)
    }
  }
}.listen(8080, "127.0.0.1")

#Prevent script from exiting
STDIN.gets
