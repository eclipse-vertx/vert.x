package tests.core.net;

import org.nodex.core.DoneHandler;
import org.nodex.core.Nodex;
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

/**
 * User: timfox
 * Date: 21/07/2011
 * Time: 13:24
 * <p/>
 * Test that the node.x threading model is obeyed
 * <p/>
 * TODO - maybe we should just do this in NetTest?
 */
public class ThreadingTest extends TestBase {

  @BeforeClass
  public void setUp() {
  }

  @AfterClass
  public void tearDown() {
  }

  @Test
  // Test that all handlers for a connection are executed with same context
  public void testNetHandlers() throws Exception {
    final int dataLength = 10000;
    final CountDownLatch serverClosedLatch = new CountDownLatch(1);
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        final Thread th = Thread.currentThread();
        final String contextID = Nodex.instance.getContextID();
        sock.data(new DataHandler() {
          public void onData(Buffer data) {
            assert th == Thread.currentThread();
            assert contextID == Nodex.instance.getContextID();
            sock.write(data);    // Send it back to client
          }
        });
        sock.closed(new DoneHandler() {
          public void onDone() {
            assert th == Thread.currentThread();
            assert contextID == Nodex.instance.getContextID();
            serverClosedLatch.countDown();
          }
        });
      }
    }).listen(8181);

    NetClient client = NetClient.createClient();

    final CountDownLatch clientClosedLatch = new CountDownLatch(1);
    client.connect(8181, new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        final Thread th = Thread.currentThread();
        final String contextID = Nodex.instance.getContextID();
        final Buffer buff = Buffer.newDynamic(0);
        sock.data(new DataHandler() {
          public void onData(Buffer data) {
            assert th == Thread.currentThread();
            assert contextID == Nodex.instance.getContextID();
            buff.append(data);
            if (buff.length() == dataLength) {
              sock.close();
            }
          }
        });
        sock.closed(new DoneHandler() {
          public void onDone() {
            assert th == Thread.currentThread();
            assert contextID == Nodex.instance.getContextID();
            clientClosedLatch.countDown();
          }
        });
        Buffer sendBuff = Utils.generateRandomBuffer(dataLength);
        sock.write(sendBuff);
        sock.close();
      }
    });

    assert serverClosedLatch.await(5, TimeUnit.SECONDS);
    assert clientClosedLatch.await(5, TimeUnit.SECONDS);

    awaitClose(server);
  }
}
