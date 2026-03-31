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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientBuilder;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerBuilder;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.net.impl.TrustAllTrustManager;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpClientConfig;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.HttpServerConfig;
import io.vertx.test.http.SimpleHttpTest;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpTLSTest extends SimpleHttpTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  public HttpTLSTest(HttpConfig config) {
    super(config, ReportMode.FORBIDDEN);
  }

  private void assumeTcp() {
    Assume.assumeTrue(config.version() != HttpVersion.HTTP_3);
  }

  private void assumeOpenSSL() {
    assumeTcp();
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
        "127.0.0.1 localhost\n" +
        "127.0.0.1 host1\n" +
        "127.0.0.1 host2.com\n" +
        "127.0.0.1 sub.host3.com\n" +
        "127.0.0.1 host4.com\n" +
        "127.0.0.1 www.host4.com\n" +
        "127.0.0.1 host5.com\n" +
        "127.0.0.1 www.host5.com\n" +
        "127.0.0.1 unknown.com"));
    return options;
  }

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertKeyStore() throws Exception {
    testTLS(Cert.NONE, () -> {
      JksOptions opts = Trust.SERVER_JKS.get();
      return new KeyStoreOptions().setType("JKS").setPath(opts.getPath()).setPassword(opts.getPassword());
    }, () -> {
      JksOptions opts = Cert.SERVER_JKS.get();
      return new KeyStoreOptions().setType("JKS").setPath(opts.getPath()).setPassword(opts.getPassword());
    }, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPKCS12() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PKCS12, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPEM() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PEM, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a root CA (not trust all)
  public void testTLSClientTrustServerCertJKSRootCAWithJKSRootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS_ROOT_CA, Cert.SERVER_JKS_ROOT_CA, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a root CA (not trust all)
  public void testTLSClientTrustServerCertJKSRootCAWithPKCS12RootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PKCS12_ROOT_CA, Cert.SERVER_JKS_ROOT_CA, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a root CA (not trust all)
  public void testTLSClientTrustServerCertJKSRootRootCAWithPEMRootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_JKS_ROOT_CA, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a root CA (not trust all)
  public void testTLSClientTrustServerCertPKCS12RootCAWithJKSRootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS_ROOT_CA, Cert.SERVER_PKCS12_ROOT_CA, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a root CA (not trust all)
  public void testTLSClientTrustServerCertPKCS12RootCAWithPKCS12RootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PKCS12_ROOT_CA, Cert.SERVER_PKCS12_ROOT_CA, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a root CA (not trust all)
  public void testTLSClientTrustServerCertPKCS12RootCAWithPEMRootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_PKCS12_ROOT_CA, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a root CA (not trust all)
  public void testTLSClientTrustServerCertPEMRootCAWithJKSRootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS_ROOT_CA, Cert.SERVER_PEM_ROOT_CA, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a root CA (not trust all)
  public void testTLSClientTrustServerCertPEMRootCAWithPKCS12RootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PKCS12_ROOT_CA, Cert.SERVER_PEM_ROOT_CA, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a root CA (not trust all)
  public void testTLSClientTrustServerCertPEMRootCAWithPEMRootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_PEM_ROOT_CA, Trust.NONE).pass();
  }

  // These two tests should be grouped in same method - todo later
  @Test
  // Server specifies cert that the client trusts via a root CA that is in a multi pem store (not trust all)
  public void testTLSClientTrustServerCertMultiPemWithPEMRootCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA_AND_OTHER_CA, Cert.SERVER_PEM_ROOT_CA, Trust.NONE).pass();
  }
  @Test
  // Server specifies cert that the client trusts via a other CA that is in a multi pem store (not trust all)
  public void testTLSClientTrustServerCertMultiPemWithPEMOtherCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA_AND_OTHER_CA, Cert.SERVER_PEM_OTHER_CA, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert chain that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPEMRootCAWithPEMCAChain() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_PEM_CA_CHAIN, Trust.NONE).pass();
  }

  @Test
  // Server specifies intermediate cert that the client doesn't trust because it is missing the intermediate CA signed by the root CA
  public void testTLSClientUntrustedServerCertPEMRootCAWithPEMCA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_PEM_INT_CA, Trust.NONE).fail();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPKCS12ServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PKCS12, Cert.SERVER_JKS, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPEMServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM, Cert.SERVER_JKS, Trust.NONE).pass();
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).fail();
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServerPEM() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_PEM, Trust.NONE).fail();
  }

  @Test
  // Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).pass();
  }

  @Test
  // Client specifies cert even though it's not required
  public void testTLSClientCertNotRequiredPEM() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_PEM, Trust.CLIENT_JKS).pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertRequiredPKCS12() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PKCS12).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertRequiredPEM() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PEM).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertPKCS12Required() throws Exception {
    testTLS(Cert.CLIENT_PKCS12, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertPEMRequired() throws Exception {
    testTLS(Cert.CLIENT_PEM, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert by CA and it is required
  public void testTLSClientCertPEM_CARequired() throws Exception {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PEM_ROOT_CA).requiresClientAuth().pass();
  }

  @Test
  // Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).requiresClientAuth().fail();
  }

  @Test
  // Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(Cert.SERVER_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).requiresClientAuth().fail();
  }

  @Test
  // Server specifies cert that the client does not trust via a revoked certificate of the CA
  public void testTLSClientRevokedServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_PEM_ROOT_CA, Trust.NONE).clientUsesCrl().fail();
  }

  @Test
  // Client specifies cert that the server does not trust via a revoked certificate of the CA
  public void testTLSRevokedClientCertServer() throws Exception {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PEM_ROOT_CA).requiresClientAuth().serverUsesCrl().fail();
  }

  @Test
  // Specify some matching cipher suites
  public void testTLSMatchingCipherSuites() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().serverEnabledCipherSuites(ENABLED_CIPHER_SUITES).pass();
  }

  @Test
  // Specify some non matching cipher suites
  public void testTLSNonMatchingCipherSuites() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().serverEnabledCipherSuites(new String[]{"TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"}).clientEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"}).fail();
  }

  @Test
  // Specify some matching TLS protocols
  public void testTLSMatchingProtocolVersions() throws Exception {
    assumeTcp();
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"}).pass();
  }

  @Test
  // Provide a host name with a trailing dot
  public void testTLSTrailingDotHost() throws Exception {
    // Reuse SNI test certificate because it is convenient
    TLSTest test = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
      .serverSni()
      .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com."))
      .pass();
    Assert.assertEquals("host2.com", TestUtils.cnOf(test.clientPeerCert()));
  }

  /*

  checks that we can enable algorithms

  static
  {
    Security.setProperty("jdk.tls.disabledAlgorithms", "");
  }

  @Test
  public void testEnableProtocols() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientEnabledSecureTransportProtocol(new String[]{"SSLv3"})
      .serverEnabledSecureTransportProtocol(new String[]{"SSLv3"})
      .pass();
  }
  */

  @Test
  // Specify some matching TLS protocols
  public void testTLSInvalidProtocolVersion() throws Exception {
    assumeTcp();
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().serverEnabledSecureTransportProtocol(new String[]{"HelloWorld"}).fail();
  }

  @Test
  // Specify some non matching TLS protocols
  public void testTLSNonMatchingProtocolVersions() throws Exception {
    assumeTcp();
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().serverEnabledSecureTransportProtocol(new String[]{"TLSv1.2"}).clientEnabledSecureTransportProtocol(new String[]{"SSLv2Hello", "TLSv1.1"}).fail();
  }

  @Test
  // Test host verification with a CN matching localhost
  public void testTLSVerifyMatchingHost() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientVerifyHost().pass();
  }

  @Test
  // Test host verification with a CN NOT matching localhost
  public void testTLSVerifyNonMatchingHost() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_MIM, Trust.NONE).clientVerifyHost().fail();
  }

  @Test
  // Test host verification with a CN matching localhost
  public void testTLSVerifyMatchingHostOpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientVerifyHost().clientOpenSSL().pass();
  }

  @Test
  // Test host verification with a CN NOT matching localhost
  public void testTLSVerifyNonMatchingHostOpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_MIM, Trust.NONE).clientVerifyHost().clientOpenSSL().fail();
  }

  // OpenSSL tests

  @Test
  // Server uses OpenSSL with JKS
  public void testTLSClientTrustServerCertJKSOpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).serverOpenSSL().pass();
  }

  @Test
  // Server uses OpenSSL with PKCS12
  public void testTLSClientTrustServerCertPKCS12OpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PKCS12, Trust.NONE).serverOpenSSL().pass();
  }

  @Test
  // Server uses OpenSSL with PEM
  public void testTLSClientTrustServerCertPEMOpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PEM, Trust.NONE).serverOpenSSL().pass();
  }

  @Test
  // Client trusts OpenSSL with PEM
  public void testTLSClientTrustServerCertWithJKSOpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientOpenSSL().pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertWithPKCS12OpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.SERVER_PKCS12, Cert.SERVER_JKS, Trust.NONE).clientOpenSSL().pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertWithPEMOpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.SERVER_PEM, Cert.SERVER_JKS, Trust.NONE).clientOpenSSL().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertRequiredOpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).clientOpenSSL().requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertPKCS12RequiredOpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.CLIENT_PKCS12, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).clientOpenSSL().requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertPEMRequiredOpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.CLIENT_PEM, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).clientOpenSSL().requiresClientAuth().pass();
  }

  @Test
  // TLSv1.3
  public void testTLSv1_3() throws Exception {
    Assume.assumeFalse(System.getProperty("java.version").startsWith("1.8"));
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.3"}).pass();
  }

  @Test
  // TLSv1.3 with OpenSSL
  public void testTLSv1_3OpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientOpenSSL()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .serverOpenSSL()
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.3"}).pass();
  }

  @Test
  // Disable TLSv1.3
  public void testDisableTLSv1_3() throws Exception {
    assumeTcp();
    Assume.assumeFalse(System.getProperty("java.version").startsWith("1.8"));
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.2"})
      .fail();
  }

  @Test
  // Disable TLSv1.3 with OpenSSL
  public void testDisableTLSv1_3OpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.2"})
      .serverOpenSSL()
      .fail();
  }

  @Test
  // Disable TLSv1.2
  public void testDisableTLSv1_2() throws Exception {
    assumeTcp();
    Assume.assumeFalse(System.getProperty("java.version").startsWith("1.8"));
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.2"})
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .fail();
  }

  @Test
  // Disable TLSv1.2 with OpenSSL
  public void testDisableTLSv1_2OpenSSL() throws Exception {
    assumeOpenSSL();
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.2"})
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .serverOpenSSL()
      .fail();
  }

  // SNI tests

  @Test
  // Client provides SNI and server responds with a matching certificate for the indicated server name
  public void testSNITrust() throws Exception {
    TLSTest test = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass();
    Assert.assertEquals("host2.com", TestUtils.cnOf(test.clientPeerCert()));
    Assert.assertEquals("host2.com", test.indicatedServerName);
  }

  @Test
  // Client provides SNI and server responds with a matching certificate for the indicated server name
  public void testSNITrustPKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host2.com", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI and server responds with a matching certificate for the indicated server name
  public void testSNITrustPEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host2.com", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI but server ignores it and provides a different cerficate
  public void testSNIServerIgnoresExtension1() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .fail();
  }

  @Test
  // Client provides SNI but server ignores it and provides a different cerficate - check we get a certificate
  public void testSNIServerIgnoresExtension2() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SNI_JKS, Trust.NONE)
        .clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("localhost", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI unknown to the server and server responds with the default certificate (first)
  public void testSNIUnknownServerName1() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("unknown.com")).fail();
  }

  @Test
  // Client provides SNI unknown to the server and server responds with the default certificate (first)
  public void testSNIUnknownServerName2() throws Exception {
    TLSTest test = testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("unknown.com"))
        .pass();
    Assert.assertEquals("localhost", TestUtils.cnOf(test.clientPeerCert()));
    Assert.assertEquals("unknown.com", test.indicatedServerName);
  }

  @Test
  // Client provides SNI matched on the server by a wildcard certificate
  public void testSNIWildcardMatch() throws Exception {
    TLSTest test = testTLS(Cert.NONE, Trust.SNI_JKS_HOST3, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("sub.host3.com"))
        .pass();
    Assert.assertEquals("*.host3.com", TestUtils.cnOf(test.clientPeerCert()));
    Assert.assertEquals("sub.host3.com", test.indicatedServerName);
  }

  @Test
  // Client provides SNI matched on the server by a wildcard certificate
  public void testSNIWildcardMatchPKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST3, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("sub.host3.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("*.host3.com", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI matched on the server by a wildcard certificate
  public void testSNIWildcardMatchPEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST3, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("sub.host3.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("*.host3.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch1() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host4.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch1PKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host4.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch1PEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host4.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch2() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host4.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch2PKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host4.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch2PEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host4.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameWildcardMatch() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host5.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameWildcardMatchPKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host5.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameWildcardMatchPEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host5.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAltenativeNameCNMatch1() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host5.com"))
        .fail()
        .clientPeerCert();
  }

  @Test
  public void testSNISubjectAltenativeNameCNMatch1PKCS12() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host5.com"))
        .fail()
        .clientPeerCert();
  }

  @Test
  public void testSNISubjectAltenativeNameCNMatch1PEM() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host5.com"))
        .fail()
        .clientPeerCert();
  }

  @Test
  public void testSNISubjectAltenativeNameCNMatch2() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host5.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAltenativeNameCNMatch2PKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host5.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAltenativeNameCNMatch2PEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host5.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNIWithALPN() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .clientUsesAlpn()
        .serverUsesAlpn()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host2.com", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI and server responds with a matching certificate for the indicated server name
  public void testSNIWithHostHeader() throws Exception {

    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestProvider(client -> client.request(new RequestOptions()
          .setServer(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST))
          .setMethod(HttpMethod.POST)
          .setPort(DEFAULT_HTTPS_PORT)
          .setHost("host2.com")
          .setURI("/somepath")))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host2.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNIWithOpenSSL() throws Exception {
    assumeOpenSSL();
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .clientOpenSSL()
        .serverOpenSSL()
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    Assert.assertEquals("host2.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNIDontSendServerNameForShortnames1() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST1, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host1"))
        .fail();
  }

  @Test
  public void testSNIDontSendServerNameForShortnames2() throws Exception {
    TLSTest test = testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SNI_JKS, Trust.NONE)
        .clientVerifyHost(false)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host1"))
        .pass();
    Assert.assertEquals(null, test.indicatedServerName);
  }

  @Test
  public void testSNIForceSend() throws Exception {
    assumeTcp();
    TLSTest test = testTLS(Cert.NONE, Trust.SNI_JKS_HOST1, Cert.SNI_JKS, Trust.NONE)
        .clientForceSni()
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host1"))
        .pass();
    Assert.assertEquals("host1", test.indicatedServerName);
  }

  @Test
  public void testSNIWithServerNameTrust() throws Exception {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.SNI_SERVER_ROOT_CA_AND_OTHER_CA_1)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true)
            .setPort(DEFAULT_HTTPS_PORT)
            .setHost("host2.com"))
        .requiresClientAuth()
        .pass();
  }

  @Test
  public void testSNIWithServerNameTrustFallback() throws Exception {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.SNI_SERVER_ROOT_CA_FALLBACK)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true)
            .setPort(DEFAULT_HTTPS_PORT)
            .setHost("host2.com"))
        .requiresClientAuth()
        .pass();
  }

  @Test
  public void testSNIWithServerNameTrustFallbackFail() throws Exception {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.SNI_SERVER_OTHER_CA_FALLBACK)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true)
            .setPort(DEFAULT_HTTPS_PORT)
            .setHost("host2.com"))
        .requiresClientAuth()
        .fail();
  }

  @Test
  public void testSNIWithServerNameTrustFail() throws Exception {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.SNI_SERVER_ROOT_CA_AND_OTHER_CA_2).serverSni()
        .requestOptions(new RequestOptions().setSsl(true)
            .setPort(DEFAULT_HTTPS_PORT)
            .setHost("host2.com"))
        .requiresClientAuth()
        .fail();
  }

  @Test
  public void testSNICustomTrustManagerFactoryMapper() throws Exception {
    testTLS(Cert.CLIENT_PEM, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, () -> new TrustOptions() {
      @Override
      public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) throws Exception {
        return null;
      }
      @Override
      public TrustManagerFactory getTrustManagerFactory(Vertx v) throws Exception {
        return new TrustManagerFactory(new TrustManagerFactorySpi() {
          @Override
          protected void engineInit(KeyStore keyStore) throws KeyStoreException {
          }

          @Override
          protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws
              InvalidAlgorithmParameterException {
          }

          @Override
          protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{TrustAllTrustManager.INSTANCE};
          }
        }, KeyPairGenerator.getInstance("RSA")
            .getProvider(), KeyPairGenerator.getInstance("RSA")
            .getAlgorithm()) {
        };
      }

      @Override
      public TrustOptions copy() {
        return this;
      }
    }).serverSni()
        .requestOptions(new RequestOptions().setSsl(true)
            .setPort(DEFAULT_HTTPS_PORT)
            .setHost("host2.com"))
        .requiresClientAuth()
        .pass();
  }

  @Test
  public void testSNICustomTrustManagerFactoryMapper2() throws Exception {
    testTLS(Cert.CLIENT_PEM, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, () -> new TrustOptions() {

      @Override
      public Function<String, TrustManager[]> trustManagerMapper(Vertx v) throws Exception {
        return (serverName) -> new TrustManager[]{TrustAllTrustManager.INSTANCE};
      }

      @Override
      public TrustManagerFactory getTrustManagerFactory(Vertx v) throws Exception {
        return new TrustManagerFactory(new TrustManagerFactorySpi() {
          @Override
          protected void engineInit(KeyStore keyStore) throws KeyStoreException {
          }

          @Override
          protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws
              InvalidAlgorithmParameterException {
          }

          @Override
          protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{TrustAllTrustManager.INSTANCE};
          }
        }, KeyPairGenerator.getInstance("RSA")
            .getProvider(), KeyPairGenerator.getInstance("RSA")
            .getAlgorithm()) {
        };
      }

      @Override
      public TrustOptions copy() {
        return this;
      }
    }).serverSni()
        .requestOptions(new RequestOptions().setSsl(true)
            .setPort(DEFAULT_HTTPS_PORT)
            .setHost("host2.com"))
        .requiresClientAuth()
        .pass();
  }

  @Test
  // Provide an host name with a trailing dot validated on the server with SNI
  public void testSniWithTrailingDotHost() throws Exception {
    TLSTest test = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
      .serverSni()
      .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com."))
      .pass();
    Assert.assertEquals("host2.com", TestUtils.cnOf(test.clientPeerCert()));
    Assert.assertEquals("host2.com", test.indicatedServerName);
  }

  // Other tests

  @Test
  // Test custom trust manager factory
  public void testCustomTrustManagerFactory() throws Exception {
    testTLS(Cert.NONE, () -> new TrustOptions() {
      @Override
      public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) throws Exception {
        return null;
      }
      @Override
      public TrustManagerFactory getTrustManagerFactory(Vertx v) throws Exception {
        return new TrustManagerFactory(new TrustManagerFactorySpi() {
          @Override
          protected void engineInit(KeyStore keyStore) throws KeyStoreException {
          }
          @Override
          protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws InvalidAlgorithmParameterException {
          }
          @Override
          protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{TrustAllTrustManager.INSTANCE};
          }
        }, KeyPairGenerator.getInstance("RSA").getProvider(), KeyPairGenerator.getInstance("RSA").getAlgorithm()) {
        };
      }
      @Override
      public TrustOptions copy() {
        return this;
      }
    }, Cert.SERVER_JKS, Trust.NONE).pass();
  }

  static class TLSTest {

    final HttpTLSTest test;
    KeyCertOptions clientCert;
    TrustOptions clientTrust;
    boolean clientTrustAll;
    boolean clientUsesCrl;
    boolean clientUsesAlpn;
    boolean clientOpenSSL;
    boolean clientVerifyHost = true;
    boolean clientSSL = true;
    boolean requiresClientAuth;
    KeyCertOptions serverCert;
    TrustOptions serverTrust;
    boolean serverUsesCrl;
    boolean serverOpenSSL;
    Boolean serverUsesAlpn;
    boolean serverSSL = true;
    boolean serverUsesProxyProtocol = false;
    ProxyType proxyType;
    boolean useProxyAuth;
    String[] clientEnabledCipherSuites = new String[0];
    String[] serverEnabledCipherSuites = new String[0];
    String[] clientEnabledSecureTransportProtocol   = new String[0];
    String[] serverEnabledSecureTransportProtocol   = new String[0];
    private String connectHostname;
    private Integer connectPort;
    private boolean followRedirects;
    private boolean serverSNI;
    private boolean clientForceSNI;
    private Function<HttpClient, Future<HttpClientRequest>> requestProvider = client -> {
      String httpHost;
      if (connectHostname != null) {
        httpHost = connectHostname;
      } else {
        httpHost = DEFAULT_HTTPS_HOST;
      }

      int httpPort;
      if(connectPort != null) {
        httpPort = connectPort;
      } else {
        httpPort = DEFAULT_HTTPS_PORT;
      }
      return client.request(new RequestOptions()
        .setMethod(HttpMethod.POST)
        .setHost(httpHost)
        .setPort(httpPort)
        .setURI(DEFAULT_TEST_URI));
    };
    Certificate clientPeerCert;
    String indicatedServerName;

    public TLSTest(HttpTLSTest test, Cert<?> clientCert, Trust<?> clientTrust, Cert<?> serverCert, Trust<?> serverTrust) {
      this.test = test;
      this.clientCert = clientCert.get();
      this.clientTrust = clientTrust.get();
      this.serverCert = serverCert.get();
      this.serverTrust = serverTrust.get();
    }

    TLSTest requiresClientAuth() {
      requiresClientAuth = true;
      return this;
    }

    TLSTest serverUsesCrl() {
      serverUsesCrl = true;
      return this;
    }

    TLSTest serverOpenSSL() {
      serverOpenSSL = true;
      return this;
    }

    TLSTest clientOpenSSL() {
      clientOpenSSL = true;
      return this;
    }

    TLSTest clientUsesCrl() {
      clientUsesCrl = true;
      return this;
    }

    TLSTest clientTrustAll() {
      clientTrustAll = true;
      return this;
    }

    TLSTest clientVerifyHost() {
      clientVerifyHost = true;
      return this;
    }

    TLSTest clientVerifyHost(boolean verify) {
      clientVerifyHost = verify;
      return this;
    }

    TLSTest clientEnabledCipherSuites(String[] value) {
      clientEnabledCipherSuites = value;
      return this;
    }

    TLSTest serverEnabledCipherSuites(String[] value) {
     serverEnabledCipherSuites = value;
     return this;
    }

    TLSTest clientEnabledSecureTransportProtocol(String[] value) {
      clientEnabledSecureTransportProtocol = value;
      return this;
    }

    TLSTest serverEnabledSecureTransportProtocol(String[] value) {
      serverEnabledSecureTransportProtocol = value;
      return this;
    }

    TLSTest serverSni() {
      serverSNI = true;
      return this;
    }

    TLSTest clientForceSni() {
      clientForceSNI = true;
      return this;
    }

    TLSTest clientUsesAlpn() {
      clientUsesAlpn = true;
      return this;
    }

    TLSTest serverUsesAlpn() {
      serverUsesAlpn = true;
      return this;
    }

    TLSTest useProxy(ProxyType type) {
      proxyType = type;
      return this;
    }

    TLSTest useProxyAuth() {
      useProxyAuth = true;
      return this;
    }

    TLSTest serverUsesProxyProtocol() {
      serverUsesProxyProtocol = true;
      return this;
    }

    TLSTest connectHostname(String connectHostname) {
      this.connectHostname = connectHostname;
      return this;
    }

    TLSTest connectPort(int connectPort) {
      this.connectPort = connectPort;
      return this;
    }

    TLSTest requestOptions(RequestOptions requestOptions) {
      this.requestProvider = client -> client.request(requestOptions);
      return this;
    }

    TLSTest requestProvider(Function<HttpClient, Future<HttpClientRequest>> requestProvider) {
      this.requestProvider = requestProvider;
      return this;
    }

    TLSTest clientSSL(boolean ssl) {
      clientSSL = ssl;
      return this;
    }

    TLSTest serverSSL(boolean ssl) {
      serverSSL = ssl;
      return this;
    }

    TLSTest followRedirects(boolean follow) {
      followRedirects = follow;
      return this;
    }

    public Certificate clientPeerCert() {
      return clientPeerCert;
    }

    TLSTest pass() {
      return run(true);
    }

    TLSTest fail() {
      return run(false);
    }

    TLSTest run(boolean shouldPass) {
      HttpClientConfig clientCfg = test.config.forClient();
      clientCfg.setSsl(clientSSL);
      clientCfg.setForceSni(clientForceSNI);
      clientCfg.setVerifyHost(clientVerifyHost);
      // Tmp
      clientCfg.configureSsl(sslOptions -> {
        sslOptions.setTrustAll(clientTrustAll);
        if (clientTrustAll) {
          sslOptions.setTrustAll(true);
        }
        if (clientUsesCrl) {
          sslOptions.addCrlPath("tls/root-ca/crl.pem");
        }
        if (clientUsesAlpn) {
          sslOptions.setUseAlpn(true);
        }
        sslOptions.setTrustOptions(clientTrust);
        sslOptions.setKeyCertOptions(clientCert);
        for (String suite: clientEnabledCipherSuites) {
          sslOptions.addEnabledCipherSuite(suite);
        }
        if(clientEnabledSecureTransportProtocol.length > 0) {
          sslOptions.getEnabledSecureTransportProtocols().forEach(sslOptions::removeEnabledSecureTransportProtocol);
        }
        for (String protocol : clientEnabledSecureTransportProtocol) {
          sslOptions.addEnabledSecureTransportProtocol(protocol);
        }
      });
      if (proxyType != null) {
        ProxyOptions proxyOptions;
        if (proxyType == ProxyType.SOCKS5) {
          proxyOptions = new ProxyOptions().setHost("localhost").setPort(11080).setType(ProxyType.SOCKS5);
        } else {
          proxyOptions = new ProxyOptions().setHost("localhost").setPort(13128).setType(ProxyType.HTTP);
        }
        if (useProxyAuth) {
          proxyOptions.setUsername("username").setPassword("username");
        }
        clientCfg.setProxyOptions(proxyOptions);
      }
      HttpClientBuilder builder = clientCfg.builder(test.vertx);
      if (clientOpenSSL) {
        builder.with(new OpenSSLEngineOptions());
      } else {
        builder.with(new JdkSSLEngineOptions());
      }
      test.client = builder.build();
      HttpServerConfig serverCfg = test.config.forServer();
      serverCfg.setSsl(serverSSL);
      serverCfg.setUseProxyProtocol(serverUsesProxyProtocol);
      if (serverSSL) {
        serverCfg.configureSsl(sslOptions -> {
          sslOptions.setClientAuth(requiresClientAuth ? ClientAuth.REQUIRED : ClientAuth.NONE);
          sslOptions.setTrustOptions(serverTrust);
          sslOptions.setKeyCertOptions(serverCert);
          if (requiresClientAuth) {
            sslOptions.setClientAuth(ClientAuth.REQUIRED);
          }
          if (serverUsesCrl) {
            sslOptions.addCrlPath("tls/root-ca/crl.pem");
          }
          if (serverUsesAlpn == Boolean.TRUE) {
            sslOptions.setUseAlpn(serverUsesAlpn);
          }
          sslOptions.setSni(serverSNI);
          for (String suite: serverEnabledCipherSuites) {
            sslOptions.addEnabledCipherSuite(suite);
          }
          if(serverEnabledSecureTransportProtocol.length > 0) {
            sslOptions.getEnabledSecureTransportProtocols().forEach(sslOptions::removeEnabledSecureTransportProtocol);
          }
          for (String protocol : serverEnabledSecureTransportProtocol) {
            sslOptions.addEnabledSecureTransportProtocol(protocol);
          }
        });
      }
      test.server.close();
      HttpServerBuilder serverBuilder = serverCfg.builder(test.vertx);
      if (serverOpenSSL) {
        serverBuilder = serverBuilder.with(new OpenSSLEngineOptions());
      }
      test.server = serverBuilder.build();
      AtomicInteger connectSuccess = new AtomicInteger();
      AtomicInteger connectFailures = new AtomicInteger();
      test.server.connectionHandler(conn -> connectSuccess.incrementAndGet());
      test.server.exceptionHandler(err -> connectFailures.incrementAndGet());
      test.server.requestHandler(req -> {
        indicatedServerName = req.connection().indicatedServerName();
//        assertEquals(options.getProtocolVersion(), req.version());
        Assert.assertEquals(serverSSL, req.isSSL());
        if (serverSSL && serverOpenSSL) {
          String name = req.sslSession().getSessionContext().getClass().getSimpleName();
          Assert.assertTrue(name.contains("OpenSslServerSessionContext"));
        }
        if (req.method() == HttpMethod.GET || req.method() == HttpMethod.HEAD) {
          req.response().end();
        } else {
          req.bodyHandler(buffer -> {
            Assert.assertEquals("foo", buffer.toString());
            req.response().end("bar");
          });
        }
      });
      test.server.listen().await();

      String httpHost;
      if (connectHostname != null) {
        httpHost = connectHostname;
      } else {
        httpHost = DEFAULT_HTTP_HOST;
      }
      Future<Void> fut = requestProvider.apply(test.client).compose(req -> {
        req.setFollowRedirects(followRedirects);
        return req.send("foo").compose(resp -> {
          HttpConnection conn = resp.request().connection();
          if (conn.isSsl()) {
            try {
              clientPeerCert = conn.peerCertificates().get(0);
            } catch (SSLPeerUnverifiedException ignore) {
            }
            if (clientSSL && clientOpenSSL) {
              String name = req.connection().sslSession().getSessionContext().getClass().getSimpleName();
              Assert.assertTrue(name.contains("OpenSslClientSessionContext"));
            }
          }
          if (shouldPass) {
            resp.version();
            HttpMethod method = resp.request().getMethod();
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
              return resp.end();
            } else {
              return resp.body().map(body -> {
                Assert.assertEquals("bar", body.toString());
                return null;
              });
            }
          } else {
            test.fail("Should not get a response");
            return null;
          }
        });
      });
      try {
        fut.await();
        Assert.assertTrue(shouldPass);
        Assert.assertTrue(connectSuccess.get() > 0);
        Assert.assertEquals(0, connectFailures.get());
      } catch (Exception err) {
        Assert.assertFalse("Should not fail " + err.getMessage(), shouldPass);
        Assert.assertEquals(0, connectSuccess.get());
      }
      return this;
    }
  }

  protected TLSTest testTLS(Cert<?> clientCert, Trust<?> clientTrust,
                          Cert<?> serverCert, Trust<?> serverTrust) throws Exception {
    return new TLSTest(this, clientCert, clientTrust, serverCert, serverTrust);
  }


  /**
   * Test that for HttpServer, the peer host and port info is available in the SSLEngine
   * when the X509ExtendedKeyManager.chooseEngineServerAlias is called.
   *
   * @throws Exception if an error occurs
   */
  @Test
  public void testTLSServerSSLEnginePeerHost() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    testTLS(Cert.NONE, Trust.SERVER_JKS, testPeerHostServerCert(Cert.SERVER_JKS, called), Trust.NONE).pass();
    Assert.assertTrue("X509ExtendedKeyManager.chooseEngineServerAlias is not called", called.get());
  }

  /**
   * Test that for HttpServer with SNI, the peer host and port info is available in the SSLEngine
   * when the X509ExtendedKeyManager.chooseEngineServerAlias is called.
   *
   * @throws Exception if an error occurs
   */
  @Test
  public void testSNIServerSSLEnginePeerHost() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    TLSTest test = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, testPeerHostServerCert(Cert.SNI_JKS, called), Trust.NONE)
      .serverSni()
      .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
      .pass();
    Assert.assertEquals("host2.com", TestUtils.cnOf(test.clientPeerCert()));
    Assert.assertEquals("host2.com", test.indicatedServerName);
    Assert.assertTrue("X509ExtendedKeyManager.chooseEngineServerAlias is not called", called.get());
  }

  /**
   * Create a {@link Cert} that will verify the peer host is not null and port is not -1 in the {@link SSLEngine}
   * when the {@link X509ExtendedKeyManager#chooseEngineServerAlias(String, Principal[], SSLEngine)}
   * is called.
   *
   * @param delegate The delegated Cert
   * @param chooseEngineServerAliasCalled Will be set to true when the
   * X509ExtendedKeyManager.chooseEngineServerAlias is called
   * @return The {@link Cert}
   */
  public static Cert<KeyCertOptions> testPeerHostServerCert(Cert<? extends KeyCertOptions> delegate, AtomicBoolean chooseEngineServerAliasCalled) {
    return testPeerHostServerCert(delegate, (peerHost, peerPort) -> {
      chooseEngineServerAliasCalled.set(true);
      if (peerHost == null || peerPort == -1) {
        throw new RuntimeException("Missing peer host/port");
      }
    });
  }

  /**
   * Create a {@link Cert} that will verify the peer host and port in the {@link SSLEngine}
   * when the {@link X509ExtendedKeyManager#chooseEngineServerAlias(String, Principal[], SSLEngine)}
   * is called.
   *
   * @param delegate The delegated Cert
   * @param peerHostVerifier The consumer to verify the peer host and port when the
   * X509ExtendedKeyManager.chooseEngineServerAlias is called
   * @return The {@link Cert}
   */
  public static Cert<KeyCertOptions> testPeerHostServerCert(Cert<? extends KeyCertOptions> delegate, BiConsumer<String, Integer> peerHostVerifier) {
    return () -> new VerifyServerPeerHostKeyCertOptions(delegate.get(), peerHostVerifier);
  }

  private static class VerifyServerPeerHostKeyCertOptions implements KeyCertOptions {
    private final KeyCertOptions delegate;
    private final BiConsumer<String, Integer> peerHostVerifier;

    VerifyServerPeerHostKeyCertOptions(KeyCertOptions delegate, BiConsumer<String, Integer> peerHostVerifier) {
      this.delegate = delegate;
      this.peerHostVerifier = peerHostVerifier;
    }

    @Override
    public KeyCertOptions copy() {
      return new VerifyServerPeerHostKeyCertOptions(delegate.copy(), peerHostVerifier);
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory(Vertx vertx) throws Exception {
      return new VerifyServerPeerHostKeyManagerFactory(delegate.getKeyManagerFactory(vertx), peerHostVerifier);
    }

    @Override
    public Function<String, KeyManagerFactory> keyManagerFactoryMapper(Vertx vertx) throws Exception {
      Function<String, KeyManagerFactory> mapper = delegate.keyManagerFactoryMapper(vertx);
      return serverName -> new VerifyServerPeerHostKeyManagerFactory(mapper.apply(serverName), peerHostVerifier);
    }
  }

  private static class VerifyServerPeerHostKeyManagerFactory extends KeyManagerFactory {
    VerifyServerPeerHostKeyManagerFactory(KeyManagerFactory delegate, BiConsumer<String, Integer> peerHostVerifier) {
      super(new KeyManagerFactorySpiWrapper(delegate, peerHostVerifier), delegate.getProvider(), delegate.getAlgorithm());
    }

    private static class KeyManagerFactorySpiWrapper extends KeyManagerFactorySpi {
      private final KeyManagerFactory delegate;
      private final BiConsumer<String, Integer> peerHostVerifier;

      KeyManagerFactorySpiWrapper(KeyManagerFactory delegate, BiConsumer<String, Integer> peerHostVerifier) {
        super();
        this.delegate = delegate;
        this.peerHostVerifier = peerHostVerifier;
      }

      @Override
      protected void engineInit(KeyStore keyStore, char[] chars) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        delegate.init(keyStore, chars);
      }

      @Override
      protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws InvalidAlgorithmParameterException {
        delegate.init(managerFactoryParameters);
      }

      @Override
      protected KeyManager[] engineGetKeyManagers() {
        KeyManager[] keyManagers = delegate.getKeyManagers().clone();
        for (int i = 0; i < keyManagers.length; ++i) {
          KeyManager km = keyManagers[i];
          if (km instanceof X509KeyManager) {
            keyManagers[i] = new VerifyServerPeerHostKeyManager((X509KeyManager) km, peerHostVerifier);
          }
        }

        return keyManagers;
      }
    }
  }

  private static class VerifyServerPeerHostKeyManager extends X509ExtendedKeyManager {
    private final X509KeyManager delegate;
    private final BiConsumer<String, Integer> peerHostVerifier;

    VerifyServerPeerHostKeyManager(X509KeyManager delegate, BiConsumer<String, Integer> peerHostVerifier) {
      this.delegate = delegate;
      this.peerHostVerifier = peerHostVerifier;
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
      if (delegate instanceof X509ExtendedKeyManager) {
        return ((X509ExtendedKeyManager) delegate).chooseEngineClientAlias(keyType, issuers, engine);
      } else {
        return delegate.chooseClientAlias(keyType, issuers, null);
      }
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
      peerHostVerifier.accept(engine.getPeerHost(), engine.getPeerPort());
      if (delegate instanceof X509ExtendedKeyManager) {
        return ((X509ExtendedKeyManager) delegate).chooseEngineServerAlias(keyType, issuers, engine);
      } else {
        return delegate.chooseServerAlias(keyType, issuers, null);
      }
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
      return delegate.chooseClientAlias(keyType, issuers, socket);
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
      return delegate.chooseServerAlias(keyType, issuers, socket);
    }

    @Override
    public String[] getClientAliases(String s, Principal[] principals) {
      return delegate.getClientAliases(s, principals);
    }

    @Override
    public String[] getServerAliases(String s, Principal[] principals) {
      return delegate.getServerAliases(s, principals);
    }

    @Override
    public X509Certificate[] getCertificateChain(String s) {
      return delegate.getCertificateChain(s);
    }

    @Override
    public PrivateKey getPrivateKey(String s) {
      return delegate.getPrivateKey(s);
    }
  }

  @Test
  public void testUpdateSSLOptions() throws Exception {
    testUpdateSSLOptions(idx -> {
      switch (idx) {
        case 0:
          return Cert.SERVER_JKS.get();
        case 1:
          return Cert.SERVER_JKS_ROOT_CA.get();
      }
      return null;
    }, idx -> {
      switch (idx) {
        case 0:
          return Trust.SERVER_JKS.get();
        case 1:
          return Trust.SERVER_JKS_ROOT_CA.get();
      }
      return null;
    }, false, true);
  }

  @Test
  public void testUpdateSSLOptionsSamePath() throws Exception {
    testUpdateSSLOptionsSamePath(false);
  }

  @Test
  public void testUpdateSSLOptionsSamePathAndForce() throws Exception {
    testUpdateSSLOptionsSamePath(true);
  }

  private void testUpdateSSLOptionsSamePath(boolean force) throws Exception {
    Path cert = Files.createTempFile("vertx", ".jks").toAbsolutePath();
    Buffer serverJks = vertx.fileSystem().readFileBlocking(Cert.SERVER_JKS.get().getPath());
    Buffer serverJksRootCA = vertx.fileSystem().readFileBlocking(Cert.SERVER_JKS_ROOT_CA.get().getPath());
    testUpdateSSLOptions(idx -> {
      try {
        switch (idx) {
          case 0:
            Files.write(cert, serverJks.getBytes());
            return Cert.SERVER_JKS.get().copy().setPath(cert.toFile().getAbsolutePath());
          case 1:
            Files.write(cert, serverJksRootCA.getBytes());
            return Cert.SERVER_JKS.get().copy().setPath(cert.toFile().getAbsolutePath());
        }
      } catch (IOException e) {
        Assert.fail(e.getMessage());
      }
      return null;
    }, idx -> {
      switch (idx) {
        case 0:
          return Trust.SERVER_JKS.get();
        case 1:
          return Trust.SERVER_JKS_ROOT_CA.get();
      }
      return null;
    }, force, force);
  }

  private void testUpdateSSLOptions(Function<Integer, JksOptions> certProvider, Function<Integer, JksOptions> trustProvider,
                                    boolean force, boolean updateTrust) throws Exception {
    server = config
      .forServer()
      .setSsl(true)
      .configureSsl(sslOptions -> sslOptions.setKeyCertOptions(certProvider.apply(0)))
      .create(vertx)
      .requestHandler(req -> {
        req.response().end("Hello World");
      });
    startServer(testAddress);
    Function<HttpClient, Future<Buffer>> request = client -> client.request(requestOptions).compose(req -> req.send().compose(HttpClientResponse::body));
    HttpClientConfig clientCfg = config
      .forClient()
      .setSsl(true)
      .setVerifyHost(false)
      .configureSsl(sslOptions -> sslOptions
        .setTrustOptions(trustProvider.apply(0))
      );
    HttpClientAgent client1 = clientCfg.create(vertx);
    HttpClientAgent client2 = clientCfg.create(vertx);
    request.apply(client1).onComplete(TestUtils.onSuccess(body1 -> {
      Assert.assertEquals("Hello World", body1.toString());
      ServerSSLOptions certUpdate = new ServerSSLOptions().setKeyCertOptions(certProvider.apply(1));
      server.updateSSLOptions(certUpdate, force).onComplete(TestUtils.onSuccess(updateOccurred -> {
        request.apply(client2).onComplete(ar -> {
          Assert.assertEquals(!updateTrust, ar.succeeded());
          if (updateTrust) {
            Assert.assertTrue(updateOccurred);
            ClientSSLOptions trustUpdate = new ClientSSLOptions().setTrustOptions(trustProvider.apply(1));
            client2.updateSSLOptions(trustUpdate, force)
              .onComplete(TestUtils.onSuccess(v2 -> {
              request.apply(client2).onComplete(TestUtils.onSuccess(body2 -> {
                Assert.assertEquals("Hello World", body2.toString());
                testComplete();
              }));
            }));
          } else {
            // Same trust options since update did not occur
            Assert.assertFalse(updateOccurred);
            testComplete();
          }
        });
      }));
    }));
    await();
  }

  @Test
  public void testUpdateWithInvalidSSLOptions() throws Exception {
    server = config
      .forServer()
      .setSsl(true)
      .configureSsl(sslOptions -> sslOptions.setKeyCertOptions(Cert.SERVER_JKS.get()))
      .create(vertx)
      .requestHandler(req -> {
        req.response().end("Hello World");
      });
    startServer(testAddress);
    client = config
      .forClient()
      .setSsl(true)
      .setVerifyHost(false)
      .configureSsl(sslOptions -> sslOptions.setTrustOptions(Trust.SERVER_JKS.get()))
      .create(vertx);
    ServerSSLOptions certUpdate = new ServerSSLOptions()
      .setKeyCertOptions(new JksOptions()
        .setValue(TestUtils.randomBuffer(20))
        .setPassword("invalid"));
    Future<Boolean> last = server.updateSSLOptions(certUpdate);
    last.onComplete(TestUtils.onFailure(err -> {
      client
        .request(requestOptions)
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(TestUtils.onSuccess(body -> {
          Assert.assertEquals("Hello World", body.toString());
          testComplete();
        }));
    }));
    await();
  }

  @Test
  public void testConcurrentUpdateSSLOptions() throws Exception {
    server = config
      .forServer()
      .setSsl(true)
      .configureSsl(sslOptions -> sslOptions.setKeyCertOptions(Cert.SERVER_JKS.get()))
      .create(vertx)
      .requestHandler(req -> {
        req.response().end("Hello World");
      });
    startServer(testAddress);
    client = config
      .forClient()
      .setSsl(true)
      .setVerifyHost(false)
      .configureSsl(sslOptions -> sslOptions.setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get()))
      .create(vertx);
    List<KeyCertOptions> list = Arrays.asList(
      Cert.SERVER_PKCS12.get(),
      Cert.SERVER_PEM.get(),
      Cert.SERVER_PEM.get(),
      Cert.SERVER_JKS_ROOT_CA.get()
    );
    AtomicInteger seq = new AtomicInteger();
    Future<Boolean> last = null;
    for (int i = 0;i < list.size();i++) {
      int val = i;
      last = server.updateSSLOptions(new ServerSSLOptions().setKeyCertOptions(list.get(i)));
      last.onComplete(TestUtils.onSuccess(v -> {
        Assert.assertEquals(val, seq.getAndIncrement());
      }));
    }
    last.onComplete(TestUtils.onSuccess(v -> {
      client
        .request(requestOptions)
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(TestUtils.onSuccess(body -> {
          Assert.assertEquals("Hello World", body.toString());
          testComplete();
        }));
    }));
    await();
  }

  @Test
  public void testServerSharingUpdateSSLOptions() throws Exception {
    int num = 4;
    HttpServer[] servers = new HttpServer[num];
    for (int i = 0;i < num;i++) {
      String msg = "Hello World " + i;
      servers[i] = config
        .forServer()
        .setSsl(true)
        .configureSsl(sslOptions -> sslOptions.setKeyCertOptions(Cert.SERVER_JKS.get()))
        .create(vertx)
        .requestHandler(req -> {
          req.response().end(msg);
        });
      servers[i].listen(testAddress).await();
    }
    HttpClient[] clients = new HttpClient[num];
    for (int i = 0;i < num;i++) {
      clients[i] = config
        .forClient()
        .setSsl(true)
        .setVerifyHost(false)
        .configureSsl(sslOptions -> sslOptions.setTrustOptions(Trust.SERVER_JKS.get()))
        .create(vertx);
    }
    for (int i = 0;i < num;i++) {
      Buffer body = clients[i].request(requestOptions).compose(req -> req.send().compose(HttpClientResponse::body)).await();
      Assert.assertEquals("Hello World " + i, body.toString());
    }
    for (int i = 0;i < num;i++) {
      servers[i].updateSSLOptions(new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_PKCS12.get())).await();
    }
    for (int i = 0;i < num;i++) {
      clients[i].close().await();
      clients[i] = config
        .forClient()
        .setSsl(true)
        .setVerifyHost(false)
        .configureSsl(sslOptions -> sslOptions.setTrustOptions(Trust.SERVER_JKS.get()))
        .create(vertx);
    }
    for (int i = 0;i < num;i++) {
      Buffer body = clients[i].request(requestOptions).compose(req -> req.send().compose(HttpClientResponse::body)).await();
      Assert.assertEquals("Hello World " + i, body.toString());
    }
  }
}
