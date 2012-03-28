package echo

import org.vertx.groovy.core.net.NetClient
import org.vertx.groovy.core.buffer.Buffer


new NetClient().connect(1234, "localhost") { socket->

  socket.dataHandler { buffer ->
    println "Net client receiving: ${buffer.toString()}"
  }

  // Now send some data
  10.times {
    String str = "hello $it\n"
    print "Net client sending: $str"
    socket << new Buffer(str)
  }
}
