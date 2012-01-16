
import org.vertx.groovy.core.net.NetServer
import org.vertx.groovy.newtests.TestUtils

println "Starting server"

tu = new TestUtils()

server = new NetServer().connectHandler { socket ->
  socket.dataHandler { buffer ->
    socket.write buffer
  }
}.listen(8080)

tu.appReady()

void vertxStop() {
  println "vertxStop called"
  server.close()
  tu.appStopped()
}


