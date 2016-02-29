/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JksOptions;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientTest extends Http2TestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    client = vertx.createHttpClient(new HttpClientOptions().
        setUseAlpn(true).
        setTrustStoreOptions((JksOptions) getClientTrustOptions(Trust.JKS)).
        setProtocolVersion(HttpVersion.HTTP_2));
  }

  @Test
  public void testGet() throws Exception {
    String expected = TestUtils.randomAlphaString(100);
    AtomicInteger reqCount = new AtomicInteger();
    server.requestHandler(req -> {
      assertEquals("https", req.scheme());
      assertEquals(HttpMethod.GET, req.method());
      assertEquals("/somepath", req.path());
      reqCount.incrementAndGet();
      req.response().end(expected);
    });
    startServer();
    client.get(4043, "localhost", "/somepath", resp -> {
      assertEquals(1, reqCount.get());
      Buffer content = Buffer.buffer();
      resp.handler(content::appendBuffer);
      resp.endHandler(v -> {
        assertEquals(expected, content.toString());
        testComplete();
      });
    }).exceptionHandler(err -> {
      testComplete();
    }).end();
    await();
  }
}
