require "net"
include Net

server = Server.create_server { |socket| socket.data { |data| socket.write(data) } }.listen(8080)

puts "hit enter to stop server"
STDIN.gets
server.stop


