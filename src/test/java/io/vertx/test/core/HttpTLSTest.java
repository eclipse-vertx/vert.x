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

import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.TrustAllTrustManager;
import io.vertx.test.core.tls.Cert;
import io.vertx.test.core.tls.Trust;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.security.cert.X509Certificate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpTLSTest extends HttpTestBase {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
        "127.0.0.1 localhost\n" +
        "127.0.0.1 host1\n" +
        "127.0.0.1 host2\n" +
        "127.0.0.1 sub.host3\n" +
        "127.0.0.1 host4\n" +
        "127.0.0.1 www.host4\n" +
        "127.0.0.1 host5\n" +
        "127.0.0.1 www.host5\n" +
        "127.0.0.1 unknown"));
    return options;
  }

  @Override
  protected void tearDown() throws Exception {
    if (proxy != null) {
      proxy.stop();
    }
    super.tearDown();
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
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).requiresClientAuth().fail();
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
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().serverEnabledSecureTransportProtocol(new String[]{"SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2"}).pass();
  }

  @Test
  // Specify some matching TLS protocols
  public void testTLSInvalidProtocolVersion() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().serverEnabledSecureTransportProtocol(new String[]{"HelloWorld"}).fail();
  }

  @Test
  // Specify some non matching TLS protocols
  public void testTLSNonMatchingProtocolVersions() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().serverEnabledSecureTransportProtocol(new String[]{"TLSv1.2"}).clientEnabledSecureTransportProtocol(new String[]{"SSLv2Hello"}).fail();
  }

  @Test
  // Test host verification with a CN matching localhost
  public void testTLSVerifyMatchingHost() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().clientVerifyHost().pass();
  }

  @Test
  // Test host verification with a CN NOT matching localhost
  public void testTLSVerifyNonMatchingHost() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_MIM, Trust.NONE).clientTrustAll().clientVerifyHost().fail();
  }

  // OpenSSL tests

  @Test
  // Server uses OpenSSL with JKS
  public void testTLSClientTrustServerCertJKSOpenSSL() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).serverOpenSSL().pass();
  }

  @Test
  // Server uses OpenSSL with PKCS12
  public void testTLSClientTrustServerCertPKCS12OpenSSL() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PKCS12, Trust.NONE).serverOpenSSL().pass();
  }

  @Test
  // Server uses OpenSSL with PEM
  public void testTLSClientTrustServerCertPEMOpenSSL() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PEM, Trust.NONE).serverOpenSSL().pass();
  }

  @Test
  // Client trusts OpenSSL with PEM
  public void testTLSClientTrustServerCertWithJKSOpenSSL() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientOpenSSL().pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertWithPKCS12OpenSSL() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PKCS12, Cert.SERVER_JKS, Trust.NONE).clientOpenSSL().pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertWithPEMOpenSSL() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM, Cert.SERVER_JKS, Trust.NONE).clientOpenSSL().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertRequiredOpenSSL() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).clientOpenSSL().requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertPKCS12RequiredOpenSSL() throws Exception {
    testTLS(Cert.CLIENT_PKCS12, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).clientOpenSSL().requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertPEMRequiredOpenSSL() throws Exception {
    testTLS(Cert.CLIENT_PEM, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS).clientOpenSSL().requiresClientAuth().pass();
  }

  @Test
  // Test custom trust manager factory
  public void testCustomTrustManagerFactory() throws Exception {
    testTLS(Cert.NONE, () -> new TrustOptions() {
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
      public TrustOptions clone() {
        return this;
      }
    }, Cert.SERVER_JKS, Trust.NONE).pass();
  }

  @Test
  // Client provides SNI but server ignores it and provides a different cerficate
  public void testTLSClientIndicatesServerNameIgnoredAndDoesNotTrustServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST1, Cert.SERVER_JKS, Trust.NONE)
        .requestOptions(new RequestOptions().setSsl(true).setServerName("host1").setPort(4043).setHost("host1")).fail();
  }

  @Test
  // Client provides SNI but server ignores it and provides a different cerficate - check we get a certificate
  public void testTLSClientIndicatesServerNameIgnoredAndTrustsServerCert() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setServerName("host1").setPort(4043).setHost("host1"))
        .pass()
        .clientPeerCert();
    assertEquals("localhost", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI and server responds with a matching certificate for the indicated server name
  public void testTLSClientIndicatesServerNameAndTrustsServerCert1() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST1, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .requestOptions(new RequestOptions().setSsl(true).setServerName("host1").setPort(4043).setHost("host1"))
        .pass()
        .clientPeerCert();
    assertEquals("host1", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI and server responds with a matching certificate for the indicated server name
  public void testTLSClientIndicatesServerNameAndTrustsServerCert2() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .requestOptions(new RequestOptions().setSsl(true).setServerName("host2").setPort(4043).setHost("host2"))
        .pass()
        .clientPeerCert();
    assertEquals("host2", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI unknown to the server and server responds with the default certificate (first)
  public void testTLSClientIndicatesServerNameUnknownToServerAndDoesNotTrustServerDefaultCert() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST1, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .requestOptions(new RequestOptions().setSsl(true).setServerName("unknown").setPort(4043).setHost("unknown")).fail();
  }

  @Test
  // Client provides SNI unknown to the server and server responds with the default certificate (first)
  public void testTLSClientIndicatesServerNameUnknownToServerAndTrustsServerDefaultCert() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setServerName("unknown").setPort(4043).setHost("unknown"))
        .pass()
        .clientPeerCert();
    assertEquals("localhost", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI matched on the server by a wildcard certificate
  public void testTLSClientIndicatesServerNameMatchedByWildcardCertificate() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST3, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .requestOptions(new RequestOptions().setSsl(true).setServerName("sub.host3").setPort(4043).setHost("sub.host3"))
        .pass()
        .clientPeerCert();
    assertEquals("*.host3", TestUtils.cnOf(cert));
  }

  @Test
  public void testTLSClientIndicatesServerNameMatchesByCertificateSAN1() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .requestOptions(new RequestOptions().setSsl(true).setServerName("host4").setPort(4043).setHost("host4"))
        .pass()
        .clientPeerCert();
    assertEquals("host4 certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testTLSClientIndicatesServerNameMatchesByCertificateSAN2() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .requestOptions(new RequestOptions().setSsl(true).setServerName("www.host4").setPort(4043).setHost("www.host4"))
        .pass()
        .clientPeerCert();
    assertEquals("host4 certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testTLSClientIndicatesServerNameMatchesByWildcardCertificateSAN() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .requestOptions(new RequestOptions().setSsl(true).setServerName("www.host5").setPort(4043).setHost("www.host5"))
        .pass()
        .clientPeerCert();
    assertEquals("host5", TestUtils.cnOf(cert));
  }

  @Test
  public void testTLSClientIndicatesServerNameMatchesByCNWithCertificateSAN1() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .requestOptions(new RequestOptions().setSsl(true).setServerName("host5").setPort(4043).setHost("host5"))
        .fail()
        .clientPeerCert();
  }

  @Test
  public void testTLSClientIndicatesServerNameMatchesByCNWithCertificateSAN2() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setServerName("host5").setPort(4043).setHost("host5"))
        .pass()
        .clientPeerCert();
    assertEquals("host5", TestUtils.cnOf(cert));
  }

  @Test
  public void testTLSClientIndicatesServerNameWithALPN() throws Exception {
    X509Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST1, Cert.SNI_JKS, Trust.NONE)
        .serverUsesSni()
        .clientUsesAlpn()
        .serverUsesAlpn()
        .requestOptions(new RequestOptions().setSsl(true).setServerName("host1").setPort(4043).setHost("host1"))
        .pass()
        .clientPeerCert();
    assertEquals("host1", TestUtils.cnOf(cert));
  }

  class TLSTest {

    HttpVersion version;
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
    boolean serverUsesAlpn;
    boolean serverSSL = true;
    boolean useProxy;
    boolean useProxyAuth;
    boolean useSocksProxy;
    String[] clientEnabledCipherSuites = new String[0];
    String[] serverEnabledCipherSuites = new String[0];
    String[] clientEnabledSecureTransportProtocol   = new String[0];
    String[] serverEnabledSecureTransportProtocol   = new String[0];
    private String connectHostname;
    private boolean followRedirects;
    private boolean serverUsesSni;
    private Function<HttpClient, HttpClientRequest> requestProvider = client -> {
      String httpHost;
      if (connectHostname != null) {
        httpHost = connectHostname;
      } else {
        httpHost = DEFAULT_HTTP_HOST;
      }
      return client.request(HttpMethod.POST, 4043, httpHost, DEFAULT_TEST_URI);
    };
    X509Certificate clientPeerCert;

    public TLSTest(Cert<?> clientCert, Trust<?> clientTrust, Cert<?> serverCert, Trust<?> serverTrust) {
      this.version = HttpVersion.HTTP_1_1;
      this.clientCert = clientCert.get();
      this.clientTrust = clientTrust.get();
      this.serverCert = serverCert.get();
      this.serverTrust = serverTrust.get();
    }

    TLSTest version(HttpVersion version) {
      this.version = version;
      return this;
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

    TLSTest serverUsesSni() {
      serverUsesSni = true;
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

    TLSTest useProxy() {
      useProxy = true;
      return this;
    }

    TLSTest useProxyAuth() {
      useProxyAuth = true;
      return this;
    }

    TLSTest useSocksProxy() {
      useSocksProxy = true;
      return this;
    }

    TLSTest connectHostname(String connectHostname) {
      this.connectHostname = connectHostname;
      return this;
    }

    TLSTest requestOptions(RequestOptions requestOptions) {
      this.requestProvider = client -> client.request(HttpMethod.POST, requestOptions);
      return this;
    }

    TLSTest requestProvider(Function<HttpClient, HttpClientRequest> requestProvider) {
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

    public X509Certificate clientPeerCert() {
      return clientPeerCert;
    }

    TLSTest pass() {
      return run(true);
    }

    TLSTest fail() {
      return run(false);
    }

    TLSTest run(boolean shouldPass) {
      server.close();
      HttpClientOptions options = new HttpClientOptions();
      options.setProtocolVersion(version);
      options.setSsl(clientSSL);
      if (clientTrustAll) {
        options.setTrustAll(true);
      }
      if (clientUsesCrl) {
        options.addCrlPath("tls/root-ca/crl.pem");
      }
      if (clientOpenSSL) {
        options.setOpenSslEngineOptions(new OpenSSLEngineOptions());
      }
      if (clientUsesAlpn) {
        options.setUseAlpn(true);
      }
      options.setVerifyHost(clientVerifyHost);
      options.setTrustOptions(clientTrust);
      options.setKeyCertOptions(clientCert);
      for (String suite: clientEnabledCipherSuites) {
        options.addEnabledCipherSuite(suite);
      }
      for (String protocols: clientEnabledSecureTransportProtocol) {
        options.addEnabledSecureTransportProtocol(protocols);
      }
      if (useProxy) {
        ProxyOptions proxyOptions;
        if (useSocksProxy) {
          proxyOptions = new ProxyOptions().setHost("localhost").setPort(11080).setType(ProxyType.SOCKS5);
        } else {
          proxyOptions = new ProxyOptions().setHost("localhost").setPort(13128).setType(ProxyType.HTTP);
        }
        if (useProxyAuth) {
          proxyOptions.setUsername("username").setPassword("username");
        }
        options.setProxyOptions(proxyOptions);
      }
      client = createHttpClient(options);
      HttpServerOptions serverOptions = new HttpServerOptions();
      serverOptions.setTrustOptions(serverTrust);
      serverOptions.setKeyCertOptions(serverCert);
      if (requiresClientAuth) {
        serverOptions.setClientAuth(ClientAuth.REQUIRED);
      }
      if (serverUsesCrl) {
        serverOptions.addCrlPath("tls/root-ca/crl.pem");
      }
      if (serverOpenSSL) {
        serverOptions.setOpenSslEngineOptions(new OpenSSLEngineOptions());
      }
      serverOptions.setUseAlpn(serverUsesAlpn);
      serverOptions.setSsl(serverSSL);
      serverOptions.setSni(serverUsesSni);
      for (String suite: serverEnabledCipherSuites) {
        serverOptions.addEnabledCipherSuite(suite);
      }
      for (String protocols: serverEnabledSecureTransportProtocol) {
        serverOptions.addEnabledSecureTransportProtocol(protocols);
      }
      server = createHttpServer(serverOptions.setPort(4043));
      server.requestHandler(req -> {
        assertEquals(version, req.version());
        req.bodyHandler(buffer -> {
          assertEquals(serverSSL, req.isSSL());
          assertEquals("foo", buffer.toString());
          req.response().end("bar");
        });
      });
      server.listen(ar -> {
        assertTrue(ar.succeeded());
        String httpHost;
        if (connectHostname != null) {
          httpHost = connectHostname;
        } else {
          httpHost = DEFAULT_HTTP_HOST;
        }
        HttpClientRequest req = requestProvider.apply(client);
        req.setFollowRedirects(followRedirects);
        req.handler(response -> {
          HttpConnection conn = response.request().connection();
          if (conn.isSsl()) {
            try {
              clientPeerCert = conn.peerCertificateChain()[0];
            } catch (SSLPeerUnverifiedException ignore) {
            }
          }
          if (shouldPass) {
            response.version();
            response.bodyHandler(data -> assertEquals("bar", data.toString()));
            testComplete();
          } else {
            HttpTLSTest.this.fail("Should not get a response");
          }
        });
        req.exceptionHandler(t -> {
          if (shouldPass) {
            t.printStackTrace();
            HttpTLSTest.this.fail("Should not throw exception");
          } else {
            testComplete();
          }
        });
        req.end("foo");
      });
      await();
      return this;
    }
  }

  abstract HttpServer createHttpServer(HttpServerOptions options);

  abstract HttpClient createHttpClient(HttpClientOptions options);

  protected TLSTest testTLS(Cert<?> clientCert, Trust<?> clientTrust,
                          Cert<?> serverCert, Trust<?> serverTrust) throws Exception {
    return new TLSTest(clientCert, clientTrust, serverCert, serverTrust);
  }

  @Test
  public void testJKSInvalidPath() {
    testInvalidKeyStore(Cert.SERVER_JKS.get().setPath("/invalid.jks"), "java.nio.file.NoSuchFileException: ", "invalid.jks");
  }

  @Test
  public void testJKSMissingPassword() {
    testInvalidKeyStore(Cert.SERVER_JKS.get().setPassword(null), "Password must not be null", null);
  }

  @Test
  public void testJKSInvalidPassword() {
    testInvalidKeyStore(Cert.SERVER_JKS.get().setPassword("wrongpassword"), "Keystore was tampered with, or password was incorrect", null);
  }

  @Test
  public void testPKCS12InvalidPath() {
    testInvalidKeyStore(Cert.SERVER_PKCS12.get().setPath("/invalid.p12"), "java.nio.file.NoSuchFileException: ", "invalid.p12");
  }

  @Test
  public void testPKCS12MissingPassword() {
    testInvalidKeyStore(Cert.SERVER_PKCS12.get().setPassword(null), "Get Key failed: null", null);
  }

  @Test
  public void testPKCS12InvalidPassword() {
    testInvalidKeyStore(Cert.SERVER_PKCS12.get().setPassword("wrongpassword"), Arrays.asList(
        "failed to decrypt safe contents entry: javax.crypto.BadPaddingException: Given final block not properly padded",
        "keystore password was incorrect"), null);
  }

  @Test
  public void testKeyCertMissingKeyPath() {
    testInvalidKeyStore(Cert.SERVER_PEM.get().setKeyPath(null), "Missing private key", null);
  }

  @Test
  public void testKeyCertInvalidKeyPath() {
    testInvalidKeyStore(Cert.SERVER_PEM.get().setKeyPath("/invalid.pem"), "java.nio.file.NoSuchFileException: ", "invalid.pem");
  }

  @Test
  public void testKeyCertMissingCertPath() {
    testInvalidKeyStore(Cert.SERVER_PEM.get().setCertPath(null), "Missing X.509 certificate", null);
  }

  @Test
  public void testKeyCertInvalidCertPath() {
    testInvalidKeyStore(Cert.SERVER_PEM.get().setCertPath("/invalid.pem"), "java.nio.file.NoSuchFileException: ", "invalid.pem");
  }

  @Test
  public void testKeyCertInvalidPem() throws IOException {
    String[] contents = {
        "",
        "-----BEGIN PRIVATE KEY-----",
        "-----BEGIN PRIVATE KEY-----\n-----END PRIVATE KEY-----",
        "-----BEGIN PRIVATE KEY-----\n*\n-----END PRIVATE KEY-----"
    };
    String[] messages = {
        "Missing -----BEGIN PRIVATE KEY----- delimiter",
        "Missing -----END PRIVATE KEY----- delimiter",
        "Empty pem file",
        "Input byte[] should at least have 2 bytes for base64 bytes"
    };
    for (int i = 0;i < contents.length;i++) {
      Path file = testFolder.newFile("vertx" + UUID.randomUUID().toString() + ".pem").toPath();
      Files.write(file, Collections.singleton(contents[i]));
      String expectedMessage = messages[i];
      testInvalidKeyStore(Cert.SERVER_PEM.get().setKeyPath(file.toString()), expectedMessage, null);
    }
  }

  @Test
  public void testNoKeyCert() {
    testInvalidKeyStore(null, "Key/certificate is mandatory for SSL", null);
  }

  @Test
  public void testCaInvalidPath() {
    testInvalidTrustStore(new PemTrustOptions().addCertPath("/invalid.pem"), "java.nio.file.NoSuchFileException: ", "invalid.pem");
  }

  @Test
  public void testCaInvalidPem() throws IOException {
    String[] contents = {
        "",
        "-----BEGIN CERTIFICATE-----",
        "-----BEGIN CERTIFICATE-----\n-----END CERTIFICATE-----",
        "-----BEGIN CERTIFICATE-----\n*\n-----END CERTIFICATE-----"
    };
    String[] messages = {
        "Missing -----BEGIN CERTIFICATE----- delimiter",
        "Missing -----END CERTIFICATE----- delimiter",
        "Empty pem file",
        "Input byte[] should at least have 2 bytes for base64 bytes"
    };
    for (int i = 0;i < contents.length;i++) {
      Path file = testFolder.newFile("vertx" + UUID.randomUUID().toString() + ".pem").toPath();
      Files.write(file, Collections.singleton(contents[i]));
      String expectedMessage = messages[i];
      testInvalidTrustStore(new PemTrustOptions().addCertPath(file.toString()), expectedMessage, null);
    }
  }

  private void testInvalidKeyStore(KeyCertOptions options, String expectedPrefix, String expectedSuffix) {
    HttpServerOptions serverOptions = new HttpServerOptions();
    setOptions(serverOptions, options);
    testStore(serverOptions, Collections.singletonList(expectedPrefix), expectedSuffix);
  }

  private void testInvalidKeyStore(KeyCertOptions options, List<String> expectedPossiblePrefixes, String expectedSuffix) {
    HttpServerOptions serverOptions = new HttpServerOptions();
    setOptions(serverOptions, options);
    testStore(serverOptions, expectedPossiblePrefixes, expectedSuffix);
  }

  private void testInvalidTrustStore(TrustOptions options, String expectedPrefix, String expectedSuffix) {
    HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions.setTrustOptions(options);
    testStore(serverOptions, Collections.singletonList(expectedPrefix), expectedSuffix);
  }

  private void testStore(HttpServerOptions serverOptions, List<String> expectedPossiblePrefixes, String expectedSuffix) {
    serverOptions.setSsl(true);
    serverOptions.setPort(4043);
    HttpServer server = vertx.createHttpServer(serverOptions);
    server.requestHandler(req -> {
    });
    try {
      server.listen();
      fail("Was expecting a failure");
    } catch (VertxException e) {
      Throwable cause = e.getCause();
      if(expectedSuffix == null) {
        boolean ok = expectedPossiblePrefixes.isEmpty();
        for (String expectedPossiblePrefix : expectedPossiblePrefixes) {
          ok |= expectedPossiblePrefix.equals(cause.getMessage());
        }
        if (!ok) {
          fail("Was expecting <" + cause.getMessage() + ">  to be equals to one of " + expectedPossiblePrefixes);
        }
      } else {
        boolean ok = expectedPossiblePrefixes.isEmpty();
        for (String expectedPossiblePrefix : expectedPossiblePrefixes) {
          ok |= cause.getMessage().startsWith(expectedPossiblePrefix);
        }
        if (!ok) {
          fail("Was expecting e.getCause().getMessage() to be prefixed by one of " + expectedPossiblePrefixes);
        }
        assertTrue(cause.getMessage().endsWith(expectedSuffix));
      }
    }
  }

  @Test
  public void testCrlInvalidPath() throws Exception {
    HttpClientOptions clientOptions = new HttpClientOptions();
    clientOptions.setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get());
    clientOptions.setSsl(true);
    clientOptions.addCrlPath("/invalid.pem");
    HttpClient client = vertx.createHttpClient(clientOptions);
    HttpClientRequest req = client.request(HttpMethod.CONNECT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", (handler) -> {});
    try {
      req.end();
      fail("Was expecting a failure");
    } catch (VertxException e) {
      assertNotNull(e.getCause());
      assertEquals(NoSuchFileException.class, e.getCause().getCause().getClass());
    }
  }

  @Test
  // Access https server via connect proxy
  public void testHttpsProxy() throws Exception {
    startProxy(null, ProxyType.HTTP);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy().pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "localhost:4043", proxy.getLastUri());
    assertEquals("Host header doesn't contain target host", "localhost:4043", proxy.getLastRequestHeaders().get("Host"));
  }

  @Test
  // Check that proxy auth fails if it is missing
  public void testHttpsProxyAuthFail() throws Exception {
    startProxy("username", ProxyType.HTTP);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy().fail();
  }

  @Test
  // Access https server via connect proxy with proxy auth required
  public void testHttpsProxyAuth() throws Exception {
    startProxy("username", ProxyType.HTTP);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy().useProxyAuth().pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "localhost:4043", proxy.getLastUri());
    assertEquals("Host header doesn't contain target host", "localhost:4043", proxy.getLastRequestHeaders().get("Host"));
  }

  @Test
  // Access https server via connect proxy with a hostname that doesn't resolve
  // the hostname may resolve at the proxy if that is accessing another DNS
  // we simulate this by mapping the hostname to localhost:xxx in the test proxy code
  public void testHttpsProxyUnknownHost() throws Exception {
    startProxy(null, ProxyType.HTTP);
    proxy.setForceUri("localhost:4043");
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy()
        .connectHostname("doesnt-resolve.host-name").clientTrustAll().clientVerifyHost(false).pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "doesnt-resolve.host-name:4043", proxy.getLastUri());
    assertEquals("Host header doesn't contain target host", "doesnt-resolve.host-name:4043", proxy.getLastRequestHeaders().get("Host"));
  }

  @Test
  // Access https server via socks5 proxy
  public void testHttpsSocks() throws Exception {
    startProxy(null, ProxyType.SOCKS5);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy().useSocksProxy().pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "localhost:4043", proxy.getLastUri());
  }

  @Test
  // Access https server via socks5 proxy with authentication
  public void testHttpsSocksAuth() throws Exception {
    startProxy("username", ProxyType.SOCKS5);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy().useSocksProxy().useProxyAuth().pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "localhost:4043", proxy.getLastUri());
  }

  @Test
  // Access https server via socks proxy with a hostname that doesn't resolve
  // the hostname may resolve at the proxy if that is accessing another DNS
  // we simulate this by mapping the hostname to localhost:xxx in the test proxy code
  public void testSocksProxyUnknownHost() throws Exception {
    startProxy(null, ProxyType.SOCKS5);
    proxy.setForceUri("localhost:4043");
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy().useSocksProxy()
        .connectHostname("doesnt-resolve.host-name").clientTrustAll().clientVerifyHost(false).pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "doesnt-resolve.host-name:4043", proxy.getLastUri());
  }
}
