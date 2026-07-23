/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.http.SendFileOptions;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SendFileOptionsTest {

  @Test
  public void testDefaults() {
    SendFileOptions options = new SendFileOptions();
    assertEquals(SendFileOptions.DEFAULT_CHUNK_SIZE, options.getChunkSize());
  }

  @Test
  public void testCopy() {
    SendFileOptions options = new SendFileOptions().setChunkSize(64 * 1024);
    SendFileOptions copy = new SendFileOptions(options);
    assertEquals(options.getChunkSize(), copy.getChunkSize());
  }

  @Test
  public void testJson() {
    JsonObject json = new JsonObject().put("chunkSize", 64 * 1024);
    SendFileOptions options = new SendFileOptions(json);
    assertEquals(64 * 1024, options.getChunkSize());
    assertEquals(json, options.toJson());
  }

  @Test
  public void testChunkSizeValidation() {
    assertThrows(IllegalArgumentException.class, () -> new SendFileOptions().setChunkSize(0));
    assertThrows(IllegalArgumentException.class, () -> new SendFileOptions().setChunkSize(-1));
  }
}
