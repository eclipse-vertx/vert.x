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

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.net.impl.KeyStoreHelper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2TestBase extends HttpTestBase {

  protected HttpServerOptions serverOptions;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    serverOptions = new HttpServerOptions()
        .setPort(4043)
        .setHost("localhost")
        .setUseAlpn(true)
        .setSsl(true)
        .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA") // Non Diffie-helman -> debuggable in wireshark
        .setKeyStoreOptions((JksOptions) getServerCertOptions(KeyCert.JKS));

    server = vertx.createHttpServer(serverOptions);

  }

  protected void startServer() throws Exception {
    startServer(vertx.getOrCreateContext());
  }

  protected void startServer(Context context) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    context.runOnContext(v -> {
      server.listen(onSuccess(s -> latch.countDown()));
    });
    awaitLatch(latch);
  }

  protected void assertOnIOContext(Context context) {
    assertEquals(context, Vertx.currentContext());
    for (StackTraceElement elt : Thread.currentThread().getStackTrace()) {
      if (elt.getMethodName().equals("executeFromIO")) {
        return;
      }
    }
    fail("Not from IO");
  }

  protected SSLContext createSSLContext() throws Exception {
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, (TrustOptions) getServerCertOptions(KeyCert.JKS));
    TrustManager[] trustMgrs = helper.getTrustMgrs((VertxInternal) vertx);
    SSLContext context = SSLContext.getInstance("SSL");
    context.init(null, trustMgrs, new java.security.SecureRandom());
    return context;
  }

  protected Http2Settings randomSettings() {
    int headerTableSize = 10 + TestUtils.randomPositiveInt() % (Http2CodecUtil.MAX_HEADER_TABLE_SIZE - 10);
    boolean enablePush = TestUtils.randomBoolean();
    long maxConcurrentStreams = TestUtils.randomPositiveLong() % (Http2CodecUtil.MAX_CONCURRENT_STREAMS - 10);
    int initialWindowSize = 10 + TestUtils.randomPositiveInt() % (Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE - 10);
    int maxFrameSize = Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND + TestUtils.randomPositiveInt() % (Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND - Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
    int maxHeaderListSize = 10 + TestUtils.randomPositiveInt() % (int) (Http2CodecUtil.MAX_HEADER_LIST_SIZE - 10);
    Http2Settings settings = new Http2Settings();
    settings.headerTableSize(headerTableSize);
    settings.pushEnabled(enablePush);
    settings.maxConcurrentStreams(maxConcurrentStreams);
    settings.initialWindowSize(initialWindowSize);
    settings.maxFrameSize(maxFrameSize);
    settings.maxHeaderListSize(maxHeaderListSize);
    return settings;
  }
}
