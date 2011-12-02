package org.vertx.tests.core.appmanager;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.AppManager;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.app.cli.DeployCommand;
import org.vertx.java.core.app.cli.SocketDeployer;
import org.vertx.java.core.app.cli.UndeployCommand;
import org.vertx.java.core.app.cli.VertxCommand;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.tests.core.TestBase;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AppManagerTest extends TestBase {

  private static final Logger log = Logger.getLogger(AppManagerTest.class);

  @Test
  public void testRequestsDistributedJava() throws Exception {

    int instances = 4;
    List<String> results = doTest(AppType.JAVA, "com.acme.someapp.TestApp1", instances, 10);
    Set<String> set = new HashSet<>();
    for (String res: results) {
      set.add(res);
    }
    azzert(set.size() == instances);
  }

  @Test
  public void testIsolationJava() throws Exception {

    int instances = 4;
    List<String> results = doTest(AppType.JAVA, "com.acme.someapp.TestApp2", instances, 10);
    Set<String> set = new HashSet<>();
    //Each instance should have its own static counter
    for (String res: results) {
      azzert(Integer.parseInt(res) == 1);
    }
  }


  private List<String> doTest(AppType appType, final String main, final int instances, final int requests) throws Exception {
    AppManager mgr = new AppManager(SocketDeployer.DEFAULT_PORT);
    mgr.startNoBlock();

    Thread.sleep(100);

    URL url = null;
    if (appType == AppType.JAVA) {
      //We need to get the URL to the root directory of where the classes are so we can use that URL
      //in another classloader to load the classes
      String classFile = main.replace('.', '/') + ".class";
      url = getClass().getClassLoader().getResource(classFile);
      String surl = url.toString();
      String surlroot = surl.substring(0, surl.length() - classFile.length());
      url = new URL(surlroot);
    }
//    else if (appType == AppType.RUBY) {
//      url = getClass().getClassLoader().getResource(main);
//      String surl = url.toString();
//      String surlroot = surl.substring(0, surl.length() - main.length());
//      url = new URL(surlroot);
//      log.info("url is " + url);
//    }

    final List<String> ret = new ArrayList<>();

    final CountDownLatch latch = new CountDownLatch(requests);

    DeployCommand cmd = new DeployCommand(appType, "myapp", main, new URL[] {url}, instances);

    sendCommand(cmd);

    Thread.sleep(200);

    VertxInternal.instance.go(new Runnable() {
      public void run() {
        for (int i = 0; i < requests; i++) {
          final HttpClient client = new HttpClient();
          client.setPort(8080).setHost("localhost").getNow("/", new Handler<HttpClientResponse>() {
            public void handle(HttpClientResponse response) {
              final Buffer buff = Buffer.create(0);
              response.dataHandler(new Handler<Buffer>() {
                public void handle(Buffer data) {
                  buff.appendBuffer(data);
                }
              });
              response.endHandler(new SimpleHandler() {
                public void handle() {
                  String result = buff.toString();
                  synchronized (ret) {
                    ret.add(result);
                  }
                  client.close();
                  latch.countDown();
                }
              });
            }
          });
        }
      }
    });

    azzert(latch.await(5, TimeUnit.SECONDS));

    sendCommand(new UndeployCommand("myapp"));

    final CountDownLatch stopLatch = new CountDownLatch(1);
    mgr.stop(new SimpleHandler() {
      public void handle() {
        stopLatch.countDown();
      }
    });
    azzert(stopLatch.await(5, TimeUnit.SECONDS));

    return ret;
  }

  private void sendCommand(final VertxCommand command) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    VertxInternal.instance.go(new Runnable() {
      public void run() {
        final NetClient client = new NetClient();
        client.connect(SocketDeployer.DEFAULT_PORT, "localhost", new Handler<NetSocket>() {
          public void handle(NetSocket socket) {
            socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
              public void handle(Buffer buff) {
                String line = buff.toString();
                azzert(line.equals("OK"));
                client.close();
                latch.countDown();
              }
            }));
            command.write(socket, null);
          }
        });
      }
    });
    azzert(latch.await(5, TimeUnit.SECONDS));
  }
}
