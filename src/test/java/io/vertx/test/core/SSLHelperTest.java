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
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerOptionsConverter;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetServerOptionsConverter;
import io.vertx.core.net.NetworkOptionsConverter;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.TCPSSLOptionsConverter;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.test.core.tls.Cert;
import io.vertx.test.core.tls.Trust;
import org.junit.Test;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSessionContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
        Cert.CLIENT_JKS.get(),
        Trust.SERVER_JKS.get());
    SslContext ctx = helper.getContext((VertxInternal) vertx);
    assertEquals(new HashSet<>(Arrays.asList(expected)), new HashSet<>(ctx.cipherSuites()));
  }

  @Test
  public void testUseOpenSSLCiphersWhenNotSpecified() throws Exception {
    Set<String> expected = OpenSsl.availableCipherSuites();
    SSLHelper helper = new SSLHelper(
        new HttpClientOptions().setOpenSslEngineOptions(new OpenSSLEngineOptions()),
        Cert.CLIENT_PEM.get(),
        Trust.SERVER_PEM.get());
    SslContext ctx = helper.getContext((VertxInternal) vertx);
    assertEquals(expected, new HashSet<>(ctx.cipherSuites()));
  }

  @Test
  public void testSNIServerNameCanBeSet() throws Exception {
    HttpClientOptions options = new HttpClientOptions();
    options.setOpenSslEngineOptions(new OpenSSLEngineOptions());
    options.setSNIServerName("foo.example.com");
    SSLHelper helper = new SSLHelper(
            options,
            Cert.CLIENT_PEM.get(),
            Trust.SERVER_PEM.get());
    SslHandler handler = helper.createSslHandler((VertxInternal) vertx);
    SSLParameters parameters = handler.engine().getSSLParameters();
    List<SNIServerName> names = parameters.getServerNames();
    assertEquals(1, names.size());
    assertEquals("foo.example.com", ((SNIHostName)names.get(0)).getAsciiName());
  }

  @Test
  public void testSNIMatchersCanBeSet() throws Exception {
    HttpServerOptions options = new HttpServerOptions();
    options.addSNIKeyCertOptionsForDomain("host1.example.com", Cert.SERVER_PEM.get());
    SSLHelper helper = new SSLHelper(
            options,
            Cert.SERVER_PEM.get(),
            null);
    SniHandler handler = helper.createSniHandler((VertxInternal)vertx);
    assertNotNull(handler);
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
            Cert.SERVER_PEM.get(),
            Trust.SERVER_PEM.get());

    SslContext ctx = defaultHelper.getContext((VertxInternal) vertx);
    assertTrue(ctx instanceof OpenSslServerContext);

    SSLSessionContext sslSessionContext = ctx.sessionContext();
    assertTrue(sslSessionContext instanceof OpenSslServerSessionContext);

    if (sslSessionContext instanceof OpenSslServerSessionContext) {
      assertEquals(testDefault, ((OpenSslServerSessionContext) sslSessionContext).isSessionCacheEnabled());
    }
  }

  @Test
  public void testPreserveEnabledCipherSuitesOrder() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, null, null);
    SSLEngine engine = context.createSSLEngine();
    HttpServerOptions options = new HttpServerOptions();
    for (String suite : engine.getEnabledCipherSuites()) {
      options.addEnabledCipherSuite(suite);
    }
    assertEquals(new ArrayList<>(options.getEnabledCipherSuites()), Arrays.asList(engine.getEnabledCipherSuites()));
    assertEquals(new ArrayList<>(new HttpServerOptions(options).getEnabledCipherSuites()), Arrays.asList(engine.getEnabledCipherSuites()));
    JsonObject json = new JsonObject();
    NetworkOptionsConverter.toJson(options, json);
    TCPSSLOptionsConverter.toJson(options, json);
    NetServerOptionsConverter.toJson(options, json);
    HttpServerOptionsConverter.toJson(options, json);
    assertEquals(new ArrayList<>(new HttpServerOptions(json).getEnabledCipherSuites()), Arrays.asList(engine.getEnabledCipherSuites()));
    SSLHelper helper = new SSLHelper(options, Cert.SERVER_JKS.get(), null);
    assertEquals(Arrays.asList(helper.createSslHandler((VertxInternal) vertx).engine().getEnabledCipherSuites()), Arrays.asList(engine.getEnabledCipherSuites()));
  }

  @Test
  public void testPreserveEnabledSecureTransportProtocolOrder() throws Exception {
    String[] protocols = {"SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2"};
    HttpServerOptions options = new HttpServerOptions();
    for (String protocol : protocols) {
      options.addEnabledSecureTransportProtocol(protocol);
    }
    assertEquals(new ArrayList<>(options.getEnabledSecureTransportProtocols()), Arrays.asList(protocols));
    assertEquals(new ArrayList<>(new HttpServerOptions(options).getEnabledSecureTransportProtocols()), Arrays.asList(protocols));
    JsonObject json = new JsonObject();
    NetworkOptionsConverter.toJson(options, json);
    TCPSSLOptionsConverter.toJson(options, json);
    NetServerOptionsConverter.toJson(options, json);
    HttpServerOptionsConverter.toJson(options, json);
    assertEquals(new ArrayList<>(new HttpServerOptions(json).getEnabledSecureTransportProtocols()), Arrays.asList(protocols));
    SSLHelper helper = new SSLHelper(options, Cert.SERVER_JKS.get(), null);
    List<String> engineProtocols = Arrays.asList(helper.createSslHandler((VertxInternal) vertx).engine().getEnabledProtocols());
    List<String> expectedProtocols = new ArrayList<>(Arrays.asList(protocols));
    expectedProtocols.retainAll(engineProtocols);
    assertEquals(engineProtocols, expectedProtocols);
  }
}
