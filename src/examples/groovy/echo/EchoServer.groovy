import org.vertx.groovy.core.net.NetServer
import java.util.concurrent.atomic.AtomicLong

class EchoServer implements VertxApp {

  def server

  void start() {
    server = new NetServer().connectHandler { socket ->
      socket.dataHandler { buffer ->
        socket.write buffer
      }
    }.listen(8080)
  }

  void stop() {
    server.close()
  }

}
