package org.vertx.java.tests.core.deploy;

import org.junit.Test;
import org.vertx.java.newtests.TestBase;
import vertx.tests.core.deploy.TestClient;
import vertx.tests.core.http.HttpTestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyDeployTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/deploy/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void test_deploy() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void test_undeploy() throws Exception {
    startTest(getMethodName());
  }

}
