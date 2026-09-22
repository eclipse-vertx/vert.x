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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.tls.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Assert;
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
    ServerSslContextManager helper = new ServerSslContextManager(SslContextManager.resolveEngineOptions(null, false));
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(Cert.CLIENT_JKS.get())
      .setTrustOptions(Trust.SERVER_JKS.get());
    helper
      .resolveSslContextProvider(options, false, (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(provider -> {
        SslContext ctx = provider.createServerContext(null);
        assertEquals(new HashSet<>(Arrays.asList(expected)), new HashSet<>(ctx.cipherSuites()));
        testComplete();
    }));
    await();
  }

  @Test
  public void testUseOpenSSLCiphersWhenNotSpecified() throws Exception {
    Set<String> expected = OpenSsl.availableOpenSslCipherSuites();
    ServerSslContextManager helper = new ServerSslContextManager(new OpenSSLEngineOptions());
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(Cert.CLIENT_PEM.get())
      .setTrustOptions(Trust.SERVER_PEM.get());
    helper.resolveSslContextProvider(options, false, (ContextInternal) vertx.getOrCreateContext()).onComplete(onSuccess(provider -> {
      SslContext ctx = provider.createServerContext(null);
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

    ServerSslContextManager defaultHelper = new ServerSslContextManager(engineOptions);

    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setKeyCertOptions(Cert.SERVER_PEM.get()).setTrustOptions(Trust.SERVER_PEM.get());

    defaultHelper
      .resolveSslContextProvider(sslOptions, false, (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(provider -> {
        SslContext ctx = provider.createServerContext(null);

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
    List<String> configuredCipherSuites = new ArrayList<>(Arrays.asList(engine.getEnabledCipherSuites()));
    assertTrue(configuredCipherSuites.size() > 1);
    Collections.shuffle(configuredCipherSuites, new Random(12345));
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(Cert.SERVER_JKS.get());
    for (String suite : configuredCipherSuites) {
      options.addEnabledCipherSuite(suite);
    }
    assertEquals(new ArrayList<>(options.getEnabledCipherSuites()), configuredCipherSuites);
    JsonObject json = options.toJson();
    assertEquals(new ArrayList<>(new HttpServerOptions(json).getEnabledCipherSuites()), configuredCipherSuites);
    ServerSslContextManager helper = new ServerSslContextManager(SslContextManager.resolveEngineOptions(null, false));
    helper
      .resolveSslContextProvider(options, false, (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(sslContextProvider -> {
        assertEquals(Arrays.asList(createEngine(sslContextProvider).getEnabledCipherSuites()), configuredCipherSuites);
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
    ServerSslContextManager helper = new ServerSslContextManager(new JdkSSLEngineOptions(), 4);
    ServerSSLOptions options = new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    SslContextProvider f1 = awaitFuture(helper.resolveSslContextProvider(options, ctx));
    SslContextProvider f2 = awaitFuture(helper.resolveSslContextProvider(options, ctx));
    assertSame(f1, f2);
    awaitFuture(helper.resolveSslContextProvider(new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_PKCS12.get()), ctx));
    awaitFuture(helper.resolveSslContextProvider(new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_PEM.get()), ctx));
    awaitFuture(helper.resolveSslContextProvider(new ServerSSLOptions().setKeyCertOptions(Cert.CLIENT_PEM.get()), ctx));
    awaitFuture(helper.resolveSslContextProvider(new ServerSSLOptions().setKeyCertOptions(Cert.SNI_PEM.get()), ctx));
    f2 = awaitFuture(helper.resolveSslContextProvider(options, ctx));
    assertNotSame(f1, f2);
  }

  @Test
  public void testDefaultVersions() {
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(Cert.SERVER_JKS.get());
    testTLSVersions(options, engine -> {
      List<String> protocols = Arrays.asList(engine.getEnabledProtocols());
      assertEquals(2, protocols.size());
      assertTrue(protocols.contains("TLSv1.2"));
      assertTrue(protocols.contains("TLSv1.3"));
    });
  }

  @Test
  public void testSetVersion() {
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      .setEnabledSecureTransportProtocols(new HashSet<>(Arrays.asList("TLSv1.3")));
    testTLSVersions(options, engine -> {
      List<String> protocols = Arrays.asList(engine.getEnabledProtocols());
      assertEquals(1, protocols.size());
      assertTrue(protocols.contains("TLSv1.3"));
    });
  }

  @Test
  public void testSetVersions() {
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      .setEnabledSecureTransportProtocols(new HashSet<>(Arrays.asList("TLSv1", "TLSv1.3")));
    testTLSVersions(options, engine -> {
      List<String> protocols = Arrays.asList(engine.getEnabledProtocols());
      assertEquals(2, protocols.size());
      assertTrue(protocols.contains("TLSv1"));
      assertTrue(protocols.contains("TLSv1.3"));
    });
  }

  private void testTLSVersions(ServerSSLOptions options, Consumer<SSLEngine> check) {
    ServerSslContextManager helper = new ServerSslContextManager(SslContextManager.resolveEngineOptions(null, false));
    helper
      .resolveSslContextProvider(options, false, (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(sslContextProvider -> {
        SSLEngine engine = createEngine(sslContextProvider);
        check.accept(engine);
        testComplete();
      }));
    await();
  }

  public SSLEngine createEngine(ServerSslContextProvider provider) {
    return provider.createServerContext(null).newEngine(ByteBufAllocator.DEFAULT);
  }

  @Test
  public void testClientCacheKey() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ClientSslContextManager helper = new ClientSslContextManager(new JdkSSLEngineOptions());
    ClientSSLOptions options1 = new ClientSSLOptions().setTrustOptions(Trust.SERVER_JKS.get()).setHostnameVerificationAlgorithm("");
    SslContextProvider f1 = awaitFuture(helper.resolveSslContextProvider(options1, ctx));
    ClientSSLOptions options2 = new ClientSSLOptions(options1);
    SslContextProvider f2 = awaitFuture(helper.resolveSslContextProvider(options2, ctx));
    assertSame(f1, f2);
    ClientSSLOptions options3 = new ClientSSLOptions(options1).setHostnameVerificationAlgorithm("HTTPS");
    SslContextProvider f3 = awaitFuture(helper.resolveSslContextProvider(options3, ctx));
    assertNotSame(f1, f3);
    ClientSSLOptions options4 = new ClientSSLOptions(options1).addEnabledSecureTransportProtocol("TLSv1.1");
    SslContextProvider f4 = awaitFuture(helper.resolveSslContextProvider(options4, ctx));
    assertNotSame(f1, f4);
    ClientSSLOptions options5 = new ClientSSLOptions(options1).addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA");
    SslContextProvider f5 = awaitFuture(helper.resolveSslContextProvider(options5, ctx));
    assertNotSame(f1, f5);
    ClientSSLOptions options6 = new ClientSSLOptions(options1).addCrlPath("tls/root-ca/crl.pem");
    SslContextProvider f6 = awaitFuture(helper.resolveSslContextProvider(options6, ctx));
    assertNotSame(f1, f6);
    ClientSSLOptions options7 = new ClientSSLOptions(options1).addCrlValue(Buffer.buffer());
    SslContextProvider f7 = awaitFuture(helper.resolveSslContextProvider(options7, ctx));
    assertNotSame(f1, f7);
  }

  @Test
  public void testServerCacheKey() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ServerSslContextManager helper = new ServerSslContextManager(new JdkSSLEngineOptions());
    ServerSSLOptions options1 = new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    SslContextProvider f1 = awaitFuture(helper.resolveSslContextProvider(options1, false, ctx));
    ServerSSLOptions options2 = new ServerSSLOptions(options1);
    SslContextProvider f2 = awaitFuture(helper.resolveSslContextProvider(options2, false, ctx));
    assertSame(f1, f2);
    ServerSSLOptions options3 = new ServerSSLOptions(options1).setClientAuth(ClientAuth.REQUIRED);
    SslContextProvider f3 = awaitFuture(helper.resolveSslContextProvider(options3, false, ctx));
    assertNotSame(f1, f3);
  }

  private void assertNotSame(SslContextProvider a, SslContextProvider b) {
    Assert.assertNotSame(a, b);
    Assert.assertNotEquals(a.hashCode(), b.hashCode());
  }
}
