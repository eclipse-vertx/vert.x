package org.vertx.java.tests.busmods.mailer;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;
import vertx.tests.busmods.mailer.TestClient;
import vertx.tests.busmods.mailer.TestMailer;

/**
 *
 * The tests need a mail server running on localhost, port 25
 * If you install sendmail, then the mail should end up in /var/mail/<username>
 *
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

  @Test
  public void testInvalidFrom() throws Exception {
    startApp(true, AppType.JAVA, TestMailer.class.getName());
    startApp(AppType.JAVA, TestClient.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testInvalidTo() throws Exception {
    startApp(true, AppType.JAVA, TestMailer.class.getName());
    startApp(AppType.JAVA, TestClient.class.getName());
    startTest(getMethodName());
  }


}
