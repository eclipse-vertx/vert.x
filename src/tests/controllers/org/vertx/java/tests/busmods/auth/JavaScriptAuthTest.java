package org.vertx.java.tests.busmods.auth;

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
public class JavaScriptAuthTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(true, AppType.JS, "busmods/auth/test_persistor.js");
    startApp(AppType.JS, "busmods/auth/test_authmgr.js");
    startApp(AppType.JS, "busmods/auth/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testLoginDeniedEmptyDB() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testLoginDeniedNonMatchingOthers() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testLoginDeniedWrongPassword() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testLoginDeniedOtherUserWithSamePassword() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testLoginOKOneEntryInDB() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testLoginOKMultipleEntryInDB() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testValidateDeniedNotLoggedIn() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testValidateDeniedInvalidSessionID() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testValidateDeniedLoggedInWrongSessionID() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testValidateDeniedLoggedOut() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testValidateOK() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testLoginMoreThanOnce() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testLoginMoreThanOnceThenLogout() throws Exception {
    startTest(getMethodName());
  }
}
