require "net"
require "buffer"
include Net

# A simple echo client which sends some data and displays it as it gets echoed back
# Make sure you start the echo server before running this

Client.create_client.connect(8080, "localhost") { |socket|
  socket.data { |data| puts "Echo client received #{data.to_s}" }
  (1..10).each { |i|
    str = "hello #{i}\n"
    puts "Echo client sending #{str}"
    socket.write(Buffer.from_str(str))
  }
}


