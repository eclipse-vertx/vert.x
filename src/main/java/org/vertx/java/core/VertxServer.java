package org.vertx.java.core;

import org.vertx.java.core.app.AppManager;
import org.vertx.java.core.app.Args;
import org.vertx.java.core.cluster.EventBus;
import org.vertx.java.core.cluster.spi.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.net.ServerID;

import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxServer {
  public static void main(String[] sargs) {
    Args args = new Args(sargs);

    if (args.map.get("-cluster") != null) {
      int clusterPort = args.getInt("-cluster-port");
      if (clusterPort == -1) {
        clusterPort = 25500;
      }
      String clusterHost = args.map.get("-cluster-host");
      if (clusterHost == null) {
        clusterHost = "0.0.0.0";
      }
      final ServerID clusterServerID = new ServerID(clusterPort, clusterHost);
      final CountDownLatch latch = new CountDownLatch(1);
      VertxInternal.instance.go(new Runnable() {
        public void run() {
          EventBus.initialize(clusterServerID, new HazelcastClusterManager());
          latch.countDown();
        }
      });
      try {
        latch.await();
      } catch (InterruptedException ignore) {
      }
    }
    AppManager mgr = new AppManager(args.getInt("-deploy-port"));
    mgr.start();
  }

}
