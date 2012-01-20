package core.net

import org.vertx.groovy.core.net.NetServer
import org.vertx.groovy.newtests.TestUtils

tu = new TestUtils()
tu.checkContext()

server = new NetServer().connectHandler { socket ->
  tu.checkContext()
  socket.dataHandler { buffer ->
    tu.checkContext()
    socket.write buffer
  }
}.listen(8080)

tu.appReady()

void vertxStop() {
  tu.checkContext()
  server.close()
  tu.appStopped()
}


