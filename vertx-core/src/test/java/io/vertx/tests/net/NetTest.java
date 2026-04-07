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
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import io.vertx.core.impl.Utils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.HAProxyMessageCompletionHandler;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.net.impl.tcp.CleanableNetClient;
import io.vertx.core.internal.net.NetServerInternal;
import io.vertx.core.net.impl.tcp.NetServerImpl;
import io.vertx.core.spi.tls.SslContextFactory;
import io.vertx.core.transport.Transport;
import io.vertx.test.core.*;
import io.vertx.test.proxy.*;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import javax.net.ssl.*;
import java.io.*;
import java.net.Inet4Address;
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
import static io.vertx.test.core.VertxTestBase.ENABLED_CIPHER_SUITES;
import static io.vertx.test.core.VertxTestBase.TRANSPORT;
import static io.vertx.test.core.VertxTestBase.USE_DOMAIN_SOCKETS;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTPS_HOST;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTPS_PORT;
import static io.vertx.tests.tls.HttpTCPTLSTest.testPeerHostServerCert;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@RunWith(VertxRunner.class)
public class NetTest {

  public static class Provider implements VertxProvider {

    public VertxOptions options() {
      VertxOptions options = new VertxOptions();
      options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
        "127.0.0.1 localhost\n" +
        "127.0.0.1 host1\n" +
        "127.0.0.1 host2.com\n" +
        "127.0.0.1 example.com"));
      return options;
    }

    @Override
    public Vertx call() {
      Vertx vertx = Vertx
        .builder()
        .with(options())
        .withTransport(TRANSPORT)
        .build();
      if (TRANSPORT != Transport.NIO) {
        if (!vertx.isNativeTransportEnabled()) {
          Assert.fail("Native transport is not enabled: " + vertx.unavailableNativeTransportCause());
        }
      }
      return vertx;
    }
  }

  private Vertx vertx;
  private SocketAddress testAddress;
  private NetServer server;
  private NetClient client;
  private ProxyBase proxy;
  private File tmp;

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  public NetTest() {
  }

  @Before
  public void setUp(@ProvidedBy(Provider.class) Vertx vertx) throws Exception {
    this.vertx = vertx;
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", TRANSPORT.implementation().supportsDomainSockets());
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
    } else {
      testAddress = SocketAddress.inetSocketAddress(1234, "localhost");
    }
    client = vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000));
    server = vertx.createNetServer();
  }

  @After
  public void tearDown() throws Exception {
    if (tmp != null) {
      tmp.delete();
    }
    if (proxy != null) {
      proxy.stop();
    }
  }

  @Test
  public void testEndHandlerCalledAfterAllEmissions() {
    Buffer  buffer = TestUtils.randomBuffer(1024 * 1024);
    server = vertx.createNetServer().connectHandler(so -> {
      so.end(buffer);
      so.close();
    });
    server.listen(1234).await();
    NetClient client = vertx.createNetClient();
    AtomicInteger received = new AtomicInteger();
    AtomicInteger ended = new AtomicInteger();
    client.connect(1234, "localhost").onComplete(ar -> {
      if (ar.succeeded()) {
        NetSocket socket = ar.result();
        socket.handler(buf -> {
          int amount = received.addAndGet(buf.length());
          assertEquals(0, ended.get());
          socket.pause();
          vertx.setTimer(50, t -> {
            socket.resume();
          });
        });
        socket.endHandler(v -> {
          assertEquals(0, ended.getAndIncrement());
        });
      }
    });
    assertWaitUntil(() -> received.get() == buffer.length());
    assertWaitUntil(() -> ended.get() > 0);
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

    assertEquals(TCPSSLOptions.DEFAULT_TCP_USER_TIMEOUT, options.getTcpUserTimeout());
    int tcpUserTimeout = TestUtils.randomPositiveInt();
    assertEquals(options, options.setTcpUserTimeout(tcpUserTimeout));
    assertEquals(tcpUserTimeout, options.getTcpUserTimeout());
    assertIllegalArgumentException(() -> options.setTcpUserTimeout(-1000));

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

    assertEquals(TCPSSLOptions.DEFAULT_TCP_USER_TIMEOUT, options.getTcpUserTimeout());
    int tcpUserTimeout = TestUtils.randomPositiveInt();
    assertEquals(options, options.setTcpUserTimeout(tcpUserTimeout));
    assertEquals(tcpUserTimeout, options.getTcpUserTimeout());
    assertIllegalArgumentException(() -> options.setTcpUserTimeout(-1000));

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
    int tcpUserTimeout = TestUtils.randomPositiveInt();
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
    options.setTcpUserTimeout(tcpUserTimeout);
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
    assertEquals(def.getTcpUserTimeout(), json.getTcpUserTimeout());
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
    int tcpUserTimeout = TestUtils.randomPositiveInt();
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
        .put("tcpUserTimeout", tcpUserTimeout)
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
    assertEquals(tcpUserTimeout, options.getTcpUserTimeout());
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
    int tcpUserTimeout = TestUtils.randomPositiveInt();
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
    options.setTcpUserTimeout(tcpUserTimeout);
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
    assertEquals(def.getTcpUserTimeout(), json.getTcpUserTimeout());
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
    int tcpUserTimeout = TestUtils.randomPositiveInt();
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
      .put("tcpUserTimeout", tcpUserTimeout)
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
    assertEquals(tcpUserTimeout, options.getTcpUserTimeout());
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
    AtomicReference<NetSocket> serverSocket = new AtomicReference<>();
    server.connectHandler(socket -> {
      serverSocket.set(socket);
      socket.pause();
      close.thenAccept(v -> {
        socket.resume();
      });
    });
    startServer();
    NetSocket so = client.connect(testAddress).await();
    writeUntilFull(so);
    Future<Void> res = so.write(Buffer.buffer("transmitted buffer"));
    serverSocket.get().resume();
    res.await();
  }

  @Test
  public void testWriteHandlerFailure() throws Exception {
    // Todo : investigate this
    Assume.assumeFalse(TRANSPORT == Transport.IO_URING);
    AtomicReference<NetSocket> serverSocket = new AtomicReference<>();
    server.connectHandler(socket -> {
      serverSocket.set(socket);
      socket.pause();
    });
    startServer(testAddress);
    NetSocket so = client.connect(testAddress).await();
    writeUntilFull(so);
    Future<Void> res = so.write(Buffer.buffer("lost buffer"));
    serverSocket.get().close();
    try {
      res.await();
      fail();
    } catch (Exception expected) {
    }
  }

  private void writeUntilFull(NetSocket so) throws Exception {
    while (!so.writeQueueFull()) {
      so.write(TestUtils.randomBuffer(16384));
      Thread.sleep(10);
    }
  }

  @Test
  public void testEchoBytes(Checkpoint checkpoint) {
    Buffer sent = TestUtils.randomBuffer(100);
    testEcho(checkpoint, sock -> sock.write(sent), buff -> assertEquals(sent, buff), sent.length());
  }

  @Test
  public void testEchoString(Checkpoint checkpoint) {
    String sent = TestUtils.randomUnicodeString(100);
    Buffer buffSent = Buffer.buffer(sent);
    testEcho(checkpoint, sock -> sock.write(sent), buff -> assertEquals(buffSent, buff), buffSent.length());
  }

  @Test
  public void testEchoStringUTF8(Checkpoint checkpoint) {
    testEchoStringWithEncoding(checkpoint, "UTF-8");
  }

  @Test
  public void testEchoStringUTF16(Checkpoint checkpoint) {
    testEchoStringWithEncoding(checkpoint, "UTF-16");
  }

  void testEchoStringWithEncoding(Checkpoint checkpoint, String encoding) {
    String sent = TestUtils.randomUnicodeString(100);
    Buffer buffSent = Buffer.buffer(sent, encoding);
    testEcho(checkpoint, sock -> sock.write(sent, encoding), buff -> assertEquals(buffSent, buff), buffSent.length());
  }

  void testEcho(Checkpoint checkpoint, Consumer<NetSocket> writer, Consumer<Buffer> dataChecker, int length) {
    startEchoServer(testAddress);
    NetSocket sock = client.connect(testAddress).await();
    Buffer buff = Buffer.buffer();
    sock.handler((buffer) -> {
      buff.appendBuffer(buffer);
      if (buff.length() == length) {
        dataChecker.accept(buff);
        checkpoint.succeed();
      }
      if (buff.length() > length) {
        fail("Too many bytes received");
      }
    });
    writer.accept(sock);
  }

  void startEchoServer(SocketAddress address) {
    Handler<NetSocket> serverHandler = socket -> socket.handler(socket::write);
    server.connectHandler(serverHandler).listen(address).await();
  }

  @Test
  public void testConnectLocalHost() {
    connect(testAddress);
  }

  void connect(SocketAddress address) {
    startEchoServer(testAddress);
    int numConnections = 100;
    for (int i = 0; i < numConnections; i++) {
      NetSocket sock = client.connect(address).await();
      sock.close().await();
    }
  }

  @Test
  public void testConnectInvalidPort() {
    assertIllegalArgumentException(() -> client.connect(-1, "localhost"));
    assertIllegalArgumentException(() -> client.connect(65536, "localhost"));
    try {
      client.connect(9998, "localhost").await();
      fail();
    } catch (Exception ignore) {
    }
  }

  @Test
  public void testConnectInvalidHost() {
    assertNullPointerException(() -> client.connect(80, null));
    try {
      client.connect(1234, "127.0.0.2").await();
      fail();
    } catch (Exception ignore) {
    }
  }

//  @Ignore("Now they share the same TCP server port")
//  @Test
//  public void testListenInvalidPort() {
//    final int port = 9090;
//    final HttpServer httpServer = vertx.createHttpServer();
//    try {
//      httpServer.requestHandler(ignore -> {})
//        .listen(port).onComplete(TestUtils.onSuccess(s ->
//          vertx.createNetServer()
//            .connectHandler(ignore -> {})
//            .listen(port).onComplete(TestUtils.onFailure(error -> {
//              assertNotNull(error);
//              testComplete();
//            }))));
//      await();
//    } finally {
//      httpServer.close();
//    }
//  }

  @Test
  public void testListenInvalidHost() {
    server = vertx.createNetServer(new NetServerOptions().setPort(1234).setHost("uhqwduhqwudhqwuidhqwiudhqwudqwiuhd"));
    try {
      server
        .connectHandler(netSocket -> {
      })
        .listen()
        .await();
      fail();
    } catch (Exception ignore) {
    }
  }

  @Test
  public void testListenOnWildcardPort() {
    server = vertx.createNetServer(new NetServerOptions().setPort(0));
    NetServer s = server.connectHandler((netSocket) -> {
    }).listen().await();
    assertTrue(server.actualPort() > 1024);
    assertEquals(server, s);
  }

  @Test
  public void testClientCloseHandlersCloseFromClient(Checkpoint checkpoint) {
    startEchoServer(testAddress);
    clientCloseHandlers(checkpoint).close().await();
  }

  @Test
  public void testClientCloseHandlersCloseFromServer(Checkpoint checkpoint) {
    server
      .connectHandler(so -> so.handler(buff -> so.close()))
      .listen(testAddress)
      .await();
    clientCloseHandlers(checkpoint).write("ping").await();
  }

  NetSocket clientCloseHandlers(Checkpoint checkpoint) {
    NetSocket so = client.connect(testAddress).await();
    AtomicInteger counter = new AtomicInteger(0);
    so.endHandler(v -> assertEquals(1, counter.incrementAndGet()));
    so.closeHandler(v -> {
      assertEquals(2, counter.incrementAndGet());
      checkpoint.succeed();
    });
    return so;
  }

  @Test
  public void testServerCloseHandlersCloseFromClient(Checkpoint checkpoint) {
    serverCloseHandlers(checkpoint, false);
    NetSocket sock = client.connect(testAddress).await();
    sock.close().await();
  }

  @Test
  public void testServerCloseHandlersCloseFromServer(Checkpoint checkpoint) {
    serverCloseHandlers(checkpoint, true);
    client.connect(testAddress).await();
  }

  void serverCloseHandlers(Checkpoint checkpoint, boolean closeFromServer) {
    server.connectHandler((sock) -> {
      AtomicInteger counter = new AtomicInteger(0);
      sock.endHandler(v -> assertEquals(1, counter.incrementAndGet()));
      sock.closeHandler(v -> {
        assertEquals(2, counter.incrementAndGet());
        checkpoint.succeed();
      });
      if (closeFromServer) {
        sock.close();
      }
    }).listen(testAddress)
      .await();
  }

  @Test
  public void testClientClose() throws Exception {
    int num = 3;
    List<NetServer> servers = new ArrayList<>();
    try {
      for (int i = 0;i < num;i++) {
        NetServer server = vertx.createNetServer();
        server.connectHandler(so -> {

        });
        startServer(SocketAddress.inetSocketAddress(1234 + i, "localhost"), server);
        servers.add(server);
      }
      NetClient client = vertx.createNetClient();
      AtomicInteger inflight = new AtomicInteger();
      for (int i = 0;i < num;i++) {
        NetSocket so = client.connect(1234 + i, "localhost").await();
        inflight.incrementAndGet();
        so.closeHandler(v -> {
          inflight.decrementAndGet();
        });
      }
      assertWaitUntil(() -> inflight.get() == 3);
      client.close().await();
      assertWaitUntil(() -> inflight.get() == 0);
    } finally {
      servers.forEach(NetServer::close);
    }
  }

  @Test
  public void testReceiveMessageAfterExplicitClose(Checkpoint checkpoint) throws Exception {
    server.connectHandler(so -> {
      so.handler(buff -> {
        so.write(buff);
      });
    });
    startServer();
    NetSocket so = client.connect(testAddress).await();
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
      checkpoint.succeed();
    });
    so.write("Hello World");
  }

  @Test
  public void testClientDrainHandler(Checkpoint checkpoint) throws Exception {

    server.connectHandler(sock -> {
        sock.pause();
        Handler<Message<Buffer>> resumeHandler = (m) -> sock.resume();
        MessageConsumer<?> reg = vertx.eventBus().<Buffer>consumer("server_resume").handler(resumeHandler);
        sock.closeHandler(v -> reg.unregister());
      });

    startServer(testAddress);

    NetSocket sock = client.connect(testAddress).await();
    assertFalse(sock.writeQueueFull());
    sock.setWriteQueueMaxSize(1000);
    Buffer buff = TestUtils.randomBuffer(10000);
    vertx.setPeriodic(1, id -> {
      Thread thread = Thread.currentThread();
      sock.write(buff.copy());
      if (sock.writeQueueFull()) {
        vertx.cancelTimer(id);
        sock.drainHandler(v -> {
          assertFalse(sock.writeQueueFull());
          assertSame(thread, Thread.currentThread());
          checkpoint.succeed();
        });
        // Tell the server to resume
        vertx.eventBus().send("server_resume", "");
      }
    });
  }

  @Test
  public void testServerDrainHandler(Checkpoint checkpoint) throws Exception {

    server.connectHandler(sock -> {
      assertFalse(sock.writeQueueFull());
      sock.setWriteQueueMaxSize(1000);
      sock.handler(trigger -> {
        Buffer buff = TestUtils.randomBuffer(10000);
        //Send data until the buffer is full
        vertx.setPeriodic(1, id -> {
          sock.write(buff.copy());
          if (sock.writeQueueFull()) {
            vertx.cancelTimer(id);
            sock.drainHandler(v -> {
              assertFalse(sock.writeQueueFull());
              // End test after a short delay to give the client some time to read the data
              vertx.setTimer(100, id2 -> checkpoint.succeed());
            });
            // Tell the client to resume
            vertx.eventBus().send("client_resume", "");
          }
        });
      });
    });

    startServer(testAddress);

    NetSocket sock = client.connect(testAddress).await();
    sock.pause();
    MessageConsumer<Buffer> reg = vertx
      .eventBus()
      .consumer("client_resume", m -> sock.resume());
    sock.closeHandler(v -> reg.unregister());
    sock.handler(buf -> {});
    sock.write("go");
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
    client = vertx.createNetClient(new NetClientOptions().setReconnectAttempts(attempts).setReconnectInterval(10));

    // Start the server after a delay
    new Thread(() -> {
      try {
        Thread.sleep(2000);
        startEchoServer(testAddress);
      } catch (InterruptedException ignore) {
      }
    }).start();

    //The server delays starting for a a few seconds, but it should still connect
    client.connect(testAddress).await();
  }

  @Test
  public void testReconnectAttemptsNotEnough() {
    // This test does not pass reliably in CI for Windows
    Assume.assumeFalse(Utils.isWindows());
    client = vertx.createNetClient(new NetClientOptions().setReconnectAttempts(100).setReconnectInterval(10));

    try {
      client.connect(testAddress).await();
      fail();
    } catch (Exception ignore) {
    }
  }

  @Test
  public void testServerIdleTimeout1(Checkpoint checkpoint) throws Exception {
    testTimeout(checkpoint, new NetClientOptions(), new NetServerOptions().setIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertEquals("0123456789", received.toString()), true);
  }

  @Test
  public void testServerIdleTimeout2(Checkpoint checkpoint) throws Exception {
    testTimeout(checkpoint, new NetClientOptions(), new NetServerOptions().setReadIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertEquals("0123456789", received.toString()), true);
  }

  @Test
  public void testServerIdleTimeout3(Checkpoint checkpoint) throws Exception {
    // Usually 012 but might be 01 or 0123
    testTimeout(checkpoint, new NetClientOptions(), new NetServerOptions().setWriteIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertFalse("0123456789".equals(received.toString())), true);
  }

  @Test
  public void testServerIdleTimeout4(Checkpoint checkpoint) throws Exception {
    testTimeout(checkpoint, new NetClientOptions(), new NetServerOptions().setIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertEquals("0123456789", received.toString()), false);
  }

  @Test
  public void testServerIdleTimeout5(Checkpoint checkpoint) throws Exception {
    // Usually 012 but might be 01 or 0123
    testTimeout(checkpoint, new NetClientOptions(), new NetServerOptions().setReadIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertFalse("0123456789".equals(received.toString())), false);
  }

  @Test
  public void testServerIdleTimeout6(Checkpoint checkpoint) throws Exception {
    testTimeout(checkpoint, new NetClientOptions(), new NetServerOptions().setWriteIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), received -> assertEquals("0123456789", received.toString()), false);
  }

  @Test
  public void testClientIdleTimeout1(Checkpoint checkpoint) throws Exception {
    testTimeout(checkpoint, new NetClientOptions().setIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), new NetServerOptions(), received -> assertEquals("0123456789", received.toString()), true);
  }

  @Test
  public void testClientIdleTimeout2(Checkpoint checkpoint) throws Exception {
    testTimeout(checkpoint, new NetClientOptions().setWriteIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), new NetServerOptions(), received -> assertEquals("0123456789", received.toString()), true);
  }

  @Test
  public void testClientIdleTimeout3(Checkpoint checkpoint) throws Exception {
    // Usually 012 but might be 01 or 0123
    testTimeout(checkpoint, new NetClientOptions().setReadIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), new NetServerOptions(), received -> assertNotEquals("0123456789", received.toString()), true);
  }

  @Test
  public void testClientIdleTimeout4(Checkpoint checkpoint) throws Exception {
    testTimeout(checkpoint, new NetClientOptions().setIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), new NetServerOptions(), received -> assertEquals("0123456789", received.toString()), false);
  }

  @Test
  public void testClientIdleTimeout5(Checkpoint checkpoint) throws Exception {
    // Usually 012 but might be 01 or 0123
    testTimeout(checkpoint, new NetClientOptions().setWriteIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), new NetServerOptions(), received -> assertNotEquals("0123456789", received.toString()), false);
  }

  @Test
  public void testClientIdleTimeout6(Checkpoint checkpoint) throws Exception {
    testTimeout(checkpoint, new NetClientOptions().setReadIdleTimeout(500).setIdleTimeoutUnit(TimeUnit.MILLISECONDS), new NetServerOptions(), received -> assertEquals("0123456789", received.toString()), false);
  }

  private void testTimeout(Checkpoint checkpoint,
                           NetClientOptions clientOptions,
                           NetServerOptions serverOptions,
                           Consumer<Buffer> check,
                           boolean clientSends) throws Exception {
    server = vertx.createNetServer(serverOptions);
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
        checkpoint.succeed();
      });
    };
    Handler<NetSocket> clientHandler = clientSends ? sender : receiver;
    Handler<NetSocket> serverHandler = clientSends ? receiver : sender;

    server.connectHandler(serverHandler);
    startServer(testAddress);

    NetSocket sock = client.connect(testAddress).await();
    clientHandler.handle(sock);
  }

  @Test
  // StartTLS
  public void testStartTLSClientTrustAll(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, true, true);
  }

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, true, false);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE, false, false, true, false);
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, false, false);
  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, false, false, true, false);
  }

  @Test
  //Client specifies cert and it's not required
  public void testTLSClientCertRequired(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, true, false);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert1_3(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, new String[0], new String[]{"TLSv1.3"});
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE, true, false, false, false);
  }

  @Test
  // StartTLS client specifies cert but it's not trusted
  public void testStartTLSClientCertClientNotTrusted(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, true);
  }

  @Test
  // Specify some cipher suites
  public void testTLSCipherSuites(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, true, false, ENABLED_CIPHER_SUITES);
  }

  @Test
  // Specify some bogus protocol
  public void testInvalidTlsProtocolVersion(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, false, false, new String[0],
    new String[]{"TLSv1.999"});
  }

  @Test
  // Specify a valid protocol
  public void testSpecificTlsProtocolVersion(Checkpoint checkpoint) throws Exception {
    testTLS(checkpoint, Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, true, true, false, new String[0],
        new String[]{"TLSv1.2"});
  }

  @Test
  public void testTLSTrailingDotHost(Checkpoint checkpoint) throws Exception {
    // Reuse SNI test certificate because it is convenient
    TLSTest test = new TLSTest(checkpoint)
      .clientTrust(Trust.SNI_JKS_HOST2)
      .connectAddress(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com."))
      .bindAddress(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com"))
      .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
  }

  @Test
  // SNI without server name should use the first keystore entry
  public void testSniWithoutServerNameUsesTheFirstKeyStoreEntry1(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SERVER_JKS)
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    assertEquals("localhost", cnOf(test.clientPeerCert()));
  }

  @Test
  // SNI without server name should use the first keystore entry
  public void testSniWithoutServerNameUsesTheFirstKeyStoreEntry2(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SNI_JKS_HOST1)
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(false);
  }

  @Test
  public void testSniImplicitServerName(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SNI_JKS_HOST2)
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com"))
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
  }

  @Test
  public void testSniImplicitServerNameDisabledForShortname1(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SNI_JKS_HOST1)
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host1"))
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(false);
  }

  @Test
  public void testSniImplicitServerNameDisabledForShortname2(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SERVER_JKS)
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host1"))
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    assertEquals("localhost", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testSniForceShortname(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SNI_JKS_HOST1)
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host1"))
        .serverName("host1")
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    assertEquals("host1", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testSniOverrideServerName(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SNI_JKS_HOST2)
        .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "example.com"))
        .serverName("host2.com")
        .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testClientSniMultipleServerName() throws Exception {
    List<String> receivedServerNames = Collections.synchronizedList(new ArrayList<>());
    server = vertx.createNetServer(new NetServerOptions()
      .setSni(true)
      .setSsl(true)
      .setKeyCertOptions(Cert.SNI_JKS.get())
    ).connectHandler(so -> {
      receivedServerNames.add(so.indicatedServerName());
    });
    startServer();
    List<String> serverNames = Arrays.asList("host1", "host2.com", "fake");
    List<String> cns = new ArrayList<>();
    client = vertx.createNetClient(new NetClientOptions().setSsl(true).setHostnameVerificationAlgorithm("").setTrustAll(true));
    for (String serverName : serverNames) {
      NetSocket so = client.connect(testAddress, serverName).await();
      String host = cnOf(so.peerCertificates().get(0));
      cns.add(host);
    }
    assertEquals(Arrays.asList("host1", "host2.com", "localhost"), cns);
    assertEquals(2, ((NetServerInternal)server).sniEntrySize());
    assertWaitUntil(() -> receivedServerNames.size() == 3);
    assertEquals(receivedServerNames, serverNames);
  }

  @Test
  public void testEnabledSniCacheSize() throws Exception {
    testSniCacheSize(true);
  }

  @Test
  public void testDisabledSniCacheSize() throws Exception {
    testSniCacheSize(false);
  }

  private void testSniCacheSize(boolean sni) throws Exception {
    List<String> receivedServerNames = Collections.synchronizedList(new ArrayList<>());
    server = vertx.createNetServer(new NetServerOptions()
      .setSni(sni)
      .setSsl(true)
      .setKeyCertOptions(Cert.SNI_JKS.get())
    ).connectHandler(so -> {
      receivedServerNames.add(so.indicatedServerName());
    });
    startServer();
    client = vertx.createNetClient(new NetClientOptions().setSsl(true).setHostnameVerificationAlgorithm("").setTrustAll(true));
    int num = 100;
    List<NetSocket> sockets = new ArrayList<>();
    List<String> actualServerNames = new ArrayList<>();
    for (int i = 0;i < num;i++) {
      String serverName = i + ".host3.com";
      NetSocket socket = client.connect(testAddress, serverName).await();
      sockets.add(socket);
      actualServerNames.add(serverName);
    }
    for (NetSocket socket : sockets) {
      socket.close().await();
    }
    assertWaitUntil(() -> num == receivedServerNames.size());
    int size = ((NetServerImpl) server).sniEntrySize();
    if (sni) {
      assertEquals(actualServerNames, receivedServerNames);
      assertEquals(SslContextProvider.DEFAULT_SNI_CACHE_SIZE, size);
    } else {
      for (String receivedServerName : receivedServerNames) {
        Assert.assertNull(receivedServerName);
      }
      assertEquals(0, size);
    }
  }

  @Test
  // SNI present an unknown server
  public void testSniWithUnknownServer1(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SERVER_JKS)
        .serverCert(Cert.SNI_JKS).sni(true).serverName("unknown");
    test.run(true);
    assertEquals("localhost", cnOf(test.clientPeerCert()));
  }

  @Test
  // SNI present an unknown server
  public void testSniWithUnknownServer2(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SNI_JKS_HOST2)
        .serverCert(Cert.SNI_JKS).sni(true).serverName("unknown");
    test.run(false);
  }

  @Test
  // SNI returns the certificate for the indicated server name
  public void testSniWithServerNameStartTLS(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
        .clientTrust(Trust.SNI_JKS_HOST1)
        .startTLS(true)
        .serverCert(Cert.SNI_JKS).sni(true).serverName("host1");
    test.run(true);
    assertEquals("host1", cnOf(test.clientPeerCert()));
  }

  @Test
  public void testSniWithServerNameTrust(Checkpoint checkpoint){
    TLSTest test = new TLSTest(checkpoint).clientTrust(Trust.SNI_JKS_HOST2)
        .clientCert(Cert.CLIENT_PEM_ROOT_CA)
        .requireClientAuth(true)
        .serverCert(Cert.SNI_JKS)
        .sni(true)
        .serverName("host2.com")
        .serverTrust(Trust.SNI_SERVER_ROOT_CA_AND_OTHER_CA_1);
    test.run(true);
  }

  @Test
  public void testSniWithServerNameTrustFallback(Checkpoint checkpoint){
    TLSTest test = new TLSTest(checkpoint).clientTrust(Trust.SNI_JKS_HOST2)
        .clientCert(Cert.CLIENT_PEM_ROOT_CA)
        .requireClientAuth(true)
        .serverCert(Cert.SNI_JKS)
        .sni(true)
        .serverName("host2.com")
        .serverTrust(Trust.SNI_SERVER_ROOT_CA_FALLBACK);
    test.run(true);
  }

  @Test
  public void testSniWithServerNameTrustFallbackFail(Checkpoint checkpoint){
    TLSTest test = new TLSTest(checkpoint).clientTrust(Trust.SNI_JKS_HOST2)
        .clientCert(Cert.CLIENT_PEM_ROOT_CA)
        .requireClientAuth(true)
        .serverCert(Cert.SNI_JKS)
        .sni(true)
        .serverName("host2.com")
        .serverTrust(Trust.SNI_SERVER_OTHER_CA_FALLBACK);
    test.run(false);
  }

  @Test
  public void testSniWithServerNameTrustFail(Checkpoint checkpoint){
    TLSTest test = new TLSTest(checkpoint).clientTrust(Trust.SNI_JKS_HOST2)
        .clientCert(Cert.CLIENT_PEM_ROOT_CA)
        .requireClientAuth(true)
        .serverCert(Cert.SNI_JKS)
        .sni(true)
        .serverName("host2.com")
        .serverTrust(Trust.SNI_SERVER_ROOT_CA_AND_OTHER_CA_2);
    test.run(false);
  }

  @Test
  public void testSniWithTrailingDotHost(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
      .clientTrust(Trust.SNI_JKS_HOST2)
      .connectAddress(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com."))
      .bindAddress(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com"))
      .serverCert(Cert.SNI_JKS).sni(true);
    test.run(true);
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
  }

  @Test
  public void testServerCertificateMultiple(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
      .serverCert(Cert.MULTIPLE_JKS)
      .clientTrustAll(true);
    test.run(true);
    assertEquals("precious", cnOf(test.clientPeerCert()));
  }

//  @Test
//  public void testServerCertificateMultipleWrongAlias(Checkpoint checkpoint) throws Exception {
//    TLSTest test = new TLSTest(checkpoint)
//      .serverCert(Cert.MULTIPLE_JKS_WRONG_ALIAS)
//      .clientTrustAll(true);
//    server = vertx
//      .createNetServer(test.setupServer())
//      .connectHandler(so -> {
//
//    });
//    server.listen(test.bindAddress).onComplete(TestUtils.onFailure(t -> {
//      Assertions.assertThat(t).isInstanceOf(IllegalArgumentException.class);
//      Assertions.assertThat(t.getMessage()).contains("alias does not exist in the keystore");
//      testComplete();
//    }));
//    await();
//  }

  @Test
  public void testServerCertificateMultipleWithKeyPassword(Checkpoint checkpoint) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
      .serverCert(Cert.MULTIPLE_JKS_ALIAS_PASSWORD)
      .clientTrustAll(true);
    test.run(true);
    assertEquals("fonky", cnOf(test.clientPeerCert()));
  }

  void testTLS(Checkpoint checkpoint,
               Cert<?> clientCert, Trust<?> clientTrust,
               Cert<?> serverCert, Trust<?> serverTrust,
    boolean requireClientAuth, boolean clientTrustAll,
    boolean shouldPass, boolean startTLS) throws Exception {
        testTLS(checkpoint, clientCert, clientTrust, serverCert, serverTrust, requireClientAuth, clientTrustAll,
        shouldPass, startTLS, new String[0], new String[]{"TLSv1.2"});
  }

  void testTLS(Checkpoint checkpoint,
               Cert<?> clientCert, Trust<?> clientTrust,
               Cert<?> serverCert, Trust<?> serverTrust,
    boolean requireClientAuth, boolean clientTrustAll,
    boolean shouldPass, boolean startTLS,
    String[] enabledCipherSuites) throws Exception {
        testTLS(checkpoint, clientCert, clientTrust, serverCert, serverTrust, requireClientAuth, clientTrustAll,
        shouldPass, startTLS, enabledCipherSuites, new String[]{"TLSv1.2"});
    }

  void testTLS(Checkpoint checkpoint,
               Cert<?> clientCert, Trust<?> clientTrust,
               Cert<?> serverCert, Trust<?> serverTrust,
               boolean requireClientAuth, boolean clientTrustAll,
               boolean shouldPass, boolean startTLS,
               String[] enabledCipherSuites,
               String[] enabledSecureTransportProtocols) throws Exception {
    TLSTest test = new TLSTest(checkpoint)
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
  }

  class TLSTest {

    final Checkpoint checkpoint;
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

    public TLSTest(Checkpoint checkpoint) {
      this.checkpoint = checkpoint;
    }

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
      NetServerOptions options = new NetServerOptions();
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
      int passes = 1;
      if (!shouldPass) {
        passes += 1;
      }
      CountDownLatch latch = checkpoint.asLatch(passes);
      Future<String> bind = vertx.deployVerticle(new AbstractVerticle() {
        @Override
        public void start(Promise<Void> startPromise) {
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
            server.exceptionHandler(err -> latch.countDown());
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
                    handler = TestUtils.onSuccess(v -> {
                      assertSame(ctx, Vertx.currentContext());
                      certificateChainChecker.accept(socket);
                      assertTrue(socket.isSsl());
                    });
                  } else {
                    handler = TestUtils.onFailure(err -> {
                      assertSame(ctx, Vertx.currentContext());
                      latch.countDown();
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
      bind.onComplete(TestUtils.onSuccess(ar -> {
        NetClientOptions clientOptions = new NetClientOptions();
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
                latch.countDown();
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

          f.onComplete(TestUtils.onSuccess(v -> latch.countDown()));
        } else {
          if (clientAuthDeferred) {
            socketFuture.onComplete(TestUtils.onSuccess(socket -> {
              socket.exceptionHandler(err -> {
                latch.countDown();
              });
            }));
          } else {
            socketFuture.onComplete(TestUtils.onFailure(v -> latch.countDown()));
          }
        }
      }));
      checkpoint.awaitSuccess();
    }
  }

  public static class NativeVertxProvider implements VertxProvider {
    @Override
    public Vertx call() throws Exception {
      return Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    }
  }

  @Test
  public void testListenDomainSocketAddressNative(Checkpoint checkpoint, @ProvidedBy(NativeVertxProvider.class) Vertx vx) throws Exception {
    assumeTrue("Native transport must be enabled", vx.isNativeTransportEnabled());
    testListenDomainSocketAddress(checkpoint, (VertxInternal) vx);
  }

  @Test
  public void testListenDomainSocketAddressJdk(Checkpoint checkpoint, @ProvidedBy(NativeVertxProvider.class) Vertx vx) throws Exception {
    assumeFalse("Native transport must not be enabled", vx.isNativeTransportEnabled());
    testListenDomainSocketAddress(checkpoint, (VertxInternal) vx);
  }

  private void testListenDomainSocketAddress(Checkpoint checkpoint, VertxInternal vx) throws Exception {
    assumeTrue("Transport must support domain sockets", vx.transport().supportsDomainSockets());
    int len = 3;
    CountDownLatch latch = checkpoint.asLatch(len * len);
    List<SocketAddress> addresses = new ArrayList<>();
    for (int i = 0;i < len;i++) {
      File sockFile = TestUtils.tmpFile(".sock");
      SocketAddress sockAddress = SocketAddress.domainSocketAddress(sockFile.getAbsolutePath());
      NetServer server = vx
        .createNetServer()
        .connectHandler(so -> {
          so.end(Buffer.buffer(sockAddress.path()));
        });
      startServer(sockAddress, server);
      addresses.add(sockAddress);
    }
    client = vx.createNetClient();
    for (int i = 0;i < len;i++) {
      for (int j = 0;j < len;j++) {
        SocketAddress sockAddress = addresses.get(i);
        client
          .connect(sockAddress)
          .onComplete(onSuccess(so -> {
            Buffer received = Buffer.buffer();
            so.handler(received::appendBuffer);
            so.closeHandler(v -> {
              assertEquals(received.toString(), sockAddress.path());
              latch.countDown();
            });
          }));
      }
    }
  }

  @Test
  public void testTLSSelectApplicationProtocol(Checkpoint checkpoint) throws Exception {
    List<String> protocols = List.of("protocol1", "protocol2");
    CountDownLatch latch = checkpoint.asLatch(protocols.size());
    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      .setUseAlpn(true);
    serverOptions.getSslOptions().setApplicationLayerProtocols(protocols);
    server = vertx.createNetServer(serverOptions);
    server.connectHandler(conn -> {
      Buffer buffer = Buffer.buffer();
      conn.handler(buffer::appendBuffer);
      conn.endHandler(v -> {
        assertEquals(conn.applicationLayerProtocol(), buffer.toString());
        latch.countDown();
      });
    });
    startServer(SocketAddress.inetSocketAddress(1234, "localhost"));
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustAll(true)
      .setHostnameVerificationAlgorithm("")
      .setUseAlpn(true);
    for (String protocol : protocols) {
      NetSocket connection = client.connect(new ConnectOptions()
        .setHost("localhost")
        .setSsl(true)
        .setSslOptions(sslOptions.copy().setApplicationLayerProtocols(List.of(protocol)))
        .setPort(1234)
      ).await();
      connection.end(Buffer.buffer(protocol));
    }
    checkpoint.awaitSuccess();
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

    vertx.deployVerticle(() -> new VerticleBase() {
        NetServer server;
        @Override
        public Future<?> start() {
          server = vertx.createNetServer();
          servers.add(server);
          return server.connectHandler(sock -> {
            threads.add(Thread.currentThread());
            connectedServers.add(server);
            sock.write("dummy");
          }).listen(testAddress);
        }
      }, new DeploymentOptions().setInstances(numServers))
      .await();

    // Create a bunch of connections
    client = vertx.createNetClient(new NetClientOptions());
    for (int i = 0; i < numConnections; i++) {
      client.connect(testAddress).await();
    }
    assertWaitUntil(() -> numServers == connectedServers.size());
    assertEquals(numServers, threads.size());
    for (NetServer server : servers) {
      assertTrue(connectedServers.contains(server));
    }
  }

  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    // Have a server running on a different port to make sure it doesn't interact
    server = vertx.createNetServer(new NetServerOptions().setPort(4321));
    server.connectHandler(sock -> {
      fail("Should not connect");
    }).listen().await();
    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host beforehand to make sure it doesn't interact
    server = vertx.createNetServer();
    server.connectHandler(sock -> {
      fail("Should not connect");
    });
    startServer(testAddress);
    server.close().await(10, TimeUnit.SECONDS);
    Thread.sleep(500); // Let some time
    testSharedServersRoundRobin();
  }

  @Test
  public void testClosingVertxCloseSharedServers() throws Exception {
    int numServers = 2;
    List<NetServerInternal> servers = new ArrayList<>();
    for (int i = 0;i < numServers;i++) {
      NetServer server = vertx.createNetServer().connectHandler(so -> {
        fail();
      });
      startServer(server);
      servers.add((NetServerInternal) server);
    }
    vertx.close().await();
    servers.forEach(server -> {
      assertTrue(server.isClosed());
    });
  }

  @Test
  public void testWriteHandlerIdNullByDefault(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
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
    NetSocket socket = client.connect(testAddress).await();
    assertNull(socket.writeHandlerID());
    socket
      .closeHandler(v -> checkpoint1.succeed())
      .handler(data -> {
        assertEquals(bye, data);
        checkpoint2.succeed();
      })
      .write(hello);
  }

  @Test
  // This tests using NetSocket.writeHandlerID (on the server side)
  // Send some data and make sure it is fanned out to all connections
  public void testFanout() throws Exception {
    int numConnections = 10;
    Set<String> connections = ConcurrentHashMap.newKeySet();
    server = vertx.createNetServer(new NetServerOptions().setRegisterWriteHandler(true));
    server.connectHandler(socket -> {
      connections.add(socket.writeHandlerID());
    });
    startServer();
    CountDownLatch receivedLatch = new CountDownLatch(numConnections);
    for (int i = 0; i < numConnections; i++) {
      NetSocket socket = client.connect(testAddress).await();
      socket.handler(data -> {
        receivedLatch.countDown();
      });
    }
    for (String actorID : connections) {
      vertx.eventBus().publish(actorID, Buffer.buffer("some data"));
    }
  }

  @Test
  public void  testSocketAddress(Checkpoint checkpoint) throws Exception {
    server.connectHandler(socket -> {
      SocketAddress addr = socket.localAddress();
      if (addr.isInetSocket()) {
        assertEquals("127.0.0.1", addr.host());
        assertNull(addr.hostName());
        assertEquals("127.0.0.1", addr.hostAddress());
      } else {
        assertEquals(testAddress.path(), addr.path());
        assertEquals(testAddress.path(), socket.remoteAddress().path());
      }
      checkpoint.succeed();
    });

    startServer(testAddress);

    NetSocket socket = client.connect(testAddress).await();
    SocketAddress addr = socket.remoteAddress();
    if (addr.isInetSocket()) {
      assertEquals("localhost", addr.host());
      assertEquals("localhost", addr.hostName());
      assertEquals("127.0.0.1", addr.hostAddress());
      assertEquals(1234, addr.port());
    } else {
      assertEquals(testAddress.path(), addr.path());
      assertEquals(testAddress.path(), socket.localAddress().path());
    }
  }

  @Test
  public void testWriteSameBufferMoreThanOnce(Checkpoint checkpoint) throws Exception {
    server.connectHandler(socket -> {
      Buffer received = Buffer.buffer();
      socket.handler(buff -> {
        received.appendBuffer(buff);
        if (received.toString().equals("foofoo")) {
          checkpoint.succeed();
        }
      });
    }).listen(testAddress).await();
    NetSocket socket = client.connect(testAddress).await();
    Buffer buff = Buffer.buffer("foo");
    socket.write(buff);
    socket.write(buff);
  }

  @Test
  public void sendFileClientToServer(Checkpoint checkpoint) throws Exception {
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
          checkpoint.succeed();
        }
      });
    });
    startServer(testAddress);
    NetSocket sock = client.connect(testAddress).await();
    sock.sendFile(file.getAbsolutePath()).await();
  }

  @Test
  public void sendFileServerToClient(Checkpoint checkpoint) throws Exception {
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
    startServer(testAddress);
    NetSocket sock = client.connect(testAddress).await();
    sock.handler(buff -> {
      received.appendBuffer(buff);
      if (received.length() == expected.length()) {
        assertEquals(expected, received);
        checkpoint.succeed();
      }
    });
    sock.write("foo");
  }

  @Test
  public void testSendFileDirectory() throws Exception {
    File fDir = testFolder.newFolder();
    server.connectHandler(socket -> {
      socket.handler(buff -> {
        fail("Should not receive any data");
      });
    });
    startServer(testAddress);
    NetSocket socket = client.connect(testAddress).await();
    try {
      socket.sendFile(fDir.getAbsolutePath()).await();
      fail();
    } catch (Exception err) {
      assertEquals(FileNotFoundException.class, err.getClass());
    }
  }

  @Test
  public void testServerOptionsCopiedBeforeUse(Checkpoint checkpoint) {
    NetServerOptions options = new NetServerOptions().setPort(1234);
    server = vertx.createNetServer(options);
    // Now change something - but server should still listen at previous port
    options.setPort(1235);
    server.connectHandler(sock -> {
      checkpoint.succeed();
    });
    server.listen().await();
    client.connect(1234, "localhost").await();
  }

  @Test
  public void testClientOptionsCopiedBeforeUse(Checkpoint checkpoint) throws Exception {
    NetClientOptions options = new NetClientOptions();
    client = vertx.createNetClient(options);
    options.setSsl(true);
    // Now change something - but server should ignore this
    server.connectHandler(sock -> {
      checkpoint.succeed();
    });
    startServer(SocketAddress.inetSocketAddress(1234, "localhost"));
    NetSocket socket = client.connect(1234, "localhost").await();
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
  public void testSetHandlerAfterListen() throws Exception {
    server.connectHandler(sock -> {
    });
    startServer(testAddress);
    try {
      server.connectHandler(sock -> {
      });
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testSetHandlerAfterListen2() {
    server.connectHandler(sock -> {
    });
    server.listen(testAddress).await();
    try {
      server.connectHandler(sock -> {
      });
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testListenTwice() {
    server.connectHandler(sock -> {
    });
    server.listen(testAddress).await();
    try {
      server.listen(testAddress).await();
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testListen(Checkpoint checkpoint) {
    server.connectHandler(so -> so.handler(so::write));
    server.listen(testAddress).await();
    NetSocket socket = client.connect(testAddress).await();
    socket.handler(v -> checkpoint.succeed());
    socket.write("hello");
  }

  @Test
  public void testListenTwice2() {
    server.connectHandler(sock -> {
    });
    server.listen(testAddress).await();
    try {
      server.listen(testAddress).await();
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testCloseTwice() {
    client.close().await();
    client.close().await(); // OK
  }

  @Test
  public void testAttemptConnectAfterClose() {
    client.close().await();
    try {
      client.connect(testAddress).await();
      fail();
    } catch (Exception ignore) {
    }
  }

  @Test
  public void testCloseWithHandler(Checkpoint checkpoint) throws Exception {
    server.connectHandler(so -> so.closeHandler(v -> {
      checkpoint.succeed();
    }));
    startServer(testAddress);
    client
      .connect(testAddress)
      .compose(NetSocket::close)
      .await();
  }

  @Test
  public void testInVerticle(Checkpoint checkpoint) throws Exception {
    testInVerticle(checkpoint, false);
  }

  private void testInVerticle(Checkpoint checkpoint, boolean worker) throws Exception {
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
        server.listen(testAddress).onComplete(onSuccess(s -> {
          assertSame(ctx, context);
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
          client = vertx.createNetClient(new NetClientOptions());
          client
            .connect(testAddress)
            .onComplete(onSuccess(sock -> {
            assertSame(ctx, context);
            if (!worker) {
              assertSame(thr, Thread.currentThread());
            }
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
                checkpoint.succeed();
              }
            });
          }));
        }));
      }
    }
    MyVerticle verticle = new MyVerticle();
    ThreadingModel threadingModel = worker ? ThreadingModel.WORKER : ThreadingModel.EVENT_LOOP;
    vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(threadingModel));
  }

  @Test
  public void testContexts(Checkpoint serverCheckpoint, Checkpoint clientCheckpoint) throws Exception {
    int numConnections = 10;

    CountDownLatch serverLatch = serverCheckpoint.asLatch(numConnections);
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
    Context listenContext = server
      .listen(testAddress)
      .map(s -> Vertx.currentContext())
      .await();

    Set<Context> contexts = ConcurrentHashMap.newKeySet();
    AtomicInteger connectCount = new AtomicInteger();
    // Each connect should be in its own context
    for (int i = 0; i < numConnections; i++) {
      Context context = ((VertxInternal)vertx).createEventLoopContext();
      context.runOnContext(v -> {
        client.connect(testAddress).onComplete(conn -> {
          contexts.add(Vertx.currentContext());
          if (connectCount.incrementAndGet() == numConnections) {
            assertEquals(numConnections, contexts.size());
            clientCheckpoint.succeed();
          }
        });
      });
    }
    clientCheckpoint.awaitSuccess();
    serverCheckpoint.awaitSuccess();

    // Close should be in own context
    server.close().await();
    assertFalse(contexts.contains(listenContext));
    assertSame(serverConnectContext.get(), listenContext);
  }

  @Test
  public void testMultipleServerClose(Checkpoint checkpoint) {
    this.server = vertx.createNetServer();
    // We assume the endHandler and the close completion handler are invoked in the same context task
    ThreadLocal stack = new ThreadLocal();
    stack.set(true);
    server.close().onComplete(ar1 -> {
//      assertNull(stack.get());
//      assertTrue(Vertx.currentContext().isEventLoopContext());
      server.close().onComplete(ar2 -> {
        server.close().onComplete(ar3 -> {
          checkpoint.succeed();
        });
      });
    });
  }

  @Test
  public void testInWorker(Checkpoint checkpoint1, Checkpoint checkpoint2) {
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
            checkpoint1.succeed();
          });
          conn.endHandler(v -> {
            assertTrue(Vertx.currentContext().isWorkerContext());
            assertTrue(Context.isOnWorkerThread());
            assertSame(context, Vertx.currentContext());
            checkpoint2.succeed();
          });
        }).listen(testAddress).onComplete(onSuccess(s -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          assertSame(context, Vertx.currentContext());
          NetClient client = vertx.createNetClient();
          client.connect(testAddress)
            .onComplete(onSuccess(res -> {
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
  }

  @Test
  public void testAsyncWriteIsFlushed(Checkpoint checkpoint) throws Exception {
    // Test that if we do a concurrent write (by another thread) during a channel read operation
    // the channel will be flushed after the concurrent write
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
            assertTrue(latch.await(10, TimeUnit.SECONDS));
          } catch (InterruptedException e) {
            fail(e.getMessage());
          }
        });
      });
      startServer();
      AtomicInteger done = new AtomicInteger();
      for (int i = 0;i < num;i++) {
        NetSocket so = client.connect(testAddress).await();
        so.handler(buff -> {
          assertEquals(expected, buff);
          so.close();
          int val = done.incrementAndGet();
          if (val == num) {
            checkpoint.succeed();
          }
        });
        so.write(TestUtils.randomBuffer(256));
      }
      checkpoint.awaitSuccess();
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

  private List<Context> createWorkers(int size) {
    List<Context> list = new ArrayList<>();
    for (int i = 0;i < size;i++) {
      list.add(((VertxInternal)vertx).createContext(ThreadingModel.WORKER));
    }
    return list;
  }

  @Test
  public void testServerWorkerMissBufferWhenBufferArriveBeforeConnectCallback(Checkpoint checkpoint) throws Exception {
    int size = new Provider().options().getWorkerPoolSize();
    List<Context> workers = createWorkers(size + 1);
    ContextInternal serverCtx = (ContextInternal) workers.get(0);
    server = serverCtx.succeededFuture()
      .flatMap(v -> vertx
        .createNetServer()
        .connectHandler(so -> {
          assertSame(serverCtx, Vertx.currentContext());
          so.handler(buf -> {
            assertEquals("hello", buf.toString());
            checkpoint.succeed();
          });
        })
        .listen(testAddress))
      .await();
    // Create a one-second worker starvation
    CyclicBarrier barrier = new CyclicBarrier(workers.size());
    for (int i = 1; i < workers.size(); i++) {
      workers.get(i).runOnContext(v3 -> {
        try {
          barrier.await();
          Thread.sleep(1000);
        } catch (Exception ignore) {
        }
      });
    }
    barrier.await();
    NetClient client = vertx.createNetClient();
    NetSocket so = client.connect(testAddress).await();
    long now = System.currentTimeMillis();
    so.write(Buffer.buffer("hello")).await();
    checkpoint.awaitSuccess();
    assertTrue(System.currentTimeMillis() - now > 0.9);
  }

  @Test
  public void testClientWorkerMissBufferWhenBufferArriveBeforeConnectCallback(Checkpoint checkpoint) throws Exception {
    int size = VertxOptions.DEFAULT_WORKER_POOL_SIZE;
    List<Context> workers = createWorkers(size + 1);
    NetServer server = vertx.createNetServer();
    AtomicReference<NetSocket> serverSocket = new AtomicReference<>();
    server.connectHandler(so -> {
      serverSocket.set(so);
    });
    server.listen(testAddress).await();
    ContextInternal clientCtx = (ContextInternal)workers.get(0);
    NetSocket socket = clientCtx.succeededFuture().flatMap(v -> client.connect(testAddress)).await();
    socket.handler(buf -> {
      assertEquals("hello", buf.toString());
      checkpoint.succeed();
    });
    // Create a one second worker starvation
    CyclicBarrier barrier = new CyclicBarrier(workers.size());
    for (int i = 1; i < workers.size(); i++) {
      workers.get(i).runOnContext(v2 -> {
        try {
          barrier.await();
          Thread.sleep(1000);
        } catch (Exception ignore) {
        }
      });
    }
    barrier.await();
    long now = System.currentTimeMillis();
    TestUtils.assertWaitUntil(() -> serverSocket.get() != null);
    serverSocket.get().write(Buffer.buffer("hello")).await();
    checkpoint.awaitSuccess();
    assertTrue(System.currentTimeMillis() - now > 0.9);
  }

  @Test
  public void testHostVerificationHttpsNotMatching(Checkpoint checkpoint) {
    NetServerOptions options = new NetServerOptions()
            .setPort(1234)
            .setHost("localhost")
            .setSsl(true)
            .setKeyCertOptions(new JksOptions().setPath("tls/mim-server-keystore.jks").setPassword("wibble"));
    NetServer server = vertx.createNetServer(options);

    NetClientOptions clientOptions = new NetClientOptions()
            .setSsl(true)
            .setTrustAll(true)
            .setHostnameVerificationAlgorithm("HTTPS");
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    server
      .listen()
      .compose(v -> client.connect(1234, "localhost"))
      .onComplete(TestUtils.onFailure(err -> {
      //Should not be able to connect
      checkpoint.succeed();
    }));
  }

  // this test sets HostnameVerification but also trustAll, it fails if hostname is
  // incorrect but does not verify the certificate validity

  @Test
  public void testHostVerificationHttpsMatching() {
    NetServerOptions options = new NetServerOptions()
            .setPort(1234)
            .setHost("localhost")
            .setSsl(true)
            .setKeyCertOptions(Cert.SERVER_JKS.get());
    NetServer server = vertx.createNetServer(options);
    NetClientOptions clientOptions = new NetClientOptions()
            .setSsl(true)
            .setTrustAll(true)
            .setHostnameVerificationAlgorithm("HTTPS");
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    server.listen().await();
    client.connect(1234, "localhost").await();
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
    NetServerOptions options = new NetServerOptions()
      .setPort(1234)
      .setHost("localhost")
      .setSsl(true)
      .setKeyCertOptions(new JksOptions().setPath("tls/server-keystore.jks").setPassword("wibble"));
    NetServer server = vertx.createNetServer(options);

    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(true)
      .setTrustAll(true);
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {
    });
    server.listen().await();
    try {
      consumer.apply(client).await();
      fail();
    } catch (Exception err) {
      assertTrue(err.getMessage().contains("Missing hostname verification algorithm"));
    }
  }

  @Test
  public void testMissingClientSSLOptions(Checkpoint checkpoint) throws Exception {
    server = vertx.createNetServer(new NetServerOptions()
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS.get()))
      .connectHandler(conn -> {
        fail();
      });
    startServer(testAddress);
    client.connect(new ConnectOptions().setPort(8443).setHost("localhost").setSsl(true)).onComplete(TestUtils.onFailure(err -> {
      assertTrue(err.getMessage().contains("ClientSSLOptions"));
      checkpoint.succeed();
    }));
  }

  @Test
  public void testReuseDefaultClientSSLOptions(Checkpoint checkpoint1) throws Exception {
    server = vertx.createNetServer(new NetServerOptions()
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS.get()))
      .connectHandler(conn -> {
        checkpoint1.succeed();
      });
    startServer(testAddress);
    client = vertx.createNetClient(new NetClientOptions().setTrustAll(true).setHostnameVerificationAlgorithm(""));
    client.connect(new ConnectOptions().setRemoteAddress(testAddress).setSsl(true)).await();
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
    proxy = new SocksProxy();
    proxy.start(vertx);
    server.listen(1234, "localhost").await();
    client.connect(1234, "localhost").await();
    // make sure we have gone through the proxy
    assertEquals("localhost:1234", proxy.getLastUri());
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
    proxy = new SocksProxy().username("username");
    proxy.start(vertx);
    server.listen(1234, "localhost").await();
    client.connect(1234, "localhost").await();
  }

  /**
   * test socks5 proxy when accessing ssl server port with correct cert.
   */
  @Test
  public void testConnectSSLWithSocks5Proxy() throws Exception {
    NetServerOptions options = new NetServerOptions()
        .setPort(1234)
        .setHost("localhost")
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get());
    server = vertx.createNetServer(options);

    NetClientOptions clientOptions = new NetClientOptions()
        .setHostnameVerificationAlgorithm("HTTPS")
        .setSsl(true)
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("127.0.0.1").setPort(11080))
        .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new SocksProxy();
    proxy.start(vertx);
    server.listen().await();
    client.connect(1234, "localhost").await();
  }

  /**
   * test socks5 proxy for accessing ssl server port with upgradeToSsl.
   * https://github.com/eclipse/vert.x/issues/1602
   */
  @Test
  public void testUpgradeSSLWithSocks5Proxy() throws Exception {
    NetServerOptions options = new NetServerOptions()
        .setPort(1234)
        .setHost("localhost")
        .setSsl(true)
        .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get());
    server = vertx.createNetServer(options);
    server.connectHandler(sock -> {

    });

    NetClientOptions clientOptions = new NetClientOptions()
        .setHostnameVerificationAlgorithm("HTTPS")
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("127.0.0.1").setPort(11080))
        .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());
    client = vertx.createNetClient(clientOptions);
    proxy = new SocksProxy();
    proxy.start(vertx);
    server.listen().await();
    client.connect(1234, "localhost")
      .compose(NetSocket::upgradeToSsl)
      .await();
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
    proxy = new HttpProxy();
    proxy.start(vertx);
    server.listen(1234, "localhost").await();
    client.connect(1234, "localhost").await();
    // make sure we have gone through the proxy
    assertEquals("localhost:1234", proxy.getLastUri());
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
    proxy = new Socks4Proxy();
    proxy.start(vertx);
    server.listen(1234, "localhost").await();
    client.connect(1234, "localhost").await();
    // make sure we have gone through the proxy
    assertEquals("localhost:1234", proxy.getLastUri());
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
    proxy = new Socks4Proxy().username("username");
    proxy.start(vertx);
    server.listen(1234, "localhost").await();
    client.connect(1234, "localhost").await();
    // make sure we have gone through the proxy
    assertEquals("localhost:1234", proxy.getLastUri());
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
    proxy = new Socks4Proxy().start(vertx);
    server.listen(1234, "localhost").await();
    client.connect(1234, "127.0.0.1").await();
    // make sure we have gone through the proxy
    assertEquals("127.0.0.1:1234", proxy.getLastUri());
  }

  @Test
  public void testNonProxyHosts() throws Exception {
    NetClientOptions clientOptions = new NetClientOptions()
      .addNonProxyHost("example.com")
      .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setPort(13128));
    NetClient client = vertx.createNetClient(clientOptions);
    server.connectHandler(sock -> {

    });
    proxy = new HttpProxy();
    proxy.start(vertx);
    server.listen(1234, "localhost").await();
    client.connect(1234, "example.com").await();
    assertNull(proxy.getLastUri());
  }

  @Test
  public void testTLSHostnameCertCheckCorrect(Checkpoint checkpoint) {
    NetClientOptions clientOptions = new NetClientOptions()
      .setHostnameVerificationAlgorithm("HTTPS")
      .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());
    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(true)
      .setPort(DEFAULT_HTTPS_PORT)
      .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get());
    client = vertx.createNetClient(clientOptions);
    server = vertx.createNetServer(serverOptions);
    server.connectHandler(netSocket -> checkpoint.succeed())
      .listen()
      .await();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST)
      .compose(NetSocket::upgradeToSsl)
      .await();
  }

  @Test
  public void testTLSHostnameCertCheckIncorrect(Checkpoint checkpoint) {
    NetClientOptions clientOptions = new NetClientOptions()
      .setHostnameVerificationAlgorithm("HTTPS")
      .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());
    NetServerOptions serverOptions = new NetServerOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT)
      .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get());
    server = vertx.createNetServer(serverOptions);
    client = vertx.createNetClient(clientOptions);
    server
      .exceptionHandler(err -> checkpoint.succeed())
      .connectHandler(so -> fail())
      .listen()
      .await();
    try {
      client
        .connect(DEFAULT_HTTPS_PORT, "127.0.0.1")
        .compose(NetSocket::upgradeToSsl)
        .await();
      fail();
    } catch (Exception expected) {
    }
  }

  /**
   * Test that NetSocket.upgradeToSsl() should fail the handler if no TLS configuration was set.
   */
  @Test
  public void testUpgradeToSSLIncorrectClientOptions1() {
    testUpgradeToSSLIncorrectClientOptions(() -> client.connect(DEFAULT_HTTPS_PORT, "127.0.0.1"));
  }

  /**
   * Test that NetSocket.upgradeToSsl() should fail the handler if no TLS configuration was set.
   */
  @Test
  public void testUpgradeToSSLIncorrectClientOptions2() {
    testUpgradeToSSLIncorrectClientOptions(() -> client.connect(new ConnectOptions().setPort(DEFAULT_HTTPS_PORT).setHost("127.0.0.1")));
  }

  /**
   * Test that NetSocket.upgradeToSsl() should fail the handler if no TLS configuration was set.
   */
  private void testUpgradeToSSLIncorrectClientOptions(Supplier<Future<NetSocket>> connect) {
    server = vertx.createNetServer(new NetServerOptions()
      .setSsl(true)
      .setPort(DEFAULT_HTTPS_PORT)
      .setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get()));
    server
      .connectHandler(ns -> {})
      .listen()
      .await();
    try {
      connect.get().compose(ns -> ns.upgradeToSsl()).await();
    } catch (Exception err) {
      assertTrue(err.getMessage().contains("Missing SSL options"));
    }
  }

  @Test
  public void testOverrideClientSSLOptions(Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(2);
    client = vertx.createNetClient(new NetClientOptions()
      .setTrustOptions(Trust.CLIENT_JKS.get()));
    server = vertx.createNetServer(new NetServerOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT)
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    server.connectHandler(ns -> {
      latch.countDown();
    }).listen().await();
    try {
      client
        .connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST)
        .compose(NetSocket::upgradeToSsl)
        .await();
      fail();
    } catch (Exception expected) {
    }
    ClientSSLOptions sslOptions = new ClientSSLOptions().setHostnameVerificationAlgorithm("").setTrustOptions(Trust.SERVER_JKS.get());
    client
      .connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST)
      .compose(so -> so.upgradeToSsl(sslOptions))
      .await();
    client
      .connect(new ConnectOptions().setPort(DEFAULT_HTTPS_PORT).setHost(DEFAULT_HTTPS_HOST).setSslOptions(sslOptions))
      .compose(NetSocket::upgradeToSsl)
      .await();
  }

  @Test
  public void testClientLocalAddress(Checkpoint checkpoint) {
    String expectedAddress = TestUtils.loopbackAddress();
    NetClientOptions clientOptions = new NetClientOptions().setLocalAddress(expectedAddress);
    server.connectHandler(sock -> {
      assertEquals(expectedAddress, sock.remoteAddress().host());
      checkpoint.succeed();
    });
    client = vertx.createNetClient(clientOptions);
    server.listen(1234, "localhost").await();
    client.connect(1234, "localhost").await();
  }

  @Test
  public void testWorkerClient(Checkpoint checkpoint) throws Exception {
    String expected = TestUtils.randomAlphaString(2000);
    server.connectHandler(so -> {
      so.write(expected);
      so.close();
    });
    startServer();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        NetClient client = vertx.createNetClient();
        client.connect(testAddress).onComplete(onSuccess(so ->{
          assertTrue(Context.isOnWorkerThread());
          Buffer received = Buffer.buffer();
          so.handler(buff -> {
            assertTrue(Context.isOnWorkerThread());
            received.appendBuffer(buff);
          });
          so.closeHandler(v -> {
            assertEquals(expected, received.toString());
            checkpoint.succeed();
          });
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }));

      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
  }

  @Test
  public void testWorkerServer(Checkpoint checkpoint) {
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
            checkpoint.succeed();
          });
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
        server.listen(testAddress).<Void>mapEmpty().onComplete(startPromise);
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)).await();
    NetSocket so = client.connect(testAddress).await();
    so.write(expected);
    so.close();
  }

  @Test
  public void testNetServerInternal(Checkpoint checkpoint) throws Exception {
    testNetServerInternal_(checkpoint, new HttpClientOptions(), false);
  }

  @Test
  public void testNetServerInternalTLS(Checkpoint checkpoint) throws Exception {
    server = vertx.createNetServer(new NetServerOptions()
      .setPort(1234)
      .setHost("localhost")
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    testNetServerInternal_(checkpoint, new HttpClientOptions()
      .setSsl(true)
      .setTrustOptions(Trust.SERVER_JKS.get())
    , true);
  }

  private void testNetServerInternal_(Checkpoint checkpoint1, HttpClientOptions clientOptions, boolean expectSSL) throws Exception {
    server.connectHandler(so -> {
      NetSocketInternal internal = (NetSocketInternal) so;
      assertEquals(expectSSL, internal.isSsl());
      ChannelHandlerContext chctx = internal.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("handler", "http", new HttpServerCodec());
      internal.handler(buff -> fail());
      AtomicBoolean last = new AtomicBoolean();
      internal.messageHandler(obj -> {
        last.set(obj instanceof LastHttpContent);
        ReferenceCountUtil.release(obj);
      });
      internal.readCompletionHandler(v1 -> {
        assertTrue(last.get());
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1,
          HttpResponseStatus.OK,
          Unpooled.copiedBuffer("Hello World", StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "11");
        internal.writeMessage(response).onComplete(checkpoint1);
      });
    });
    startServer(SocketAddress.inetSocketAddress(1234, "localhost"));
    HttpClient client = vertx.createHttpClient(clientOptions);
    Buffer body = client.request(io.vertx.core.http.HttpMethod.GET, 1234, "localhost", "/somepath")
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)).await();
    assertEquals("Hello World", body.toString());
  }

  @Test
  public void testNetClientInternal(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testNetClientInternal_(checkpoint1, checkpoint2, new HttpServerOptions().setHost("localhost").setPort(1234), false);
  }

  @Test
  public void testNetClientInternalTLS(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    client = vertx.createNetClient(new NetClientOptions()
      .setSsl(true)
      .setHostnameVerificationAlgorithm("")
      .setTrustOptions(Trust.SERVER_JKS.get())
    );
    testNetClientInternal_(checkpoint1, checkpoint2, new HttpServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get()), true);
  }

  // This test is here to cover a WildFly use case for passing in an SSLContext for which there are no
  // configuration options.
  // This is currently done by casing to NetClientImpl and calling setSuppliedSSLContext().
  @Test
  public void testNetClientInternalTLSWithSuppliedSSLContext(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
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

    client = vertx.createNetClient(new NetClientOptions().setSsl(true).setHostnameVerificationAlgorithm("")
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

    testNetClientInternal_(checkpoint1, checkpoint2, new HttpServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get()), true);
  }

  private void testNetClientInternal_(Checkpoint checkpoint1, Checkpoint checkpoint2, HttpServerOptions options, boolean expectSSL) throws Exception {
    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      req.response().end("Hello World"); });
    server.listen().await();
    client
      .connect(1234, "localhost")
      .onComplete(onSuccess(so -> {
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
              checkpoint1.succeed();
              break;
            default:
              fail();
          }
        });
        soInt
          .writeMessage(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/somepath"))
          .onComplete(checkpoint2);
      }));
  }

  @Test
  public void testNetSocketInternalBuffer(Checkpoint checkpoint) throws Exception {
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
    NetSocketInternal soi = (NetSocketInternal)client.connect(testAddress).await();
    soi.handler(msg -> {
      ByteBuf byteBuf = ((BufferInternal)msg).getByteBuf();
      assertFalse(byteBuf.isDirect());
      assertEquals(1, byteBuf.refCnt());
      assertFalse(byteBuf.release());
      assertEquals(1, byteBuf.refCnt());
      assertEquals("Hello World", msg.toString());
      checkpoint.succeed();
    });
    soi.write(Buffer.buffer("Hello World")).await();
  }

  @Test
  public void testNetSocketInternalDirectBuffer(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.connectHandler(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.messageHandler(msg -> {
        ByteBuf byteBuf = (ByteBuf) msg;
        assertTrue(byteBuf.isDirect());
        assertEquals(1, byteBuf.refCnt());
        soi
          .writeMessage(msg)
          .andThen(ar -> assertEquals(0, byteBuf.refCnt()))
          .onComplete(checkpoint1);
      });
    });
    startServer();
    NetSocketInternal soi = (NetSocketInternal)client.connect(testAddress).await();
    soi.messageHandler(msg -> {
      ByteBuf byteBuf = (ByteBuf) msg;
      assertTrue(byteBuf.isDirect());
      assertEquals(1, byteBuf.refCnt());
      assertEquals("Hello World", byteBuf.toString(StandardCharsets.UTF_8));
      assertTrue(byteBuf.release());
      assertEquals(0, byteBuf.refCnt());
      checkpoint2.succeed();
    });
    soi.write(Buffer.buffer("Hello World"));
    // soi.messageHandler(msg -> fail("Unexpected"));
  }

  @Test
  public void testNetSocketInternalRemoveVertxHandler(Checkpoint checkpoint) throws Exception {
    client = vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000).setRegisterWriteHandler(true));
    server.connectHandler(so -> {
      so.closeHandler(v -> checkpoint.succeed());
    });
    startServer();
    NetSocketInternal soi = (NetSocketInternal)client.connect(testAddress).await();
    String id = soi.writeHandlerID();
    ChannelHandlerContext ctx = soi.channelHandlerContext();
    ChannelPipeline pipeline = ctx.pipeline();
    pipeline.remove(VertxHandler.class);
    vertx.eventBus().request(id, "test").onComplete(TestUtils.onFailure(what -> {
      ctx.close();
    }));
  }

  // We only do it for server, as client uses the same NetSocket implementation
  @Test
  public void testServerNetSocketShouldBeClosedWhenTheClosedHandlerIsCalled(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.connectHandler(so -> {
      CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), 2, so);
      sender.send();
      so.closeHandler(v -> {
        Throwable failure = sender.close();
        if (failure != null) {
          fail(failure.getMessage());
        } else {
          checkpoint1.succeed();
        }
      });
      so.endHandler(v -> {
        Throwable failure = sender.close();
        if (failure != null) {
          fail(failure.getMessage());
        } else {
          checkpoint2.succeed();
        }
      });
    });
    startServer();
    NetSocket so = client.connect(testAddress).await();
    vertx.setTimer(1000, id -> {
      so.close();
    });
  }

  @Test
  public void testNetSocketInternalEvent(Checkpoint checkpoint) throws Exception {
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
    client
      .connect(testAddress)
      .onComplete(onSuccess(so -> so.closeHandler(v -> checkpoint.succeed())));
  }

  @Test
  public void testServerWithIdleTimeoutSendChunkedFile(Checkpoint checkpoint) throws Exception {
    testIdleTimeoutSendChunkedFile(checkpoint, true);
  }

  @Test
  public void testClientWithIdleTimeoutSendChunkedFile(Checkpoint checkpoint) throws Exception {
    testIdleTimeoutSendChunkedFile(checkpoint, false);
  }

  private void testIdleTimeoutSendChunkedFile(Checkpoint checkpoint, boolean idleOnServer) throws Exception {
    Assume.assumeFalse(TRANSPORT == Transport.IO_URING);
    int expected = 16 * 1024 * 1024; // We estimate this will take more than 200ms to transfer with a 1ms pause in chunks
    File sent = TestUtils.tmpFile(".dat", expected);
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
        checkpoint.succeed();
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
      so.exceptionHandler(err -> fail(err.getMessage()));
      so.endHandler(v -> {
        remaining.set(expected - len[0]);
        testChecker.run();
      });
    };
    server = vertx
      .createNetServer(new NetServerOptions().setIdleTimeout(200).setIdleTimeoutUnit(TimeUnit.MILLISECONDS))
      .connectHandler((idleOnServer ? sender : receiver)::accept);
    startServer();
    client = vertx.createNetClient(new NetClientOptions().setIdleTimeout(200).setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
    NetSocket so = client.connect(testAddress).await();
    (idleOnServer ? receiver : sender).accept(so);
  }

  @Test
  public void testHalfCloseCallsEndHandlerAfterBuffersAreDelivered(Checkpoint checkpoint) throws Exception {
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
    client
      .connect(testAddress, "localhost")
      .onComplete(onSuccess(so -> {
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
          checkpoint.succeed();
        });
      }));
  }

  @Test
  public void testSslHandshakeTimeoutHappenedOnServer(Checkpoint checkpoint) throws Exception {
    testSslHandshakeTimeoutHappened(checkpoint, false, false);
  }

  @Test
  public void testSslHandshakeTimeoutHappenedOnSniServer(Checkpoint checkpoint) throws Exception {
    testSslHandshakeTimeoutHappened(checkpoint, false, true);
  }

  public void testSslHandshakeTimeoutHappened(Checkpoint checkpoint, boolean onClient, boolean sni) throws Exception {
    // set up a normal server to force the SSL handshake time out in client
    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(!onClient)
      .setSslHandshakeTimeout(200)
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      .setSni(sni)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    server = vertx.createNetServer(serverOptions);

    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(onClient)
      .setTrustAll(true)
      .setSslHandshakeTimeout(200)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    client = vertx.createNetClient(clientOptions);

    Consumer<Throwable> checker = err -> {
      assertTrue(err instanceof SSLException);
      assertEquals("handshake timed out after 200ms", err.getMessage());
      checkpoint.succeed();
    };

    if (!onClient) {
      server.exceptionHandler(checker::accept);
    }
    server.connectHandler(s -> {
    });
    startServer();

    client.connect(testAddress).onComplete(ar -> {
      if (onClient) {
        assertTrue(ar.failed());
        checker.accept(ar.cause());
      }
    });
  }

  @Test
  public void testSslHandshakeTimeoutNotHappened() throws Exception {
    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      // set 100ms to let the connection established
      .setSslHandshakeTimeout(100)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    server = vertx.createNetServer(serverOptions);

    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(true)
      .setTrustAll(true)
      .setHostnameVerificationAlgorithm("");
    client = vertx.createNetClient(clientOptions);

    server.connectHandler(s -> {
    });
    startServer(testAddress);
    NetSocket so = client.connect(testAddress).await();
  }

  @Test
  public void testSslHandshakeTimeoutHappenedWhenUpgradeSsl() throws Exception {
    // set up a normal server to force the SSL handshake time out in client
    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(false);
    server = vertx.createNetServer(serverOptions);

    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(false)
      .setTrustAll(true)
      .setHostnameVerificationAlgorithm("")
      .setSslHandshakeTimeout(200)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    client = vertx.createNetClient(clientOptions);

    server.connectHandler(s -> {
    });
    startServer(testAddress);
    try {
      client.connect(testAddress)
        .compose(so -> {
          assertFalse(so.isSsl());
          return so.upgradeToSsl();
        }).await();
      fail();
    } catch (Exception err) {
      assertTrue(err instanceof SSLException);
      assertEquals("handshake timed out after 200ms", err.getMessage());
    }
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
    Promise<Object> promise = Promise.promise();
    context.runOnContext(v -> server.listen(remoteAddress).onComplete(promise));
    promise.future().await();
  }

  @Test
  public void testPausedDuringLastChunk(Checkpoint checkpoint) throws Exception {
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
        checkpoint.succeed();
      });
    });
    startServer();
    NetSocket so = client.connect(testAddress, "localhost").await();
    so.close().await();
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
  public void testNetSocketHandlerFailureReportedToContextExceptionHandler(Checkpoint checkpoint) throws Exception {
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
          checkpoint.succeed();
        });
        throw err3;
      });
    });
    startServer(testAddress);
    NetSocket so = client.connect(testAddress).await();
    so.write("ping").await();
    so.close().await();
  }

  @Test
  public void testHAProxyProtocolIdleTimeout(Checkpoint checkpoint) throws Exception {
    HAProxy proxy = new HAProxy(testAddress, Buffer.buffer());
    proxy.start(vertx);

    server = vertx.createNetServer(new NetServerOptions()
      .setProxyProtocolTimeout(2)
      .setUseProxyProtocol(true))
      .connectHandler(u -> fail("Should not be called"));
    startServer();
    client.connect(proxy.getPort(), proxy.getHost())
      .onComplete(onSuccess(so -> {
        so.closeHandler(event -> checkpoint.succeed());
      }));
    try {
      checkpoint.awaitSuccess();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolIdleTimeoutNotHappened(Checkpoint checkpoint) throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server = vertx.createNetServer(new NetServerOptions()
      .setProxyProtocolTimeout(100)
      .setProxyProtocolTimeoutUnit(TimeUnit.MILLISECONDS)
      .setUseProxyProtocol(true))
      .connectHandler(u -> checkpoint.succeed());
    startServer();
    NetSocket so = client.connect(proxy.getPort(), proxy.getHost()).await();
    so.close().await();
    try {
      checkpoint.awaitSuccess();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolConnectSSL(Checkpoint checkpoint) throws Exception {
    assumeTrue(testAddress.isInetSocket());
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    NetServerOptions options = new NetServerOptions()
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
        checkpoint.succeed();
      });

    startServer();
    NetClientOptions clientOptions = new NetClientOptions()
      .setHostnameVerificationAlgorithm("HTTPS")
      .setSsl(true)
      .setTrustOptions(Trust.SERVER_JKS_ROOT_CA.get());

    client = vertx.createNetClient(clientOptions);
    NetSocket so = client
      .connect(proxy.getPort(), proxy.getHost()).await();
    so.close().await();
    try {
      checkpoint.awaitSuccess();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolVersion1TCP4(Checkpoint checkpoint) throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(checkpoint, header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion1TCP6(Checkpoint checkpoint) throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion1TCP6ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(checkpoint, header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion1Unknown(Checkpoint checkpoint) throws Exception {
    assumeTrue(testAddress.isInetSocket());
    Buffer header = HAProxy.createVersion1UnknownProtocolHeader();
    testHAProxyProtocolAccepted(checkpoint, header, null, null);
  }

  @Test
  public void testHAProxyProtocolVersion2TCP4(Checkpoint checkpoint) throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion2TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(checkpoint, header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2TCP6(Checkpoint checkpoint) throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion2TCP6ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(checkpoint, header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2UnixSocket(Checkpoint checkpoint) throws Exception {
    SocketAddress remote = SocketAddress.domainSocketAddress("/tmp/remoteSocket");
    SocketAddress local = SocketAddress.domainSocketAddress("/tmp/localSocket");
    Buffer header = HAProxy.createVersion2UnixStreamProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(checkpoint, header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2Unknown(Checkpoint checkpoint) throws Exception {
    assumeTrue(testAddress.isInetSocket());
    Buffer header = HAProxy.createVersion2UnknownProtocolHeader();
    testHAProxyProtocolAccepted(checkpoint, header, null, null);
  }


  private void testHAProxyProtocolAccepted(Checkpoint checkpoint, Buffer header, SocketAddress remote, SocketAddress local) throws Exception {
    /*
     * In case remote / local is null then we will use the connected remote / local address from the proxy. This is needed
     * in order to test unknown protocol since we will use the actual connected addresses and ports.
     * This is only valid when testAddress is an InetSocketAddress. If testAddress is a DomainSocketAddress then
     * remoteAddress and localAddress are null
     *
     * Have in mind that proxies connectionRemoteAddress is the server request local address and proxies connectionLocalAddress is the
     * server request remote address.
     * */
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server = vertx.createNetServer(new NetServerOptions()
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
        checkpoint.succeed();
      });
    startServer();
    NetSocket so = client.connect(proxy.getPort(), proxy.getHost()).await();
    so.close().await();
    try {
      checkpoint.awaitSuccess();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolVersion2UDP4(Checkpoint checkpoint) throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion2UDP4ProtocolHeader(remote, local);
    testHAProxyProtocolRejected(checkpoint, header);
  }

  @Test
  public void testHAProxyProtocolVersion2UDP6(Checkpoint checkpoint) throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion2UDP6ProtocolHeader(remote, local);
    testHAProxyProtocolRejected(checkpoint, header);
  }

  @Test
  public void testHAProxyProtocolVersion2UnixDataGram(Checkpoint checkpoint) throws Exception {
    SocketAddress remote = SocketAddress.domainSocketAddress("/tmp/remoteSocket");
    SocketAddress local = SocketAddress.domainSocketAddress("/tmp/localSocket");
    Buffer header = HAProxy.createVersion2UnixDatagramProtocolHeader(remote, local);
    testHAProxyProtocolRejected(checkpoint, header);
  }

  private void testHAProxyProtocolRejected(Checkpoint checkpoint, Buffer header) throws Exception {
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server = vertx.createNetServer(new NetServerOptions()
      .setUseProxyProtocol(true))
      .exceptionHandler(ex -> {
        if (ex.equals(HAProxyMessageCompletionHandler.UNSUPPORTED_PROTOCOL_EXCEPTION))
          checkpoint.succeed();
      })
      .connectHandler(so -> fail());

    startServer();
    NetSocket so = client.connect(proxy.getPort(), proxy.getHost()).await();
    so.close().await();
    try {
      checkpoint.awaitSuccess();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolIllegalHeader1(Checkpoint checkpoint) throws Exception {
    testHAProxyProtocolIllegal(checkpoint, Buffer.buffer("This is an illegal HA PROXY protocol header\r\n"));
  }

  @Test
  public void testHAProxyProtocolIllegalHeader2(Checkpoint checkpoint) throws Exception {
    //IPv4 remote IPv6 Local
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolIllegal(checkpoint, header);
  }

  private void testHAProxyProtocolIllegal(Checkpoint checkpoint, Buffer header) throws Exception {
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server = vertx.createNetServer(new NetServerOptions().setUseProxyProtocol(true))
      .connectHandler(u -> fail("Should not be called")).exceptionHandler(exception -> {
        if (exception instanceof io.netty.handler.codec.haproxy.HAProxyProtocolException)
          checkpoint.succeed();
      });
    startServer();
    NetSocket so = client.connect(proxy.getPort(), proxy.getHost()).await();
    so.close().await();
    try {
      checkpoint.awaitSuccess();
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
  public void testConnectTimeout(Checkpoint checkpoint) {
    client = vertx.createNetClient(new NetClientOptions().setConnectTimeout(1));
    client.connect(1234, TestUtils.NON_ROUTABLE_HOST)
      .onComplete(TestUtils.onFailure(err -> {
        assertTrue(err instanceof ConnectTimeoutException);
        checkpoint.succeed();
      }));
  }

  @Test
  public void testConnectTimeoutOverride(Checkpoint checkpoint) {
    client = vertx.createNetClient();
    client.connect(new ConnectOptions()
        .setPort(1234)
        .setHost(TestUtils.NON_ROUTABLE_HOST)
        .setTimeout(1))
      .onComplete(TestUtils.onFailure(err -> {
        assertTrue(err instanceof ConnectTimeoutException);
        checkpoint.succeed();
      }));
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
  public void testClientShutdownHandlerTimeout(Checkpoint checkpoint) throws Exception {
    testClientShutdown(checkpoint, false, true, now -> System.currentTimeMillis() - now >= 2000);
  }

  @Test
  public void testClientShutdownHandlerOverride(Checkpoint checkpoint) throws Exception {
    testClientShutdown(checkpoint, true, true, now -> System.currentTimeMillis() - now <= 2000);
  }

  @Test
  public void testClientShutdown(Checkpoint checkpoint) throws Exception {
    testClientShutdown(checkpoint, true, false, now -> System.currentTimeMillis() - now <= 2000);
  }

  private void testClientShutdown(Checkpoint checkpoint, boolean override, boolean useHandler, LongPredicate checker) throws Exception {
    server.connectHandler(so -> {

    });
    startServer();
    NetClientInternal client = ((CleanableNetClient) vertx.createNetClient()).unwrap();
    long now = System.currentTimeMillis();
    NetSocket so = client.connect(testAddress).await();
    AtomicInteger eventCount = new AtomicInteger();
    if (useHandler) {
      so.shutdownHandler(v -> {
        assertEquals(1, eventCount.incrementAndGet());
        if (override) {
          so.close();
        }
      });
    }
    so.closeHandler(v -> {
      assertEquals(useHandler ? 1 : 0, eventCount.get());
      assertTrue(checker.test(now));
      checkpoint.succeed();
    });
    client.shutdown(2, TimeUnit.SECONDS).await();
    checkpoint.awaitSuccess();
    assertTrue(checker.test(now));
  }

  @Test
  public void testServerShutdownHandlerTimeout(Checkpoint checkpoint) throws Exception {
    testServerShutdown(checkpoint, false, true, now -> System.currentTimeMillis() - now >= 2000);
  }

  @Test
  public void testServerShutdownHandlerOverride(Checkpoint checkpoint) throws Exception {
    testServerShutdown(checkpoint, true, true, now -> System.currentTimeMillis() - now <= 2000);
  }

  @Rule
  public final RepeatRule repeatRule  = new RepeatRule();

  @Test
  public void testServerShutdown(Checkpoint checkpoint) throws Exception {
    testServerShutdown(checkpoint, false, false, now -> System.currentTimeMillis() - now <= 2000);
  }

  public void testServerShutdown(Checkpoint checkpoint, boolean closeServerSocketOnShutdown, boolean useHandler, LongPredicate checker) throws Exception {
    AtomicInteger eventCount = new AtomicInteger();
    long now = System.currentTimeMillis();
    AtomicInteger count = new AtomicInteger();
    server.connectHandler(so -> {
      count.incrementAndGet();
      if (useHandler) {
        so.shutdownHandler(v -> {
          eventCount.incrementAndGet();
          if (closeServerSocketOnShutdown) {
            so.close();
          }
        });
      }
      so.closeHandler(v -> {
        checkpoint.succeed();
      });
    });
    startServer();
    NetSocket so = client.connect(testAddress).await();
    so.write("ping").await();
    TestUtils.assertWaitUntil(() -> count.get() > 0);
    server.shutdown(10, TimeUnit.SECONDS).await();
    checkpoint.awaitSuccess();
    assertEquals(useHandler ? 1 : 0, eventCount.getAndIncrement());
    assertTrue(checker.test(now));
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
    TestUtils.awaitLatch(latch);
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
  public void testTLSServerSSLEnginePeerHost(Checkpoint checkpoint) throws Exception {
    testTLSServerSSLEnginePeerHostImpl(checkpoint, false);
  }

  /**
   * Test that for NetServer with start TLS, the peer host and port info is available
   * in the SSLEngine when the X509ExtendedKeyManager.chooseEngineServerAlias is called.
   *
   * @throws Exception if an error occurs
   */
  @Test
  public void testStartTLSServerSSLEnginePeerHost(Checkpoint checkpoint) throws Exception {
    testTLSServerSSLEnginePeerHostImpl(checkpoint, true);
  }

  private void testTLSServerSSLEnginePeerHostImpl(Checkpoint checkpoint, boolean startTLS) throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    testTLS(checkpoint, Cert.NONE, Trust.SERVER_JKS, testPeerHostServerCert(Cert.SERVER_JKS, called), Trust.NONE,
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
  public void testSNIServerSSLEnginePeerHost(Checkpoint checkpoint) throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    TLSTest test = new TLSTest(checkpoint)
      .clientTrust(Trust.SNI_JKS_HOST2)
      .address(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, "host2.com"))
      .serverCert(testPeerHostServerCert(Cert.SNI_JKS, called))
      .sni(true);
    test.run(true);
    assertEquals("host2.com", cnOf(test.clientPeerCert()));
    assertEquals("host2.com", test.indicatedServerName);
    assertTrue("X509ExtendedKeyManager.chooseEngineServerAlias is not called", called.get());
  }

  @Test
  public void testCloseConnectionAndClient(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3, Checkpoint checkpoint4) {
    server.connectHandler(so -> {
      so.closeHandler(v -> {
        checkpoint1.succeed();
      });
    }).listen(testAddress).await();
    Promise<Void> trigger = Promise.promise();
    NetSocket so = client.connect(testAddress).await();
    so.exceptionHandler(err -> fail(err.getMessage()));
    so.shutdownHandler(duration -> {
      trigger
        .future()
        .compose(v -> so.close())
        .onComplete(checkpoint2);
    });
    so.close().onComplete(checkpoint3);
    client.close().onComplete(checkpoint4);
    trigger.complete();
  }

  @Test
  public void testConnectWithResolvedSocketAddress() throws Exception {
    testConnectWithResolvedSocketAddress(false);
  }

  @Test
  public void testTlsConnectWithResolvedSocketAddress() throws Exception {
    testConnectWithResolvedSocketAddress(true);
  }

  private void testConnectWithResolvedSocketAddress(boolean ssl) throws Exception {
    Assume.assumeFalse(USE_DOMAIN_SOCKETS);
    AtomicInteger connects = new AtomicInteger();
    if (ssl) {
      server = vertx.createNetServer(new NetServerOptions()
        .setSsl(true)
        .setSni(true)
        .setKeyCertOptions(Cert.SNI_JKS.get())
      );
    }
    server.connectHandler(so -> {
      if (ssl) {
        assertEquals("host2.com", so.indicatedServerName());
      }
      connects.incrementAndGet();
    });
    startServer();
    String doesNotResolve = randomAlphaString(32);
    InetSocketAddress isa = new InetSocketAddress(Inet4Address.getByAddress(doesNotResolve, NetUtil.LOCALHOST4.getAddress()), 1234);
    ConnectOptions connect = new ConnectOptions()
      .setRemoteAddress(SocketAddress.inetSocketAddress(isa))
      .setSniServerName("host2.com");
    if (ssl) {
      connect.setSsl(true);
      connect.setSslOptions(new ClientSSLOptions()
        .setTrustOptions(Trust.SNI_JKS_HOST2.get())
        .setHostnameVerificationAlgorithm(""));
    }
    NetSocket socket = client.connect(connect).await();
    SocketAddress remove = socket.remoteAddress();
    assertEquals(doesNotResolve, remove.hostName());
    assertEquals("127.0.0.1", remove.hostAddress());
    assertWaitUntil(() -> connects.get() == 1);
  }
}
