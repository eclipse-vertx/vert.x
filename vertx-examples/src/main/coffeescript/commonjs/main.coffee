Server = require("./server.coffee")
handler = require("./handler.coffee")

server = new Server(8080, "localhost")

server.use handler
server.start()