require "net"

Server.create_server{|socket| socket.data{|data| socket.write(data)}}.listen(8080, "127.0.0.1")


