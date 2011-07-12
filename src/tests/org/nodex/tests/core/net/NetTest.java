package org.nodex.tests.core.net;

import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;
import org.nodex.tests.core.org.nodex.tests.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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
  public void test1() throws Exception {
    final Buffer receivedBuff = Buffer.newDynamic(0);
    final Utils.AwaitDone await = new Utils.AwaitDone();
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        sock.data(new DataHandler() {
          public void onData(Buffer data) {
            receivedBuff.append(data);
          }
        });
        sock.closed(await);
      }
    }).listen(8181);

    final Buffer sentBuff = Buffer.newDynamic(0);
    NetClient client = NetClient.createClient().connect(8181, new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        for (int i = 0; i < 10; i++) {
          Buffer b = Utils.generateRandomBuffer(100);
          sentBuff.append(b);
          sock.write(b);
        }
        sock.close();
      }
    });

    assert await.awaitDone(2);

    assert Utils.buffersEqual(sentBuff, receivedBuff);

    server.close();

  }

  /*
  1) Connect test
  Test that connect is called for each connection made
   */

}
