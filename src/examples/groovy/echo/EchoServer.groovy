import org.vertx.groovy.core.net.NetServer
import org.vertx.groovy.core.streams.Pump

new NetServer().connectHandler { socket ->
  new Pump(socket, socket).start()
}.listen(1234)


