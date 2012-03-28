package echo

import org.vertx.groovy.core.net.NetServer
import org.vertx.groovy.core.streams.Pump

server = new NetServer().connectHandler { socket ->
  new Pump(socket, socket).start()
}.listen(1234)


void vertxStop() {
  server.close()
}


