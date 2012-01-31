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
    startApp(true, AppType.JAVA, TestMailer.class.getName());
    startApp(AppType.JAVA, TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSendMultiple() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testSendWithSingleRecipient() throws Exception {
    startTest(getMethodName());
  }

  public void testSendWithRecipientList() throws Exception {
    startTest(getMethodName());
  }

  public void testSendWithSingleCC() throws Exception {
    startTest(getMethodName());
  }

  public void testSendWithCCList() throws Exception {
    startTest(getMethodName());
  }

  public void testSendWithSingleBCC() throws Exception {
    startTest(getMethodName());
  }

  public void testSendWithBCCList() throws Exception {
    startTest(getMethodName());
  }

  public void testInvalidSingleFrom() throws Exception {
    startTest(getMethodName());
  }

  public void testInvalidSingleRecipient() throws Exception {
    startTest(getMethodName());
  }

  public void testInvalidRecipientList() throws Exception {
    startTest(getMethodName());
  }

  public void testNoSubject() throws Exception {
    startTest(getMethodName());
  }

  public void testNoBody() throws Exception {
    startTest(getMethodName());
  }

  public void testNoTo() throws Exception {
    startTest(getMethodName());
  }

  public void testNoFrom() throws Exception {
    startTest(getMethodName());
  }

}
