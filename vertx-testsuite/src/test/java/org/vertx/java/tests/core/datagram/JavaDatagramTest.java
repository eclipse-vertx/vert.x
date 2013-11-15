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

package org.vertx.java.tests.core.datagram;

import org.junit.Test;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.datagram.TestClient;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class JavaDatagramTest extends TestBase {

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
  public void testListenHostPort() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testListenPort() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testListenInetSocketAddress() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testListenSamePortMultipleTimes() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testSendReceive() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testEcho() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testSendAfterCloseFails() {
    startTest(getMethodName());
  }

  @Test
  public void testBroadcast() {
    startTest(getMethodName());
  }

  @Test
  public void testBroadcastFailsIfNotConfigured() {
    startTest(getMethodName());
  }

  @Test
  public void testConfigureAfterSendString() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testConfigureAfterSendStringWithEnc() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testConfigureAfterSendBuffer() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testConfigureAfterListen() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testConfigureAfterListenWithInetSocketAddress() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testConfigure() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testMulticastJoinLeave() throws Exception {
    startTest(getMethodName());
  }
}
