package org.vertx.java.tests.core.eventbus;

import vertx.tests.core.eventbus.ClusteredClient;
import vertx.tests.core.eventbus.ClusteredPeer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaClusteredEventBusTest extends JavaEventBusTest {

  // We use this to get a unique port id
  public static AtomicInteger portCounter = new AtomicInteger(25500);

  protected String getLocalPeerClassName() {
    return ClusteredPeer.class.getName();
  }

  protected String getLocalClientClassName() {
    return ClusteredClient.class.getName();
  }

//  public void testFoo() throws Exception {
//    super.runTestInLoop("testPointToPoint", 10000);
//  }

}
