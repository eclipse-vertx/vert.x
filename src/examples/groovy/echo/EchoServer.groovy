package echo

import org.vertx.groovy.core.net.NetServer

server = new NetServer().connectHandler { socket ->
  socket.dataHandler { buffer ->
    socket.write buffer
  }
}.listen(1234)


void vertxStop() {
  server.close()
}


