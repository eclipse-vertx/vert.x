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

package org.vertx.java.tests.busmods.mailer;

import org.junit.Test;
import org.vertx.java.framework.TestBase;
import vertx.tests.busmods.mailer.TestClient;

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
    startApp(TestClient.class.getName());
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
