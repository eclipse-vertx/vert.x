/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.incubator.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Future;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import io.vertx.core.impl.Utils;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.CleanableNetClient;
import io.vertx.core.net.impl.HAProxyMessageCompletionHandler;
import io.vertx.core.net.impl.NetServerImpl;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.tls.SslContextFactory;
import io.vertx.test.core.CheckingSender;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.netty.TestLoggerFactory;
import io.vertx.test.proxy.*;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import static io.vertx.test.core.TestUtils.*;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTPS_HOST;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTPS_PORT;
import static io.vertx.tests.tls.HttpTLSTest.testPeerHostServerCert;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class NetTest extends VertxTestBase {

  private SocketAddress testAddress;
  private NetServer server;
  private NetClient client;
  private TestProxyBase proxy;
  private File tmp;

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  protected abstract NetServerOptions createNetServerOptions();

  protected abstract NetClientOptions createNetClientOptions();

  protected abstract HttpServerOptions createBaseServerOptions();

  protected abstract HttpClientOptions createBaseClientOptions();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", TRANSPORT.implementation().supportsDomainSockets());
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
    } else {
      testAddress = SocketAddress.inetSocketAddress(1234, "localhost");
    }
    client = vertx.createNetClient(createNetClientOptions().setConnectTimeout(1000));
    server = vertx.createNetServer(createNetServerOptions());
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

  @Override
  protected void tearDown() throws Exception {
    if (tmp != null) {
      tmp.delete();
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
    assertEquals(options, options.setKeyCertOptions(keyStoreOptions));
    assertEquals(keyStoreOptions, options.getKeyCertOptions());

    assertNull(options.getTrustOptions());
    JksOptions trustStoreOptions = new JksOptions().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setTrustOptions(trustStoreOptions));
    assertEquals(trustStoreOptions, options.getTrustOptions());

    assertFalse(options.isTrustAll());
    assertEquals(options, options.setTrustAll(true));
//    assertTrue(options.isTrustAll());

    String randomAlphaString = TestUtils.randomAlphaString(10);
    assertNull(options.getHostnameVerificationAlgorithm());
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

    assertEquals(SSLOptions.DEFAULT_SSL_HANDSHAKE_TIMEOUT, options.getSslHandshakeTimeout());
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
    assertEquals(options, options.setKeyCertOptions(keyStoreOptions));
    assertEquals(keyStoreOptions, options.getKeyCertOptions());

    assertNull(options.getTrustOptions());
    JksOptions trustStoreOptions = new JksOptions().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setTrustOptions(trustStoreOptions));
    assertEquals(trustStoreOptions, options.getTrustOptions());

    assertEquals(-1, options.getAcceptBacklog());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setAcceptBacklog(rand));
    assertEquals(rand, options.getAcceptBacklog());

    assertEquals(0, options.getPort());
    assertEquals(options, options.setPort(1234));
    assertEquals(1234, options.getPort());
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

    assertEquals(SSLOptions.DEFAULT_SSL_HANDSHAKE_TIMEOUT, options.getSslHandshakeTimeout());
    long randomSslTimeout = TestUtils.randomPositiveLong();
    assertEquals(options, options.setSslHandshakeTimeout(randomSslTimeout));
    assertEquals(randomSslTimeout, options.getSslHandshakeTimeout());
    assertIllegalArgumentException(() -> options.setSslHandshakeTimeout(-123));

    assertFalse(options.isUseProxyProtocol());
    assertEquals(options, options.setUseProxyProtocol(true));
    assertTrue(options.isUseProxyProtocol());

    assertEquals(NetServerOptions.DEFAULT_PROXY_PROTOCOL_TIMEOUT_TIME_UNIT, options.getProxyProtocolTimeoutUnit());
    long randomProxyTimeout = TestUtils.randomPositiveLong();
    assertEquals(options, options.setProxyProtocolTimeout(randomProxyTimeout));
    assertEquals(randomProxyTimeout, options.getProxyProtocolTimeout());
    assertIllegalArgumentException(() -> options.setProxyProtocolTimeout(-123));

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
    options.setKeyCertOptions(keyStoreOptions);
    options.setTrustOptions(trustStoreOptions);
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
    String sslEngine;
    JsonObject sslEngineOptions;
    if (TestUtils.randomBoolean()) {
      sslEngine = "jdkSslEngineOptions";
      sslEngineOptions = new JsonObject();
    } else {
      sslEngine = "openSslEngineOptions";
      boolean sessionCacheEnabled = rand.nextBoolean();
      sslEngineOptions = new JsonObject()
        .put("sessionCacheEnabled", sessionCacheEnabled)
        .put("useWorkerThread", SSLEngineOptions.DEFAULT_USE_WORKER_POOL);
    }
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
        .put(sslEngine, sslEngineOptions)
        .put("hostnameVerificationAlgorithm", hostnameVerificationAlgorithm)
        .put("sslHandshakeTimeout", sslHandshakeTimeout);


    JsonObject converted = new NetClientOptions(json).toJson();
    for (Map.Entry<String, Object> entry : json) {
      assertEquals(entry.getValue(), converted.getValue(entry.getKey()));
    }

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
    boolean useProxyProtocol = TestUtils.randomBoolean();
    long proxyProtocolTimeout = TestUtils.randomPositiveLong();

    options.setSendBufferSize(sendBufferSize);
    options.setReceiveBufferSize(receiverBufferSize);
    options.setReuseAddress(reuseAddress);
    options.setTrafficClass(trafficClass);
    options.setTcpNoDelay(tcpNoDelay);
    options.setTcpKeepAlive(tcpKeepAlive);
    options.setSoLinger(soLinger);
    options.setIdleTimeout(idleTimeout);
    options.setSsl(ssl);
    options.setKeyCertOptions(keyStoreOptions);
    options.setTrustOptions(trustStoreOptions);
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
    options.setUseProxyProtocol(useProxyProtocol);
    options.setProxyProtocolTimeout(proxyProtocolTimeout);

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
    assertEquals(def.getSslHandshakeTimeoutUnit(), json.getSslHandshakeTimeoutUnit());
    assertEquals(def.isUseProxyProtocol(), json.isUseProxyProtocol());
    assertEquals(def.getProxyProtocolTimeout(), json.getProxyProtocolTimeout());
    assertEquals(def.getProxyProtocolTimeoutUnit(), json.getProxyProtocolTimeoutUnit());
  }

  @Test
  public void testServerOptionsJson() {
    int sendBufferSize = TestUtils.randomPositiveInt();
    int receiverBufferSize = TestUtils.randomPositiveInt();
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
    boolean useProxyProtocol = TestUtils.randomBoolean();
    long proxyProtocolTimeout = TestUtils.randomPositiveLong();

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
      .put("sslHandshakeTimeout", sslHandshakeTimeout)
      .put("useProxyProtocol", useProxyProtocol)
      .put("proxyProtocolTimeout", proxyProtocolTimeout);

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
    assertEquals(useProxyProtocol, options.isUseProxyProtocol());
    assertEquals(proxyProtocolTimeout, options.getProxyProtocolTimeout());

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
    client.connect(testAddress).onComplete(onSuccess(so -> {
      writeUntilFull(so, v -> {
        so.write(Buffer.buffer("lost buffer")).onComplete(onSuccess(ack -> testComplete()));
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
    client.connect(testAddress).onComplete(onSuccess(so -> {
      writeUntilFull(so, v -> {
        so.write(Buffer.buffer("lost buffer")).onComplete(onFailure(err -> testComplete()));
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
    startEchoServer(testAddress, s -> client.connect(testAddress).onComplete(clientHandler));
    await();
  }

  void startEchoServer(SocketAddress address, Handler<AsyncResult<NetServer>> listenHandler) {
    Handler<NetSocket> serverHandler = socket -> socket.handler(socket::write);
    server.connectHandler(serverHandler).listen(address).onComplete(listenHandler);
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
        client.connect(address).onComplete(handler);
      }
    });
    await();
  }

  @Test
  public void testConnectInvalidPort() {
    assertIllegalArgumentException(() -> client.connect(-1, "localhost"));
    assertIllegalArgumentException(() -> client.connect(65536, "localhost"));
    client.connect(9998, "localhost").onComplete(onFailure((err -> testComplete())));
    await();
  }

  @Test
  public void testConnectInvwalidHost() {
    assertNullPointerException(() -> client.connect(80, null));
    client.connect(1234, "127.0.0.2").onComplete(onFailure(err -> testComplete()));
    await();
  }

  @Ignore("Now they share the same TCP server port")
  @Test
  public void testListenInvalidPort() {
    final int port = 9090;
    final HttpServer httpServer = vertx.createHttpServer();
    try {
      httpServer.requestHandler(ignore -> {})
        .listen(port).onComplete(onSuccess(s ->
          vertx.createNetServer(createNetServerOptions())
            .connectHandler(ignore -> {})
            .listen(port).onComplete(onFailure(error -> {
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
    server = vertx.createNetServer(createNetServerOptions().setPort(1234).setHost("uhqwduhqwudhqwuidhqwiudhqwudqwiuhd"));
    server.connectHandler(netSocket -> {
    }).listen().onComplete(onFailure(err -> testComplete()));
    await();
  }

  @Test
  public void testListenOnWildcardPort() {
    server.close();
    server = vertx.createNetServer(createNetServerOptions().setPort(0));
    server.connectHandler((netSocket) -> {
    }).listen().onComplete(onSuccess(s -> {
      assertTrue(server.actualPort() > 1024);
      assertEquals(server, s);
      testComplete();
    }));
    await();
  }

  @Test
  public void testClientCloseHandlersCloseFromClient() {
    startEchoServer(testAddress, s -> clientCloseHandlers(true));
    await();
  }

  @Test
  public void testClientCloseHandlersCloseFromServer() {
    server.connectHandler(NetSocket::close).listen(testAddress).onComplete((s) -> clientCloseHandlers(false));
    await();
  }

  void clientCloseHandlers(boolean closeFromClient) {
    client.connect(testAddress).onComplete(onSuccess(so -> {
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
    serverCloseHandlers(false, s -> client.connect(testAddress).onComplete(ar -> ar.result().close()));
    await();
  }

  @Test
  public void testServerCloseHandlersCloseFromServer() {
    serverCloseHandlers(true, s -> client.connect(testAddress));
    await();
  }

  void serverCloseHandlers(boolean closeFromServer, Handler<NetServer> listenHandler) {
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
    }).listen(testAddress).onComplete(onSuccess(listenHandler::handle));
  }

  @Test
  public void testClientClose() throws Exception {
    int num = 3;
    List<NetServer> servers = new ArrayList<>();
    try {
      for (int i = 0;i < num;i++) {
        NetServer server = vertx.createNetServer(createNetServerOptions());
        server.connectHandler(so -> {

        });
        startServer(SocketAddress.inetSocketAddress(1234 + i, "localhost"), server);
        servers.add(server);
      }
      NetClient client = vertx.createNetClient(createNetClientOptions());
      AtomicInteger inflight = new AtomicInteger();
      for (int i = 0;i < num;i++) {
        client.connect(1234 + i, "localhost").onComplete(onSuccess(so -> {
          inflight.incrementAndGet();
          so.closeHandler(v -> {
            inflight.decrementAndGet();
          });
        }));
      }
      assertWaitUntil(() -> inflight.get() == 3);
      CountDownLatch latch = new CountDownLatch(1);
      client.close().onComplete(onSuccess(v -> latch.countDown()));
      awaitLatch(latch);
      assertWaitUntil(() -> inflight.get() == 0);
    } finally {
      servers.forEach(NetServer::close);
    }
  }

  @Test
  public void testReceiveMessageAfterExplicitClose() throws Exception {
    server.connectHandler(so -> {
      so.handler(buff -> {
        so.write(buff);
      });
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.channelHandlerContext().pipeline().addFirst(new ChannelInboundHandlerAdapter() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          so.close();
          super.channelRead(ctx, msg);
        }
      });
      so.handler(buff -> {
        assertEquals("Hello World", buff.toString());
        testComplete();
      });
      so.write("Hello World");
    }));
    await();
  }

  @Test
  public void testClientDrainHandler() {
    pausingServer((s) -> {
      client.connect(testAddress).onComplete(onSuccess(sock -> {
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
      MessageConsumer<?> reg = vertx.eventBus().<Buffer>consumer("server_resume").handler(resumeHandler);
      sock.closeHandler(v -> reg.unregister());
    }).listen(testAddress).onComplete(listenHandler);
  }

  @Test
  public void testServerDrainHandler() {
    drainingServer(s -> {
      client.connect(testAddress).onComplete(onSuccess(sock -> {
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
    }).listen(testAddress).onComplete(listenHandler);
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
    client = vertx.createNetClient(createNetClientOptions().setReconnectAttempts(attempts).setReconnectInterval(10));

    //The server delays starting for a a few seconds, but it should still connect
    client.connect(testAddress).onComplete(onSuccess(so -> testComplete()));

    // Start the server after a delay
    vertx.setTimer(2000, id -> startEchoServer(testAddress, s -> {}));

    await();
  }

  @Test
  public void testReconnectAttemptsNotEnough() {
    // This test does not pass reliably in CI for Windows
    Assume.assumeFalse(Utils.isWindows());
    client.close();
    client = vertx.createNetClient(createNetClientOptions().setReconnectAttempts(100).setReconnectInterval(10));

    client.connect(testAddress).onComplete(onFailure(err -> testComplete()));

    await();
  }

  @Test
  public void testServerIdleTimeout1() {
    testTimeout(createNetClientOptions(), createNetServerOptions().setIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertEquals("0123456789", received.toString()), true);
  }

  @Test
  public void testServerIdleTimeout2() {
    testTimeout(createNetClientOptions(), createNetServerOptions().setReadIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertEquals("0123456789", received.toString()), true);
  }

  @Test
  public void testServerIdleTimeout3() {
    // Usually 012 but might be 01 or 0123
    testTimeout(createNetClientOptions(), createNetServerOptions().setWriteIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertFalse("0123456789".equals(received.toString())), true);
  }

  @Test
  public void testServerIdleTimeout4() {
    testTimeout(createNetClientOptions(), createNetServerOptions().setIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertEquals("0123456789", received.toString()), false);
  }

  @Test
  public void testServerIdleTimeout5() {
    // Usually 012 but might be 01 or 0123
    testTimeout(createNetClientOptions(), createNetServerOptions().setReadIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertFalse("0123456789".equals(received.toString())), false);
  }

  @Test
  public void testServerIdleTimeout6() {
    testTimeout(createNetClientOptions(), createNetServerOptions().setWriteIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertEquals("0123456789", received.toString()), false);
  }

  @Test
  public void testClientIdleTimeout1() {
    testTimeout(createNetClientOptions().setIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), createNetServerOptions(), received -> assertEquals("0123456789", received.toString()), true);
  }

  @Test
  public void testClientIdleTimeout2() {
    testTimeout(createNetClientOptions().setWriteIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), createNetServerOptions(), received -> assertEquals("0123456789", received.toString()), true);
  }

  @Test
  public void testClientIdleTimeout3() {
    // Usually 012 but might be 01 or 0123
    testTimeout(createNetClientOptions().setReadIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), createNetServerOptions(), received -> assertFalse("0123456789".equals(received.toString())), true);
  }

  @Test
  public void testClientIdleTimeout4() {
    testTimeout(createNetClientOptions().setIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), createNetServerOptions(), received -> assertEquals("0123456789", received.toString()), false);
  }

  @Test
  public void testClientIdleTimeout5() {
    // Usually 012 but might be 01 or 0123
    testTimeout(createNetClientOptions().setWriteIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), createNetServerOptions(), received -> assertFalse("0123456789".equals(received.toString())), false);
  }

  @Test
  public void testClientIdleTimeout6() {
    testTimeout(createNetClientOptions().setReadIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), createNetServerOptions(), received -> assertEquals("0123456789", received.toString()), false);
  }

  private void testTimeout(NetClientOptions clientOptions, NetServerOptions serverOptions, Consumer<Buffer> check, boolean clientSends) {
    server.close();
    server = vertx.createNetServer(serverOptions);
    client.close();
    client = vertx.createNetClient(clientOptions);
    Buffer received = Buffer.buffer();
    AtomicInteger idleEvents = new AtomicInteger();
    Handler<NetSocket> receiver = so -> {
      ((NetSocketInternal)so).eventHandler(evt -> {
        if (evt instanceof IdleStateEvent) {
          idleEvents.incrementAndGet();
        }
      });
      so.handler(received::appendBuffer);
    };
    Handler<NetSocket> sender = so -> {
      AtomicInteger times = new AtomicInteger();
      vertx.setPeriodic(100, id -> {
        int val = times.getAndIncrement();
        if (val < 10) {
          so.write("" + val);
        } else {
          vertx.cancelTimer(id);
        }
      });
      ((NetSocketInternal)so).eventHandler(evt -> {
        if (evt instanceof IdleStateEvent) {
          idleEvents.incrementAndGet();
        }
      });
      so.closeHandler(v -> {
        check.accept(received);
        assertEquals(1, idleEvents.get());
        testComplete();
      });
    };
    Handler<NetSocket> clientHandler = clientSends ? sender : receiver;
    Handler<NetSocket> serverHandler = clientSends ? receiver : sender;
    server.connectHandler(serverHandler).listen(testAddress).onComplete(onSuccess(s -> {
      client.connect(testAddress).onComplete(onSuccess(clientHandler::handle));
    }));
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
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert1_3() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, new String[0], new String[]{"TLSv1.3"});
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
    assumeTrue(PlatformDependent.javaVersion() < 9);
    // We just need a vanilla cert for this test
    SelfSignedCertificate cert = SelfSignedCertificate.create("host2.com");
    TLSTest test = new TLSTest()
      .clientTrust(cert::trustOptions)
      .connectAddress(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com."))
      .bindAddress(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com"))
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
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com"))
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
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host1"))
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(false);
    await();
  }

  @Test
  public void testSniImplicitServerNameDisabledForShortname2() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SERVER_JKS)
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host1"))
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    await();
    assertEquals("localhost", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testSniForceShortname() throws Exception {
    TLSTest test = new TLSTest()
        .clientTrust(Trust.SNI_JKS_HOST1)
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host1"))
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
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "example.com"))
        .serverName("host2.com")
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    await();
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testClientSniMultipleServerName() throws Exception {
    List<String> receivedServerNames = Collections.synchronizedList(new ArrayList<>());
    server = vertx.createNetServer(createNetServerOptions()
      .setSni(true)
      .setSsl(true)
      .setKeyCertOptions(Cert.SNI_JKS.get())
    ).connectHandler(so -> {
      receivedServerNames.add(so.indicatedServerName());
    });
    startServer();
    List<String> serverNames = Arrays.asList("host1", "host2.com", "fake");
    List<String> cns = new ArrayList<>();
    client = vertx.createNetClient(createNetClientOptions().setSsl(true).setHostnameVerificationAlgorithm("").setTrustAll(true));
    for (String serverName : serverNames) {
      NetSocket so = awaitFuture(client.connect(testAddress, serverName));
      String host = cnOf(so.peerCertificates().get(0));
      cns.add(host);
    }
    assertEquals(Arrays.asList("host1", "host2.com", "localhost"), cns);
    assertEquals(2, ((NetServerImpl)server).sniEntrySize());
    assertWaitUntil(() -> receivedServerNames.size() == 3);
    assertEquals(receivedServerNames, serverNames);
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
      .connectAddress(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com."))
      .bindAddress(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com"))
      .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    await();
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
  }

  @Test
  public void testServerCertificateMultiple() throws Exception {
    TLSTest test = new TLSTest()
      .serverCert(Cert.MULTIPLE_JKS)
      .clientTrustAll(true);
    test.run(true);
    await();
    assertEquals("precious", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testServerCertificateMultipleWrongAlias() throws Exception {
    TLSTest test = new TLSTest()
      .serverCert(Cert.MULTIPLE_JKS_WRONG_ALIAS)
      .clientTrustAll(true);
    server = vertx
      .createNetServer(test.setupServer())
      .connectHandler(so -> {

    });
    server.listen(test.bindAddress).onComplete(onFailure(t -> {
      assertThat(t, is(instanceOf(IllegalArgumentException.class)));
      assertThat(t.getMessage(), containsString("alias does not exist in the keystore"));
      testComplete();
    }));
    await();
  }

  @Test
  public void testServerCertificateMultipleWithKeyPassword() throws Exception {
    TLSTest test = new TLSTest()
      .serverCert(Cert.MULTIPLE_JKS_ALIAS_PASSWORD)
      .clientTrustAll(true);
    test.run(true);
    await();
    assertEquals("fonky", cnOf(test.clientPeerCert()));
  }

  void testTLS(Cert<?> clientCert, Trust<?> clientTrust,
               Cert<?> serverCert, Trust<?> serverTrust,
    boolean requireClientAuth, boolean clientTrustAll,
    boolean shouldPass, boolean startTLS) throws Exception {
        testTLS(clientCert, clientTrust, serverCert, serverTrust, requireClientAuth, clientTrustAll,
        shouldPass, startTLS, new String[0], new String[]{"TLSv1.2"});
  }

  void testTLS(Cert<?> clientCert, Trust<?> clientTrust,
               Cert<?> serverCert, Trust<?> serverTrust,
    boolean requireClientAuth, boolean clientTrustAll,
    boolean shouldPass, boolean startTLS,
    String[] enabledCipherSuites) throws Exception {
        testTLS(clientCert, clientTrust, serverCert, serverTrust, requireClientAuth, clientTrustAll,
        shouldPass, startTLS, enabledCipherSuites, new String[]{"TLSv1.2"});
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
        .clientVersion(enabledSecureTransportProtocols)
        .serverVersion(enabledSecureTransportProtocols);
    test.run(shouldPass);
    await();
  }

  class TLSTest {

    Cert<?> clientCert = Cert.NONE;
    Trust<?> clientTrust = Trust.NONE;
    Cert<?> serverCert = Cert.NONE;
    Trust<?> serverTrust = Trust.NONE;
    Set<String> serverVersions = Collections.singleton("TLSv1.2");
    Set<String> clientVersions = Collections.singleton("TLSv1.2");
    boolean requireClientAuth;
    boolean clientTrustAll;
    boolean startTLS;
    String[] enabledCipherSuites = new String[0];
    boolean sni;
    SocketAddress bindAddress = SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "localhost");
    SocketAddress connectAddress = bindAddress;
    String serverName;
    Certificate clientPeerCert;
    String indicatedServerName;

    public TLSTest clientVersion(String... versions) {
      clientVersions = new HashSet<>(Arrays.asList(versions));
      return this;
    }

    public TLSTest serverVersion(String... versions) {
      serverVersions = new HashSet<>(Arrays.asList(versions));
      return this;
    }

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

    public Certificate clientPeerCert() {
      return clientPeerCert;
    }

    NetServerOptions setupServer() {
      NetServerOptions options = createNetServerOptions();
      if (!startTLS) {
        options.setSsl(true);
      }
      options.setEnabledSecureTransportProtocols(serverVersions);
      options.setTrustOptions(serverTrust.get());
      options.setKeyCertOptions(serverCert.get());
      if (requireClientAuth) {
        options.setClientAuth(ClientAuth.REQUIRED);
      }
      for (String suite: enabledCipherSuites) {
        options.addEnabledCipherSuite(suite);
      }
      options.setSni(sni);
      return options;
    }

    void run(boolean shouldPass) {
      if (!shouldPass) {
        waitForMore(1);
      }
      Future<String> bind = vertx.deployVerticle(new AbstractVerticle() {
        @Override
        public void start(Promise<Void> startPromise) {
          server.close();
          Consumer<NetSocket> certificateChainChecker = socket -> {
            try {
              List<Certificate> certs = socket.peerCertificates();
              if (clientCert != Cert.NONE) {
                assertNotNull(certs);
                assertEquals(1, certs.size());
              } else {
                assertNull(certs);
              }
            } catch (SSLPeerUnverifiedException e) {
              assertTrue(clientTrust.get() != Trust.NONE || clientTrustAll);
            }
          };
          server = vertx.createNetServer(setupServer());
          if (!shouldPass) {
            server.exceptionHandler(err -> complete());
          }
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
            socket.handler(buff -> {
              if (startTLS) {
                if (upgradedServer.compareAndSet(false, true)) {
                  indicatedServerName = socket.indicatedServerName();
                  assertFalse(socket.isSsl());
                  Context ctx = Vertx.currentContext();
                  Handler<AsyncResult<Void>> handler;
                  if (shouldPass) {
                    handler = onSuccess(v -> {
                      assertSame(ctx, Vertx.currentContext());
                      certificateChainChecker.accept(socket);
                      assertTrue(socket.isSsl());
                    });
                  } else {
                    handler = onFailure(err -> {
                      assertSame(ctx, Vertx.currentContext());
                      complete();
                    });
                  }
                  socket.upgradeToSsl(buff).onComplete(handler);
                } else {
                  assertTrue(socket.isSsl());
                  socket.write(buff);
                }
              } else {
                assertTrue(socket.isSsl());
                socket.write(buff);
              }
            });
          };
          server.connectHandler(serverHandler);
          server
            .listen(bindAddress)
            .<Void>mapEmpty()
            .onComplete(startPromise);
        }
      });
      bind.onComplete(onSuccess(ar -> {
        client.close();
        NetClientOptions clientOptions = createNetClientOptions();
        if (!startTLS) {
          clientOptions.setSsl(true);
        }
        if (clientTrustAll) {
          clientOptions.setTrustAll(true);
        }
        clientOptions.setEnabledSecureTransportProtocols(clientVersions);
        clientOptions.setHostnameVerificationAlgorithm("");
        clientOptions.setTrustOptions(clientTrust.get());
        clientOptions.setKeyCertOptions(clientCert.get());
        for (String suite: enabledCipherSuites) {
          clientOptions.addEnabledCipherSuite(suite);
        }
        client = vertx.createNetClient(clientOptions);
        Future<NetSocket> socketFuture = client.connect(connectAddress, serverName);
        String tls1_3 = "TLSv1.3";
        boolean clientAuthDeferred = clientVersions.contains(tls1_3) && serverVersions.contains(tls1_3);
        if (shouldPass || startTLS) {
          Future<Void> f = socketFuture.compose(socket -> {
            Promise<Void> result = Promise.promise();
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

            if (socket.isSsl()) {
              try {
                clientPeerCert = socket.peerCertificates().get(0);
              } catch (SSLPeerUnverifiedException ignore) {
              }
            }

            final AtomicBoolean upgradedClient = new AtomicBoolean();
            socket.exceptionHandler(result::tryFail);
            socket.handler(buffer -> {
              received.appendBuffer(buffer);
              if (received.length() == expected.length()) {
                assertEquals(expected, received);
                complete();
              }
              if (startTLS && !upgradedClient.get()) {
                upgradedClient.set(true);
                assertFalse(socket.isSsl());
                Future<Void> fut;
                if (serverName != null) {
                  fut = socket.upgradeToSsl(serverName);
                } else {
                  fut = socket.upgradeToSsl();
                }
                if (shouldPass) {
                  fut.onSuccess(v -> {
                    assertTrue(socket.isSsl());
                    try {
                      clientPeerCert = socket.peerCertificates().get(0);
                    } catch (SSLPeerUnverifiedException ignore) {
                    }
                    // Now send the rest
                    for (int i = 1; i < numChunks; i++) {
                      socket.write(toSend.get(i));
                    }
                  });
                } else {
                  fut.onFailure(v -> result.complete());
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

            return result.future();
          });

          f.onComplete(onSuccess(v -> complete()));
        } else {
          if (clientAuthDeferred) {
            socketFuture.onComplete(onSuccess(socket -> {
              socket.exceptionHandler(err -> {
                complete();
              });
            }));
          } else {
            socketFuture.onComplete(onFailure(v -> complete()));
          }
        }
      }));
    }
  }

  @Test
  public void testListenDomainSocketAddressNative() throws Exception {
    VertxInternal vx = (VertxInternal) Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    assumeTrue("Native transport must be enabled", vx.isNativeTransportEnabled());
    testListenDomainSocketAddress(vx);
  }

  @Test
  public void testListenDomainSocketAddressJdk() throws Exception {
    VertxInternal vx = (VertxInternal) Vertx.vertx(new VertxOptions().setPreferNativeTransport(false));
    assumeFalse("Native transport must not be enabled", vx.isNativeTransportEnabled());
    testListenDomainSocketAddress(vx);
  }

  private void testListenDomainSocketAddress(VertxInternal vx) throws Exception {
    assumeTrue("Transport must support domain sockets", vx.transport().supportsDomainSockets());
    int len = 3;
    waitFor(len * len);
    List<SocketAddress> addresses = new ArrayList<>();
    for (int i = 0;i < len;i++) {
      File sockFile = TestUtils.tmpFile(".sock");
      SocketAddress sockAddress = SocketAddress.domainSocketAddress(sockFile.getAbsolutePath());
      NetServer server = vx
        .createNetServer(createNetServerOptions())
        .connectHandler(so -> {
          so.end(Buffer.buffer(sockAddress.path()));
        });
      startServer(sockAddress, server);
      addresses.add(sockAddress);
    }
    NetClient client = vx.createNetClient(createNetClientOptions());
    for (int i = 0;i < len;i++) {
      for (int j = 0;j < len;j++) {
        SocketAddress sockAddress = addresses.get(i);
        client.connect(sockAddress).onComplete(onSuccess(so -> {
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
    int numServers = Math.max(2, VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2- 1);
    int numConnections = numServers * (domainSocket ? 10 : 20);

    List<NetServer> servers = new ArrayList<>();
    Set<NetServer> connectedServers = ConcurrentHashMap.newKeySet();
    Set<Thread> threads = ConcurrentHashMap.newKeySet();

    Future<String> listenLatch = vertx.deployVerticle(() -> new AbstractVerticle() {
      NetServer server;
      @Override
      public void start(Promise<Void> startPromise) {
        server = vertx.createNetServer(createNetServerOptions());
        servers.add(server);
        server.connectHandler(sock -> {
          threads.add(Thread.currentThread());
          connectedServers.add(server);
          sock.write("dummy");
        }).listen(testAddress).onComplete(onSuccess(v -> startPromise.complete()));
      }
    }, new DeploymentOptions().setInstances(numServers));
    awaitFuture(listenLatch);

    // Create a bunch of connections
    client.close();
    client = vertx.createNetClient(createNetClientOptions());
    for (int i = 0; i < numConnections; i++) {
      awaitFuture(client.connect(testAddress));
    }
    assertWaitUntil(() -> numServers == connectedServers.size());
    assertEquals(numServers, threads.size());
    for (NetServer server : servers) {
      assertTrue(connectedServers.contains(server));
    }
  }

  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    // Have a server running on a different port to make sure it doesn't interact
    server.close();
    server = vertx.createNetServer(createNetServerOptions().setPort(4321));
    server.connectHandler(sock -> {
      fail("Should not connect");
    }).listen().onComplete(ar2 -> {
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
    server = vertx.createNetServer(createNetServerOptions());
    server.connectHandler(sock -> {
      fail("Should not connect");
    }).listen(testAddress).onComplete(ar -> {
      if (ar.succeeded()) {
        latch.countDown();
      } else {
        fail("Failed to bind server");
      }
    });
    awaitLatch(latch);
    CountDownLatch closeLatch = new CountDownLatch(1);
    server.close().onComplete(onSuccess(v -> closeLatch.countDown()));
    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));

    Thread.sleep(500); // Let some time

    testSharedServersRoundRobin();
  }

  @Test
  public void testClosingVertxCloseSharedServers() throws Exception {
    int numServers = 2;
    Vertx vertx = createVertx(getOptions());
    List<NetServerImpl> servers = new ArrayList<>();
    for (int i = 0;i < numServers;i++) {
      NetServer server = vertx.createNetServer(createNetServerOptions()).connectHandler(so -> {
        fail();
      });
      startServer(server);
      servers.add((NetServerImpl) server);
    }
    CountDownLatch latch = new CountDownLatch(1);
    vertx.close().onComplete(onSuccess(v -> latch.countDown()));
    awaitLatch(latch);
    servers.forEach(server -> {
      assertTrue(server.isClosed());
    });
  }

  @Test
  public void testWriteHandlerIdNullByDefault() throws Exception {
    Buffer hello = Buffer.buffer("hello");
    Buffer bye = Buffer.buffer("bye");
    server.connectHandler(socket -> {
      assertNull(socket.writeHandlerID());
      socket.handler(data -> {
        assertEquals(hello, data);
        socket.end(bye);
      });
    });
    startServer();
    waitFor(2);
    client.connect(testAddress).onComplete(onSuccess(socket -> {
      assertNull(socket.writeHandlerID());
      socket
        .closeHandler(v -> complete())
        .handler(data -> {
          assertEquals(bye, data);
          complete();
        })
        .write(hello);
    }));
    await();
  }

  @Test
  // This tests using NetSocket.writeHandlerID (on the server side)
  // Send some data and make sure it is fanned out to all connections
  public void testFanout() throws Exception {
    server.close();
    server = vertx.createNetServer(createNetServerOptions().setRegisterWriteHandler(true));

    int numConnections = 10;

    Set<String> connections = ConcurrentHashMap.newKeySet();
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
      client.connect(testAddress).onComplete(onSuccess(socket -> {
        socket.handler(data -> {
          receivedLatch.countDown();
        });
      }));
    }
    assertTrue(receivedLatch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void  testSocketAddress() {
    server.connectHandler(socket -> {
      SocketAddress addr = socket.localAddress();
      if (addr.isInetSocket()) {
        assertEquals("127.0.0.1", addr.host());
        assertEquals(null, addr.hostName());
        assertEquals("127.0.0.1", addr.hostAddress());
      } else {
        assertEquals(testAddress.path(), addr.path());
        assertEquals(testAddress.path(), socket.remoteAddress().path());
      }
      socket.close();
    }).listen(testAddress).onComplete(onSuccess(v -> {
      vertx.createNetClient(createNetClientOptions()).connect(testAddress).onComplete(onSuccess(socket -> {
        SocketAddress addr = socket.remoteAddress();
        if (addr.isInetSocket()) {
          assertEquals("localhost", addr.host());
          assertEquals("localhost", addr.hostName());
          assertEquals("127.0.0.1", addr.hostAddress());
          assertEquals(addr.port(), 1234);
        } else {
          assertEquals(testAddress.path(), addr.path());
          assertEquals(testAddress.path(), socket.localAddress().path());
        }
        socket.closeHandler(v2 -> testComplete());
      }));
    }));
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
    }).listen(testAddress).onComplete(onSuccess(v -> {
      client.connect(testAddress).onComplete(onSuccess(socket -> {
        Buffer buff = Buffer.buffer("foo");
        socket.write(buff);
        socket.write(buff);
      }));
    }));
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
    server.listen(testAddress).onComplete(onSuccess(v -> {
      client.connect(testAddress).onComplete(onSuccess(sock -> {
        sock.handler(buf -> {
          sock.sendFile(file.getAbsolutePath());
        });
      }));
    }));

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
    server.listen(testAddress).onComplete(onSuccess(v -> {
      client.connect(testAddress).onComplete(onSuccess(sock -> {
        sock.handler(buff -> {
          received.appendBuffer(buff);
          if (received.length() == expected.length()) {
            assertEquals(expected, received);
            testComplete();
          }
        });
        sock.write("foo");
      }));
    }));

    await();
  }

  @Test
  public void testSendFileDirectory() throws Exception {
    File fDir = testFolder.newFolder();
    server.connectHandler(socket -> {
      socket.handler(buff -> {
        fail("Should not receive any data");
      });
    }).listen(testAddress).onComplete(onSuccess(v -> {
      client.connect(testAddress).onComplete(onSuccess(socket -> {
        socket.sendFile(fDir.getAbsolutePath()).onComplete(onFailure(err -> {
          assertEquals(FileNotFoundException.class, err.getClass());
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testServerOptionsCopiedBeforeUse() {
    server.close();
    NetServerOptions options = createNetServerOptions().setPort(1234);
    server = vertx.createNetServer(options);
    // Now change something - but server should still listen at previous port
    options.setPort(1235);
    server.connectHandler(sock -> {
      testComplete();
    });
    server.listen().onComplete(onSuccess(v -> {
      client.connect(1234, "localhost").onComplete(onSuccess(v2 -> {}));
    }));
    await();
  }

  @Test
  public void testClientOptionsCopiedBeforeUse() {
    client.close();
    NetClientOptions options = createNetClientOptions();
    client = vertx.createNetClient(options);
    options.setSsl(true);
    // Now change something - but server should ignore this
    server.connectHandler(sock -> {
      testComplete();
    });
    server.listen(1234, "localhost").onComplete(onSuccess(v1 -> {
      client.connect(1234, "localhost").onComplete(onSuccess(v2 -> {}));
    }));
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
      server.listen(testAddress).onComplete(onFailure(err -> {}));
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testSetHandlerAfterListen() {
    server.connectHandler(sock -> {
    });
    server.listen(testAddress).onComplete(onSuccess(v -> testComplete()));
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
    server.listen(testAddress).onComplete(onSuccess(v -> {
      try {
        server.connectHandler(sock -> {
        });
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }
      testComplete();
    }));
    await();
  }

  @Test
  public void testListenTwice() {
    server.connectHandler(sock -> {
    });
    server.listen(testAddress).onComplete(onSuccess(s -> {
      try {
        server.listen(testAddress);
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
    server.listen(1234).onComplete(onSuccess(ns -> {
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
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
    server.listen(testAddress).onComplete(onSuccess(ns -> {
      client.connect(testAddress).onComplete(onSuccess(so -> {
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
    server.listen(testAddress).onComplete(onSuccess(v -> {
      try {
        server.listen(testAddress);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }
      testComplete();
    }));
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
    client.connect(testAddress).onComplete(onFailure(err -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testCloseWithHandler() {
    waitFor(2);
    server.connectHandler(so -> {
      so.closeHandler(v -> {
        complete();
      });
    }).listen(testAddress).onComplete(onSuccess(s -> {
      client.connect(testAddress).onComplete(onSuccess(so -> {
        so.close().onComplete(onSuccess(v -> {
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
    }).listen(testAddress).onComplete(onSuccess(c -> {
      for (int i = 0; i < numThreads; i++) {
        threads[i] = new Thread(() -> client.connect(testAddress).onComplete(onSuccess(sock -> {
          Buffer buff = randomBuffer(100000);
          sock.write(buff);
          Buffer received = Buffer.buffer();
          sock.handler(rec -> {
            received.appendBuffer(rec);
            if (received.length() == buff.length()) {
              assertEquals(buff, received);
              latch.countDown();
            }
          });
        })));
        threads[i].start();
      }
    }));
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
        server = vertx.createNetServer(createNetServerOptions());
        server.connectHandler(sock -> {
          sock.handler(buff -> {
            sock.write(buff);
          });
          assertSame(ctx, context);
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
        });
        server.listen(testAddress).onComplete(onSuccess(ar -> {
          assertSame(ctx, context);
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
          client = vertx.createNetClient(createNetClientOptions());
          client.connect(testAddress).onComplete(onSuccess(sock -> {
            assertSame(ctx, context);
            if (!worker) {
              assertSame(thr, Thread.currentThread());
            }
            Buffer buff = TestUtils.randomBuffer(maxPacketSize());
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
          }));
        }));
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(worker ? ThreadingModel.WORKER : ThreadingModel.EVENT_LOOP));
    await();
  }

  protected int maxPacketSize() {
    return 10000;
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
    server.listen(testAddress).onComplete(onSuccess(v -> {
      listenContext.set(Vertx.currentContext());
      listenLatch.countDown();
    }));
    awaitLatch(listenLatch);

    Set<Context> contexts = ConcurrentHashMap.newKeySet();
    AtomicInteger connectCount = new AtomicInteger();
    CountDownLatch clientLatch = new CountDownLatch(1);
    // Each connect should be in its own context
    for (int i = 0; i < numConnections; i++) {
      Context context = ((VertxInternal)vertx).createEventLoopContext();
      context.runOnContext(v -> {
        client.connect(testAddress).onComplete(conn -> {
          contexts.add(Vertx.currentContext());
          if (connectCount.incrementAndGet() == numConnections) {
            assertEquals(numConnections, contexts.size());
            clientLatch.countDown();
          }
        });
      });
    }
    awaitLatch(clientLatch);
    awaitLatch(serverLatch);

    // Close should be in own context
    server.close().onComplete(onSuccess(ar -> {
//      Context closeContext = Vertx.currentContext();
//      assertFalse(contexts.contains(closeContext));
      assertFalse(contexts.contains(listenContext.get()));
      assertSame(serverConnectContext.get(), listenContext.get());
      testComplete();
    }));

    await();
  }

  @Test
  public void testMultipleServerClose() {
    this.server = vertx.createNetServer(createNetServerOptions());
    // We assume the endHandler and the close completion handler are invoked in the same context task
    ThreadLocal stack = new ThreadLocal();
    stack.set(true);
    server.close().onComplete(ar1 -> {
//      assertNull(stack.get());
//      assertTrue(Vertx.currentContext().isEventLoopContext());
      server.close().onComplete(ar2 -> {
        server.close().onComplete(ar3 -> {
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
        NetServer server1 = vertx.createNetServer(createNetServerOptions());
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
        }).listen(testAddress).onComplete(onSuccess(s -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          assertSame(context, Vertx.currentContext());
          NetClient client = vertx.createNetClient(createNetClientOptions());
          client.connect(testAddress).onComplete(onSuccess(res -> {
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
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
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
        client.connect(testAddress).onComplete(onSuccess(so -> {
          so.handler(buff -> {
            assertEquals(expected, buff);
            so.close();
            int val = done.incrementAndGet();
            if (val == num) {
              testComplete();
            }
          });
          so.write(TestUtils.randomBuffer(256));
        }));
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
      NetServer server = vertx.createNetServer(createNetServerOptions());
      server.connectHandler(so -> {
        so.handler(buf -> {
          assertEquals("hello", buf.toString());
          testComplete();
        });
      });
      server.listen(testAddress).onComplete(onSuccess(v2 -> {
        // Create a one second worker starvation
        for (int i = 1; i < workers.size(); i++) {
          workers.get(i).runOnContext(v3 -> {
            latch1.countDown();
            try {
              Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
          });
        }
      }));
    });
    awaitLatch(latch1);
    NetClient client = vertx.createNetClient(createNetClientOptions());
    client.connect(testAddress).onComplete(onSuccess(so -> {
      so.write(Buffer.buffer("hello"));
    }));
    await();
  }

  @Test
  public void testClientWorkerMissBufferWhenBufferArriveBeforeConnectCallback() throws Exception {
    int size = getOptions().getWorkerPoolSize();
    List<Context> workers = createWorkers(size + 1);
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(size);
    NetServer server = vertx.createNetServer(createNetServerOptions());
    server.connectHandler(so -> {
      try {
        awaitLatch(latch2);
      } catch (InterruptedException e) {
        fail(e.getMessage());
        return;
      }
      so.write(Buffer.buffer("hello"));
    });
    server.listen(testAddress).onComplete(onSuccess(v -> {
      latch1.countDown();
    }));
    awaitLatch(latch1);
    workers.get(0).runOnContext(v -> {
      NetClient client = vertx.createNetClient(createNetClientOptions());
      client.connect(testAddress).onComplete(onSuccess(so -> {
        so.handler(buf -> {
          assertEquals("hello", buf.toString());
          testComplete();
        });
      }));
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
    NetServerOptions options = createNetServerOptions()
            .setPort(1234)
            .setHost("localhost")
            .setSsl(true)
            .setKeyCertOptions(new JksOptions().setPath("tls/mim-server-keystore.jks").setPassword("wibble"));
    NetServer server = vertx.createNetServer(options);

    NetClientOptions clientOptions = createNetClientOptions()
            .setSsl(true)
            .setTrustAll(true)
            .setHostnameVerificationAlgorithm("HTTPS");
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    server
      .listen()
      .compose(v -> client.connect(1234, "localhost"))
      .onComplete(onFailure(err -> {
      //Should not be able to connect
      testComplete();
    }));
    await();
  }

  // this test sets HostnameVerification but also trustAll, it fails if hostname is
  // incorrect but does not verify the certificate validity

  @Test
  public void testHostVerificationHttpsMatching() {
    server.close();
    NetServerOptions options = createNetServerOptions()
            .setPort(1234)
            .setHost("localhost")
            .setSsl(true)
            .setKeyCertOptions(new JksOptions().setPath("tls/server-keystore.jks").setPassword("wibble"));
    NetServer server = vertx.createNetServer(options);

    NetClientOptions clientOptions = createNetClientOptions()
            .setSsl(true)
            .setTrustAll(true)
            .setHostnameVerificationAlgorithm("HTTPS");
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    server.listen().onComplete(onSuccess(v -> {
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        //Should be able to connect
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testClientMissingHostnameVerificationAlgorithm1() {
    testClientMissingHostnameVerificationAlgorithm(client -> client.connect(1234, "localhost"));
  }

  @Test
  public void testClientMissingHostnameVerificationAlgorithm2() {
    testClientMissingHostnameVerificationAlgorithm(client -> client.connect(new ConnectOptions()
      .setHost("localhost")
      .setPort(1234)
      .setSsl(true)));
  }

  @Test
  public void testClientMissingHostnameVerificationAlgorithm3() {
    testClientMissingHostnameVerificationAlgorithm(client -> client.connect(new ConnectOptions()
      .setHost("localhost")
      .setPort(1234)).compose(so -> so.upgradeToSsl(new ClientSSLOptions())));
  }

  private void testClientMissingHostnameVerificationAlgorithm(Function<NetClient, Future<?>> consumer) {
    server.close();
    NetServerOptions options = createNetServerOptions()
      .setPort(1234)
      .setHost("localhost")
      .setSsl(true)
      .setKeyCertOptions(new JksOptions().setPath("tls/server-keystore.jks").setPassword("wibble"));
    NetServer server = vertx.createNetServer(options);

    NetClientOptions clientOptions = createNetClientOptions()
      .setSsl(true)
      .setTrustAll(true);
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    server.listen().onComplete(onSuccess(v -> {
      consumer.apply(client).onComplete(onFailure(err -> {
        assertTrue(err.getMessage().contains("Missing hostname verification algorithm"));
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testMissingClientSSLOptions() throws Exception {
    server = vertx.createNetServer(createNetServerOptions()
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS.get()))
      .connectHandler(conn -> {
        fail();
      });
    startServer(testAddress);
    client.connect(new ConnectOptions().setPort(8443).setHost("localhost").setSsl(true)).onComplete(onFailure(err -> {
      assertTrue(err.getMessage().contains("ClientSSLOptions"));
      testComplete();
    }));
    await();
  }

  @Test
  public void testReuseDefaultClientSSLOptions() throws Exception {
    waitFor(2);
    server = vertx.createNetServer(createNetServerOptions()
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS.get()))
      .connectHandler(conn -> {
        complete();
      });
    startServer(testAddress);
    client = vertx.createNetClient(createNetClientOptions().setTrustAll(true).setHostnameVerificationAlgorithm(""));
    client.connect(new ConnectOptions().setRemoteAddress(testAddress).setSsl(true)).onComplete(onSuccess(so -> {
      complete();
    }));
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
    server = vertx.createNetServer(createNetServerOptions().setLogActivity(true));
    TestLoggerFactory factory = testLogging();
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testClientLogging() throws Exception {
    client.close();
    client = vertx.createNetClient(createNetClientOptions().setLogActivity(true));
    TestLoggerFactory factory = testLogging();
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  private TestLoggerFactory testLogging() throws Exception {
    return TestUtils.testLogging(() -> {
      server.connectHandler(so -> {
        so.end(Buffer.buffer("fizzbuzz"));
      });
      server.listen(testAddress).onComplete(onSuccess(v1 -> {
        client.connect(testAddress).onComplete(onSuccess(so -> {
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
    NetClientOptions clientOptions = createNetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setPort(11080));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new SocksProxy();
    proxy.start(vertx);
    server.listen(1234, "localhost")
      .onComplete(onSuccess(v -> {
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        // make sure we have gone through the proxy
        assertEquals("localhost:1234", proxy.getLastUri());
        testComplete();
      }));
    }));
    await();
  }

  /**
   * test socks5 proxy for accessing arbitrary server port with authentication.
   */
  @Test
  public void testWithSocks5ProxyAuth() throws Exception {
    NetClientOptions clientOptions = createNetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setPort(11080)
            .setUsername("username").setPassword("username"));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new SocksProxy().username("username");
    proxy.start(vertx);
    server.listen(1234, "localhost").onComplete(onSuccess(c -> {
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        testComplete();
      }));
    }));
    await();
  }

  /**
   * test socks5 proxy when accessing ssl server port with correct cert.
   */
  @Test
  public void testConnectSSLWithSocks5Proxy() throws Exception {
    server.close();
    NetServerOptions options = createNetServerOptions()
        .setPort(1234)
        .setHost("localhost")
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get());
    server = vertx.createNetServer(options);

    NetClientOptions clientOptions = createNetClientOptions()
        .setHostnameVerificationAlgorithm("HTTPS")
        .setSsl(true)
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("127.0.0.1").setPort(11080))
        .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new SocksProxy();
    proxy.start(vertx);
    server.listen().onComplete(onSuccess(v -> {
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        testComplete();
      }));
    }));
    await();
  }

  /**
   * test socks5 proxy for accessing ssl server port with upgradeToSsl.
   * https://github.com/eclipse/vert.x/issues/1602
   */
  @Test
  public void testUpgradeSSLWithSocks5Proxy() throws Exception {
    server.close();
    NetServerOptions options = createNetServerOptions()
        .setPort(1234)
        .setHost("localhost")
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get());
    server = vertx.createNetServer(options);

    NetClientOptions clientOptions = createNetClientOptions()
        .setHostnameVerificationAlgorithm("HTTPS")
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("127.0.0.1").setPort(11080))
        .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new SocksProxy();
    proxy.start(vertx);
    server.listen().onComplete(onSuccess(v -> {
      client.connect(1234, "localhost").onComplete(onSuccess(ns -> {
        ns.upgradeToSsl().onComplete(onSuccess(v2 -> {
          testComplete();
        }));
      }));
    }));
    await();
  }

  /**
   * test http connect proxy for accessing a arbitrary server port
   * note that this may not work with a "real" proxy since there are usually access rules defined
   * that limit the target host and ports (e.g. connecting to localhost or to port 25 may not be allowed)
   */
  @Test
  public void testWithHttpConnectProxy() throws Exception {
    NetClientOptions clientOptions = createNetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setPort(13128));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new HttpProxy();
    proxy.start(vertx);
    server.listen(1234, "localhost").onComplete(onSuccess(ar -> {
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        // make sure we have gone through the proxy
        assertEquals("localhost:1234", proxy.getLastUri());
        testComplete();
      }));
    }));
    await();
  }

  /**
   * test socks4a proxy for accessing arbitrary server port.
   */
  @Test
  public void testWithSocks4aProxy() throws Exception {
    NetClientOptions clientOptions = createNetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS4).setPort(11080));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new Socks4Proxy();
    proxy.start(vertx);
    server.listen(1234, "localhost").onComplete(onSuccess(v -> {
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        // make sure we have gone through the proxy
        assertEquals("localhost:1234", proxy.getLastUri());
        testComplete();
      }));
    }));
    await();
  }

  /**
   * test socks4a proxy for accessing arbitrary server port using username auth.
   */
  @Test
  public void testWithSocks4aProxyAuth() throws Exception {
    NetClientOptions clientOptions = createNetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS4).setPort(11080)
            .setUsername("username"));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new Socks4Proxy().username("username");
    proxy.start(vertx);
    server.listen(1234, "localhost").onComplete(onSuccess(v -> {
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        // make sure we have gone through the proxy
        assertEquals("localhost:1234", proxy.getLastUri());
        testComplete();
      }));
    }));
    await();
  }

  /**
   * test socks4a proxy for accessing arbitrary server port using an already resolved address.
   */
  @Test
  public void testWithSocks4LocalResolver() throws Exception {
    NetClientOptions clientOptions = createNetClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS4).setPort(11080));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new Socks4Proxy().start(vertx);
    server.listen(1234, "localhost").onComplete(onSuccess(v -> {
      client.connect(1234, "127.0.0.1").onComplete(onSuccess(so -> {
        // make sure we have gone through the proxy
        assertEquals("127.0.0.1:1234", proxy.getLastUri());
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testNonProxyHosts() throws Exception {
    NetClientOptions clientOptions = createNetClientOptions()
      .addNonProxyHost("example.com")
      .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setPort(13128));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new HttpProxy();
    proxy.start(vertx);
    server.listen(1234, "localhost").onComplete(onSuccess(s -> {
      client.connect(1234, "example.com").onComplete(onSuccess(so -> {
        assertNull(proxy.getLastUri());
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testTLSHostnameCertCheckCorrect() {
    server.close();
    server = vertx.createNetServer(createNetServerOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get()));
    server.connectHandler(netSocket -> netSocket.close()).listen().onComplete(onSuccess(v -> {

      NetClientOptions options = createNetClientOptions()
          .setHostnameVerificationAlgorithm("HTTPS")
          .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());

      NetClient client = vertx.createNetClient(options);

      client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST).onComplete(onSuccess(ns -> {
        ns.upgradeToSsl().onComplete(onSuccess(v2 -> {
          testComplete();
        }));
      }));
    }));

    await();
  }

  @Test
  public void testTLSHostnameCertCheckIncorrect() {
    server.close();
    server = vertx.createNetServer(createNetServerOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get()));
    server.connectHandler(netSocket -> netSocket.close()).listen().onComplete(onSuccess(v -> {

      NetClientOptions options = createNetClientOptions()
          .setHostnameVerificationAlgorithm("HTTPS")
          .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());

      NetClient client = vertx.createNetClient(options);

      client.connect(DEFAULT_HTTPS_PORT, "127.0.0.1").onComplete(onSuccess(ns -> {
        ns.upgradeToSsl().onComplete(onFailure(err -> {
          testComplete();
        }));
      }));
    }));

    await();
  }

  /**
   * Test that NetSocket.upgradeToSsl() should fail the handler if no TLS configuration was set.
   */
  @Test
  public void testUpgradeToSSLIncorrectClientOptions1() {
    NetClient client = vertx.createNetClient(createNetClientOptions());
    try {
      testUpgradeToSSLIncorrectClientOptions(() -> client.connect(DEFAULT_HTTPS_PORT, "127.0.0.1"));
    } finally {
      client.close();
    }
  }

  /**
   * Test that NetSocket.upgradeToSsl() should fail the handler if no TLS configuration was set.
   */
  @Test
  public void testUpgradeToSSLIncorrectClientOptions2() {
    NetClient client = vertx.createNetClient(createNetClientOptions());
    try {
      testUpgradeToSSLIncorrectClientOptions(() -> client.connect(new ConnectOptions().setPort(DEFAULT_HTTPS_PORT).setHost("127.0.0.1")));
    } finally {
      client.close();
    }
  }

  /**
   * Test that NetSocket.upgradeToSsl() should fail the handler if no TLS configuration was set.
   */
  private void testUpgradeToSSLIncorrectClientOptions(Supplier<Future<NetSocket>> connect) {
    server.close();
    server = vertx.createNetServer(createNetServerOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT)
      .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get()));
    server.connectHandler(ns -> {}).listen().onComplete(onSuccess(v -> {
      connect.get().onComplete(onSuccess(ns -> {
        ns.upgradeToSsl().onComplete(onFailure(err -> {
          assertTrue(err.getMessage().contains("Missing SSL options"));
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testOverrideClientSSLOptions() {
    waitFor(4);
    server.close();
    server = vertx.createNetServer(createNetServerOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT)
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    server.connectHandler(ns -> {
      complete();
    }).listen().onComplete(onSuccess(v -> {
      NetClient client = vertx.createNetClient(createNetClientOptions()
        .setTrustOptions(Trust.CLIENT_JKS.get()));
      client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST).onComplete(onSuccess(ns -> {
        ns.upgradeToSsl().onComplete(onFailure(err -> {
          ClientSSLOptions sslOptions = new ClientSSLOptions().setHostnameVerificationAlgorithm("").setTrustOptions(Trust.SERVER_JKS.get());
          client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST).onComplete(onSuccess(ns2 -> {
            ns2.upgradeToSsl(sslOptions).onComplete(onSuccess(v2 -> {
              complete();
            }));
          }));
          client.connect(new ConnectOptions().setPort(DEFAULT_HTTPS_PORT).setHost(DEFAULT_HTTPS_HOST).setSslOptions(sslOptions)).onComplete(onSuccess(ns2 -> {
            ns2.upgradeToSsl().onComplete(onSuccess(v2 -> {
              complete();
            }));
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testClientLocalAddress() {
    String expectedAddress = TestUtils.loopbackAddress();
    NetClientOptions clientOptions = createNetClientOptions().setLocalAddress(expectedAddress);
    client.close();
    client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {
      assertEquals(expectedAddress, sock.remoteAddress().host());
      sock.close();
    });
    server.listen(1234, "localhost").onComplete(onSuccess(v -> {
      client.connect(1234, "localhost").onComplete(onSuccess(socket -> {
        socket.closeHandler(v2 -> {
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testSelfSignedCertificate() throws Exception {
    assumeTrue(PlatformDependent.javaVersion() < 9);

    CountDownLatch latch = new CountDownLatch(2);

    SelfSignedCertificate certificate = SelfSignedCertificate.create();

    NetServerOptions serverOptions = createNetServerOptions()
      .setSsl(true)
      .setKeyCertOptions(certificate.keyCertOptions())
      .setTrustOptions(certificate.trustOptions());

    NetClientOptions clientOptions = createNetClientOptions()
      .setSsl(true)
      .setKeyCertOptions(certificate.keyCertOptions())
      .setTrustOptions(certificate.trustOptions());

    NetClientOptions clientTrustAllOptions = createNetClientOptions()
      .setSsl(true)
      .setTrustAll(true);

    server = vertx.createNetServer(serverOptions)
      .connectHandler(socket -> {
        socket.end(Buffer.buffer("123"));
      });
     server.listen(testAddress).onComplete(onSuccess(s -> {

        client = vertx.createNetClient(clientOptions);
        client.connect(testAddress).onComplete(onSuccess(socket -> {
          socket.handler(buffer -> {
            assertEquals("123", buffer.toString());
            latch.countDown();
          });
        }));

        client = vertx.createNetClient(clientTrustAllOptions);
        client.connect(testAddress).onComplete(onSuccess(socket -> {
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
        NetClient client = vertx.createNetClient(createNetClientOptions());
        client.connect(testAddress).onComplete(onSuccess(so ->{
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
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    await();
  }

  @Test
  public void testWorkerServer() {
    String expected = TestUtils.randomAlphaString(2000);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        NetServer server = vertx.createNetServer(createNetServerOptions());
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
        server.listen(testAddress).<Void>mapEmpty().onComplete(startPromise);
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)).onComplete(onSuccess(v -> {
      client.connect(testAddress).onComplete(onSuccess(so -> {
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
    server = vertx.createNetServer(createNetServerOptions()
      .setPort(1234)
      .setHost("localhost")
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    testNetServerInternal_(new HttpClientOptions()
      .setSsl(true)
      .setTrustOptions(Trust.SERVER_JKS.get())
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
          internal.writeMessage(response).onComplete(onSuccess(v -> complete()));
        }
      });
    });
    startServer(SocketAddress.inetSocketAddress(1234, "localhost"));
    HttpClient client = vertx.createHttpClient(clientOptions);
    client.request(io.vertx.core.http.HttpMethod.GET, 1234, "localhost", "/somepath")
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)).onComplete(onSuccess(body -> {
        assertEquals("Hello World", body.toString());
        complete();
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
    client = vertx.createNetClient(createNetClientOptions()
      .setSsl(true)
      .setHostnameVerificationAlgorithm("")
      .setTrustOptions(Trust.SERVER_JKS.get())
    );
    testNetClientInternal_(createBaseServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get()), true);
  }

  // This test is here to cover a WildFly use case for passing in an SSLContext for which there are no
  // configuration options.
  // This is currently done by casing to NetClientImpl and calling setSuppliedSSLContext().
  @Test
  public void testNetClientInternalTLSWithSuppliedSSLContext() throws Exception {
    client.close();
    Buffer trust = vertx.fileSystem().readFileBlocking(Trust.SERVER_JKS.get().getPath());

    TrustManagerFactory tmFactory;
    try (InputStream trustStoreStream = new ByteArrayInputStream(trust.getBytes())){
      KeyStore trustStore = KeyStore.getInstance("jks");
      trustStore.load(trustStoreStream, Trust.SERVER_JKS.get().getPassword().toCharArray());
      tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmFactory.init(trustStore);
    }

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(
      null,
      tmFactory.getTrustManagers(),
      null
    );

    client = vertx.createNetClient(createNetClientOptions().setSsl(true).setHostnameVerificationAlgorithm("")
      .setSslEngineOptions(new JdkSSLEngineOptions() {
        @Override
        public SslContextFactory sslContextFactory() {
          return () -> new JdkSslContext(
            sslContext,
            true,
            null,
            IdentityCipherSuiteFilter.INSTANCE,
            ApplicationProtocolConfig.DISABLED,
            io.netty.handler.ssl.ClientAuth.NONE,
            null,
            false);
        }
      }));

    testNetClientInternal_(new HttpServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get()), true);
  }

  private void testNetClientInternal_(HttpServerOptions options, boolean expectSSL) throws Exception {
    waitFor(2);
    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      req.response().end("Hello World"); });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen().onComplete(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    client.connect(1234, "localhost").onComplete(onSuccess(so -> {
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
            assertTrue(content.isDirect());
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
      soInt.writeMessage(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/somepath")).onComplete(onSuccess(v -> complete()));
    }));
    await();
  }

  @Test
  public void testNetSocketInternalBuffer() throws Exception {
    server.connectHandler(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.handler(msg -> {
        ByteBuf byteBuf = ((BufferInternal)msg).getByteBuf();
        assertFalse(byteBuf.isDirect());
        assertEquals(1, byteBuf.refCnt());
        assertFalse(byteBuf.release());
        assertEquals(1, byteBuf.refCnt());
        soi.write(msg);
      });
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.write(Buffer.buffer("Hello World"));
      soi.handler(msg -> {
        ByteBuf byteBuf = ((BufferInternal)msg).getByteBuf();
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
  public void testNetSocketInternalDirectBuffer() throws Exception {
    waitFor(2);
    server.connectHandler(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.messageHandler(msg -> {
        ByteBuf byteBuf = (ByteBuf) msg;
        assertTrue(byteBuf.isDirect());
        assertEquals(1, byteBuf.refCnt());
        soi.writeMessage(msg).onSuccess(v -> {
          assertEquals(0, byteBuf.refCnt());
          complete();
        });
      });
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.write(Buffer.buffer("Hello World"));
      // soi.messageHandler(msg -> fail("Unexpected"));
      soi.messageHandler(msg -> {
        ByteBuf byteBuf = (ByteBuf) msg;
        assertTrue(byteBuf.isDirect());
        assertEquals(1, byteBuf.refCnt());
        assertEquals("Hello World", byteBuf.toString(StandardCharsets.UTF_8));
        assertTrue(byteBuf.release());
        assertEquals(0, byteBuf.refCnt());
        complete();
      });
    }));
    await();
  }

  @Test
  public void testNetSocketInternalRemoveVertxHandler() throws Exception {
    client.close();
    client = vertx.createNetClient(createNetClientOptions().setConnectTimeout(1000).setRegisterWriteHandler(true));

    server.connectHandler(so -> {
      so.closeHandler(v -> testComplete());
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      String id = soi.writeHandlerID();
      ChannelHandlerContext ctx = soi.channelHandlerContext();
      ChannelPipeline pipeline = ctx.pipeline();
      pipeline.remove(VertxHandler.class);
      vertx.eventBus().request(id, "test").onComplete(onFailure(what -> {
        ctx.close();
      }));
    }));
    await();
  }

  @Test
  public void testCloseCompletionHandlerNotCalledWhenActualServerFailed() {
    server.close();
    server = vertx.createNetServer(
      createNetServerOptions()
        .setSsl(true)
        .setKeyCertOptions(new PemKeyCertOptions().setKeyPath("invalid")))
      .connectHandler(c -> {
    });
    server.listen(10000).onComplete(onFailure(err -> {
      server.close().onComplete(onSuccess(v -> {
        testComplete();
      }));
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
    client.connect(testAddress).onComplete(onSuccess(so -> {
      vertx.setTimer(1000, id -> {
        so.close();
      });
    }));
    await();
  }

  @Test
  public void testNetSocketInternalEvent() throws Exception {
    server.connectHandler(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      Object expectedEvent = new Object();
      soi.eventHandler(event -> {
        assertSame(expectedEvent, event);
        soi.close();
      });
      ChannelPipeline pipeline = soi.channelHandlerContext().pipeline();
      pipeline.addFirst(new ChannelHandlerAdapter() {
        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
          super.handlerAdded(ctx);
          ctx.executor().schedule(() -> {
            ctx.fireUserEventTriggered(expectedEvent);
          }, 10, TimeUnit.MILLISECONDS);
        }
      });
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(so -> {
      so.closeHandler(v -> testComplete());
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
      so.sendFile(sent.getAbsolutePath()).onComplete(ar -> {
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
      .createNetServer(createNetServerOptions().setIdleTimeout(200).setIdleTimeoutUnit(TimeUnit.MILLISECONDS))
      .connectHandler((idleOnServer ? sender : receiver)::accept);
    startServer();
    client.close();
    client = vertx.createNetClient(createNetClientOptions().setIdleTimeout(200).setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
    client.connect(testAddress).onComplete(onSuccess(idleOnServer ? receiver : sender));
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
    vertx.runOnContext(v1 -> {
      client.connect(testAddress, "localhost").onComplete(onSuccess(so -> {
        so.pause();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean ended = new AtomicBoolean();
        Buffer received = Buffer.buffer();
        so.handler(received::appendBuffer);
        so.closeHandler(v2 -> {
          assertFalse(ended.get());
          assertEquals(Buffer.buffer(), received);
          closed.set(true);
          so.resume();
        });
        so.endHandler(v -> {
          assertEquals(expected.toString(), received.toString());
          assertTrue(closed.get());
          ended.set(true);
          testComplete();
        });
      }));
    });
    await();
  }

  @Test
  public void testSslHandshakeTimeoutHappenedOnServer() throws Exception {
    testSslHandshakeTimeoutHappened(false, false);
  }

  @Test
  public void testSslHandshakeTimeoutHappenedOnSniServer() throws Exception {
    testSslHandshakeTimeoutHappened(false, true);
  }

  public void testSslHandshakeTimeoutHappened(boolean onClient, boolean sni) throws Exception {
    server.close();
    client.close();

    // set up a normal server to force the SSL handshake time out in client
    NetServerOptions serverOptions = createNetServerOptions()
      .setSsl(!onClient)
      .setSslHandshakeTimeout(200)
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      .setSni(sni)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    server = vertx.createNetServer(serverOptions);

    NetClientOptions clientOptions = createNetClientOptions()
      .setSsl(onClient)
      .setTrustAll(true)
      .setSslHandshakeTimeout(200)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    client = vertx.createNetClient(clientOptions);

    Consumer<Throwable> checker = err -> {
      assertTrue(err instanceof SSLException);
      assertEquals("handshake timed out after 200ms", err.getMessage());
      testComplete();
    };

    if (!onClient) {
      server.exceptionHandler(checker::accept);
    }
    server.connectHandler(s -> {
    }).listen(testAddress).onComplete(onSuccess(s -> {
      client.connect(testAddress).onComplete(ar -> {
        if (onClient) {
          assertTrue(ar.failed());
          checker.accept(ar.cause());
        }
      });
    }));
    await();
  }

  @Test
  public void testSslHandshakeTimeoutNotHappened() throws Exception {
    server.close();
    client.close();

    NetServerOptions serverOptions = createNetServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      // set 100ms to let the connection established
      .setSslHandshakeTimeout(100)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    server = vertx.createNetServer(serverOptions);

    NetClientOptions clientOptions = createNetClientOptions()
      .setSsl(true)
      .setTrustAll(true)
      .setHostnameVerificationAlgorithm("");
    client = vertx.createNetClient(clientOptions);

    server.connectHandler(s -> {
    }).listen(testAddress).onComplete(onSuccess(v -> {
      client.connect(testAddress).onComplete(onSuccess(so -> {
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testSslHandshakeTimeoutHappenedWhenUpgradeSsl() {
    server.close();
    client.close();

    // set up a normal server to force the SSL handshake time out in client
    NetServerOptions serverOptions = createNetServerOptions()
      .setSsl(false);
    server = vertx.createNetServer(serverOptions);

    NetClientOptions clientOptions = createNetClientOptions()
      .setSsl(false)
      .setTrustAll(true)
      .setHostnameVerificationAlgorithm("")
      .setSslHandshakeTimeout(200)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    client = vertx.createNetClient(clientOptions);

    server.connectHandler(s -> {
    }).listen(testAddress).onComplete(onSuccess(v -> {
      client.connect(testAddress).onComplete(onSuccess(socket -> {
        assertFalse(socket.isSsl());
        socket.upgradeToSsl().onComplete(onFailure(err -> {
          assertTrue(err instanceof SSLException);
          assertEquals("handshake timed out after 200ms", err.getMessage());
          testComplete();
        }));
      }));
    }));
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
      server.listen(remoteAddress).onComplete(onSuccess(s -> latch.countDown()));
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
    client.connect(testAddress, "localhost").onComplete(onSuccess(so -> {
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
    client.connect(testAddress).onComplete(onSuccess(so -> {
      so.write("ping");
      so.close();
    }));
    await();
  }

  @Test
  public void testHAProxyProtocolIdleTimeout() throws Exception {
    HAProxy proxy = new HAProxy(testAddress, Buffer.buffer());
    proxy.start(vertx);

    server.close();
    server = vertx.createNetServer(createNetServerOptions()
      .setProxyProtocolTimeout(2)
      .setUseProxyProtocol(true))
      .connectHandler(u -> fail("Should not be called"));
    startServer();
    client.connect(proxy.getPort(), proxy.getHost())
      .onSuccess(so -> {
        so.closeHandler(event -> testComplete());
      })
      .onFailure(this::fail);
    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    waitFor(2);

    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server.close();
    server = vertx.createNetServer(createNetServerOptions()
      .setProxyProtocolTimeout(100)
      .setProxyProtocolTimeoutUnit(TimeUnit.MILLISECONDS)
      .setUseProxyProtocol(true))
      .connectHandler(u -> complete());
    startServer();
    client.connect(proxy.getPort(), proxy.getHost())
      .onSuccess(so -> {
        so.close();
        complete();
      })
      .onFailure(this::fail);
    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolConnectSSL() throws Exception {
    assumeTrue(testAddress.isInetSocket());
    waitFor(2);
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server.close();
    NetServerOptions options = createNetServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get())
      .setUseProxyProtocol(true);
    server = vertx.createNetServer(options)
      .connectHandler(event -> {
        assertAddresses(remote, event.remoteAddress());
        assertAddresses(remote, event.remoteAddress(false));
        assertAddresses(proxy.getConnectionLocalAddress(), event.remoteAddress(true));
        assertAddresses(local, event.localAddress());
        assertAddresses(local, event.localAddress(false));
        assertAddresses(USE_DOMAIN_SOCKETS ? null : SocketAddress.inetSocketAddress(server.actualPort(), "127.0.0.1"), event.localAddress(true));
        complete();
      });

    startServer();
    NetClientOptions clientOptions = createNetClientOptions()
      .setHostnameVerificationAlgorithm("HTTPS")
      .setSsl(true)
      .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());

    vertx.createNetClient(clientOptions)
      .connect(proxy.getPort(), proxy.getHost())
      .onSuccess(so -> {
        so.close();
        complete();
      })
      .onFailure(this::fail);
    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolVersion1TCP4() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion1TCP6() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion1TCP6ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion1Unknown() throws Exception {
    assumeTrue(testAddress.isInetSocket());
    Buffer header = HAProxy.createVersion1UnknownProtocolHeader();
    testHAProxyProtocolAccepted(header, null, null);
  }

  @Test
  public void testHAProxyProtocolVersion2TCP4() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion2TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2TCP6() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion2TCP6ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2UnixSocket() throws Exception {
    SocketAddress remote = SocketAddress.domainSocketAddress("/tmp/remoteSocket");
    SocketAddress local = SocketAddress.domainSocketAddress("/tmp/localSocket");
    Buffer header = HAProxy.createVersion2UnixStreamProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2Unknown() throws Exception {
    assumeTrue(testAddress.isInetSocket());
    Buffer header = HAProxy.createVersion2UnknownProtocolHeader();
    testHAProxyProtocolAccepted(header, null, null);
  }


  private void testHAProxyProtocolAccepted(Buffer header, SocketAddress remote, SocketAddress local) throws Exception {
    /*
     * In case remote / local is null then we will use the connected remote / local address from the proxy. This is needed
     * in order to test unknown protocol since we will use the actual connected addresses and ports.
     * This is only valid when testAddress is an InetSocketAddress. If testAddress is a DomainSocketAddress then
     * remoteAddress and localAddress are null
     *
     * Have in mind that proxies connectionRemoteAddress is the server request local address and proxies connectionLocalAddress is the
     * server request remote address.
     * */
    waitFor(2);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server.close();
    server = vertx.createNetServer(createNetServerOptions()
      .setUseProxyProtocol(true))
      .connectHandler(so -> {
        assertAddresses(remote == null && testAddress.isInetSocket() ?
            proxy.getConnectionLocalAddress() :
            remote,
          so.remoteAddress());
        assertAddresses(local == null && testAddress.isInetSocket() ?
            proxy.getConnectionRemoteAddress() :
            local,
          so.localAddress());
        complete();
      });
    startServer();
    client.connect(proxy.getPort(), proxy.getHost())
      .onSuccess(so -> {
        so.close();
        complete();
      })
      .onFailure(this::fail);
    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolVersion2UDP4() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion2UDP4ProtocolHeader(remote, local);
    testHAProxyProtocolRejected(header);
  }

  @Test
  public void testHAProxyProtocolVersion2UDP6() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion2UDP6ProtocolHeader(remote, local);
    testHAProxyProtocolRejected(header);
  }

  @Test
  public void testHAProxyProtocolVersion2UnixDataGram() throws Exception {
    SocketAddress remote = SocketAddress.domainSocketAddress("/tmp/remoteSocket");
    SocketAddress local = SocketAddress.domainSocketAddress("/tmp/localSocket");
    Buffer header = HAProxy.createVersion2UnixDatagramProtocolHeader(remote, local);
    testHAProxyProtocolRejected(header);
  }

  private void testHAProxyProtocolRejected(Buffer header) throws Exception {
    waitFor(2);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server.close();
    server = vertx.createNetServer(createNetServerOptions()
      .setUseProxyProtocol(true))
      .exceptionHandler(ex -> {
        if (ex.equals(HAProxyMessageCompletionHandler.UNSUPPORTED_PROTOCOL_EXCEPTION))
          complete();
      })
      .connectHandler(so -> fail());

    startServer();
    client.connect(proxy.getPort(), proxy.getHost())
      .onSuccess(so -> {
        so.close();
        complete();
      })
      .onFailure(this::fail);

    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolIllegalHeader1() throws Exception {
    testHAProxyProtocolIllegal(Buffer.buffer("This is an illegal HA PROXY protocol header\r\n"));
  }

  @Test
  public void testHAProxyProtocolIllegalHeader2() throws Exception {
    //IPv4 remote IPv6 Local
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolIllegal(header);
  }

  private void testHAProxyProtocolIllegal(Buffer header) throws Exception {
    waitFor(2);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server.close();
    server = vertx.createNetServer(createNetServerOptions().setUseProxyProtocol(true))
      .connectHandler(u -> fail("Should not be called")).exceptionHandler(exception -> {
        if (exception instanceof io.netty.handler.codec.haproxy.HAProxyProtocolException)
          complete();
      });
    startServer();
    client.connect(proxy.getPort(), proxy.getHost())
      .onSuccess(so -> {
        so.close();
        complete();
      })
      .onFailure(this::fail);
    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  private void assertAddresses(SocketAddress address1, SocketAddress address2) {
    if (address1 == null || address2 == null)
      assertEquals(address1, address2);
    else {
      assertEquals(address1.hostAddress(), address2.hostAddress());
      assertEquals(address1.port(), address2.port());
    }
  }

  @Test
  public void testConnectTimeout() {
    client.close();
    client = vertx.createNetClient(createNetClientOptions().setConnectTimeout(1));
    client.connect(1234, TestUtils.NON_ROUTABLE_HOST)
      .onComplete(onFailure(err -> {
        assertTrue(err instanceof ConnectTimeoutException);
        testComplete();
      }));
    await();
  }

  @Test
  public void testConnectTimeoutOverride() {
    client.close();
    client = vertx.createNetClient(createNetClientOptions());
    client.connect(new ConnectOptions()
        .setPort(1234)
        .setHost(TestUtils.NON_ROUTABLE_HOST)
        .setTimeout(1))
      .onComplete(onFailure(err -> {
        assertTrue(err instanceof ConnectTimeoutException);
        testComplete();
      }));
    await();
  }

  @Test
  public void testInvalidPort() {
    try {
      server.connectHandler(so -> {

      }).listen(65536);
      fail();
    } catch (IllegalArgumentException ignore) {
    }
  }

  @Test
  public void testClientShutdown() throws Exception {
    testClientShutdown(false, now -> System.currentTimeMillis() - now >= 2000);
  }

  @Test
  public void testClientShutdownOverride() throws Exception {
    testClientShutdown(true, now -> System.currentTimeMillis() - now <= 2000);
  }

  private void testClientShutdown(boolean override, LongPredicate checker) throws Exception {
    waitFor(2);
    server.connectHandler(so -> {

    });
    startServer();
    NetClientInternal client = ((CleanableNetClient) vertx.createNetClient(createNetClientOptions())).unwrap();
    CountDownLatch latch = new CountDownLatch(1);
    long now = System.currentTimeMillis();
    client.connect(testAddress)
      .onComplete(onSuccess(so -> {
        AtomicInteger eventCount = new AtomicInteger();
        so.shutdownHandler(v -> {
          eventCount.incrementAndGet();
          if (override) {
            so.close();
          }
        });
        eventCount.incrementAndGet();
        so.closeHandler(v -> {
          assertEquals(2, eventCount.get());
          assertTrue(checker.test(now));
          complete();
        });
        latch.countDown();
    }));
    awaitLatch(latch);
    Future<Void> fut = client.shutdown(2, TimeUnit.SECONDS);
    fut.onComplete(onSuccess(v -> {
      assertTrue(checker.test(now));
      complete();
    }));
    await();
  }

  @Test
  public void testServerShutdown() throws Exception {
    testServerShutdown(false, now -> System.currentTimeMillis() - now >= 2000);
  }

  @Test
  public void testServerShutdownOverride() throws Exception {
    testServerShutdown(true, now -> System.currentTimeMillis() - now <= 2000);
  }

  public void testServerShutdown(boolean override, LongPredicate checker) throws Exception {
    waitFor(2);
    long now = System.currentTimeMillis();
    server.connectHandler(so -> {
      AtomicInteger eventCount = new AtomicInteger();
      so.shutdownHandler(v -> {
        eventCount.incrementAndGet();
        if (override) {
          so.close();
        }
      });
      so.closeHandler(v -> {
        assertEquals(1, eventCount.getAndIncrement());
        assertTrue(checker.test(now));
        complete();
      });
      so.write("ping");
    });
    startServer();
    CountDownLatch latch = new CountDownLatch(1);
    client.connect(testAddress).onComplete(onSuccess(so -> {
      so.handler(buff -> {
        latch.countDown();
      });
    }));
    awaitLatch(latch);
    Future<Void> fut = server.shutdown(2, TimeUnit.SECONDS);
    fut.onComplete(onSuccess(v -> {
      assertTrue(checker.test(now));
      complete();
    }));
    await();
  }

  @Test
  public void testConnectToServerShutdown() throws Exception {
    AtomicBoolean shutdown = new AtomicBoolean();
    server.connectHandler(so -> {
      if (!shutdown.get()) {
        so.shutdownHandler(v -> {
          shutdown.set(true);
        });
        so.handler(buff -> {
          if (buff.toString().equals("close")) {
            so.close();
          } else {
            so.write(buff);
          }
        });
      } else {
        so.close();
      }
    });
    startServer();
    NetSocket so = client.connect(testAddress).await();
    CountDownLatch latch = new CountDownLatch(1);
    so.handler(buff -> {
      latch.countDown();
    });
    so.write("hello");
    awaitLatch(latch);
    Future<Void> fut = server.shutdown(20, TimeUnit.SECONDS);
    assertWaitUntil(shutdown::get);
    boolean refused = false;
    for (int i = 0;i < 10;i++) {
      try {
        client.connect(testAddress).await();
      } catch (Exception e) {
        // Connection refused
        refused = true;
        break;
      }
      Thread.sleep(100);
    }
    assertTrue(refused);
    so.handler(buff -> {
      so.write("close");
    });
    AtomicBoolean closed = new AtomicBoolean();
    so.closeHandler(v -> closed.set(true));
    // Verify the socket still works
    so.write("ping");
    assertWaitUntil(closed::get);
    fut.await();
  }

  /**
   * Test that for NetServer, the peer host and port info is available in the SSLEngine
   * when the X509ExtendedKeyManager.chooseEngineServerAlias is called.
   *
   * @throws Exception if an error occurs
   */
  @Test
  public void testTLSServerSSLEnginePeerHost() throws Exception {
    testTLSServerSSLEnginePeerHostImpl(false);
  }

  /**
   * Test that for NetServer with start TLS, the peer host and port info is available
   * in the SSLEngine when the X509ExtendedKeyManager.chooseEngineServerAlias is called.
   *
   * @throws Exception if an error occurs
   */
  @Test
  public void testStartTLSServerSSLEnginePeerHost() throws Exception {
    testTLSServerSSLEnginePeerHostImpl(true);
  }

  private void testTLSServerSSLEnginePeerHostImpl(boolean startTLS) throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    testTLS(Cert.NONE, Trust.SERVER_JKS, testPeerHostServerCert(Cert.SERVER_JKS, called), Trust.NONE,
      false, false, true, startTLS);
    assertTrue("X509ExtendedKeyManager.chooseEngineServerAlias is not called", called.get());
  }

  /**
   * Test that for NetServer with SNI, the peer host and port info is available
   * in the SSLEngine when the X509ExtendedKeyManager.chooseEngineServerAlias is called.
   *
   * @throws Exception if an error occurs
   */
  @Test
  public void testSNIServerSSLEnginePeerHost() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    TLSTest test = new TLSTest()
      .clientTrust(Trust.SNI_JKS_HOST2)
      .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com"))
      .serverCert(testPeerHostServerCert(Cert.SNI_JKS, called))
      .sni(true);
    test.run(true);
    await();
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
    assertTrue("X509ExtendedKeyManager.chooseEngineServerAlias is not called", called.get());
  }
}
