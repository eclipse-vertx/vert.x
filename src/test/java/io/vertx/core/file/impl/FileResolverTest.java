/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FileResolverTest {

  @Test
  public void isValidWindowsCachePath() throws Exception {
    assertTrue(FileResolver.isValidWindowsCachePath("a"));
    assertFalse(FileResolver.isValidWindowsCachePath("\u0000"));
    assertFalse(FileResolver.isValidWindowsCachePath("\u001f"));
    assertFalse(FileResolver.isValidWindowsCachePath("<"));
    assertFalse(FileResolver.isValidWindowsCachePath(">"));
    assertFalse(FileResolver.isValidWindowsCachePath(":"));
    assertFalse(FileResolver.isValidWindowsCachePath("\""));
    assertFalse(FileResolver.isValidWindowsCachePath("\\"));
    assertFalse(FileResolver.isValidWindowsCachePath("|"));
    assertFalse(FileResolver.isValidWindowsCachePath("?"));
    assertFalse(FileResolver.isValidWindowsCachePath("*"));
    assertTrue(FileResolver.isValidWindowsCachePath("a.b"));
    assertTrue(FileResolver.isValidWindowsCachePath("a b"));
    assertTrue(FileResolver.isValidWindowsCachePath("/a/b/c"));
    assertTrue(FileResolver.isValidWindowsCachePath("/  a/  b/  c"));
    assertTrue(FileResolver.isValidWindowsCachePath("/..a/..b/..c"));
    assertTrue(FileResolver.isValidWindowsCachePath("/a a/b b/c c"));
    assertTrue(FileResolver.isValidWindowsCachePath("/a.a/b.b/c.c"));
    assertFalse(FileResolver.isValidWindowsCachePath("/a/b /c"));
    assertFalse(FileResolver.isValidWindowsCachePath("/a/b./c"));
    assertFalse(FileResolver.isValidWindowsCachePath("/a/b/c "));
    assertFalse(FileResolver.isValidWindowsCachePath("/a/b/c."));
  }

}
