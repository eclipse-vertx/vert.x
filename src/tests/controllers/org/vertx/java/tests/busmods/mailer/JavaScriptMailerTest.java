package org.vertx.java.tests.busmods.mailer;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 *
 * The tests need a mail server running on localhost, port 25
 * If you install sendmail, then the mail should end up in /var/mail/<username>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptMailerTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(true, AppType.JS, "busmods/mailer/test_mailer.js");
    startApp(AppType.JS, "busmods/mailer/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testMailer() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testMailerError() throws Exception {
    startTest(getMethodName());
  }

}
