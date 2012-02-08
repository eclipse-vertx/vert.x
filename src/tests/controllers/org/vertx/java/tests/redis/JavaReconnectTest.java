package org.vertx.java.tests.redis;

import org.vertx.java.newtests.TestBase;
import vertx.tests.redis.ReconnectTestClient;

/**
 *
 * This test must be run manually
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class JavaReconnectTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(ReconnectTestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testConnectionFailure() {
    startTest(getMethodName());
  }

  public void testConnectionFailureWhenInPool() {
    startTest(getMethodName());
  }
}
