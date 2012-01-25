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
  }

  private void startApps() throws Exception {
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
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testLoginDeniedNonMatchingOthers() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testLoginDeniedWrongPassword() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testLoginDeniedOtherUserWithSamePassword() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testLoginOKOneEntryInDB() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testLoginOKMultipleEntryInDB() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testValidateDeniedNotLoggedIn() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testValidateDeniedInvalidSessionID() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testValidateDeniedLoggedInWrongSessionID() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testValidateDeniedLoggedOut() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testValidateOK() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testLoginMoreThanOnce() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testLoginMoreThanOnceThenLogout() throws Exception {
    startApps();
    startTest(getMethodName());
  }

  @Test
  public void testSessionTimeout() throws Exception {
    startApp(true, AppType.JS, "busmods/auth/test_persistor.js");
    startApp(AppType.JS, "busmods/auth/test_authmgr_timeout.js");
    startApp(AppType.JS, "busmods/auth/test_client.js");
    startTest(getMethodName());
  }
}
