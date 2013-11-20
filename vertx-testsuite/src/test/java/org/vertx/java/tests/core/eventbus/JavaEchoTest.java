/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
  public void testEchoJsonArray() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullJson() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullJsonArray() {
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

  @Test
  public void testSendWithTimeoutReply() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testSendWithTimeoutNoReply() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testSendReplyWithTimeoutNoReplyHandler() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testSendWithDefaultTimeoutNoReply() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testReplyWithTimeoutReply() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testReplyWithTimeoutNoReply() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testReplyNoHandlers() {
    startTest(getMethodName());
  }

  @Test
  public void testReplyRecipientFailure() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testReplyRecipientFailureStandardHandler() {
    runPeerTest(getMethodName());
  }


}
