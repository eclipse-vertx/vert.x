#There must be a better way of doing this!!
$LOAD_PATH << ENV['LOAD_PATH']

require "net"

Server.create_server{ |socket|
  (@sockets ||= []) << socket
  socket.data{
    |data| @sockets.each{
      |socket| socket.write(data)
    }
  }
}.listen(8080, "127.0.0.1")

