package core.net

import org.vertx.groovy.core.net.NetClient
import org.vertx.groovy.framework.TestUtils
import org.vertx.groovy.core.net.NetServer
import org.vertx.groovy.core.buffer.Buffer

tu = new TestUtils()
tu.checkContext()

client = null

void testEcho() {
  echo(false)
}

void testEchoSSL() {
  echo(true)  
}

void echo(boolean ssl) {

  server = new NetServer();

  if (ssl) {
    server.setSSL(true)
    server.setKeyStorePath("./src/tests/keystores/server-keystore.jks")
    server.setKeyStorePassword("wibble")
    server.setTrustStorePath("./src/tests/keystores/server-truststore.jks")
    server.setTrustStorePassword("wibble")
    server.setClientAuthRequired(true)
  }

  server.connectHandler { socket ->
    tu.checkContext()
    socket.dataHandler { buffer ->
      tu.checkContext()
      socket << buffer
    }
  }.listen(8080)


  client = new NetClient()

  if (ssl) {
    client.setSSL(true)
    client.setKeyStorePath("./src/tests/keystores/client-keystore.jks")
    client.setKeyStorePassword("wibble")
    client.setTrustStorePath("./src/tests/keystores/client-truststore.jks")
    client.setTrustStorePassword("wibble")
  }

  client.connect(8080, "localhost", { socket ->
    tu.checkContext()

    sends = 10
    size = 100

    sent = new Buffer()
    received = new Buffer()

    socket.dataHandler { buffer ->
      tu.checkContext()

      received << buffer

      if (received.length() == sends * size) {
        tu.azzert(TestUtils.buffersEqual(sent, received))

        server.close {
          client.close()
          tu.testComplete()
        }

      }
    }

    socket.endHandler {
      tu.checkContext()
    }

    socket.closedHandler {
      tu.checkContext()
    }

    socket.drainHandler {
      tu.checkContext()
    }

    socket.pause()

    socket.resume()

    sends.times {
      Buffer data = TestUtils.generateRandomBuffer(size)
      sent << data
      socket << data
    }

  })
}

tu.registerTests(this)
tu.appReady()

void vertxStop() {
  if (client != null) client.close()
  tu.unregisterAll()
  tu.appStopped()
}

