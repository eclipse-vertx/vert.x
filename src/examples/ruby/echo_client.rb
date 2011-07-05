require "net"
require "buffer"

Client.connect(8080, "localhost") { |socket|
  socket.data { |data| puts "AmqpClient received #{data.to_s}" }
  (1..10).each { |i|
    str = "hello #{i}\n"
    puts "AmqpClient sending #{str}"
    socket.write(Buffer.from_str(str))
  }
}


