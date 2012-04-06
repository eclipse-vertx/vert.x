import org.vertx.groovy.core.streams.Pump

vertx.createNetServer().connectHandler { socket ->
  Pump.createPump(socket, socket).start()
}.listen(1234)


