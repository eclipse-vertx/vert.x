/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.impl;


import org.junit.Test;

import io.vertx.test.http.HttpTestBase;
import io.vertx.core.http.RequestOptions;

public class HttpClientRequestBaseTest extends HttpTestBase {

  @Test
  public void testPathCacheAndQueryCache() {
    server.requestHandler(req -> {});
    server.listen(testAddress).onComplete(onSuccess(server -> {
      client.request(new RequestOptions(requestOptions).setURI("/?")).onComplete(onSuccess(req -> {
        assertThat(req.getURI(), a -> a.isEqualTo("/?"));
        assertThat(req.path(), a -> a.isEqualTo("/"));
        assertThat(req.query(), a -> a.isEqualTo(""));
        req.setURI("/index.html");
        assertThat(req.getURI(), a -> a.isEqualTo("/index.html"));
        assertThat(req.path(), a -> a.isEqualTo("/index.html"));
        assertThat(req.query(), a -> a.isNull());
        req.setURI("/foo?bar");
        assertThat(req.getURI(), a -> a.isEqualTo("/foo?bar"));
        assertThat(req.path(), a -> a.isEqualTo("/foo"));
        assertThat(req.query(), a -> a.isEqualTo("bar"));
        req.setURI("/baz?key=value");
        assertThat(req.getURI(), a -> a.isEqualTo("/baz?key=value"));
        assertThat(req.path(), a -> a.isEqualTo("/baz"));
        assertThat(req.query(), a -> a.isEqualTo("key=value"));
        req.setURI("");
        assertThat(req.getURI(), a -> a.isEqualTo(""));
        assertThat(req.path(), a -> a.isEqualTo(""));
        assertThat(req.query(), a -> a.isNull());
        req.setURI("?");
        assertThat(req.getURI(), a -> a.isEqualTo("?"));
        assertThat(req.path(), a -> a.isEqualTo(""));
        assertThat(req.query(), a -> a.isEqualTo(""));
        testComplete();
      }));
    }));
    await();
  }
}
