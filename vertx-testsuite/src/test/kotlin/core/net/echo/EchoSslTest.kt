package core.net.echo

import org.vertx.java.framework.TestBase
import org.vertx.java.core.streams.Pump

import org.vertx.java.core.buffer.Buffer
import org.vertx.java.framework.TestUtils

import org.vertx.kotlin.core.*
import org.vertx.java.core.Handler
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.lang.reflect.Method
import junit.framework.TestSuite
import org.vertx.java.framework.TestVerticle

public class KotlinNetTest() : TestBase() {
    protected override fun setUp() {
        super.setUp()
        startApp(javaClass<EchoSslApp>().getName())
    }

    public fun testEcho() {
        startTest(getMethodName())
    }

    public fun testEchoSSL() {
        startTest(getMethodName())
    }
}

public class KotlinNetScriptTest() : TestBase() {
    protected override fun setUp() {
        super.setUp()
        startApp("./vertx-testsuite/src/test/kotlin/core/net/echo/EchoSslTest.ktscript")
    }

    public fun testEcho() {
        startTest(getMethodName())
    }

    public fun testEchoSSL() {
        startTest(getMethodName())
    }
}

public class EchoSslApp() : TestVerticle() {
    public fun testEcho() {
        echo(false)
    }

    public fun testEchoSSL() {
        echo(true)
    }

    fun echo(ssl: Boolean) {

        val server = createNetServer() {
            if(ssl) {
                SSL = true
                keyStorePath = "./vertx-testsuite/src/test/keystores/server-keystore.jks"
                keyStorePassword = "wibble"
                trustStorePath = "./vertx-testsuite/src/test/keystores/server-truststore.jks"
                trustStorePassword = "wibble"
                clientAuthRequired = true
            }
            
            connectHandler { socket ->
                testUtils!!!!.checkContext()
                socket.dataHandler { buffer ->
                    testUtils!!!!.checkContext()
                    socket.write(buffer)
                }
            }
        }.listen(8080)


        val client = createNetClient() {
            if(ssl)  {
                SSL = true
                keyStorePath = "./vertx-testsuite/src/test/keystores/client-keystore.jks"
                keyStorePassword = "wibble"
                trustStorePath = "./vertx-testsuite/src/test/keystores/client-truststore.jks"
                trustStorePassword = "wibble"
            }
        }

        client.connect(8080, "localhost") { socket ->
            testUtils!!.checkContext()

            val sends = 10
            val size = 100

            val sent = Buffer()
            val received = Buffer()

            socket {
                dataHandler { buffer ->
                    testUtils!!.checkContext()

                    received.appendBuffer(buffer)

                    if (received.length() == sends * size) {
                        testUtils!!.azzert(TestUtils.buffersEqual(sent, received))

                        // FIXME: kotlin compiler thinks that server is nullable. why?
                        server!!.close{
                            client.close()
                            testUtils!!.testComplete()
                        }
                    }
                }

                endHandler {
                    testUtils!!.checkContext()
                }

                closedHandler {
                    testUtils!!.checkContext()
                }

                drainHandler {
                    testUtils!!.checkContext()
                }

                pause()
                resume()
            }

            sends.times {
                val data = TestUtils.generateRandomBuffer(size)
                sent.appendBuffer(data)
                socket.write(data)
            }
        }
    }
}