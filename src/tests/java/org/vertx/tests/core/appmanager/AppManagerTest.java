package org.vertx.tests.core.appmanager;

import org.vertx.java.core.logging.Logger;
import org.vertx.tests.core.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AppManagerTest extends TestBase {

  private static final Logger log = Logger.getLogger(AppManagerTest.class);

//  @Test
//  public void test1() throws Exception {
//    AppManager am = new AppManager();
//
//    String error = am.deploy("some-app", AppType.JAVA, null, "com.acme.someapp.TestApp1", 4);
//
//    if (error != null) {
//      log.error(error);
//    }
//
//    Thread.sleep(1000000);
//  }

//  @Test
//  public void testRequestsDistributed() throws Exception {
//
//    int instances = 4;
//    List<String> results = doTest("com.acme.someapp.TestApp1", instances, 10);
//    Set<String> set = new HashSet<>();
//    for (String res: results) {
//      set.add(res);
//    }
//    azzert(set.size() == instances);
//  }
//
//  @Test
//  public void testIsolation() throws Exception {
//
//    int instances = 4;
//    List<String> results = doTest("com.acme.someapp.TestApp2", instances, 10);
//    Set<String> set = new HashSet<>();
//    //Each instance should have its own static counter
//    for (String res: results) {
//      azzert(Integer.parseInt(res) == 1);
//    }
//  }
//
//  private List<String> doTest(final String mainClass, final int instances, final int requests) throws Exception {
//    AppManager mgr = new AppManager();
//    mgr.startNoBlock();
//
//    Thread.sleep(100);
//
//    File f = new File(".");
//    System.out.println("current dir is " + f.getCanonicalPath());
//
//    final String testClassesRoot = f.getCanonicalPath() + "/target/test-apps/classes/";
//
//    final List<String> ret = new ArrayList<>();
//
//    final CountDownLatch latch = new CountDownLatch(requests);
//
//    sendCommand("deploy java myapp file://" + testClassesRoot + " " + mainClass + " " + instances + "\n");
//
//    VertxInternal.instance.go(new Runnable() {
//      public void run() {
//        for (int i = 0; i < requests; i++) {
//          final HttpClient client = new HttpClient();
//          client.setPort(8080).setHost("localhost").getNow("/", new Handler<HttpClientResponse>() {
//            public void handle(HttpClientResponse response) {
//              final Buffer buff = Buffer.create(0);
//              response.dataHandler(new Handler<Buffer>() {
//                public void handle(Buffer data) {
//                  buff.appendBuffer(data);
//                }
//              });
//              response.endHandler(new SimpleHandler() {
//                public void handle() {
//                  String result = buff.toString();
//                  //System.out.println("Got result: " + result);
//                  synchronized (ret) {
//                    ret.add(result);
//                  }
//                  client.close();
//                  latch.countDown();
//                }
//              });
//            }
//          });
//        }
//      }
//    });
//
//    azzert(latch.await(5, TimeUnit.SECONDS));
//
//    sendCommand("undeploy myapp\n");
//
//    //Thread.sleep(500); // This is hacky - but we need to ensure any servers started by the app are shut down
//
//    final CountDownLatch stopLatch = new CountDownLatch(1);
//    mgr.stop(new SimpleHandler() {
//      public void handle() {
//        stopLatch.countDown();
//      }
//    });
//    azzert(stopLatch.await(5, TimeUnit.SECONDS));
//
//    return ret;
//  }
//
//  private void sendCommand(final String command) throws Exception {
//    final CountDownLatch latch = new CountDownLatch(1);
//    VertxInternal.instance.go(new Runnable() {
//      public void run() {
//        final NetClient client = new NetClient();
//        client.connect(25571, "localhost", new Handler<NetSocket>() {
//          public void handle(NetSocket socket) {
//            socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
//              public void handle(Buffer buff) {
//                String line = buff.toString();
//                azzert(line.equals("OK"));
//                client.close();
//                latch.countDown();
//              }
//            }));
//            socket.write(command);
//          }
//        });
//      }
//    });
//    azzert(latch.await(5, TimeUnit.SECONDS));
//  }
}
