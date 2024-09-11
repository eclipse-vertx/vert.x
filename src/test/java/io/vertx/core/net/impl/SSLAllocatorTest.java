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

package io.vertx.core.net.impl;

import javax.net.ssl.SSLContext;

import org.junit.Test;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.ssl.SslProvider;
import io.vertx.core.buffer.impl.PartialPooledByteBufAllocator;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

public class SSLAllocatorTest extends VertxTestBase {

  @Test
  public void testUsePartialPooledByteBufAllocatorInstanceWhenNotSpecified() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, null, null);
    SSLHelper helper = new SSLHelper(new HttpClientOptions()
      .setSsl(true)
      .setKeyStoreOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()),
      null);
    helper
      .buildContextProvider(new SSLOptions().setKeyCertOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()), (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(provider -> {
        assertSame(SslProvider.JDK, provider.sslProvider());
        assertSame(PartialPooledByteBufAllocator.INSTANCE, helper.clientByteBufAllocator(provider));
        assertSame(PartialPooledByteBufAllocator.INSTANCE, helper.serverByteBufAllocator(provider));
        testComplete();
      }));
    await();
  }

  @Test
  public void testUsePartialPooledByteBufAllocatorInstanceIfDefaultJDKSSLIsConfigured() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, null, null);
    SSLHelper helper = new SSLHelper(new HttpClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new JdkSSLEngineOptions())
      .setKeyStoreOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()),
      null);
    helper
      .buildContextProvider(new SSLOptions().setKeyCertOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()), (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(provider -> {
        assertSame(SslProvider.JDK, provider.sslProvider());
        assertSame(PartialPooledByteBufAllocator.INSTANCE, helper.clientByteBufAllocator(provider));
        assertSame(PartialPooledByteBufAllocator.INSTANCE, helper.serverByteBufAllocator(provider));
        testComplete();
      }));
    await();
  }

  @Test
  public void testUsePooledByteBufAllocatorDefaultIfJDKSSLPooledHeapBufferConfigured() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, null, null);
    SSLHelper helper = new SSLHelper(new HttpClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new JdkSSLEngineOptions().setPooledHeapBuffers(true))
      .setKeyStoreOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()),
      null);
    helper
      .buildContextProvider(new SSLOptions().setKeyCertOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()), (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(provider -> {
        assertSame(SslProvider.JDK, provider.sslProvider());
        assertSame(PooledByteBufAllocator.DEFAULT, helper.clientByteBufAllocator(provider));
        assertSame(PooledByteBufAllocator.DEFAULT, helper.serverByteBufAllocator(provider));
        testComplete();
      }));
    await();
  }

  @Test
  public void testClientUsePartialPooledByteBufAllocatorInstanceIfSSLNotConfigured() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, null, null);
    SSLHelper helper = new SSLHelper(new HttpClientOptions()
      .setSsl(false)
      .setKeyStoreOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()),
      null);
    helper
      .buildContextProvider(new SSLOptions().setKeyCertOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()), (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(provider -> {
        // this shouldn't happen, really, because of options::setSsl(false)
        assertSame(PartialPooledByteBufAllocator.INSTANCE, helper.clientByteBufAllocator(provider));
        testComplete();
      }));
    await();
  }

  @Test
  public void testServerUsePooledByteBufAllocatorInstanceIfSSLNotConfigured() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, null, null);
    SSLHelper helper = new SSLHelper(new HttpServerOptions()
      .setSsl(false)
      .setKeyStoreOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()),
      null);
    helper
      .buildContextProvider(new SSLOptions().setKeyCertOptions(Cert.CLIENT_JKS.get()).setTrustOptions(Trust.SERVER_JKS.get()), (ContextInternal) vertx.getOrCreateContext())
      .onComplete(onSuccess(provider -> {
        // this shouldn't happen, really, because of options::setSsl(false)
        assertSame(PooledByteBufAllocator.DEFAULT, helper.serverByteBufAllocator(provider));
        testComplete();
      }));
    await();
  }

  @Test
  public void testUsePooledByteBufAllocatorDefaultIfOpenSSLIsConfigured() {
    SSLHelper helper = new SSLHelper(
      new HttpClientOptions().setOpenSslEngineOptions(new OpenSSLEngineOptions())
        .setSsl(true)
        .setPemKeyCertOptions(Cert.CLIENT_PEM.get()).setTrustOptions(Trust.SERVER_PEM.get()),
      null);
    helper.buildContextProvider(new SSLOptions().setKeyCertOptions(Cert.CLIENT_PEM.get()).setTrustOptions(Trust.SERVER_PEM.get()), (ContextInternal) vertx.getOrCreateContext()).onComplete(onSuccess(provider -> {
      assertSame(SslProvider.OPENSSL, provider.sslProvider());
      assertSame(PartialPooledByteBufAllocator.INSTANCE, helper.clientByteBufAllocator(provider));
      assertSame(PartialPooledByteBufAllocator.INSTANCE, helper.serverByteBufAllocator(provider));
      testComplete();
    }));
    await();
  }


}
