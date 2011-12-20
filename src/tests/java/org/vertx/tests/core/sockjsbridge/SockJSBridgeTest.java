package org.vertx.tests.core.sockjsbridge;

import org.testng.annotations.Test;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.SockJSBridge;
import org.vertx.java.core.eventbus.spi.ClusterManager;
import org.vertx.java.core.eventbus.spi.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.net.ServerID;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.tests.core.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSBridgeTest extends TestBase {

  @Test
  public void testBridge() throws Exception {


//    VertxInternal.instance.go(new Runnable() {
//      public void run() {
//
//        ClusterManager mgr = new HazelcastClusterManager();
//        ServerID serverID = new ServerID(2345, "localhost");
//        EventBus bus = new EventBus(serverID, mgr) {
//        };
//        EventBus.initialize(bus);
//
//        HttpServer server = new HttpServer();
//        SockJSServer sjsServer = new SockJSServer(server);
//        SockJSBridge bridge = new SockJSBridge(sjsServer, new AppConfig());
//        server.listen(8080);
//      }
//    });
//
//    Thread.sleep(10000000);
  }
}
