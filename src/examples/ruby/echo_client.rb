require "net"
require "buffer"

Client.connect(8080, "localhost") { |socket|
  socket.data{ |data| puts "Client received #{data.to_s}" }
  (1..10).each {|i|
    str = "hello #{i}\n"
    puts "Client sending #{str}"
    socket.write(Buffer.from_str(str))
  }
}


