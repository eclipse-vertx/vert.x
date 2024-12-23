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

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3TLSTest extends HttpTLSTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    HttpServerOptions serverOptions = new HttpServerOptions()
      .setPort(HttpTestBase.DEFAULT_HTTPS_PORT)
      .setHttp3(true)
      .setUseAlpn(true)
      .setSsl(true);

    return serverOptions;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    HttpClientOptions httpClientOptions = new HttpClientOptions()
      .setUseAlpn(true)
      .setSsl(true)
      .setProtocolVersion(HttpVersion.HTTP_3);
    httpClientOptions.setHttp3(true);
    return httpClientOptions;
  }

  @Override
  protected TLSTest testTLS(Cert<?> clientCert, Trust<?> clientTrust, Cert<?> serverCert, Trust<?> serverTrust) throws Exception {
    return super.testTLS(clientCert, clientTrust, serverCert, serverTrust).version(HttpVersion.HTTP_3);
  }

  @Override
  @Test
  @Ignore
  public void testEngineUseEventLoopThread() throws Exception {
    //TODO: resolve this test issue.
    super.testEngineUseEventLoopThread();
  }

  @Override
  @Test
  @Ignore("QUIC only supports TLSv1.3, so TLSv1.2 tests are not possible and protocol setting isn't possible in Netty.")
  public void testDisableTLSv1_2OpenSSL() throws Exception {
    super.testDisableTLSv1_2OpenSSL();
  }

  @Override
  @Test
  @Ignore("QUIC only supports TLSv1.3, so TLSv1.2 tests are not possible and protocol setting isn't possible in Netty.")
  public void testDisableTLSv1_2() throws Exception {
    super.testDisableTLSv1_2();
  }

  @Override
  @Test
  @Ignore("QUIC only supports TLSv1.3, so TLSv1.2 tests are not possible and protocol setting isn't possible in Netty.")
  public void testDisableTLSv1_3() throws Exception {
    super.testDisableTLSv1_3();
  }

  @Override
  @Test
  @Ignore
  public void testTLSClientRevokedServerCert() throws Exception {
    //TODO: resolve this test issue.
    super.testTLSClientRevokedServerCert();
  }

  @Override
  @Test
  @Ignore
  public void testTLSVerifyNonMatchingHost() throws Exception {
    //TODO: resolve this test issue.
    super.testTLSVerifyNonMatchingHost();
  }

  @Override
  @Test
  @Ignore
  public void testTLSClientUntrustedServerCertPEMRootCAWithPEMCA() throws Exception {
    //TODO: resolve this test issue.
    super.testTLSClientUntrustedServerCertPEMRootCAWithPEMCA();
  }

  @Override
  @Test
  @Ignore
  public void testServerSharingUpdateSSLOptions() throws Exception {
    //TODO: resolve this test issue.
    super.testServerSharingUpdateSSLOptions();
  }

  @Override
  @Test
  @Ignore
  public void testSNISubjectAltenativeNameCNMatch1() throws Exception {
    //TODO: resolve this test issue.
    super.testSNISubjectAltenativeNameCNMatch1();
  }

  @Override
  @Test
  @Ignore
  public void testTLSVerifyNonMatchingHostOpenSSL() throws Exception {
    //TODO: resolve this test issue.
    super.testTLSVerifyNonMatchingHostOpenSSL();
  }

  @Override
  @Test
  @Ignore
  public void testSNISubjectAltenativeNameCNMatch1PKCS12() throws Exception {
    //TODO: resolve this test issue.
    super.testSNISubjectAltenativeNameCNMatch1PKCS12();
  }

  @Override
  @Test
  @Ignore("QUIC only supports TLSv1.3, so TLSv1.2 tests are not possible and protocol setting isn't possible in Netty.")
  public void testDisableTLSv1_3OpenSSL() throws Exception {
    super.testDisableTLSv1_3OpenSSL();
  }

  @Override
  @Test
  @Ignore
  public void testHttpsSocksWithSNI() throws Exception {
    //TODO: resolve this test issue.
    super.testHttpsSocksWithSNI();
  }

  @Override
  @Test
  @Ignore
  public void testSNIWithServerNameTrustFallbackFail() throws Exception {
    //TODO: resolve this test issue.
    super.testSNIWithServerNameTrustFallbackFail();
  }

  @Override
  @Test
  @Ignore
  public void testSNIUnknownServerName1() throws Exception {
    //TODO: resolve this test issue.
    super.testSNIUnknownServerName1();
  }

  @Override
  @Test
  @Ignore
  public void testSniEngineUseEventLoopThread() throws Exception {
    //TODO: resolve this test issue.
    super.testSniEngineUseEventLoopThread();
  }

  @Override
  @Test
  @Ignore
  public void testSNIWithServerNameTrustFail() throws Exception {
    //TODO: resolve this test issue.
    super.testSNIWithServerNameTrustFail();
  }

  @Override
  @Test
  @Ignore
  public void testSNIServerIgnoresExtension1() throws Exception {
    //TODO: resolve this test issue.
    super.testSNIServerIgnoresExtension1();
  }

  @Override
  @Test
  @Ignore
  public void testTLSClientUntrustedServer() throws Exception {
    //TODO: resolve this test issue.
    super.testTLSClientUntrustedServer();
  }

  @Override
  @Test
  @Ignore
  public void testConcurrentUpdateSSLOptions() throws Exception {
    //TODO: resolve this test issue.
    super.testConcurrentUpdateSSLOptions();
  }

  @Override
  @Test
  @Ignore("QUIC only supports TLSv1.3, so TLSv1.2 tests are not possible and protocol setting isn't possible in Netty.")
  public void testTLSNonMatchingProtocolVersions() throws Exception {
    super.testTLSNonMatchingProtocolVersions();
  }

  @Override
  @Test
  @Ignore
  public void testUpdateSSLOptions() throws Exception {
    //TODO: resolve this test issue.
    super.testUpdateSSLOptions();
  }

  @Override
  @Test
  @Ignore
  public void testSNIDontSendServerNameForShortnames1() throws Exception {
    //TODO: resolve this test issue.
    super.testSNIDontSendServerNameForShortnames1();
  }

  @Override
  @Test
  @Ignore
  public void testHttpsSocksAuth() throws Exception {
    //TODO: resolve this test issue.
    super.testHttpsSocksAuth();
  }

  @Override
  @Test
  @Ignore
  public void testSNISubjectAltenativeNameCNMatch1PEM() throws Exception {
    //TODO: resolve this test issue.
    super.testSNISubjectAltenativeNameCNMatch1PEM();
  }

  @Override
  @Test
  @Ignore
  public void testUpdateSSLOptionsSamePath() throws Exception {
    //TODO: resolve this test issue.
    super.testUpdateSSLOptionsSamePath();
  }

  @Override
  @Test
  @Ignore
  public void testHttpsSocks() throws Exception {
    //TODO: resolve this test issue.
    super.testHttpsSocks();
  }

  @Override
  @Test
  @Ignore("QUIC only supports TLSv1.3, so TLSv1.2 tests are not possible and protocol setting isn't possible in Netty.")
  public void testTLSInvalidProtocolVersion() throws Exception {
    //TODO: resolve this test issue.
    super.testTLSInvalidProtocolVersion();
  }

  @Override
  @Test
  @Ignore
  public void testEngineUseWorkerThreads() throws Exception {
    //TODO: resolve this test issue.
    super.testEngineUseWorkerThreads();
  }

  @Override
  @Test
  @Ignore
  public void testTLSNonMatchingCipherSuites() throws Exception {
    //TODO: resolve this test issue.
    super.testTLSNonMatchingCipherSuites();
  }

  @Override
  @Test
  @Ignore
  public void testUpdateSSLOptionsSamePathAndForce() throws Exception {
    //TODO: resolve this test issue.
    super.testUpdateSSLOptionsSamePathAndForce();
  }

  @Override
  @Test
  @Ignore
  public void testTLSClientUntrustedServerPEM() throws Exception {
    //TODO: resolve this test issue.
    super.testTLSClientUntrustedServerPEM();
  }

  @Override
  @Test
  @Ignore
  public void testSniEngineUseWorkerThreads() throws Exception {
    //TODO: resolve this test issue.
    super.testSniEngineUseWorkerThreads();
  }

  @Override
  @Test
  @Ignore
  public void testSniWithTrailingDotHost() throws Exception {
    //TODO: resolve this test issue.
    super.testSniWithTrailingDotHost();
  }

  @Override
  @Test
  @Ignore
  public void testUpdateWithInvalidSSLOptions() throws Exception {
    //TODO: resolve this test issue.
    super.testUpdateWithInvalidSSLOptions();
  }

  @Override
  @Test
  @Ignore
  public void testSocksProxyUnknownHost() throws Exception {
    //TODO: resolve this test issue.
    super.testSocksProxyUnknownHost();
  }

  @Override
  @Test
  @Ignore
  public void testHAProxy() throws Exception {
    //TODO: resolve this test issue.
    super.testHAProxy();
  }

  @Override
  @Test
  @Ignore
  public void testHttpsProxyWithSNI() throws Exception {
    //TODO: resolve this test issue.
    super.testHttpsProxyWithSNI();
  }

  @Override
  @Test
  @Ignore
  public void testHttpsProxyUnknownHost() throws Exception {
    //TODO: resolve this test issue.
    super.testHttpsProxyUnknownHost();
  }

  @Override
  @Test
  @Ignore
  public void testHttpsProxy() throws Exception {
    //TODO: resolve this test issue.
    super.testHttpsProxy();
  }

  @Override
  @Test
  @Ignore
  public void testHttpsProxyAuth() throws Exception {
    //TODO: resolve this test issue.
    super.testHttpsProxyAuth();
  }
}
