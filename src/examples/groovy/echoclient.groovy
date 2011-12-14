import org.vertx.groovy.core.Vertx
import org.vertx.groovy.core.net.NetClient
import org.vertx.java.core.buffer.Buffer

Vertx.go {

  for (int i in 0..<10) {
    new NetClient().connect(8080, "localhost", { socket, index ->

      socket.dataHandler { buffer ->
        println "Net client receiving: ${buffer.toString("UTF-8")}"
      }

      // Now send some data
      String str = "hello $index\n"
      print "Net client sending: $str"
      socket << Buffer.create(str)
    }.rcurry(i))
  }

}

println "Press Ctrl-C to exit"
while (true) Thread.currentThread().sleep(10000)
