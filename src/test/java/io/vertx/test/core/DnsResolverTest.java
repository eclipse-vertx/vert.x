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

import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.test.fakedns.FakeDNSServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DnsResolverTest extends VertxTestBase {

  private FakeDNSServer dnsServer;
  private InetSocketAddress dnsServerAddress;

  @Override
  public void setUp() throws Exception {
    dnsServer = FakeDNSServer.testResolveASameServer("127.0.0.1");
    dnsServer.start();
    dnsServerAddress = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    if (dnsServer.isStarted()) {
      dnsServer.stop();
    }
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getHostnameResolverOptions().addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort());
    options.getHostnameResolverOptions().setOptResourceEnabled(false);
    return options;
  }

  @Test
  public void testAsyncResolve() throws Exception {
    ((VertxImpl)vertx).resolveHostname("vertx.io", onSuccess(resolved -> {
      assertEquals("127.0.0.1", resolved.getHostAddress());
      testComplete();
    }));
    await();
  }

  @Test
  public void testAsyncResolveFail() throws Exception {
    ((VertxImpl)vertx).resolveHostname("vertx.com", onFailure(failure -> {
      assertEquals(UnknownHostException.class, failure.getClass());
      testComplete();
    }));
    await();
  }

  @Test
  public void testNet() throws Exception {
    NetClient client = vertx.createNetClient();
    NetServer server = vertx.createNetServer().connectHandler(so -> {
      so.handler(buff -> {
        so.write(buff);
        so.close();
      });
    });
    try {
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(1234, "vertx.io", onSuccess(s -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.connect(1234, "vertx.io", onSuccess(so -> {
        Buffer buffer = Buffer.buffer();
        so.handler(buffer::appendBuffer);
        so.closeHandler(v -> {
          assertEquals(Buffer.buffer("foo"), buffer);
          testComplete();
        });
        so.write(Buffer.buffer("foo"));
      }));
      await();
    } finally {
      client.close();
      server.close();
    }
  }

  @Test
  public void testHttp() throws Exception {
    HttpClient client = vertx.createHttpClient();
    HttpServer server = vertx.createHttpServer().requestHandler(req -> {
      req.response().end("foo");
    });
    try {
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(8080, "vertx.io", onSuccess(s -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.getNow(8080, "vertx.io", "/somepath", resp -> {
        Buffer buffer = Buffer.buffer();
        resp.handler(buffer::appendBuffer);
        resp.endHandler(v -> {
          assertEquals(Buffer.buffer("foo"), buffer);
          testComplete();
        });
      });
      await();
    } finally {
      client.close();
      server.close();
    }
  }
}
