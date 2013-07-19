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

package org.vertx.java.tests.core.eventbus;

import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.eventbus.LocalEchoClient;
import vertx.tests.core.eventbus.LocalEchoPeer;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaEchoTest extends TestBase {

  private static final Logger log = LoggerFactory.getLogger(JavaEchoTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    startApp(getPeerClassName());
    startApp(getClientClassName());
  }

  protected String getPeerClassName() {
    return LocalEchoPeer.class.getName();
  }

  protected String getClientClassName() {
    return LocalEchoClient.class.getName();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  private void runPeerTest(String testName) {
    startTest(testName + "Initialise");
    startTest(testName);
  }

  @Test
  public void testEchoString() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullString() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoBooleanTrue() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoBooleanFalse() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullBoolean() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoBuffer() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullBuffer() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoByteArray() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullByteArray() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoByte() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullByte() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoCharacter() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullCharacter() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoDouble() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullDouble() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoFloat() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullFloat() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoInt() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullInt() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoJson() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullJson() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoLong() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullLong() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoShort() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullShort() {
    runPeerTest(getMethodName());
  }
}
