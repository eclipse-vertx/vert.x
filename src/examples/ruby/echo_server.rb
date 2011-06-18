require "../../api/ruby/net"

puts "node.x echo server"

server = Net.create_server{|socket| socket.data{ |data|
                                            puts "received data #{data}"
                                            socket.write(data)
                                            }
                          }.listen(8080, "127.0.0.1")


#Prevent script from exiting
STDIN.gets                      



