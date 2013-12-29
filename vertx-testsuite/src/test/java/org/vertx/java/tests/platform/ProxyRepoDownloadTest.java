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

package org.vertx.java.tests.platform;

import org.junit.Test;
import org.vertx.java.testframework.TestBase;
import vertx.tests.RepoDownloadTestClient;

public class ProxyRepoDownloadTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    System.setProperty("http.proxyHost", "127.0.0.1");
    System.setProperty("http.proxyPort", "9194");
    super.setUp();

    startApp(RepoDownloadTestClient.class.getName());

    // TODO: Remove this once we have a way to wait for listen to complete
    Thread.sleep(1000);
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
  }

  @Test
  public void testMavenDownloadWithProxy() throws Exception {
    startTest(getMethodName());
  }
}
