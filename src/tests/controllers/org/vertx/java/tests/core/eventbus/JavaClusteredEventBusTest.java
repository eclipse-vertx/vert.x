package org.vertx.java.tests.core.eventbus;

import vertx.tests.core.eventbus.ClusteredClient;
import vertx.tests.core.eventbus.ClusteredPeer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaClusteredEventBusTest extends JavaEventBusTest {

  protected String getPeerClassName() {
    return ClusteredPeer.class.getName();
  }

  protected String getClientClassName() {
    return ClusteredClient.class.getName();
  }
}
