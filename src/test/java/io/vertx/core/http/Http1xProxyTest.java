/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import org.junit.Ignore;
import org.junit.Test;

import java.util.function.Consumer;

public class Http1xProxyTest extends HttpTestBase {

  @Test
  public void testHttpProxyRequest() throws Exception {
    startProxy(null, ProxyType.HTTP);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setHost("localhost").setPort(proxy.getPort())));
    testHttpProxyRequest2(handler -> client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", handler));
  }

  @Test
  public void testHttpProxyRequestOverrideClientSsl() throws Exception {
    startProxy(null, ProxyType.HTTP);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true).setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setHost("localhost").setPort(proxy.getPort())));
    testHttpProxyRequest2(handler -> client.get(new RequestOptions().setSsl(false).setHost("localhost").setPort(8080), handler));
  }

  private void testHttpProxyRequest2(Consumer<Handler<AsyncResult<HttpClientResponse>>> reqFact) throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      reqFact.accept(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        assertNotNull("request did not go through proxy", proxy.getLastUri());
        assertEquals("Host header doesn't contain target host", "localhost:8080", proxy.getLastRequestHeaders().get("Host"));
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testHttpProxyRequestAuth() throws Exception {
    startProxy("user", ProxyType.HTTP);

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setHost("localhost").setPort(proxy.getPort())
        .setUsername("user").setPassword("user")));

    server.requestHandler(req -> {
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        assertNotNull("request did not go through proxy", proxy.getLastUri());
        assertEquals("Host header doesn't contain target host", "localhost:8080", proxy.getLastRequestHeaders().get("Host"));
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testHttpProxyFtpRequest() throws Exception {
    startProxy(null, ProxyType.HTTP);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setHost("localhost").setPort(proxy.getPort())));
    final String url = "ftp://ftp.gnu.org/gnu/";
    proxy.setForceUri("http://localhost:8080/");
    server.requestHandler(req -> {
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.get(new RequestOptions().setURI(url), onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("request did sent the expected url", url, proxy.getLastUri());
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testHttpSocksProxyRequest() throws Exception {
    startProxy(null, ProxyType.SOCKS5);

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("localhost").setPort(proxy.getPort())));

    server.requestHandler(req -> req.response().end());

    startServer();

    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      assertNotNull("request did not go through proxy", proxy.getLastUri());
      testComplete();
    }));
    await();
  }

  @Test
  public void testHttpSocksProxyRequestAuth() throws Exception {
    startProxy("user", ProxyType.SOCKS5);

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("localhost").setPort(proxy.getPort())
        .setUsername("user").setPassword("user")));

    server.requestHandler(req -> {
      req.response().end();
    });

    startServer();

    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      assertNotNull("request did not go through proxy", proxy.getLastUri());
      testComplete();
    }));
    await();
  }
}
