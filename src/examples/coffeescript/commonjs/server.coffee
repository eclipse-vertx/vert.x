vertx = require("vertx")

class Server 
    constructor: (@port, @host) ->
        @server = vertx.createHttpServer()
    
    use: (handler) ->
        @server.requestHandler handler

    start: ->
        @server.listen @port, @host        

module.exports = Server