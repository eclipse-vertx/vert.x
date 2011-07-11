require "net"
include Net

# A simple echo server which just echos back any data it receives
# Uses this with telnet, or the echo client

server = Server.create_server { |socket| socket.data { |data| socket.write(data) } }.listen(8080)

puts "hit enter to stop server"
STDIN.gets
server.stop


