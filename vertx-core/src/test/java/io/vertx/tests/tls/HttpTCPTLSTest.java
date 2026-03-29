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

import org.assertj.core.api.Assertions;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.*;

import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.proxy.Proxy;
import io.vertx.test.proxy.ProxyKind;
import io.vertx.test.proxy.WithProxy;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.buffer.Buffer;
import io.vertx.test.core.TestUtils;
import io.vertx.test.proxy.HAProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpTCPTLSTest extends HttpTLSTest {

  @Rule
  public Proxy proxy = new Proxy();

  protected HttpClientOptions createBaseClientOptions() {
    return ((HttpConfig.Http1xOr2Config)config).createBaseClientOptions();
  }

  protected HttpServerOptions createBaseServerOptions() {
    return ((HttpConfig.Http1xOr2Config)config).createBaseServerOptions();
  }

  public HttpTCPTLSTest(HttpConfig config) {
    super(config);
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
      Assertions.assertThat(exceptionMessage).endsWith(expectedSuffix);
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

  @WithProxy(kind = ProxyKind.HTTP)
  @Test
  // Access https server via connect proxy
  public void testHttpsProxy() throws Exception {
    testProxy(ProxyType.HTTP);
    assertEquals("Host header doesn't contain target host", DEFAULT_HTTPS_HOST_AND_PORT, proxy.lastRequestHeaders().get("Host"));
    assertEquals("Host header doesn't contain target host", HttpMethod.CONNECT, proxy.lastMethod());
  }

  private void testProxy(ProxyType proxyType) throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(proxyType).pass();
    assertNotNull("connection didn't access the proxy", proxy.lastUri());
    assertEquals("hostname resolved but it shouldn't be", DEFAULT_HTTPS_HOST_AND_PORT, proxy.lastUri());
  }

  @WithProxy(kind = ProxyKind.HTTP, localhosts = { "localhost", "host2.com"})
  @Test
  // Access https server via connect proxy
  public void testHttpsProxyWithSNI() throws Exception {
    testProxyWithSNI(ProxyType.HTTP);
    assertEquals("Host header doesn't contain target host", "host2.com:" + DEFAULT_HTTPS_PORT, proxy.lastRequestHeaders().get("Host"));
    assertEquals("Host header doesn't contain target host", HttpMethod.CONNECT, proxy.lastMethod());
  }

  private void testProxyWithSNI(ProxyType proxyType) throws Exception {
    Certificate cert = testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE)
        .serverSni()
        .useProxy(proxyType)
        .requestOptions(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost("host2.com"))
        .pass()
        .clientPeerCert();
    assertNotNull("connection didn't access the proxy", proxy.lastUri());
    assertEquals("hostname resolved but it shouldn't be", "host2.com:" + DEFAULT_HTTPS_PORT, proxy.lastUri());
    assertEquals("host2.com", TestUtils.cnOf(cert));
  }

  @WithProxy(username = "username", kind = ProxyKind.HTTP)
  @Test
  // Check that proxy auth fails if it is missing
  public void testHttpsProxyAuthFail() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.HTTP).fail();
  }

  @WithProxy(username = "username", kind = ProxyKind.HTTP)
  @Test
  // Access https server via connect proxy with proxy auth required
  public void testHttpsProxyAuth() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.HTTP).useProxyAuth().pass();
    assertNotNull("connection didn't access the proxy", proxy.lastUri());
    assertEquals("hostname resolved but it shouldn't be", DEFAULT_HTTPS_HOST_AND_PORT, proxy.lastUri());
    assertEquals("Host header doesn't contain target host", DEFAULT_HTTPS_HOST_AND_PORT, proxy.lastRequestHeaders().get("Host"));
    assertEquals("Host header doesn't contain target host", HttpMethod.CONNECT, proxy.lastMethod());
  }

  @WithProxy(kind = ProxyKind.HTTP)
  @Test
  // Access https server via connect proxy with a hostname that doesn't resolve
  // the hostname may resolve at the proxy if that is accessing another DNS
  // we simulate this by mapping the hostname to localhost:xxx in the test proxy code
  public void testHttpsProxyUnknownHost() throws Exception {
    proxy.forceUri(DEFAULT_HTTPS_HOST_AND_PORT);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.HTTP)
        .connectHostname("doesnt-resolve.host-name").clientTrustAll().clientVerifyHost(false).pass();
    assertNotNull("connection didn't access the proxy", proxy.lastUri());
    assertEquals("hostname resolved but it shouldn't be", "doesnt-resolve.host-name:" + DEFAULT_HTTPS_PORT, proxy.lastUri());
    assertEquals("Host header doesn't contain target host", "doesnt-resolve.host-name:" + DEFAULT_HTTPS_PORT, proxy.lastRequestHeaders().get("Host"));
    assertEquals("Host header doesn't contain target host", HttpMethod.CONNECT, proxy.lastMethod());
  }

  @WithProxy(kind = ProxyKind.SOCKS5)
  @Test
  // Access https server via socks5 proxy
  public void testHttpsSocks() throws Exception {
    testProxy(ProxyType.SOCKS5);
  }

  @WithProxy(kind = ProxyKind.SOCKS5, localhosts = { "localhost", "host2.com" })
  @Test
  // Access https server via socks5 proxy
  public void testHttpsSocksWithSNI() throws Exception {
    testProxyWithSNI(ProxyType.SOCKS5);
  }

  @WithProxy(username = "username", kind = ProxyKind.SOCKS5)
  @Test
  // Access https server via socks5 proxy with authentication
  public void testHttpsSocksAuth() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.SOCKS5).useProxyAuth().pass();
    assertNotNull("connection didn't access the proxy", proxy.lastUri());
    assertEquals("hostname resolved but it shouldn't be", DEFAULT_HTTPS_HOST_AND_PORT, proxy.lastUri());
  }

  @WithProxy(kind = ProxyKind.SOCKS5)
  @Test
  // Access https server via socks proxy with a hostname that doesn't resolve
  // the hostname may resolve at the proxy if that is accessing another DNS
  // we simulate this by mapping the hostname to localhost:xxx in the test proxy code
  public void testSocksProxyUnknownHost() throws Exception {
    proxy.forceUri(DEFAULT_HTTPS_HOST_AND_PORT);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).useProxy(ProxyType.SOCKS5)
        .connectHostname("doesnt-resolve.host-name").clientTrustAll().clientVerifyHost(false).pass();
    assertNotNull("connection didn't access the proxy", proxy.lastUri());
    assertEquals("hostname resolved but it shouldn't be", "doesnt-resolve.host-name:" + DEFAULT_HTTPS_PORT, proxy.lastUri());
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
  public void testOverrideClientSSLOptions() throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setSsl(true).setKeyCertOptions(Cert.SERVER_JKS.get()));
    server.requestHandler(request -> {
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setVerifyHost(false).setSsl(true).setTrustOptions(Trust.CLIENT_JKS.get()));
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
      .setSsl(true)
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
      .setSsl(true)
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
}
