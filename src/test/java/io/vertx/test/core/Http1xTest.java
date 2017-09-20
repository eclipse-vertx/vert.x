/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.netty.handler.codec.TooLongFrameException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.impl.HttpClientRequestImpl;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.NetworkOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.Pump;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.vertx.test.core.TestUtils.*;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class Http1xTest extends HttpTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    client = vertx.createHttpClient(new HttpClientOptions());
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST).setHandle100ContinueAutomatically(true));
  }

  @Test
  public void testClientOptions() {
    HttpClientOptions options = new HttpClientOptions();

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

    assertFalse(options.isUsePooledBuffers());
    assertEquals(options, options.setUsePooledBuffers(true));
    assertTrue(options.isUsePooledBuffers());

    assertEquals(0, options.getIdleTimeout());
    assertEquals(options, options.setIdleTimeout(10));
    assertEquals(10, options.getIdleTimeout());
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

    assertFalse(options.isTrustAll());
    assertEquals(options, options.setTrustAll(true));
    assertTrue(options.isTrustAll());

    assertTrue(options.isVerifyHost());
    assertEquals(options, options.setVerifyHost(false));
    assertFalse(options.isVerifyHost());

    assertEquals(5, options.getMaxPoolSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setMaxPoolSize(rand));
    assertEquals(rand, options.getMaxPoolSize());
    assertIllegalArgumentException(() -> options.setMaxPoolSize(0));
    assertIllegalArgumentException(() -> options.setMaxPoolSize(-1));

    assertTrue(options.isKeepAlive());
    assertEquals(options, options.setKeepAlive(false));
    assertFalse(options.isKeepAlive());

    assertFalse(options.isPipelining());
    assertEquals(options, options.setPipelining(true));
    assertTrue(options.isPipelining());

    assertEquals(HttpClientOptions.DEFAULT_PIPELINING_LIMIT, options.getPipeliningLimit());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setPipeliningLimit(rand));
    assertEquals(rand, options.getPipeliningLimit());
    assertIllegalArgumentException(() -> options.setPipeliningLimit(0));
    assertIllegalArgumentException(() -> options.setPipeliningLimit(-1));

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_MAX_POOL_SIZE, options.getHttp2MaxPoolSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setHttp2MaxPoolSize(rand));
    assertEquals(rand, options.getHttp2MaxPoolSize());
    assertIllegalArgumentException(() -> options.setHttp2MaxPoolSize(0));
    assertIllegalArgumentException(() -> options.setHttp2MaxPoolSize(-1));

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_MULTIPLEXING_LIMIT, options.getHttp2MultiplexingLimit());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setHttp2MultiplexingLimit(rand));
    assertEquals(rand, options.getHttp2MultiplexingLimit());
    assertIllegalArgumentException(() -> options.setHttp2MultiplexingLimit(0));
    assertEquals(options, options.setHttp2MultiplexingLimit(-1));
    assertEquals(-1, options.getHttp2MultiplexingLimit());

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE, options.getHttp2ConnectionWindowSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setHttp2ConnectionWindowSize(rand));
    assertEquals(rand, options.getHttp2ConnectionWindowSize());
    assertEquals(options, options.setHttp2ConnectionWindowSize(-1));
    assertEquals(-1, options.getHttp2ConnectionWindowSize());

    assertEquals(60000, options.getConnectTimeout());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setConnectTimeout(rand));
    assertEquals(rand, options.getConnectTimeout());
    assertIllegalArgumentException(() -> options.setConnectTimeout(-2));

    assertFalse(options.isTryUseCompression());
    assertEquals(options, options.setTryUseCompression(true));
    assertEquals(true, options.isTryUseCompression());

    assertTrue(options.getEnabledCipherSuites().isEmpty());
    assertEquals(options, options.addEnabledCipherSuite("foo"));
    assertEquals(options, options.addEnabledCipherSuite("bar"));
    assertNotNull(options.getEnabledCipherSuites());
    assertTrue(options.getEnabledCipherSuites().contains("foo"));
    assertTrue(options.getEnabledCipherSuites().contains("bar"));

    assertEquals(HttpVersion.HTTP_1_1, options.getProtocolVersion());
    assertEquals(options, options.setProtocolVersion(HttpVersion.HTTP_1_0));
    assertEquals(HttpVersion.HTTP_1_0, options.getProtocolVersion());
    assertIllegalArgumentException(() -> options.setProtocolVersion(null));

    assertEquals(HttpClientOptions.DEFAULT_MAX_CHUNK_SIZE, options.getMaxChunkSize());
    assertEquals(options, options.setMaxChunkSize(100));
    assertEquals(100, options.getMaxChunkSize());

    assertEquals(HttpClientOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH, options.getMaxInitialLineLength());
    assertEquals(options, options.setMaxInitialLineLength(100));
    assertEquals(100, options.getMaxInitialLineLength());

    assertEquals(HttpClientOptions.DEFAULT_MAX_HEADER_SIZE, options.getMaxHeaderSize());
    assertEquals(options, options.setMaxHeaderSize(100));
    assertEquals(100, options.getMaxHeaderSize());

    assertEquals(HttpClientOptions.DEFAULT_MAX_WAIT_QUEUE_SIZE, options.getMaxWaitQueueSize());
    assertEquals(options, options.setMaxWaitQueueSize(100));
    assertEquals(100, options.getMaxWaitQueueSize());

    Http2Settings initialSettings = randomHttp2Settings();
    assertEquals(new Http2Settings(), options.getInitialSettings());
    assertEquals(options, options.setInitialSettings(initialSettings));
    assertEquals(initialSettings, options.getInitialSettings());

    assertEquals(false, options.isUseAlpn());
    assertEquals(options, options.setUseAlpn(true));
    assertEquals(true, options.isUseAlpn());

    assertNull(options.getSslEngineOptions());
    assertEquals(options, options.setJdkSslEngineOptions(new JdkSSLEngineOptions()));
    assertTrue(options.getSslEngineOptions() instanceof JdkSSLEngineOptions);

    List<HttpVersion> alpnVersions = Collections.singletonList(HttpVersion.HTTP_1_1);
    assertEquals(HttpClientOptions.DEFAULT_ALPN_VERSIONS, options.getAlpnVersions());
    assertEquals(options, options.setAlpnVersions(alpnVersions));
    assertEquals(alpnVersions, options.getAlpnVersions());

    assertEquals(true, options.isHttp2ClearTextUpgrade());
    assertEquals(options, options.setHttp2ClearTextUpgrade(false));
    assertEquals(false, options.isHttp2ClearTextUpgrade());

    assertEquals(null, options.getLocalAddress());


    assertEquals(false,options.isSendUnmaskedFrames());
    assertEquals(options,options.setSendUnmaskedFrames(true));
    assertEquals(true,options.isSendUnmaskedFrames());

    assertEquals(HttpClientOptions.DEFAULT_DECODER_INITIAL_BUFFER_SIZE, options.getDecoderInitialBufferSize());
    assertEquals(options, options.setDecoderInitialBufferSize(256));
    assertEquals(256, options.getDecoderInitialBufferSize());
    assertIllegalArgumentException(() -> options.setDecoderInitialBufferSize(-1));
  }

  @Test
  public void testServerOptions() {
    HttpServerOptions options = new HttpServerOptions();

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

    assertFalse(options.isUsePooledBuffers());
    assertEquals(options, options.setUsePooledBuffers(true));
    assertTrue(options.isUsePooledBuffers());

    assertEquals(0, options.getIdleTimeout());
    assertEquals(options, options.setIdleTimeout(10));
    assertEquals(10, options.getIdleTimeout());
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

    assertFalse(options.isCompressionSupported());
    assertEquals(options, options.setCompressionSupported(true));
    assertTrue(options.isCompressionSupported());

    assertEquals(65536, options.getMaxWebsocketFrameSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setMaxWebsocketFrameSize(rand));
    assertEquals(rand, options.getMaxWebsocketFrameSize());

    assertEquals(80, options.getPort());
    assertEquals(options, options.setPort(1234));
    assertEquals(1234, options.getPort());
    assertIllegalArgumentException(() -> options.setPort(-1));
    assertIllegalArgumentException(() -> options.setPort(65536));

    assertEquals("0.0.0.0", options.getHost());
    String randString = TestUtils.randomUnicodeString(100);
    assertEquals(options, options.setHost(randString));
    assertEquals(randString, options.getHost());

    assertNull(options.getWebsocketSubProtocols());
    assertEquals(options, options.setWebsocketSubProtocols("foo"));
    assertEquals("foo", options.getWebsocketSubProtocols());

    HttpServerOptions optionsCopy = new HttpServerOptions(options);
    assertEquals(options, optionsCopy.setWebsocketSubProtocols(new String(options.getWebsocketSubProtocols())));

    assertTrue(options.getEnabledCipherSuites().isEmpty());
    assertEquals(options, options.addEnabledCipherSuite("foo"));
    assertEquals(options, options.addEnabledCipherSuite("bar"));
    assertNotNull(options.getEnabledCipherSuites());
    assertTrue(options.getEnabledCipherSuites().contains("foo"));
    assertTrue(options.getEnabledCipherSuites().contains("bar"));

    assertFalse(options.isHandle100ContinueAutomatically());
    assertEquals(options, options.setHandle100ContinueAutomatically(true));
    assertTrue(options.isHandle100ContinueAutomatically());

    assertEquals(false, options.isUseAlpn());
    assertEquals(options, options.setUseAlpn(true));
    assertEquals(true, options.isUseAlpn());

    assertNull(options.getSslEngineOptions());
    assertEquals(options, options.setJdkSslEngineOptions(new JdkSSLEngineOptions()));
    assertTrue(options.getSslEngineOptions() instanceof JdkSSLEngineOptions);

    Http2Settings initialSettings = randomHttp2Settings();
    assertEquals(new Http2Settings().setMaxConcurrentStreams(HttpServerOptions.DEFAULT_INITIAL_SETTINGS_MAX_CONCURRENT_STREAMS), options.getInitialSettings());
    assertEquals(options, options.setInitialSettings(initialSettings));
    assertEquals(initialSettings, options.getInitialSettings());

    List<HttpVersion> alpnVersions = Collections.singletonList(HttpVersion.HTTP_1_1);
    assertEquals(HttpServerOptions.DEFAULT_ALPN_VERSIONS, options.getAlpnVersions());
    assertEquals(options, options.setAlpnVersions(alpnVersions));
    assertEquals(alpnVersions, options.getAlpnVersions());

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE, options.getHttp2ConnectionWindowSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setHttp2ConnectionWindowSize(rand));
    assertEquals(rand, options.getHttp2ConnectionWindowSize());
    assertEquals(options, options.setHttp2ConnectionWindowSize(-1));
    assertEquals(-1, options.getHttp2ConnectionWindowSize());

    assertFalse(options.isDecompressionSupported());
    assertEquals(options, options.setDecompressionSupported(true));
    assertTrue(options.isDecompressionSupported());

    assertEquals(HttpServerOptions.DEFAULT_DECODER_INITIAL_BUFFER_SIZE, options.getDecoderInitialBufferSize());
    assertEquals(options, options.setDecoderInitialBufferSize(256));
    assertEquals(256, options.getDecoderInitialBufferSize());
    assertIllegalArgumentException(() -> options.setDecoderInitialBufferSize(-1));

  }

  @Test
  public void testCopyClientOptions() {
    HttpClientOptions options = new HttpClientOptions();
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
    KeyCertOptions keyCertOptions = randomKeyCertOptions();
    TrustOptions trustOptions = randomTrustOptions();
    String enabledCipher = TestUtils.randomAlphaString(100);
    int connectTimeout = TestUtils.randomPositiveInt();
    boolean trustAll = rand.nextBoolean();
    String crlPath = TestUtils.randomUnicodeString(100);
    Buffer crlValue = TestUtils.randomBuffer(100);

    boolean verifyHost = rand.nextBoolean();
    int maxPoolSize = TestUtils.randomPositiveInt();
    boolean keepAlive = rand.nextBoolean();
    boolean pipelining = rand.nextBoolean();
    int pipeliningLimit = TestUtils.randomPositiveInt();
    int http2MaxPoolSize = TestUtils.randomPositiveInt();
    int http2MultiplexingLimit = TestUtils.randomPositiveInt();
    int http2ConnectionWindowSize = TestUtils.randomPositiveInt();
    boolean tryUseCompression = rand.nextBoolean();
    HttpVersion protocolVersion = HttpVersion.HTTP_1_0;
    int maxChunkSize = TestUtils.randomPositiveInt();
    int maxInitialLineLength = TestUtils.randomPositiveInt();
    int maxHeaderSize = TestUtils.randomPositiveInt();
    int maxWaitQueueSize = TestUtils.randomPositiveInt();
    Http2Settings initialSettings = randomHttp2Settings();
    boolean useAlpn = TestUtils.randomBoolean();
    SSLEngineOptions sslEngine = TestUtils.randomBoolean() ? new JdkSSLEngineOptions() : new OpenSSLEngineOptions();
    List<HttpVersion> alpnVersions = Collections.singletonList(HttpVersion.values()[TestUtils.randomPositiveInt() % 3]);
    boolean h2cUpgrade = TestUtils.randomBoolean();
    boolean openSslSessionCacheEnabled = rand.nextBoolean();
    boolean sendUnmaskedFrame = rand.nextBoolean();
    String localAddress = TestUtils.randomAlphaString(10);
    int decoderInitialBufferSize = TestUtils.randomPositiveInt();

    options.setSendBufferSize(sendBufferSize);
    options.setReceiveBufferSize(receiverBufferSize);
    options.setReuseAddress(reuseAddress);
    options.setTrafficClass(trafficClass);
    options.setSsl(ssl);
    options.setTcpNoDelay(tcpNoDelay);
    options.setTcpKeepAlive(tcpKeepAlive);
    options.setSoLinger(soLinger);
    options.setUsePooledBuffers(usePooledBuffers);
    options.setIdleTimeout(idleTimeout);
    options.setKeyCertOptions(keyCertOptions);
    options.setTrustOptions(trustOptions);
    options.addEnabledCipherSuite(enabledCipher);
    options.setConnectTimeout(connectTimeout);
    options.setTrustAll(trustAll);
    options.addCrlPath(crlPath);
    options.addCrlValue(crlValue);
    options.setVerifyHost(verifyHost);
    options.setMaxPoolSize(maxPoolSize);
    options.setKeepAlive(keepAlive);
    options.setPipelining(pipelining);
    options.setPipeliningLimit(pipeliningLimit);
    options.setHttp2MaxPoolSize(http2MaxPoolSize);
    options.setHttp2MultiplexingLimit(http2MultiplexingLimit);
    options.setHttp2ConnectionWindowSize(http2ConnectionWindowSize);
    options.setTryUseCompression(tryUseCompression);
    options.setProtocolVersion(protocolVersion);
    options.setMaxChunkSize(maxChunkSize);
    options.setMaxInitialLineLength(maxInitialLineLength);
    options.setMaxHeaderSize(maxHeaderSize);
    options.setMaxWaitQueueSize(maxWaitQueueSize);
    options.setInitialSettings(initialSettings);
    options.setUseAlpn(useAlpn);
    options.setSslEngineOptions(sslEngine);
    options.setAlpnVersions(alpnVersions);
    options.setHttp2ClearTextUpgrade(h2cUpgrade);
    options.setLocalAddress(localAddress);
    options.setSendUnmaskedFrames(sendUnmaskedFrame);
    options.setDecoderInitialBufferSize(decoderInitialBufferSize);
    HttpClientOptions copy = new HttpClientOptions(options);
    checkCopyHttpClientOptions(options, copy);
    HttpClientOptions copy2 = new HttpClientOptions(options.toJson());
    checkCopyHttpClientOptions(options, copy2);
  }

  private void checkCopyHttpClientOptions(HttpClientOptions options, HttpClientOptions copy) {
    assertEquals(options.getSendBufferSize(), copy.getSendBufferSize());
    assertEquals(options.getReceiveBufferSize(), copy.getReceiveBufferSize());
    assertEquals(options.isReuseAddress(), copy.isReuseAddress());
    assertEquals(options.getTrafficClass(), copy.getTrafficClass());
    assertEquals(options.isTcpNoDelay(), copy.isTcpNoDelay());
    assertEquals(options.isTcpKeepAlive(), copy.isTcpKeepAlive());
    assertEquals(options.getSoLinger(), copy.getSoLinger());
    assertEquals(options.isUsePooledBuffers(), copy.isUsePooledBuffers());
    assertEquals(options.getIdleTimeout(), copy.getIdleTimeout());
    assertEquals(options.isSsl(), copy.isSsl());
    assertNotSame(options.getKeyCertOptions(), copy.getKeyCertOptions());
    assertEquals(options.getKeyCertOptions(), copy.getKeyCertOptions());
    assertNotSame(options.getTrustOptions(), copy.getTrustOptions());
    if (copy.getTrustOptions() instanceof PemTrustOptions) {
      assertEquals(((PemTrustOptions) options.getTrustOptions()).getCertValues(), ((PemTrustOptions) copy.getTrustOptions()).getCertValues());
    } else {
      assertEquals(options.getTrustOptions(), copy.getTrustOptions());
    }
    assertEquals(1, copy.getEnabledCipherSuites().size());
    assertEquals(options.getEnabledCipherSuites(), copy.getEnabledCipherSuites());
    assertEquals(options.getConnectTimeout(), copy.getConnectTimeout());
    assertEquals(options.isTrustAll(), copy.isTrustAll());
    assertEquals(1, copy.getCrlPaths().size());
    assertEquals(options.getCrlPaths().get(0), copy.getCrlPaths().get(0));
    assertEquals(1, copy.getCrlValues().size());
    assertEquals(options.getCrlValues().get(0), copy.getCrlValues().get(0));
    assertEquals(options.isVerifyHost(), copy.isVerifyHost());
    assertEquals(options.getMaxPoolSize(), copy.getMaxPoolSize());
    assertEquals(options.isKeepAlive(), copy.isKeepAlive());
    assertEquals(options.isPipelining(), copy.isPipelining());
    assertEquals(options.getPipeliningLimit(), copy.getPipeliningLimit());
    assertEquals(options.getHttp2MaxPoolSize(), copy.getHttp2MaxPoolSize());
    assertEquals(options.getHttp2MultiplexingLimit(), copy.getHttp2MultiplexingLimit());
    assertEquals(options.getHttp2ConnectionWindowSize(), copy.getHttp2ConnectionWindowSize());
    assertEquals(options.isTryUseCompression(), copy.isTryUseCompression());
    assertEquals(options.getProtocolVersion(), copy.getProtocolVersion());
    assertEquals(options.getMaxChunkSize(), copy.getMaxChunkSize());
    assertEquals(options.getMaxInitialLineLength(), copy.getMaxInitialLineLength());
    assertEquals(options.getMaxHeaderSize(), copy.getMaxHeaderSize());
    assertEquals(options.getMaxWaitQueueSize(), copy.getMaxWaitQueueSize());
    assertEquals(options.getInitialSettings(), copy.getInitialSettings());
    assertEquals(options.isUseAlpn(), copy.isUseAlpn());
    assertEquals(options.getSslEngineOptions(), copy.getSslEngineOptions());
    assertEquals(options.getAlpnVersions(), copy.getAlpnVersions());
    assertEquals(options.isHttp2ClearTextUpgrade(), copy.isHttp2ClearTextUpgrade());
    assertEquals(options.getLocalAddress(), copy.getLocalAddress());
    assertEquals(options.isSendUnmaskedFrames(), copy.isSendUnmaskedFrames());
  }

  @Test
  public void testDefaultClientOptionsJson() {
    HttpClientOptions def = new HttpClientOptions();
    HttpClientOptions json = new HttpClientOptions(new JsonObject());
    assertEquals(def.getMaxPoolSize(), json.getMaxPoolSize());
    assertEquals(def.isKeepAlive(), json.isKeepAlive());
    assertEquals(def.isPipelining(), json.isPipelining());
    assertEquals(def.getPipeliningLimit(), json.getPipeliningLimit());
    assertEquals(def.getHttp2MaxPoolSize(), json.getHttp2MaxPoolSize());
    assertEquals(def.getHttp2MultiplexingLimit(), json.getHttp2MultiplexingLimit());
    assertEquals(def.getHttp2ConnectionWindowSize(), json.getHttp2ConnectionWindowSize());
    assertEquals(def.isVerifyHost(), json.isVerifyHost());
    assertEquals(def.isTryUseCompression(), json.isTryUseCompression());
    assertEquals(def.isTrustAll(), json.isTrustAll());
    assertEquals(def.getCrlPaths(), json.getCrlPaths());
    assertEquals(def.getCrlValues(), json.getCrlValues());
    assertEquals(def.getConnectTimeout(), json.getConnectTimeout());
    assertEquals(def.isTcpNoDelay(), json.isTcpNoDelay());
    assertEquals(def.isTcpKeepAlive(), json.isTcpKeepAlive());
    assertEquals(def.getSoLinger(), json.getSoLinger());
    assertEquals(def.isUsePooledBuffers(), json.isUsePooledBuffers());
    assertEquals(def.isSsl(), json.isSsl());
    assertEquals(def.getProtocolVersion(), json.getProtocolVersion());
    assertEquals(def.getMaxWaitQueueSize(), json.getMaxWaitQueueSize());
    assertEquals(def.getMaxChunkSize(), json.getMaxChunkSize());
    assertEquals(def.getMaxInitialLineLength(), json.getMaxInitialLineLength());
    assertEquals(def.getMaxHeaderSize(), json.getMaxHeaderSize());
    assertEquals(def.getInitialSettings(), json.getInitialSettings());
    assertEquals(def.isUseAlpn(), json.isUseAlpn());
    assertEquals(def.getSslEngineOptions(), json.getSslEngineOptions());
    assertEquals(def.getAlpnVersions(), json.getAlpnVersions());
    assertEquals(def.isHttp2ClearTextUpgrade(), json.isHttp2ClearTextUpgrade());
    assertEquals(def.getLocalAddress(), json.getLocalAddress());
    assertEquals(def.getDecoderInitialBufferSize(), json.getDecoderInitialBufferSize());
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
    int connectTimeout = TestUtils.randomPositiveInt();
    boolean trustAll = rand.nextBoolean();
    String crlPath = TestUtils.randomUnicodeString(100);
    boolean verifyHost = rand.nextBoolean();
    int maxPoolSize = TestUtils.randomPositiveInt();
    boolean keepAlive = rand.nextBoolean();
    boolean pipelining = rand.nextBoolean();
    int pipeliningLimit = TestUtils.randomPositiveInt();
    int http2MaxPoolSize = TestUtils.randomPositiveInt();
    int http2MultiplexingLimit = TestUtils.randomPositiveInt();
    int http2ConnectionWindowSize = TestUtils.randomPositiveInt();
    boolean tryUseCompression = rand.nextBoolean();
    HttpVersion protocolVersion = HttpVersion.HTTP_1_1;
    int maxChunkSize = TestUtils.randomPositiveInt();
    int maxInitialLineLength = TestUtils.randomPositiveInt();
    int maxHeaderSize = TestUtils.randomPositiveInt();
    int maxWaitQueueSize = TestUtils.randomPositiveInt();
    Http2Settings initialSettings = randomHttp2Settings();
    boolean useAlpn = TestUtils.randomBoolean();
    String sslEngine = TestUtils.randomBoolean() ? "jdkSslEngineOptions" : "openSslEngineOptions";
    List<HttpVersion> alpnVersions = Collections.singletonList(HttpVersion.values()[TestUtils.randomPositiveInt() % 3]);
    boolean h2cUpgrade = rand.nextBoolean();
    boolean openSslSessionCacheEnabled = rand.nextBoolean();
    String localAddress = TestUtils.randomAlphaString(10);
    int decoderInitialBufferSize = TestUtils.randomPositiveInt();

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
      .put("connectTimeout", connectTimeout)
      .put("trustAll", trustAll)
      .put("crlPaths", new JsonArray().add(crlPath))
      .put("keyStoreOptions", new JsonObject().put("password", ksPassword).put("path", ksPath))
      .put("trustStoreOptions", new JsonObject().put("password", tsPassword).put("path", tsPath))
      .put("verifyHost", verifyHost)
      .put("maxPoolSize", maxPoolSize)
      .put("keepAlive", keepAlive)
      .put("pipelining", pipelining)
      .put("pipeliningLimit", pipeliningLimit)
      .put("http2MaxPoolSize", http2MaxPoolSize)
      .put("http2MultiplexingLimit", http2MultiplexingLimit)
      .put("http2ConnectionWindowSize", http2ConnectionWindowSize)
      .put("tryUseCompression", tryUseCompression)
      .put("protocolVersion", protocolVersion.name())
      .put("maxChunkSize", maxChunkSize)
      .put("maxInitialLineLength", maxInitialLineLength)
      .put("maxHeaderSize", maxHeaderSize)
      .put("maxWaitQueueSize", maxWaitQueueSize)
      .put("initialSettings", new JsonObject()
          .put("pushEnabled", initialSettings.isPushEnabled())
          .put("headerTableSize", initialSettings.getHeaderTableSize())
          .put("maxHeaderListSize", initialSettings.getMaxHeaderListSize())
          .put("maxConcurrentStreams", initialSettings.getMaxConcurrentStreams())
          .put("initialWindowSize", initialSettings.getInitialWindowSize())
          .put("maxFrameSize", initialSettings.getMaxFrameSize()))
      .put("useAlpn", useAlpn)
      .put(sslEngine, new JsonObject())
      .put("alpnVersions", new JsonArray().add(alpnVersions.get(0).name()))
      .put("http2ClearTextUpgrade", h2cUpgrade)
      .put("openSslSessionCacheEnabled", openSslSessionCacheEnabled)
      .put("localAddress", localAddress)
      .put("decoderInitialBufferSize", decoderInitialBufferSize);

    HttpClientOptions options = new HttpClientOptions(json);
    assertEquals(sendBufferSize, options.getSendBufferSize());
    assertEquals(receiverBufferSize, options.getReceiveBufferSize());
    assertEquals(reuseAddress, options.isReuseAddress());
    assertEquals(trafficClass, options.getTrafficClass());
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(tcpNoDelay, options.isTcpNoDelay());
    assertEquals(soLinger, options.getSoLinger());
    assertEquals(usePooledBuffers, options.isUsePooledBuffers());
    assertEquals(idleTimeout, options.getIdleTimeout());
    assertEquals(ssl, options.isSsl());
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
    assertEquals(verifyHost, options.isVerifyHost());
    assertEquals(maxPoolSize, options.getMaxPoolSize());
    assertEquals(keepAlive, options.isKeepAlive());
    assertEquals(pipelining, options.isPipelining());
    assertEquals(pipeliningLimit, options.getPipeliningLimit());
    assertEquals(http2MaxPoolSize, options.getHttp2MaxPoolSize());
    assertEquals(http2MultiplexingLimit, options.getHttp2MultiplexingLimit());
    assertEquals(http2ConnectionWindowSize, options.getHttp2ConnectionWindowSize());
    assertEquals(tryUseCompression, options.isTryUseCompression());
    assertEquals(protocolVersion, options.getProtocolVersion());
    assertEquals(maxChunkSize, options.getMaxChunkSize());
    assertEquals(maxInitialLineLength, options.getMaxInitialLineLength());
    assertEquals(maxHeaderSize, options.getMaxHeaderSize());
    assertEquals(maxWaitQueueSize, options.getMaxWaitQueueSize());
    assertEquals(initialSettings, options.getInitialSettings());
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
    assertEquals(alpnVersions, options.getAlpnVersions());
    assertEquals(h2cUpgrade, options.isHttp2ClearTextUpgrade());
    assertEquals(localAddress, options.getLocalAddress());
    assertEquals(decoderInitialBufferSize, options.getDecoderInitialBufferSize());

    // Test other keystore/truststore types
    json.remove("keyStoreOptions");
    json.remove("trustStoreOptions");
    json.put("pfxKeyCertOptions", new JsonObject().put("password", ksPassword))
      .put("pfxTrustOptions", new JsonObject().put("password", tsPassword));
    options = new HttpClientOptions(json);
    assertTrue(options.getTrustOptions() instanceof PfxOptions);
    assertTrue(options.getKeyCertOptions() instanceof PfxOptions);

    json.remove("pfxKeyCertOptions");
    json.remove("pfxTrustOptions");
    json.put("pemKeyCertOptions", new JsonObject())
      .put("pemTrustOptions", new JsonObject());
    options = new HttpClientOptions(json);
    assertTrue(options.getTrustOptions() instanceof PemTrustOptions);
    assertTrue(options.getKeyCertOptions() instanceof PemKeyCertOptions);

    // Test invalid protocolVersion
    json.put("protocolVersion", "invalidProtocolVersion");
    assertIllegalArgumentException(() -> new HttpClientOptions(json));
  }

  @Test
  public void testCopyServerOptions() {
    HttpServerOptions options = new HttpServerOptions();
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
    KeyCertOptions keyCertOptions = randomKeyCertOptions();
    TrustOptions trustOptions = randomTrustOptions();
    String enabledCipher = TestUtils.randomAlphaString(100);
    String crlPath = TestUtils.randomUnicodeString(100);
    Buffer crlValue = TestUtils.randomBuffer(100);
    int port = 1234;
    String host = TestUtils.randomAlphaString(100);
    int acceptBacklog = TestUtils.randomPortInt();
    boolean compressionSupported = rand.nextBoolean();
    int maxWebsocketFrameSize = TestUtils.randomPositiveInt();
    String wsSubProtocol = TestUtils.randomAlphaString(10);
    boolean is100ContinueHandledAutomatically = rand.nextBoolean();
    int maxChunkSize = rand.nextInt(10000);
    Http2Settings initialSettings = randomHttp2Settings();
    boolean useAlpn = TestUtils.randomBoolean();
    int http2ConnectionWindowSize = TestUtils.randomInt();
    boolean openSslSessionCacheEnabled = rand.nextBoolean();
    SSLEngineOptions sslEngine = TestUtils.randomBoolean() ? new JdkSSLEngineOptions() : new OpenSSLEngineOptions();
    List<HttpVersion> alpnVersions = Collections.singletonList(HttpVersion.values()[TestUtils.randomPositiveInt() % 3]);
    boolean decompressionSupported = rand.nextBoolean();
    boolean acceptUnmaskedFrames = rand.nextBoolean();
    int decoderInitialBufferSize = TestUtils.randomPositiveInt();

    options.setSendBufferSize(sendBufferSize);
    options.setReceiveBufferSize(receiverBufferSize);
    options.setReuseAddress(reuseAddress);
    options.setTrafficClass(trafficClass);
    options.setTcpNoDelay(tcpNoDelay);
    options.setTcpKeepAlive(tcpKeepAlive);
    options.setSoLinger(soLinger);
    options.setUsePooledBuffers(usePooledBuffers);
    options.setIdleTimeout(idleTimeout);
    options.setSsl(ssl);
    options.setKeyCertOptions(keyCertOptions);
    options.setTrustOptions(trustOptions);
    options.addEnabledCipherSuite(enabledCipher);
    options.addCrlPath(crlPath);
    options.addCrlValue(crlValue);
    options.setPort(port);
    options.setHost(host);
    options.setAcceptBacklog(acceptBacklog);
    options.setCompressionSupported(compressionSupported);
    options.setMaxWebsocketFrameSize(maxWebsocketFrameSize);
    options.setWebsocketSubProtocols(wsSubProtocol);
    options.setHandle100ContinueAutomatically(is100ContinueHandledAutomatically);
    options.setMaxChunkSize(maxChunkSize);
    options.setUseAlpn(useAlpn);
    options.setHttp2ConnectionWindowSize(http2ConnectionWindowSize);
    options.setSslEngineOptions(sslEngine);
    options.setInitialSettings(initialSettings);
    options.setAlpnVersions(alpnVersions);
    options.setDecompressionSupported(decompressionSupported);
    options.setAcceptUnmaskedFrames(acceptUnmaskedFrames);
    options.setDecoderInitialBufferSize(decoderInitialBufferSize);

    HttpServerOptions copy = new HttpServerOptions(options);
    checkCopyHttpServerOptions(options, copy);
    HttpServerOptions copy2 = new HttpServerOptions(options.toJson());
    checkCopyHttpServerOptions(options, copy2);
  }

  private void checkCopyHttpServerOptions(HttpServerOptions options, HttpServerOptions copy) {
    assertEquals(options.getSendBufferSize(), copy.getSendBufferSize());
    assertEquals(options.getReceiveBufferSize(), copy.getReceiveBufferSize());
    assertEquals(options.isReuseAddress(), copy.isReuseAddress());
    assertEquals(options.getTrafficClass(), copy.getTrafficClass());
    assertEquals(options.isTcpNoDelay(), copy.isTcpNoDelay());
    assertEquals(options.isTcpKeepAlive(), copy.isTcpKeepAlive());
    assertEquals(options.getSoLinger(), copy.getSoLinger());
    assertEquals(options.isUsePooledBuffers(), copy.isUsePooledBuffers());
    assertEquals(options.getIdleTimeout(), copy.getIdleTimeout());
    assertEquals(options.isSsl(), copy.isSsl());
    assertNotSame(options.getKeyCertOptions(), copy.getKeyCertOptions());
    assertEquals(options.getKeyCertOptions(), copy.getKeyCertOptions());
    assertNotSame(options.getTrustOptions(), copy.getTrustOptions());
    if (copy.getTrustOptions() instanceof PemTrustOptions) {
      assertEquals(((PemTrustOptions) options.getTrustOptions()).getCertValues(), ((PemTrustOptions) copy.getTrustOptions()).getCertValues());
    } else {
      assertEquals(options.getTrustOptions(), copy.getTrustOptions());
    }
    assertEquals(1, copy.getEnabledCipherSuites().size());
    assertEquals(options.getEnabledCipherSuites(), copy.getEnabledCipherSuites());
    assertEquals(1, copy.getCrlPaths().size());
    assertEquals(options.getCrlPaths().get(0), copy.getCrlPaths().get(0));
    assertEquals(1, copy.getCrlValues().size());
    assertEquals(options.getCrlValues().get(0), copy.getCrlValues().get(0));
    assertEquals(options.getPort(), copy.getPort());
    assertEquals(options.getHost(), copy.getHost());
    assertEquals(options.getAcceptBacklog(), copy.getAcceptBacklog());
    assertEquals(options.isCompressionSupported(), copy.isCompressionSupported());
    assertEquals(options.getMaxWebsocketFrameSize(), copy.getMaxWebsocketFrameSize());
    assertEquals(options.getWebsocketSubProtocols(), copy.getWebsocketSubProtocols());
    assertEquals(options.isHandle100ContinueAutomatically(), copy.isHandle100ContinueAutomatically());
    assertEquals(options.getMaxChunkSize(), copy.getMaxChunkSize());
    assertEquals(options.getInitialSettings(), copy.getInitialSettings());
    assertEquals(options.isUseAlpn(), copy.isUseAlpn());
    assertEquals(options.getHttp2ConnectionWindowSize(), copy.getHttp2ConnectionWindowSize());
    assertEquals(options.getSslEngineOptions(), copy.getSslEngineOptions());
    assertEquals(options.getAlpnVersions(), copy.getAlpnVersions());
    assertEquals(options.isDecompressionSupported(), copy.isDecompressionSupported());
    assertEquals(options.isAcceptUnmaskedFrames(), copy.isAcceptUnmaskedFrames());
    assertEquals(options.getDecoderInitialBufferSize(), copy.getDecoderInitialBufferSize());
  }

  @Test
  public void testDefaultServerOptionsJson() {
    HttpServerOptions def = new HttpServerOptions();
    HttpServerOptions json = new HttpServerOptions(new JsonObject());
    assertEquals(def.getMaxWebsocketFrameSize(), json.getMaxWebsocketFrameSize());
    assertEquals(def.getWebsocketSubProtocols(), json.getWebsocketSubProtocols());
    assertEquals(def.isCompressionSupported(), json.isCompressionSupported());
    assertEquals(def.isClientAuthRequired(), json.isClientAuthRequired());
    assertEquals(def.getCrlPaths(), json.getCrlPaths());
    assertEquals(def.getCrlValues(), json.getCrlValues());
    assertEquals(def.getAcceptBacklog(), json.getAcceptBacklog());
    assertEquals(def.getPort(), json.getPort());
    assertEquals(def.getHost(), json.getHost());
    assertEquals(def.isTcpNoDelay(), json.isTcpNoDelay());
    assertEquals(def.isTcpKeepAlive(), json.isTcpKeepAlive());
    assertEquals(def.getSoLinger(), json.getSoLinger());
    assertEquals(def.isUsePooledBuffers(), json.isUsePooledBuffers());
    assertEquals(def.isSsl(), json.isSsl());
    assertEquals(def.isHandle100ContinueAutomatically(), json.isHandle100ContinueAutomatically());
    assertEquals(def.getMaxChunkSize(), json.getMaxChunkSize());
    assertEquals(def.getMaxInitialLineLength(), json.getMaxInitialLineLength());
    assertEquals(def.getMaxHeaderSize(), json.getMaxHeaderSize());
    assertEquals(def.getInitialSettings(), json.getInitialSettings());
    assertEquals(def.isUseAlpn(), json.isUseAlpn());
    assertEquals(def.getSslEngineOptions(), json.getSslEngineOptions());
    assertEquals(def.getAlpnVersions(), json.getAlpnVersions());
    assertEquals(def.getHttp2ConnectionWindowSize(), json.getHttp2ConnectionWindowSize());
    assertEquals(def.isDecompressionSupported(), json.isDecompressionSupported());
    assertEquals(def.isAcceptUnmaskedFrames(), json.isAcceptUnmaskedFrames());
    assertEquals(def.getDecoderInitialBufferSize(), json.getDecoderInitialBufferSize());
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
    boolean compressionSupported = rand.nextBoolean();
    int maxWebsocketFrameSize = TestUtils.randomPositiveInt();
    String wsSubProtocol = TestUtils.randomAlphaString(10);
    boolean is100ContinueHandledAutomatically = rand.nextBoolean();
    int maxChunkSize = rand.nextInt(10000);
    int maxInitialLineLength = rand.nextInt(10000);
    int maxHeaderSize = rand.nextInt(10000);
    HttpVersion enabledProtocol = HttpVersion.values()[rand.nextInt(HttpVersion.values().length)];
    Http2Settings initialSettings = TestUtils.randomHttp2Settings();
    boolean useAlpn = TestUtils.randomBoolean();
    int http2ConnectionWindowSize = TestUtils.randomInt();
    String sslEngine = TestUtils.randomBoolean() ? "jdkSslEngineOptions" : "openSslEngineOptions";
    List<HttpVersion> alpnVersions = Collections.singletonList(HttpVersion.values()[TestUtils.randomPositiveInt() % 3]);
    boolean openSslSessionCacheEnabled = TestUtils.randomBoolean();
    boolean decompressionSupported = TestUtils.randomBoolean();
    boolean acceptUnmaskedFrames = TestUtils.randomBoolean();
    int decoderInitialBufferSize = TestUtils.randomPositiveInt();

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
      .put("compressionSupported", compressionSupported)
      .put("maxWebsocketFrameSize", maxWebsocketFrameSize)
      .put("websocketSubProtocols", wsSubProtocol)
      .put("handle100ContinueAutomatically", is100ContinueHandledAutomatically)
      .put("maxChunkSize", maxChunkSize)
      .put("maxInitialLineLength", maxInitialLineLength)
      .put("maxHeaderSize", maxHeaderSize)
      .put("enabledProtocols", new JsonArray().add(enabledProtocol.name()))
      .put("initialSettings", new JsonObject()
          .put("pushEnabled", initialSettings.isPushEnabled())
          .put("headerTableSize", initialSettings.getHeaderTableSize())
          .put("maxHeaderListSize", initialSettings.getMaxHeaderListSize())
          .put("maxConcurrentStreams", initialSettings.getMaxConcurrentStreams())
          .put("initialWindowSize", initialSettings.getInitialWindowSize())
          .put("maxFrameSize", initialSettings.getMaxFrameSize()))
      .put("useAlpn", useAlpn)
      .put("http2ConnectionWindowSize", http2ConnectionWindowSize)
      .put(sslEngine, new JsonObject())
      .put("alpnVersions", new JsonArray().add(alpnVersions.get(0).name()))
      .put("openSslSessionCacheEnabled", openSslSessionCacheEnabled)
      .put("decompressionSupported", decompressionSupported)
      .put("acceptUnmaskedFrames", acceptUnmaskedFrames)
      .put("decoderInitialBufferSize", decoderInitialBufferSize);

    HttpServerOptions options = new HttpServerOptions(json);
    assertEquals(sendBufferSize, options.getSendBufferSize());
    assertEquals(receiverBufferSize, options.getReceiveBufferSize());
    assertEquals(reuseAddress, options.isReuseAddress());
    assertEquals(trafficClass, options.getTrafficClass());
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(tcpNoDelay, options.isTcpNoDelay());
    assertEquals(soLinger, options.getSoLinger());
    assertEquals(usePooledBuffers, options.isUsePooledBuffers());
    assertEquals(idleTimeout, options.getIdleTimeout());
    assertEquals(ssl, options.isSsl());
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
    assertEquals(compressionSupported, options.isCompressionSupported());
    assertEquals(maxWebsocketFrameSize, options.getMaxWebsocketFrameSize());
    assertEquals(wsSubProtocol, options.getWebsocketSubProtocols());
    assertEquals(is100ContinueHandledAutomatically, options.isHandle100ContinueAutomatically());
    assertEquals(maxChunkSize, options.getMaxChunkSize());
    assertEquals(maxInitialLineLength, options.getMaxInitialLineLength());
    assertEquals(maxHeaderSize, options.getMaxHeaderSize());
    assertEquals(initialSettings, options.getInitialSettings());
    assertEquals(useAlpn, options.isUseAlpn());
    assertEquals(http2ConnectionWindowSize, options.getHttp2ConnectionWindowSize());
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
    assertEquals(alpnVersions, options.getAlpnVersions());
    assertEquals(decompressionSupported, options.isDecompressionSupported());
    assertEquals(acceptUnmaskedFrames, options.isAcceptUnmaskedFrames());
    assertEquals(decoderInitialBufferSize, options.getDecoderInitialBufferSize());

    // Test other keystore/truststore types
    json.remove("keyStoreOptions");
    json.remove("trustStoreOptions");
    json.put("pfxKeyCertOptions", new JsonObject().put("password", ksPassword))
      .put("pfxTrustOptions", new JsonObject().put("password", tsPassword));
    options = new HttpServerOptions(json);
    assertTrue(options.getTrustOptions() instanceof PfxOptions);
    assertTrue(options.getKeyCertOptions() instanceof PfxOptions);

    json.remove("pfxKeyCertOptions");
    json.remove("pfxTrustOptions");
    json.put("pemKeyCertOptions", new JsonObject())
      .put("pemTrustOptions", new JsonObject());
    options = new HttpServerOptions(json);
    assertTrue(options.getTrustOptions() instanceof PemTrustOptions);
    assertTrue(options.getKeyCertOptions() instanceof PemKeyCertOptions);
  }

  @Override
  public void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd() throws Exception {
    testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(0);
  }

  // Extra tests

  @Test
  public void testTimedOutWaiterDoesntConnect() throws Exception {
    long responseDelay = 300;
    int requests = 6;
    client.close();
    CountDownLatch firstCloseLatch = new CountDownLatch(1);
    server.close(onSuccess(v -> firstCloseLatch.countDown()));
    // Make sure server is closed before continuing
    awaitLatch(firstCloseLatch);

    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false).setMaxPoolSize(1));
    AtomicInteger connectCount = new AtomicInteger(0);
    // We need a net server because we need to intercept the socket connection, not just full http requests
    NetServer server = vertx.createNetServer(new NetServerOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT));
    server.connectHandler(socket -> {
      connectCount.incrementAndGet();
      // Delay and write a proper http response
      vertx.setTimer(responseDelay, time -> socket.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK"));
    });

    CountDownLatch latch = new CountDownLatch(requests);

    server.listen(onSuccess(s -> {
      for(int count = 0; count < requests; count++) {
        HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
          resp.bodyHandler(buff -> {
            assertEquals("OK", buff.toString());
            latch.countDown();
          });
        });
        // Odd requests get a timeout less than the responseDelay, since we have a pool size of one and a delay all but
        // the first request should end up in the wait queue, the odd numbered requests should time out so we should get
        // (requests + 1 / 2) connect attempts
        if (count % 2 == 1) {
          req.setTimeout(responseDelay / 2);
          req.exceptionHandler(ex -> {
            latch.countDown();
          });
        }
        req.end();
      }
    }));

    awaitLatch(latch);

    assertEquals("Incorrect number of connect attempts.", (requests + 1) / 2, connectCount.get());
    server.close();
  }

  @Test
  public void testPipeliningOrder() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true).setMaxPoolSize(1));
    int requests = 100;

    AtomicInteger reqCount = new AtomicInteger(0);
    server.requestHandler(req -> {
      int theCount = reqCount.get();
      assertEquals(theCount, Integer.parseInt(req.headers().get("count")));
      reqCount.incrementAndGet();
      req.response().setChunked(true);
      req.bodyHandler(buff -> {
        assertEquals("This is content " + theCount, buff.toString());
        // We write the response back after a random time to increase the chances of responses written in the
        // wrong order if we didn't implement pipelining correctly
        vertx.setTimer(1 + (long) (10 * Math.random()), id -> {
          req.response().headers().set("count", String.valueOf(theCount));
          req.response().write(buff);
          req.response().end();
        });
      });
    });


    CountDownLatch latch = new CountDownLatch(requests);

    server.listen(onSuccess(s -> {
      vertx.setTimer(500, id -> {
        for (int count = 0; count < requests; count++) {
          int theCount = count;
          HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
            assertEquals(theCount, Integer.parseInt(resp.headers().get("count")));
            resp.bodyHandler(buff -> {
              assertEquals("This is content " + theCount, buff.toString());
              latch.countDown();
            });
          });
          req.setChunked(true);
          req.headers().set("count", String.valueOf(count));
          req.write("This is content " + count);
          req.end();
        }
      });

    }));

    awaitLatch(latch);

  }

  @Test
  public void testPipeliningLimit() throws Exception {
    int limit = 25;
    int requests = limit * 4;
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().
        setKeepAlive(true).
        setPipelining(true).
        setPipeliningLimit(limit).
        setMaxPoolSize(1));
    AtomicInteger count = new AtomicInteger();
    String data = "GET /somepath HTTP/1.1\r\n" +
        "Host: localhost:8080\r\n" +
        "\r\n";
    NetServer server = vertx.createNetServer(new NetServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTPS_HOST));
    server.connectHandler(so -> {
      StringBuilder total = new StringBuilder();
      so.handler(buff -> {
        total.append(buff);
        while (total.indexOf(data) == 0) {
          total.delete(0, data.length());
          if (count.incrementAndGet() == limit) {
            vertx.setTimer(100, timerID -> {
              assertEquals(limit, count.get());
              count.set(0);
              for (int i = 0;i < limit;i++) {
                so.write("HTTP/1.1 200 OK\r\nContent-Length : 0\r\n\r\n");
              }
            });
          }
        }
      });
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(onSuccess(v -> {
      listenLatch.countDown();
    }));
    awaitLatch(listenLatch);

    AtomicInteger responses = new AtomicInteger();
    for (int i = 0;i < requests;i++) {
      client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        assertEquals(200, resp.statusCode());
        if (responses.incrementAndGet() == requests) {
          testComplete();
        }
      });
    }
    await();
  }

  @Test
  public void testKeepAlive() throws Exception {
    testKeepAlive(true, 5, 10, 5);
  }

  @Test
  public void testNoKeepAlive() throws Exception {
    testKeepAlive(false, 5, 10, 10);
  }

  private void testKeepAlive(boolean keepAlive, int poolSize, int numServers, int expectedConnectedServers) throws Exception {
    client.close();
    CountDownLatch firstCloseLatch = new CountDownLatch(1);
    server.close(onSuccess(v -> firstCloseLatch.countDown()));
    // Make sure server is closed before continuing
    awaitLatch(firstCloseLatch);

    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(keepAlive).setPipelining(false).setMaxPoolSize(poolSize));
    int requests = 100;

    // Start the servers
    HttpServer[] servers = new HttpServer[numServers];
    CountDownLatch startServerLatch = new CountDownLatch(numServers);
    Set<HttpServer> connectedServers = new ConcurrentHashSet<>();
    for (int i = 0; i < numServers; i++) {
      HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT));
      server.requestHandler(req -> {
        connectedServers.add(server);
        req.response().end();
      });
      server.listen(ar -> {
        assertTrue(ar.succeeded());
        startServerLatch.countDown();
      });
      servers[i] = server;
    }

    awaitLatch(startServerLatch);

    CountDownLatch reqLatch = new CountDownLatch(requests);

    // We make sure we execute all the requests on the same context otherwise some responses can come beack when there
    // are no waiters resulting in it being closed so a a new connection is made for the next request resulting in the
    // number of total connections being > pool size (which is correct)
    vertx.runOnContext(v -> {
      for (int count = 0; count < requests; count++) {
        client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
          assertEquals(200, resp.statusCode());
          reqLatch.countDown();
        }).end();
      }
    });

    awaitLatch(reqLatch);

    //client.dispConnCount();
    assertEquals(expectedConnectedServers, connectedServers.size());

    CountDownLatch serverCloseLatch = new CountDownLatch(numServers);
    for (HttpServer server: servers) {
      server.close(ar -> {
        assertTrue(ar.succeeded());
        serverCloseLatch.countDown();
      });
    }

    awaitLatch(serverCloseLatch);
  }

  @Test
  public void testPoolingKeepAliveAndPipelining() {
    testPooling(true, true);
  }

  @Test
  public void testPoolingKeepAliveNoPipelining() {
    testPooling(true, false);
  }

  @Test
  public void testPoolingNoKeepAliveNoPipelining() {
    testPooling(false, false);
  }

  @Test
  public void testPoolingNoKeepAliveAndPipelining() {
    testPooling(false, true);
  }

  private void testPooling(boolean keepAlive, boolean pipelining) {
    String path = "foo.txt";
    int numGets = 100;
    int maxPoolSize = 10;
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(keepAlive).setPipelining(pipelining).setMaxPoolSize(maxPoolSize));

    server.requestHandler(req -> {
      String cnt = req.headers().get("count");
      req.response().headers().set("count", cnt);
      req.response().end();
    });

    AtomicBoolean completeAlready = new AtomicBoolean();

    server.listen(onSuccess(s -> {

      AtomicInteger cnt = new AtomicInteger(0);
      for (int i = 0; i < numGets; i++) {
        int theCount = i;
        HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, path, resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals(theCount, Integer.parseInt(resp.headers().get("count")));
          if (cnt.incrementAndGet() == numGets) {
            testComplete();
          }
        });
        req.exceptionHandler(t -> {
          if (pipelining && !keepAlive) {
            // Illegal combination - should get exception
            assertTrue(t instanceof IllegalStateException);
            if (completeAlready.compareAndSet(false, true)) {
              testComplete();
            }
          } else {
            fail("Should not throw exception: " + t.getMessage());
          }
        });
        req.headers().set("count", String.valueOf(i));
        req.end();
      }
    }));

    await();
  }

  @Test
  public void testMaxWaitQueueSizeIsRespected() throws Exception {
    client.close();

    client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(DEFAULT_HTTP_HOST).setDefaultPort(DEFAULT_HTTP_PORT)
        .setPipelining(false).setMaxWaitQueueSize(0).setMaxPoolSize(2));

    server.requestHandler(req -> {
      req.response().setStatusCode(200);
      req.response().end("OK");
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req1 = client.get(DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(body -> {
          assertEquals("OK", body.toString());
        });
      });
      req1.exceptionHandler(t -> fail("Should not be called."));

      HttpClientRequest req2 = client.get(DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(body -> {
          assertEquals("OK", body.toString());
          testComplete();
        });
      });
      req2.exceptionHandler(t -> fail("Should not be called."));

      HttpClientRequest req3 = client.get(DEFAULT_TEST_URI, resp -> {
        fail("Should not be called.");
      });
      req3.exceptionHandler(t -> {
        assertTrue("Incorrect exception time.", t instanceof ConnectionPoolTooBusyException);
      });

      req1.end();
      req2.end();
      req3.end();
    }));

    await();
  }

  // Note : cannot pass for http/2 because flushing is not the same : investigate
  @Test
  public void testRequestTimeoutExtendedWhenResponseChunksReceived() {
    long timeout = 2000;
    int numChunks = 100;
    AtomicInteger count = new AtomicInteger(0);
    long interval = timeout * 2 / numChunks;

    server.requestHandler(req -> {
      req.response().setChunked(true);
      vertx.setPeriodic(interval, timerID -> {
        req.response().write("foo");
        if (count.incrementAndGet() == numChunks) {
          req.response().end();
          vertx.cancelTimer(timerID);
        }
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(200, resp.statusCode());
        resp.endHandler(v -> testComplete());
      });
      req.exceptionHandler(t -> fail("Should not be called"));
      req.setTimeout(timeout);
      req.end();
    }));

    await();
  }

  @Test
  public void testServerWebsocketIdleTimeout() {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST));
    server.websocketHandler(ws -> {}).listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", ws -> {
        ws.closeHandler(v -> testComplete());
      });
    });

    await();
  }

  @Test
  public void testClientWebsocketIdleTimeout() {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setIdleTimeout(1));
    server.websocketHandler(ws -> {}).listen(ar -> {
      client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", ws -> {
        ws.closeHandler(v -> testComplete());
      });

    });

    await();
  }

  @Test
  public void testSharedServersRoundRobin() throws Exception {
    client.close();
    server.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false));
    int numServers = 5;
    int numRequests = numServers * 100;

    List<HttpServer> servers = new ArrayList<>();
    Set<HttpServer> connectedServers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    Map<HttpServer, Integer> requestCount = new ConcurrentHashMap<>();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numRequests);
    Set<Context> contexts = new ConcurrentHashSet<>();
    for (int i = 0; i < numServers; i++) {
      HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
      servers.add(theServer);
      final AtomicReference<Context> context = new AtomicReference<>();
      theServer.requestHandler(req -> {
        Context ctx = Vertx.currentContext();
        if (context.get() != null) {
          assertSame(ctx, context.get());
        } else {
          context.set(ctx);
          contexts.add(ctx);
        }
        connectedServers.add(theServer);
        Integer cnt = requestCount.get(theServer);
        int icnt = cnt == null ? 0 : cnt;
        icnt++;
        requestCount.put(theServer, icnt);
        latchConns.countDown();
        req.response().end();
      }).listen(onSuccess(s -> {
          assertEquals(DEFAULT_HTTP_PORT, s.actualPort());
          latchListen.countDown();
      }));
    }
    awaitLatch(latchListen);


    // Create a bunch of connections
    CountDownLatch latchClient = new CountDownLatch(numRequests);
    for (int i = 0; i < numRequests; i++) {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, res -> latchClient.countDown()).end();
    }

    assertTrue(latchClient.await(10, TimeUnit.SECONDS));
    assertTrue(latchConns.await(10, TimeUnit.SECONDS));

    assertEquals(numServers, connectedServers.size());
    for (HttpServer server : servers) {
      assertTrue(connectedServers.contains(server));
    }
    assertEquals(numServers, requestCount.size());
    for (int cnt : requestCount.values()) {
      assertEquals(numRequests / numServers, cnt);
    }
    assertEquals(numServers, contexts.size());

    CountDownLatch closeLatch = new CountDownLatch(numServers);

    for (HttpServer server : servers) {
      server.close(ar -> {
        assertTrue(ar.succeeded());
        closeLatch.countDown();
      });
    }

    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    // Have a server running on a different port to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(8081));
    theServer.requestHandler(req -> {
      fail("Should not process request");
    }).listen(onSuccess(s -> latch.countDown()));
    awaitLatch(latch);

    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host before hand to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    theServer.requestHandler(req -> {
      fail("Should not process request");
    }).listen(onSuccess(s -> latch.countDown()));
    awaitLatch(latch);

    CountDownLatch closeLatch = new CountDownLatch(1);
    theServer.close(ar -> {
      assertTrue(ar.succeeded());
      closeLatch.countDown();
    });
    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));

    testSharedServersRoundRobin();
  }

  @Test
  public void testDefaultHttpVersion() {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> resp.endHandler(v -> testComplete())).end();
    }));

    await();
  }

  @Test
  public void testIncorrectHttpVersion() throws Exception {
    server.requestHandler(req -> {
      NetSocket so = req.netSocket();
      so.write(Buffer.buffer("HTTP/1.2 200 OK\r\n\r\n"));
      so.close();
    });
    startServer();
    HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> fail("Should not be called"));
    req.exceptionHandler(err -> {
      assertTrue("message " + err.getMessage() + " should contain HTTP/1.2", err.getMessage().contains("HTTP/1.2"));
      req.connection().closeHandler(v -> {
        testComplete();
      });
    }).putHeader("connection", "close").end();
    await();
  }

  @Test
  public void testHttp11PersistentConnectionNotClosed() throws Exception {
    client.close();

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      assertNull(req.getHeader("Connection"));
      req.response().end();
      assertFalse(req.response().closed());
    });

    server.listen(onSuccess(s -> {
      client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setKeepAlive(true));
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.endHandler(v -> {
          assertNull(resp.getHeader("Connection"));
          assertEquals(resp.getHeader("Content-Length"), "0");
          testComplete();
        });
      });
      req.end();
    }));

    await();
  }

  @Test
  public void testHttp11NonPersistentConnectionClosed() throws Exception {
    client.close();

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      assertEquals(req.getHeader("Connection"), "close");
      req.response().end();
      assertTrue(req.response().closed());
    });

    server.listen(onSuccess(s -> {
      client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setKeepAlive(false));
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.endHandler(v -> {
          assertEquals(resp.getHeader("Connection"), "close");
          testComplete();
        });
      });
      req.end();
    }));

    await();
  }

  @Test
  public void testHttp10KeepAliveConnectionNotClosed() throws Exception {
    client.close();

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_0, req.version());
      assertEquals(req.getHeader("Connection"), "keep-alive");
      req.response().end();
      assertFalse(req.response().closed());
    });

    server.listen(onSuccess(s -> {
      client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0).setKeepAlive(true));
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.endHandler(v -> {
          assertEquals(resp.getHeader("Connection"), "keep-alive");
          assertEquals(resp.getHeader("Content-Length"), "0");
          testComplete();
        });
      });
      req.end();
    }));

    await();
  }

  @Test
  public void testHttp10NonKeepAliveConnectionClosed() throws Exception {
    client.close();

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_0, req.version());
      assertNull(req.getHeader("Connection"));
      req.response().end();
      assertTrue(req.response().closed());
    });

    server.listen(onSuccess(s -> {
      client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0).setKeepAlive(false));
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.endHandler(v -> {
          assertNull(resp.getHeader("Connection"));
          testComplete();
        });
      });
      req.end();
    }));

    await();
  }

  @Test
  public void requestAbsNoPort() {
    client.requestAbs(HttpMethod.GET, "http://www.google.com", res -> testComplete()).end();
    await();
  }

  @Test
  public void testAccessNetSocket() throws Exception {
    Buffer toSend = TestUtils.randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().headers().set("HTTP/1.1", "101 Upgrade");
      req.bodyHandler(data -> {
        assertEquals(toSend, data);
        req.response().end();
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.endHandler(v -> {
          assertNotNull(resp.netSocket());
          testComplete();
        });
      });
      req.headers().set("content-length", String.valueOf(toSend.length()));
      req.write(toSend);
    }));

    await();
  }

  @Test
  public void testHttpConnect() {
    Buffer buffer = TestUtils.randomBuffer(128);
    Buffer received = Buffer.buffer();
    vertx.createNetServer(new NetServerOptions().setPort(1235)).connectHandler(socket -> {
      socket.handler(socket::write);
    }).listen(onSuccess(netServer -> {
      server.requestHandler(req -> {
        vertx.createNetClient(new NetClientOptions()).connect(netServer.actualPort(), "localhost", onSuccess(socket -> {
          req.response().setStatusCode(200);
          req.response().setStatusMessage("Connection established");
          req.response().end();

          // Create pumps which echo stuff
          Pump.pump(req.netSocket(), socket).start();
          Pump.pump(socket, req.netSocket()).start();
          req.netSocket().closeHandler(v -> socket.close());
        }));
      });
      server.listen(onSuccess(s -> {
        client.request(HttpMethod.CONNECT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
          assertEquals(200, resp.statusCode());
          NetSocket socket = resp.netSocket();
          socket.handler(buff -> {
            received.appendBuffer(buff);
            if (received.length() == buffer.length()) {
              netServer.close();
              assertEquals(buffer, received);
              testComplete();
            }
          });
          socket.write(buffer);
        }).end();
      }));
    }));

    await();
  }

  @Test
  public void testRequestsTimeoutInQueue() {

    server.requestHandler(req -> {
      vertx.setTimer(1000, id -> {
        req.response().end();
      });
    });

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false).setMaxPoolSize(1));

    server.listen(onSuccess(s -> {
      // Add a few requests that should all timeout
      for (int i = 0; i < 5; i++) {
        HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
          fail("Should not be called");
        });
        req.exceptionHandler(t -> assertTrue(t instanceof TimeoutException));
        req.setTimeout(500);
        req.end();
      }
      // Now another request that should not timeout
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      });
      req.exceptionHandler(t -> fail("Should not throw exception"));
      req.setTimeout(3000);
      req.end();
    }));

    await();
  }

  @Test
  public void testServerOptionsCopiedBeforeUse() {
    server.close();
    HttpServerOptions options = new HttpServerOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT);
    HttpServer server = vertx.createHttpServer(options);
    // Now change something - but server should still listen at previous port
    options.setPort(DEFAULT_HTTP_PORT + 1);
    server.requestHandler(req -> {
      req.response().end();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/uri", res -> {
        assertEquals(200, res.statusCode());
        testComplete();
      }).end();
    });
    await();
  }

  @Test
  public void testClientOptionsCopiedBeforeUse() {
    client.close();
    server.requestHandler(req -> {
      req.response().end();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      HttpClientOptions options = new HttpClientOptions();
      client = vertx.createHttpClient(options);
      // Now change something - but server should ignore this
      options.setSsl(true);
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/uri", res -> {
        assertEquals(200, res.statusCode());
        testComplete();
      }).end();
    });
    await();
  }

  @Test
  public void testClientContextWithKeepAlive() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(false).setMaxPoolSize(1));
    testClientContext();
  }

  @Test
  public void testClientContextWithPipelining() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true).setMaxPoolSize(1));
    testClientContext();
  }

  private void testClientContext() throws Exception {
    CountDownLatch serverLatch = new CountDownLatch(1);
    server.requestHandler(req -> {
      req.response().end();
    } ).listen(ar -> {
      assertTrue(ar.succeeded());
      serverLatch.countDown();;
    });
    awaitLatch(serverLatch);
    CountDownLatch req1Latch = new CountDownLatch(1);
    AtomicReference<Context> c = new AtomicReference<>();
    HttpClientRequest req1 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/1");
    AtomicReference<HttpConnection> conn = new AtomicReference<>();
    req1.handler(res -> {
      c.set(Vertx.currentContext());
      conn.set(req1.connection());
      res.endHandler(v -> req1Latch.countDown());
    });
    req1.end();
    Consumer<HttpClientRequest> checker = req -> {
      assertSame(Vertx.currentContext(), c.get());
      assertSame(conn.get(), req.connection());
    };
    awaitLatch(req1Latch);
    CountDownLatch req2Latch = new CountDownLatch(2);
    HttpClientRequest req2 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/2");
    req2.handler(res -> {
      checker.accept(req2);
      req2Latch.countDown();
    }).exceptionHandler(err -> {
      fail(err);
    }).sendHead();
    HttpClientRequest req3 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/3");
    req3.handler(res -> {
      checker.accept(req3);
      req2Latch.countDown();
    }).exceptionHandler(err -> {
      fail(err);
    });
    req2.end();
    req3.end();
    awaitLatch(req2Latch);
    vertx.getOrCreateContext().runOnContext(v -> {
      HttpClientRequest req4 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/4");
      req4.handler(res -> {
        // This should warn in the log (console) as we are called back on the connection context
        // and not on the context doing the request
        checker.accept(req4);
        testComplete();
      });
      req4.exceptionHandler(err -> {
        fail(err);
      });
      req4.end();
    });
    await();
  }

  @Test
  public void testContexts() throws Exception {
    Set<ContextImpl> contexts = new ConcurrentHashSet<>();
    AtomicInteger cnt = new AtomicInteger();
    AtomicReference<ContextImpl> serverRequestContext = new AtomicReference<>();
    // Server connect handler should always be called with same context
    server.requestHandler(req -> {
      ContextImpl serverContext = ((VertxInternal) vertx).getContext();
      if (serverRequestContext.get() != null) {
        assertSame(serverRequestContext.get(), serverContext);
      } else {
        serverRequestContext.set(serverContext);
      }
      req.response().end();
    });
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<ContextImpl> listenContext = new AtomicReference<>();
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      listenContext.set(((VertxInternal) vertx).getContext());
      latch.countDown();
    });
    awaitLatch(latch);
    CountDownLatch latch2 = new CountDownLatch(1);
    int numReqs = 16;
    int numConns = 8;
    // There should be a context per *connection*
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(numConns));
    for (int i = 0; i < numReqs; i++) {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", resp -> {
        assertEquals(200, resp.statusCode());
        contexts.add(((VertxInternal) vertx).getContext());
        if (cnt.incrementAndGet() == numReqs) {
          // Some connections might get closed if response comes back quick enough hence the >=
          assertTrue(contexts.size() >= numConns);
          latch2.countDown();
        }
      }).exceptionHandler(this::fail).end();
    }
    awaitLatch(latch2);
    // Close should be in own context
    server.close(ar -> {
      assertTrue(ar.succeeded());
      ContextImpl closeContext = ((VertxInternal) vertx).getContext();
      assertFalse(contexts.contains(closeContext));
      assertNotSame(serverRequestContext.get(), closeContext);
      assertFalse(contexts.contains(listenContext.get()));
      assertSame(serverRequestContext.get(), listenContext.get());
      testComplete();
    });

    server = null;
    await();
  }

  @Test
  public void testRequestHandlerNotCalledInvalidRequest() {
    server.requestHandler(req -> {
      fail();
    });
    server.listen(onSuccess(s -> {
      vertx.createNetClient(new NetClientOptions()).connect(8080, "127.0.0.1", onSuccess(socket -> {
        socket.closeHandler(r -> {
          testComplete();
        });
        socket.write("GET HTTP1/1\r\n");

        // trigger another write to be sure we detect that the other peer has closed the connection.
        socket.write("X-Header: test\r\n");
      }));
    }));
    await();
  }

  @Test
  public void testTwoServersSameAddressDifferentContext() throws Exception {
    vertx.deployVerticle(SimpleServer.class.getName(), new DeploymentOptions().setInstances(2), onSuccess(id -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testInWorker() throws Exception {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        assertTrue(Vertx.currentContext().isWorkerContext());
        assertTrue(Context.isOnWorkerThread());
        HttpServer server1 = vertx.createHttpServer(new HttpServerOptions()
                .setHost(HttpTestBase.DEFAULT_HTTP_HOST).setPort(HttpTestBase.DEFAULT_HTTP_PORT));
        server1.requestHandler(req -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          Buffer buf = Buffer.buffer();
          req.handler(buf::appendBuffer);
          req.endHandler(v -> {
            assertEquals("hello", buf.toString());
            req.response().end("bye");
          });
        }).listen(onSuccess(s -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          HttpClient client = vertx.createHttpClient();
          client.put(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/blah", resp -> {
            assertEquals(200, resp.statusCode());
            assertTrue(Vertx.currentContext().isWorkerContext());
            assertTrue(Context.isOnWorkerThread());
            resp.handler(buf -> {
              assertEquals("bye", buf.toString());
              resp.endHandler(v -> {
                testComplete();
              });
            });
          }).setChunked(true).write(Buffer.buffer("hello")).end();
        }));
      }
    }, new DeploymentOptions().setWorker(true));
    await();
  }

  /*
  Fix for https://bugs.eclipse.org/bugs/show_bug.cgi?id=475017
  Also see https://groups.google.com/forum/?fromgroups#!topic/vertx/N_wSoQlvMMs
   */
  @Test
  public void testPauseResumeClientResponse() {
    byte[] data = new byte[64 * 1024 * 1024];
    new Random().nextBytes(data);
    Buffer buffer = Buffer.buffer(data);
    Buffer readBuffer = Buffer.buffer(64 * 1024 * 1024);
    server.requestHandler(request -> {
      request.response().setChunked(true);
      for (int i = 0; i < buffer.length() / 8192; i++) {
        request.response().write(buffer.slice(i * 8192, (i + 1) * 8192));
      }
      request.response().end();
    });
    server.listen(10000, onSuccess(hs -> {
      HttpClient httpClient = vertx.createHttpClient();
      HttpClientRequest clientRequest = httpClient.get(10000, "localhost", "/");
      clientRequest.handler(resp -> {
        resp.handler(b -> {
          readBuffer.appendBuffer(b);
          for (int i = 0; i < 64; i++) {
            vertx.setTimer(1, n -> {
              try {
                Thread.sleep(0);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            });
          }
          ;
          resp.endHandler(v -> {
            byte[] expectedData = buffer.getBytes();
            byte[] actualData = readBuffer.getBytes();
            assertTrue(Arrays.equals(expectedData, actualData));
            testComplete();
          });
        });
      });
      clientRequest.end();
    }));
    await();
  }

  @Test
  public void testMultipleRecursiveCallsAndPipelining() throws Exception {
    int sendRequests = 100;
    AtomicInteger receivedRequests = new AtomicInteger();
    server.requestHandler(x -> {
        x.response().end("hello");
      })
      .listen(8080, r -> {
        if (r.succeeded()) {
          HttpClient client = vertx.createHttpClient(new HttpClientOptions()
              .setKeepAlive(true)
              .setPipelining(true)
              .setDefaultPort(8080)
          );
          IntStream.range(0, 5).forEach(i -> recursiveCall(client, receivedRequests, sendRequests));
        }
      });
    await();
  }

  private void recursiveCall(HttpClient client, AtomicInteger receivedRequests, int sendRequests){
    client.getNow("/", r -> {
      int numRequests = receivedRequests.incrementAndGet();
      if (numRequests == sendRequests) {
        testComplete();
      } else if (numRequests < sendRequests) {
        recursiveCall(client, receivedRequests, sendRequests);
      }
    });
  }

  @Test
  public void testUnsupportedHttpVersion() throws Exception {
    testUnsupported("GET /someuri HTTP/1.7\r\nHost: localhost\r\n\r\n", false);
  }

  private void testUnsupported(String rawReq, boolean method) throws Exception {
    server
      .requestHandler(req -> {
        try {
          if (method) {
            req.method();
          } else {
            req.version();
          }
          fail("Should throw exception");
        } catch (IllegalStateException e) {
          // OK
        }
      })
      .listen(8080, r -> {
        if (r.succeeded()) {
          NetClient client = vertx.createNetClient();
          // Send a raw request
          client.connect(8080, "localhost", onSuccess(conn -> {
            conn.write(rawReq);
            Buffer respBuff = Buffer.buffer();
            conn.handler(respBuff::appendBuffer);
            conn.closeHandler(v -> {
              // Server should automatically close it after sending back 501
              assertTrue(respBuff.toString().contains("501 Not Implemented"));
              client.close();
              testComplete();
            });
          }));
        }
      });
    await();
  }

  @Test
  public void testTwoServersDifferentEventLoopsCloseOne() throws Exception {
    CountDownLatch latch1 = new CountDownLatch(2);
    AtomicInteger server1Count = new AtomicInteger();
    AtomicInteger server2Count = new AtomicInteger();
    server.requestHandler(req -> {
      server1Count.incrementAndGet();
      req.response().end();
    }).listen(8080, onSuccess(s -> {
      latch1.countDown();
    }));
    HttpServer server2 = vertx.createHttpServer().requestHandler(req -> {
      server2Count.incrementAndGet();
      req.response().end();
    }).listen(8080, onSuccess(s -> {
      latch1.countDown();
    }));
    awaitLatch(latch1);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false).setDefaultPort(8080));

    for (int i = 0; i < 2; i++) {
      CountDownLatch latch2 = new CountDownLatch(1);
      client.getNow("/", resp -> {
        assertEquals(200, resp.statusCode());
        latch2.countDown();
      });
      awaitLatch(latch2);
    }

    // Now close server 2
    CountDownLatch latch3 = new CountDownLatch(1);
    server2.close(onSuccess(v -> {
      latch3.countDown();
    }));
    awaitLatch(latch3);
    // Send some more requests
    for (int i = 0; i < 2; i++) {
      CountDownLatch latch2 = new CountDownLatch(1);
      client.getNow("/", resp -> {
        assertEquals(200, resp.statusCode());
        latch2.countDown();
      });
      awaitLatch(latch2);
    }

    assertEquals(3, server1Count.get());
    assertEquals(1, server2Count.get());
  }


  @Test
  public void testSetWriteQueueMaxSize() throws Exception {

    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setWriteQueueMaxSize(256 * 1024);
      // Now something bigger
      resp.setWriteQueueMaxSize(512 * 1024);
      // And something smaller again
      resp.setWriteQueueMaxSize(128 * 1024);
      resp.setWriteQueueMaxSize(129 * 1024);
      resp.end();
    }).listen(8080, onSuccess(s -> {
      client.getNow(8080, "localhost", "/", resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testServerMaxInitialLineLength() {
    testServerMaxInitialLineLength(HttpServerOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH);
  }

  @Test
  public void testServerMaxInitialLineLengthOption() {
    // 5017 = 5000 for longParam and 17 for the rest in the following line - "GET /?t=longParam HTTP/1.1"
    testServerMaxInitialLineLength(5017);
  }

  private void testServerMaxInitialLineLength(int maxInitialLength) {
    String longParam = TestUtils.randomAlphaString(5000);
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setMaxInitialLineLength(maxInitialLength)
        .setHost("localhost").setPort(8080)).requestHandler(req -> {
      assertEquals(req.getParam("t"), longParam);
      req.response().end();
    }).listen(onSuccess(res -> {
      HttpClientRequest req = vertx.createHttpClient(new HttpClientOptions())
          .request(HttpMethod.GET, 8080, "localhost", "/?t=" + longParam);
      req.handler(resp -> {
        if (maxInitialLength > HttpServerOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH) {
          assertEquals(200, resp.statusCode());
          testComplete();
        } else {
          assertEquals(414, resp.statusCode());
          req.connection().closeHandler(v -> {
            testComplete();
          });
        }
      });
      req.end();
    }));
    await();
  }

  @Test
  public void testClientMaxInitialLineLengthOption() {

    String longParam = TestUtils.randomAlphaString(5000);
    NetServer server = vertx.createNetServer();

    server.connectHandler(so -> {
      so.write("" +
          "HTTP/1.1 200 OK\r\n" +
          "Transfer-Encoding: chunked\r\n" +
          "\r\n" +
          "A; name=\"" + longParam + "\"\r\n" +
          "0123456789\r\n" +
          "0\r\n" +
          "\r\n");
    });

    // 5017 = 5000 for longParam and 17 for the rest in the following line - "GET /?t=longParam HTTP/1.1"
    try {
      server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTPS_HOST, onSuccess(v -> {
        vertx.createHttpClient(new HttpClientOptions().setMaxInitialLineLength(6000))
            .request(HttpMethod.GET, 8080, "localhost", "/?t=" + longParam, resp -> {
              resp.bodyHandler(body -> {
                assertEquals("0123456789", body.toString());
                testComplete();
              });
            }).end();
      }));
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testClientMaxHeaderSizeOption() {

    String longHeader = TestUtils.randomAlphaString(9000);

    // min 9023 = 9000 for longHeader and 23 for "Content-Length: 0 t: "
    vertx.createHttpServer(new HttpServerOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      // Add longHeader
      req.response().putHeader("t", longHeader).end();
    }).listen(onSuccess(res -> {
      HttpClientRequest req = vertx.createHttpClient(new HttpClientOptions().setMaxHeaderSize(10000))
          .request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", resp -> {
            assertEquals(200, resp.statusCode());
            assertEquals(resp.getHeader("t"), longHeader);
            testComplete();
          });
      req.end();
    }));

    await();
  }

  @Test
  public void testServerMaxHeaderSize() {
    testServerMaxHeaderSize(HttpServerOptions.DEFAULT_MAX_HEADER_SIZE);
  }

  @Test
  public void testServerMaxHeaderSizeOption() {
    // min 9023 = 9000 for longHeader and 23 for "Content-Length: 0 t: "
    testServerMaxHeaderSize(10000);
  }

  private void testServerMaxHeaderSize(int maxHeaderSize) {

    String longHeader = TestUtils.randomAlphaString(9000);

    vertx.createHttpServer(new HttpServerOptions().setMaxHeaderSize(maxHeaderSize)
        .setHost("localhost").setPort(8080)).requestHandler(req -> {
      assertEquals(req.getHeader("t"), longHeader);
      req.response().end();
    }).listen(onSuccess(res -> {
      HttpClientRequest req = vertx.createHttpClient(new HttpClientOptions())
          .request(HttpMethod.GET, 8080, "localhost", "/");
      req.handler(resp -> {
        if (maxHeaderSize > HttpServerOptions.DEFAULT_MAX_HEADER_SIZE) {
          assertEquals(200, resp.statusCode());
          testComplete();
        } else {
          assertEquals(400, resp.statusCode());
          req.connection().closeHandler(v -> {
            testComplete();
          });
        }
      });
      // Add longHeader
      req.putHeader("t", longHeader);
      req.end();
    }));

    await();
  }

  @Test
  public void testInvalidHttpResponse() {

    waitFor(2);

    AtomicInteger count = new AtomicInteger(0);
    CompletableFuture<Void> sendResp = new CompletableFuture<>();
    NetServer server  = vertx.createNetServer();
    String match = "GET /somepath HTTP/1.1\r\nHost: localhost:8080\r\n\r\n";
    server.connectHandler(so -> {
      StringBuilder content = new StringBuilder();
      so.handler(buff -> {
        content.append(buff);
        while (content.toString().startsWith(match)) {
          content.delete(0, match.length());
          switch (count.getAndIncrement()) {
            case 0:
              sendResp.thenAccept(v -> {
              });
              break;
            case 1:
              // Send an invalid response
              Buffer resp1 = Buffer.buffer(TestUtils.randomAlphaString(40) + "\r\n");
              // Send a valid response even though it will be ignored by Netty decoder
              Buffer resp2 = Buffer.buffer("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
              // Send at once
              so.write(Buffer.buffer().appendBuffer(resp1).appendBuffer(resp2));
              break;
            default:
              fail();
              break;
          }
        }
      });
    }).listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(s -> {

      // We force two pipelined requests to check that the second request does not get stuck after the first failing
      client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true).setMaxPoolSize(1));

      AtomicBoolean fail1 = new AtomicBoolean();
      HttpClientRequest req1 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        fail();
      }).exceptionHandler(err -> {
        if (fail1.compareAndSet(false, true)) {
          assertEquals(IllegalArgumentException.class, err.getClass()); // invalid version format
          complete();
        }
      });

      AtomicBoolean fail2 = new AtomicBoolean();
      HttpClientRequest req2 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        resp.bodyHandler(buff -> {
          assertEquals("okusa", buff.toString());
          testComplete();
        });
      }).exceptionHandler(err -> {
        if (fail2.compareAndSet(false, true)) {
          assertEquals(VertxException.class, err.getClass()); // Closed
          complete();
        }
      });

      req1.end();
      req2.end();
    }));

    await();
  }

  @Test
  public void testHandleInvalid204Response() throws Exception {
    int numReq = 3;
    waitFor(3);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true).setMaxPoolSize(1));
    List<HttpServerRequest> received = new ArrayList<>();
    server.requestHandler(r -> {
      // Generate an invalid response for the pipe-lined
      r.response().setChunked(true).setStatusCode(204).end();
    }).listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v1 -> {
      for (int i = 0;i < numReq;i++) {
        HttpClientRequest post = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
        post.handler(r -> {
          r.endHandler(v2 -> {
            complete();
          });
        }).exceptionHandler(err -> {
          complete();
        }).end();
      }
    }));
    await();
  }

  @Test
  public void testConnectionCloseHttp_1_0_NoClose() throws Exception {
    testConnectionClose(req -> {
      req.putHeader("Connection", "close");
      req.end();
    }, socket -> {
      AtomicBoolean firstRequest = new AtomicBoolean(true);
      socket.handler(RecordParser.newDelimited("\r\n\r\n", buffer -> {
        if (firstRequest.getAndSet(false)) {
          socket.write("HTTP/1.0 200 OK\n" + "Content-Type: text/plain\n" + "Content-Length: 4\n"
              + "Connection: keep-alive\n" + "\n" + "xxx\n");
        } else {
          socket.write("HTTP/1.0 200 OK\n" + "Content-Type: text/plain\n" + "Content-Length: 1\n"
              + "\n" + "\n");
        }
      }));
    });
  }

  @Test
  public void testConnectionCloseHttp_1_0_Close() throws Exception {
    testConnectionClose(req -> {
      req.putHeader("Connection", "close");
      req.end();
    }, socket -> {
      AtomicBoolean firstRequest = new AtomicBoolean(true);
      socket.handler(RecordParser.newDelimited("\r\n\r\n", buffer -> {
        if (firstRequest.getAndSet(false)) {
          socket.write("HTTP/1.0 200 OK\n" + "Content-Type: text/plain\n" + "Content-Length: 4\n"
              + "Connection: keep-alive\n" + "\n" + "xxx\n");
        } else {
          socket.write("HTTP/1.0 200 OK\n" + "Content-Type: text/plain\n" + "Content-Length: 1\n"
              + "\n" + "\n");
          socket.close();
        }
      }));
    });
  }

  @Test
  public void testConnectionCloseHttp_1_1_NoClose() throws Exception {
    testConnectionClose(HttpClientRequest::end, socket -> {
      AtomicBoolean firstRequest = new AtomicBoolean(true);
      socket.handler(RecordParser.newDelimited("\r\n\r\n", buffer -> {
        if (firstRequest.getAndSet(false)) {
          socket.write("HTTP/1.1 200 OK\n" + "Content-Type: text/plain\n" + "Content-Length: 4\n"
              + "\n" + "xxx\n");
        } else {
          socket.write("HTTP/1.1 200 OK\n" + "Content-Type: text/plain\n" + "Content-Length: 1\n"
              + "Connection: close\n" + "\n" + "\n");
        }
      }));
    });
  }

  @Test
  public void testConnectionCloseHttp_1_1_Close() throws Exception {
    testConnectionClose(HttpClientRequest::end, socket -> {
      AtomicBoolean firstRequest = new AtomicBoolean(true);
      socket.handler(RecordParser.newDelimited("\r\n\r\n", buffer -> {
        if (firstRequest.getAndSet(false)) {
          socket.write("HTTP/1.1 200 OK\n" + "Content-Type: text/plain\n" + "Content-Length: 4\n"
              + "\n" + "xxx\n");
        } else {
          socket.write("HTTP/1.1 200 OK\n" + "Content-Type: text/plain\n" + "Content-Length: 1\n"
              + "Connection: close\n" + "\n" + "\n");
          socket.close();
        }
      }));
    });
  }

  private void testConnectionClose(
      Handler<HttpClientRequest> clientRequest,
      Handler<NetSocket> connectHandler
  ) throws Exception {

    client.close();
    server.close();

    NetServerOptions serverOptions = new NetServerOptions();

    CountDownLatch serverLatch = new CountDownLatch(1);
    vertx.createNetServer(serverOptions).connectHandler(connectHandler).listen(8080, result -> {
      if (result.succeeded()) {
        serverLatch.countDown();
      } else {
        fail();
      }
    });

    awaitLatch(serverLatch);

    HttpClientOptions clientOptions = new HttpClientOptions()
        .setDefaultHost("localhost")
        .setDefaultPort(8080)
        .setKeepAlive(true)
        .setPipelining(false);
    client = vertx.createHttpClient(clientOptions);

    int requests = 11;
    AtomicInteger count = new AtomicInteger(requests);

    for (int i = 0; i < requests; i++) {
      HttpClientRequest req = client.get("/", resp -> {
        resp.bodyHandler(buffer -> {
        });
        resp.endHandler(v -> {
          if (count.decrementAndGet() == 0) {
            complete();
          }
        });
        resp.exceptionHandler(th -> {
          fail();
        });
      }).exceptionHandler(th -> {
        fail();
      });
      clientRequest.handle(req);
    }

    await();
  }

  @Test
  public void testDontReuseConnectionWhenResponseEndsDuringAnOngoingRequest() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(true).setKeepAlive(true));
    server.requestHandler(req -> {
      req.response().end();
    });
    CountDownLatch serverLatch = new CountDownLatch(1);
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      serverLatch.countDown();
    });

    awaitLatch(serverLatch);
    HttpClientRequest req1 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/");
    req1.handler(resp -> {
      resp.endHandler(v1 -> {
        // End request after the response ended
        vertx.setTimer(100, v2 -> {
          req1.end();
        });
      });
    });
    // Send head to the server and trigger the request handler
    req1.sendHead();

    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", resp -> {
      testComplete();
    }).end();

    await();
  }

  @Test
  public void testRecyclePipelinedConnection() throws Exception {
    CountDownLatch listenLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    List<String> responses = new ArrayList<>();
    server.requestHandler(req-> {
      responses.add(req.path());
      req.response().end();
      doneLatch.countDown();
    });
    server.listen(onSuccess(s -> {
      listenLatch.countDown();
    }));
    awaitLatch(listenLatch);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(true).setKeepAlive(true));
    CountDownLatch respLatch = new CountDownLatch(2);
    HttpClientRequestImpl req = (HttpClientRequestImpl) client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/first", resp -> {
      fail();
    });
    req.handleException(new Throwable()); // Simulate the connection timed out
    req.end(); // When connected, the connection should be recycled
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/second", resp -> {
      assertEquals(200, resp.statusCode());
      resp.endHandler(v -> {
        respLatch.countDown();
      });
    }).exceptionHandler(this::fail).end();
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/third", resp -> {
      assertEquals(200, resp.statusCode());
      resp.endHandler(v -> {
        respLatch.countDown();
      });
    }).exceptionHandler(this::fail).end();
    awaitLatch(doneLatch);
    assertEquals(Arrays.asList("/second", "/third"), responses);
    awaitLatch(respLatch);
    server.close();
  }

  @Test
  public void testClientConnectionExceptionHandler() throws Exception {
    server.requestHandler(req -> {
      NetSocket so = req.netSocket();
      so.write(Buffer.buffer(TestUtils.randomAlphaString(40) + "\r\n"));
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(onSuccess(s -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    HttpClientRequest req = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
    });
    req.connectionHandler(conn -> {
      conn.exceptionHandler(err -> {
        testComplete();
      });
    });
    req.sendHead();
    await();
  }

  @Test
  public void testServerConnectionExceptionHandler() throws Exception {
    server.connectionHandler(conn -> {
      conn.exceptionHandler(err -> {
        assertTrue(err instanceof TooLongFrameException);
        testComplete();
      });
    });
    server.requestHandler(req -> {
      fail();
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(onSuccess(s -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    HttpClientRequest req = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
    });
    req.putHeader("the_header", TestUtils.randomAlphaString(10000));
    req.sendHead();
    await();
  }

  @Test
  public void testHttpProxyRequest() throws Exception {
    startProxy(null, ProxyType.HTTP);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setHost("localhost").setPort(proxy.getPort())));
    testHttpProxyRequest2(client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/"));
  }

  @Test
  public void testHttpProxyRequestOverrideClientSsl() throws Exception {
    startProxy(null, ProxyType.HTTP);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setSsl(true).setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setHost("localhost").setPort(proxy.getPort())));
    testHttpProxyRequest2(client.get(new RequestOptions().setSsl(false).setHost("localhost").setPort(8080)));
  }

  private void testHttpProxyRequest2(HttpClientRequest clientReq) throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      clientReq.handler(resp -> {
        assertEquals(200, resp.statusCode());
        assertNotNull("request did not go through proxy", proxy.getLastUri());
        assertEquals("Host header doesn't contain target host", "localhost:8080", proxy.getLastRequestHeaders().get("Host"));
        testComplete();
      });
      clientReq.exceptionHandler(this::fail);
      clientReq.end();
    }));
    await();
  }

  @Test
  public void testHttpProxyRequestAuth() throws Exception {
    startProxy("user", ProxyType.HTTP);

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setHost("localhost").setPort(proxy.getPort())
            .setUsername("user").setPassword("user")));

    server.requestHandler(req -> {
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", resp -> {
        assertEquals(200, resp.statusCode());
        assertNotNull("request did not go through proxy", proxy.getLastUri());
        assertEquals("Host header doesn't contain target host", "localhost:8080", proxy.getLastRequestHeaders().get("Host"));
        testComplete();
      }).exceptionHandler(th -> fail(th)).end();
    }));
    await();
  }

  @Test
  public void testHttpProxyFtpRequest() throws Exception {
    startProxy(null, ProxyType.HTTP);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setHost("localhost").setPort(proxy.getPort())));
    final String url = "ftp://ftp.gnu.org/gnu/";
    proxy.setForceUri("http://localhost:8080/");
    HttpClientRequest clientReq = client.getAbs(url);
    server.requestHandler(req -> {
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      clientReq.handler(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("request did sent the expected url", url, proxy.getLastUri());
        testComplete();
      });
      clientReq.exceptionHandler(this::fail);
      clientReq.end();
    }));
    await();
  }

  @Test
  public void testHttpSocksProxyRequest() throws Exception {
    startProxy(null, ProxyType.SOCKS5);

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("localhost").setPort(proxy.getPort())));

    server.requestHandler(req -> {
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", resp -> {
        assertEquals(200, resp.statusCode());
        assertNotNull("request did not go through proxy", proxy.getLastUri());
        testComplete();
      }).exceptionHandler(th -> fail(th)).end();
    }));
    await();
  }

  @Test
  public void testHttpSocksProxyRequestAuth() throws Exception {
    startProxy("user", ProxyType.SOCKS5);

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost("localhost").setPort(proxy.getPort())
            .setUsername("user").setPassword("user")));

    server.requestHandler(req -> {
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", resp -> {
        assertEquals(200, resp.statusCode());
        assertNotNull("request did not go through proxy", proxy.getLastUri());
        testComplete();
      }).exceptionHandler(th -> fail(th)).end();
    }));
    await();
  }

  @Test
  public void testRandomPorts() throws Exception {
    int numServers = 10;
    Set<Integer> ports = new HashSet<>();
    AtomicInteger count = new AtomicInteger();
    for (int i = 0;i < numServers;i++) {
      vertx.createHttpServer().requestHandler(req -> {
        req.response().end();
      }).listen(0, DEFAULT_HTTP_HOST, onSuccess(s -> {
        int port = s.actualPort();
        ports.add(port);
        client.getNow(port, DEFAULT_HTTP_HOST, "/somepath", resp -> {
          if (count.incrementAndGet() == numServers) {
            assertEquals(numServers, ports.size());
            testComplete();
          }
        });
      }));
    }
    await();
  }

  @Test
  public void testContentDecompression() throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setDecompressionSupported(true));
    String expected = TestUtils.randomAlphaString(1000);
    byte[] dataGzipped = TestUtils.compressGzip(expected);
    server.requestHandler(req -> {
      assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get("host"));
      req.bodyHandler(buffer -> {
        assertEquals(expected, buffer.toString());
        req.response().end();
      });
    });

    server.listen(onSuccess(server -> {
      client
      .request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "some-uri", resp -> testComplete())
      .putHeader("Content-Encoding", "gzip")
      .end(Buffer.buffer(dataGzipped));
    }));

    await();
  }

  @Test
  public void testResetClientRequestNotYetSent() throws Exception {
    testResetClientRequestNotYetSent(false, false);
  }

  @Test
  public void testResetKeepAliveClientRequestNotYetSent() throws Exception {
    testResetClientRequestNotYetSent(true, false);
  }

  @Test
  public void testResetPipelinedClientRequestNotYetSent() throws Exception {
    testResetClientRequestNotYetSent(true, true);
  }

  private void testResetClientRequestNotYetSent(boolean keepAlive, boolean pipelined) throws Exception {
    waitFor(2);
    server.close();
    NetServer server = vertx.createNetServer();
    try {
      AtomicInteger numReq = new AtomicInteger();
      server.connectHandler(conn -> {
        assertEquals(0, numReq.getAndIncrement());
        StringBuilder sb = new StringBuilder();
        conn.handler(buff -> {
          sb.append(buff);
          String content = sb.toString();
          if (content.startsWith("GET some-uri HTTP/1.1\r\n") && content.endsWith("\r\n\r\n")) {
            conn.write("HTTP/1.1 200 OK\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n");
            complete();
          }
        });
      });
      CountDownLatch latch = new CountDownLatch(1);
      server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v -> {
        latch.countDown();
      }));
      awaitLatch(latch);
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setKeepAlive(keepAlive).setPipelining(pipelined));
      HttpClientRequest post = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        fail();
      });
      post.setChunked(true).write(TestUtils.randomBuffer(1024));
      assertTrue(post.reset());
      client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(1, numReq.get());
        complete();
      });
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testResetKeepAliveClientRequest() throws Exception {
    waitFor(2);
    server.close();
    NetServer server = vertx.createNetServer();
    try {
      AtomicInteger count = new AtomicInteger();
      server.connectHandler(so -> {
        assertEquals(0, count.getAndIncrement());
        Buffer total = Buffer.buffer();
        so.handler(buff -> {
          total.appendBuffer(buff);
          if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
              "Host: localhost:8080\r\n" +
              "\r\n")) {
            so.write(
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: 11\r\n" +
                    "\r\n" +
                    "Hello world");
            so.closeHandler(v -> {
              complete();
            });
          }
        });
      });
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(false).setKeepAlive(true));
      AtomicInteger status = new AtomicInteger();
      HttpClientRequest req1 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        assertEquals(0, status.getAndIncrement());
      });
      req1.connectionHandler(conn -> {
        conn.closeHandler(v -> {
          assertEquals(1, status.getAndIncrement());
          complete();
        });
      });
      req1.end();
      HttpClientRequest req2 = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        fail();
      });
      req2.sendHead(v -> {
        assertTrue(req2.reset());
      });
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testResetPipelinedClientRequest() throws Exception {
    waitFor(2);
    CompletableFuture<Void> reset = new CompletableFuture<>();
    server.close();
    NetServer server = vertx.createNetServer();
    AtomicInteger count = new AtomicInteger();
    server.connectHandler(so -> {
      assertEquals(0, count.getAndIncrement());
      Buffer total = Buffer.buffer();
      so.handler(buff -> {
        total.appendBuffer(buff);
        if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
            "Host: localhost:8080\r\n" +
            "\r\n" +
            "POST /somepath HTTP/1.1\r\n" +
            "Host: localhost:8080\r\n" +
            "\r\n")) {
          reset.complete(null);
          so.write(
              "HTTP/1.1 200 OK\r\n" +
              "Content-Type: text/plain\r\n" +
              "Content-Length: 11\r\n" +
              "\r\n" +
              "Hello world");
        }
      });
      so.closeHandler(v -> {
        complete();
      });
    });
    try {
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(true).setKeepAlive(true));
      AtomicInteger status = new AtomicInteger();
      HttpClientRequest req1 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        assertEquals(0, status.getAndIncrement());
      });
      req1.connectionHandler(conn -> {
        conn.closeHandler(v -> {
          assertEquals(1, status.getAndIncrement());
          complete();
        });
      });
      req1.end();
      HttpClientRequest req2 = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        fail();
      });
      req2.sendHead();
      reset.thenAccept(v -> {
        assertTrue(req2.reset());
      });
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testCloseTheConnectionAfterResetKeepAliveClientRequest() throws Exception {
    testCloseTheConnectionAfterResetPersistentClientRequest(false);
  }

  @Test
  public void testCloseTheConnectionAfterResetPipelinedClientRequest() throws Exception {
    testCloseTheConnectionAfterResetPersistentClientRequest(true);
  }

  private void testCloseTheConnectionAfterResetPersistentClientRequest(boolean pipelined) throws Exception {
    waitFor(2);
    server.close();
    NetServer server = vertx.createNetServer();
    try {
      AtomicInteger count = new AtomicInteger();
      AtomicBoolean closed = new AtomicBoolean();
      server.connectHandler(so -> {
        Buffer total = Buffer.buffer();
        switch (count.getAndIncrement()) {
          case 0:
            so.handler(buff -> {
              total.appendBuffer(buff);
              if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
                  "Host: localhost:8080\r\n" +
                  "\r\n")) {
                so.closeHandler(v -> {
                  closed.set(true);
                });
              }
            });
            break;
          case 1:
            so.handler(buff -> {
              total.appendBuffer(buff);
              if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
                  "Host: localhost:8080\r\n" +
                  "\r\n")) {
                so.write(
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: 11\r\n" +
                        "\r\n" +
                        "Hello world");
                complete();
              }
            });
            break;
          default:
            fail("Invalid count");
            break;

        }
      });
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(pipelined).setKeepAlive(true));
      HttpClientRequest req1 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        fail();
      });
      if (pipelined) {
        req1.sendHead(v -> {
          assertTrue(req1.reset());
          client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
            assertEquals(200, resp.statusCode());
            resp.bodyHandler(body -> {
              assertEquals("Hello world", body.toString());
              complete();
            });
          });
        });
      } else {
        req1.sendHead(v -> {
          assertTrue(req1.reset());
        });
        client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
          assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            assertEquals("Hello world", body.toString());
            complete();
          });
        });
      }
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testCloseTheConnectionAfterResetKeepAliveClientResponse() throws Exception {
    testCloseTheConnectionAfterResetPersistentClientResponse(false);
  }

  @Test
  public void testCloseTheConnectionAfterResetPipelinedClientResponse() throws Exception {
    testCloseTheConnectionAfterResetPersistentClientResponse(true);
  }

  private void testCloseTheConnectionAfterResetPersistentClientResponse(boolean pipelined) throws Exception {
    waitFor(2);
    server.close();
    NetServer server = vertx.createNetServer();
    try {
      AtomicInteger count = new AtomicInteger();
      AtomicBoolean closed = new AtomicBoolean();
      server.connectHandler(so -> {
        Buffer total = Buffer.buffer();
        switch (count.getAndIncrement()) {
          case 0:
            so.handler(buff -> {
              total.appendBuffer(buff);
              if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
                  "Host: localhost:8080\r\n" +
                  "\r\n")) {
                so.write(Buffer.buffer(
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Length: 200\r\n" +
                    "\r\n" +
                    "Some-Buffer"
                ));
                so.closeHandler(v -> {
                  closed.set(true);
                });
              }
            });
            break;
          case 1:
            so.handler(buff -> {
              total.appendBuffer(buff);
              if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
                  "Host: localhost:8080\r\n" +
                  "\r\n")) {
                so.write(
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: 11\r\n" +
                        "\r\n" +
                        "Hello world");
                complete();
              }
            });
            break;
          default:
            fail("Invalid count");
            break;

        }
      });
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(pipelined).setKeepAlive(true));
      HttpClientRequest req1 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
      if (pipelined) {
        req1.handler(resp1 -> {
          resp1.handler(buff -> {
            req1.reset();
            client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
              assertEquals(200, resp.statusCode());
              resp.bodyHandler(body -> {
                assertEquals("Hello world", body.toString());
                complete();
              });
            });
          });
        });
        req1.end();
      } else {
        req1.handler(resp -> {
          resp.handler(buff -> {
            req1.reset();
          });
        });
        req1.end();
        client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
          assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            assertEquals("Hello world", body.toString());
            complete();
          });
        });
      }
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testCloseTheConnectionAfterResetBeforePipelinedResponseReceived() throws Exception {
    testCloseTheConnectionAfterResetBeforeResponseReceived(true);
  }

  @Test
  public void testCloseTheConnectionAfterResetBeforeKeepAliveResponseReceived() throws Exception {
    testCloseTheConnectionAfterResetBeforeResponseReceived(false);
  }

  private void testCloseTheConnectionAfterResetBeforeResponseReceived(boolean pipelined) throws Exception {
    waitFor(2);
    server.close();
    NetServer server = vertx.createNetServer();
    CompletableFuture<Void> requestReceived = new CompletableFuture<>();
    try {
      AtomicInteger count = new AtomicInteger();
      AtomicBoolean closed = new AtomicBoolean();
      server.connectHandler(so -> {
        Buffer total = Buffer.buffer();
        switch (count.getAndIncrement()) {
          case 0:
            so.handler(buff -> {
              total.appendBuffer(buff);
              if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
                  "Host: localhost:8080\r\n" +
                  "\r\n")) {
                requestReceived.complete(null);
                so.write(Buffer.buffer(
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Length: 200\r\n" +
                        "\r\n" +
                        "Some-Buffer"
                ));
                so.closeHandler(v -> {
                  closed.set(true);
                });
              }
            });
            break;
          case 1:
            so.handler(buff -> {
              total.appendBuffer(buff);
              if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
                  "Host: localhost:8080\r\n" +
                  "\r\n")) {
                so.write(
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: 11\r\n" +
                        "\r\n" +
                        "Hello world");
                complete();
              }
            });
            break;
          default:
            fail("Invalid count");
            break;

        }
      });
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(pipelined).setKeepAlive(true));
      HttpClientRequest req1 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
      if (pipelined) {
        requestReceived.thenAccept(v -> {
          req1.reset();
          client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
            assertEquals(200, resp.statusCode());
            resp.bodyHandler(body -> {
              assertEquals("Hello world", body.toString());
              complete();
            });
          });
        });
        req1.handler(resp1 -> fail());
        req1.end();
      } else {
        requestReceived.thenAccept(v -> {
          req1.reset();
        });
        req1.handler(resp -> fail());
        req1.end();
        client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
          assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            assertEquals("Hello world", body.toString());
            complete();
          });
        });
      }
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testInvalidTrailerInHttpServerRequest() throws Exception {
    testHttpServerRequestDecodeError(so -> {
      so.write("0\r\n"); // Empty chunk
      // Send large trailer
      for (int i = 0;i < 2000;i++) {
        so.write("01234567");
      }
    }, errors -> {
      assertEquals(2, errors.size());
      assertEquals(TooLongFrameException.class, errors.get(0).getClass());
    });
  }

  @Test
  public void testInvalidChunkInHttpServerRequest() throws Exception {
    testHttpServerRequestDecodeError(so -> {
      so.write("invalid\r\n"); // Empty chunk
    }, errors -> {
      assertEquals(2, errors.size());
      assertEquals(NumberFormatException.class, errors.get(0).getClass());
    });
  }

  private void testHttpServerRequestDecodeError(Handler<NetSocket> bodySender, Handler<List<Throwable>> errorsChecker) throws Exception {
    AtomicReference<NetSocket> current = new AtomicReference<>();
    server.requestHandler(req -> {
      List<Throwable> errors = new ArrayList<>();
      req.exceptionHandler(errors::add);
      req.response().closeHandler(v -> {
        errorsChecker.handle(errors);
        testComplete();
      });
      bodySender.handle(current.get());
    });
    startServer();
    NetClient client = vertx.createNetClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(so -> {
      current.set(so);
      so.write("POST /somepath HTTP/1.1\r\n");
      so.write("Transfer-Encoding: chunked\r\n");
      so.write("\r\n");
    }));
    await();
  }

  @Test
  public void testInvalidChunkInHttpClientResponse() throws Exception {
    server.requestHandler(req -> {
      NetSocket so = req.netSocket();
      so.write("HTTP/1.1 200 OK\r\n");
      so.write("Transfer-Encoding: chunked\r\n");
      so.write("\r\n");
      so.write("invalid\r\n"); // Empty chunk
    });
    AtomicInteger status = new AtomicInteger();
    testHttpClientResponseDecodeError(err -> {
      switch (status.incrementAndGet()) {
        case 1:
          assertTrue(err instanceof NumberFormatException);
          break;
        case 2:
          assertTrue(err instanceof VertxException);
          assertTrue(err.getMessage().equals("Connection was closed"));
          testComplete();
          break;
      }
    });
  }

  @Test
  public void testInvalidTrailersInHttpClientResponse() throws Exception {
    server.requestHandler(req -> {
      NetSocket so = req.netSocket();
      so.write("HTTP/1.1 200 OK\r\n");
      so.write("Transfer-Encoding: chunked\r\n");
      so.write("\r\n");
      so.write("0\r\n"); // Empty chunk
      // Send large trailer
      for (int i = 0;i < 2000;i++) {
        so.write("01234567");
      }
    });
    AtomicInteger status = new AtomicInteger();
    testHttpClientResponseDecodeError(err -> {
      switch (status.incrementAndGet()) {
        case 1:
          assertTrue(err instanceof TooLongFrameException);
          break;
        case 2:
          assertTrue(err instanceof VertxException);
          assertTrue(err.getMessage().equals("Connection was closed"));
          testComplete();
          break;
      }
    });
  }

  private void testHttpClientResponseDecodeError(Handler<Throwable> errorHandler) throws Exception {
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      resp.exceptionHandler(errorHandler);
    });
    await();
  }

  @Test
  public void testRequestTimeoutIsNotDelayedAfterResponseIsReceived() throws Exception {
    int n = 6;
    waitFor(n);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        HttpClient client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(n));
        for (int i = 0;i < n;i++) {
          AtomicBoolean responseReceived = new AtomicBoolean();
          client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
            try {
              Thread.sleep(150);
            } catch (InterruptedException e) {
              fail(e);
            }
            responseReceived.set(true);
            // Complete later, if some timeout tasks have been queued, this will be executed after
            vertx.runOnContext(v -> complete());
          }).exceptionHandler(err -> {
            fail("Was not expecting to get a timeout after the response is received");
          }).setTimeout(500).end();
        }
      }
    }, new DeploymentOptions().setWorker(true));
    await();
  }

  @Test
  public void testPerPeerPooling() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setMaxPoolSize(1)
        .setKeepAlive(true)
        .setPipelining(false));
    testPerPeerPooling(i -> client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath").setHost("host" + i));
  }

  @Test
  public void testPerPeerPoolingWithProxy() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setMaxPoolSize(1)
        .setKeepAlive(true)
        .setPipelining(false).setProxyOptions(new ProxyOptions()
            .setType(ProxyType.HTTP)
            .setHost(DEFAULT_HTTP_HOST)
            .setPort(DEFAULT_HTTP_PORT)));
    testPerPeerPooling(i -> client.get(80, "host" + i, "/somepath"));
  }

  @Test
  public void testNoNPEWhenClientBreaksConnection() throws Exception {

    final File f = setupFile("file.pdf", TestUtils.randomUnicodeString(1000000));

    server.requestHandler(req -> {
      req.connection().closeHandler(v -> {
        req.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/pdf");

        try {
          req.response().sendFile(f.getAbsolutePath(), event -> {
            if (event.failed()) {
              testComplete();
            } else {
              fail("It should not reach this point");
            }
          });
        } catch (NullPointerException e) {
          // this was the bug reported with issues/issue-80
          fail("It should not throw NPE");
        }
      });
    });

    server.listen(onSuccess(server -> {
      vertx.createNetClient().connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, socket -> {
        socket.result().write("GET / HTTP/1.1\r\n\r\n").close();
      });
    }));

    await();
  }

  private void testPerPeerPooling(Function<Integer, HttpClientRequest> requestProvider) throws Exception {
    // Even though we use the same server host, we pool per peer host
    waitFor(2);
    int numPeers = 3;
    int numRequests = 5;
    Map<String, HttpServerResponse> map = new HashMap<>();
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      assertFalse(map.containsKey(req.host()));
      map.put(req.host(), req.response());
      if (map.size() == numPeers) {
        map.values().forEach(HttpServerResponse::end);
        map.clear();
        if (count.incrementAndGet() == numRequests) {
          complete();
        }
      }
    });
    startServer();
    AtomicInteger remaining = new AtomicInteger(numPeers * numRequests);
    for (int i = 0;i < numPeers;i++) {
      for (int j = 0;j < numRequests;j++) {
        HttpClientRequest req = requestProvider.apply(i);
        req.handler(resp -> {
          assertEquals(200, resp.statusCode());
          if (remaining.decrementAndGet() == 0) {
            complete();
          }
        });
        req.end();
      }
    }
    await();
  }

  @Test
  public void testHeadMDontAutomaticallySetContentHeaders() throws Exception {
    testHeadMustNoAutomaticallySetContentHeaders(MultiMap.caseInsensitiveMultiMap(), respHeaders -> {
      assertFalse(respHeaders.contains("Content-Length"));
      assertFalse(respHeaders.contains("Transfer-Encoding"));
    });
  }

  @Test
  public void testHeadMustNotSendBodyWhenContentLengthSet() throws Exception {
    MultiMap reqHeaders = MultiMap.caseInsensitiveMultiMap();
    reqHeaders.set("Content-Length", "10");
    testHeadMustNoAutomaticallySetContentHeaders(reqHeaders, respHeaders -> {
      assertEquals(respHeaders.get("Content-Length"), " 10");
      assertFalse(respHeaders.contains("Transfer-Encoding"));
    });
  }

  @Ignore("See https://github.com/netty/netty/issues/6761")
  @Test
  public void testHeadMustNotSendBodyWhenTransferEncodingSet() throws Exception {
    MultiMap reqHeaders = MultiMap.caseInsensitiveMultiMap();
    reqHeaders.set("Transfer-Encoding", "chunked");
    testHeadMustNoAutomaticallySetContentHeaders(reqHeaders, respHeaders -> {
      assertEquals(respHeaders.get("Content-Length"), "10");
      assertFalse(respHeaders.contains("Transfer-Encoding"));
    });
  }

  private void testHeadMustNoAutomaticallySetContentHeaders(MultiMap reqHeaders, Consumer<MultiMap> headersChecker) throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.headers().addAll(reqHeaders);
      resp.end();
    });
    startServer();
    NetClient client = vertx.createNetClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTPS_HOST, onSuccess(so -> {
      so.write(
          "HEAD / HTTP/1.1\r\n" +
          "Connection: close\r\n" +
          "\r\n");
      Buffer buff = Buffer.buffer();
      so.handler(buff::appendBuffer);
      so.endHandler(v -> {
        String content = buff.toString();
        int idx = content.indexOf("\r\n\r\n");
        LinkedList<String> records = new LinkedList<String>(Arrays.asList(content.substring(0, idx).split("\\r\\n")));
        assertEquals("HTTP/1.1 200 OK", records.removeFirst());
        assertEquals("", content.substring(idx + 4));
        MultiMap respHeaders = MultiMap.caseInsensitiveMultiMap();
        records.forEach(record -> {
          int index = record.indexOf(":");
          String value = record.substring(0, index);
          respHeaders.add(value, record.substring(index + 1));
        });
        headersChecker.accept(respHeaders);
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testUnknownContentLengthIsSetToZeroWithHTTP_1_0() throws Exception {
    server.requestHandler(req -> {
      req.response().write("Some-String").end();
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0));
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      assertNull(resp.getHeader("Content-Length"));
      testComplete();
    });
    await();
  }

  @Test
  public void testPartialH2CAmbiguousRequest() throws Exception {
    server.requestHandler(req -> {
      assertEquals("POST", req.rawMethod());
      testComplete();
    });
    Buffer fullRequest = Buffer.buffer("POST /whatever HTTP/1.1\r\n\r\n");
    startServer();
    NetClient client = vertx.createNetClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(so -> {
      so.write(fullRequest.slice(0, 1));
      vertx.setTimer(1000, id -> {
        so.write(fullRequest.slice(1, fullRequest.length()));
      });
    }));
    await();
  }

  @Test
  public void testIdleTimeoutWithPartialH2CRequest() throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setIdleTimeout(1));
    server.requestHandler(req -> {
      testComplete();
    });
    startServer();
    NetClient client = vertx.createNetClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(so -> {
      so.closeHandler(v -> {
        testComplete();
      });
    }));
    await();
  }
}
