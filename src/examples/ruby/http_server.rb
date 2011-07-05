require "http"
include Http

server = Server.create_server{|conn|
  conn.request{|req, resp|
    puts "request uri is #{req.uri}"
    resp.headers["Content-Type"] = "text/html; charset=UTF-8"
    resp.write("<html><body><h1>Hello from Node.x!</h1></body></html>", "UTF-8").end
  }
}.listen(8080)

puts "hit enter to stop server"
STDIN.gets
server.stop