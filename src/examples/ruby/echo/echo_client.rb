require "net"
require "buffer"
include Net

Client.connect(8080, "localhost") { |socket|
  socket.data { |data| puts "Echo client received #{data.to_s}" }
  (1..10).each { |i|
    str = "hello #{i}\n"
    puts "Echo client sending #{str}"
    socket.write(Buffer.from_str(str))
  }
}


