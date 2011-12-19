/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.tests.core.net;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TLSTest extends TestBase {

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

  private void testTLS(final boolean clientCert, final boolean clientTrust,
                       final boolean serverCert, final boolean serverTrust,
                       final boolean requireClientAuth, final boolean clientTrustAll,
                       final boolean shouldPass) throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);
    final CountDownLatch exceptionLatch = new CountDownLatch(1);
    final int numSends = 10;
    final int sendSize = 100;
    final Buffer sentBuff = Buffer.create(numSends * sendSize);
    final Buffer receivedBuff = Buffer.create(0);
    final AtomicReference<Exception> excRef = new AtomicReference<Exception>();

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final NetServer server = new NetServer();

        final long actorId = Vertx.instance.registerHandler(new Handler<String>() {
          public void handle(String msg) {
            server.close(new SimpleHandler() {
              public void handle() {
                latch.countDown();
              }
            });
          }
        });

        Handler<NetSocket> serverHandler = new Handler<NetSocket>() {
          public void handle(final NetSocket sock) {
            final ContextChecker checker = new ContextChecker();
            sock.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                checker.check();
                receivedBuff.appendBuffer(data);
                if (receivedBuff.length() == numSends * sendSize) {
                  sock.close();
                  Vertx.instance.sendToHandler(actorId, "foo");
                }
              }
            });
          }
        };

        Handler<NetSocket> clientHandler = new Handler<NetSocket>() {
          public void handle(NetSocket sock) {

            sock.exceptionHandler(new Handler<Exception>() {
              public void handle(Exception e) {
                e.printStackTrace();
                excRef.set(e);
                Vertx.instance.sendToHandler(actorId, "foo");
              }
            });
            for (int i = 0; i < numSends; i++) {
              Buffer b = Utils.generateRandomBuffer(sendSize);
              sentBuff.appendBuffer(b);
              sock.write(b);
            }
          }
        };

        server.connectHandler(serverHandler).setSSL(true);

        if (serverTrust) {
          server.setTrustStorePath("./src/tests/keystores/server-truststore.jks").setTrustStorePassword
              ("wibble");
        }
        if (serverCert) {
          server.setKeyStorePath("./src/tests/keystores/server-keystore.jks").setKeyStorePassword("wibble");
        }
        if (requireClientAuth) {
          server.setClientAuthRequired(true);
        }

        server.listen(4043);

        NetClient client = new NetClient().setSSL(true);

        if (clientTrustAll) {
          client.setTrustAll(true);
        }

        if (clientTrust) {
          client.setTrustStorePath("./src/tests/keystores/client-truststore.jks")
              .setTrustStorePassword("wibble");
        }
        if (clientCert) {
          client.setKeyStorePath("./src/tests/keystores/client-keystore.jks")
              .setKeyStorePassword("wibble");
        }

        client.connect(4043, clientHandler);
      }
    });

    if (shouldPass) {
      azzert(latch.await(5, TimeUnit.SECONDS));
      azzert(Utils.buffersEqual(sentBuff, receivedBuff));
    } else {
      azzert(latch.await(5, TimeUnit.SECONDS));
      azzert(excRef.get() != null);
    }

    throwAssertions();
  }
}
