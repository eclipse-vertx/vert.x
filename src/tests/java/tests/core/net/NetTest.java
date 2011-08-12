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

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;
import org.testng.annotations.Test;
import tests.Utils;
import tests.core.TestBase;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NetTest extends TestBase {

  @Test
  public void testConnect() throws Exception {
    int connectCount = 10;
    final CountDownLatch serverConnectLatch = new CountDownLatch(connectCount);
    NetServer server = new NetServer(new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        serverConnectLatch.countDown();
      }
    }).listen(8181);

    final CountDownLatch clientConnectLatch = new CountDownLatch(connectCount);
    NetClient client = new NetClient();

    for (int i = 0; i < connectCount; i++) {
      client.connect(8181, new NetConnectHandler() {
        public void onConnect(NetSocket sock) {
          clientConnectLatch.countDown();
          sock.close();
        }
      });
    }

    azzert(serverConnectLatch.await(5, TimeUnit.SECONDS));
    azzert(clientConnectLatch.await(5, TimeUnit.SECONDS));

    awaitClose(server);

    throwAssertions();
  }

  @Test
  /*
  Test setting all the server params
  Actually quite hard to test this meaningfully
   */
  public void testServerParams() throws Exception {
    new NetServer(new NetConnectHandler() {
      public void onConnect(NetSocket sock) {

      }
    }).setKeepAlive(true).setReceiveBufferSize(64 * 1024).setSendBufferSize(32 * 1024).setReuseAddress(true)
        .setSoLinger(true).setTcpNoDelay(false).setTrafficClass(123);
  }

  @Test
  /*
  Test setting all the client params
  Actually quite hard to test this meaningfully
   */
  public void testClientParams() throws Exception {
    new NetClient().setKeepAlive(true).setReceiveBufferSize(64 * 1024).setSendBufferSize(32 * 1024).setReuseAddress(true)
        .setSoLinger(true).setTcpNoDelay(false).setTrafficClass(123);
  }

  @Test
  public void testCloseHandlerCloseFromClient() throws Exception {
    testCloseHandler(true);
  }

  @Test
  public void testCloseHandlerCloseFromServer() throws Exception {
    testCloseHandler(false);
  }

  @Test
  /* Start and stop some servers in a loop, awaiting close each time
   */
  public void testStartAndClose() throws Exception {
    int serverCount = 10;
    for (int i = 0; i < 10; i++) {
      NetServer server = new NetServer(new NetConnectHandler() {
        public void onConnect(NetSocket sock) {
        }
      }).listen(8181);
      awaitClose(server); // If we didn't await we would get bind exceptions next time around the loop
    }
  }

  @Test
  public void testSendDataClientToServerString() throws Exception {
    testSendData(true, true);
  }

  @Test
  public void testSendDataServerToClientString() throws Exception {
    testSendData(false, true);
  }

  @Test
  public void testSendDataClientToServerBytes() throws Exception {
    testSendData(true, false);
  }

  @Test
  public void testSendDataServerToClientBytes() throws Exception {
    testSendData(false, false);
  }

  @Test
  /*
  Test writing with a completion
   */
  public void testWriteWithCompletion() throws Exception {
    final Buffer receivedBuff = Buffer.create(0);
    final CountDownLatch latch = new CountDownLatch(1);
    final int numSends = 10;
    final int sendSize = 100;
    NetServer server = new NetServer(new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        sock.dataHandler(new DataHandler() {
          public void onData(Buffer data) {
            receivedBuff.append(data);
            if (receivedBuff.length() == numSends * sendSize) {
              latch.countDown();
            }
          }
        });
      }
    }).listen(8181);

    final Buffer sentBuff = Buffer.create(0);
    NetClient client = new NetClient().connect(8181, new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        final ContextChecker checker = new ContextChecker();
        doWrite(sentBuff, sock, numSends, sendSize, checker);
      }
    });

    azzert(latch.await(2, TimeUnit.SECONDS));
    azzert(Utils.buffersEqual(sentBuff, receivedBuff));

    awaitClose(server);
    throwAssertions();
  }

  @Test
  public void testSendFileClientToServer() throws Exception {
    testSendFile(true);
  }

  @Test
  public void testSendFileServerToClient() throws Exception {
    testSendFile(false);
  }

  private void testSendFile(boolean clientToServer) throws Exception {
    final String host = "localhost";
    final boolean keepAlive = true;
    final String path = "foo.txt";
    final int port = 8181;

    final String content = Utils.randomAlphaString(10000);
    final File file = setupFile(path, content);

    NetConnectHandler sender = new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        String fileName = "./" + path;
        sock.sendFile(fileName);
        System.out.println("Called sendfile on server");
      }
    };
    final CountDownLatch latch = new CountDownLatch(1);

    NetConnectHandler receiver = new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        final Buffer buff = Buffer.create(0);
        sock.dataHandler(new DataHandler() {
          public void onData(final Buffer data) {
            buff.append(data);
            if (buff.length() == file.length()) {
              azzert(content.equals(buff.toString()));
              latch.countDown();
            }
          }
        });
      }
    };

    NetConnectHandler serverHandler = clientToServer ? receiver: sender;
    NetConnectHandler clientHandler = clientToServer ? sender: receiver;

    NetServer server = new NetServer(serverHandler).listen(8181);
    new NetClient().connect(8181, clientHandler);

    assert latch.await(5, TimeUnit.SECONDS);

    throwAssertions();
    file.delete();
    awaitClose(server);
  }

  //Recursive - we don't write the next packet until we get the completion back from the previous write
  private void doWrite(final Buffer sentBuff, final NetSocket sock, int count, final int sendSize,
                       final ContextChecker checker) {
    Buffer b = Utils.generateRandomBuffer(sendSize);
    sentBuff.append(b);
    count--;
    final int c = count;
    if (count == 0) {
      sock.write(b);
    } else {
      sock.write(b, new Runnable() {
        public void run() {
          checker.check();
          doWrite(sentBuff, sock, c, sendSize, checker);
        }
      });
    }
  }

  private void testSendData(boolean clientToServer, final boolean string) throws Exception {
    final Buffer receivedBuff = Buffer.create(0);
    final CountDownLatch latch = new CountDownLatch(1);
    final int numSends = 10;
    final int sendSize = 100;
    NetConnectHandler receiver = new NetConnectHandler() {
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
    final Buffer sentBuff = Buffer.create(0);
    NetConnectHandler sender = new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        for (int i = 0; i < numSends; i++) {
          if (string) {
            byte[] bytes = new byte[sendSize];
            Arrays.fill(bytes, (byte) 'X');
            try {
              String s = new String(bytes, "UTF-8");
              sentBuff.append(bytes);
              sock.write(s);
            } catch (Exception e) {
              e.printStackTrace();
            }
          } else {
            Buffer b = Utils.generateRandomBuffer(sendSize);
            sentBuff.append(b);
            sock.write(b);
          }
        }
      }
    };
    NetConnectHandler serverHandler = clientToServer ? receiver : sender;
    NetConnectHandler clientHandler = clientToServer ? sender : receiver;

    NetServer server = new NetServer(serverHandler).listen(8181);
    new NetClient().connect(8181, clientHandler);

    azzert(latch.await(2, TimeUnit.SECONDS));

    azzert(Utils.buffersEqual(sentBuff, receivedBuff));

    awaitClose(server);
    throwAssertions();
  }


  private void testCloseHandler(final boolean closeClient) throws Exception {
    int connectCount = 10;
    final CountDownLatch serverCloseLatch = new CountDownLatch(connectCount);
    NetServer server = new NetServer(new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        final ContextChecker checker = new ContextChecker();
        sock.closedHandler(new Runnable() {
          public void run() {
            checker.check();
            serverCloseLatch.countDown();
          }
        });
        if (!closeClient) sock.close();
      }
    }).listen(8181);

    final CountDownLatch clientCloseLatch = new CountDownLatch(connectCount);
    NetClient client = new NetClient();

    for (int i = 0; i < connectCount; i++) {
      client.connect(8181, new NetConnectHandler() {
        public void onConnect(NetSocket sock) {
          final ContextChecker checker = new ContextChecker();
          sock.closedHandler(new Runnable() {
            public void run() {
              checker.check();
              clientCloseLatch.countDown();
            }
          });
          if (closeClient) sock.close();
        }
      });
    }

    azzert(serverCloseLatch.await(5, TimeUnit.SECONDS));
    azzert(clientCloseLatch.await(5, TimeUnit.SECONDS));

    awaitClose(server);
  }


}
