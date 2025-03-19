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

package io.vertx.tests.tls;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.*;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SslContextManagerTest extends VertxTestBase {

  @Test
  public void testUseJdkCiphersWhenNotSpecified() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, null, null);
    SSLEngine engine = context.createSSLEngine();
    String[] expected = engine.getEnabledCipherSuites();
    SslContextManager helper = new SslContextManager(SslContextManager.resolveEngineOptions(null, false));
    helper
      .buildSslContextProvider(new SSLOptions().setKeyCertOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()), null, ClientAuth.NONE, null, false, (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(provider -> {
        SslContext ctx = provider.createContext(false, false, false);
        assertEquals(new HashSet<>(Arrays.asList(expected)), new HashSet<>(ctx.cipherSuites()));
        testComplete();
    }));
    await();
  }

  @Test
  public void testUseOpenSSLCiphersWhenNotSpecified() throws Exception {
    Set<String> expected = OpenSsl.availableOpenSslCipherSuites();
    SslContextManager helper = new SslContextManager(new OpenSSLEngineOptions());
    helper.buildSslContextProvider(new SSLOptions().setKeyCertOptions(Cert.CLIENT_PEM.get()).setTrustOptions(Trust.SERVER_PEM.get()), null, ClientAuth.NONE, null, false, (ContextInternal) vertx.getOrCreateContext()).onComplete(onSuccess(provider -> {
      SslContext ctx = provider.createContext(false, false, false);
      assertEquals(expected, new HashSet<>(ctx.cipherSuites()));
      testComplete();
    }));
    await();
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

    SSLEngineOptions engineOptions;
    if (!testDefault) {
      engineOptions = new OpenSSLEngineOptions().setSessionCacheEnabled(false);
    } else {
      engineOptions = new OpenSSLEngineOptions();
    }

    SslContextManager defaultHelper = new SslContextManager(engineOptions);

    SSLOptions sslOptions = new SSLOptions();
    sslOptions.setKeyCertOptions(Cert.SERVER_PEM.get()).setTrustOptions(Trust.SERVER_PEM.get());

    defaultHelper
      .buildSslContextProvider(sslOptions, null, ClientAuth.NONE, null, false, (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(provider -> {
        SslContext ctx = provider.createContext(true, false, false);

        SSLSessionContext sslSessionContext = ctx.sessionContext();
        assertTrue(sslSessionContext instanceof OpenSslServerSessionContext);

        if (sslSessionContext instanceof OpenSslServerSessionContext) {
          assertEquals(testDefault, ((OpenSslServerSessionContext) sslSessionContext).isSessionCacheEnabled());
        }
      testComplete();
    }));

    await();
  }

  @Test
  public void testPreserveEnabledCipherSuitesOrder() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, null, null);
    SSLEngine engine = context.createSSLEngine();
    SSLOptions options = new SSLOptions();
    for (String suite : engine.getEnabledCipherSuites()) {
      options.addEnabledCipherSuite(suite);
    }
    assertEquals(new ArrayList<>(options.getEnabledCipherSuites()), Arrays.asList(engine.getEnabledCipherSuites()));
    JsonObject json = options.toJson();
    assertEquals(new ArrayList<>(new HttpServerOptions(json).getEnabledCipherSuites()), Arrays.asList(engine.getEnabledCipherSuites()));
    SslContextManager helper = new SslContextManager(SslContextManager.resolveEngineOptions(null, false));
    helper
      .buildSslContextProvider(options, null, ClientAuth.NONE, null, false, (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(sslContextProvider -> {
        assertEquals(new HashSet<>(Arrays.asList(createEngine(sslContextProvider).getEnabledCipherSuites())), new HashSet<>(Arrays.asList(engine.getEnabledCipherSuites())));
        testComplete();
      }));
    await();
  }

  @Test
  public void testPreserveEnabledSecureTransportProtocolOrder() throws Exception {
    HttpServerOptions options = new HttpServerOptions();
    List<String> expectedProtocols = new ArrayList<>(options.getEnabledSecureTransportProtocols());

    options.removeEnabledSecureTransportProtocol("TLSv1");
    options.addEnabledSecureTransportProtocol("SSLv3");
    expectedProtocols.remove("TLSv1");
    expectedProtocols.add("SSLv3");

    assertEquals(new ArrayList<>(options.getEnabledSecureTransportProtocols()), expectedProtocols);
    assertEquals(new ArrayList<>(new HttpServerOptions(options).getEnabledSecureTransportProtocols()), expectedProtocols);
    JsonObject json = options.toJson();
    assertEquals(new ArrayList<>(new HttpServerOptions(json).getEnabledSecureTransportProtocols()), expectedProtocols);
  }

  @Test
  public void testCache() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    SslContextManager helper = new SslContextManager(new JdkSSLEngineOptions(), 4);
    SSLOptions options = new SSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    SslContextProvider f1 = awaitFuture(helper.resolveSslContextProvider(options, "", ClientAuth.NONE, null, ctx));
    SslContextProvider f2 = awaitFuture(helper.resolveSslContextProvider(options, "", ClientAuth.NONE, null, ctx));
    assertSame(f1, f2);
    awaitFuture(helper.resolveSslContextProvider(new SSLOptions().setKeyCertOptions(Cert.SERVER_PKCS12.get()), "", ClientAuth.NONE, null, ctx));
    awaitFuture(helper.resolveSslContextProvider(new SSLOptions().setKeyCertOptions(Cert.SERVER_PEM.get()), "", ClientAuth.NONE, null, ctx));
    awaitFuture(helper.resolveSslContextProvider(new SSLOptions().setKeyCertOptions(Cert.CLIENT_PEM.get()), "", ClientAuth.NONE, null, ctx));
    awaitFuture(helper.resolveSslContextProvider(new SSLOptions().setKeyCertOptions(Cert.SNI_PEM.get()), "", ClientAuth.NONE, null, ctx));
    f2 = awaitFuture(helper.resolveSslContextProvider(options, "", ClientAuth.NONE, null, ctx));
    assertNotSame(f1, f2);
  }

  @Test
  public void testDefaultVersions() {
    testTLSVersions(new SSLOptions(), engine -> {
      List<String> protocols = Arrays.asList(engine.getEnabledProtocols());
      assertEquals(2, protocols.size());
      assertTrue(protocols.contains("TLSv1.2"));
      assertTrue(protocols.contains("TLSv1.3"));
    });
  }

  @Test
  public void testSetVersion() {
    testTLSVersions(new SSLOptions().setEnabledSecureTransportProtocols(new HashSet<>(Arrays.asList("TLSv1.3"))), engine -> {
      List<String> protocols = Arrays.asList(engine.getEnabledProtocols());
      assertEquals(1, protocols.size());
      assertTrue(protocols.contains("TLSv1.3"));
    });
  }

  @Test
  public void testSetVersions() {
    testTLSVersions(new SSLOptions().setEnabledSecureTransportProtocols(new HashSet<>(Arrays.asList("TLSv1", "TLSv1.3"))), engine -> {
      List<String> protocols = Arrays.asList(engine.getEnabledProtocols());
      assertEquals(2, protocols.size());
      assertTrue(protocols.contains("TLSv1"));
      assertTrue(protocols.contains("TLSv1.3"));
    });
  }

  private void testTLSVersions(SSLOptions options, Consumer<SSLEngine> check) {
    SslContextManager helper = new SslContextManager(SslContextManager.resolveEngineOptions(null, false));
    helper
      .buildSslContextProvider(options, null, ClientAuth.NONE, null, false, (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(sslContextProvider -> {
        SSLEngine engine = createEngine(sslContextProvider);
        check.accept(engine);
        testComplete();
      }));
    await();
  }

  public SSLEngine createEngine(SslContextProvider provider) {
    return provider.createContext(false, false, false).newEngine(ByteBufAllocator.DEFAULT);
  }
}
