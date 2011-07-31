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
import org.nodex.core.http.HttpClient;
import org.nodex.core.http.HttpClientConnectHandler;
import org.nodex.core.http.HttpClientConnection;
import org.nodex.core.http.HttpClientRequest;
import org.nodex.core.http.HttpClientResponse;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpResponseHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerConnectHandler;
import org.nodex.core.http.HttpServerConnection;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.http.HttpServerResponse;
import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tests.Utils;
import tests.core.TestBase;

import java.util.Arrays;
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
  public void test1() throws Exception {
    testSendData(true);
  }

  private void testSendData(boolean clientToServer) throws Exception {
    final Buffer receivedBuff = Buffer.newDynamic(0);
    final CountDownLatch latch = new CountDownLatch(1);
    final int numSends = 10;
    final int sendSize = 100;
    NetConnectHandler receiver = new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        final ContextChecker checker = new ContextChecker();
        sock.data(new DataHandler() {
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
    final Buffer sentBuff = Buffer.newDynamic(0);
    NetConnectHandler sender = new NetConnectHandler() {
      public void onConnect(NetSocket sock) {

        sock.exception(new ExceptionHandler() {
          public void onException(Exception e) {
            System.err.println("*** Got exception in execption handler");
            //TODO need to assert this
            e.printStackTrace();
          }
        });
        for (int i = 0; i < numSends; i++) {
          Buffer b = Utils.generateRandomBuffer(sendSize);
          sentBuff.append(b);
          sock.write(b);
        }
      }
    };
    NetConnectHandler serverHandler = clientToServer ? receiver : sender;
    NetConnectHandler clientHandler = clientToServer ? sender : receiver;

    NetServer server = NetServer.createServer(serverHandler).setSSL(true).setTrustStorePath
        ("/Users/timfox/keystore/server-truststore.jks")
        .setTrustStorePassword("wibble").setKeyStorePath
        ("/Users/timfox/keystore/server-keystore.jks")
    .setKeyStorePassword("wibble").setClientAuthRequired(true).listen(4043);

    NetClient.createClient().setSSL(true).setTrustStorePath("/Users/timfox/keystore/client-truststore.jks")
        .setTrustStorePassword("wibble").setKeyStorePath("/Users/timfox/keystore/client-keystore.jks")
        .setKeyStorePassword("wibble").connect
        (4043, clientHandler);

    azzert(latch.await(2, TimeUnit.SECONDS));

    azzert(Utils.buffersEqual(sentBuff, receivedBuff));

    awaitClose(server);
    throwAssertions();
  }
}
