load "vertx.js"

client = vertx.createNetClient()
client.connect 1234, (sock) ->
  stdout.println "connected"
  sock.dataHandler (buffer) ->
    stdout.println "client receiving " + buffer.toString()

  i = 0

  while i < 10
    str = "hello" + i + "\n"
    stdout.println "Net client sending: " + str
    sock.write new vertx.Buffer(str)
    i++