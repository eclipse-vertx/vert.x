/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RequestOptionsTest {

  @Test
  public void testDefaults() {
    RequestOptions options = new RequestOptions();
    assertEquals(RequestOptions.DEFAULT_HOST, options.getHost());
    assertEquals(RequestOptions.DEFAULT_PORT, options.getPort());
    assertEquals(RequestOptions.DEFAULT_SSL, options.isSsl());
    assertEquals(RequestOptions.DEFAULT_URI, options.getURI());
  }

  @Test
  public void testCopy() {
    RequestOptions options = new RequestOptions()
      .setPort(8443)
      .setSsl(true);
    RequestOptions copy = new RequestOptions(options);
    assertEquals(options.getPort(), copy.getPort());
    assertEquals(options.isSsl(), copy.isSsl());
  }

  @Test
  public void testToJson() {
    RequestOptions options = new RequestOptions()
      .setPort(8443)
      .setSsl(true)
      .addHeader("key", "value")
      .addHeader("foo", "bar")
      .addHeader("foo", "baz");
    JsonObject expected = new JsonObject()
      .put("host", RequestOptions.DEFAULT_HOST)
      .put("uri", RequestOptions.DEFAULT_URI)
      .put("port", 8443)
      .put("ssl", true)
      .put("headers", new JsonObject()
        .put("key", "value")
        .put("foo", new JsonArray().add("bar").add("baz"))
      );
    assertEquals(expected, options.toJson());
  }

  @Test
  public void testFromJson() {
    JsonObject json = new JsonObject()
      .put("method", "PUT")
      .put("port", 8443)
      .put("ssl", true)
      .put("headers", new JsonObject()
        .put("key", "value")
        .put("foo", new JsonArray().add("bar").add("baz"))
      );
    RequestOptions options = new RequestOptions(json);
    assertEquals(8443, options.getPort());
    assertTrue(options.isSsl());
    MultiMap headers = options.getHeaders();
    assertEquals(headers.toString(), 2, headers.size());
    assertEquals(Collections.singletonList("value"), headers.getAll("key"));
    assertEquals(Arrays.asList("bar", "baz"), headers.getAll("foo"));
  }
}
