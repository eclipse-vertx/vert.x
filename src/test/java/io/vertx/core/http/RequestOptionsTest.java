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
    assertEquals(RequestOptions.DEFAULT_SERVER, options.getServer());
    assertEquals(RequestOptions.DEFAULT_HTTP_METHOD, options.getMethod());
    assertEquals(RequestOptions.DEFAULT_HOST, options.getHost());
    assertEquals(RequestOptions.DEFAULT_PORT, options.getPort());
    assertEquals(RequestOptions.DEFAULT_SSL, options.isSsl());
    assertEquals(RequestOptions.DEFAULT_URI, options.getURI());
    assertEquals(RequestOptions.DEFAULT_FOLLOW_REDIRECTS, options.getFollowRedirects());
    assertEquals(RequestOptions.DEFAULT_TIMEOUT, options.getTimeout());
  }

  @Test
  public void testCopy() {
    RequestOptions options = new RequestOptions()
      .setMethod(HttpMethod.PUT)
      .setPort(8443)
      .setSsl(true)
      .setFollowRedirects(true);
    RequestOptions copy = new RequestOptions(options);
    assertEquals(options.getMethod(), copy.getMethod());
    assertEquals(options.getPort(), copy.getPort());
    assertEquals(options.isSsl(), copy.isSsl());
    assertEquals(options.getFollowRedirects(), copy.getFollowRedirects());
  }

  @Test
  public void testToJson() {
    RequestOptions options = new RequestOptions()
      .setMethod(HttpMethod.PUT)
      .setPort(8443)
      .setSsl(true)
      .setFollowRedirects(true)
      .addHeader("key", "value")
      .addHeader("foo", Arrays.asList("bar", "baz"));
    JsonObject expected = new JsonObject()
      .put("timeout", RequestOptions.DEFAULT_TIMEOUT)
      .put("uri", RequestOptions.DEFAULT_URI)
      .put("method", "PUT")
      .put("port", 8443)
      .put("ssl", true)
      .put("followRedirects", true)
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
      .put("followRedirects", true)
      .put("headers", new JsonObject()
        .put("key", "value")
        .put("foo", new JsonArray().add("bar").add("baz"))
      );
    RequestOptions options = new RequestOptions(json);
    assertEquals(HttpMethod.PUT, options.getMethod());
    assertEquals(Integer.valueOf(8443), options.getPort());
    assertTrue(options.isSsl());
    assertTrue(options.getFollowRedirects());
    MultiMap headers = options.getHeaders();
    assertEquals(headers.toString(), 2, headers.size());
    assertEquals(Collections.singletonList("value"), headers.getAll("key"));
    assertEquals(Arrays.asList("bar", "baz"), headers.getAll("foo"));
  }
}
