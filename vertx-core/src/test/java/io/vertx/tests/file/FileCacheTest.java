/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.file;

import io.vertx.core.VertxException;
import io.vertx.core.file.impl.FileCache;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class FileCacheTest extends VertxTestBase {

  @Test
  public void testMutateCacheContentOnly() throws IOException {
    File testRoot = File.createTempFile("vertx-", "-cache");
    assertTrue(testRoot.delete());
    assertTrue(testRoot.mkdirs());
    testRoot.deleteOnExit();
    File cacheRoot = new File(testRoot, "content");
    FileCache cache = new FileCache(cacheRoot);
    File other = new File(testRoot, "content-other");
    Files.write(other.toPath(), "protected".getBytes(), StandardOpenOption.CREATE);
    try {
      cache.cacheFile("../content-other", new ByteArrayInputStream("hello".getBytes()), true);
      fail();
    } catch (VertxException ignore) {
      assertEquals("protected", new String(Files.readAllBytes(other.toPath())));
    }
  }
}
