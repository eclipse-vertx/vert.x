package org.vertx.java.tests.container;
/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import org.junit.Test;
import org.vertx.java.testframework.TestBase;
import vertx.tests.RepoDownloadTestClient;

public class RepoDownloadTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    startApp(RepoDownloadTestClient.class.getName());

    // TODO: Remove this once we have a way to wait for listen to complete
    Thread.sleep(1000);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testMavenDownload() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testBintrayDownload() throws Exception {
    startTest(getMethodName());
  }
}
