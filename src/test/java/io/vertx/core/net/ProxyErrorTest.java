/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import java.net.InetSocketAddress;

import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.test.fakedns.FakeDNSServer;

/**
 * Test all kinds of errors raised by the proxy
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 */
public class ProxyErrorTest extends VertxTestBase {

  private static final Logger log = LoggerFactory.getLogger(ProxyErrorTest.class);

  private HttpProxy proxy = null;

  private FakeDNSServer dnsServer;
  private InetSocketAddress dnsServerAddress;

  @Override
  public void setUp() throws Exception {
    dnsServer = new FakeDNSServer().testLookupNonExisting();
    dnsServer.start();
    dnsServerAddress = dnsServer.localAddress();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    if (dnsServer.isStarted()) {
      dnsServer.stop();
    }
    if (proxy!=null) {
      proxy.stop();
    }
    super.tearDown();
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort());
    options.getAddressResolverOptions().setOptResourceEnabled(false);
    return options;
  }

  // we don't start http/https servers, due to the error, they will not be queried

  private void startProxy(int error, String username) throws Exception {
    proxy = new HttpProxy(username);
    proxy.setError(error);
    proxy.start(vertx);
  }

  @Test
  public void testProxyHttpsError() throws Exception {
    expectProxyException(403, null, "https://localhost/");
  }

  @Test
  public void testProxyHttpsAuthFail() throws Exception {
    expectProxyException(0, "user", "https://localhost/");
  }

  @Test
  public void testProxyHttpsHostUnknown() throws Exception {
    expectProxyException(0, null, "https://unknown.hostname/");
  }

  @Test
  public void testProxyError() throws Exception {
    expectStatusError(403, 403, null, "http://localhost/");
  }

  @Test
  public void testProxyAuthFail() throws Exception {
    expectStatusError(0, 407, "user", "http://localhost/");
  }

  @Test
  public void testProxyHostUnknown() throws Exception {
    expectStatusError(0, 504, null, "http://unknown.hostname/");
  }

  // we expect the request to fail with a ProxyConnectException if we use https
  // so we fail the test when it succeeds
  private void expectProxyException(int error, String username, String url) throws Exception {
    proxyTest(error, username, url, resp -> {
      log.info("request is supposed to fail but response is " + resp.statusCode() + " " + resp.statusMessage());
      fail("request is supposed to fail");
    }, true);
  }

  // we expect the request to fail with a http status error if we use http (behaviour is similar to Squid)
  private void expectStatusError(int error, int responseStatus, String username, String url) throws Exception {
    proxyTest(error, username, url, resp -> {
      assertEquals(responseStatus, resp.statusCode());
      testComplete();
    }, false);
  }

  private void proxyTest(int error, String username, String url, Handler<HttpClientResponse> assertResponse, boolean completeOnException) throws Exception {
    startProxy(error, username);

    final HttpClientOptions options = new HttpClientOptions()
        .setSsl(url.startsWith("https"))
        .setProxyOptions(new ProxyOptions()
            .setType(ProxyType.HTTP)
            .setHost("localhost")
            .setPort(proxy.getPort()));
    HttpClient client = vertx.createHttpClient(options);

    client.getAbs(url, assertResponse)
    .exceptionHandler(e -> {
      if (completeOnException) {
        testComplete();
      } else {
        fail(e);
      }
    })
    .end();

    await();
  }

}
