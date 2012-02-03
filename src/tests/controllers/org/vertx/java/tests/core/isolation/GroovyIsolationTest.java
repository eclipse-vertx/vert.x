package org.vertx.java.tests.core.isolation;

import org.junit.Test;
import org.vertx.java.core.app.VerticleType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class GroovyIsolationTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testIsolation() throws Exception {
    int numInstances = 10;
    for (int i = 0; i < numInstances; i++) {
      startApp(VerticleType.GROOVY, "core/isolation/testclient.groovy");
    }
    startApp(VerticleType.GROOVY, "core/isolation/testclient.groovy", numInstances);
    startTest(getMethodName(), false);
    for (int i = 0; i < numInstances * 2; i++) {
      waitTestComplete();
    }
  }


}
