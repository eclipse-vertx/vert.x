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
package io.vertx.core.http.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;

import org.junit.Test;

import io.vertx.core.http.HttpTestBase;
import io.vertx.core.http.RequestOptions;

public class HttpClientRequestBaseTest extends HttpTestBase {

  @Test
  public void testPathCacheAndQueryCache() {
    server.requestHandler(req -> {});
    server.listen(testAddress, onSuccess(server -> {
      client.request(new RequestOptions(requestOptions).setURI("/?"), onSuccess(req -> {
        assertThat(req.getURI(), is("/?"));
        assertThat(req.path(), is("/"));
        assertThat(req.query(), is(""));
        req.setURI("/index.html");
        assertThat(req.getURI(), is("/index.html"));
        assertThat(req.path(), is("/index.html"));
        assertThat(req.query(), is(nullValue()));
        req.setURI("/foo?bar");
        assertThat(req.getURI(), is("/foo?bar"));
        assertThat(req.path(), is("/foo"));
        assertThat(req.query(), is("bar"));
        req.setURI("/baz?key=value");
        assertThat(req.getURI(), is("/baz?key=value"));
        assertThat(req.path(), is("/baz"));
        assertThat(req.query(), is("key=value"));
        req.setURI("");
        assertThat(req.getURI(), is(""));
        assertThat(req.path(), is(""));
        assertThat(req.query(), is(nullValue()));
        req.setURI("?");
        assertThat(req.getURI(), is("?"));
        assertThat(req.path(), is(""));
        assertThat(req.query(), is(""));
        testComplete();
      }));
    }));
    await();
  }
}
