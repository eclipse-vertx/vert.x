
import org.vertx.groovy.core.net.NetClient
import org.vertx.java.core.buffer.Buffer
import org.vertx.groovy.newtests.TestUtils

tu = new TestUtils()

client = null

tu.register("test1", {
  client = new NetClient().connect(8080, "localhost", { socket ->

    socket.dataHandler { buffer ->
      println "Net client receiving: ${buffer.toString("UTF-8")}"
      tu.testComplete("test1")
    }

    String str = "hello"
    socket << Buffer.create(str)
  })
})

tu.appReady()

void vertxStop() {
  if (client != null) client.close()
  tu.appStopped()
}

