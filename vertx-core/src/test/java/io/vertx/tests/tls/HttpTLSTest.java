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

import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assume.assumeTrue;

import java.io.*;
import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.*;

import io.vertx.core.*;
import io.netty.handler.codec.quic.QuicSslEngine;
import io.vertx.core.http.*;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.test.http.HttpTestBase;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.TrustAllTrustManager;
import io.vertx.test.core.TestUtils;
import io.vertx.test.proxy.HAProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

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
        "127.0.0.1 host2.com\n" +
        "127.0.0.1 sub.host3.com\n" +
        "127.0.0.1 host4.com\n" +
        "127.0.0.1 www.host4.com\n" +
        "127.0.0.1 host5.com\n" +
        "127.0.0.1 www.host5.com\n" +
        "127.0.0.1 unknown.com"));
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
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()

      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"}).pass();
  }

  @Test
  // Provide an host name with a trailing dot
  public void testTLSTrailingDotHost() throws Exception {
    // We just need a vanilla cert for this test
    SelfSignedCertificate cert = SelfSignedCertificate.create("host2.com");
    TLSTest test = testTLS(Cert.NONE, cert::trustOptions, cert::keyCertOptions, Trust.NONE)
      .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com."))
      .pass();
    assertEquals("host2.com", TestUtils.cnOf(test.clientPeerCert()));
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
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll().serverEnabledSecureTransportProtocol(new String[]{"HelloWorld"}).fail();
  }

  @Test
  // Specify some non matching TLS protocols
  public void testTLSNonMatchingProtocolVersions() throws Exception {
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
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientVerifyHost().clientOpenSSL().pass();
  }

  @Test
  // Test host verification with a CN NOT matching localhost
  public void testTLSVerifyNonMatchingHostOpenSSL() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_MIM, Trust.NONE).clientVerifyHost().clientOpenSSL().fail();
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
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientOpenSSL()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .serverOpenSSL()
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.3"}).pass();
  }

  @Test
  // Disable TLSv1.3
  public void testDisableTLSv1_3() throws Exception {
    Assume.assumeFalse(System.getProperty("java.version").startsWith("1.8"));
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.2"})
      .fail();
  }

  @Test
  // Disable TLSv1.3 with OpenSSL
  public void testDisableTLSv1_3OpenSSL() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.2"})
      .serverOpenSSL()
      .fail();
  }

  @Test
  // Disable TLSv1.2
  public void testDisableTLSv1_2() throws Exception {
    Assume.assumeFalse(System.getProperty("java.version").startsWith("1.8"));
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE).clientTrustAll()
      .clientEnabledSecureTransportProtocol(new String[]{"TLSv1.2"})
      .serverEnabledSecureTransportProtocol(new String[]{"TLSv1.3"})
      .fail();
  }

  @Test
  // Disable TLSv1.2 with OpenSSL
  public void testDisableTLSv1_2OpenSSL() throws Exception {
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
    assertEquals("host2.com", TestUtils.cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
  }

  @Test
  // Client provides SNI and server responds with a matching certificate for the indicated server name
  public void testSNITrustPKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host2.com", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI and server responds with a matching certificate for the indicated server name
  public void testSNITrustPEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host2.com", TestUtils.cnOf(cert));
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
    assertEquals("localhost", TestUtils.cnOf(cert));
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
    assertEquals("localhost", TestUtils.cnOf(test.clientPeerCert()));
    assertEquals("unknown.com", test.indicatedServerName);
  }

  @Test
  // Client provides SNI matched on the server by a wildcard certificate
  public void testSNIWildcardMatch() throws Exception {
    TLSTest test = testTLS(Cert.NONE, Trust.SNI_JKS_HOST3, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("sub.host3.com"))
        .pass();
    assertEquals("*.host3.com", TestUtils.cnOf(test.clientPeerCert()));
    assertEquals("sub.host3.com", test.indicatedServerName);
  }

  @Test
  // Client provides SNI matched on the server by a wildcard certificate
  public void testSNIWildcardMatchPKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST3, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("sub.host3.com"))
        .pass()
        .clientPeerCert();
    assertEquals("*.host3.com", TestUtils.cnOf(cert));
  }

  @Test
  // Client provides SNI matched on the server by a wildcard certificate
  public void testSNIWildcardMatchPEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST3, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("sub.host3.com"))
        .pass()
        .clientPeerCert();
    assertEquals("*.host3.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch1() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host4.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch1PKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host4.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch1PEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host4.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch2() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host4.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch2PKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host4.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameMatch2PEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST4, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host4.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host4.com certificate", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameWildcardMatch() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host5.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameWildcardMatchPKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host5.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAlternativeNameWildcardMatchPEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("www.host5.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host5.com", TestUtils.cnOf(cert));
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
    assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAltenativeNameCNMatch2PKCS12() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PKCS12, Trust.NONE)
        .serverSni()
        .clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host5.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host5.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNISubjectAltenativeNameCNMatch2PEM() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST5, Cert.SNI_PEM, Trust.NONE)
        .serverSni()
        .clientVerifyHost(false)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host5.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host5.com", TestUtils.cnOf(cert));
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
    assertEquals("host2.com", TestUtils.cnOf(cert));
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
    assertEquals("host2.com", TestUtils.cnOf(cert));
  }

  @Test
  public void testSNIWithOpenSSL() throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .clientOpenSSL()
        .serverOpenSSL()
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    assertEquals("host2.com", TestUtils.cnOf(cert));
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
    assertEquals(null, test.indicatedServerName);
  }

  @Test
  public void testSNIForceSend() throws Exception {
    TLSTest test = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .clientForceSni()
        .serverSni()
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass();
    assertEquals("host2.com", test.indicatedServerName);
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

  @Ignore
  @Test
  // Provide an host name with a trailing dot validated on the server with SNI
  public void testSniWithTrailingDotHost() throws Exception {
    TLSTest test = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
      .serverSni()
      .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com."))
      .pass();
    assertEquals("host2.com", TestUtils.cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
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
      if (proxyType == null || shouldPass) {
        // The test with proxy that fails will not connect
        waitFor(2);
      }
      HttpClientOptions options = createBaseClientOptions();
      options.setProtocolVersion(version);
      options.setSsl(clientSSL);
      options.setForceSni(clientForceSNI);
      if (clientTrustAll) {
        options.setTrustAll(true);
      }
      if (clientUsesCrl) {
        options.addCrlPath("tls/root-ca/crl.pem");
      }
      if (clientOpenSSL) {
        options.setSslEngineOptions(new OpenSSLEngineOptions());
      } else {
        options.setSslEngineOptions(new JdkSSLEngineOptions());
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
      if(clientEnabledSecureTransportProtocol.length > 0) {
        options.getEnabledSecureTransportProtocols().forEach(options::removeEnabledSecureTransportProtocol);
      }
      for (String protocol : clientEnabledSecureTransportProtocol) {
        options.addEnabledSecureTransportProtocol(protocol);
      }
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
        options.setProxyOptions(proxyOptions);
      }
      client = vertx.createHttpClient(options);
      HttpServerOptions serverOptions = createBaseServerOptions();
      serverOptions.setTrustOptions(serverTrust);
      serverOptions.setAlpnVersions(Arrays.asList(version));
      serverOptions.setKeyCertOptions(serverCert);
      if (requiresClientAuth) {
        serverOptions.setClientAuth(ClientAuth.REQUIRED);
      }
      if (serverUsesCrl) {
        serverOptions.addCrlPath("tls/root-ca/crl.pem");
      }
      if (serverOpenSSL) {
        serverOptions.setSslEngineOptions(new OpenSSLEngineOptions());
      }
      if (serverUsesAlpn == Boolean.TRUE) {
        serverOptions.setUseAlpn(serverUsesAlpn);
      }
      serverOptions.setSsl(serverSSL);
      serverOptions.setSni(serverSNI);
      serverOptions.setUseProxyProtocol(serverUsesProxyProtocol);
      for (String suite: serverEnabledCipherSuites) {
        serverOptions.addEnabledCipherSuite(suite);
      }
      if(serverEnabledSecureTransportProtocol.length > 0) {
        serverOptions.getEnabledSecureTransportProtocols().forEach(serverOptions::removeEnabledSecureTransportProtocol);
      }
      for (String protocol : serverEnabledSecureTransportProtocol) {
        serverOptions.addEnabledSecureTransportProtocol(protocol);
      }
      server.close();
      server = vertx.createHttpServer(serverOptions.setPort(DEFAULT_HTTPS_PORT));
      server.connectionHandler(conn -> complete());
      AtomicInteger count = new AtomicInteger();
      server.exceptionHandler(err -> {
        if (!shouldPass) {
          if (count.incrementAndGet() == 1) {
            complete();
          }
        }
      });
      server.requestHandler(req -> {
        indicatedServerName = req.connection().indicatedServerName();
        assertEquals(version, req.version());
        assertEquals(serverSSL, req.isSSL());
        if (req.method() == HttpMethod.GET || req.method() == HttpMethod.HEAD) {
          req.response().end();
        } else {
          req.bodyHandler(buffer -> {
            assertEquals("foo", buffer.toString());
            req.response().end("bar");
          });
        }
      });
      server.listen().onComplete(onSuccess(v -> {
        String httpHost;
        if (connectHostname != null) {
          httpHost = connectHostname;
        } else {
          httpHost = DEFAULT_HTTP_HOST;
        }
        Future<Void> fut = requestProvider.apply(client).compose(req -> {
          req.setFollowRedirects(followRedirects);
          return req.send("foo").compose(resp -> {
            HttpConnection conn = resp.request().connection();
            if (conn.isSsl()) {
              try {
                clientPeerCert = conn.peerCertificates().get(0);
              } catch (SSLPeerUnverifiedException ignore) {
              }
            }
            if (shouldPass) {
              resp.version();
              HttpMethod method = resp.request().getMethod();
              if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                return resp.end();
              } else {
                return resp.body().map(body -> {
                  assertEquals("bar", body.toString());
                  return null;
                });
              }
            } else {
              HttpTLSTest.this.fail("Should not get a response");
              return null;
            }
          });
        });
        fut.onSuccess(v2 -> {
          assertTrue(shouldPass);
          complete();
        });
        fut.onFailure(err -> {
          assertFalse("Should not fail " + err.getMessage(), shouldPass);
          complete();
        });
      }));
      await();
      return this;
    }
  }

  protected TLSTest testTLS(Cert<?> clientCert, Trust<?> clientTrust,
                          Cert<?> serverCert, Trust<?> serverTrust) throws Exception {
    return new TLSTest(clientCert, clientTrust, serverCert, serverTrust);
  }

  @Test
  public void testJKSInvalidPath() {
    testInvalidKeyStore(Cert.SERVER_JKS.get().setPath("/invalid.jks"), "Unable to read file at path", "invalid.jks'");
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
    testInvalidKeyStore(Cert.SERVER_PKCS12.get().setPath("/invalid.p12"), "Unable to read file at path", "invalid.p12'");
  }

  @Test
  public void testPKCS12MissingPassword() {
    String msg;
    if (PlatformDependent.javaVersion() < 15) {
      msg = "Get Key failed: null";
    } else {
      msg = "Get Key failed: Cannot read the array length because \"password\" is null";
    }
    testInvalidKeyStore(Cert.SERVER_PKCS12.get().setPassword(null), msg, null);
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
    testInvalidKeyStore(Cert.SERVER_PEM.get().setKeyPath("/invalid.pem"), "Unable to read file at path", "invalid.pem'");
  }

  @Test
  public void testKeyCertMissingCertPath() {
    testInvalidKeyStore(Cert.SERVER_PEM.get().setCertPath(null), "Missing X.509 certificate", null);
  }

  @Test
  public void testKeyCertInvalidCertPath() {
    testInvalidKeyStore(Cert.SERVER_PEM.get().setCertPath("/invalid.pem"), "Unable to read file at path", "invalid.pem'");
  }

  @Test
  public void testKeyCertInvalidPem() throws IOException {
    String[] contents = {
        "",
        "-----BEGIN PRIVATE KEY-----",
        "-----BEGIN RSA PRIVATE KEY-----",
        "-----BEGIN EC PRIVATE KEY-----",
        "-----BEGIN PRIVATE KEY-----\n-----END PRIVATE KEY-----",
        "-----BEGIN RSA PRIVATE KEY-----\n-----END RSA PRIVATE KEY-----",
        "-----BEGIN EC PRIVATE KEY-----\n-----END EC PRIVATE KEY-----",
        "-----BEGIN PRIVATE KEY-----\n*\n-----END PRIVATE KEY-----",
        "-----BEGIN RSA PRIVATE KEY-----\n*\n-----END RSA PRIVATE KEY-----",
        "-----BEGIN EC PRIVATE KEY-----\n*\n-----END EC PRIVATE KEY-----"
    };
    String[] messages = {
        "Missing -----BEGIN PRIVATE KEY----- or -----BEGIN RSA PRIVATE KEY----- or -----BEGIN EC PRIVATE KEY----- delimiter",
        "Missing -----END PRIVATE KEY----- delimiter",
        "Missing -----END RSA PRIVATE KEY----- delimiter",
        "Missing -----END EC PRIVATE KEY----- delimiter",
        "Empty pem file",
        "Empty pem file",
        "Empty pem file",
        "Input byte[] should at least have 2 bytes for base64 bytes",
        "Input byte[] should at least have 2 bytes for base64 bytes",
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
    testInvalidTrustStore(new PemTrustOptions().addCertPath("/invalid.pem"), "Unable to read file at path", "invalid.pem'");
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
    testStore(new HttpServerOptions().setKeyCertOptions(options), Collections.singletonList(expectedPrefix), expectedSuffix);
  }

  private void testInvalidKeyStore(KeyCertOptions options, List<String> expectedPossiblePrefixes, String expectedSuffix) {
    testStore(new HttpServerOptions().setKeyCertOptions(options), expectedPossiblePrefixes, expectedSuffix);
  }

  private void testInvalidTrustStore(TrustOptions options, String expectedPrefix, String expectedSuffix) {
    HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions.setTrustOptions(options);
    testStore(serverOptions, Collections.singletonList(expectedPrefix), expectedSuffix);
  }

  private void testStore(HttpServerOptions serverOptions, List<String> expectedPossiblePrefixes, String expectedSuffix) {
    serverOptions.setSsl(true);
    serverOptions.setPort(DEFAULT_HTTPS_PORT);
    HttpServer server = vertx.createHttpServer(serverOptions);
    server.requestHandler(req -> {
    });
    AtomicReference<Throwable> failure = new AtomicReference<>();
    server.listen().onComplete(onFailure(failure::set));
    assertWaitUntil(() -> failure.get() != null);
    Throwable cause = failure.get();
    String exceptionMessage = cause.getMessage();
    if (expectedSuffix == null) {
      boolean ok = expectedPossiblePrefixes.isEmpty();
      for (String expectedPossiblePrefix : expectedPossiblePrefixes) {
        ok |= expectedPossiblePrefix.equals(exceptionMessage);
      }
      if (!ok) {
        fail("Was expecting <" + exceptionMessage + ">  to be equals to one of " + expectedPossiblePrefixes);
      }
    } else {
      boolean ok = expectedPossiblePrefixes.isEmpty();
      for (String expectedPossiblePrefix : expectedPossiblePrefixes) {
        ok |= exceptionMessage.startsWith(expectedPossiblePrefix);
      }
      if (!ok) {
        fail("Was expecting <" + exceptionMessage + "> e.getCause().getMessage() to be prefixed by one of " + expectedPossiblePrefixes);
      }
      assertThat(exceptionMessage, endsWith(expectedSuffix));
    }
  }

  @Test
  public void testCrlInvalidPath() {
    HttpClientOptions clientOptions = createBaseClientOptions();
    clientOptions.setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get());
    clientOptions.setSsl(true);
    clientOptions.addCrlPath("/invalid.pem");
    HttpClient client = vertx.createHttpClient(clientOptions);
    client.request(HttpMethod.GET, 9292, "localhost", "/").onComplete(onFailure(err -> {
      assertEquals(NoSuchFileException.class, TestUtils.rootCause(err).getClass());
      testComplete();
    }));
    await();
  }

  // Proxy tests

  @Test
  // Access https server via connect proxy
  public void testHttpsProxy() throws Exception {
    testProxy(ProxyType.HTTP);
    assertEquals("Host header doesn't contain target host", DEFAULT_HTTPS_HOST_AND_PORT, proxy.getLastRequestHeaders().get("Host"));
    assertEquals("Host header doesn't contain target host", HttpMethod.CONNECT, proxy.getLastMethod());
  }

  private void testProxy(ProxyType proxyType) throws Exception {
    startProxy(null, proxyType);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(proxyType).pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", DEFAULT_HTTPS_HOST_AND_PORT, proxy.getLastUri());
  }

  @Test
  // Access https server via connect proxy
  public void testHttpsProxyWithSNI() throws Exception {
    testProxyWithSNI(ProxyType.HTTP);
    assertEquals("Host header doesn't contain target host", "host2.com:" + DEFAULT_HTTPS_PORT, proxy.getLastRequestHeaders().get("Host"));
    assertEquals("Host header doesn't contain target host", HttpMethod.CONNECT, proxy.getLastMethod());
  }

  private void testProxyWithSNI(ProxyType proxyType) throws Exception {
    startProxy(null, proxyType);
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .useProxy(proxyType)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "host2.com:" + DEFAULT_HTTPS_PORT, proxy.getLastUri());
    assertEquals("host2.com", TestUtils.cnOf(cert));
  }

  @Test
  // Check that proxy auth fails if it is missing
  public void testHttpsProxyAuthFail() throws Exception {
    startProxy("username", ProxyType.HTTP);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.HTTP).fail();
  }

  @Test
  // Access https server via connect proxy with proxy auth required
  public void testHttpsProxyAuth() throws Exception {
    startProxy("username", ProxyType.HTTP);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.HTTP).useProxyAuth().pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", DEFAULT_HTTPS_HOST_AND_PORT, proxy.getLastUri());
    assertEquals("Host header doesn't contain target host", DEFAULT_HTTPS_HOST_AND_PORT, proxy.getLastRequestHeaders().get("Host"));
    assertEquals("Host header doesn't contain target host", HttpMethod.CONNECT, proxy.getLastMethod());
  }

  @Test
  // Access https server via connect proxy with a hostname that doesn't resolve
  // the hostname may resolve at the proxy if that is accessing another DNS
  // we simulate this by mapping the hostname to localhost:xxx in the test proxy code
  public void testHttpsProxyUnknownHost() throws Exception {
    startProxy(null, ProxyType.HTTP);
    proxy.setForceUri(DEFAULT_HTTPS_HOST_AND_PORT);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.HTTP)
        .connectHostname("doesnt-resolve.host-name").clientTrustAll().clientVerifyHost(false).pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "doesnt-resolve.host-name:" + DEFAULT_HTTPS_PORT, proxy.getLastUri());
    assertEquals("Host header doesn't contain target host", "doesnt-resolve.host-name:" + DEFAULT_HTTPS_PORT, proxy.getLastRequestHeaders().get("Host"));
    assertEquals("Host header doesn't contain target host", HttpMethod.CONNECT, proxy.getLastMethod());
  }

  @Test
  // Access https server via socks5 proxy
  public void testHttpsSocks() throws Exception {
    testProxy(ProxyType.SOCKS5);
  }

  @Test
  // Access https server via socks5 proxy
  public void testHttpsSocksWithSNI() throws Exception {
    testProxyWithSNI(ProxyType.SOCKS5);
  }

  @Test
  // Access https server via socks5 proxy with authentication
  public void testHttpsSocksAuth() throws Exception {
    startProxy("username", ProxyType.SOCKS5);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.SOCKS5).useProxyAuth().pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", DEFAULT_HTTPS_HOST_AND_PORT, proxy.getLastUri());
  }

  @Test
  // Access https server via socks proxy with a hostname that doesn't resolve
  // the hostname may resolve at the proxy if that is accessing another DNS
  // we simulate this by mapping the hostname to localhost:xxx in the test proxy code
  public void testSocksProxyUnknownHost() throws Exception {
    startProxy(null, ProxyType.SOCKS5);
    proxy.setForceUri(DEFAULT_HTTPS_HOST_AND_PORT);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.SOCKS5)
        .connectHostname("doesnt-resolve.host-name").clientTrustAll().clientVerifyHost(false).pass();
    assertNotNull("connection didn't access the proxy", proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "doesnt-resolve.host-name:" + DEFAULT_HTTPS_PORT, proxy.getLastUri());
  }

  @Test
  public void testHAProxy() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    HAProxy proxy = new HAProxy(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT, header);
    proxy.start(vertx);
    try {
      testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE)
        .serverUsesProxyProtocol()
        .connectHostname(proxy.getHost())
        .connectPort(proxy.getPort())
        .pass();
    } finally {
      proxy.stop();
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
        fail(e);
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

  private void testUpdateSSLOptions(Function<Integer, JksOptions> blah, Function<Integer, JksOptions> bluh, boolean force, boolean updateTrust) throws Exception {
    server = vertx.createHttpServer(createBaseServerOptions().setKeyCertOptions(blah.apply(0)))
      .requestHandler(req -> {
        req.response().end("Hello World");
      });
    startServer(testAddress);
    Function<HttpClient, Future<Buffer>> request = client -> client.request(requestOptions).compose(req -> req.send().compose(HttpClientResponse::body));
    HttpClient client1 = vertx.createHttpClient(createBaseClientOptions().setVerifyHost(false).setTrustOptions(bluh.apply(0)));
    HttpClientAgent client2 = vertx.createHttpClient(createBaseClientOptions().setVerifyHost(false).setTrustOptions(bluh.apply(0)));
    request.apply(client1).onComplete(onSuccess(body1 -> {
      assertEquals("Hello World", body1.toString());
      server.updateSSLOptions(createBaseServerOptions().setKeyCertOptions(blah.apply(1)).getSslOptions(), force).onComplete(onSuccess(updateOccurred -> {
        request.apply(client2).onComplete(ar -> {
          assertEquals(!updateTrust, ar.succeeded());
          if (updateTrust) {
            assertTrue(updateOccurred);
            client2.updateSSLOptions(createBaseClientOptions().setTrustOptions(bluh.apply(1)).getSslOptions(), force).onComplete(onSuccess(v2 -> {
              request.apply(client2).onComplete(onSuccess(body2 -> {
                assertEquals("Hello World", body2.toString());
                testComplete();
              }));
            }));
          } else {
            // Same trust options since update did not occur
            assertFalse(updateOccurred);
            testComplete();
          }
        });
      }));
    }));
    await();
  }

  @Test
  public void testUpdateWithInvalidSSLOptions() throws Exception {
    server = vertx.createHttpServer(createBaseServerOptions().setKeyCertOptions(Cert.SERVER_JKS.get()))
      .requestHandler(req -> {
        req.response().end("Hello World");
      });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions().setVerifyHost(false).setTrustOptions(Trust.SERVER_JKS.get()));
    Future<Boolean> last = server.updateSSLOptions(createBaseServerOptions()
      .setKeyCertOptions(new JksOptions().setValue(TestUtils.randomBuffer(20)).setPassword("invalid"))
      .getSslOptions());
    last.onComplete(onFailure(err -> {
      client
        .request(requestOptions)
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(onSuccess(body -> {
          assertEquals("Hello World", body.toString());
          testComplete();
        }));
    }));
    await();
  }

  @Test
  public void testConcurrentUpdateSSLOptions() throws Exception {
    server = vertx.createHttpServer(createBaseServerOptions().setKeyCertOptions(Cert.SERVER_JKS.get()))
      .requestHandler(req -> {
        req.response().end("Hello World");
      });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions().setVerifyHost(false).setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get()));
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
      last = server.updateSSLOptions(createBaseServerOptions().setKeyCertOptions(list.get(i)).getSslOptions());
      last.onComplete(onSuccess(v -> {
        assertEquals(val, seq.getAndIncrement());
      }));
    }
    last.onComplete(onSuccess(v -> {
      client
        .request(requestOptions)
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(onSuccess(body -> {
        assertEquals("Hello World", body.toString());
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
      servers[i] = vertx.createHttpServer(createBaseServerOptions().setKeyCertOptions(Cert.SERVER_JKS.get()))
        .requestHandler(req -> {
          req.response().end(msg);
        });
      awaitFuture(servers[i].listen(testAddress));
    }
    HttpClient[] clients = new HttpClient[num];
    for (int i = 0;i < num;i++) {
      clients[i] = vertx.createHttpClient(createBaseClientOptions().setVerifyHost(false).setTrustOptions(Trust.SERVER_JKS.get()));
    }
    for (int i = 0;i < num;i++) {
      Buffer body = clients[i].request(requestOptions).compose(req -> req.send().compose(HttpClientResponse::body)).await();
      assertEquals("Hello World " + i, body.toString());
    }
    for (int i = 0;i < num;i++) {
      servers[i].updateSSLOptions(createBaseServerOptions().setKeyCertOptions(Cert.SERVER_PKCS12.get()).getSslOptions()).await();
    }
    for (int i = 0;i < num;i++) {
      clients[i].close();
      clients[i] = vertx.createHttpClient(createBaseClientOptions().setVerifyHost(false).setTrustOptions(Trust.SERVER_JKS.get()));
    }
    for (int i = 0;i < num;i++) {
      Buffer body = clients[i].request(requestOptions).compose(req -> req.send().compose(HttpClientResponse::body)).await();
      assertEquals("Hello World " + i, body.toString());
    }
  }

  @Test
  public void testOverrideClientSSLOptions() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setSsl(true).setKeyCertOptions(Cert.SERVER_JKS.get()));
    server.requestHandler(request -> {
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setVerifyHost(false).setSsl(true).setTrustOptions(Trust.CLIENT_JKS.get()));
    client.request(requestOptions).onComplete(onFailure(err -> {
      client.request(new RequestOptions(requestOptions).setSslOptions(new ClientSSLOptions().setTrustOptions(Trust.SERVER_JKS.get())))
        .onComplete(onSuccess(request -> {
          testComplete();
        }));
    }));
    await();
  }

  @Test
  public void testEngineUseEventLoopThread() throws Exception {
    testUseThreadPool(false, false);
  }

  @Test
  public void testEngineUseWorkerThreads() throws Exception {
    testUseThreadPool(true, false);
  }

  @Test
  public void testSniEngineUseEventLoopThread() throws Exception {
    testUseThreadPool(false, true);
  }

  @Test
  public void testSniEngineUseWorkerThreads() throws Exception {
    testUseThreadPool(true, true);
  }

  private void testUseThreadPool(boolean useWorkerThreads, boolean useSni) throws Exception {
    JksOptions jksOptions = Cert.SNI_JKS.get();
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(new ByteArrayInputStream(vertx.fileSystem().readFileBlocking(jksOptions.getPath()).getBytes()), jksOptions.getPassword().toCharArray());
    final Set<Thread> engineThreads = Collections.synchronizedSet(new HashSet<>());
    class TestKeyStoreSpi extends KeyStoreSpi {
      @Override
      public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        try {
          RSAPrivateKey key = (RSAPrivateKey) ks.getKey(alias, password);
          return new RSAPrivateKey() {
            private void addThread() {
              engineThreads.add(Thread.currentThread());
            }
            @Override
            public BigInteger getPrivateExponent() {
              addThread();
              return key.getPrivateExponent();
            }
            @Override
            public String getAlgorithm() {
              addThread();
              return key.getAlgorithm();
            }
            @Override
            public String getFormat() {
              addThread();
              return key.getFormat();
            }
            @Override
            public byte[] getEncoded() {
              addThread();
              return key.getEncoded();
            }
            @Override
            public BigInteger getModulus() {
              addThread();
              return key.getModulus();
            }
          };
        } catch (KeyStoreException e) {
          throw new UndeclaredThrowableException(e);
        }
      }
      @Override
      public Certificate[] engineGetCertificateChain(String alias) {
        try {
          return ks.getCertificateChain(alias);
        } catch (KeyStoreException e) {
          throw new UndeclaredThrowableException(e);
        }
      }
      @Override
      public Certificate engineGetCertificate(String alias) {
        try {
          return ks.getCertificate(alias);
        } catch (KeyStoreException e) {
          throw new UndeclaredThrowableException(e);
        }
      }
      @Override
      public Date engineGetCreationDate(String alias) {
        try {
          return ks.getCreationDate(alias);
        } catch (KeyStoreException e) {
          throw new UndeclaredThrowableException(e);
        }
      }
      @Override
      public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        throw new UnsupportedOperationException();
      }
      @Override
      public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        throw new UnsupportedOperationException();
      }
      @Override
      public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        throw new UnsupportedOperationException();
      }
      @Override
      public void engineDeleteEntry(String alias) throws KeyStoreException {
        throw new UnsupportedOperationException();
      }
      @Override
      public Enumeration<String> engineAliases() {
        try {
          return ks.aliases();
        } catch (KeyStoreException e) {
          throw new UndeclaredThrowableException(e);
        }
      }
      @Override
      public boolean engineContainsAlias(String alias) {
        try {
          return ks.containsAlias(alias);
        } catch (KeyStoreException e) {
          throw new UndeclaredThrowableException(e);
        }
      }
      @Override
      public int engineSize() {
        try {
          return ks.size();
        } catch (KeyStoreException e) {
          throw new UndeclaredThrowableException(e);
        }
      }
      @Override
      public boolean engineIsKeyEntry(String alias) {
        try {
          return ks.isKeyEntry(alias);
        } catch (KeyStoreException e) {
          throw new UndeclaredThrowableException(e);
        }
      }
      @Override
      public boolean engineIsCertificateEntry(String alias) {
        try {
          return ks.isCertificateEntry(alias);
        } catch (KeyStoreException e) {
          throw new UndeclaredThrowableException(e);
        }
      }
      @Override
      public String engineGetCertificateAlias(Certificate cert) {
        throw new UnsupportedOperationException();
      }
      @Override
      public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new UnsupportedOperationException();
      }
      @Override
      public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        // NOOP
      }
    }

    KeyStore testKs = new KeyStore(new TestKeyStoreSpi(), ks.getProvider(), ks.getType()) {

    };
    testKs.load(new ByteArrayInputStream(new byte[0]), new char[0]);
    KeyCertOptions testOptions = new KeyCertOptions() {
      @Override
      public KeyCertOptions copy() {
        return this;
      }
      @Override
      public KeyManagerFactory getKeyManagerFactory(Vertx vertx) throws Exception {
        return new KeyStoreHelper(testKs, jksOptions.getPassword(), null).getKeyMgrFactory();
      }
      @Override
      public Function<String, KeyManagerFactory> keyManagerFactoryMapper(Vertx vertx) throws Exception {
        X509KeyManager keyManager = (X509KeyManager) getKeyManagerFactory(vertx).getKeyManagers()[0];
        KeyManagerFactory kmf = KeyStoreHelper.toKeyManagerFactory(new X509KeyManager() {
          @Override
          public String[] getClientAliases(String keyType, Principal[] issuers) {
            throw new UnsupportedOperationException();
          }

          @Override
          public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            throw new UnsupportedOperationException();
          }

          @Override
          public String[] getServerAliases(String keyType, Principal[] issuers) {
            throw new UnsupportedOperationException();
          }

          @Override
          public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            throw new UnsupportedOperationException();
          }

          @Override
          public X509Certificate[] getCertificateChain(String alias) {
            return keyManager.getCertificateChain("test-host2");
          }

          @Override
          public PrivateKey getPrivateKey(String alias) {
            return keyManager.getPrivateKey("test-host2");
          }
        });
        return serverName -> {
          return kmf;
        };
      }
    };

    server = vertx.createHttpServer(createBaseServerOptions()
      .setSslEngineOptions(new JdkSSLEngineOptions().setUseWorkerThread(useWorkerThreads))
      .setSni(useSni)
      .setKeyCertOptions(testOptions)
    )
      .requestHandler(req -> {
        req.response().end("Hello World");
      });
    startServer(testAddress);
    Supplier<Future<Buffer>> request = () -> {
      RequestOptions options = new RequestOptions(requestOptions);
      if (useSni) {
        options.setHost("host2.com");
      }
      return client.request(options)
        .compose(req -> req.send()
          .compose(HttpClientResponse::body)
        );
    };
    CountDownLatch latch = new CountDownLatch(1);
    client = vertx.createHttpClient(createBaseClientOptions()
      .setKeepAlive(false)
      .setVerifyHost(false)
      .setTrustAll(true)
    );
    request.get().onComplete(onSuccess(body1 -> {
      assertEquals("Hello World", body1.toString());
      latch.countDown();
    }));
    awaitLatch(latch);
    assertTrue(engineThreads.size() > 0);
    long numWorkers = engineThreads.stream()
      .map(thread -> (VertxThread) thread)
      .filter(VertxThread::isWorker)
      .count();
    if (useWorkerThreads) {
      assertTrue(numWorkers > 0);
    } else {
      // It is fine using worker threads in this case
    }
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
    assertTrue("X509ExtendedKeyManager.chooseEngineServerAlias is not called", called.get());
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
    assertEquals("host2.com", TestUtils.cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
    assertTrue("X509ExtendedKeyManager.chooseEngineServerAlias is not called", called.get());
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
      if (engine instanceof QuicSslEngine) {
        peerHostVerifier.accept(engine.getSession().getPeerHost(), engine.getSession().getPeerPort());
      } else {
        peerHostVerifier.accept(engine.getPeerHost(), engine.getPeerPort());
      }
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
}
