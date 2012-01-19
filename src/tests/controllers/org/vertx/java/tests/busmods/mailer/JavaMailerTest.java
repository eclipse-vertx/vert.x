package org.vertx.java.tests.busmods.mailer;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;
import vertx.tests.busmods.mailer.TestMailer;
import vertx.tests.busmods.mailer.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaMailerTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSimple() throws Exception {
    startApp(true, AppType.JAVA, TestMailer.class.getName());
    startApp(AppType.JAVA, TestClient.class.getName());
    startTest(getMethodName());
  }


}
