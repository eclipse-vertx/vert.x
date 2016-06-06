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

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.OpenSslServerSessionContext;
import io.netty.handler.ssl.SslContext;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.impl.SSLHelper;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SSLHelperTest extends VertxTestBase {

  @Test
  public void testUseJdkCiphersWhenNotSpecified() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, null, null);
    SSLEngine engine = context.createSSLEngine();
    String[] expected = engine.getEnabledCipherSuites();
    SSLHelper helper = new SSLHelper(new HttpClientOptions(),
        TLSCert.JKS.getClientKeyCertOptions(),
        TLSCert.JKS.getClientTrustOptions());
    SslContext ctx = helper.getContext((VertxInternal) vertx);
    assertEquals(new HashSet<>(Arrays.asList(expected)), new HashSet<>(ctx.cipherSuites()));
  }

  @Test
  public void testUseOpenSSLCiphersWhenNotSpecified() throws Exception {
    Set<String> expected = OpenSsl.availableCipherSuites();
    SSLHelper helper = new SSLHelper(
        new HttpClientOptions().setOpenSslEngineOptions(new OpenSSLEngineOptions()),
        TLSCert.PEM.getClientKeyCertOptions(),
        TLSCert.PEM.getClientTrustOptions());
    SslContext ctx = helper.getContext((VertxInternal) vertx);
    assertEquals(expected, new HashSet<>(ctx.cipherSuites()));
  }

  @Test
  public void testDefaultOpenSslServerSessionContext() throws Exception {
    testOpenSslServerSessionContext(true);
  }

  @Test
  public void testUserSetOpenSslServerSessionContext() throws Exception {
    testOpenSslServerSessionContext(false);
  }

  private void testOpenSslServerSessionContext(boolean testDefault){
    HttpServerOptions httpServerOptions = new HttpServerOptions().setOpenSslEngineOptions(new OpenSSLEngineOptions());

    if(!testDefault) {
      httpServerOptions.setOpenSslEngineOptions(new OpenSSLEngineOptions().setSessionCacheEnabled(false));
    }

    SSLHelper defaultHelper = new SSLHelper(httpServerOptions,
            TLSCert.PEM.getServerKeyCertOptions(),
            TLSCert.PEM.getServerTrustOptions());

    SslContext ctx = defaultHelper.getContext((VertxInternal) vertx);
    assertTrue(ctx instanceof OpenSslServerContext);

    SSLSessionContext sslSessionContext = ctx.sessionContext();
    assertTrue(sslSessionContext instanceof OpenSslServerSessionContext);

    if (sslSessionContext instanceof OpenSslServerSessionContext) {
      assertEquals(testDefault, ((OpenSslServerSessionContext) sslSessionContext).isSessionCacheEnabled());
    }
  }
}
