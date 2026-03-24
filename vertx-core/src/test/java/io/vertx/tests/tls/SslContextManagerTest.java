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
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.tls.ServerSslContextManager;
import io.vertx.core.internal.tls.ServerSslContextProvider;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.apache.logging.log4j.core.jmx.Server;
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
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(Cert.SERVER_JKS.get());
    for (String suite : engine.getEnabledCipherSuites()) {
      options.addEnabledCipherSuite(suite);
    }
    assertEquals(new ArrayList<>(options.getEnabledCipherSuites()), Arrays.asList(engine.getEnabledCipherSuites()));
    JsonObject json = options.toJson();
    assertEquals(new ArrayList<>(new HttpServerOptions(json).getEnabledCipherSuites()), Arrays.asList(engine.getEnabledCipherSuites()));
    ServerSslContextManager helper = new ServerSslContextManager(SslContextManager.resolveEngineOptions(null, false));
    helper
      .resolveSslContextProvider(options, false, (ContextInternal) vertx.getOrCreateContext())
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
}
