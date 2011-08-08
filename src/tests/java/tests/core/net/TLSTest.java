/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tests.core.net;

import org.nodex.core.ExceptionHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tests.Utils;
import tests.core.TestBase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TLSTest extends TestBase {

  @BeforeClass
  public void setUp() {
  }

  @AfterClass
  public void tearDown() {
  }

  @Test
  public void testClientAuthAll() throws Exception {
    testTLS(false, false, false, false, false, true, false);
    testTLS(false, false, true, false, false, true, true);
  }

  @Test
  public void testServerAuthOnly() throws Exception {
    testTLS(false, true, true, false, false, false, true);
    testTLS(false, false, true, false, false, false, false);
  }

  @Test
  public void testClientAndServerAuth() throws Exception {
    testTLS(true, true, true, true, true, false, true);
    testTLS(true, true, true, true, false, false, true);
    testTLS(false, true, true, true, true, false, false);
    testTLS(true, true, true, false, true, false, false);
    testTLS(false, true, true, false, true, false, false);
  }

  private void testTLS(boolean clientCert, boolean clientTrust,
                            boolean serverCert, boolean serverTrust,
                            boolean requireClientAuth, boolean clientTrustAll,
                            boolean shouldPass) throws Exception {
    final Buffer receivedBuff = Buffer.createBuffer(0);
    final CountDownLatch latch = new CountDownLatch(1);
    final int numSends = 10;
    final int sendSize = 100;
    NetConnectHandler serverHandler = new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        final ContextChecker checker = new ContextChecker();
        sock.dataHandler(new DataHandler() {
          public void onData(Buffer data) {
            checker.check();
            receivedBuff.append(data);
            if (receivedBuff.length() == numSends * sendSize) {
              sock.close();
              latch.countDown();
            }
          }
        });
      }
    };
    final Buffer sentBuff = Buffer.createBuffer(numSends * sendSize);
    final CountDownLatch exceptionLatch = new CountDownLatch(1);
    NetConnectHandler clientHandler = new NetConnectHandler() {
      public void onConnect(NetSocket sock) {

        sock.exceptionHandler(new ExceptionHandler() {
          public void onException(Exception e) {
            System.err.println("*** Got exceptionHandler in execption handler");
            //TODO need to assert this
            e.printStackTrace();
            exceptionLatch.countDown();
          }
        });
        for (int i = 0; i < numSends; i++) {
          Buffer b = Utils.generateRandomBuffer(sendSize);
          sentBuff.append(b);
          sock.write(b);
        }
      }
    };

    NetServer server = NetServer.createServer(serverHandler).setSSL(true);

    if (serverTrust) {
      server.setTrustStorePath("./src/tests/resources/keystores/server-truststore.jks").setTrustStorePassword
          ("wibble");
    }
    if (serverCert) {
      server.setKeyStorePath("./src/tests/resources/keystores/server-keystore.jks").setKeyStorePassword("wibble");
    }
    if (requireClientAuth) {
      server.setClientAuthRequired(true);
    }

    server.listen(4043);

    NetClient client = NetClient.createClient().setSSL(true);

    if (clientTrustAll) {
      client.setTrustAll(true);
    }

    if (clientTrust) {
      client.setTrustStorePath("./src/tests/resources/keystores/client-truststore.jks")
        .setTrustStorePassword("wibble");
    }
    if (clientCert) {
      client.setKeyStorePath("./src/tests/resources/keystores/client-keystore.jks")
        .setKeyStorePassword("wibble");
    }

    client.connect(4043, clientHandler);

    if (shouldPass) {
      azzert(latch.await(5, TimeUnit.SECONDS));
      azzert(Utils.buffersEqual(sentBuff, receivedBuff));
    } else {
      azzert(exceptionLatch.await(5, TimeUnit.SECONDS));
    }

    awaitClose(server);
    throwAssertions();
  }
}
