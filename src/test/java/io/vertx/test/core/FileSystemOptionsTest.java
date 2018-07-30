/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.json.JsonObject;
import org.junit.Test;
import java.util.Random;

public class FileSystemOptionsTest extends VertxTestBase {

  @Test
  public void testDefaults() {
    FileSystemOptions options = new FileSystemOptions();

    assertTrue(options.isFileResolverCachingEnabled());
    assertTrue(options.isClassPathResolvingEnabled());
  }

  @Test
  public void testCopy() {
    FileSystemOptions options = new FileSystemOptions();

    Random rand = new Random();
    boolean enabled = rand.nextBoolean();
    options.setFileResolverCachingEnabled(enabled);
    options.setClassPathResolvingEnabled(enabled);
    options = new FileSystemOptions(options);
    assertEquals(enabled, options.isClassPathResolvingEnabled());
    assertEquals(enabled, options.isFileResolverCachingEnabled());
  }

  @Test
  public void testEmptyJsonOptions() {
    FileSystemOptions options = new FileSystemOptions(new JsonObject());
    assertTrue(options.isFileResolverCachingEnabled());
    assertTrue(options.isClassPathResolvingEnabled());
  }

  @Test
  public void testJsonOptions() {
    Random rand = new Random();
    boolean enabled = rand.nextBoolean();
    FileSystemOptions options = new FileSystemOptions(new JsonObject().
      put("fileResolverCachingEnabled", enabled).
      put("classPathResolvingEnabled", enabled)
    );
    assertEquals(enabled, options.isFileResolverCachingEnabled());
    assertEquals(enabled, options.toJson().getBoolean("fileResolverCachingEnabled"));
    assertEquals(enabled, options.isClassPathResolvingEnabled());
    assertEquals(enabled, options.toJson().getBoolean("classPathResolvingEnabled"));
  }
}
