require "http"
include Http

server = Server.create_server { |conn|
  conn.request { |req, resp| resp.write_str("<html><body><h1>Hello from Node.x!</h1></body></html>", "UTF-8").end }
}.listen(8080)

puts "hit enter to stop server"
STDIN.gets
server.stop