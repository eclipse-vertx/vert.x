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

package io.vertx.core.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.impl.NetServerImpl;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.streams.ReadStream;
import io.vertx.test.core.*;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import io.vertx.test.netty.TestLoggerFactory;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.proxy.Socks4Proxy;
import io.vertx.test.proxy.SocksProxy;
import io.vertx.test.proxy.TestProxyBase;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetTest extends VertxTestBase {

  private static final Logger log = LoggerFactory.getLogger(NetTest.class);

  private SocketAddress testAddress;
  private NetServer server;
  private NetClient client;
  private TestProxyBase proxy;
  private File tmp;

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  public void setUp() throws Exception {
    super.setUp();
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", USE_NATIVE_TRANSPORT);
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
    } else {
      testAddress = SocketAddress.inetSocketAddress(1234, "localhost");
    }
    client = vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000));
    server = vertx.createNetServer();
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
        "127.0.0.1 localhost\n" +
        "127.0.0.1 host1\n" +
        "127.0.0.1 host2.com\n" +
        "127.0.0.1 example.com"));
    return options;
  }

  protected void awaitClose(NetServer server) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    server.close((asyncResult) -> {
      latch.countDown();
    });
    awaitLatch(latch);
  }

  protected void tearDown() throws Exception {
    if (tmp != null) {
      tmp.delete();
    }
    if (client != null) {
      client.close();
    }
    if (server != null) {
      awaitClose(server);
    }
    if (proxy != null) {
      proxy.stop();
    }
    super.tearDown();
  }

  @Test
  public void testClientOptions() {
    NetClientOptions options = new NetClientOptions();

    assertEquals(NetworkOptions.DEFAULT_SEND_BUFFER_SIZE, options.getSendBufferSize());
    int rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    assertIllegalArgumentException(() -> options.setSendBufferSize(0));
    assertIllegalArgumentException(() -> options.setSendBufferSize(-123));

    assertEquals(NetworkOptions.DEFAULT_RECEIVE_BUFFER_SIZE, options.getReceiveBufferSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    assertIllegalArgumentException(() -> options.setReceiveBufferSize(0));
    assertIllegalArgumentException(() -> options.setReceiveBufferSize(-123));

    assertTrue(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(false));
    assertFalse(options.isReuseAddress());

    assertEquals(NetworkOptions.DEFAULT_TRAFFIC_CLASS, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    assertIllegalArgumentException(() -> options.setTrafficClass(-2));
    assertIllegalArgumentException(() -> options.setTrafficClass(256));

    assertTrue(options.isTcpNoDelay());
    assertEquals(options, options.setTcpNoDelay(false));
    assertFalse(options.isTcpNoDelay());

    boolean tcpKeepAlive = false;
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(options, options.setTcpKeepAlive(!tcpKeepAlive));
    assertEquals(!tcpKeepAlive, options.isTcpKeepAlive());

    int soLinger = -1;
    assertEquals(soLinger, options.getSoLinger());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSoLinger(rand));
    assertEquals(rand, options.getSoLinger());
    assertIllegalArgumentException(() -> options.setSoLinger(-2));

    rand = TestUtils.randomPositiveInt();
    assertEquals(0, options.getIdleTimeout());
    assertEquals(options, options.setIdleTimeout(rand));
    assertEquals(rand, options.getIdleTimeout());

    assertFalse(options.isSsl());
    assertEquals(options, options.setSsl(true));
    assertTrue(options.isSsl());

    assertNull(options.getKeyCertOptions());
    JksOptions keyStoreOptions = new JksOptions().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setKeyStoreOptions(keyStoreOptions));
    assertEquals(keyStoreOptions, options.getKeyCertOptions());

    assertNull(options.getTrustOptions());
    JksOptions trustStoreOptions = new JksOptions().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setTrustStoreOptions(trustStoreOptions));
    assertEquals(trustStoreOptions, options.getTrustOptions());

    assertFalse(options.isTrustAll());
    assertEquals(options, options.setTrustAll(true));
    assertTrue(options.isTrustAll());

    String randomAlphaString = TestUtils.randomAlphaString(10);
    assertTrue(options.getHostnameVerificationAlgorithm().isEmpty());
    assertEquals(options, options.setHostnameVerificationAlgorithm(randomAlphaString));
    assertEquals(randomAlphaString, options.getHostnameVerificationAlgorithm());

    assertEquals(0, options.getReconnectAttempts());
    assertIllegalArgumentException(() -> options.setReconnectAttempts(-2));
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReconnectAttempts(rand));
    assertEquals(rand, options.getReconnectAttempts());

    assertEquals(1000, options.getReconnectInterval());
    assertIllegalArgumentException(() -> options.setReconnectInterval(0));
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReconnectInterval(rand));
    assertEquals(rand, options.getReconnectInterval());

    assertTrue(options.getEnabledCipherSuites().isEmpty());
    assertEquals(options, options.addEnabledCipherSuite("foo"));
    assertEquals(options, options.addEnabledCipherSuite("bar"));
    assertNotNull(options.getEnabledCipherSuites());
    assertTrue(options.getEnabledCipherSuites().contains("foo"));
    assertTrue(options.getEnabledCipherSuites().contains("bar"));

    assertEquals(false, options.isUseAlpn());
    assertEquals(options, options.setUseAlpn(true));
    assertEquals(true, options.isUseAlpn());

    assertNull(options.getSslEngineOptions());
    assertEquals(options, options.setSslEngineOptions(new JdkSSLEngineOptions()));
    assertTrue(options.getSslEngineOptions() instanceof JdkSSLEngineOptions);

    assertEquals(TCPSSLOptions.DEFAULT_SSL_HANDSHAKE_TIMEOUT, options.getSslHandshakeTimeout());
    long randLong = TestUtils.randomPositiveLong();
    assertEquals(options, options.setSslHandshakeTimeout(randLong));
    assertEquals(randLong, options.getSslHandshakeTimeout());
    assertIllegalArgumentException(() -> options.setSslHandshakeTimeout(-123));

    testComplete();
  }

  @Test
  public void testServerOptions() {
    NetServerOptions options = new NetServerOptions();

    assertEquals(NetworkOptions.DEFAULT_SEND_BUFFER_SIZE, options.getSendBufferSize());
    int rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    assertIllegalArgumentException(() -> options.setSendBufferSize(0));
    assertIllegalArgumentException(() -> options.setSendBufferSize(-123));

    assertEquals(NetworkOptions.DEFAULT_RECEIVE_BUFFER_SIZE, options.getReceiveBufferSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    assertIllegalArgumentException(() -> options.setReceiveBufferSize(0));
    assertIllegalArgumentException(() -> options.setReceiveBufferSize(-123));

    assertTrue(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(false));
    assertFalse(options.isReuseAddress());

    assertEquals(NetworkOptions.DEFAULT_TRAFFIC_CLASS, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    assertIllegalArgumentException(() -> options.setTrafficClass(-2));
    assertIllegalArgumentException(() -> options.setTrafficClass(256));

    assertTrue(options.isTcpNoDelay());
    assertEquals(options, options.setTcpNoDelay(false));
    assertFalse(options.isTcpNoDelay());

    boolean tcpKeepAlive = false;
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(options, options.setTcpKeepAlive(!tcpKeepAlive));
    assertEquals(!tcpKeepAlive, options.isTcpKeepAlive());

    int soLinger = -1;
    assertEquals(soLinger, options.getSoLinger());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSoLinger(rand));
    assertEquals(rand, options.getSoLinger());
    assertIllegalArgumentException(() -> options.setSoLinger(-2));

    rand = TestUtils.randomPositiveInt();
    assertEquals(0, options.getIdleTimeout());
    assertEquals(options, options.setIdleTimeout(rand));
    assertEquals(rand, options.getIdleTimeout());
    assertIllegalArgumentException(() -> options.setIdleTimeout(-1));

    assertFalse(options.isSsl());
    assertEquals(options, options.setSsl(true));
    assertTrue(options.isSsl());

    assertNull(options.getKeyCertOptions());
    JksOptions keyStoreOptions = new JksOptions().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setKeyStoreOptions(keyStoreOptions));
    assertEquals(keyStoreOptions, options.getKeyCertOptions());

    assertNull(options.getTrustOptions());
    JksOptions trustStoreOptions = new JksOptions().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setTrustStoreOptions(trustStoreOptions));
    assertEquals(trustStoreOptions, options.getTrustOptions());

    assertEquals(-1, options.getAcceptBacklog());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setAcceptBacklog(rand));
    assertEquals(rand, options.getAcceptBacklog());

    assertEquals(0, options.getPort());
    assertEquals(options, options.setPort(1234));
    assertEquals(1234, options.getPort());
    assertIllegalArgumentException(() -> options.setPort(-1));
    assertIllegalArgumentException(() -> options.setPort(65536));

    assertEquals("0.0.0.0", options.getHost());
    String randString = TestUtils.randomUnicodeString(100);
    assertEquals(options, options.setHost(randString));
    assertEquals(randString, options.getHost());

    assertTrue(options.getEnabledCipherSuites().isEmpty());
    assertEquals(options, options.addEnabledCipherSuite("foo"));
    assertEquals(options, options.addEnabledCipherSuite("bar"));
    assertNotNull(options.getEnabledCipherSuites());
    assertTrue(options.getEnabledCipherSuites().contains("foo"));
    assertTrue(options.getEnabledCipherSuites().contains("bar"));

    assertEquals(false, options.isUseAlpn());
    assertEquals(options, options.setUseAlpn(true));
    assertEquals(true, options.isUseAlpn());

    assertNull(options.getSslEngineOptions());
    assertEquals(options, options.setSslEngineOptions(new JdkSSLEngineOptions()));
    assertTrue(options.getSslEngineOptions() instanceof JdkSSLEngineOptions);

    assertFalse(options.isSni());
    assertEquals(options, options.setSni(true));
    assertTrue(options.isSni());

    assertEquals(TCPSSLOptions.DEFAULT_SSL_HANDSHAKE_TIMEOUT, options.getSslHandshakeTimeout());
    long randLong = TestUtils.randomPositiveLong();
    assertEquals(options, options.setSslHandshakeTimeout(randLong));
    assertEquals(randLong, options.getSslHandshakeTimeout());
    assertIllegalArgumentException(() -> options.setSslHandshakeTimeout(-123));

    testComplete();
  }

  @Test
  public void testCopyClientOptions() {
    NetClientOptions options = new NetClientOptions();
    int sendBufferSize = TestUtils.randomPositiveInt();
    int receiverBufferSize = TestUtils.randomPortInt();
    Random rand = new Random();
    boolean reuseAddress = rand.nextBoolean();
    int trafficClass = TestUtils.randomByte() + 128;
    boolean tcpNoDelay = rand.nextBoolean();
    boolean tcpKeepAlive = rand.nextBoolean();
    int soLinger = TestUtils.randomPositiveInt();
    int idleTimeout = TestUtils.randomPositiveInt();
    boolean ssl = rand.nextBoolean();
    String hostnameVerificationAlgorithm = TestUtils.randomAlphaString(10);
    JksOptions keyStoreOptions = new JksOptions();
    String ksPassword = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPassword(ksPassword);
    JksOptions trustStoreOptions = new JksOptions();
    String tsPassword = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPassword(tsPassword);
    String enabledCipher = TestUtils.randomAlphaString(100);
    int connectTimeout = TestUtils.randomPositiveInt();
    boolean trustAll = rand.nextBoolean();
    String crlPath = TestUtils.randomUnicodeString(100);
    Buffer crlValue = TestUtils.randomBuffer(100);
    int reconnectAttempts = TestUtils.randomPositiveInt();
    long reconnectInterval = TestUtils.randomPositiveInt();
    boolean useAlpn = TestUtils.randomBoolean();
    boolean openSslSessionCacheEnabled = rand.nextBoolean();
    long sslHandshakeTimeout = TestUtils.randomPositiveLong();

    SSLEngineOptions sslEngine = TestUtils.randomBoolean() ? new JdkSSLEngineOptions() : new OpenSSLEngineOptions();
    options.setSendBufferSize(sendBufferSize);
    options.setReceiveBufferSize(receiverBufferSize);
    options.setReuseAddress(reuseAddress);
    options.setTrafficClass(trafficClass);
    options.setSsl(ssl);
    options.setTcpNoDelay(tcpNoDelay);
    options.setTcpKeepAlive(tcpKeepAlive);
    options.setSoLinger(soLinger);
    options.setIdleTimeout(idleTimeout);
    options.setKeyStoreOptions(keyStoreOptions);
    options.setTrustStoreOptions(trustStoreOptions);
    options.addEnabledCipherSuite(enabledCipher);
    options.setConnectTimeout(connectTimeout);
    options.setTrustAll(trustAll);
    options.addCrlPath(crlPath);
    options.addCrlValue(crlValue);
    options.setReconnectAttempts(reconnectAttempts);
    options.setReconnectInterval(reconnectInterval);
    options.setUseAlpn(useAlpn);
    options.setSslEngineOptions(sslEngine);
    options.setHostnameVerificationAlgorithm(hostnameVerificationAlgorithm);
    options.setSslHandshakeTimeout(sslHandshakeTimeout);

    NetClientOptions copy = new NetClientOptions(options);
    assertEquals(options.toJson(), copy.toJson());
  }

  @Test
  public void testDefaultClientOptionsJson() {
    NetClientOptions def = new NetClientOptions();
    NetClientOptions json = new NetClientOptions(new JsonObject());
    assertEquals(def.getReconnectAttempts(), json.getReconnectAttempts());
    assertEquals(def.getReconnectInterval(), json.getReconnectInterval());
    assertEquals(def.isTrustAll(), json.isTrustAll());
    assertEquals(def.getCrlPaths(), json.getCrlPaths());
    assertEquals(def.getCrlValues(), json.getCrlValues());
    assertEquals(def.getConnectTimeout(), json.getConnectTimeout());
    assertEquals(def.isTcpNoDelay(), json.isTcpNoDelay());
    assertEquals(def.isTcpKeepAlive(), json.isTcpKeepAlive());
    assertEquals(def.getSoLinger(), json.getSoLinger());
    assertEquals(def.isSsl(), json.isSsl());
    assertEquals(def.isUseAlpn(), json.isUseAlpn());
    assertEquals(def.getSslEngineOptions(), json.getSslEngineOptions());
    assertEquals(def.getHostnameVerificationAlgorithm(), json.getHostnameVerificationAlgorithm());
    assertEquals(def.getSslHandshakeTimeout(), json.getSslHandshakeTimeout());
  }

  @Test
  public void testClientOptionsJson() {
    int sendBufferSize = TestUtils.randomPositiveInt();
    int receiverBufferSize = TestUtils.randomPortInt();
    Random rand = new Random();
    boolean reuseAddress = rand.nextBoolean();
    int trafficClass = TestUtils.randomByte() + 128;
    boolean tcpNoDelay = rand.nextBoolean();
    boolean tcpKeepAlive = rand.nextBoolean();
    int soLinger = TestUtils.randomPositiveInt();
    int idleTimeout = TestUtils.randomPositiveInt();
    boolean ssl = rand.nextBoolean();
    JksOptions keyStoreOptions = new JksOptions();
    String ksPassword = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPassword(ksPassword);
    String ksPath = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPath(ksPath);
    JksOptions trustStoreOptions = new JksOptions();
    String tsPassword = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPassword(tsPassword);
    String tsPath = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPath(tsPath);
    String enabledCipher = TestUtils.randomAlphaString(100);
    int connectTimeout = TestUtils.randomPositiveInt();
    boolean trustAll = rand.nextBoolean();
    String crlPath = TestUtils.randomUnicodeString(100);
    int reconnectAttempts = TestUtils.randomPositiveInt();
    long reconnectInterval = TestUtils.randomPositiveInt();
    boolean useAlpn = TestUtils.randomBoolean();
    String hostnameVerificationAlgorithm = TestUtils.randomAlphaString(10);
    String sslEngine = TestUtils.randomBoolean() ? "jdkSslEngineOptions" : "openSslEngineOptions";
    boolean openSslSessionCacheEnabled = rand.nextBoolean();
    long sslHandshakeTimeout = TestUtils.randomPositiveLong();

    JsonObject json = new JsonObject();
    json.put("sendBufferSize", sendBufferSize)
        .put("receiveBufferSize", receiverBufferSize)
        .put("reuseAddress", reuseAddress)
        .put("trafficClass", trafficClass)
        .put("tcpNoDelay", tcpNoDelay)
        .put("tcpKeepAlive", tcpKeepAlive)
        .put("soLinger", soLinger)
        .put("idleTimeout", idleTimeout)
        .put("ssl", ssl)
        .put("enabledCipherSuites", new JsonArray().add(enabledCipher))
        .put("connectTimeout", connectTimeout)
        .put("trustAll", trustAll)
        .put("crlPaths", new JsonArray().add(crlPath))
        .put("keyStoreOptions", new JsonObject().put("password", ksPassword).put("path", ksPath))
        .put("trustStoreOptions", new JsonObject().put("password", tsPassword).put("path", tsPath))
        .put("reconnectAttempts", reconnectAttempts)
        .put("reconnectInterval", reconnectInterval)
        .put("useAlpn", useAlpn)
        .put(sslEngine, new JsonObject())
        .put("hostnameVerificationAlgorithm", hostnameVerificationAlgorithm)
        .put("openSslSessionCacheEnabled", openSslSessionCacheEnabled)
        .put("sslHandshakeTimeout", sslHandshakeTimeout);

    NetClientOptions options = new NetClientOptions(json);
    assertEquals(sendBufferSize, options.getSendBufferSize());
    assertEquals(receiverBufferSize, options.getReceiveBufferSize());
    assertEquals(reuseAddress, options.isReuseAddress());
    assertEquals(trafficClass, options.getTrafficClass());
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(tcpNoDelay, options.isTcpNoDelay());
    assertEquals(soLinger, options.getSoLinger());
    assertEquals(idleTimeout, options.getIdleTimeout());
    assertEquals(ssl, options.isSsl());
    assertEquals(sslHandshakeTimeout, options.getSslHandshakeTimeout());
    assertNotSame(keyStoreOptions, options.getKeyCertOptions());
    assertEquals(ksPassword, ((JksOptions) options.getKeyCertOptions()).getPassword());
    assertEquals(ksPath, ((JksOptions) options.getKeyCertOptions()).getPath());
    assertNotSame(trustStoreOptions, options.getTrustOptions());
    assertEquals(tsPassword, ((JksOptions) options.getTrustOptions()).getPassword());
    assertEquals(tsPath, ((JksOptions) options.getTrustOptions()).getPath());
    assertEquals(1, options.getEnabledCipherSuites().size());
    assertTrue(options.getEnabledCipherSuites().contains(enabledCipher));
    assertEquals(connectTimeout, options.getConnectTimeout());
    assertEquals(trustAll, options.isTrustAll());
    assertEquals(1, options.getCrlPaths().size());
    assertEquals(crlPath, options.getCrlPaths().get(0));
    assertEquals(reconnectAttempts, options.getReconnectAttempts());
    assertEquals(reconnectInterval, options.getReconnectInterval());
    assertEquals(useAlpn, options.isUseAlpn());
    switch (sslEngine) {
      case "jdkSslEngineOptions":
        assertTrue(options.getSslEngineOptions() instanceof JdkSSLEngineOptions);
        break;
      case "openSslEngineOptions":
        assertTrue(options.getSslEngineOptions() instanceof OpenSSLEngineOptions);
        break;
      default:
        fail();
        break;
    }
    assertEquals(hostnameVerificationAlgorithm, options.getHostnameVerificationAlgorithm());

    // Test other keystore/truststore types
    json.remove("keyStoreOptions");
    json.remove("trustStoreOptions");
    json.put("pfxKeyCertOptions", new JsonObject().put("password", ksPassword))
      .put("pfxTrustOptions", new JsonObject().put("password", tsPassword));
    options = new NetClientOptions(json);
    assertTrue(options.getTrustOptions() instanceof PfxOptions);
    assertTrue(options.getKeyCertOptions() instanceof PfxOptions);

    json.remove("pfxKeyCertOptions");
    json.remove("pfxTrustOptions");
    json.put("pemKeyCertOptions", new JsonObject())
      .put("pemTrustOptions", new JsonObject());
    options = new NetClientOptions(json);
    assertTrue(options.getTrustOptions() instanceof PemTrustOptions);
    assertTrue(options.getKeyCertOptions() instanceof PemKeyCertOptions);
  }

  @Test
  public void testCopyServerOptions() {
    NetServerOptions options = new NetServerOptions();
    int sendBufferSize = TestUtils.randomPositiveInt();
    int receiverBufferSize = TestUtils.randomPortInt();
    Random rand = new Random();
    boolean reuseAddress = rand.nextBoolean();
    int trafficClass = TestUtils.randomByte() + 128;
    boolean tcpNoDelay = rand.nextBoolean();
    boolean tcpKeepAlive = rand.nextBoolean();
    int soLinger = TestUtils.randomPositiveInt();
    boolean usePooledBuffers = rand.nextBoolean();
    int idleTimeout = TestUtils.randomPositiveInt();
    boolean ssl = rand.nextBoolean();
    JksOptions keyStoreOptions = new JksOptions();
    String ksPassword = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPassword(ksPassword);
    JksOptions trustStoreOptions = new JksOptions();
    String tsPassword = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPassword(tsPassword);
    String enabledCipher = TestUtils.randomAlphaString(100);
    String crlPath = TestUtils.randomUnicodeString(100);
    Buffer crlValue = TestUtils.randomBuffer(100);
    int port = 1234;
    String host = TestUtils.randomAlphaString(100);
    int acceptBacklog = TestUtils.randomPortInt();
    boolean useAlpn = TestUtils.randomBoolean();
    boolean openSslSessionCacheEnabled = rand.nextBoolean();
    SSLEngineOptions sslEngine = TestUtils.randomBoolean() ? new JdkSSLEngineOptions() : new OpenSSLEngineOptions();
    boolean sni = TestUtils.randomBoolean();
    long sslHandshakeTimeout = TestUtils.randomPositiveLong();

    options.setSendBufferSize(sendBufferSize);
    options.setReceiveBufferSize(receiverBufferSize);
    options.setReuseAddress(reuseAddress);
    options.setTrafficClass(trafficClass);
    options.setTcpNoDelay(tcpNoDelay);
    options.setTcpKeepAlive(tcpKeepAlive);
    options.setSoLinger(soLinger);
    options.setIdleTimeout(idleTimeout);
    options.setSsl(ssl);
    options.setKeyStoreOptions(keyStoreOptions);
    options.setTrustStoreOptions(trustStoreOptions);
    options.addEnabledCipherSuite(enabledCipher);
    options.addCrlPath(crlPath);
    options.addCrlValue(crlValue);
    options.setPort(port);
    options.setHost(host);
    options.setAcceptBacklog(acceptBacklog);
    options.setUseAlpn(useAlpn);
    options.setSslEngineOptions(sslEngine);
    options.setSni(sni);
    options.setSslHandshakeTimeout(sslHandshakeTimeout);

    NetServerOptions copy = new NetServerOptions(options);
    assertEquals(options.toJson(), copy.toJson());
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testDefaultServerOptionsJson() {
    NetServerOptions def = new NetServerOptions();
    NetServerOptions json = new NetServerOptions(new JsonObject());
    assertEquals(def.getCrlPaths(), json.getCrlPaths());
    assertEquals(def.getCrlValues(), json.getCrlValues());
    assertEquals(def.getAcceptBacklog(), json.getAcceptBacklog());
    assertEquals(def.getPort(), json.getPort());
    assertEquals(def.getHost(), json.getHost());
    assertEquals(def.getCrlPaths(), json.getCrlPaths());
    assertEquals(def.getCrlValues(), json.getCrlValues());
    assertEquals(def.getAcceptBacklog(), json.getAcceptBacklog());
    assertEquals(def.getPort(), json.getPort());
    assertEquals(def.getHost(), json.getHost());
    assertEquals(def.isTcpNoDelay(), json.isTcpNoDelay());
    assertEquals(def.isTcpKeepAlive(), json.isTcpKeepAlive());
    assertEquals(def.getSoLinger(), json.getSoLinger());
    assertEquals(def.isSsl(), json.isSsl());
    assertEquals(def.isUseAlpn(), json.isUseAlpn());
    assertEquals(def.getSslEngineOptions(), json.getSslEngineOptions());
    assertEquals(def.isSni(), json.isSni());
    assertEquals(def.getSslHandshakeTimeout(), json.getSslHandshakeTimeout());
  }

  @Test
  public void testServerOptionsJson() {
    int sendBufferSize = TestUtils.randomPositiveInt();
    int receiverBufferSize = TestUtils.randomPortInt();
    Random rand = new Random();
    boolean reuseAddress = rand.nextBoolean();
    int trafficClass = TestUtils.randomByte() + 128;
    boolean tcpNoDelay = rand.nextBoolean();
    boolean tcpKeepAlive = rand.nextBoolean();
    int soLinger = TestUtils.randomPositiveInt();
    boolean usePooledBuffers = rand.nextBoolean();
    int idleTimeout = TestUtils.randomPositiveInt();
    boolean ssl = rand.nextBoolean();
    JksOptions keyStoreOptions = new JksOptions();
    String ksPassword = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPassword(ksPassword);
    String ksPath = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPath(ksPath);
    JksOptions trustStoreOptions = new JksOptions();
    String tsPassword = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPassword(tsPassword);
    String tsPath = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPath(tsPath);
    String enabledCipher = TestUtils.randomAlphaString(100);
    String crlPath = TestUtils.randomUnicodeString(100);
    int port = 1234;
    String host = TestUtils.randomAlphaString(100);
    int acceptBacklog = TestUtils.randomPortInt();
    boolean useAlpn = TestUtils.randomBoolean();
    boolean openSslSessionCacheEnabled = rand.nextBoolean();
    String sslEngine = TestUtils.randomBoolean() ? "jdkSslEngineOptions" : "openSslEngineOptions";
    boolean sni = TestUtils.randomBoolean();
    long sslHandshakeTimeout = TestUtils.randomPositiveLong();

    JsonObject json = new JsonObject();
    json.put("sendBufferSize", sendBufferSize)
      .put("receiveBufferSize", receiverBufferSize)
      .put("reuseAddress", reuseAddress)
      .put("trafficClass", trafficClass)
      .put("tcpNoDelay", tcpNoDelay)
      .put("tcpKeepAlive", tcpKeepAlive)
      .put("soLinger", soLinger)
      .put("usePooledBuffers", usePooledBuffers)
      .put("idleTimeout", idleTimeout)
      .put("ssl", ssl)
      .put("enabledCipherSuites", new JsonArray().add(enabledCipher))
      .put("crlPaths", new JsonArray().add(crlPath))
      .put("keyStoreOptions", new JsonObject().put("password", ksPassword).put("path", ksPath))
      .put("trustStoreOptions", new JsonObject().put("password", tsPassword).put("path", tsPath))
      .put("port", port)
      .put("host", host)
      .put("acceptBacklog", acceptBacklog)
      .put("useAlpn", useAlpn)
      .put(sslEngine, new JsonObject())
      .put("openSslSessionCacheEnabled", openSslSessionCacheEnabled)
      .put("sni", sni)
      .put("sslHandshakeTimeout", sslHandshakeTimeout);

    NetServerOptions options = new NetServerOptions(json);
    assertEquals(sendBufferSize, options.getSendBufferSize());
    assertEquals(receiverBufferSize, options.getReceiveBufferSize());
    assertEquals(reuseAddress, options.isReuseAddress());
    assertEquals(trafficClass, options.getTrafficClass());
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(tcpNoDelay, options.isTcpNoDelay());
    assertEquals(soLinger, options.getSoLinger());
    assertEquals(idleTimeout, options.getIdleTimeout());
    assertEquals(ssl, options.isSsl());
    assertEquals(sslHandshakeTimeout, options.getSslHandshakeTimeout());
    assertNotSame(keyStoreOptions, options.getKeyCertOptions());
    assertEquals(ksPassword, ((JksOptions) options.getKeyCertOptions()).getPassword());
    assertEquals(ksPath, ((JksOptions) options.getKeyCertOptions()).getPath());
    assertNotSame(trustStoreOptions, options.getTrustOptions());
    assertEquals(tsPassword, ((JksOptions) options.getTrustOptions()).getPassword());
    assertEquals(tsPath, ((JksOptions) options.getTrustOptions()).getPath());
    assertEquals(1, options.getEnabledCipherSuites().size());
    assertTrue(options.getEnabledCipherSuites().contains(enabledCipher));
    assertEquals(1, options.getCrlPaths().size());
    assertEquals(crlPath, options.getCrlPaths().get(0));
    assertEquals(port, options.getPort());
    assertEquals(host, options.getHost());
    assertEquals(acceptBacklog, options.getAcceptBacklog());
    assertEquals(useAlpn, options.isUseAlpn());
    switch (sslEngine) {
      case "jdkSslEngineOptions":
        assertTrue(options.getSslEngineOptions() instanceof JdkSSLEngineOptions);
        break;
      case "openSslEngineOptions":
        assertTrue(options.getSslEngineOptions() instanceof OpenSSLEngineOptions);
        break;
      default:
        fail();
        break;
    }
    assertEquals(sni, options.isSni());

    // Test other keystore/truststore types
    json.remove("keyStoreOptions");
    json.remove("trustStoreOptions");
    json.put("pfxKeyCertOptions", new JsonObject().put("password", ksPassword))
      .put("pfxTrustOptions", new JsonObject().put("password", tsPassword));
    options = new NetServerOptions(json);
    assertTrue(options.getTrustOptions() instanceof PfxOptions);
    assertTrue(options.getKeyCertOptions() instanceof PfxOptions);

    json.remove("pfxKeyCertOptions");
    json.remove("pfxTrustOptions");
    json.put("pemKeyCertOptions", new JsonObject())
      .put("pemTrustOptions", new JsonObject());
    options = new NetServerOptions(json);
    assertTrue(options.getTrustOptions() instanceof PemTrustOptions);
    assertTrue(options.getKeyCertOptions() instanceof PemKeyCertOptions);
  }

  @Test
  public void testWriteHandlerSuccess() throws Exception {
    CompletableFuture<Void> close = new CompletableFuture<>();
    server.connectHandler(socket -> {
      socket.pause();
      close.thenAccept(v -> {
        socket.resume();
      });
    });
    startServer();
    client.connect(testAddress, onSuccess(so -> {
      writeUntilFull(so, v -> {
        so.write(Buffer.buffer("lost buffer"), onSuccess(ack -> testComplete()));
        close.complete(null);
      });
    }));
    await();
  }

  @Test
  public void testWriteHandlerFailure() throws Exception {
    CompletableFuture<Void> close = new CompletableFuture<>();
    server.connectHandler(socket -> {
      socket.pause();
      close.thenAccept(v -> {
        socket.close();
      });
    });
    startServer();
    client.connect(testAddress, onSuccess(so -> {
      writeUntilFull(so, v -> {
        so.write(Buffer.buffer("lost buffer"), onFailure(err -> {
          testComplete();
        }));
        close.complete(null);
      });
    }));
    await();
  }

  private void writeUntilFull(NetSocket so, Handler<Void> handler) {
    if (so.writeQueueFull()) {
      handler.handle(null);
    } else {
      // Give enough time to report a proper full
      so.write(TestUtils.randomBuffer(16384));
      vertx.setTimer(10, id -> writeUntilFull(so, handler));
    }
  }

  @Test
  public void testEchoBytes() {
    Buffer sent = TestUtils.randomBuffer(100);
    testEcho(sock -> sock.write(sent), buff -> assertEquals(sent, buff), sent.length());
  }

  @Test
  public void testEchoString() {
    String sent = TestUtils.randomUnicodeString(100);
    Buffer buffSent = Buffer.buffer(sent);
    testEcho(sock -> sock.write(sent), buff -> assertEquals(buffSent, buff), buffSent.length());
  }

  @Test
  public void testEchoStringUTF8() {
    testEchoStringWithEncoding("UTF-8");
  }

  @Test
  public void testEchoStringUTF16() {
    testEchoStringWithEncoding("UTF-16");
  }

  void testEchoStringWithEncoding(String encoding) {
    String sent = TestUtils.randomUnicodeString(100);
    Buffer buffSent = Buffer.buffer(sent, encoding);
    testEcho(sock -> sock.write(sent, encoding), buff -> assertEquals(buffSent, buff), buffSent.length());
  }

  void testEcho(Consumer<NetSocket> writer, Consumer<Buffer> dataChecker, int length) {
    Handler<AsyncResult<NetSocket>> clientHandler = (asyncResult) -> {
      if (asyncResult.succeeded()) {
        NetSocket sock = asyncResult.result();
        Buffer buff = Buffer.buffer();
        sock.handler((buffer) -> {
          buff.appendBuffer(buffer);
          if (buff.length() == length) {
            dataChecker.accept(buff);
            testComplete();
          }
          if (buff.length() > length) {
            fail("Too many bytes received");
          }
        });
        writer.accept(sock);
      } else {
        fail("failed to connect");
      }
    };
    startEchoServer(testAddress, s -> client.connect(testAddress, clientHandler));
    await();
  }

  void startEchoServer(SocketAddress address, Handler<AsyncResult<NetServer>> listenHandler) {
    Handler<NetSocket> serverHandler = socket -> socket.handler(socket::write);
    server.connectHandler(serverHandler).listen(address, listenHandler);
  }

  @Test
  public void testConnectLocalHost() {
    connect(testAddress);
  }

  void connect(SocketAddress address) {
    startEchoServer(testAddress, s -> {
      final int numConnections = 100;
      final AtomicInteger connCount = new AtomicInteger(0);
      for (int i = 0; i < numConnections; i++) {
        Handler<AsyncResult<NetSocket>> handler = res -> {
          if (res.succeeded()) {
            res.result().close();
            if (connCount.incrementAndGet() == numConnections) {
              testComplete();
            }
          }
        };
        client.connect(address, handler);
      }
    });
    await();
  }

  @Test
  public void testConnectInvalidPort() {
    assertIllegalArgumentException(() -> client.connect(-1, "localhost", res -> {}));
    assertIllegalArgumentException(() -> client.connect(65536, "localhost", res -> {}));
    client.connect(9998, "localhost", res -> {
      assertTrue(res.failed());
      assertFalse(res.succeeded());
      assertNotNull(res.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testConnectInvalidHost() {
    assertNullPointerException(() -> client.connect(80, null, res -> {}));
    client.connect(1234, "127.0.0.2", res -> {
      assertTrue(res.failed());
      assertFalse(res.succeeded());
      assertNotNull(res.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testConnectInvalidConnectHandler() throws Exception {
    assertNullPointerException(() -> client.connect(80, "localhost", (Handler<AsyncResult<NetSocket>>) null));
  }

  @Test
  public void testListenInvalidPort() {
    final int port = 9090;
    final HttpServer httpServer = vertx.createHttpServer();
    try {
      httpServer.requestHandler(ignore -> {})
        .listen(port, onSuccess(s ->
          vertx.createNetServer()
            .connectHandler(ignore -> {})
            .listen(port, onFailure(error -> {
              assertNotNull(error);
              testComplete();
            }))));
      await();
    } finally {
      httpServer.close();
    }
  }

  @Test
  public void testListenInvalidHost() {
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setPort(1234).setHost("uhqwduhqwudhqwuidhqwiudhqwudqwiuhd"));
    server.connectHandler(netSocket -> {
    }).listen(ar -> {
      assertTrue(ar.failed());
      assertFalse(ar.succeeded());
      assertNotNull(ar.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenOnWildcardPort() {
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setPort(0));
    server.connectHandler((netSocket) -> {
    }).listen(ar -> {
      assertFalse(ar.failed());
      assertTrue(ar.succeeded());
      assertNull(ar.cause());
      assertTrue(server.actualPort() > 1024);
      assertEquals(server, ar.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testClientCloseHandlersCloseFromClient() {
    startEchoServer(testAddress, s -> clientCloseHandlers(true));
    await();
  }

  @Test
  public void testClientCloseHandlersCloseFromServer() {
    server.connectHandler(NetSocket::close).listen(testAddress, (s) -> clientCloseHandlers(false));
    await();
  }

  void clientCloseHandlers(boolean closeFromClient) {
    client.connect(testAddress, onSuccess(so -> {
      AtomicInteger counter = new AtomicInteger(0);
      so.endHandler(v -> assertEquals(1, counter.incrementAndGet()));
      so.closeHandler(v -> {
        assertEquals(2, counter.incrementAndGet());
        testComplete();
      });
      if (closeFromClient) {
        so.close();
      }
    }));
  }

  @Test
  public void testServerCloseHandlersCloseFromClient() {
    serverCloseHandlers(false, s -> client.connect(testAddress, ar -> ar.result().close()));
    await();
  }

  @Test
  public void testServerCloseHandlersCloseFromServer() {
    serverCloseHandlers(true, s -> client.connect(testAddress, ar -> {}));
    await();
  }

  void serverCloseHandlers(boolean closeFromServer, Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler((sock) -> {
      AtomicInteger counter = new AtomicInteger(0);
      sock.endHandler(v -> assertEquals(1, counter.incrementAndGet()));
      sock.closeHandler(v -> {
        assertEquals(2, counter.incrementAndGet());
        testComplete();
      });
      if (closeFromServer) {
        sock.close();
      }
    }).listen(testAddress, listenHandler);
  }

  @Test
  public void testClientDrainHandler() {
    pausingServer((s) -> {
      client.connect(testAddress, onSuccess(sock -> {
        assertFalse(sock.writeQueueFull());
        sock.setWriteQueueMaxSize(1000);
        Buffer buff = TestUtils.randomBuffer(10000);
        vertx.setPeriodic(1, id -> {
          sock.write(buff.copy());
          if (sock.writeQueueFull()) {
            vertx.cancelTimer(id);
            sock.drainHandler(v -> {
              assertFalse(sock.writeQueueFull());
              testComplete();
            });
            // Tell the server to resume
            vertx.eventBus().send("server_resume", "");
          }
        });
      }));
    });
    await();
  }

  void pausingServer(Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler(sock -> {
      sock.pause();
      Handler<Message<Buffer>> resumeHandler = (m) -> sock.resume();
      MessageConsumer reg = vertx.eventBus().<Buffer>consumer("server_resume").handler(resumeHandler);
      sock.closeHandler(v -> reg.unregister());
    }).listen(testAddress, listenHandler);
  }

  @Test
  public void testServerDrainHandler() {
    drainingServer(s -> {
      client.connect(testAddress, onSuccess(sock -> {
        sock.pause();
        setHandlers(sock);
        sock.handler(buf -> {});
      }));
    });
    await();
  }

  void setHandlers(NetSocket sock) {
    Handler<Message<Buffer>> resumeHandler = m -> sock.resume();
    MessageConsumer reg = vertx.eventBus().<Buffer>consumer("client_resume").handler(resumeHandler);
    sock.closeHandler(v -> reg.unregister());
  }

  void drainingServer(Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler(sock -> {
      assertFalse(sock.writeQueueFull());
      sock.setWriteQueueMaxSize(1000);

      Buffer buff = TestUtils.randomBuffer(10000);
      //Send data until the buffer is full
      vertx.setPeriodic(1, id -> {
        sock.write(buff.copy());
        if (sock.writeQueueFull()) {
          vertx.cancelTimer(id);
          sock.drainHandler(v -> {
            assertFalse(sock.writeQueueFull());
            // End test after a short delay to give the client some time to read the data
            vertx.setTimer(100, id2 -> testComplete());
          });

          // Tell the client to resume
          vertx.eventBus().send("client_resume", "");
        }
      });
    }).listen(testAddress, listenHandler);
  }

  @Test
  public void testReconnectAttemptsInfinite() {
    reconnectAttempts(-1);
  }

  @Test
  public void testReconnectAttemptsMany() {
    reconnectAttempts(100000);
  }

  private void reconnectAttempts(int attempts) {
    client.close();
    client = vertx.createNetClient(new NetClientOptions().setReconnectAttempts(attempts).setReconnectInterval(10));

    //The server delays starting for a a few seconds, but it should still connect
    client.connect(testAddress, onSuccess(so -> testComplete()));

    // Start the server after a delay
    vertx.setTimer(2000, id -> startEchoServer(testAddress, s -> {}));

    await();
  }

  @Test
  public void testReconnectAttemptsNotEnough() {
    client.close();
    client = vertx.createNetClient(new NetClientOptions().setReconnectAttempts(100).setReconnectInterval(10));

    client.connect(testAddress, (res) -> {
      assertFalse(res.succeeded());
      assertTrue(res.failed());
      testComplete();
    });

    await();
  }

  @Test
  public void testServerIdleTimeout() {
    server.close();
    NetServerOptions netServerOptions = new NetServerOptions();
    netServerOptions.setIdleTimeout(1000);
    netServerOptions.setIdleTimeoutUnit(TimeUnit.MILLISECONDS);
    server = vertx.createNetServer(netServerOptions);
    server.connectHandler(s -> {}).listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      client.connect(testAddress, res -> {
        assertTrue(res.succeeded());
        NetSocket socket = res.result();
        socket.closeHandler(v -> testComplete());
      });
    });
    await();
  }

  @Test
  public void testClientIdleTimeout() {
    client.close();
    NetClientOptions netClientOptions = new NetClientOptions();
    netClientOptions.setIdleTimeout(1000);
    netClientOptions.setIdleTimeoutUnit(TimeUnit.MILLISECONDS);
    client = vertx.createNetClient(netClientOptions);

    server.connectHandler(s -> {
    }).listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      client.connect(testAddress, res -> {
        assertTrue(res.succeeded());
        NetSocket socket = res.result();
        socket.closeHandler(v -> testComplete());
      });
    });
    await();
  }

  @Test
  // StartTLS
  public void testStartTLSClientTrustAll() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, true, true);
  }

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, true, false);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE, false, false, true, false);
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, false, false);
  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, false, false, true, false);
  }

  @Test
  //Client specifies cert and it's not required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, true, false);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false);
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE, true, false, false, false);
  }

  @Test
  // StartTLS client specifies cert but it's not trusted
  public void testStartTLSClientCertClientNotTrusted() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, true);
  }

  @Test
  // Specify some cipher suites
  public void testTLSCipherSuites() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, true, false, ENABLED_CIPHER_SUITES);
  }

  @Test
  // Specify some bogus protocol
  public void testInvalidTlsProtocolVersion() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, false, false, new String[0],
    new String[]{"TLSv1.999"});
  }

  @Test
  // Specify a valid protocol
  public void testSpecificTlsProtocolVersion() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, true, false, new String[0],
        new String[]{"TLSv1.2"});
  }

  @Test
  public void testTLSTrailingDotHost() throws Exception {
    // We just need a vanilla cert for this test
    SelfSignedCertificate cert = SelfSignedCertificate.create("host2.com");
    TLSTest test = new TLSTest()
      .clientTrust(cert::trustOptions)
      .connectAddress(SocketAddress.inetSocketAddress(4043, "host2.com."))
      .bindAddress(SocketAddress.inetSocketAddress(4043, "host2.com"))
      .serverCert(cert::keyCertOptions);
    test.run(true);
    await();
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
    assertNull(test.indicatedServerName);
  }

  @Test
  // SNI without server name should use the first keystore entry
  public void testSniWithoutServerNameUsesTheFirstKeyStoreEntry1() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SERVER_JKS)
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    await();
    assertEquals("localhost", cnOf(test.clientPeerCert()));
  }

  @Test
  // SNI without server name should use the first keystore entry
  public void testSniWithoutServerNameUsesTheFirstKeyStoreEntry2() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SNI_JKS_HOST1)
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(false);
    await();
  }

  @Test
  public void testSniImplicitServerName() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SNI_JKS_HOST2)
        .address(SocketAddress.inetSocketAddress(4043, "host2.com"))
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    await();
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
  }

  @Test
  public void testSniImplicitServerNameDisabledForShortname1() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SNI_JKS_HOST1)
        .address(SocketAddress.inetSocketAddress(4043, "host1"))
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(false);
    await();
  }

  @Test
  public void testSniImplicitServerNameDisabledForShortname2() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SERVER_JKS)
        .address(SocketAddress.inetSocketAddress(4043, "host1"))
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    await();
    assertEquals("localhost", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testSniForceShortname() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SNI_JKS_HOST1)
        .address(SocketAddress.inetSocketAddress(4043, "host1"))
        .serverName("host1")
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    await();
    assertEquals("host1", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testSniOverrideServerName() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SNI_JKS_HOST2)
        .address(SocketAddress.inetSocketAddress(4043, "example.com"))
        .serverName("host2.com")
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    await();
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
  }

  @Test
  // SNI present an unknown server
  public void testSniWithUnknownServer1() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SERVER_JKS)
        .serverCert(Cert.SNI_JKS).sni(true).serverName("unknown");
    test.run(true);
    await();
    assertEquals("localhost", cnOf(test.clientPeerCert()));
  }

  @Test
  // SNI present an unknown server
  public void testSniWithUnknownServer2() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SNI_JKS_HOST2)
        .serverCert(Cert.SNI_JKS).sni(true).serverName("unknown");
    test.run(false);
    await();
  }

  @Test
  // SNI returns the certificate for the indicated server name
  public void testSniWithServerNameStartTLS() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SNI_JKS_HOST1)
        .startTLS(true)
        .serverCert(Cert.SNI_JKS).sni(true).serverName("host1");
    test.run(true);
    await();
    assertEquals("host1", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testSniWithServerNameTrust(){
    TLSTest test = new TLSTest().clientTrust(Trust.SNI_JKS_HOST2)
        .clientCert(Cert.CLIENT_PEM_ROOT_CA)
        .requireClientAuth(true)
        .serverCert(Cert.SNI_JKS)
        .sni(true)
        .serverName("host2.com")
        .serverTrust(Trust.SNI_SERVER_ROOT_CA_AND_OTHER_CA_1);
    test.run(true);
    await();
  }

  @Test
  public void testSniWithServerNameTrustFallback(){
    TLSTest test = new TLSTest().clientTrust(Trust.SNI_JKS_HOST2)
        .clientCert(Cert.CLIENT_PEM_ROOT_CA)
        .requireClientAuth(true)
        .serverCert(Cert.SNI_JKS)
        .sni(true)
        .serverName("host2.com")
        .serverTrust(Trust.SNI_SERVER_ROOT_CA_FALLBACK);
    test.run(true);
    await();
  }

  @Test
  public void testSniWithServerNameTrustFallbackFail(){
    TLSTest test = new TLSTest().clientTrust(Trust.SNI_JKS_HOST2)
        .clientCert(Cert.CLIENT_PEM_ROOT_CA)
        .requireClientAuth(true)
        .serverCert(Cert.SNI_JKS)
        .sni(true)
        .serverName("host2.com")
        .serverTrust(Trust.SNI_SERVER_OTHER_CA_FALLBACK);
    test.run(false);
    await();
  }

  @Test
  public void testSniWithServerNameTrustFail(){
    TLSTest test = new TLSTest().clientTrust(Trust.SNI_JKS_HOST2)
        .clientCert(Cert.CLIENT_PEM_ROOT_CA)
        .requireClientAuth(true)
        .serverCert(Cert.SNI_JKS)
        .sni(true)
        .serverName("host2.com")
        .serverTrust(Trust.SNI_SERVER_ROOT_CA_AND_OTHER_CA_2);
    test.run(false);
    await();
  }

  @Test
  public void testSniWithTrailingDotHost() throws Exception {
    TLSTest test = new TLSTest()
      .clientTrust(Trust.SNI_JKS_HOST2)
      .connectAddress(SocketAddress.inetSocketAddress(4043, "host2.com."))
      .bindAddress(SocketAddress.inetSocketAddress(4043, "host2.com"))
      .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    await();
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
  }

  void testTLS(Cert<?> clientCert, Trust<?> clientTrust,
               Cert<?> serverCert, Trust<?> serverTrust,
    boolean requireClientAuth, boolean clientTrustAll,
    boolean shouldPass, boolean startTLS) throws Exception {
        testTLS(clientCert, clientTrust, serverCert, serverTrust, requireClientAuth, clientTrustAll,
        shouldPass, startTLS, new String[0], new String[0]);
  }

  void testTLS(Cert<?> clientCert, Trust<?> clientTrust,
               Cert<?> serverCert, Trust<?> serverTrust,
    boolean requireClientAuth, boolean clientTrustAll,
    boolean shouldPass, boolean startTLS,
    String[] enabledCipherSuites) throws Exception {
        testTLS(clientCert, clientTrust, serverCert, serverTrust, requireClientAuth, clientTrustAll,
        shouldPass, startTLS, enabledCipherSuites, new String[0]);
    }

  void testTLS(Cert<?> clientCert, Trust<?> clientTrust,
               Cert<?> serverCert, Trust<?> serverTrust,
               boolean requireClientAuth, boolean clientTrustAll,
               boolean shouldPass, boolean startTLS,
               String[] enabledCipherSuites,
               String[] enabledSecureTransportProtocols) throws Exception {
    TLSTest test = new TLSTest()
        .clientCert(clientCert)
        .clientTrust(clientTrust)
        .serverCert(serverCert)
        .serverTrust(serverTrust)
        .requireClientAuth(requireClientAuth)
        .clientTrustAll(clientTrustAll)
        .startTLS(startTLS)
        .enabledCipherSuites(enabledCipherSuites)
        .enabledSecureTransportProtocols(enabledSecureTransportProtocols);
    test.run(shouldPass);
    await();
  }

  class TLSTest {

    Cert<?> clientCert = Cert.NONE;
    Trust<?> clientTrust = Trust.NONE;
    Cert<?> serverCert = Cert.NONE;
    Trust<?> serverTrust = Trust.NONE;
    boolean requireClientAuth;
    boolean clientTrustAll;
    boolean startTLS;
    String[] enabledCipherSuites = new String[0];
    String[] enabledSecureTransportProtocols = new String[0];
    boolean sni;
    SocketAddress bindAddress = SocketAddress.inetSocketAddress(4043, "localhost");
    SocketAddress connectAddress = bindAddress;
    String serverName;
    X509Certificate clientPeerCert;
    String indicatedServerName;

    public TLSTest clientCert(Cert<?> clientCert) {
      this.clientCert = clientCert;
      return this;
    }

    public TLSTest clientTrust(Trust<?> clientTrust) {
      this.clientTrust = clientTrust;
      return this;
    }

    public TLSTest serverCert(Cert<?> serverCert) {
      this.serverCert = serverCert;
      return this;
    }

    public TLSTest serverTrust(Trust<?> serverTrust) {
      this.serverTrust = serverTrust;
      return this;
    }

    public TLSTest requireClientAuth(boolean requireClientAuth) {
      this.requireClientAuth = requireClientAuth;
      return this;
    }

    public TLSTest clientTrustAll(boolean clientTrustAll) {
      this.clientTrustAll = clientTrustAll;
      return this;
    }

    public TLSTest startTLS(boolean startTLS) {
      this.startTLS = startTLS;
      return this;
    }

    public TLSTest enabledCipherSuites(String[] enabledCipherSuites) {
      this.enabledCipherSuites = enabledCipherSuites;
      return this;
    }

    public TLSTest enabledSecureTransportProtocols(String[] enabledSecureTransportProtocols) {
      this.enabledSecureTransportProtocols = enabledSecureTransportProtocols;
      return this;
    }

    public TLSTest address(SocketAddress address) {
      this.bindAddress = address;
      this.connectAddress = address;
      return this;
    }

    public TLSTest bindAddress(SocketAddress address) {
      this.bindAddress = address;
      return this;
    }

    public TLSTest connectAddress(SocketAddress address) {
      this.connectAddress = address;
      return this;
    }

    public TLSTest serverName(String serverName) {
      this.serverName = serverName;
      return this;
    }

    public TLSTest sni(boolean sni) {
      this.sni = sni;
      return this;
    }

    public X509Certificate clientPeerCert() {
      return clientPeerCert;
    }

    void run(boolean shouldPass) {
      server.close();
      NetServerOptions options = new NetServerOptions();
      if (!startTLS) {
        options.setSsl(true);
      }
      options.setTrustOptions(serverTrust.get());
      options.setKeyCertOptions(serverCert.get());
      if (requireClientAuth) {
        options.setClientAuth(ClientAuth.REQUIRED);
      }
      for (String suite: enabledCipherSuites) {
        options.addEnabledCipherSuite(suite);
      }
      if(enabledSecureTransportProtocols.length > 0) {
        options.getEnabledSecureTransportProtocols().forEach(options::removeEnabledSecureTransportProtocol);
      }
      for (String protocol : enabledSecureTransportProtocols) {
        options.addEnabledSecureTransportProtocol(protocol);
      }
      options.setSni(sni);

      Consumer<NetSocket> certificateChainChecker = socket -> {
        try {
          X509Certificate[] certs = socket.peerCertificateChain();
          if (clientCert != Cert.NONE) {
            assertNotNull(certs);
            assertEquals(1, certs.length);
          } else {
            assertNull(certs);
          }
        } catch (SSLPeerUnverifiedException e) {
          assertTrue(clientTrust.get() != Trust.NONE || clientTrustAll);
        }
      };

      server = vertx.createNetServer(options);
      if (!shouldPass) {
        waitForMore(1);
      }
      server.exceptionHandler(err -> complete());
      Handler<NetSocket> serverHandler = socket -> {
        indicatedServerName = socket.indicatedServerName();
        SSLSession sslSession = socket.sslSession();
        if (socket.isSsl()) {
          assertNotNull(sslSession);
          certificateChainChecker.accept(socket);
        } else {
          assertNull(sslSession);
        }
        AtomicBoolean upgradedServer = new AtomicBoolean();
        AtomicInteger upgradedServerCount = new AtomicInteger();
        socket.handler(buff -> {
          socket.write(buff); // echo the data
          if (startTLS) {
            if (upgradedServer.compareAndSet(false, true)) {
              indicatedServerName = socket.indicatedServerName();
              assertFalse(socket.isSsl());
              Context ctx = Vertx.currentContext();
              socket.upgradeToSsl(ar -> {
                assertSame(ctx, Vertx.currentContext());
                assertEquals(shouldPass, ar.succeeded());
                if (ar.succeeded()) {
                  certificateChainChecker.accept(socket);
                  upgradedServerCount.incrementAndGet();
                  assertTrue(socket.isSsl());
                } else {
                  complete();
                }
              });
            } else {
              assertTrue(socket.isSsl());
              assertEquals(1, upgradedServerCount.get());
            }
          } else {
            assertTrue(socket.isSsl());
          }
        });
      };
      server.connectHandler(serverHandler).listen(bindAddress, onSuccess(ar -> {
        client.close();
        NetClientOptions clientOptions = new NetClientOptions();
        if (!startTLS) {
          clientOptions.setSsl(true);
        }
        if (clientTrustAll) {
          clientOptions.setTrustAll(true);
        }
        clientOptions.setTrustOptions(clientTrust.get());
        clientOptions.setKeyCertOptions(clientCert.get());
        for (String suite: enabledCipherSuites) {
          clientOptions.addEnabledCipherSuite(suite);
        }
        if(enabledSecureTransportProtocols.length > 0) {
          clientOptions.getEnabledSecureTransportProtocols().forEach(clientOptions::removeEnabledSecureTransportProtocol);
        }
        for (String protocol : enabledSecureTransportProtocols) {
          clientOptions.addEnabledSecureTransportProtocol(protocol);
        }
        client = vertx.createNetClient(clientOptions);
        client.connect(connectAddress, serverName, ar2 -> {
          if (ar2.succeeded()) {
            if (!startTLS && !shouldPass) {
              fail("Should not connect");
              return;
            }
            final int numChunks = 100;
            final int chunkSize = 100;
            final List<Buffer> toSend = new ArrayList<>();
            final Buffer expected = Buffer.buffer();
            for (int i = 0; i< numChunks;i++) {
              Buffer chunk = TestUtils.randomBuffer(chunkSize);
              toSend.add(chunk);
              expected.appendBuffer(chunk);
            }
            final Buffer received = Buffer.buffer();
            final NetSocket socket = ar2.result();

            if (socket.isSsl()) {
              try {
                clientPeerCert = socket.peerCertificateChain()[0];
              } catch (SSLPeerUnverifiedException ignore) {
              }
            }

            final AtomicBoolean upgradedClient = new AtomicBoolean();
            socket.handler(buffer -> {
              received.appendBuffer(buffer);
              if (received.length() == expected.length()) {
                assertEquals(expected, received);
                complete();
              }
              if (startTLS && !upgradedClient.get()) {
                upgradedClient.set(true);
                assertFalse(socket.isSsl());
                Handler<AsyncResult<Void>> handler;
                if (shouldPass) {
                  handler = onSuccess(v -> {
                    assertTrue(socket.isSsl());
                    try {
                      clientPeerCert = socket.peerCertificateChain()[0];
                    } catch (SSLPeerUnverifiedException ignore) {
                    }
                    // Now send the rest
                    for (int i = 1; i < numChunks; i++) {
                      socket.write(toSend.get(i));
                    }
                  });
                } else {
                  handler = onFailure(err -> complete());
                }
                if (serverName != null) {
                  socket.upgradeToSsl(serverName, handler);
                } else {
                  socket.upgradeToSsl(handler);
                }
              } else {
                assertTrue(socket.isSsl());
              }
            });

            //Now send some data
            int numToSend = startTLS ? 1 : numChunks;
            for (int i = 0; i < numToSend; i++) {
              socket.write(toSend.get(i));
            }
          } else {
            if (shouldPass) {
              ar2.cause().printStackTrace();
              fail("Should not fail to connect");
            } else {
              complete();
            }
          }
        });
      }));
    }
  }

  @Test
  public void testListenDomainSocketAddress() throws Exception {
    Vertx vx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    Assume.assumeTrue("Native transport must be enabled", vx.isNativeTransportEnabled());
    int len = 3;
    waitFor(len * len);
    List<SocketAddress> addresses = new ArrayList<>();
    for (int i = 0;i < len;i++) {
      File sockFile = TestUtils.tmpFile(".sock");
      SocketAddress sockAddress = SocketAddress.domainSocketAddress(sockFile.getAbsolutePath());
      NetServer server = vertx
        .createNetServer()
        .connectHandler(so -> {
          so.end(Buffer.buffer(sockAddress.path()));
        });
      startServer(sockAddress, server);
      addresses.add(sockAddress);
    }
    for (int i = 0;i < len;i++) {
      for (int j = 0;j < len;j++) {
        SocketAddress sockAddress = addresses.get(i);
        client.connect(sockAddress, onSuccess(so -> {
          Buffer received = Buffer.buffer();
          so.handler(received::appendBuffer);
          so.closeHandler(v -> {
            assertEquals(received.toString(), sockAddress.path());
            complete();
          });
        }));
      }
    }
    try {
      await();
    } finally {
      vx.close();
    }
  }

  @Test
  // Need to:
  // sudo sysctl -w net.core.somaxconn=10000
  // sudo sysctl -w net.ipv4.tcp_max_syn_backlog=10000
  // To get this to reliably pass with a lot of connections.
  public void testSharedServersRoundRobin() throws Exception {

    boolean domainSocket = testAddress.isDomainSocket();
    int numServers = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2- 1;
    int numConnections = numServers * (domainSocket ? 10 : 20);

    List<NetServer> servers = new ArrayList<>();
    Map<NetServer, Integer> connectCount = new ConcurrentHashMap<>();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numConnections);
    for (int i = 0; i < numServers; i++) {
      NetServer theServer = vertx.createNetServer();
      servers.add(theServer);
      theServer.connectHandler(sock -> {
        connectCount.compute(theServer, (s, cur) -> cur == null ? 1 : cur + 1);
        latchConns.countDown();
      }).listen(testAddress, ar -> {
        if (ar.succeeded()) {
          latchListen.countDown();
        } else {
          fail("Failed to bind server");
        }
      });
    }
    assertTrue(latchListen.await(10, TimeUnit.SECONDS));

    // Create a bunch of connections
    client.close();
    client = vertx.createNetClient(new NetClientOptions());
    CountDownLatch latchClient = new CountDownLatch(numConnections);
    for (int i = 0; i < numConnections; i++) {
      client.connect(testAddress, res -> {
        if (res.succeeded()) {
          latchClient.countDown();
        } else {
          res.cause().printStackTrace();
          fail("Failed to connect");
        }
      });
    }

    awaitLatch(latchClient);
    awaitLatch(latchConns);

    assertEquals(numServers, connectCount.size());
    for (NetServer server : servers) {
      assertTrue(connectCount.containsKey(server));
    }
    assertEquals(numServers, connectCount.size());
    for (int cnt : connectCount.values()) {
      assertEquals(numConnections / numServers, cnt);
    }

    testComplete();
  }

  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    // Have a server running on a different port to make sure it doesn't interact
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setPort(4321));
    server.connectHandler(sock -> {
      fail("Should not connect");
    }).listen(ar2 -> {
      if (ar2.succeeded()) {
        latch.countDown();
      } else {
        fail("Failed to bind server");
      }
    });
    awaitLatch(latch);
    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host before hand to make sure it doesn't interact
    server.close();
    CountDownLatch latch = new CountDownLatch(1);
    server = vertx.createNetServer();
    server.connectHandler(sock -> {
      fail("Should not connect");
    }).listen(testAddress, ar -> {
      if (ar.succeeded()) {
        latch.countDown();
      } else {
        fail("Failed to bind server");
      }
    });
    awaitLatch(latch);
    CountDownLatch closeLatch = new CountDownLatch(1);
    server.close(ar -> {
      assertTrue(ar.succeeded());
      closeLatch.countDown();
    });
    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
    testSharedServersRoundRobin();
  }

  @Test
  public void testClosingVertxCloseSharedServers() throws Exception {
    int numServers = 2;
    Vertx vertx = Vertx.vertx(getOptions());
    List<NetServerImpl> servers = new ArrayList<>();
    for (int i = 0;i < numServers;i++) {
      NetServer server = vertx.createNetServer().connectHandler(so -> {
        fail();
      });
      startServer(server);
      servers.add((NetServerImpl) server);
    }
    CountDownLatch latch = new CountDownLatch(1);
    vertx.close(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    servers.forEach(server -> {
      assertTrue(server.isClosed());
    });
  }

  @Test
  // This tests using NetSocket.writeHandlerID (on the server side)
  // Send some data and make sure it is fanned out to all connections
  public void testFanout() throws Exception {
    int numConnections = 10;

    Set<String> connections = new ConcurrentHashSet<>();
    server.connectHandler(socket -> {
      connections.add(socket.writeHandlerID());
      if (connections.size() == numConnections) {
        for (String actorID : connections) {
          vertx.eventBus().publish(actorID, Buffer.buffer("some data"));
        }
      }
      socket.closeHandler(v -> {
        connections.remove(socket.writeHandlerID());
      });
    });
    startServer();

    CountDownLatch receivedLatch = new CountDownLatch(numConnections);
    for (int i = 0; i < numConnections; i++) {
      client.connect(testAddress, onSuccess(socket -> {
        socket.handler(data -> {
          receivedLatch.countDown();
        });
      }));
    }
    assertTrue(receivedLatch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void testRemoteAddress() {
    server.connectHandler(socket -> {
      SocketAddress addr = socket.remoteAddress();
      assertEquals("127.0.0.1", addr.host());
      assertEquals(null, addr.hostName());
      assertEquals("127.0.0.1", addr.hostAddress());
      socket.close();
    }).listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      vertx.createNetClient(new NetClientOptions()).connect(1234, "localhost", onSuccess(socket -> {
        SocketAddress addr = socket.remoteAddress();
        assertEquals("localhost", addr.host());
        assertEquals("localhost", addr.hostName());
        assertEquals("127.0.0.1", addr.hostAddress());
        assertEquals(addr.port(), 1234);
        socket.closeHandler(v -> testComplete());
      }));
    });
    await();
  }

  @Test
  public void testWriteSameBufferMoreThanOnce() throws Exception {
    server.connectHandler(socket -> {
      Buffer received = Buffer.buffer();
      socket.handler(buff -> {
        received.appendBuffer(buff);
        if (received.toString().equals("foofoo")) {
          testComplete();
        }
      });
    }).listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      client.connect(testAddress, result -> {
        NetSocket socket = result.result();
        Buffer buff = Buffer.buffer("foo");
        socket.write(buff);
        socket.write(buff);
      });
    });
    await();
  }

  @Test
  public void sendFileClientToServer() throws Exception {
    File fDir = testFolder.newFolder();
    String content = TestUtils.randomUnicodeString(10000);
    File file = setupFile(fDir.toString(), "some-file.txt", content);
    Buffer expected = Buffer.buffer(content);
    Buffer received = Buffer.buffer();
    server.connectHandler(sock -> {
      sock.handler(buff -> {
        received.appendBuffer(buff);
        if (received.length() == expected.length()) {
          assertEquals(expected, received);
          testComplete();
        }
      });
      // Send some data to the client to trigger the sendfile
      sock.write("foo");
    });
    server.listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      client.connect(testAddress, ar2 -> {
        assertTrue(ar2.succeeded());
        NetSocket sock = ar2.result();
        sock.handler(buf -> {
          sock.sendFile(file.getAbsolutePath());
        });
      });
    });

    await();
  }

  @Test
  public void sendFileServerToClient() throws Exception {
    File fDir = testFolder.newFolder();
    String content = TestUtils.randomUnicodeString(10000);
    File file = setupFile(fDir.toString(), "some-file.txt", content);
    Buffer expected = Buffer.buffer(content);
    Buffer received = Buffer.buffer();
    server.connectHandler(sock -> {
      sock.handler(buf -> {
        sock.sendFile(file.getAbsolutePath());
      });
    });
    server.listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      client.connect(testAddress, ar2 -> {
        assertTrue(ar2.succeeded());
        NetSocket sock = ar2.result();
        sock.handler(buff -> {
          received.appendBuffer(buff);
          if (received.length() == expected.length()) {
            assertEquals(expected, received);
            testComplete();
          }
        });
        sock.write("foo");
      });
    });

    await();
  }

  @Test
  public void testSendFileDirectory() throws Exception {
    File fDir = testFolder.newFolder();
    server.connectHandler(socket -> {
      socket.handler(buff -> {
        fail("Should not receive any data");
      });
    }).listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      client.connect(testAddress, result -> {
        assertTrue(result.succeeded());
        NetSocket socket = result.result();
        try {
          socket.sendFile(fDir.getAbsolutePath().toString());
          // should throw exception and never hit the assert
          fail("Should throw exception");
        } catch (IllegalArgumentException e) {
          testComplete();
        }
      });
    });
    await();
  }

  @Test
  public void testServerOptionsCopiedBeforeUse() {
    server.close();
    NetServerOptions options = new NetServerOptions().setPort(1234);
    server = vertx.createNetServer(options);
    // Now change something - but server should still listen at previous port
    options.setPort(1235);
    server.connectHandler(sock -> {
      testComplete();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
      });
    });
    await();
  }

  @Test
  public void testClientOptionsCopiedBeforeUse() {
    client.close();
    NetClientOptions options = new NetClientOptions();
    client = vertx.createNetClient(options);
    options.setSsl(true);
    // Now change something - but server should ignore this
    server.connectHandler(sock -> {
      testComplete();
    });
    server.listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
      });
    });
    await();
  }

  @Test
  public void testListenWithNoHandler() {
    try {
      server.listen(testAddress);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testListenWithNoHandler2() {
    try {
      server.listen(testAddress, ar -> {
        assertFalse(ar.succeeded());
      });
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testSetHandlerAfterListen() {
    server.connectHandler(sock -> {
    });
    server.listen(testAddress, onSuccess(v -> testComplete()));
    try {
      server.connectHandler(sock -> {
      });
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
    await();
  }

  @Test
  public void testSetHandlerAfterListen2() {
    server.connectHandler(sock -> {
    });
    server.listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      try {
        server.connectHandler(sock -> {
        });
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }
      testComplete();
    });
    await();
  }

  @Test
  public void testListenTwice() {
    server.connectHandler(sock -> {
    });
    server.listen(testAddress, onSuccess(s -> {
      try {
        server.listen(testAddress, res -> {});
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        // OK
        testComplete();
      } catch (Exception e) {
        fail(e.getMessage());
      }
    }));
    await();
  }

  @Test
  public void testListenOnPortNoHandler() {
    server.connectHandler(NetSocket::close);
    server.listen(1234, onSuccess(ns -> {
      client.connect(1234, "localhost", onSuccess(so -> {
        so.closeHandler(v -> {
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testListen() {
    server.connectHandler(NetSocket::close);
    server.listen(testAddress, onSuccess(ns -> {
      client.connect(testAddress, onSuccess(so -> {
        so.closeHandler(v -> {
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testListenTwice2() {
    server.connectHandler(sock -> {
    });
    server.listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      try {
        server.listen(testAddress, sock -> {
        });
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }
      testComplete();
    });
    await();
  }

  @Test
  public void testCloseTwice() {
    client.close();
    client.close(); // OK
  }

  @Test
  public void testAttemptConnectAfterClose() {
    client.close();
    try {
      client.connect(testAddress, ar -> {
      });
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
  }

  @Test
  public void testCloseWithHandler() {
    waitFor(2);
    server.connectHandler(so -> {
      so.closeHandler(v -> {
        complete();
      });
    }).listen(testAddress, onSuccess(s -> {
      client.connect(testAddress, onSuccess(so -> {
        so.close(onSuccess(v -> {
          complete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testClientMultiThreaded() throws Exception {
    int numThreads = 10;
    Thread[] threads = new Thread[numThreads];
    CountDownLatch latch = new CountDownLatch(numThreads);
    server.connectHandler(socket -> {
      socket.handler(socket::write);
    }).listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      for (int i = 0; i < numThreads; i++) {
        threads[i] = new Thread() {
          public void run() {
            client.connect(testAddress, result -> {
              assertTrue(result.succeeded());
              Buffer buff = TestUtils.randomBuffer(100000);
              NetSocket sock = result.result();
              sock.write(buff);
              Buffer received = Buffer.buffer();
              sock.handler(rec -> {
                received.appendBuffer(rec);
                if (received.length() == buff.length()) {
                  assertEquals(buff, received);
                  latch.countDown();
                }
              });
            });
          }
        };
        threads[i].start();
      }
    });
    awaitLatch(latch);
    for (int i = 0; i < numThreads; i++) {
      threads[i].join();
    }
  }

  @Test
  public void testInVerticle() throws Exception {
    testInVerticle(false);
  }

  private void testInVerticle(boolean worker) throws Exception {
    client.close();
    server.close();
    class MyVerticle extends AbstractVerticle {
      Context ctx;
      @Override
      public void start() {
        ctx = context;
        if (worker) {
          assertTrue(ctx.isWorkerContext());
        } else {
          assertTrue(ctx.isEventLoopContext());
        }
        Thread thr = Thread.currentThread();
        server = vertx.createNetServer();
        server.connectHandler(sock -> {
          sock.handler(buff -> {
            sock.write(buff);
          });
          assertSame(ctx, context);
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
        });
        server.listen(testAddress, ar -> {
          assertTrue(ar.succeeded());
          assertSame(ctx, context);
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
          client = vertx.createNetClient(new NetClientOptions());
          client.connect(testAddress, ar2 -> {
            assertSame(ctx, context);
            if (!worker) {
              assertSame(thr, Thread.currentThread());
            }
            assertTrue(ar2.succeeded());
            NetSocket sock = ar2.result();
            Buffer buff = TestUtils.randomBuffer(10000);
            sock.write(buff);
            Buffer brec = Buffer.buffer();
            sock.handler(rec -> {
              assertSame(ctx, context);
              if (!worker) {
                assertSame(thr, Thread.currentThread());
              }
              brec.appendBuffer(rec);
              if (brec.length() == buff.length()) {
                testComplete();
              }
            });
          });
        });
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(worker));
    await();
  }

  @Test
  public void testContexts() throws Exception {
    int numConnections = 10;

    CountDownLatch serverLatch = new CountDownLatch(numConnections);
    AtomicReference<Context> serverConnectContext = new AtomicReference<>();
    server.connectHandler(sock -> {
      // Server connect handler should always be called with same context
      Context serverContext = Vertx.currentContext();
      if (serverConnectContext.get() != null) {
        assertSame(serverConnectContext.get(), serverContext);
      } else {
        serverConnectContext.set(serverContext);
      }
      serverLatch.countDown();
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    AtomicReference<Context> listenContext = new AtomicReference<>();
    server.listen(testAddress, onSuccess(v -> {
      listenContext.set(Vertx.currentContext());
      listenLatch.countDown();
    }));
    awaitLatch(listenLatch);

    Set<Context> contexts = new ConcurrentHashSet<>();
    AtomicInteger connectCount = new AtomicInteger();
    CountDownLatch clientLatch = new CountDownLatch(1);
    // Each connect should be in its own context
    for (int i = 0; i < numConnections; i++) {
      client.connect(testAddress, conn -> {
        contexts.add(Vertx.currentContext());
        if (connectCount.incrementAndGet() == numConnections) {
          assertEquals(numConnections, contexts.size());
          clientLatch.countDown();
        }
      });
    }
    awaitLatch(clientLatch);
    awaitLatch(serverLatch);

    // Close should be in own context
    server.close(ar -> {
      assertTrue(ar.succeeded());
      Context closeContext = Vertx.currentContext();
      assertFalse(contexts.contains(closeContext));
      assertNotSame(serverConnectContext.get(), closeContext);
      assertFalse(contexts.contains(listenContext.get()));
      assertSame(serverConnectContext.get(), listenContext.get());
      testComplete();
    });

    await();
  }

  @Test
  public void testReadStreamPauseResume() {
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setAcceptBacklog(1));
    ReadStream<NetSocket> socketStream = server.connectStream();
    AtomicBoolean paused = new AtomicBoolean();
    socketStream.handler(so -> {
      assertTrue(!paused.get());
      so.write("hello");
      so.close();
    });
    server.listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      paused.set(true);
      socketStream.pause();
      client.connect(testAddress, ar2 -> {
        assertTrue(ar2.succeeded());
        NetSocket so2 = ar2.result();
        so2.handler(buffer -> {
          fail();
        });
        so2.closeHandler(v -> {
          paused.set(false);
          socketStream.resume();
          client.connect(testAddress, ar3 -> {
            assertTrue(ar3.succeeded());
            NetSocket so3 = ar3.result();
            Buffer buffer = Buffer.buffer();
            so3.handler(buffer::appendBuffer);
            so3.closeHandler(v3 -> {
              assertEquals("hello", buffer.toString("utf-8"));
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testMultipleServerClose() {
    this.server = vertx.createNetServer();
    AtomicInteger times = new AtomicInteger();
    // We assume the endHandler and the close completion handler are invoked in the same context task
    ThreadLocal stack = new ThreadLocal();
    stack.set(true);
    server.connectStream().endHandler(v -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      times.incrementAndGet();
    });
    server.close(ar1 -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      server.close(ar2 -> {
        server.close(ar3 -> {
          assertEquals(1, times.get());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testInWorker() {
    waitFor(2);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        assertTrue(Vertx.currentContext().isWorkerContext());
        assertTrue(Context.isOnWorkerThread());
        final Context context = Vertx.currentContext();
        NetServer server1 = vertx.createNetServer();
        server1.connectHandler(conn -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          assertSame(context, Vertx.currentContext());
          conn.handler(buff -> {
            assertTrue(Vertx.currentContext().isWorkerContext());
            assertTrue(Context.isOnWorkerThread());
            assertSame(context, Vertx.currentContext());
            conn.write(buff);
          });
          conn.closeHandler(v -> {
            assertTrue(Vertx.currentContext().isWorkerContext());
            assertTrue(Context.isOnWorkerThread());
            assertSame(context, Vertx.currentContext());
            complete();
          });
          conn.endHandler(v -> {
            assertTrue(Vertx.currentContext().isWorkerContext());
            assertTrue(Context.isOnWorkerThread());
            assertSame(context, Vertx.currentContext());
            complete();
          });
        }).listen(testAddress, onSuccess(s -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          assertSame(context, Vertx.currentContext());
          NetClient client = vertx.createNetClient();
          client.connect(testAddress, onSuccess(res -> {
            assertTrue(Vertx.currentContext().isWorkerContext());
            assertTrue(Context.isOnWorkerThread());
            assertSame(context, Vertx.currentContext());
            res.write("foo");
            res.handler(buff -> {
              assertTrue(Vertx.currentContext().isWorkerContext());
              assertTrue(Context.isOnWorkerThread());
              assertSame(context, Vertx.currentContext());
              res.close();
            });
          }));
        }));
      }
    }, new DeploymentOptions().setWorker(true));
    await();
  }

  @Test
  public void testAsyncWriteIsFlushed() throws Exception {
    // Test that if we do a concurrent write (by another thread) during a channel read operation
    // the channel will be flished after the concurrent write
    int num = 128;
    Buffer expected = TestUtils.randomBuffer(1024);
    ExecutorService exec = Executors.newFixedThreadPool(1);
    try {
      server.connectHandler(so -> {
        so.handler(buff -> {
          assertEquals(256, buff.length());
          CountDownLatch latch = new CountDownLatch(1);
          exec.execute(() -> {
            latch.countDown();
            so.write(expected);
          });
          try {
            awaitLatch(latch);
          } catch (InterruptedException e) {
            fail(e);
          }
        });
      });
      startServer();
      AtomicInteger done = new AtomicInteger();
      for (int i = 0;i < num;i++) {
        client.connect(testAddress, ar -> {
          if (ar.succeeded()) {
            NetSocket so = ar.result();
            so.handler(buff -> {
              assertEquals(expected, buff);
              so.close();
              int val = done.incrementAndGet();
              if (val == num) {
                testComplete();
              }
            });
            so.write(TestUtils.randomBuffer(256));
          } else {
            ar.cause().printStackTrace();
          }
        });
      }
      await();
    } finally {
      exec.shutdown();
    }
  }

  private File setupFile(String testDir, String fileName, String content) throws Exception {
    File file = new File(testDir, fileName);
    if (file.exists()) {
      file.delete();
    }
    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    out.write(content);
    out.close();
    return file;
  }

  @Test
  public void testServerWorkerMissBufferWhenBufferArriveBeforeConnectCallback() throws Exception {
    int size = getOptions().getWorkerPoolSize();
    List<Context> workers = createWorkers(size + 1);
    CountDownLatch latch1 = new CountDownLatch(workers.size() - 1);
    workers.get(0).runOnContext(v -> {
      NetServer server = vertx.createNetServer();
      server.connectHandler(so -> {
        so.handler(buf -> {
          assertEquals("hello", buf.toString());
          testComplete();
        });
      });
      server.listen(testAddress, ar -> {
        assertTrue(ar.succeeded());
        // Create a one second worker starvation
        for (int i = 1; i < workers.size(); i++) {
          workers.get(i).runOnContext(v2 -> {
            latch1.countDown();
            try {
              Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
          });
        }
      });
    });
    awaitLatch(latch1);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress, ar -> {
      assertTrue(ar.succeeded());
      NetSocket so = ar.result();
      so.write(Buffer.buffer("hello"));
    });
    await();
  }

  @Test
  public void testClientWorkerMissBufferWhenBufferArriveBeforeConnectCallback() throws Exception {
    int size = getOptions().getWorkerPoolSize();
    List<Context> workers = createWorkers(size + 1);
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(size);
    NetServer server = vertx.createNetServer();
    server.connectHandler(so -> {
      try {
        awaitLatch(latch2);
      } catch (InterruptedException e) {
        fail(e.getMessage());
        return;
      }
      so.write(Buffer.buffer("hello"));
    });
    server.listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      latch1.countDown();
    });
    awaitLatch(latch1);
    workers.get(0).runOnContext(v -> {
      NetClient client = vertx.createNetClient();
      client.connect(testAddress, ar -> {
        assertTrue(ar.succeeded());
        NetSocket so = ar.result();
        so.handler(buf -> {
          assertEquals("hello", buf.toString());
          testComplete();
        });
      });
      // Create a one second worker starvation
      for (int i = 1; i < workers.size(); i++) {
        workers.get(i).runOnContext(v2 -> {
          latch2.countDown();
          try {
            Thread.sleep(1000);
          } catch (InterruptedException ignore) {
          }
        });
      }
    });
    await();
  }

  @Test
  public void testHostVerificationHttpsNotMatching() {
    server.close();
    NetServerOptions options = new NetServerOptions()
            .setPort(1234)
            .setHost("localhost")
            .setSsl(true)
            .setKeyStoreOptions(new JksOptions().setPath("tls/mim-server-keystore.jks").setPassword("wibble"));
    NetServer server = vertx.createNetServer(options);

    NetClientOptions clientOptions = new NetClientOptions()
            .setSsl(true)
            .setTrustAll(true)
            .setHostnameVerificationAlgorithm("HTTPS");
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        //Should not be able to connect
        assertTrue(ar2.failed());
        testComplete();
      });
    });
    await();
  }

  // this test sets HostnameVerification but also trustAll, it fails if hostname is
  // incorrect but does not verify the certificate validity

  @Test
  public void testHostVerificationHttpsMatching() {
    server.close();
    NetServerOptions options = new NetServerOptions()
            .setPort(1234)
            .setHost("localhost")
            .setSsl(true)
            .setKeyStoreOptions(new JksOptions().setPath("tls/server-keystore.jks").setPassword("wibble"));
    NetServer server = vertx.createNetServer(options);

    NetClientOptions clientOptions = new NetClientOptions()
            .setSsl(true)
            .setTrustAll(true)
            .setHostnameVerificationAlgorithm("HTTPS");
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        //Should be able to connect
        assertTrue(ar2.succeeded());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testNoLogging() throws Exception {
    TestLoggerFactory factory = testLogging();
    assertFalse(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testServerLogging() throws Exception {
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setLogActivity(true));
    TestLoggerFactory factory = testLogging();
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testClientLogging() throws Exception {
    client.close();
    client = vertx.createNetClient(new NetClientOptions().setLogActivity(true));
    TestLoggerFactory factory = testLogging();
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  private TestLoggerFactory testLogging() throws Exception {
    return TestUtils.testLogging(() -> {
      server.connectHandler(so -> {
        so.end(Buffer.buffer("fizzbuzz"));
      });
      server.listen(testAddress, onSuccess(v1 -> {
        client.connect(testAddress, onSuccess(so -> {
          so.closeHandler(v2 -> testComplete());
        }));
      }));
      await();
    });
  }

  /**
   * test socks5 proxy for accessing arbitrary server port.
   */
  @Test
  public void testWithSocks5Proxy() throws Exception {
    NetClientOptions clientOptions = new NetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setPort(11080));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new SocksProxy(null);
    proxy.start(vertx);
    server.listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        if (ar2.failed()) {
          log.warn("failed", ar2.cause());
        }
        assertTrue(ar2.succeeded());
        // make sure we have gone through the proxy
        assertEquals("localhost:1234", proxy.getLastUri());
        testComplete();
      });
    });
    await();
  }

  /**
   * test socks5 proxy for accessing arbitrary server port with authentication.
   */
  @Test
  public void testWithSocks5ProxyAuth() throws Exception {
    NetClientOptions clientOptions = new NetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setPort(11080)
            .setUsername("username").setPassword("username"));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new SocksProxy("username");
    proxy.start(vertx);
    server.listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
        testComplete();
      });
    });
    await();
  }

  /**
   * test socks5 proxy when accessing ssl server port with correct cert.
   */
  @Test
  public void testConnectSSLWithSocks5Proxy() throws Exception {
    server.close();
    NetServerOptions options = new NetServerOptions()
        .setPort(1234)
        .setHost("localhost")
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get());
    NetServer server = vertx.createNetServer(options);

    NetClientOptions clientOptions = new NetClientOptions()
        .setHostnameVerificationAlgorithm("HTTPS")
        .setSsl(true)
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("127.0.0.1").setPort(11080))
        .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new SocksProxy(null);
    proxy.start(vertx);
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
        testComplete();
      });
    });
    await();
  }

  /**
   * test socks5 proxy for accessing ssl server port with upgradeToSsl.
   * https://github.com/eclipse/vert.x/issues/1602
   */
  @Test
  public void testUpgradeSSLWithSocks5Proxy() throws Exception {
    server.close();
    NetServerOptions options = new NetServerOptions()
        .setPort(1234)
        .setHost("localhost")
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get());
    NetServer server = vertx.createNetServer(options);

    NetClientOptions clientOptions = new NetClientOptions()
        .setHostnameVerificationAlgorithm("HTTPS")
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("127.0.0.1").setPort(11080))
        .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new SocksProxy(null);
    proxy.start(vertx);
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
        NetSocket ns = ar2.result();
        ns.upgradeToSsl(onSuccess(v2 -> {
          testComplete();
        }));
      });
    });
    await();
  }

  /**
   * test http connect proxy for accessing a arbitrary server port
   * note that this may not work with a "real" proxy since there are usually access rules defined
   * that limit the target host and ports (e.g. connecting to localhost or to port 25 may not be allowed)
   */
  @Test
  public void testWithHttpConnectProxy() throws Exception {
    NetClientOptions clientOptions = new NetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setPort(13128));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new HttpProxy(null);
    proxy.start(vertx);
    server.listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        if (ar2.failed()) {
          log.warn("failed", ar2.cause());
        }
        assertTrue(ar2.succeeded());
        // make sure we have gone through the proxy
        assertEquals("localhost:1234", proxy.getLastUri());
        testComplete();
      });
    });
    await();
  }

  /**
   * test socks4a proxy for accessing arbitrary server port.
   */
  @Test
  public void testWithSocks4aProxy() throws Exception {
    NetClientOptions clientOptions = new NetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS4).setPort(11080));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new Socks4Proxy(null);
    proxy.start(vertx);
    server.listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        if (ar2.failed()) {
          log.warn("failed", ar2.cause());
        }
        assertTrue(ar2.succeeded());
        // make sure we have gone through the proxy
        assertEquals("localhost:1234", proxy.getLastUri());
        testComplete();
      });
    });
    await();
  }

  /**
   * test socks4a proxy for accessing arbitrary server port using username auth.
   */
  @Test
  public void testWithSocks4aProxyAuth() throws Exception {
    NetClientOptions clientOptions = new NetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS4).setPort(11080)
            .setUsername("username"));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new Socks4Proxy("username");
    proxy.start(vertx);
    server.listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        if (ar2.failed()) {
          log.warn("failed", ar2.cause());
        }
        assertTrue(ar2.succeeded());
        // make sure we have gone through the proxy
        assertEquals("localhost:1234", proxy.getLastUri());
        testComplete();
      });
    });
    await();
  }

  /**
   * test socks4a proxy for accessing arbitrary server port using an already resolved address.
   */
  @Test
  public void testWithSocks4LocalResolver() throws Exception {
    NetClientOptions clientOptions = new NetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS4).setPort(11080));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new Socks4Proxy(null).start(vertx);
    server.listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "127.0.0.1", ar2 -> {
        if (ar2.failed()) {
          log.warn("failed", ar2.cause());
        }
        assertTrue(ar2.succeeded());
        // make sure we have gone through the proxy
        assertEquals("127.0.0.1:1234", proxy.getLastUri());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testTLSHostnameCertCheckCorrect() {
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setSsl(true).setPort(4043)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get()));
    server.connectHandler(netSocket -> netSocket.close()).listen(ar -> {

      NetClientOptions options = new NetClientOptions()
          .setHostnameVerificationAlgorithm("HTTPS")
          .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());

      NetClient client = vertx.createNetClient(options);

      client.connect(4043, "localhost", arSocket -> {
        if (arSocket.succeeded()) {
          NetSocket ns = arSocket.result();
          ns.upgradeToSsl(onSuccess(v -> {
            testComplete();
          }));
        } else {
          fail(ar.cause());
        }
      });
    });

    await();
  }

  @Test
  public void testTLSHostnameCertCheckIncorrect() {
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setSsl(true).setPort(4043)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get()));
    server.connectHandler(netSocket -> netSocket.close()).listen(ar -> {

      NetClientOptions options = new NetClientOptions()
          .setHostnameVerificationAlgorithm("HTTPS")
          .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());

      NetClient client = vertx.createNetClient(options);

      client.connect(4043, "127.0.0.1", arSocket -> {
        if (arSocket.succeeded()) {
          NetSocket ns = arSocket.result();
          ns.upgradeToSsl(onFailure(err -> {
            testComplete();
          }));
        } else {
          fail(ar.cause());
        }
      });
    });

    await();
  }

  @Test
  public void testClientLocalAddress() {
    String expectedAddress = TestUtils.loopbackAddress();
    NetClientOptions clientOptions = new NetClientOptions().setLocalAddress(expectedAddress);
    client.close();
    client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {
      assertEquals(expectedAddress, sock.remoteAddress().host());
      sock.close();
    });
    server.listen(1234, "localhost", onSuccess(v -> {
      client.connect(1234, "localhost", onSuccess(socket -> {
        socket.closeHandler(v2 -> {
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testSelfSignedCertificate() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);

    SelfSignedCertificate certificate = SelfSignedCertificate.create();

    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(true)
      .setKeyCertOptions(certificate.keyCertOptions())
      .setTrustOptions(certificate.trustOptions());

    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(true)
      .setKeyCertOptions(certificate.keyCertOptions())
      .setTrustOptions(certificate.trustOptions());

    NetClientOptions clientTrustAllOptions = new NetClientOptions()
      .setSsl(true)
      .setTrustAll(true);

    server = vertx.createNetServer(serverOptions)
      .connectHandler(socket -> {
        socket.end(Buffer.buffer("123"));
      })
      .listen(testAddress, onSuccess(s -> {

        client = vertx.createNetClient(clientOptions);
        client.connect(testAddress, onSuccess(socket -> {
          socket.handler(buffer -> {
            assertEquals("123", buffer.toString());
            latch.countDown();
          });
        }));

        client = vertx.createNetClient(clientTrustAllOptions);
        client.connect(testAddress, onSuccess(socket -> {
          socket.handler(buffer -> {
            assertEquals("123", buffer.toString());
            latch.countDown();
          });
        }));

      }));

    awaitLatch(latch);
  }

  @Test
  public void testWorkerClient() throws Exception {
    String expected = TestUtils.randomAlphaString(2000);
    server.connectHandler(so -> {
      so.write(expected);
      so.close();
    });
    startServer();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        NetClient client = vertx.createNetClient();
        client.connect(testAddress, onSuccess(so ->{
          assertTrue(Context.isOnWorkerThread());
          Buffer received = Buffer.buffer();
          so.handler(buff -> {
            assertTrue(Context.isOnWorkerThread());
            received.appendBuffer(buff);
          });
          so.closeHandler(v -> {
            assertEquals(expected, received.toString());
            testComplete();
          });
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }));

      }
    }, new DeploymentOptions().setWorker(true));
    await();
  }

  @Test
  public void testWorkerServer() {
    String expected = TestUtils.randomAlphaString(2000);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        NetServer server = vertx.createNetServer();
        server.connectHandler(so -> {
          assertTrue(Context.isOnWorkerThread());
          Buffer received = Buffer.buffer();
          so.handler(buffer -> {
            assertTrue(Context.isOnWorkerThread());
            received.appendBuffer(buffer);
          });
          so.closeHandler(v -> {
            assertTrue(Context.isOnWorkerThread());
            assertEquals(expected, received.toString());
            testComplete();
          });
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
        server.listen(testAddress, ar -> {
          startPromise.handle(ar.mapEmpty());
        });
      }
    }, new DeploymentOptions().setWorker(true), onSuccess(v -> {
      client.connect(testAddress, onSuccess(so -> {
        so.write(expected);
        so.close();
      }));
    }));
    await();
  }

  @Test
  public void testNetServerInternal() throws Exception {
    testNetServerInternal_(new HttpClientOptions(), false);
  }

  @Test
  public void testNetServerInternalTLS() throws Exception {
    server.close();
    server = vertx.createNetServer(new NetServerOptions()
      .setPort(1234)
      .setHost("localhost")
      .setSsl(true)
      .setKeyStoreOptions(Cert.SERVER_JKS.get()));
    testNetServerInternal_(new HttpClientOptions()
      .setSsl(true)
      .setTrustStoreOptions(Trust.SERVER_JKS.get())
    , true);
  }

  private void testNetServerInternal_(HttpClientOptions clientOptions, boolean expectSSL) throws Exception {
    waitFor(2);
    server.connectHandler(so -> {
      NetSocketInternal internal = (NetSocketInternal) so;
      assertEquals(expectSSL, internal.isSsl());
      ChannelHandlerContext chctx = internal.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("handler", "http", new HttpServerCodec());
      internal.handler(buff -> fail());
      internal.messageHandler(obj -> {
        if (obj instanceof LastHttpContent) {
          DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer("Hello World", StandardCharsets.UTF_8));
          response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "11");
          internal.writeMessage(response, onSuccess(v -> complete()));
        }
      });
    });
    startServer(SocketAddress.inetSocketAddress(1234, "localhost"));
    HttpClient client = vertx.createHttpClient(clientOptions);
    client.get(1234, "localhost", "/somepath", onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      resp.bodyHandler(buff -> {
        assertEquals("Hello World", buff.toString());
        complete();
      });
    }));
    await();
  }

  @Test
  public void testNetClientInternal() throws Exception {
    testNetClientInternal_(new HttpServerOptions().setHost("localhost").setPort(1234), false);
  }

  @Test
  public void testNetClientInternalTLS() throws Exception {
    client.close();
    client = vertx.createNetClient(new NetClientOptions().setSsl(true).setTrustStoreOptions(Trust.SERVER_JKS.get()));
    testNetClientInternal_(new HttpServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setSsl(true)
      .setKeyStoreOptions(Cert.SERVER_JKS.get()), true);
  }

  private void testNetClientInternal_(HttpServerOptions options, boolean expectSSL) throws Exception {
    waitFor(2);
    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      req.response().end("Hello World"); });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    client.connect(1234, "localhost", onSuccess(so -> {
      NetSocketInternal soInt = (NetSocketInternal) so;
      assertEquals(expectSSL, soInt.isSsl());
      ChannelHandlerContext chctx = soInt.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("handler", "http", new HttpClientCodec());
      AtomicInteger status = new AtomicInteger();
      soInt.handler(buff -> fail());
      soInt.messageHandler(obj -> {
        switch (status.getAndIncrement()) {
          case 0:
            assertTrue(obj instanceof HttpResponse);
            HttpResponse resp = (HttpResponse) obj;
            assertEquals(200, resp.status().code());
            break;
          case 1:
            assertTrue(obj instanceof LastHttpContent);
            ByteBuf content = ((LastHttpContent) obj).content();
            assertEquals(!expectSSL, content.isDirect());
            assertEquals(1, content.refCnt());
            String val = content.toString(StandardCharsets.UTF_8);
            assertTrue(content.release());
            assertEquals("Hello World", val);
            complete();
            break;
          default:
            fail();
        }
      });
      soInt.writeMessage(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/somepath"), onSuccess(v -> complete()));
    }));
    await();
  }

  @Test
  public void testNetSocketInternalBuffer() throws Exception {
    server.connectHandler(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.messageHandler(msg -> fail("Unexpected"));
      soi.handler(msg -> {
        ByteBuf byteBuf = msg.getByteBuf();
        assertFalse(byteBuf.isDirect());
        assertEquals(1, byteBuf.refCnt());
        assertFalse(byteBuf.release());
        assertEquals(1, byteBuf.refCnt());
        soi.write(msg);
      });
    });
    startServer();
    client.connect(testAddress, onSuccess(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.write(Buffer.buffer("Hello World"));
      soi.messageHandler(msg -> fail("Unexpected"));
      soi.handler(msg -> {
        ByteBuf byteBuf = msg.getByteBuf();
        assertFalse(byteBuf.isDirect());
        assertEquals(1, byteBuf.refCnt());
        assertFalse(byteBuf.release());
        assertEquals(1, byteBuf.refCnt());
        assertEquals("Hello World", msg.toString());
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testCloseCompletionHandlerNotCalledWhenActualServerFailed() {
    server.close();
    server = vertx.createNetServer(
      new NetServerOptions()
        .setSsl(true)
        .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath("invalid")))
      .connectHandler(c -> {
    });
    try {
      server.listen(10000, r -> fail());
    } catch (Exception ignore) {
      // Expected
    }
    server.close(onSuccess(v -> {
      testComplete();
    }));
    await();
  }

  // We only do it for server, as client uses the same NetSocket implementation
  @Test
  public void testServerNetSocketShouldBeClosedWhenTheClosedHandlerIsCalled() throws Exception {
    waitFor(2);
    server.connectHandler(so -> {
      CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), 2, so);
      sender.send();
      so.closeHandler(v -> {
        Throwable failure = sender.close();
        if (failure != null) {
          fail(failure);
        } else {
          complete();
        }
      });
      so.endHandler(v -> {
        Throwable failure = sender.close();
        if (failure != null) {
          fail(failure);
        } else {
          complete();
        }
      });
    });
    startServer();
    client.connect(testAddress, onSuccess(so -> {
      vertx.setTimer(1000, id -> {
        so.close();
      });
    }));
    await();
  }

  @Test
  public void testServerWithIdleTimeoutSendChunkedFile() throws Exception {
    testIdleTimeoutSendChunkedFile(true);
  }

  @Test
  public void testClientWithIdleTimeoutSendChunkedFile() throws Exception {
    testIdleTimeoutSendChunkedFile(false);
  }

  private void testIdleTimeoutSendChunkedFile(boolean idleOnServer) throws Exception {
    int expected = 16 * 1024 * 1024; // We estimate this will take more than 200ms to transfer with a 1ms pause in chunks
    File sent = TestUtils.tmpFile(".dat", expected);
    server.close();
    AtomicReference<AsyncResult<Void>> sendResult = new AtomicReference<>();
    AtomicReference<Integer> remaining = new AtomicReference<>();
    AtomicLong now = new AtomicLong();
    Runnable testChecker = () -> {
      if (sendResult.get() != null && remaining.get() != null) {
        if (remaining.get() > 0) {
          // It might fail sometimes
          assertTrue(sendResult.get().failed());
        } else {
          assertTrue(sendResult.get().succeeded());
          assertTrue(System.currentTimeMillis() - now.get() > 200);
        }
        testComplete();
      }
    };
    Consumer<NetSocket> sender = so -> {
      so.sendFile(sent.getAbsolutePath(), ar -> {
        sendResult.set(ar);
        testChecker.run();
      });
    };
    Consumer<NetSocket> receiver = so -> {
      now.set(System.currentTimeMillis());
      int[] len = { 0 };
      so.handler(buff -> {
        len[0] += buff.length();
        so.pause();
        vertx.setTimer(1, id -> {
          so.resume();
        });
      });
      so.exceptionHandler(this::fail);
      so.endHandler(v -> {
        remaining.set(expected - len[0]);
        testChecker.run();
      });
    };
    server = vertx
      .createNetServer(new NetServerOptions().setIdleTimeout(200).setIdleTimeoutUnit(TimeUnit.MILLISECONDS))
      .connectHandler((idleOnServer ? sender : receiver)::accept);
    startServer();
    client.close();
    client = vertx.createNetClient(new NetClientOptions().setIdleTimeout(200).setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
    client.connect(testAddress, onSuccess(idleOnServer ? receiver : sender));
    await();
  }

  @Test
  public void testHalfCloseCallsEndHandlerAfterBuffersAreDelivered() throws Exception {
    // Synchronized on purpose
    StringBuffer expected = new StringBuffer();
    server.connectHandler(so -> {
      Context ctx = vertx.getOrCreateContext();
      for (int i = 0;i < 8;i++) {
        int val = i;
        ctx.runOnContext(v -> {
          String chunk = "chunk-" + val + "\r\n";
          so.write(chunk);
          expected.append(chunk);
        });
      }
      ctx.runOnContext(v -> {
        // This will half close the connection
        so.close();
      });
    });
    startServer();
    client.connect(testAddress, "localhost", onSuccess(so -> {
      so.pause();
      AtomicBoolean closed = new AtomicBoolean();
      AtomicBoolean ended = new AtomicBoolean();
      Buffer received = Buffer.buffer();
      so.handler(received::appendBuffer);
      so.closeHandler(v -> {
        assertFalse(ended.get());
        assertEquals(Buffer.buffer(), received);
        closed.set(true);
        so.resume();
      });
      so.endHandler(v -> {
        assertEquals(expected.toString(), received.toString());
        ended.set(true);
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testSslHandshakeTimeoutHappened() throws Exception {
    server.close();
    client.close();

    // set up a normal server to force the SSL handshake time out in client
    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(false);
    server = vertx.createNetServer(serverOptions);

    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(true)
      .setTrustAll(true)
      .setSslHandshakeTimeout(200)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    client = vertx.createNetClient(clientOptions);

    server.connectHandler(s -> {
    }).listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      client.connect(testAddress, onFailure(err -> {
        assertTrue(err instanceof SSLHandshakeException);
        assertEquals("handshake timed out", err.getCause().getMessage());
        testComplete();
      }));
    });
    await();
  }

  @Test
  public void testSslHandshakeTimeoutNotHappened() throws Exception {
    server.close();
    client.close();

    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(true)
      .setKeyStoreOptions(Cert.SERVER_JKS.get())
      // set 100ms to let the connection established
      .setSslHandshakeTimeout(100)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    server = vertx.createNetServer(serverOptions);

    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(true)
      .setTrustAll(true);
    client = vertx.createNetClient(clientOptions);

    server.connectHandler(s -> {
    }).listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      client.connect(testAddress, res -> {
        assertTrue(res.succeeded());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testSslHandshakeTimeoutHappenedWhenUpgradeSsl() {
    server.close();
    client.close();

    // set up a normal server to force the SSL handshake time out in client
    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(false);
    server = vertx.createNetServer(serverOptions);

    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(false)
      .setTrustAll(true)
      .setSslHandshakeTimeout(200)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    client = vertx.createNetClient(clientOptions);

    server.connectHandler(s -> {
    }).listen(testAddress, ar -> {
      assertTrue(ar.succeeded());
      client.connect(testAddress, res -> {
        assertTrue(res.succeeded());
        NetSocket socket = res.result();

        assertFalse(socket.isSsl());
        socket.upgradeToSsl(onFailure(err -> {
          assertTrue(err instanceof SSLException);
          assertEquals("handshake timed out", err.getMessage());
          testComplete();
        }));
      });
    });
    await();
  }

  protected void startServer(SocketAddress remoteAddress) throws Exception {
    startServer(remoteAddress, vertx.getOrCreateContext());
  }

  protected void startServer(SocketAddress remoteAddress, NetServer server) throws Exception {
    startServer(remoteAddress, vertx.getOrCreateContext(), server);
  }

  protected void startServer(SocketAddress remoteAddress, Context context) throws Exception {
    startServer(remoteAddress, context, server);
  }

  protected void startServer(SocketAddress remoteAddress, Context context, NetServer server) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    context.runOnContext(v -> {
      server.listen(remoteAddress, onSuccess(s -> latch.countDown()));
    });
    awaitLatch(latch);
  }

  @Test
  public void testPausedDuringLastChunk() throws Exception {
    server.connectHandler(so -> {
      AtomicBoolean paused = new AtomicBoolean();
      paused.set(true);
      so.pause();
      so.closeHandler(v -> {
        paused.set(false);
        so.resume();
      });
      so.endHandler(v -> {
        assertFalse(paused.get());
        testComplete();
      });
    });
    startServer();
    client.connect(testAddress, "localhost", onSuccess(so -> {
      so.close();
    }));
    await();
  }

  protected void startServer() throws Exception {
    startServer(testAddress, vertx.getOrCreateContext());
  }

  protected void startServer(NetServer server) throws Exception {
    startServer(testAddress, vertx.getOrCreateContext(), server);
  }

  protected void startServer(Context context) throws Exception {
    startServer(testAddress, context, server);
  }

  protected void startServer(Context context, NetServer server) throws Exception {
    startServer(testAddress, context, server);
  }

  @Test
  public void testUnresolvedSocketAddress() {
    InetSocketAddress a = InetSocketAddress.createUnresolved("localhost", 8080);
    SocketAddress converted = ((VertxInternal) vertx).transport().convert(a);
    assertEquals(8080, converted.port());
    assertEquals("localhost", converted.host());
  }

  @Test
  public void testNetSocketHandlerFailureReportedToContextExceptionHandler() throws Exception {
    server.connectHandler(so -> {
      Context ctx = Vertx.currentContext();
      List<Throwable> reported = new ArrayList<>();
      ctx.exceptionHandler(reported::add);
      NullPointerException err1 = new NullPointerException();
      so.handler(buff -> {
        throw err1;
      });
      NullPointerException err2 = new NullPointerException();
      so.endHandler(v ->{
        throw err2;
      });
      NullPointerException err3 = new NullPointerException();
      so.closeHandler(v1 -> {
        ctx.runOnContext(v2 -> {
          assertEquals(Arrays.asList(err1, err2, err3), reported);
          testComplete();
        });
        throw err3;
      });
    });
    startServer(testAddress);
    client.connect(testAddress, onSuccess(so -> {
      so.write("ping");
      so.close();
    }));
    await();
  }

}
