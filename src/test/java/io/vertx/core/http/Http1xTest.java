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

package io.vertx.core.http;

import io.netty.handler.codec.TooLongFrameException;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.CheckingSender;
import io.vertx.test.verticles.SimpleServer;
import io.vertx.test.core.TestUtils;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class Http1xTest extends HttpTest {

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
      "127.0.0.1 localhost\n" +
      "127.0.0.1 host0\n" +
      "127.0.0.1 host1\n" +
      "127.0.0.1 host2\n"));
    return options;
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

    assertEquals(0, options.getIdleTimeout());
    assertEquals(TimeUnit.SECONDS, options.getIdleTimeoutUnit());
    assertEquals(options, options.setIdleTimeout(10));
    assertEquals(options, options.setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
    assertEquals(10, options.getIdleTimeout());
    assertEquals(TimeUnit.MILLISECONDS, options.getIdleTimeoutUnit());
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

    assertEquals(HttpClientOptions.DEFAULT_KEEP_ALIVE_TIMEOUT, options.getKeepAliveTimeout());
    assertEquals(options, options.setKeepAliveTimeout(10));
    assertEquals(10, options.getKeepAliveTimeout());
    assertIllegalArgumentException(() -> options.setKeepAliveTimeout(-1));

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT, options.getHttp2KeepAliveTimeout());
    assertEquals(options, options.setHttp2KeepAliveTimeout(10));
    assertEquals(10, options.getHttp2KeepAliveTimeout());
    assertIllegalArgumentException(() -> options.setHttp2KeepAliveTimeout(-1));
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

    assertEquals(65536, options.getMaxWebSocketFrameSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setMaxWebSocketFrameSize(rand));
    assertEquals(rand, options.getMaxWebSocketFrameSize());

    assertEquals(80, options.getPort());
    assertEquals(options, options.setPort(1234));
    assertEquals(1234, options.getPort());
    assertIllegalArgumentException(() -> options.setPort(-1));
    assertIllegalArgumentException(() -> options.setPort(65536));

    assertEquals("0.0.0.0", options.getHost());
    String randString = TestUtils.randomUnicodeString(100);
    assertEquals(options, options.setHost(randString));
    assertEquals(randString, options.getHost());

    assertNull(options.getWebSocketSubProtocols());
    assertEquals(options, options.setWebSocketSubProtocols(Collections.singletonList("foo")));
    assertEquals(Collections.singletonList("foo"), options.getWebSocketSubProtocols());

    HttpServerOptions optionsCopy = new HttpServerOptions(options);
    assertEquals(options.toJson(), optionsCopy.setWebSocketSubProtocols(options.getWebSocketSubProtocols()).toJson());

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
    int idleTimeout = TestUtils.randomPositiveInt();
    boolean ssl = rand.nextBoolean();
    KeyCertOptions keyCertOptions = randomKeyCertOptions();
    TrustOptions trustOptions = randomTrustOptions();
    String enabledCipher = TestUtils.randomAlphaString(100);
    int connectTimeout = TestUtils.randomPositiveInt();
    boolean trustAll = rand.nextBoolean();
    String crlPath = TestUtils.randomUnicodeString(100);
    Buffer crlValue = TestUtils.randomBuffer(100);
    int keepAliveTimeout = TestUtils.randomPositiveInt();
    int http2KeepAliveTimeout = TestUtils.randomPositiveInt();

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
    options.setKeepAliveTimeout(keepAliveTimeout);
    options.setHttp2KeepAliveTimeout(http2KeepAliveTimeout);
    HttpClientOptions copy = new HttpClientOptions(options);
    checkCopyHttpClientOptions(options, copy);
    HttpClientOptions copy2 = new HttpClientOptions(options.toJson());
    checkCopyHttpClientOptions(options, copy2);
  }

  private void checkCopyHttpClientOptions(HttpClientOptions options, HttpClientOptions copy) {
    assertEquals(options.toJson(), copy.toJson());
    assertNotSame(options.getKeyCertOptions(), copy.getKeyCertOptions());
    assertNotSame(options.getTrustOptions(), copy.getTrustOptions());
    if (copy.getTrustOptions() instanceof PemTrustOptions) {
      assertEquals(((PemTrustOptions) options.getTrustOptions()).getCertValues(), ((PemTrustOptions) copy.getTrustOptions()).getCertValues());
    } else {
      assertEquals(options.getTrustOptions().toJson(), copy.getTrustOptions().toJson());
    }
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
    assertEquals(def.getKeepAliveTimeout(), json.getKeepAliveTimeout());
    assertEquals(def.getHttp2KeepAliveTimeout(), json.getHttp2KeepAliveTimeout());
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
    int keepAliveTimeout = TestUtils.randomPositiveInt();
    int http2KeepAliveTimeout = TestUtils.randomPositiveInt();

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
      .put("decoderInitialBufferSize", decoderInitialBufferSize)
      .put("keepAliveTimeout", keepAliveTimeout)
      .put("http2KeepAliveTimeout", http2KeepAliveTimeout);

    HttpClientOptions options = new HttpClientOptions(json);
    assertEquals(sendBufferSize, options.getSendBufferSize());
    assertEquals(receiverBufferSize, options.getReceiveBufferSize());
    assertEquals(reuseAddress, options.isReuseAddress());
    assertEquals(trafficClass, options.getTrafficClass());
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(tcpNoDelay, options.isTcpNoDelay());
    assertEquals(soLinger, options.getSoLinger());
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

    assertEquals(keepAliveTimeout, options.getKeepAliveTimeout());
    assertEquals(http2KeepAliveTimeout, options.getHttp2KeepAliveTimeout());
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
    int maxWebSocketFrameSize = TestUtils.randomPositiveInt();
    List<String> wsSubProtocols = Arrays.asList(TestUtils.randomAlphaString(10));
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
    options.setMaxWebSocketFrameSize(maxWebSocketFrameSize);
    options.setWebSocketSubProtocols(wsSubProtocols);
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
    assertEquals(options.toJson(), copy.toJson());
    assertNotSame(options.getKeyCertOptions(), copy.getKeyCertOptions());
    assertNotSame(options.getTrustOptions(), copy.getTrustOptions());
    if (copy.getTrustOptions() instanceof PemTrustOptions) {
      assertEquals(((PemTrustOptions) options.getTrustOptions()).getCertValues(), ((PemTrustOptions) copy.getTrustOptions()).getCertValues());
    } else {
      assertEquals(options.getTrustOptions().toJson(), copy.getTrustOptions().toJson());
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testDefaultServerOptionsJson() {
    HttpServerOptions def = new HttpServerOptions();
    HttpServerOptions json = new HttpServerOptions(new JsonObject());
    assertEquals(def.getMaxWebSocketFrameSize(), json.getMaxWebSocketFrameSize());
    assertEquals(def.getWebSocketSubProtocols(), json.getWebSocketSubProtocols());
    assertEquals(def.isCompressionSupported(), json.isCompressionSupported());
    assertEquals(def.getCrlPaths(), json.getCrlPaths());
    assertEquals(def.getCrlValues(), json.getCrlValues());
    assertEquals(def.getAcceptBacklog(), json.getAcceptBacklog());
    assertEquals(def.getPort(), json.getPort());
    assertEquals(def.getHost(), json.getHost());
    assertEquals(def.isTcpNoDelay(), json.isTcpNoDelay());
    assertEquals(def.isTcpKeepAlive(), json.isTcpKeepAlive());
    assertEquals(def.getSoLinger(), json.getSoLinger());
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
    int maxWebSocketFrameSize = TestUtils.randomPositiveInt();
    List<String> wsSubProtocols = Collections.singletonList(randomAlphaString(10));
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
      .put("maxWebSocketFrameSize", maxWebSocketFrameSize)
      .put("webSocketSubProtocols", wsSubProtocols)
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
    assertEquals(maxWebSocketFrameSize, options.getMaxWebSocketFrameSize());
    assertEquals(wsSubProtocols, options.getWebSocketSubProtocols());
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

  @Test
  @Override
  public void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd() throws Exception {
    testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(0);
  }

  // Extra tests

  @Test
  public void testTimedOutWaiterDoesntConnect() throws Exception {
    Assume.assumeTrue("Domain socket don't pass this test", testAddress.isInetSocket());
    long responseDelay = 300;
    int requests = 6;
    client.close();
    CountDownLatch firstCloseLatch = new CountDownLatch(1);
    server.close(onSuccess(v -> firstCloseLatch.countDown()));
    // Make sure server is closed before continuing
    awaitLatch(firstCloseLatch);

    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(false).setMaxPoolSize(1));
    AtomicInteger requestCount = new AtomicInteger(0);
    // We need a net server because we need to intercept the socket connection, not just full http requests
    NetServer server = vertx.createNetServer();
    server.connectHandler(socket -> {
      Buffer content = Buffer.buffer();
      AtomicBoolean closed = new AtomicBoolean();
      socket.closeHandler(v -> closed.set(true));
      socket.handler(buff -> {
        content.appendBuffer(buff);
        if (buff.toString().endsWith("\r\n\r\n")) {
          // Delay and write a proper http response
          vertx.setTimer(responseDelay, time -> {
            if (!closed.get()) {
              requestCount.incrementAndGet();
              socket.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK");
            }
          });
        }
      });
    });

    CountDownLatch latch = new CountDownLatch(requests);

    server.listen(testAddress, onSuccess(s -> {
      for(int count = 0; count < requests; count++) {

        RequestOptions reqOptions = new RequestOptions()
          .setPort(DEFAULT_HTTP_PORT)
          .setHost(DEFAULT_HTTP_HOST)
          .setURI(DEFAULT_TEST_URI);

        HttpClientRequest req;
        if (count % 2 == 0) {
          req = client.request(testAddress, reqOptions)
            .setHandler(onSuccess(resp -> {
              resp.bodyHandler(buff -> {
                assertEquals("OK", buff.toString());
                latch.countDown();
              });
            }))
            .exceptionHandler(this::fail);
        } else {
          // Odd requests get a timeout less than the responseDelay, since we have a pool size of one and a delay all but
          // the first request should end up in the wait queue, the odd numbered requests should time out so we should get
          // (requests + 1 / 2) connect attempts
          req = client.request(testAddress, reqOptions).setHandler(onFailure(err -> {
            latch.countDown();
          }));
          req.setTimeout(responseDelay / 2);
        }
        req.end();
      }
    }));

    awaitLatch(latch);

    assertEquals("Incorrect number of connect attempts.", (requests + 1) / 2, requestCount.get());
    server.close();
  }

  @Test
  public void testPipeliningOrder() throws Exception {
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true).setPipelining(true).setMaxPoolSize(1));
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

    server.listen(testAddress, onSuccess(s -> {
      vertx.setTimer(500, id -> {
        for (int count = 0; count < requests; count++) {
          int theCount = count;
          client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
            .setHandler(onSuccess(resp -> {
              assertEquals(theCount, Integer.parseInt(resp.headers().get("count")));
              resp.bodyHandler(buff -> {
                assertEquals("This is content " + theCount, buff.toString());
                latch.countDown();
              });
            }))
            .setChunked(true)
            .putHeader("count", String.valueOf(count))
            .end("This is content " + count);
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
        "host: localhost:8080\r\n" +
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
    server.listen(testAddress, onSuccess(v -> {
      listenLatch.countDown();
    }));
    awaitLatch(listenLatch);

    AtomicInteger responses = new AtomicInteger();
    for (int i = 0;i < requests;i++) {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          if (responses.incrementAndGet() == requests) {
            testComplete();
          }
        }))
        .end();
    }
    await();
  }

  @Test
  @Repeat(times = 10)
  public void testCloseServerConnectionWithPendingMessages() throws Exception {
    int n = 5;
    server.requestHandler(req -> {
      vertx.setTimer(100, id -> {
        req.response().close();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(n).setPipelining(true));
    AtomicBoolean completed = new AtomicBoolean();
    client.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        if (completed.compareAndSet(false, true)) {
          testComplete();
        }
      });
    });
    for (int i = 0; i < n * 2; i++) {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onFailure(resp -> {}))
        .end();
    }
    await();
  }

  @Test
  public void testPipeliningFailure() throws Exception {
    int n = 5;
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true).setPipeliningLimit(n).setMaxPoolSize(1));
    CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    AtomicBoolean first = new AtomicBoolean(true);
    server.requestHandler(req -> {
      if (first.compareAndSet(true, false)) {
        closeFuture.thenAccept(v -> {
          req.response().close();
        });
      } else {
        req.response().end();
      }
    });
    startServer(testAddress);
    AtomicInteger succeeded = new AtomicInteger();
    List<HttpClientRequest> requests = new CopyOnWriteArrayList<>();
    Consumer<HttpClientRequest> checkEnd = req -> {
      requests.remove(req);
      if (requests.isEmpty()) {
        assertEquals(n, succeeded.get());
        testComplete();
      }
    };
    for (int i = 0;i < n * 2;i++) {
      AtomicReference<HttpClientRequest> ref = new AtomicReference<>();
      HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/" + i)
        .setHandler(ar -> {
          if (ar.succeeded()) {
            succeeded.incrementAndGet();
          }
          checkEnd.accept(ref.get());
        });
      ref.set(req);
      requests.add(req);
      req.end();
    }
    closeFuture.complete(null);
    await();
  }

  /**
   * A test that stress HTTP server pipe-lining.
   */
  @Test
  public void testPipelineStress() throws Exception {

    // A client that will aggressively pipeline HTTP requests and close the connection abruptly after one second
    class Client {
      private final NetSocket so;
      private StringBuilder received;
      private int curr;
      private int count;
      private boolean closed;
      Client(NetSocket so) {
        this.so = so;
      }
      private void close() {
        closed = true;
      }
      private void receiveChunk(Buffer chunk) {
        received.append(chunk);
        int c;
        while ((c = received.indexOf("\r\n\r\n", curr)) != -1) {
          curr = c + 4;
          count++;
        }
        if (count == 16 && !closed) {
          send();
        }
      }
      private void send() {
        received = new StringBuilder();
        curr = 0;
        count = 0;
        for (int i = 0;i < 16;i++) {
          so.write(Buffer.buffer("" +
            "GET / HTTP/1.1\r\n" +
            "content-length:0\r\n" +
            "\r\n"));
        }
      }
      void run() {
        so.handler(this::receiveChunk);
        so.closeHandler(v -> {
          close();
          complete();
        });
        send();
        vertx.setTimer(1000, id -> {
          so.close();
        });
      }
    }

    // We want to be aware of uncaught exceptions and fail the test when it happens
    vertx.exceptionHandler(err -> {
      fail(err);
    });

    server.requestHandler(req -> {
      // Use runOnContext to allow pipelined requests to pile up in the server
      // when we send a response right away it's nearly like no pipe-lining is occurring
      Vertx.currentContext().runOnContext(v -> {
        req.response().end("Hello World");
      });
    });
    startServer(testAddress);
    NetClient tcpClient = vertx.createNetClient(new NetClientOptions().setSoLinger(0));
    int numConn = 32;
    waitFor(numConn);
    for (int i = 0;i < numConn;i++) {
      tcpClient.connect(testAddress, onSuccess(so -> {
        Client client = new Client(so);
        client.run();
      }));
    }
    await();
  }

  @Test
  public void testPipeliningPauseRequest() throws Exception {
    int n = 10;
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true).setMaxPoolSize(1));
    server.requestHandler(req -> {
      AtomicBoolean paused = new AtomicBoolean();
      paused.set(true);
      req.pause();
      req.bodyHandler(buff -> {
        assertFalse(paused.get());
        req.response().end();
      });
      vertx.setTimer(30, id -> {
        paused.set(false);
        req.resume();
      });
    });
    startServer(testAddress);
    AtomicInteger remaining = new AtomicInteger(n);
    for (int i = 0;i < n;i++) {
      client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.endHandler(v -> {
            if (remaining.decrementAndGet() == 0) {
              testComplete();
            }
          });
        }))
        .end(TestUtils.randomAlphaString(16));
    }
    await();
  }

  @Test
  public void testServerPipeliningConnectionConcurrency() throws Exception {
    int n = 5;
    boolean[] processing = {false};
    int[] count = {0};
    server.requestHandler(req -> {
      count[0]++;
      assertFalse(processing[0]);
      processing[0] = true;
      vertx.setTimer(20, id -> {
        processing[0] = false;
        HttpServerResponse resp = req.response();
        resp.end();
        if (count[0] == n) {
          resp.close();
        }
      });
    });
    startServer(testAddress);
    Buffer requests = Buffer.buffer();
    for (int i = 0;i < n;i++) {
      requests.appendString("GET " + DEFAULT_TEST_URI + " HTTP/1.1\r\n\r\n");
    }
    NetClient client = vertx.createNetClient();
    client.connect(testAddress, onSuccess(so -> {
      so.closeHandler(v -> testComplete());
      so.write(requests);
    }));
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

    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(keepAlive).setPipelining(false).setMaxPoolSize(poolSize));
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
      server.listen(testAddress, onSuccess(s -> {
        startServerLatch.countDown();
      }));
      servers[i] = server;
    }

    awaitLatch(startServerLatch);

    CountDownLatch reqLatch = new CountDownLatch(requests);

    // We make sure we execute all the requests on the same context otherwise some responses can come beack when there
    // are no waiters resulting in it being closed so a a new connection is made for the next request resulting in the
    // number of total connections being > pool size (which is correct)
    vertx.runOnContext(v -> {
      for (int count = 0; count < requests; count++) {
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
            reqLatch.countDown();
          }))
          .end();
      }
    });

    awaitLatch(reqLatch);

    //client.dispConnCount();
    assertEquals(expectedConnectedServers, connectedServers.size());

    CountDownLatch serverCloseLatch = new CountDownLatch(numServers);
    for (HttpServer server: servers) {
      server.close(onSuccess(s -> {
        serverCloseLatch.countDown();
      }));
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

  private void testPooling(boolean keepAlive, boolean pipelining) {
    String path = "foo.txt";
    int numGets = 100;
    int maxPoolSize = 10;
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions()
      .setKeepAlive(keepAlive)
      .setPipelining(pipelining)
      .setMaxPoolSize(maxPoolSize)
    );

    server.requestHandler(req -> {
      String cnt = req.headers().get("count");
      req.response().headers().set("count", cnt);
      req.response().end();
    });

    server.listen(testAddress, onSuccess(s -> {

      AtomicInteger cnt = new AtomicInteger(0);
      for (int i = 0; i < numGets; i++) {
        int theCount = i;
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, path)
          .setHandler(onSuccess(resp -> {
            resp.exceptionHandler(this::fail);
            assertEquals(200, resp.statusCode());
            assertEquals(theCount, Integer.parseInt(resp.headers().get("count")));
            if (cnt.incrementAndGet() == numGets) {
              testComplete();
            }
          }))
          .exceptionHandler(t -> {
            fail("Should not throw exception: " + t.getMessage());
          })
          .putHeader("count", String.valueOf(i))
          .end();
      }
    }));

    await();
  }

  @Test
  public void testPoolingNoKeepAliveAndPipelining() {
    try {
      vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false).setPipelining(true));
      fail();
    } catch (IllegalStateException ignore) {
    }
  }

  @Test
  public void testMaxWaitQueueSizeIsRespected() throws Exception {
    client.close();

    client = vertx.createHttpClient(createBaseClientOptions().setDefaultHost(DEFAULT_HTTP_HOST).setDefaultPort(DEFAULT_HTTP_PORT)
        .setPipelining(false).setMaxWaitQueueSize(0).setMaxPoolSize(2));

    waitFor(3);

    Set<String> expected = new HashSet<>(Arrays.asList("/1", "/2"));
    server.requestHandler(req -> {
      assertTrue(expected.contains(req.path()));
      complete();
    });

    startServer(testAddress);

    HttpClientRequest req1 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/1")
      .setHandler(onFailure(err -> {}));

    HttpClientRequest req2 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/2")
      .setHandler(onFailure(resp -> {}));

    HttpClientRequest req3 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/3")
      .setHandler(onFailure(t -> {
        assertTrue("Incorrect exception: " + t.getClass().getName(), t instanceof ConnectionPoolTooBusyException);
        complete();
      }));

    req1.end();
    req2.end();
    req3.end();

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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          resp.endHandler(v -> testComplete());
        })).exceptionHandler(t -> fail("Should not be called"))
        .setTimeout(timeout)
        .end();
    }));

    await();
  }

  @Test
  public void testServerWebSocketIdleTimeout() {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST));
    server.webSocketHandler(ws -> {}).listen(ar -> {
      assertTrue(ar.succeeded());
      client.webSocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", onSuccess(ws -> {
        ws.closeHandler(v -> testComplete());
      }));
    });

    await();
  }

  @Test
  public void testClientWebSocketIdleTimeout() {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setIdleTimeout(1));
    server.webSocketHandler(ws -> {}).listen(ar -> {
      client.webSocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", onSuccess(ws -> {
        ws.closeHandler(v -> testComplete());
      }));
    });

    await();
  }

  @Test
  public void testSharedServersRoundRobin() throws Exception {
    client.close();
    server.close();
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(false));
    int numServers = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2- 1;
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
          assertSameEventLoop(ctx, context.get());
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
      }).listen(testAddress, onSuccess(s -> {
        if (s.actualPort() > 0) {
          assertEquals(DEFAULT_HTTP_PORT, s.actualPort());
        }
        latchListen.countDown();
      }));
    }
    awaitLatch(latchListen);


    // Create a bunch of connections
    CountDownLatch latchClient = new CountDownLatch(numRequests);
    for (int i = 0; i < numRequests; i++) {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(res -> latchClient.countDown())
        .end();
    }

    assertTrue(latchClient.await(10, TimeUnit.SECONDS));
    assertTrue(latchConns.await(10, TimeUnit.SECONDS));

    assertEquals(numServers, connectedServers.size());
    for (HttpServer server : servers) {
      assertTrue(connectedServers.contains(server));
    }
    assertEquals(numServers, requestCount.size());
    assertEquals(requestCount.values().stream().mapToInt(i -> i).sum(), numRequests);
    assertEquals(IntStream.range(0, requestCount.size())
      .mapToObj(i -> numRequests / numServers)
      .collect(Collectors.toList()), new ArrayList<>(requestCount.values()));
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
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> resp.endHandler(v -> testComplete())))
        .end();
    }));

    await();
  }

  @Test
  public void testIncorrectHttpVersion() throws Exception {
    server.requestHandler(req -> {
      NetSocket so = req.netSocket();
      so.write(Buffer.buffer("HTTP/1.2 200 OK\r\nContent-Length:5\r\n\r\nHELLO"));
      so.close();
    });
    startServer(testAddress);
    AtomicBoolean a = new AtomicBoolean();
    HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onFailure(err -> {
      if (a.compareAndSet(false, true)) {
        assertTrue("message " + err.getMessage() + " should contain HTTP/1.2", err.getMessage().contains("HTTP/1.2"));
      }
    }));
    client.connectionHandler(conn -> conn.closeHandler(v -> testComplete()));
    req.exceptionHandler(err -> {
      fail("Should not be called");
    }).putHeader("connection", "close")
      .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setKeepAlive(true));
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertNull(resp.getHeader("Connection"));
            assertEquals(resp.getHeader("Content-Length"), "0");
            testComplete();
          });
        }))
        .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setKeepAlive(false));
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertEquals(resp.getHeader("Connection"), "close");
            testComplete();
          });
        }))
        .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0).setKeepAlive(true));
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertEquals(resp.getHeader("Connection"), "keep-alive");
            assertEquals(resp.getHeader("Content-Length"), "0");
            testComplete();
          });
        }))
        .end();
    }));

    await();
  }

  @Test
  public void testHttp10RequestNonKeepAliveConnectionClosed() throws Exception {
    client.close();

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_0, req.version());
      assertNull(req.getHeader("Connection"));
      req.response().end();
      assertTrue(req.response().closed());
    });

    server.listen(testAddress, onSuccess(s -> {
      client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0).setKeepAlive(false));
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertNull(resp.getHeader("Connection"));
            testComplete();
          });
        }))
        .end();
    }));

    await();
  }

  @Test
  public void testHttp10ResponseNonKeepAliveConnectionClosed() throws Exception {
    waitFor(3);
    server.close();
    NetServer server = vertx.createNetServer().connectHandler(so -> {
      StringBuilder request = new StringBuilder();
      so.handler(buff -> {
        request.append(buff);
        if (request.toString().endsWith("\r\n\r\n")) {
          request.setLength(0);
          so.write("" +
            "HTTP/1.0 200 OK\r\n" +
            "Content-Length: 0\r\n" +
            "\r\n");
          so.close();
        }
      });
    });
    server.listen(testAddress, onSuccess(v1 -> {
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true).setMaxPoolSize(1));
      for (int i = 0;i < 3;i++) {
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
          .setHandler(onSuccess(resp -> {
            resp.endHandler(v2 -> {
              complete();
            });
          })).exceptionHandler(err -> {
          fail();
        }).end();
      }
    }));
    await();
  }

  @Test
  public void requestAbsNoPort() {
    client.request(new RequestOptions().setAbsoluteURI("http://www.google.com")).setHandler(res -> testComplete()).end();
    await();
  }

  @Test
  public void testRequestsTimeoutInQueue() {

    server.requestHandler(req -> {
      vertx.setTimer(1000, id -> {
        HttpServerResponse resp = req.response();
        if (!resp.closed()) {
          resp.end();
        }
      });
    });

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false).setMaxPoolSize(1));

    server.listen(testAddress, onSuccess(s -> {
      // Add a few requests that should all timeout
      for (int i = 0; i < 5; i++) {
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(onFailure(t -> {
            assertTrue(t instanceof TimeoutException);
          }))
          .setTimeout(500)
          .end();
      }
      // Now another request that should not timeout
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        })).exceptionHandler(t -> fail("Should not throw exception"))
        .setTimeout(3000)
        .end();
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
    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/uri")
        .setHandler(onSuccess(res -> {
          assertEquals(200, res.statusCode());
          testComplete();
        }))
        .end();
    }));
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
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/uri")
        .setHandler(onSuccess(res -> {
          assertEquals(200, res.statusCode());
          testComplete();
        }))
        .end();
    });
    await();
  }

  @Test
  public void testRequestExceptionHandlerContext() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().close();
    });
    startServer(testAddress);
    Context clientCtx = vertx.getOrCreateContext();
    client.close();
    clientCtx.runOnContext(v -> {
      client = vertx.createHttpClient(createBaseClientOptions());
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onFailure(err -> {
          assertSameEventLoop(clientCtx, Vertx.currentContext());
          complete();
        }))
        .exceptionHandler(err -> {
          assertSameEventLoop(clientCtx, Vertx.currentContext());
          complete();
        })
        .sendHead();
    });
    await();
  }

  private void fill(Buffer buffer, WriteStream<Buffer> ws, LongConsumer done) {
    fill(0, buffer, ws, done);
  }

  private void fill(long amount, Buffer buffer, WriteStream<Buffer> ws, LongConsumer done) {
    if (ws.writeQueueFull()) {
      done.accept(amount);
    } else {
      ws.write(buffer);
      vertx.setTimer(1, v -> {
        fill(amount + buffer.length(), buffer, ws, done);
      });
    }
  }

  @Test
  public void testContexts() throws Exception {
    int numReqs = 4;
    Buffer data = randomBuffer(1024);
    AtomicInteger cnt = new AtomicInteger();
    // Server connect handler should always be called with same context
    Context serverCtx = vertx.getOrCreateContext();
    CountDownLatch latch = new CountDownLatch(1);
    List<HttpServerRequest> requests = Collections.synchronizedList(new ArrayList<>());
    Map<String, CompletionStage<Long>> requestResumeMap = new HashMap<>();
    Map<String, CompletionStage<Void>> responseResumeMap = new HashMap<>();
    serverCtx.runOnContext(v1 -> {
      server.requestHandler(req -> {
        req.pause();
        assertSameEventLoop(serverCtx, Vertx.currentContext());
        Buffer body = Buffer.buffer();
        requestResumeMap.get(req.path()).thenAccept(amount -> {
          req.resume();
          req.endHandler(v2 -> {
            assertSameEventLoop(serverCtx, Vertx.currentContext());
            assertEquals((long)amount, body.length());
            requests.add(req);
            if (requests.size() == numReqs) {
              requests.forEach(req_ ->{
                HttpServerResponse resp = req_.response();
                CompletableFuture<Void> cf = new CompletableFuture<>();
                responseResumeMap.put(req_.path(), cf);
                resp.setChunked(true);
                fill(data, resp, sent -> {
                  cf.complete(null);
                  resp.drainHandler(v -> {
                    assertSameEventLoop(serverCtx, Vertx.currentContext());
                    resp.end();
                  });
                });
              });
            }
          });
        });
        req.handler(chunk -> {
          assertSameEventLoop(serverCtx, Vertx.currentContext());
          body.appendBuffer(chunk);
        });
      });
      server.listen(testAddress, onSuccess(s -> {
        assertSameEventLoop(serverCtx, Vertx.currentContext());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    CountDownLatch latch2 = new CountDownLatch(1);
    int numConns = 4;
    // There should be a context per request
    Set<Context> contexts = new ConcurrentHashSet<>();
    Set<Thread> threads = new ConcurrentHashSet<>();
    client.close();
    client = null;
    Context clientCtx = vertx.getOrCreateContext();
    clientCtx.runOnContext(v -> {
      client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(numConns));
    });
    waitUntil(() -> client != null);
    for (int i = 0; i < numReqs; i++) {
      CompletableFuture<Long> cf = new CompletableFuture<>();
      String path = "/" + i;
      requestResumeMap.put(path, cf);
      Context requestCtx = vertx.getOrCreateContext();
      requestCtx.runOnContext(v -> {
        HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, path)
          .setHandler(onSuccess(resp -> {
            assertSameEventLoop(requestCtx, Vertx.currentContext());
            assertEquals(200, resp.statusCode());
            contexts.add(Vertx.currentContext());
            threads.add(Thread.currentThread());
            resp.pause();
            responseResumeMap.get(path).thenAccept(v2 -> resp.resume());
            resp.handler(chunk -> {
              assertSameEventLoop(requestCtx, Vertx.currentContext());
            });
            resp.exceptionHandler(this::fail);
            resp.endHandler(v2 -> {
              assertSameEventLoop(requestCtx, Vertx.currentContext());
              if (cnt.incrementAndGet() == numReqs) {
                assertEquals(4, contexts.size());
                assertEquals(4, threads.size());
                latch2.countDown();
              }
            });
          }))
          .setChunked(true)
          .exceptionHandler(this::fail);
        req.drainHandler(v2 -> {
          assertSameEventLoop(requestCtx, Vertx.currentContext());
          req.end();
        });
        req.sendHead(version -> {
          assertSameEventLoop(requestCtx, Vertx.currentContext());
          fill(data, req, cf::complete);
        });
      });
    }
    awaitLatch(latch2, 40, TimeUnit.SECONDS);
    // Close should be in own context
    server.close(onSuccess(v -> {
      ContextInternal closeContext = ((VertxInternal) vertx).getContext();
      assertFalse(contexts.contains(closeContext));
      assertNotSame(serverCtx, closeContext);
      assertFalse(contexts.contains(serverCtx));
      testComplete();
    }));

    server = null;
    await();
  }

  @Test
  public void testRequestHandlerNotCalledInvalidRequest() {
    server.requestHandler(req -> {
      fail();
    });
    server.listen(testAddress, onSuccess(s -> {
      vertx.createNetClient(new NetClientOptions()).connect(testAddress, onSuccess(socket -> {
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
        HttpServer server = vertx.createHttpServer();
        server.requestHandler(req -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          Buffer buf = Buffer.buffer();
          req.handler(buf::appendBuffer);
          req.endHandler(v -> {
            assertEquals("hello", buf.toString());
            req.response().end("bye");
          });
        }).listen(testAddress, onSuccess(s -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          HttpClient client = vertx.createHttpClient();
          client.request(HttpMethod.PUT, testAddress, HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/blah")
            .setHandler(onSuccess(resp -> {
              assertEquals(200, resp.statusCode());
              assertTrue(Vertx.currentContext().isWorkerContext());
              assertTrue(Context.isOnWorkerThread());
              resp.handler(buf -> {
                assertEquals("bye", buf.toString());
                resp.endHandler(v -> {
                  testComplete();
                });
              });
            }))
            .setChunked(true)
            .end(Buffer.buffer("hello"));
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
    server.listen(testAddress, onSuccess(hs -> {
      HttpClient httpClient = vertx.createHttpClient();
      httpClient.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(
          onSuccess(resp -> {
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
              resp.endHandler(v -> {
                byte[] expectedData = buffer.getBytes();
                byte[] actualData = readBuffer.getBytes();
                assertTrue(Arrays.equals(expectedData, actualData));
                testComplete();
              });
            });
          }))
        .end();
    }));
    await();
  }

  /*
   A reproducer for this issue https://github.com/eclipse/vert.x/issues/2230
   */
  @Test
  public void testPauseResumeServerRequestFromAnotherThread() throws Exception {
    ExecutorService exec = Executors.newSingleThreadExecutor();
    Buffer buffer = TestUtils.randomBuffer(64 * 1024 * 1024);
    Buffer readBuffer = Buffer.buffer(64 * 1024 * 1024);
    server.requestHandler(request -> {
      request.handler(b -> {
        readBuffer.appendBuffer(b);
        request.pause();
        exec.execute(() -> {
          try {
            Thread.sleep(0, 100);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          request.resume();
        });
      });
      request.endHandler(v -> {
        byte[] expectedData = buffer.getBytes();
        byte[] actualData = readBuffer.getBytes();
        assertTrue(Arrays.equals(expectedData, actualData));
        testComplete();
      });
    });
    startServer(testAddress);
    HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTPS_HOST, DEFAULT_TEST_URI)
      .setHandler(onFailure(err -> {
      }))
      .setChunked(true);
    for (int i = 0; i < buffer.length() / 8192; i++) {
      req.write(buffer.slice(i * 8192, (i + 1) * 8192));
      Thread.sleep(0, 100);
    }
    req.end();
  }

  @Test
  public void testEndServerResponseResumeTheConnection() throws Exception {
    server.requestHandler(req -> {
      req.endHandler(v -> {
        req.pause();
        req.response().end();
      });
    });
    startServer(testAddress);
    client.close();
    waitFor(2);
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setMaxPoolSize(1));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }))
      .end();
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }))
      .end();
    await();
  }

  @Test
  public void testEndServerRequestResumeTheConnection() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
      req.endHandler(v -> {
        req.pause();
      });
    });
    startServer(testAddress);
    client.close();
    waitFor(2);
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true).setMaxPoolSize(1));
    client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      complete();
    }))
      .end("1");
    client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      complete();
    }))
      .end("2");
    await();
  }

  @Test
  public void testMultipleRecursiveCallsAndPipelining() throws Exception {
    int sendRequests = 100;
    AtomicInteger receivedRequests = new AtomicInteger();
    server.requestHandler(x -> {
        x.response().end("hello");
      })
      .listen(testAddress, r -> {
        if (r.succeeded()) {
          HttpClient client = vertx.createHttpClient(createBaseClientOptions()
              .setKeepAlive(true)
              .setPipelining(true)
          );
          IntStream.range(0, 5).forEach(i -> recursiveCall(client, receivedRequests, sendRequests));
        }
      });
    await();
  }

  private void recursiveCall(HttpClient client, AtomicInteger receivedRequests, int sendRequests){
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTPS_HOST, DEFAULT_TEST_URI)
      .setHandler(r -> {
        int numRequests = receivedRequests.incrementAndGet();
        if (numRequests == sendRequests) {
          testComplete();
        } else if (numRequests < sendRequests) {
          recursiveCall(client, receivedRequests, sendRequests);
        }
      })
      .end();
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
      .listen(testAddress, onSuccess(s -> {
        NetClient client = vertx.createNetClient();
        // Send a raw request
        client.connect(testAddress, onSuccess(conn -> {
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
      }));
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
    }).listen(testAddress, onSuccess(s -> {
      latch1.countDown();
    }));
    HttpServer server2 = vertx.createHttpServer().requestHandler(req -> {
      server2Count.incrementAndGet();
      req.response().end();
    }).listen(testAddress, onSuccess(s -> {
      latch1.countDown();
    }));
    awaitLatch(latch1);

    HttpClient[] clients = new HttpClient[5];
    for (int i = 0;i < 5;i++) {
      clients[i] = vertx.createHttpClient(createBaseClientOptions());
    }
    for (int i = 0; i < 2; i++) {
      CountDownLatch latch2 = new CountDownLatch(1);
      clients[i].request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          latch2.countDown();
        }))
        .end();
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
      clients[2 + i].request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          latch2.countDown();
        }))
        .end();
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
    }).listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }))
        .end();
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
    server = vertx.createHttpServer(createBaseServerOptions().setMaxInitialLineLength(maxInitialLength))
      .requestHandler(req -> {
      assertEquals(req.getParam("t"), longParam);
      req.response().end();
    }).listen(testAddress, onSuccess(res -> {
        vertx.createHttpClient()
          .request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/?t=" + longParam)
          .setHandler(
            onSuccess(resp -> {
              if (maxInitialLength > HttpServerOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH) {
                assertEquals(200, resp.statusCode());
                testComplete();
              } else {
                assertEquals(414, resp.statusCode());
                resp.request().connection().closeHandler(v -> {
                  testComplete();
                });
              }
            }))
          .end();
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
      server.listen(testAddress, onSuccess(v -> {
        vertx.createHttpClient(new HttpClientOptions().setMaxInitialLineLength(6000))
          .request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/?t=" + longParam)
          .setHandler(onSuccess(resp -> {
            resp.bodyHandler(body -> {
              assertEquals("0123456789", body.toString());
              testComplete();
            });
          }))
          .end();
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
    vertx.createHttpServer(createBaseServerOptions()).requestHandler(req -> {
      // Add longHeader
      req.response().putHeader("t", longHeader).end();
    }).listen(testAddress, onSuccess(res -> {
      vertx.createHttpClient(new HttpClientOptions().setMaxHeaderSize(10000))
        .request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals(resp.getHeader("t"), longHeader);
          testComplete();
        }))
        .end();
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

    vertx.createHttpServer(createBaseServerOptions().setMaxHeaderSize(maxHeaderSize))
      .requestHandler(req -> {
      assertEquals(req.getHeader("t"), longHeader);
      req.response().end();
    }).listen(testAddress, onSuccess(res -> {
      vertx.createHttpClient(new HttpClientOptions())
        .request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).setHandler(
        onSuccess(resp -> {
          if (maxHeaderSize > HttpServerOptions.DEFAULT_MAX_HEADER_SIZE) {
            assertEquals(200, resp.statusCode());
            testComplete();
          } else {
            assertEquals(400, resp.statusCode());
            resp.request().connection().closeHandler(v -> {
              testComplete();
            });
          }
        }))
        .putHeader("t", longHeader)
        .end();
    }));

    await();
  }

  @Test
  public void testInvalidHttpResponse() {

    waitFor(2);

    AtomicInteger count = new AtomicInteger(0);
    CompletableFuture<Void> sendResp = new CompletableFuture<>();
    NetServer server  = vertx.createNetServer();
    String match = "GET /somepath HTTP/1.1\r\nhost: localhost:8080\r\n\r\n";
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
    }).listen(testAddress, onSuccess(s -> {

      // We force two pipelined requests to check that the second request does not get stuck after the first failing
      client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true).setPipelining(true).setMaxPoolSize(1));

      AtomicBoolean fail1 = new AtomicBoolean();
      HttpClientRequest req1 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .setHandler(onFailure(err -> {
          if (fail1.compareAndSet(false, true)) {
            assertEquals(IllegalArgumentException.class, err.getClass()); // invalid version format
            complete();
          }
        }));

      AtomicBoolean fail2 = new AtomicBoolean();
      HttpClientRequest req2 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .setHandler(onFailure(err -> {
          if (fail2.compareAndSet(false, true)) {
            assertEquals(IllegalArgumentException.class, err.getClass()); // Closed
            complete();
          }
        }));

      req1.end();
      req2.end();
    }));

    await();
  }

  @Test
  public void testHandleInvalid204Response() throws Exception {
    int numReq = 3;
    waitFor(numReq);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true).setMaxPoolSize(1));
    server.requestHandler(r -> {
      // Generate an invalid response for the pipe-lined
      r.response().setChunked(true).setStatusCode(204).end();
    }).listen(testAddress, onSuccess(v1 -> {
      for (int i = 0;i < numReq;i++) {
        AtomicInteger count = new AtomicInteger();
        client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(
            onSuccess(r -> {
              r.endHandler(v2 -> {
                complete();
              });
            })).exceptionHandler(err -> {
          if (count.incrementAndGet() == 1) {
            complete();
          }
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
          socket.write("HTTP/1.0 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 4\r\n"
              + "Connection: keep-alive\r\n" + "\r\n" + "xxx\n");
        } else {
          socket.write("HTTP/1.0 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 1\r\n"
              + "\r\n" + "\n");
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
          socket.write("HTTP/1.0 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 4\r\n"
              + "Connection: keep-alive\r\n" + "\n" + "xxx\n");
        } else {
          socket.write("HTTP/1.0 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 1\r\n"
              + "\r\n" + "\n");
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
          socket.write("HTTP/1.1 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 4\r\n"
              + "\r\n" + "xxx\n");
        } else {
          socket.write("HTTP/1.1 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 1\r\n"
              + "Connection: close\r\n" + "\r\n" + "\n");
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
          socket.write("HTTP/1.1 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 3\r\n"
              + "\r\n" + "xxx");
        } else {
          socket.write("HTTP/1.1 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 1\r\n"
              + "Connection: close\r\n" + "\r\n" + "\n");
          socket.close();
        }
      }));
    });
  }

  private void testConnectionClose(
      Handler<HttpClientRequest> clientRequest,
      Handler<NetSocket> connectHandler
  ) throws Exception {
    // Cannot reliably pass due to https://github.com/netty/netty/issues/9113
    Assume.assumeTrue(testAddress.isInetSocket());

    client.close();
    server.close();

    NetServerOptions serverOptions = new NetServerOptions();

    CountDownLatch serverLatch = new CountDownLatch(1);
    vertx.createNetServer(serverOptions).connectHandler(connectHandler).listen(testAddress, result -> {
      if (result.succeeded()) {
        serverLatch.countDown();
      } else {
        fail();
      }
    });

    awaitLatch(serverLatch);

    HttpClientOptions clientOptions = new HttpClientOptions()
        .setDefaultHost("localhost")
        .setKeepAlive(true)
        .setPipelining(false);
    client = vertx.createHttpClient(clientOptions);

    int requests = 11;
    AtomicInteger count = new AtomicInteger(requests);

    for (int i = 0; i < requests; i++) {
      HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.handler(buffer -> {
            // Should check
          });
          resp.endHandler(v -> {
            if (count.decrementAndGet() == 0) {
              complete();
            }
          });
          resp.exceptionHandler(this::fail);
        }));
      clientRequest.handle(req);
    }

    await();
  }

  @Test
  public void testDontReuseConnectionWhenResponseEndsBeforeRequest() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(true).setKeepAlive(true));
    AtomicBoolean req1Ended = new AtomicBoolean();
    server.requestHandler(req -> {
      switch (req.path()) {
        case "/1":
          assertFalse(req1Ended.get());
          break;
        case "/2":
          assertTrue(req1Ended.get());
          break;
      }
      req.response().end();
    });
    startServer(testAddress);

    HttpClientRequest req1 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/1")
      .setHandler(
        onSuccess(resp -> {
          resp.endHandler(v1 -> {
            // End request after the response ended
            vertx.setTimer(100, v2 -> {
              req1Ended.set(true);
              resp.request().end();
            });
          });
        }));
    // Send head to the server and trigger the request handler
    req1
      .setChunked(true)
      .sendHead();

    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/2")
      .setHandler(onSuccess(resp -> {
        testComplete();
      }))
      .end();

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
    server.listen(testAddress, onSuccess(s -> {
      listenLatch.countDown();
    }));
    awaitLatch(listenLatch);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setPipelining(true).setKeepAlive(true));
    AtomicInteger connCount = new AtomicInteger();
    client.connectionHandler(conn -> connCount.incrementAndGet());
    HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/first")
      .setHandler(onFailure(err -> {

    }));
    req.reset(0);
    CountDownLatch respLatch = new CountDownLatch(2);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/second")
      .setHandler(onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      resp.endHandler(v -> {
        respLatch.countDown();
      });
    }))
      .exceptionHandler(this::fail)
      .end();
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/third")
      .setHandler(onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      resp.endHandler(v -> {
        respLatch.countDown();
      });
    }))
      .exceptionHandler(this::fail)
      .end();
    awaitLatch(doneLatch);
    assertEquals(Arrays.asList("/second", "/third"), responses);
    awaitLatch(respLatch);
    server.close();
    assertEquals(1, connCount.get());
  }

  @Test
  public void testClientConnectionExceptionHandler() throws Exception {
    server.requestHandler(req -> {
      NetSocket so = req.netSocket();
      so.write(Buffer.buffer(TestUtils.randomAlphaString(40) + "\r\n"));
    });
    startServer(testAddress);
    client.connectionHandler(conn -> {
      conn.exceptionHandler(err -> {
        testComplete();
      });
    });
    client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(resp -> {
      })
      .sendHead();
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
      req.response().end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(resp1 -> {
        client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
          .setHandler(resp2 -> { })
          .putHeader("the_header", TestUtils.randomAlphaString(10000))
          .sendHead();
      })
      .end();
    await();
  }

  @Test
  public void testServerExceptionHandler() throws Exception {
    Context serverCtx = vertx.getOrCreateContext();
    server.exceptionHandler(err -> {
      assertSame(serverCtx, Vertx.currentContext());
      assertTrue(err instanceof TooLongFrameException);
      testComplete();
    });
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress, serverCtx);
    client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(resp -> { })
      .putHeader("the_header", TestUtils.randomAlphaString(10000))
      .sendHead();
    await();
  }

  @Test
  public void testRandomPorts() {
    int numServers = 10;
    Set<Integer> ports = Collections.synchronizedSet(new HashSet<>());
    AtomicInteger count = new AtomicInteger();
    for (int i = 0;i < numServers;i++) {
      vertx.createHttpServer().requestHandler(req -> {
        req.response().end();
      }).listen(0, DEFAULT_HTTP_HOST, onSuccess(s -> {
        int port = s.actualPort();
        ports.add(port);
        client.get(port, DEFAULT_HTTP_HOST, "/somepath", resp -> {
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

    server.listen(testAddress, onSuccess(server -> {
      client
        .request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "some-uri")
        .setHandler(onSuccess(resp -> testComplete()))
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
      server.listen(testAddress, onSuccess(v -> {
        latch.countDown();
      }));
      awaitLatch(latch);
      client.close();
      // There might be a race between the request write and the request reset
      // so we do it on the context thread to avoid it
      vertx.runOnContext(v -> {
        client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setKeepAlive(keepAlive).setPipelining(pipelined));
        HttpClientRequest post = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(onFailure(err -> {
          }))
          .setChunked(true);
        post.write(TestUtils.randomBuffer(1024));
        assertTrue(post.reset());
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(resp -> {
            assertEquals(1, numReq.get());
            complete();
          })
          .end();
      });
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testResetKeepAliveClientRequest() throws Exception {
    waitFor(3);
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
              "host: localhost:8080\r\n" +
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
      server.listen(testAddress, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(false).setKeepAlive(true));
      AtomicInteger status = new AtomicInteger();
      HttpClientRequest req1 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .setHandler(resp -> {
          assertEquals(0, status.getAndIncrement());
        });
      client.connectionHandler(conn -> {
        conn.closeHandler(v -> {
          assertEquals(1, status.getAndIncrement());
          complete();
        });
      });
      req1.end();
      HttpClientRequest req2 = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .setHandler(onFailure(err -> complete()));
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
    CompletableFuture<Void> doReset = new CompletableFuture<>();
    server.close();
    NetServer server = vertx.createNetServer();
    AtomicInteger count = new AtomicInteger();
    server.connectHandler(so -> {
      assertEquals(0, count.getAndIncrement());
      Buffer total = Buffer.buffer();
      so.handler(buff -> {
        total.appendBuffer(buff);
        if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
            "host: localhost:8080\r\n" +
            "\r\n" +
            "POST /somepath HTTP/1.1\r\n" +
            "host: localhost:8080\r\n" +
            "\r\n")) {
          doReset.complete(null);
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
      server.listen(testAddress, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setPipelining(true).setKeepAlive(true));
      client.connectionHandler(conn -> {
        conn.closeHandler(v -> {
          complete();
        });
      });
      vertx.runOnContext(v1 -> {
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
          .setHandler(ar -> {
            // We may or not receive the response
          })
          .end();
        HttpClientRequest req2 = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
          .setHandler(onFailure(resp -> {
          }));
        req2.sendHead();
        doReset.thenAccept(v2 -> {
          assertTrue(req2.reset());
        });
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
                  "host: localhost:8080\r\n" +
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
                  "host: localhost:8080\r\n" +
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
      server.listen(testAddress, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setPipelining(pipelined).setKeepAlive(true));
      HttpClientRequest req1 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .setHandler(onFailure(err -> {}));
      if (pipelined) {
        AtomicInteger connCount = new AtomicInteger();
        client.connectionHandler(conn -> {
          if (connCount.getAndIncrement() == 0) {
            conn.closeHandler(v2 -> {
              client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
                .setHandler(onSuccess(resp -> {
                  assertEquals(200, resp.statusCode());
                  resp.bodyHandler(body -> {
                    assertEquals("Hello world", body.toString());
                    complete();
                  });
                }))
                .end();
            });
          }
        });
        req1.sendHead(v -> {
          assertTrue(req1.reset());
        });
      } else {
        req1.sendHead(v -> {
          assertTrue(req1.reset());
        });
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath").setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            assertEquals("Hello world", body.toString());
            complete();
          });
        }))
          .end();
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
                  "host: localhost:8080\r\n" +
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
                  "host: localhost:8080\r\n" +
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
      server.listen(testAddress, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setPipelining(pipelined).setKeepAlive(true));
      if (pipelined) {
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
          .setHandler(
          onSuccess(resp1 -> {
            resp1.handler(buff -> {
              // Since we pipeline we must be sure that the first request is closed before running a new one
              resp1.request().connection().closeHandler(v -> {
                client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath").setHandler(onSuccess(resp -> {
                  assertEquals(200, resp.statusCode());
                  resp.bodyHandler(body -> {
                    assertEquals("Hello world", body.toString());
                    complete();
                  });
                })).end();
              });
              resp1.request().reset();
            });
          })).end();
      } else {
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath").setHandler(
          onSuccess(resp -> {
            resp.handler(buff -> {
              resp.request().reset();
            });
          }))
          .end();
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath").setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            assertEquals("Hello world", body.toString());
            complete();
          });
        }))
          .end();
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
    CompletableFuture<Void> sendResponse = new CompletableFuture<>();
    try {
      AtomicInteger count = new AtomicInteger();
      AtomicBoolean closed = new AtomicBoolean();
      server.connectHandler(so -> {
        Buffer total = Buffer.buffer();
        switch (count.getAndIncrement()) {
          case 0:
            so.handler(buff -> {
              total.appendBuffer(buff);
              if (total.toString().equals("GET /1 HTTP/1.1\r\n" +
                  "host: localhost:8080\r\n" +
                  "\r\n")) {
                requestReceived.complete(null);
                sendResponse.whenComplete((v, err) -> {
                  so.write(Buffer.buffer(
                    "HTTP/1.1 200 OK\r\n" +
                      "Content-Length: 11\r\n" +
                      "\r\n" +
                      "Some-Buffer"
                  ));
                });
                so.closeHandler(v -> {
                  closed.set(true);
                });
              }
            });
            break;
          case 1:
            so.handler(buff -> {
              total.appendBuffer(buff);
              if (total.toString().equals("GET /2 HTTP/1.1\r\n" +
                  "host: localhost:8080\r\n" +
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
      server.listen(testAddress, onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setPipelining(pipelined).setKeepAlive(true));
      HttpClientRequest req1 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/1")
        .setHandler(onFailure(err -> {
        // We need a small delay before the server send the HTTP response
        // so the stream is reset before the response is received by the client
        vertx.setTimer(100, id -> {
          sendResponse.complete(null);
        });
      }));
      requestReceived.thenAccept(v -> {
        req1.reset();
      });
      if (pipelined) {
        AtomicInteger connCount = new AtomicInteger();
        client.connectionHandler(conn -> {
          if (connCount.getAndIncrement() == 0) {
            conn.closeHandler(v2 -> {
              client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/2")
                .setHandler(onSuccess(resp -> {
                assertEquals(200, resp.statusCode());
                resp.bodyHandler(body -> {
                  assertEquals("Hello world", body.toString());
                  complete();
                });
              }))
                .end();
            });
          }
        });
        req1.end();
      } else {
        req1.end();
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/2")
          .setHandler(onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
            resp.bodyHandler(body -> {
              assertEquals("Hello world", body.toString());
              complete();
            });
          }))
          .end();
      }
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testTooLongContentInHttpServerRequest() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    server.connectionHandler(conn -> {
      conn.exceptionHandler(error -> {
        assertEquals(IllegalArgumentException.class, error.getClass());
        testComplete();
      });
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    try {
      client.connect(testAddress, onSuccess(so -> {
        so.write("POST / HTTP/1.1\r\nContent-Length: 4\r\n\r\ntoolong\r\n");
      }));
      await();
    } finally {
      client.close();
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
      Handler<Throwable> handler = err -> {
        errors.add(err);
        if (errors.size() == 2) {
          errorsChecker.handle(errors);
          testComplete();
        }
      };
      req.exceptionHandler(handler);
      bodySender.handle(current.get());
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress, onSuccess(so -> {
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
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        resp.exceptionHandler(errorHandler);
      }))
      .end();
    await();
  }

  @Test
  public void testRequestTimeoutIsNotDelayedAfterResponseIsReceived() throws Exception {
    int n = 6;
    waitFor(n);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        HttpClient client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(n));
        for (int i = 0;i < n;i++) {
          AtomicBoolean responseReceived = new AtomicBoolean();
          client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
            .setHandler(resp -> {
            try {
              Thread.sleep(150);
            } catch (InterruptedException e) {
              fail(e);
            }
            responseReceived.set(true);
            // Complete later, if some timeout tasks have been queued, this will be executed after
            vertx.runOnContext(v -> complete());
          })
            .exceptionHandler(err -> {
            fail("Was not expecting to get a timeout after the response is received");
          })
            .setTimeout(500)
            .end();
        }
      }
    }, new DeploymentOptions().setWorker(true));
    await();
  }

  @Test
  public void testPerHostPooling() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setMaxPoolSize(1)
      .setKeepAlive(true)
      .setPipelining(false));
    testPerXXXPooling((i, handler) -> client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, "host" + i, "/somepath")
      .setHandler(handler)
      .setAuthority("host:8080")
      .putHeader("key", "host" + i), req -> req.getHeader("key"));
  }

  @Test
  public void testPerPeerPooling() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setMaxPoolSize(1)
        .setKeepAlive(true)
        .setPipelining(false));
    testPerXXXPooling((i, handler) -> client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(handler)
      .setAuthority("host" + i + ":8080"), HttpServerRequest::host);
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
    testPerXXXPooling((i, handler) -> client.request(HttpMethod.GET, 80, "host" + i, "/somepath")
      .setHandler(handler), HttpServerRequest::host);
  }

  private void testPerXXXPooling(BiFunction<Integer, Handler<AsyncResult<HttpClientResponse>>, HttpClientRequest> requestProvider, Function<HttpServerRequest, String> keyExtractor) throws Exception {
    // Even though we use the same server host, we pool per peer host
    waitFor(2);
    int numPeers = 3;
    int numRequests = 5;
    Map<String, HttpServerResponse> map = new HashMap<>();
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      String key = keyExtractor.apply(req);
      assertFalse(map.containsKey(key));
      map.put(key, req.response());
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
        HttpClientRequest req = requestProvider.apply(i, onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          if (remaining.decrementAndGet() == 0) {
            complete();
          }
        }));
        req.end();
      }
    }
    await();
  }

  @Test
  public void testSendFileFailsWhenClientClosesConnection() throws Exception {
    // 10 megs
    final File f = setupFile("file.pdf", TestUtils.randomUnicodeString(10 * 1024 * 1024));
    server.requestHandler(req -> {
      try {
        req.response().sendFile(f.getAbsolutePath(), ar -> {
          if (ar.failed()) {
            // Broken pipe
            testComplete();
          } else {
            fail(new Exception("It should not reach this point"));
          }
        });
      } catch (Exception e) {
        // this was the bug reported with issues/issue-80
        fail(e);
      }
    });
    startServer(testAddress);
    vertx.createNetClient().connect(testAddress, onSuccess(socket -> {
      socket.write("GET / HTTP/1.1\r\n\r\n");
      socket.close();
    }));
    await();
  }

  // Use a raw socket to check the body response is effectively empty (it could be an empty chunk)
  protected MultiMap checkEmptyHttpResponse(HttpMethod method, int sc, MultiMap reqHeaders) throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setStatusCode(sc);
      resp.headers().addAll(reqHeaders);
      resp.end();
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    try {
      CompletableFuture<MultiMap> result = new CompletableFuture<>();
      client.connect(testAddress, ar -> {
        if (ar.succeeded()) {
          NetSocket so = ar.result();
          so.write(
            method.name() + " / HTTP/1.1\r\n" +
              "Connection: close\r\n" +
              "\r\n");
          Buffer body = Buffer.buffer();
          so.exceptionHandler(result::completeExceptionally);
          so.handler(body::appendBuffer);
          so.endHandler(v -> {
            String content = body.toString();
            int idx = content.indexOf("\r\n\r\n");
            if (idx == content.length() - 4) {
              LinkedList<String> records = new LinkedList<>(Arrays.asList(content.substring(0, idx).split("\\r\\n")));
              String statusLine = records.removeFirst();
              assertEquals("HTTP/1.1 " + sc, statusLine.substring(0, statusLine.indexOf(' ', 9)));
              assertEquals("", content.substring(idx + 4));
              MultiMap respHeaders = HttpHeaders.headers();
              records.forEach(record -> {
                int index = record.indexOf(":");
                String value = record.substring(0, index);
                respHeaders.add(value.trim(), record.substring(index + 1).trim());
              });
              result.complete(respHeaders);
            } else {
              result.completeExceptionally(new Exception());
            }
          });
        } else {
          result.completeExceptionally(ar.cause());
        }
      });
      return result.get(20, TimeUnit.SECONDS);
    } finally {
      client.close();
    }
  }

  @Test
  public void testUnknownContentLengthIsSetToZeroWithHTTP_1_0() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.write("Some-String");
      resp.end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
        assertNull(resp.getHeader("Content-Length"));
        testComplete();
      }))
      .end();
    await();
  }

  @Test
  public void testPartialH2CAmbiguousRequest() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.POST, req.method());
      testComplete();
    });
    Buffer fullRequest = Buffer.buffer("POST /whatever HTTP/1.1\r\n\r\n");
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress, onSuccess(so -> {
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
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress, onSuccess(so -> {
      so.closeHandler(v -> {
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testIdleTimeoutInfiniteSkipOfControlCharactersState() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setIdleTimeout(1));
    server.requestHandler(req -> {
      testComplete();
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress, onSuccess(so -> {
      long id = vertx.setPeriodic(1, timerID -> {
        so.write(Buffer.buffer().setInt(0, 0xD));
      });
      so.closeHandler(v -> {
        vertx.cancelTimer(id);
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testCompressedResponseWithConnectionCloseAndNoCompressionHeader() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(2048));
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setCompressionSupported(true));
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(expected, buff);
          complete();
        });
      }))
      .putHeader("Connection", "close")
      .exceptionHandler(this::fail)
      .end();
    await();
  }

  @Test
  public void testKeepAliveTimeout() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    testKeepAliveTimeout(new HttpClientOptions().setMaxPoolSize(1).setKeepAliveTimeout(3), 1);
  }

  @Test
  public void testKeepAliveTimeoutHeader() throws Exception {
    AtomicBoolean sent = new AtomicBoolean();
    server.requestHandler(req -> {
      if (sent.compareAndSet(false, true)) {
        req.response().putHeader("keep-alive", "timeout=3").end();
      }
    });
    testKeepAliveTimeout(new HttpClientOptions().setMaxPoolSize(1).setKeepAliveTimeout(30), 1);
  }

  @Test
  public void testKeepAliveTimeoutHeaderReusePrevious() throws Exception {
    AtomicBoolean sent = new AtomicBoolean();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      if (sent.compareAndSet(false, true)) {
        resp.putHeader("keep-alive", "timeout=3");
      }
      resp.end();
    });
    testKeepAliveTimeout(new HttpClientOptions().setMaxPoolSize(1).setKeepAliveTimeout(30), 2);
  }

  @Test
  public void testKeepAliveTimeoutHeaderOverwritePrevious() throws Exception {
    AtomicBoolean sent = new AtomicBoolean();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      int timeout;
      if (sent.compareAndSet(false, true)) {
        timeout = 15;
      } else {
        timeout = 3;
      }
      resp.putHeader("keep-alive", "timeout=" + timeout);
      resp.end();
    });
    testKeepAliveTimeout(new HttpClientOptions().setMaxPoolSize(1).setKeepAliveTimeout(30), 2);
  }

  private void testKeepAliveTimeout(HttpClientOptions options, int numReqs) throws Exception {
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(options.setPoolCleanerPeriod(1));
    AtomicInteger respCount = new AtomicInteger();
    for (int i = 0;i < numReqs;i++) {
      int current = 1 + i;
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          respCount.incrementAndGet();
          if (current == numReqs) {
            long now = System.currentTimeMillis();
            resp.request().connection().closeHandler(v -> {
              long timeout = System.currentTimeMillis() - now;
              int delta = 500;
              int low = 3000 - delta;
              int high = 3000 + delta;
              assertTrue("Expected actual close timeout " + timeout + " to be > " + low, low < timeout);
              assertTrue("Expected actual close timeout " + timeout + " + to be < " + high, timeout < high);
              testComplete();
            });
          }
        }))
        .end();
    }
    await();
  }

  @Test
  public void testPoolNotExpiring() throws Exception {
    AtomicLong now = new AtomicLong();
    server.requestHandler(req -> {
      req.response().end();
      now.set(System.currentTimeMillis());
      vertx.setTimer(2000, id -> {
        req.connection().close();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setPoolCleanerPeriod(0).setKeepAliveTimeout(100));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
        resp.endHandler(v1 -> {
          resp.request().connection().closeHandler(v2 -> {
            long time = System.currentTimeMillis() - now.get();
            assertTrue("Was expecting " + time + " to be > 2000", time >= 2000);
            testComplete();
          });
        });
      }))
      .end();
    await();
  }

  @Test
  public void testPausedHttpServerRequestPauseTheConnectionAtRequestEnd() throws Exception {
    int numRequests = 20;
    waitFor(numRequests);
    server.requestHandler(req -> {
      int[] paused = new int[1];
      req.handler(buff -> {
        assertEquals("small", buff.toString());
        req.pause();
        paused[0]++;
        vertx.setTimer(10, id -> {
          paused[0]--;
          req.resume();
        });
      });
      req.endHandler(v -> {
        assertEquals(0, paused[0]);
        req.response().end();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1));
    for (int i = 0; i < numRequests; i++) {
      client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri")
        .setHandler(resp -> {
          complete();
        })
        .end("small");
    }
    await();
  }

  @Test
  public void testPausedHttpClientResponseUnpauseTheConnectionAtRequestEnd() throws Exception {
    testHttpClientResponsePause(resp -> {
      resp.handler(buff -> {
        // Pausing the request here should have no effect since it's the last chunk
        assertEquals("ok", buff.toString());
        resp.pause();
      });
    });
  }

  @Test
  public void testHttpClientResponsePauseIsIgnoredAtRequestEnd() throws Exception {
    testHttpClientResponsePause(resp -> {
      resp.endHandler(v -> {
        // Pausing the request in end handler should be a no-op
        resp.pause();
      });
    });
  }

  private void testHttpClientResponsePause(Handler<HttpClientResponse> h) throws Exception {
    server.requestHandler(req -> req.response().end("ok"));
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setKeepAlive(true));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp1 -> {
        h.handle(resp1);
        vertx.setTimer(10, timerId -> {
          // The connection should be resumed as it's ended
          client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
            .setHandler(onSuccess(resp2 -> {
              assertSame(resp1.request().connection(), resp2.request().connection());
              resp2.endHandler(v -> testComplete());
            }))
            .end();
        });
      }))
      .end();
    await();
  }

  @Test
  public void testPoolLIFOPolicy() throws Exception {
    List<HttpServerRequest> requests = new ArrayList<>();
    server.requestHandler(req -> {
      requests.add(req);
      switch (requests.size()) {
        case 2:
          requests.forEach(r -> r.response().end());
          break;
        case 3:
          req.response().end();
          break;
      }
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(2));
    // Make two concurrent requests and finish one first
    List<HttpConnection> connections = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch = new CountDownLatch(2);
    // Use one event loop to be sure about response ordering
    vertx.runOnContext(v0 -> {
      for (int i = 0;i < 2;i++) {
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri")
          .setHandler(onSuccess(resp -> {
          resp.endHandler(v1 -> {
            // Use runOnContext to be sure the connections is put back in the pool
            vertx.runOnContext(v2 -> {
              connections.add(resp.request().connection());
              latch.countDown();
            });
          });
        }))
          .end();
      }
    });
    awaitLatch(latch);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri")
      .setHandler(onSuccess(resp -> {
        assertSame(resp.request().connection(), connections.get(1));
        testComplete();
      }))
      .end();
    await();
  }

  @Test
  public void testHttpClientResponseThrowsExceptionInResponseHandler() throws Exception {
    testHttpClientResponseThrowsExceptionInHandler(null, (resp, failure) -> {
      throw failure;
    });
  }

  @Test
  public void testHttpClientResponseThrowsExceptionInChunkHandler() throws Exception {
    testHttpClientResponseThrowsExceptionInHandler("blah", (resp, failure) -> {
      resp.handler(chunk -> {
        throw failure;
      });
    });
  }

  @Test
  public void testHttpClientResponseThrowsExceptionInEndHandler() throws Exception {
    testHttpClientResponseThrowsExceptionInHandler(null, (resp, failure) -> {
      resp.endHandler(v -> {
        throw failure;
      });
    });
  }

  private void testHttpClientResponseThrowsExceptionInHandler(
    String chunk,
    BiConsumer<HttpClientResponse, RuntimeException> handler) throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      if (chunk != null) {
        resp.end(chunk);
      } else {
        resp.end();
      }
    });
    startServer(testAddress);
    int num = 50;
    waitFor(num);
    RuntimeException failure = new RuntimeException();
    for (int i = 0;i < num;i++) {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri")
        .setHandler(onSuccess(resp -> {
          ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
          ctx.exceptionHandler(err -> {
            if (err == failure) {
              complete();
            }
          });
          handler.accept(resp, failure);
        }))
        .end();
    }
    await();
  }

  @Test
  public void testConnectionCloseDuringShouldCallHandleExceptionOnlyOnce() throws Exception {
    CompletableFuture<Void> continuation = new CompletableFuture<>();
    server.requestHandler(req -> {
      req.response().setChunked(true).write("chunk");
      continuation.thenAccept(v -> {
        req.connection().close();
      });
    });
    AtomicInteger count = new AtomicInteger();
    startServer(testAddress);
    HttpClientRequest post = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(onSuccess(res -> {
      continuation.complete(null);
    }));
    post.setChunked(true);
    post.write(TestUtils.randomBuffer(10000));
    CountDownLatch latch = new CountDownLatch(1);
    post.exceptionHandler(x-> {
      if (count.incrementAndGet() == 1) {
        vertx.setTimer(100, id -> {
          latch.countDown();
        });
      }
    });
    // then stall until timeout and the exception handler will be called.
    awaitLatch(latch);
    assertEquals(count.get(), 1);
  }

  @Test
  public void testDeferredRequestEnd() throws Exception {
    server.requestHandler(req -> {
      req.pause();
      req.bodyHandler(body -> {
        assertTrue(req.isEnded());
        req.response().end(body);
      });
      vertx.setTimer(10, v -> {
        assertFalse(req.isEnded());
        req.resume();
      });
    });
    startServer(testAddress);
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1024));
    client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/").setHandler(onSuccess(resp -> {
      resp.bodyHandler(body -> {
        assertEquals(expected, body);
        testComplete();
      });
    }))
      .end(expected);
    await();
  }

  @Test
  public void testPipelinedWithResponseSent() throws Exception {
    int numReq = 10;
    waitFor(numReq * 2);
    AtomicInteger inflight = new AtomicInteger();
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      int val = count.getAndIncrement();
      assertEquals(val + 1, inflight.incrementAndGet());
      req.pause();
      req.response().end("" + val);
      req.bodyHandler(body -> {
        if (inflight.decrementAndGet() == 0) {
          assertEquals(numReq, count.get());
        }
        complete();
      });
      vertx.setTimer(1000, v -> {
        req.resume();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setMaxPoolSize(1).setKeepAlive(true));
    vertx.runOnContext(v -> {
      // Run on context so requests are enqueued with a predictable ordering
      for (int i = 0;i < numReq;i++) {
        String expected = "" + i;
        client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
          .setHandler(onSuccess(resp -> {
            resp.bodyHandler(body -> {
              assertEquals(expected, body.toString());
              complete();
            });
          }))
          .end(Buffer.buffer(TestUtils.randomAlphaString(1024)));
      }
    });
    await();
  }

  @Test
  public void testPipelinedWithPendingResponse() throws Exception {
    int numReq = 10;
    waitFor(numReq);
    AtomicInteger inflight = new AtomicInteger();
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      int val = count.getAndIncrement();
      assertEquals(0, inflight.getAndIncrement());
      vertx.setTimer(100, v -> {
        inflight.decrementAndGet();
        req.response().end("" + val);
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setMaxPoolSize(1).setKeepAlive(true));
    vertx.runOnContext(v -> {
      // Run on context so requests are enqueued with a predictable ordering
      for (int i = 0;i < numReq;i++) {
        String expected = "" + i;
        client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
          .setHandler(onSuccess(resp -> {
            resp.bodyHandler(body -> {
              assertEquals(expected, body.toString());
              complete();
            });
          }))
          .end(TestUtils.randomAlphaString(1024));
      }
    });
    await();
  }

  @Test
  public void testPipelinedPostRequestStartedByResponseSent() throws Exception {
    String chunk1 = TestUtils.randomAlphaString(1024);
    String chunk2 = TestUtils.randomAlphaString(1024);
    CountDownLatch latch2 = new CountDownLatch(1);
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      switch (count.getAndIncrement()) {
        case 0:
          req.pause();
          vertx.setTimer(500, id -> {
            req.resume();
          });
          req.endHandler(v -> {
            req.response().end();
          });
          break;
        case 1:
          latch2.countDown();
          req.bodyHandler(body -> {
            assertEquals(chunk1 + chunk2, body.toString());
            req.response().end();
          });
          break;
      }
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setMaxPoolSize(1).setKeepAlive(true));
    CountDownLatch latch1 = new CountDownLatch(1);
    client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(resp -> {
      })
      .end(TestUtils.randomAlphaString(1024), onSuccess(v -> {
        latch1.countDown();
      }));
    awaitLatch(latch1);
    HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(resp -> {
        testComplete();
      })
      .setChunked(true);
    req.write(chunk1);
    awaitLatch(latch2);
    req.end(chunk2);
    await();
  }

  @Test
  public void testBeginPipelinedRequestByResponseSentOnRequestCompletion() throws Exception {
    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        req.pause();
        vertx.setTimer(100, id -> {
          req.resume();
        });
      }
      req.endHandler(v -> {
        req.response().end();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setMaxPoolSize(1).setKeepAlive(true));
    client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", Buffer.buffer(TestUtils.randomAlphaString(1024)), resp -> {
    });
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(resp -> {
        testComplete();
      })
      .end();
    await();
  }

  @Test
  public void testBeginPipelinedRequestByResponseSentBeforeRequestCompletion() throws Exception {
    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        req.pause();
        vertx.setTimer(100, id1 -> {
          req.response().end();
          vertx.setTimer(100, id2 -> {
            req.resume();
          });
        });
      } else if (req.method() == HttpMethod.GET) {
        req.response().end();
      }
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setMaxPoolSize(1).setKeepAlive(true));
    client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(noOpHandler())
      .end(TestUtils.randomAlphaString(1024));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(resp -> {
      testComplete();
    }).end();
    await();
  }

  @Test
  public void testPipeliningQueueDelayed() throws Exception {
    waitFor(3);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setMaxPoolSize(1).setKeepAlive(true));
    HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(resp -> {
      complete();
    });
    req.sendHead();
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(resp -> {
        complete();
      }).end();
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(resp -> {
        complete();
      })
      .end();
    // Need to wait a little so requests 2 and 3 are appended to the first request
    Thread.sleep(300);
    // This will end request 1 and make requests 2 and 3 progress
    req.end();
    await();
  }

  @Test
  public void testHttpClientResponseBufferedWithPausedEnd() throws Exception {
    AtomicInteger i = new AtomicInteger();
    server.requestHandler(req -> {
      req.response().end("HelloWorld" + i.incrementAndGet());
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1).setKeepAlive(true));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp1 -> {
        // Response is paused but request is put back in the pool since the HTTP response fully arrived
        // but the response it's not yet delivered to the application as we pause the response
        resp1.pause();
        // Do a request on the same connection
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(onSuccess(resp2 -> {
            resp2.bodyHandler(body2 -> {
              // When the response arrives -> resume the first request
              assertEquals("HelloWorld2", body2.toString());
              resp1.bodyHandler(body1 -> {
                assertEquals("HelloWorld1", body1.toString());
                testComplete();
              });
              resp1.resume();
            });
          }))
          .end();
      }))
      .end();
    await();
  }

  @Test
  public void testHttpClientResumeConnectionOnResponseOnLastMessage() throws Exception {
    server.requestHandler(req -> req.response().end("ok"));
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setMaxPoolSize(1));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp1 -> {
        resp1.pause();
        // The connection resume is asynchronous and the end message will be received before connection resume happens
        resp1.resume();
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(onSuccess(resp2 -> {
            testComplete();
          }))
          .end();
      }))
      .end();
    await();
  }

  @Test
  public void testSetChunkedToFalse() throws Exception {
    server.requestHandler(req -> req.response().setChunked(false).end());
    startServer();
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(resp -> {
        testComplete();
      })
      .setChunked(false)
      .end();
    await();
  }

  @Test
  public void testHttpServerRequestShouldCallExceptionHandlerWhenTheClosedHandlerIsCalled() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), resp);
      sender.send();
      resp.closeHandler(v -> {
        Throwable failure = sender.close();
        if (failure != null) {
          fail(failure);
        } else {
          testComplete();
        }
      });
    });
    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri")
        .setHandler(onSuccess(resp -> {
          vertx.setTimer(1000, id -> {
            resp.request().connection().close();
          });
        }))
        .end();
    }));
    await();
  }

  @Test
  public void testHttpClientRequestShouldCallExceptionHandlerWhenTheClosedHandlerIsCalled() throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      vertx.setTimer(1000, id -> {
        req.response().close();
      });
    });
    startServer(testAddress);
    HttpClientRequest req = client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri")
      .setHandler(resp -> { })
      .setChunked(true);
    CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), req);
    AtomicBoolean connected = new AtomicBoolean();
    AtomicBoolean done = new AtomicBoolean();
    req.exceptionHandler(err -> {
      assertTrue(connected.get());
      Throwable failure = sender.close();
      if (failure != null) {
        fail(failure);
      } else if (done.compareAndSet(false, true)) {
        testComplete();
      }
    });
    req.sendHead(v -> {
      connected.set(true);
      sender.send();
    });
    await();
  }

  @Test
  public void testHeaderNameValidation() {
    for (char c : "\0\t\n\u000B\f\r ,:;=\u0080".toCharArray()) {
      try {
        HttpUtils.validateHeaderName(Character.toString(c));
        fail("Char 0x" + Integer.toHexString(c) + " should not be valid");
      } catch (IllegalArgumentException ignore) {
        // Ok
      }
    }
  }

  @Test
  public void testHeaderValueValidation() {
    List<String> invalid = Arrays.asList("\f", "\0", "\u000b", "\r\n3", "\r3", "\n3", "\n\r");
    for (String test : invalid) {
      try {
        HttpUtils.validateHeaderValue(test);
        fail("String \"" + test + "\" should not be valid");
      } catch (IllegalArgumentException e) {
        // Ok
      }
    }
    List<String> valid = Arrays.asList("\r\n\t", "\r\n ", "\n\t", "\n ");
    for (String test : valid) {
      HttpUtils.validateHeaderValue(test);
    }
  }

  @Test
  public void testChunkedServerResponse() {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      assertTrue(resp.isChunked());
      resp.write("the-chunk");
      vertx.setTimer(1, id -> {
        resp.end();
      });
    }).listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .setHandler(onSuccess(res -> {
          assertEquals("chunked", res.getHeader("transfer-encoding"));
          res.bodyHandler(body -> {
            assertEquals("the-chunk", body.toString());
            testComplete();
          });
        }))
        .end();
    }));
    await();
  }

  @Test
  public void testChunkedClientRequest() {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      assertEquals("chunked", req.getHeader("transfer-encoding"));
      req.bodyHandler(body -> {
        assertEquals("the-chunk", body.toString());
        req.response().end();
      });
    }).listen(testAddress, onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .setHandler(onSuccess(resp -> testComplete()))
        .setChunked(true);
      req.write("the-chunk");
      vertx.setTimer(1, id -> {
        req.end();
      });
    }));
    await();
  }

  @Test
  public void testClosingVertxCloseSharedServers() throws Exception {
    int numServers = 2;
    Vertx vertx = Vertx.vertx();
    List<HttpServerImpl> servers = new ArrayList<>();
    for (int i = 0;i < numServers;i++) {
      HttpServer server = vertx.createHttpServer(createBaseServerOptions()).requestHandler(req -> {

      });
      startServer(server);
      servers.add((HttpServerImpl) server);
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
  public void testHttpServerWithIdleTimeoutSendChunkedFile() throws Exception {
    // Does not pass reliably in CI (timeout)
    Assume.assumeFalse(vertx.isNativeTransportEnabled());
    int expected = 16 * 1024 * 1024; // We estimate this will take more than 200ms to transfer with a 1ms pause in chunks
    File sent = TestUtils.tmpFile(".dat", expected);
    server.close();
    server = vertx
      .createHttpServer(createBaseServerOptions().setIdleTimeout(400).setIdleTimeoutUnit(TimeUnit.MILLISECONDS))
      .requestHandler(
        req -> {
          req.response().sendFile(sent.getAbsolutePath());
        });
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .setHandler(onSuccess(resp -> {
        long now = System.currentTimeMillis();
        int[] length = {0};
        resp.handler(buff -> {
          length[0] += buff.length();
          resp.pause();
          vertx.setTimer(1, id -> {
            resp.resume();
          });
        });
        resp.exceptionHandler(this::fail);
        resp.endHandler(v -> {
          assertEquals(expected, length[0]);
          assertTrue(System.currentTimeMillis() - now > 1000);
          testComplete();
        });
      }))
      .end();
    await();
  }

  @Test
  public void testSendFilePipelined() throws Exception {
    int n = 4;
    waitFor(n);
    File sent = TestUtils.tmpFile(".dat", 16 * 1024);
    server.requestHandler(
      req -> {
        req.response().sendFile(sent.getAbsolutePath());
      });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setMaxPoolSize(1));
    for (int i = 0;i < n;i++) {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.exceptionHandler(this::fail);
          resp.bodyHandler(body -> {
            complete();
          });
        }))
        .end();
    }
    await();
  }
}
