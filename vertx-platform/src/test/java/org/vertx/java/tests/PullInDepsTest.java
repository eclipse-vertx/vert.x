package org.vertx.java.tests;/*
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

import java.io.File;

public class PullInDepsTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testPullInDeps() throws Exception {
    String deployID = startMod("mod-maven-server", null, 1, false);
    assertTrue(platformManager.pullInDependencies("mod-pullin"));
    stopApp(deployID, false);
    assertFileExists("src/test/mod-test/mod-pullin/mods");
    assertFileExists("src/test/mod-test/mod-pullin/mods/maven:io.vertx:mod-pullin-a:2.0.0");
    assertFileExists("src/test/mod-test/mod-pullin/mods/maven:io.vertx:mod-pullin-b:1.0.1");
    assertFileExists("src/test/mod-test/mod-pullin/mods/maven:io.vertx:mod-pullin-c:0.1");
    assertFileExists("src/test/mod-test/mod-pullin/mods/maven:io.vertx:mod-pullin-d:1.2-beta");
    // Nested
    assertFileExists("src/test/mod-test/mod-pullin/mods/maven:io.vertx:mod-pullin-d:1.2-beta/mods/maven:io.vertx:mod-pullin-e:2.2");
    vertx.fileSystem().deleteSync("src/test/mod-test/mod-pullin/mods", true);
  }

  private void assertFileExists(String fileName) {
    assertTrue(new File(fileName).exists());
  }


}

