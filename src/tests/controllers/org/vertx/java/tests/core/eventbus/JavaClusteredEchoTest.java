package org.vertx.java.tests.core.eventbus;

import vertx.tests.core.eventbus.ClusteredEchoClient;
import vertx.tests.core.eventbus.ClusteredEchoPeer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaClusteredEchoTest extends JavaEchoTest {

  protected String getPeerClassName() {
    return ClusteredEchoPeer.class.getName();
  }

  protected String getClientClassName() {
    return ClusteredEchoClient.class.getName();
  }
}