package tests.core;

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: timfox
 * Date: 21/07/2011
 * Time: 13:58
 */
public class TimerTest extends TestBase {
  @BeforeClass
  public void setUp() {
  }

  @AfterClass
  public void tearDown() {
  }

  @Test
  public void testOneOff() throws Exception {
    final CountDownLatch setDataHandlerLatch = new CountDownLatch(1);
    final CountDownLatch endLatch = new CountDownLatch(1);
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        final Thread th = Thread.currentThread();
        final String contextID = Nodex.instance.getContextID();
        Nodex.instance.setTimeout(100, new DoneHandler() {
          public void onDone() {
            assert th == Thread.currentThread();
            assert contextID == Nodex.instance.getContextID();
            sock.data(new DataHandler() {
              public void onData(final Buffer data) {
                Nodex.instance.setTimeout(100, new DoneHandler() {
                  public void onDone() {
                    assert th == Thread.currentThread();
                    assert contextID == Nodex.instance.getContextID();
                    endLatch.countDown();
                  }
                });
              }
            });
            setDataHandlerLatch.countDown();
          }
        });
      }
    }).listen(8181);

    NetClient client = NetClient.createClient();

    client.connect(8181, new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        try {
          assert setDataHandlerLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          assert false;
        }
        Buffer sendBuff = Utils.generateRandomBuffer(1000);
        sock.write(sendBuff);
      }
    });

    assert endLatch.await(5, TimeUnit.SECONDS);

    awaitClose(server);
  }
}
