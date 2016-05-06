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

import io.vertx.core.VertxException;
import io.vertx.core.http.*;
import io.vertx.core.net.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpTLSTest extends HttpTestBase {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();
  private ConnectHttpProxy proxy;

  @Override
  protected void tearDown() throws Exception {
    if (proxy != null) {
      proxy.stop();
    }
    super.tearDown();
  }

  private void startProxy(String username) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    proxy = new ConnectHttpProxy(username);
    proxy.start(vertx, v -> latch.countDown());
    awaitLatch(latch);
  }

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.JKS, TLSCert.NONE).clientTrustAll().pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.JKS, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPKCS12() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.PKCS12, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPEM() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.PEM, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertJKS_CAWithJKS_CA() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.JKS_CA, TLSCert.JKS_CA, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertJKS_CAWithPKCS12_CA() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PKCS12_CA, TLSCert.JKS_CA, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertJKS_CAWithPEM_CA() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PEM_CA, TLSCert.JKS_CA, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPKCS12_CAWithJKS_CA() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.JKS_CA, TLSCert.PKCS12_CA, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPKCS12_CAWithPKCS12_CA() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PKCS12_CA, TLSCert.PKCS12_CA, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPKCS12_CAWithPEM_CA() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PEM_CA, TLSCert.PKCS12_CA, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPEM_CAWithJKS_CA() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.JKS_CA, TLSCert.PEM_CA, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPEM_CAWithPKCS12_CA() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PKCS12_CA, TLSCert.PEM_CA, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPEM_CAWithPEM_CA() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PEM_CA, TLSCert.PEM_CA, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPKCS12ServerCert() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PKCS12, TLSCert.JKS, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPEMServerCert() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PEM, TLSCert.JKS, TLSCert.NONE).pass();
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.JKS, TLSCert.NONE).fail();
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServerPEM() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.PEM, TLSCert.NONE).fail();
  }

  @Test
  // Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(TLSCert.JKS, TLSCert.JKS, TLSCert.JKS, TLSCert.JKS).pass();
  }

  @Test
  // Client specifies cert even though it's not required
  public void testTLSClientCertNotRequiredPEM() throws Exception {
    testTLS(TLSCert.JKS, TLSCert.JKS, TLSCert.PEM, TLSCert.JKS).pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(TLSCert.JKS, TLSCert.JKS, TLSCert.JKS, TLSCert.JKS).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertRequiredPKCS12() throws Exception {
    testTLS(TLSCert.JKS, TLSCert.JKS, TLSCert.JKS, TLSCert.PKCS12).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertRequiredPEM() throws Exception {
    testTLS(TLSCert.JKS, TLSCert.JKS, TLSCert.JKS, TLSCert.PEM).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertPKCS12Required() throws Exception {
    testTLS(TLSCert.PKCS12, TLSCert.JKS, TLSCert.JKS, TLSCert.JKS).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert and it is required
  public void testTLSClientCertPEMRequired() throws Exception {
    testTLS(TLSCert.PEM, TLSCert.JKS, TLSCert.JKS, TLSCert.JKS).requiresClientAuth().pass();
  }

  @Test
  // Client specifies cert by CA and it is required
  public void testTLSClientCertPEM_CARequired() throws Exception {
    testTLS(TLSCert.PEM_CA, TLSCert.JKS, TLSCert.JKS, TLSCert.PEM_CA).requiresClientAuth().pass();
  }

  @Test
  // Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.JKS, TLSCert.JKS).requiresClientAuth().fail();
  }

  @Test
  // Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(TLSCert.JKS, TLSCert.JKS, TLSCert.JKS, TLSCert.NONE).requiresClientAuth().fail();
  }

  @Test
  // Server specifies cert that the client does not trust via a revoked certificate of the CA
  public void testTLSClientRevokedServerCert() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PEM_CA, TLSCert.PEM_CA, TLSCert.NONE).clientUsesCrl().fail();
  }

  @Test
  // Client specifies cert that the server does not trust via a revoked certificate of the CA
  public void testTLSRevokedClientCertServer() throws Exception {
    testTLS(TLSCert.PEM_CA, TLSCert.JKS, TLSCert.JKS, TLSCert.PEM_CA).requiresClientAuth().clientUsesCrl().fail();
  }

  @Test
  // Specify some matching cipher suites
  public void testTLSMatchingCipherSuites() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.JKS, TLSCert.NONE).clientTrustAll().serverEnabledCipherSuites(ENABLED_CIPHER_SUITES).pass();
  }

  @Test
  // Specify some non matching cipher suites
  public void testTLSNonMatchingCipherSuites() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.JKS, TLSCert.NONE).clientTrustAll().serverEnabledCipherSuites(new String[]{"TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"}).clientEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"}).fail();
  }

  @Test
  // Specify some matching TLS protocols
  public void testTLSMatchingProtocolVersions() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.JKS, TLSCert.NONE).clientTrustAll().serverEnabledSecureTransportProtocol(new String[]{"SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2"}).pass();
  }

  @Test
  // Specify some matching TLS protocols
  public void testTLSInvalidProtocolVersion() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.JKS, TLSCert.NONE).clientTrustAll().serverEnabledSecureTransportProtocol(new String[]{"HelloWorld"}).fail();
  }

  @Test
  // Specify some non matching TLS protocols
  public void testTLSNonMatchingProtocolVersions() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.JKS, TLSCert.NONE).clientTrustAll().serverEnabledSecureTransportProtocol(new String[]{"TLSv1.2"}).clientEnabledSecureTransportProtocol(new String[]{"SSLv2Hello"}).fail();
  }

  @Test
  // Test host verification with a CN matching localhost
  public void testTLSVerifyMatchingHost() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.JKS, TLSCert.NONE).clientTrustAll().clientVerifyHost().pass();
  }

  @Test
  // Test host verification with a CN NOT matching localhost
  public void testTLSVerifyNonMatchingHost() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.NONE, TLSCert.MIM, TLSCert.NONE).clientTrustAll().clientVerifyHost().fail();
  }

    // OpenSSL tests

  @Test
  // Server uses OpenSSL with PEM
  public void testTLSClientTrustServerCertPEMOpenSSL() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.PEM, TLSCert.NONE).serverOpenSSL().pass();
  }

  @Test
  // Client trusts OpenSSL with PEM
  public void testTLSClientTrustServerCertWithJKSOpenSSL() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.JKS, TLSCert.NONE).clientOpenSSL().pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertWithPKCS12OpenSSL() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PKCS12, TLSCert.JKS, TLSCert.NONE).clientOpenSSL().pass();
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertWithPEMOpenSSL() throws Exception {
    testTLS(TLSCert.NONE, TLSCert.PEM, TLSCert.JKS, TLSCert.NONE).clientOpenSSL().pass();
  }

  class TLSTest {

    HttpVersion version;
    TLSCert clientCert;
    TLSCert clientTrust;
    TLSCert serverCert;
    TLSCert serverTrust;
    boolean clientTrustAll;
    boolean clientUsesCrl;
    boolean clientUsesAlpn;
    boolean clientOpenSSL;
    boolean clientVerifyHost;
    boolean requiresClientAuth;
    boolean serverUsesCrl;
    boolean serverOpenSSL;
    boolean serverUsesAlpn;
    boolean useProxy;
    boolean useProxyAuth;
    String[] clientEnabledCipherSuites = new String[0];
    String[] serverEnabledCipherSuites = new String[0];
    String[] clientEnabledSecureTransportProtocol   = new String[0];
    String[] serverEnabledSecureTransportProtocol   = new String[0];


    public TLSTest(TLSCert clientCert, TLSCert clientTrust, TLSCert serverCert, TLSCert serverTrust) {
      this.version = HttpVersion.HTTP_1_1;
      this.clientCert = clientCert;
      this.clientTrust = clientTrust;
      this.serverCert = serverCert;
      this.serverTrust = serverTrust;
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

    void pass() {
      run(true);
    }

    void fail() {
      run(false);
    }

    void run(boolean shouldPass) {
      server.close();
      HttpClientOptions options = new HttpClientOptions();
      options.setProtocolVersion(version);
      options.setSsl(true);
      if (clientTrustAll) {
        options.setTrustAll(true);
      }
      if (clientUsesCrl) {
        options.addCrlPath("tls/ca/crl.pem");
      }
      if (clientOpenSSL) {
        options.setSslEngine(SSLEngine.OPENSSL);
      }
      if (clientUsesAlpn) {
        options.setUseAlpn(true);
      }
      if (clientVerifyHost) {
        options.setVerifyHost(true);
      }
      setOptions(options, clientTrust.getClientTrustOptions());
      setOptions(options, clientCert.getClientKeyCertOptions());
      for (String suite: clientEnabledCipherSuites) {
        options.addEnabledCipherSuite(suite);
      }
      for (String protocols: clientEnabledSecureTransportProtocol) {
        options.addEnabledSecureTransportProtocol(protocols);
      }
      if (useProxy) {
        options.setProxyHost("localhost");
        options.setProxyPort(13128);
      }
      if (useProxyAuth) {
        options.setProxyUsername("username");
        options.setProxyPassword("username");
      }
      client = createHttpClient(options);
      HttpServerOptions serverOptions = new HttpServerOptions();
      serverOptions.setSsl(true);
      setOptions(serverOptions, serverTrust.getServerTrustOptions());
      setOptions(serverOptions, serverCert.getServerKeyCertOptions());
      if (requiresClientAuth) {
        serverOptions.setClientAuth(ClientAuth.REQUIRED);
      }
      if (serverUsesCrl) {
        serverOptions.addCrlPath("tls/ca/crl.pem");
      }
      if (serverOpenSSL) {
        serverOptions.setSslEngine(SSLEngine.OPENSSL);
      }
      if (serverUsesAlpn) {
        serverOptions.setUseAlpn(true);
      }
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
          assertEquals(true, req.isSSL());
          assertEquals("foo", buffer.toString());
          req.response().end("bar");
        });
      });
      server.listen(ar -> {
        assertTrue(ar.succeeded());

        HttpClientRequest req = client.request(HttpMethod.GET, 4043, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, response -> {
          response.version();
          response.bodyHandler(data -> assertEquals("bar", data.toString()));
          testComplete();
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
    }

  }

  abstract HttpServer createHttpServer(HttpServerOptions options);

  abstract HttpClient createHttpClient(HttpClientOptions options);

  protected TLSTest testTLS(TLSCert clientCert, TLSCert clientTrust,
                          TLSCert serverCert, TLSCert serverTrust) throws Exception {
    return new TLSTest(clientCert, clientTrust, serverCert, serverTrust);
  }

  @Test
  public void testJKSInvalidPath() {
    testInvalidKeyStore(((JksOptions) TLSCert.JKS.getServerKeyCertOptions()).setPath("/invalid.jks"), "java.nio.file.NoSuchFileException: ", "invalid.jks");
  }

  @Test
  public void testJKSMissingPassword() {
    testInvalidKeyStore(((JksOptions) TLSCert.JKS.getServerKeyCertOptions()).setPassword(null), "Password must not be null", null);
  }

  @Test
  public void testJKSInvalidPassword() {
    testInvalidKeyStore(((JksOptions) TLSCert.JKS.getServerKeyCertOptions()).setPassword("wrongpassword"), "Keystore was tampered with, or password was incorrect", null);
  }

  @Test
  public void testJKSOpenSSL() {
    HttpServerOptions serverOptions = new HttpServerOptions().setSslEngine(SSLEngine.OPENSSL);
    setOptions(serverOptions, TLSCert.JKS.getServerKeyCertOptions());
    testStore(serverOptions, Collections.singletonList("OpenSSL server key/certificate must be configured with .pem format"), null);
  }

  @Test
  public void testPKCS12OpenSSL() {
    HttpServerOptions serverOptions = new HttpServerOptions().setSslEngine(SSLEngine.OPENSSL);
    setOptions(serverOptions, TLSCert.JKS.getServerKeyCertOptions());
    testStore(serverOptions, Collections.singletonList("OpenSSL server key/certificate must be configured with .pem format"), null);
  }

  @Test
  public void testPKCS12InvalidPath() {
    testInvalidKeyStore(((PfxOptions) TLSCert.PKCS12.getServerKeyCertOptions()).setPath("/invalid.p12"), "java.nio.file.NoSuchFileException: ", "invalid.p12");
  }

  @Test
  public void testPKCS12MissingPassword() {
    testInvalidKeyStore(((PfxOptions) TLSCert.PKCS12.getServerKeyCertOptions()).setPassword(null), "Get Key failed: null", null);
  }

  @Test
  public void testPKCS12InvalidPassword() {
    testInvalidKeyStore(((PfxOptions) TLSCert.PKCS12.getServerKeyCertOptions()).setPassword("wrongpassword"), Arrays.asList(
        "failed to decrypt safe contents entry: javax.crypto.BadPaddingException: Given final block not properly padded",
        "keystore password was incorrect"), null);
  }

  @Test
  public void testKeyCertMissingKeyPath() {
    testInvalidKeyStore(((PemKeyCertOptions) TLSCert.PEM.getServerKeyCertOptions()).setKeyPath(null), "Missing private key", null);
  }

  @Test
  public void testKeyCertInvalidKeyPath() {
    testInvalidKeyStore(((PemKeyCertOptions) TLSCert.PEM.getServerKeyCertOptions()).setKeyPath("/invalid.pem"), "java.nio.file.NoSuchFileException: ", "invalid.pem");
  }

  @Test
  public void testKeyCertMissingCertPath() {
    testInvalidKeyStore(((PemKeyCertOptions) TLSCert.PEM.getServerKeyCertOptions()).setCertPath(null), "Missing X.509 certificate", null);
  }

  @Test
  public void testKeyCertInvalidCertPath() {
    testInvalidKeyStore(((PemKeyCertOptions) TLSCert.PEM.getServerKeyCertOptions()).setCertPath("/invalid.pem"), "java.nio.file.NoSuchFileException: ", "invalid.pem");
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
      testInvalidKeyStore(((PemKeyCertOptions) TLSCert.PEM.getServerKeyCertOptions()).setKeyPath(file.toString()), expectedMessage, null);
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
    setOptions(serverOptions, options);
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
    setOptions(clientOptions, TLSCert.PEM_CA.getClientTrustOptions());
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
    startProxy(null);
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.JKS, TLSCert.NONE).useProxy().pass();
    // check that the connection did in fact go through the proxy
    assertNotNull(proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "localhost:4043", proxy.getLastUri());
  }

  @Test
  // Check that proxy auth fails if it is missing
  public void testHttpsProxyAuthFail() throws Exception {
    startProxy("username");
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.JKS, TLSCert.NONE).useProxy().useProxyAuth().fail();
  }

  @Test
  // Access https server via connect proxy with proxy auth required
  public void testHttpsProxyAuth() throws Exception {
    startProxy("username");
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.JKS, TLSCert.NONE).useProxy().useProxyAuth().pass();
    // check that the connection did in fact go through the proxy
    assertNotNull(proxy.getLastUri());
    assertEquals("hostname resolved but it shouldn't be", "localhost:4043", proxy.getLastUri());
  }
}
