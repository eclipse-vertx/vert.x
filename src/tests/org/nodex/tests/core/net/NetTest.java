package org.nodex.tests.core.net;

import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;
import org.nodex.tests.core.org.nodex.tests.AwaitDone;
import org.nodex.tests.core.org.nodex.tests.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: tim
 * Date: 12/07/11
 * Time: 10:27
 */
public class NetTest {

  @BeforeClass
  public void setUp() {
  }

  @AfterClass
  public void tearDown() {
  }

  @Test
  public void testConnect() throws Exception {
    int connectCount = 10;
    final CountDownLatch serverConnectLatch = new CountDownLatch(connectCount);
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        serverConnectLatch.countDown();
      }
    }).listen(8181);

    final CountDownLatch clientConnectLatch = new CountDownLatch(connectCount);
    NetClient client = NetClient.createClient();

    for (int i = 0; i < connectCount; i++) {
      client.connect(8181, new NetConnectHandler() {
        public void onConnect(NetSocket sock) {
          clientConnectLatch.countDown();
          sock.close();
        }
      });
    }

    assert serverConnectLatch.await(5, TimeUnit.SECONDS);
    assert clientConnectLatch.await(5, TimeUnit.SECONDS);

    awaitClose(server);
  }

  @Test
  /*
  Test setting all the server params
  Actually quite hard to test this meaningfully
   */
  public void testServerParams() throws Exception {
    NetServer.createServer(new NetConnectHandler() {
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
    NetClient.createClient().setKeepAlive(true).setReceiveBufferSize(64 * 1024).setSendBufferSize(32 * 1024).setReuseAddress(true)
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
      NetServer server = NetServer.createServer(new NetConnectHandler() {
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
    final Buffer receivedBuff = Buffer.newDynamic(0);
    final CountDownLatch latch = new CountDownLatch(1);
    final int numSends = 10;
    final int sendSize = 100;
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        sock.data(new DataHandler() {
          public void onData(Buffer data) {
            receivedBuff.append(data);
            if (receivedBuff.length() == numSends * sendSize) {
              latch.countDown();
            }
          }
        });
      }
    }).listen(8181);

    final Buffer sentBuff = Buffer.newDynamic(0);
    NetClient.createClient().connect(8181, new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        doWrite(sentBuff, sock, numSends, sendSize);
      }
    });

    assert latch.await(2, TimeUnit.SECONDS);
    assert Utils.buffersEqual(sentBuff, receivedBuff);

    awaitClose(server);
  }

  /*

  12) Tetst pause, resume and drain
  13) Test exception handler
   */

  //Recursive - we don't write the next packet until we get the completion back from the previous write
  private void doWrite(final Buffer sentBuff, final NetSocket sock, int count, final int sendSize) {
    Buffer b = Utils.generateRandomBuffer(sendSize);
    sentBuff.append(b);
    count--;
    final int c = count;
    if (count == 0) {
      sock.write(b);
    } else {
      sock.write(b, new DoneHandler() {
        public void onDone() {
          doWrite(sentBuff, sock, c, sendSize);
        }
      });
    }
  }

  private void testSendData(boolean clientToServer, final boolean string) throws Exception {
    final Buffer receivedBuff = Buffer.newDynamic(0);
    final CountDownLatch latch = new CountDownLatch(1);
    final int numSends = 10;
    final int sendSize = 100;
    NetConnectHandler receiver = new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        sock.data(new DataHandler() {
          public void onData(Buffer data) {
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
        for (int i = 0; i < numSends; i++) {
          if (string) {
            byte[] bytes = new byte[sendSize];
            Arrays.fill(bytes, (byte)'X');
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
    NetConnectHandler clientHandler = clientToServer ? sender: receiver;

    NetServer server = NetServer.createServer(serverHandler).listen(8181);
    NetClient.createClient().connect(8181, clientHandler);

    assert latch.await(2, TimeUnit.SECONDS);

    assert Utils.buffersEqual(sentBuff, receivedBuff);

    awaitClose(server);
  }


  private void testCloseHandler(final boolean closeClient) throws Exception {
    int connectCount = 10;
    final CountDownLatch serverCloseLatch = new CountDownLatch(connectCount);
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
       sock.closed(new DoneHandler() {
         public void onDone() {
           serverCloseLatch.countDown();
         }
       });
       if (!closeClient) sock.close();
      }
    }).listen(8181);

    final CountDownLatch clientCloseLatch = new CountDownLatch(connectCount);
    NetClient client = NetClient.createClient();

    for (int i = 0; i < connectCount; i++) {
      client.connect(8181, new NetConnectHandler() {
       public void onConnect(NetSocket sock) {
         sock.closed(new DoneHandler() {
           public void onDone() {
             clientCloseLatch.countDown();
           }
         });
         if (closeClient) sock.close();
       }
      });
    }

    assert serverCloseLatch.await(5, TimeUnit.SECONDS);
    assert clientCloseLatch.await(5, TimeUnit.SECONDS);

    awaitClose(server);
  }

  private void awaitClose(NetServer server) {
    AwaitDone await = new AwaitDone();
    server.close(await);
    assert await.awaitDone(5);
  }


}
