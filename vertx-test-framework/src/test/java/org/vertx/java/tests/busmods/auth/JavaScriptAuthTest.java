/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.tests.busmods.auth;

import java.net.URL;
import java.util.Enumeration;

import org.junit.Test;
import org.vertx.java.framework.TestBase;

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
    startApp("busmods/auth/test_client.js");
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
    startApp("busmods/auth/test_client_timeout.js");
    startTest(getMethodName());
  }
}
