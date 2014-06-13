/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.tests.core;

import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.http.ClientOptions;
import org.vertx.java.core.http.RequestOptions;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpCompressionTest extends HttpTestBase {

  @Before
  public void before() {
    client = vertx.createHttpClient(new ClientOptions().setTryUseCompression(true));
    server.setCompressionSupported(true);
  }

  @Test
  public void testDefaultRequestHeaders() {
    server.requestHandler(req -> {
      assertEquals(2, req.headers().size());
      assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get("host"));
      assertNotNull(req.headers().get("Accept-Encoding"));
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.getNow(new RequestOptions().setRequestURI("some-uri").setPort(DEFAULT_HTTP_PORT), resp -> testComplete());
    }));

    await();
  }
}
