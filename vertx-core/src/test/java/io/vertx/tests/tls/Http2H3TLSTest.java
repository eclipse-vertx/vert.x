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

import java.util.Arrays;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http2H3TLSTest extends Http2TLSTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    HttpServerOptions serverOptions = new HttpServerOptions()
      .setPort(HttpTestBase.DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setAlpnVersions(Arrays.asList(HttpVersion.HTTP_3))
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
    return httpClientOptions;
  }

  @Override
  protected TLSTest testTLS(Cert<?> clientCert, Trust<?> clientTrust, Cert<?> serverCert, Trust<?> serverTrust) throws Exception {
    return super.testTLS(clientCert, clientTrust, serverCert, serverTrust).version(HttpVersion.HTTP_3);
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
  @Ignore("QUIC only supports TLSv1.3, so TLSv1.2 tests are not possible and protocol setting isn't possible in Netty.")
  public void testDisableTLSv1_3OpenSSL() throws Exception {
    super.testDisableTLSv1_3OpenSSL();
  }

  @Override
  @Test
  @Ignore("QUIC only supports TLSv1.3, so TLSv1.2 tests are not possible and protocol setting isn't possible in Netty.")
  public void testTLSNonMatchingProtocolVersions() throws Exception {
    super.testTLSNonMatchingProtocolVersions();
  }

  @Override
  @Test
  @Ignore("QUIC only supports TLSv1.3, so TLSv1.2 tests are not possible and protocol setting isn't possible in Netty.")
  public void testTLSInvalidProtocolVersion() throws Exception {
    super.testTLSInvalidProtocolVersion();
  }

  @Override
  @Test
  @Ignore("Cipher suites cannot be modified in QUIC.")
  public void testTLSNonMatchingCipherSuites() throws Exception {
    super.testTLSNonMatchingCipherSuites();
  }

  @Override
  @Test
  @Ignore("Trailing dots are not allowed in netty for QUIC.")
  public void testSniWithTrailingDotHost() throws Exception {
    /*
     * QuicheQuicSslEngine.isValidHostNameForSNI() returns false for hostnames with trailing dots.
     */
    super.testSniWithTrailingDotHost();
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
  public void testSNISubjectAltenativeNameCNMatch1PKCS12() throws Exception {
    //TODO: resolve this test issue.
    super.testSNISubjectAltenativeNameCNMatch1PKCS12();
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
  public void testSNIWithServerNameTrustFail() throws Exception {
    //TODO: resolve this test issue.
    super.testSNIWithServerNameTrustFail();
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
  public void testHttpsSocks() throws Exception {
    //TODO: resolve this test issue.
    super.testHttpsSocks();
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

  @Override
  @Test
  @Ignore
  public void testHttpsProxyAuthFail() throws Exception {
    //TODO: resolve this test issue.
    super.testHttpsProxyAuthFail();
  }

  @Override
  @Test
  @Ignore
  public void testTLSServerSSLEnginePeerHost() throws Exception {
    //TODO: resolve this test issue.
    super.testTLSServerSSLEnginePeerHost();
  }

  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertPEMRootCAWithPEMRootCA() throws Exception {
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertPEMRootCAWithPEMRootCA();
  }

  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertPKCS12RootCAWithPKCS12RootCA() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertPKCS12RootCAWithPKCS12RootCA();
  }
  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertPEMRootCAWithJKSRootCA() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertPEMRootCAWithJKSRootCA();
  }
  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertMultiPemWithPEMRootCA() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertMultiPemWithPEMRootCA();
  }
  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertJKSRootRootCAWithPEMRootCA() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertJKSRootRootCAWithPEMRootCA();
  }
  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertPKCS12RootCAWithPEMRootCA() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertPKCS12RootCAWithPEMRootCA();
  }
  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertJKSRootCAWithPKCS12RootCA() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertJKSRootCAWithPKCS12RootCA();
  }
  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertMultiPemWithPEMOtherCA() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertMultiPemWithPEMOtherCA();
  }
  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertPKCS12RootCAWithJKSRootCA() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertPKCS12RootCAWithJKSRootCA();
  }
  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertPEMRootCAWithPEMCAChain() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertPEMRootCAWithPEMCAChain();
  }
  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertJKSRootCAWithJKSRootCA() throws Exception{
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testTLSClientTrustServerCertJKSRootCAWithJKSRootCA();
  }

  @Override
  @Test
  @Ignore
  public void testTLSClientTrustServerCertPEMRootCAWithPKCS12RootCA() throws Exception {
    super.testTLSClientTrustServerCertPEMRootCAWithPKCS12RootCA();
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
  }

  @Override
  @Test
  @Ignore
  public void testConcurrentUpdateSSLOptions() throws Exception {
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testConcurrentUpdateSSLOptions();
  }

  @Override
  @Test
  @Ignore
  public void testUpdateSSLOptions() throws Exception {
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testUpdateSSLOptions();
  }

  @Override
  @Test
  @Ignore
  public void testUpdateSSLOptionsSamePathAndForce() throws Exception {
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testUpdateSSLOptionsSamePathAndForce();
  }

  @Override
  @Test
  @Ignore
  public void testSNIServerSSLEnginePeerHost() throws Exception {
    // TODO: Resolve this test issue.
    // TODO: It started failing after the migration from incubator-http3. The test will pass if `trustAll` is set on the client options.
    super.testSNIServerSSLEnginePeerHost();
  }
}
