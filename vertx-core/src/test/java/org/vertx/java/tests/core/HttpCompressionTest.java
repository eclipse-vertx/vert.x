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

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpCompressionTest extends HttpTestBase {

  @Before
  public void before() {
    client.setTryUseCompression(true);
    server.setCompressionSupported(true);
  }

  @Test
  public void testDefaultRequestHeaders() {
    server.requestHandler(req -> {
      assertEquals(2, req.headers().size());
      assertEquals("localhost:" + port, req.headers().get("host"));
      assertNotNull(req.headers().get("Accept-Encoding"));
      req.response().end();
    });

    server.listen(port, onSuccess(server -> {
      client.getNow("some-uri", resp -> testComplete());
    }));

    await();
  }
}
