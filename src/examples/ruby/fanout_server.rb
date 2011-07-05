require "net"

Server.create_server { |socket|
  (@sockets ||= []) << socket
  socket.data {
      |data| @sockets.each {
        |socket| socket.write(data)
    }
  }
}.listen(8080)

puts "hit enter to stop server"
STDIN.gets
server.stop

