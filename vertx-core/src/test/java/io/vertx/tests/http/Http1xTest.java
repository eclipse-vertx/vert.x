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

package io.vertx.tests.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.TooLongHttpHeaderException;
import io.vertx.core.Future;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.impl.Utils;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.core.CheckingSender;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.vertx.core.http.HttpMethod.PUT;
import static io.vertx.test.core.AssertExpectations.that;
import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class Http1xTest extends HttpTest {

  @Override
  protected HttpVersion clientAlpnProtocolVersion() {
    return HttpVersion.HTTP_1_1;
  }

  @Override
  protected HttpVersion serverAlpnProtocolVersion() {
    return HttpVersion.HTTP_1_1;
  }

  protected NetClientOptions createNetClientOptions() {
    return HttpOptionsFactory.createH2NetClientOptions();
  }

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
    assertEquals(options, options.setKeyCertOptions(keyStoreOptions));
    assertEquals(keyStoreOptions, options.getKeyCertOptions());

    assertNull(options.getTrustOptions());
    JksOptions trustStoreOptions = new JksOptions().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setTrustOptions(trustStoreOptions));
    assertEquals(trustStoreOptions, options.getTrustOptions());

    assertFalse(options.isTrustAll());
    assertEquals(options, options.setTrustAll(true));
    assertTrue(options.isTrustAll());

    assertTrue(options.isVerifyHost());
    assertEquals(options, options.setVerifyHost(false));
    assertFalse(options.isVerifyHost());

    rand = TestUtils.randomPositiveInt();

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

    assertFalse(options.isDecompressionSupported());
    assertEquals(options, options.setDecompressionSupported(true));
    assertEquals(true, options.isDecompressionSupported());

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

    Http2Settings initialSettings = randomHttp2Settings();
    assertEquals(new Http2Settings(), options.getInitialSettings());
    assertEquals(options, options.setInitialSettings(initialSettings));
    assertEquals(initialSettings, options.getInitialSettings());

    assertEquals(false, options.isUseAlpn());
    assertEquals(options, options.setUseAlpn(true));
    assertEquals(true, options.isUseAlpn());

    assertNull(options.getSslEngineOptions());
    assertEquals(options, options.setSslEngineOptions(new JdkSSLEngineOptions()));
    assertTrue(options.getSslEngineOptions() instanceof JdkSSLEngineOptions);

    List<HttpVersion> alpnVersions = Collections.singletonList(HttpVersion.HTTP_1_1);
    assertEquals(HttpClientOptions.DEFAULT_ALPN_VERSIONS, options.getAlpnVersions());
    assertEquals(options, options.setAlpnVersions(alpnVersions));
    assertEquals(alpnVersions, options.getAlpnVersions());

    assertEquals(true, options.isHttp2ClearTextUpgrade());
    assertEquals(options, options.setHttp2ClearTextUpgrade(false));
    assertEquals(false, options.isHttp2ClearTextUpgrade());

    assertEquals(null, options.getLocalAddress());


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
    assertEquals(options, options.setSslEngineOptions(new JdkSSLEngineOptions()));
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
    boolean decompressionSupported = rand.nextBoolean();
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
    options.setKeepAlive(keepAlive);
    options.setPipelining(pipelining);
    options.setPipeliningLimit(pipeliningLimit);
    options.setHttp2MultiplexingLimit(http2MultiplexingLimit);
    options.setHttp2ConnectionWindowSize(http2ConnectionWindowSize);
    options.setDecompressionSupported(decompressionSupported);
    options.setProtocolVersion(protocolVersion);
    options.setMaxChunkSize(maxChunkSize);
    options.setMaxInitialLineLength(maxInitialLineLength);
    options.setMaxHeaderSize(maxHeaderSize);
    options.setInitialSettings(initialSettings);
    options.setUseAlpn(useAlpn);
    options.setSslEngineOptions(sslEngine);
    options.setAlpnVersions(alpnVersions);
    options.setHttp2ClearTextUpgrade(h2cUpgrade);
    options.setLocalAddress(localAddress);
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
    } else if (copy.getTrustOptions() instanceof JksOptions) {
      JksOptions a = (JksOptions) options.getTrustOptions();
      JksOptions b = (JksOptions) copy.getTrustOptions();
      assertEquals(a.getPath(), b.getPath());
      assertEquals(a.getPassword(), b.getPassword());
      assertEquals(a.getValue(), b.getValue());
    } else if (copy.getTrustOptions() instanceof PfxOptions) {
      PfxOptions a = (PfxOptions) options.getTrustOptions();
      PfxOptions b = (PfxOptions) copy.getTrustOptions();
      assertEquals(a.getPath(), b.getPath());
      assertEquals(a.getPassword(), b.getPassword());
      assertEquals(a.getValue(), b.getValue());
    }
  }

  @Test
  public void testDefaultClientOptionsJson() {
    HttpClientOptions def = new HttpClientOptions();
    HttpClientOptions json = new HttpClientOptions(new JsonObject());
    assertEquals(def.isKeepAlive(), json.isKeepAlive());
    assertEquals(def.isPipelining(), json.isPipelining());
    assertEquals(def.getPipeliningLimit(), json.getPipeliningLimit());
    assertEquals(def.getHttp2MultiplexingLimit(), json.getHttp2MultiplexingLimit());
    assertEquals(def.getHttp2ConnectionWindowSize(), json.getHttp2ConnectionWindowSize());
    assertEquals(def.isVerifyHost(), json.isVerifyHost());
    assertEquals(def.isDecompressionSupported(), json.isDecompressionSupported());
    assertEquals(def.isTrustAll(), json.isTrustAll());
    assertEquals(def.getCrlPaths(), json.getCrlPaths());
    assertEquals(def.getCrlValues(), json.getCrlValues());
    assertEquals(def.getConnectTimeout(), json.getConnectTimeout());
    assertEquals(def.isTcpNoDelay(), json.isTcpNoDelay());
    assertEquals(def.isTcpKeepAlive(), json.isTcpKeepAlive());
    assertEquals(def.getSoLinger(), json.getSoLinger());
    assertEquals(def.isSsl(), json.isSsl());
    assertEquals(def.getProtocolVersion(), json.getProtocolVersion());
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
    boolean decompressionSupported = rand.nextBoolean();
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
      .put("decompressionSupported", decompressionSupported)
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
    assertEquals(keepAlive, options.isKeepAlive());
    assertEquals(pipelining, options.isPipelining());
    assertEquals(pipeliningLimit, options.getPipeliningLimit());
    assertEquals(http2MultiplexingLimit, options.getHttp2MultiplexingLimit());
    assertEquals(http2ConnectionWindowSize, options.getHttp2ConnectionWindowSize());
    assertEquals(decompressionSupported, options.isDecompressionSupported());
    assertEquals(protocolVersion, options.getProtocolVersion());
    assertEquals(maxChunkSize, options.getMaxChunkSize());
    assertEquals(maxInitialLineLength, options.getMaxInitialLineLength());
    assertEquals(maxHeaderSize, options.getMaxHeaderSize());
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
    } else if (copy.getTrustOptions() instanceof JksOptions) {
      JksOptions a = (JksOptions) options.getTrustOptions();
      JksOptions b = (JksOptions) copy.getTrustOptions();
      assertEquals(a.getPath(), b.getPath());
      assertEquals(a.getPassword(), b.getPassword());
      assertEquals(a.getValue(), b.getValue());
    } else if (copy.getTrustOptions() instanceof PfxOptions) {
      PfxOptions a = (PfxOptions) options.getTrustOptions();
      PfxOptions b = (PfxOptions) copy.getTrustOptions();
      assertEquals(a.getPath(), b.getPath());
      assertEquals(a.getPassword(), b.getPassword());
      assertEquals(a.getValue(), b.getValue());
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
  public void testPipeliningOrder() throws Exception {
    // Does not pass with IO_Uring
    Assume.assumeTrue(((VertxInternal)vertx).transport().getClass().getName().startsWith("io.vertx.core"));
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true).setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
    int requests = 100;

    AtomicInteger reqCount = new AtomicInteger(0);
    server.requestHandler(req -> {
      assertSame(Vertx.currentContext(), ((HttpServerRequestInternal)req).context());
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


    waitFor(requests);

    startServer(testAddress);

    vertx.setTimer(500, id -> {
      for (int count = 0; count < requests; count++) {
        int theCount = count;
        client
          .request(new RequestOptions(requestOptions)
              .setMethod(PUT)
              .putHeader("count", String.valueOf(theCount)))
          .compose(req -> req
            .send(Buffer.buffer("This is content " + theCount))
            .expecting(that(resp -> assertEquals(theCount, Integer.parseInt(resp.headers().get("count")))))
            .compose(resp -> resp
              .body()
              .expecting(that(buff -> assertEquals("This is content " + theCount, buff.toString())))))
          .onComplete(onSuccess(v -> complete()));
      }
    });

    await();

  }

  @Test
  public void testPipeliningLimit() throws Exception {
    int limit = 25;
    int requests = limit * 4;
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().
        setKeepAlive(true).
        setPipelining(true).
        setPipeliningLimit(limit), new PoolOptions().setHttp1MaxSize(1));
    AtomicInteger count = new AtomicInteger();
    String data = "GET /somepath HTTP/1.1\r\n" +
        "host: " + DEFAULT_HTTP_HOST_AND_PORT+ "\r\n" +
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

    server
      .listen(testAddress)
      .await(20, TimeUnit.SECONDS);

    AtomicInteger responses = new AtomicInteger();
    for (int i = 0;i < requests;i++) {
      client.request(new RequestOptions(requestOptions).setURI("/somepath"))
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          if (responses.incrementAndGet() == requests) {
            testComplete();
          }
        }));
    }
    await();
  }

  @Test
  @Repeat(times = 10)
  public void testCloseServerConnectionWithPendingMessages() throws Exception {
    int n = 5;
    server.requestHandler(req -> {
      vertx.setTimer(100, id -> {
        req.connection().close();
      });
    });
    startServer(testAddress);
    client.close();
    AtomicBoolean completed = new AtomicBoolean();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions().setPipelining(true))
      .with(new PoolOptions().setHttp1MaxSize(n))
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          if (completed.compareAndSet(false, true)) {
            testComplete();
          }
        });
      })
      .build();
    for (int i = 0; i < n * 2; i++) {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onFailure(resp -> {}));
    }
    await();
  }

  @Test
  public void testPipeliningFailure() throws Exception {
    int n = 5;
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true).setPipeliningLimit(n), new PoolOptions().setHttp1MaxSize(1));
    AtomicBoolean first = new AtomicBoolean(true);
    CompletableFuture<Void> latch = new CompletableFuture<>();
    server.requestHandler(req -> {
      if (first.compareAndSet(true, false)) {
        latch.whenComplete((v, err) -> {
          req.connection().close();
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
      if (requests.isEmpty() && succeeded.get() == n) {
        testComplete();
      }
    };
    for (int i = 0;i < n * 2;i++) {
      boolean countDown = i + 1 == n;
      client.request(new RequestOptions(requestOptions).setURI("/" + i)).onComplete(onSuccess(req -> {
        req
          .send().onComplete(ar -> {
            if (ar.succeeded()) {
              succeeded.incrementAndGet();
            }
            checkEnd.accept(req);
          });
        requests.add(req);
        if (countDown) {
          latch.complete(null);
        }
      }));
    }
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
      tcpClient.connect(testAddress).onComplete(onSuccess(so -> {
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
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
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
      client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(onSuccess(req -> {
        req.send(Buffer.buffer(TestUtils.randomAlphaString(16))).onComplete(onSuccess(resp -> {
          resp.endHandler(v -> {
            if (remaining.decrementAndGet() == 0) {
              testComplete();
            }
          });
        }));
      }));
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
          req.connection().close();
        }
      });
    });
    startServer(testAddress);
    Buffer requests = Buffer.buffer();
    for (int i = 0;i < n;i++) {
      requests.appendString("GET " + DEFAULT_TEST_URI + " HTTP/1.1\r\n\r\n");
    }
    NetClient client = vertx.createNetClient();
    client.connect(testAddress).onComplete(onSuccess(so -> {
      so.closeHandler(v -> testComplete());
      so.write(requests);
    }));
    await();
  }

  @Test
  public void testServerConnectionCloseBeforeRequestEnded() throws Exception {
    testServerConnectionClose(true);
  }

  @Test
  public void testServerConnectionCloseAfterRequestEnded() throws Exception {
    testServerConnectionClose(false);
  }

  private void testServerConnectionClose(boolean sendEarlyResponse) throws Exception {
    CompletableFuture<HttpServerRequest> requestLatch = new CompletableFuture<>();
    server.requestHandler(requestLatch::complete);
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress).onComplete(onSuccess(so -> {
      so.write(
        "PUT / HTTP/1.1\r\n" +
          "connection: close\r\n" +
          "content-length: 1\r\n" +
          "\r\n");
      requestLatch.whenComplete((req, err) -> {
        if (sendEarlyResponse) {
          req.response().end();
        } else {
          req.endHandler(v -> {
            req.response().end();
          });
        }
        so.write("A");
      });
      Buffer response = Buffer.buffer();
      so.handler(response::appendBuffer);
      so.closeHandler(v -> {
        assertTrue(response.toString().startsWith("HTTP/1.1 200 OK"));
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testServerConnectionCloseDoesNotProcessHTTPMessages() throws Exception {
    AtomicInteger requestCount = new AtomicInteger();
    server.requestHandler(req -> {
      requestCount.incrementAndGet();
      req.response().end();
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress).onComplete(onSuccess(so -> {
      so.write(
        "PUT / HTTP/1.1 \r\n" +
          "connection: close\r\n" +
          "content-length: 0\r\n" +
          "\r\n" + "PUT / HTTP/1.1 \r\n" +
          "content-length: 0\r\n" +
          "\r\n");
      Buffer response = Buffer.buffer();
      so.handler(response::appendBuffer);
      so.closeHandler(v -> {
        String s = response.toString();
        String predicate = "HTTP/1.1 200 OK";
        assertEquals(s.indexOf(predicate), s.lastIndexOf("HTTP/1.1 200 OK"));
        testComplete();
      });
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
    CountDownLatch firstCloseLatch = new CountDownLatch(1);
    server.close().onComplete(onSuccess(v -> firstCloseLatch.countDown()));
    // Make sure server is closed before continuing
    awaitLatch(firstCloseLatch);

    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(keepAlive).setPipelining(false), new PoolOptions().setHttp1MaxSize(poolSize));
    int requests = 100;

    // Start the servers
    HttpServer[] servers = new HttpServer[numServers];
    CountDownLatch startServerLatch = new CountDownLatch(numServers);
    Set<HttpServer> connectedServers = ConcurrentHashMap.newKeySet();
    for (int i = 0; i < numServers; i++) {
      HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT));
      server.requestHandler(req -> {
        connectedServers.add(server);
        req.response().end();
      });
      server.listen(testAddress).onComplete(onSuccess(s -> {
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
        client.request(requestOptions)
          .compose(HttpClientRequest::send)
          .onComplete(onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
            reqLatch.countDown();
          }));
      }
    });

    awaitLatch(reqLatch);

    //client.dispConnCount();
    assertEquals(expectedConnectedServers, connectedServers.size());

    CountDownLatch serverCloseLatch = new CountDownLatch(numServers);
    for (HttpServer server: servers) {
      server.close().onComplete(onSuccess(s -> {
        serverCloseLatch.countDown();
      }));
    }

    awaitLatch(serverCloseLatch);
  }

  @Test
  public void testPoolingKeepAliveAndPipelining() throws Exception {
    testPooling(true, true);
  }

  @Test
  public void testPoolingKeepAliveNoPipelining() throws Exception {
    testPooling(true, false);
  }

  @Test
  public void testPoolingNoKeepAliveNoPipelining() throws Exception {
    testPooling(false, false);
  }

  private void testPooling(boolean keepAlive, boolean pipelining) throws Exception {
    String path = "foo.txt";
    int numGets = 100;
    int maxPoolSize = 10;
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions()
      .setKeepAlive(keepAlive)
      .setPipelining(pipelining), new PoolOptions().setHttp1MaxSize(maxPoolSize)
    );

    server.requestHandler(req -> {
      String cnt = req.headers().get("count");
      req.response().headers().set("count", cnt);
      req.response().end();
    });

    startServer(testAddress);

    AtomicInteger cnt = new AtomicInteger(0);
    for (int i = 0; i < numGets; i++) {
      int theCount = i;
      client.request(new RequestOptions(requestOptions).setURI(path))
        .compose(req -> req.putHeader("count", String.valueOf(theCount))
          .send()
          .expecting(that(resp -> {
            resp.exceptionHandler(this::fail);
            assertEquals(200, resp.statusCode());
            assertEquals(theCount, Integer.parseInt(resp.headers().get("count")));
          }))
          .compose(HttpClientResponse::end))
        .onComplete(onSuccess(resp -> {
          if (cnt.incrementAndGet() == numGets) {
            testComplete();
          }
        }));
    }

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

    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(false), new PoolOptions().setHttp1MaxSize(1).setMaxWaitQueueSize(0));

    waitFor(2);

    server.requestHandler(req -> {
      assertEquals("/1", req.path());
      complete();
    });

    startServer(testAddress);

    client.request(new RequestOptions(requestOptions).setURI("/1")).onComplete(onSuccess(req -> {
      Future<HttpClientRequest> request = client.request(new RequestOptions(requestOptions).setURI("/1"));
      request.onComplete(onFailure(err -> {
        req.end();
        complete();
      }));
    }));
    await();
  }

  @Test
  public void testServerWebSocketIdleTimeout() {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST));
    WebSocketClient client = vertx.createWebSocketClient();
    server
      .webSocketHandler(ws -> {})
      .listen()
      .onComplete(onSuccess(v1 -> {
        client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
          .onComplete(onSuccess(ws -> {
            ws.closeHandler(v2 -> testComplete());
          }));
      }));

    await();
  }

  @Test
  public void testClientWebSocketIdleTimeout() {
    WebSocketClient client = vertx.createWebSocketClient(new WebSocketClientOptions().setIdleTimeout(1));
    server
      .webSocketHandler(ws -> {})
      .listen().onComplete(onSuccess(v1 -> {
      client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .onComplete(onSuccess(ws -> {
          ws.closeHandler(v2 -> testComplete());
        }));
    }));

    await();
  }

  @Test
  public void testSharedServersRoundRobin() throws Exception {
    client.close();
    server.close();
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(false), new PoolOptions().setHttp1MaxSize(1));
    int numServers = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2- 1;
    int numRequests = numServers * 100;

    List<HttpServer> servers = Collections.synchronizedList(new ArrayList<>());
    Set<HttpServer> connectedServers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    Map<HttpServer, Integer> requestCount = new ConcurrentHashMap<>();

    CountDownLatch latchConns = new CountDownLatch(numRequests);
    Set<Thread> threads = ConcurrentHashMap.newKeySet();
    Future<String> listenLatch = vertx.deployVerticle(() -> new AbstractVerticle() {
      Thread thread;
      @Override
      public void start(Promise<Void> startPromise) {
        HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
        servers.add(theServer);
        theServer.requestHandler(req -> {
          Thread current = Thread.currentThread();
          if (thread == null) {
            thread = current;
            threads.add(current);
          } else {
            assertSame(current, thread);
          }
          connectedServers.add(theServer);
          Integer cnt = requestCount.get(theServer);
          int icnt = cnt == null ? 0 : cnt;
          icnt++;
          requestCount.put(theServer, icnt);
          latchConns.countDown();
          req.response().end();
        }).listen(testAddress).onComplete(onSuccess(s -> {
          if (s.actualPort() > 0) {
            assertEquals(DEFAULT_HTTP_PORT, s.actualPort());
          }
          startPromise.complete();
        }));
      }
    }, new DeploymentOptions().setInstances(numServers));
    awaitFuture(listenLatch);


    // Create a bunch of connections
    CountDownLatch latchClient = new CountDownLatch(numRequests);
    for (int i = 0; i < numRequests; i++) {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(res -> latchClient.countDown());
    }

    assertTrue(latchClient.await(30, TimeUnit.SECONDS));
    assertTrue(latchConns.await(30, TimeUnit.SECONDS));

    assertEquals(numServers, connectedServers.size());
    for (HttpServer server : servers) {
      assertTrue(connectedServers.contains(server));
    }
    assertEquals(numServers, requestCount.size());
    assertEquals(requestCount.values().stream().mapToInt(i -> i).sum(), numRequests);
    assertEquals(IntStream.range(0, requestCount.size())
      .mapToObj(i -> numRequests / numServers)
      .collect(Collectors.toList()), new ArrayList<>(requestCount.values()));
    assertEquals(numServers, threads.size());

    CountDownLatch closeLatch = new CountDownLatch(numServers);

    for (HttpServer server : servers) {
      server
        .close()
        .onComplete(onSuccess(v -> closeLatch.countDown()));
    }

    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    // Have a server running on a different port to make sure it doesn't interact
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT + 1));
    awaitFuture(theServer.requestHandler(req -> fail("Should not process request")).listen());
    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host before hand to make sure it doesn't interact
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    awaitFuture(theServer.requestHandler(req -> fail("Should not process request")).listen());
    awaitFuture(theServer.close());

    Thread.sleep(500); // Let some time

    testSharedServersRoundRobin();
  }

  @Test
  public void testDefaultHttpVersion() {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });

    server.listen(testAddress).onComplete(onSuccess(s -> {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .compose(HttpClientResponse::body)
        .onComplete(onSuccess(v -> testComplete()));
    }));

    await();
  }

  @Test
  public void testIncorrectHttpVersion() throws Exception {
    NetServer server = vertx.createNetServer();
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.connectHandler(so -> {
      Buffer content = Buffer.buffer();
      so.handler(buff -> {
        content.appendBuffer(buff);
        if (content.toString().endsWith("\r\n\r\n")) {
          so.write(Buffer.buffer("HTTP/1.2 200 OK\r\nContent-Length:5\r\n\r\nHELLO"));
        }
      });
    }).listen(testAddress).onComplete(onSuccess(v -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    AtomicBoolean a = new AtomicBoolean();
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> conn.closeHandler(v -> testComplete()))
      .build();
    client.request(requestOptions)
      .compose(req -> req.putHeader("connection", "close").send())
      .onComplete(onFailure(err -> {
      if (a.compareAndSet(false, true)) {
        assertTrue("message " + err.getMessage() + " should contain HTTP/1.2", err.getMessage().contains("HTTP/1.2"));
      }
    }));
    await();
  }

  @Test
  public void testHttp11PersistentConnectionNotClosed() throws Exception {

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      assertNull(req.getHeader("Connection"));
      req.response().end();
      assertFalse(req.response().closed());
    });

    server.listen(testAddress).onComplete(onSuccess(s -> {
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setKeepAlive(true));
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertNull(resp.getHeader("Connection"));
            assertEquals(resp.getHeader("Content-Length"), "0");
            testComplete();
          });
        }));
    }));

    await();
  }

  @Test
  public void testHttp11NonPersistentConnectionClosed() throws Exception {

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      assertEquals(req.getHeader("Connection"), "close");
      req.response().end();
      assertTrue(req.response().closed());
    });

    server.listen(testAddress).onComplete(onSuccess(s -> {
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setKeepAlive(false));
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertEquals(resp.getHeader("Connection"), "close");
            testComplete();
          });
        }));
    }));

    await();
  }

  @Test
  public void testHttp10KeepAliveConnectionNotClosed() throws Exception {

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_0, req.version());
      assertEquals(req.getHeader("Connection"), "keep-alive");
      req.response().end();
      assertFalse(req.response().closed());
    });

    server.listen(testAddress).onComplete(onSuccess(s -> {
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0).setKeepAlive(true));
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertEquals(resp.getHeader("Connection"), "keep-alive");
            assertEquals(resp.getHeader("Content-Length"), "0");
            testComplete();
          });
        }));
    }));

    await();
  }

  @Test
  public void testHttp10RequestNonKeepAliveConnectionClosed() throws Exception {

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_0, req.version());
      assertNull(req.getHeader("Connection"));
      req.response().end();
      assertTrue(req.response().closed());
    });

    server.listen(testAddress).onComplete(onSuccess(s -> {
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0).setKeepAlive(false));
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertNull(resp.getHeader("Connection"));
            testComplete();
          });
        }));
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
    server.listen(testAddress).onComplete(onSuccess(v1 -> {
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
      for (int i = 0;i < 3;i++) {
        client.request(requestOptions).compose(req -> req
            .send()
          .expecting(that(resp -> assertEquals(200, resp.statusCode())))
            .compose(HttpClientResponse::end))
          .onComplete(onSuccess(v -> complete()));
      }
    }));
    await();
  }

  @Test
  public void requestAbsNoPort() {
    client.request(new RequestOptions().setAbsoluteURI("http://www.google.com"))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> testComplete()));
    await();
  }

  @Test
  public void testServerOptionsCopiedBeforeUse() throws Exception {
    server.close();
    HttpServerOptions options = new HttpServerOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT);
    server = vertx.createHttpServer(options);
    // Now change something - but server should still listen at previous port
    options.setPort(DEFAULT_HTTP_PORT + 1);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(res -> {
        assertEquals(200, res.statusCode());
        testComplete();
      }));
    await();
  }

  @Test
  public void testClientOptionsCopiedBeforeUse() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    HttpClientOptions options = new HttpClientOptions();
    client.close();
    client = vertx.createHttpClient(options);
    // Now change something - but server should ignore this
    options.setSsl(true);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(res -> {
        assertEquals(200, res.statusCode());
        testComplete();
      }));
    await();
  }

  @Test
  public void testRequestExceptionHandlerContext() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.connection().close();
    });
    startServer(testAddress);
    Context clientCtx = vertx.getOrCreateContext();
    clientCtx.runOnContext(v -> {
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions());
      client.request(requestOptions)
        .onComplete(onSuccess(req -> {
          req.exceptionHandler(err -> {
            assertSameEventLoop(clientCtx, Vertx.currentContext());
            complete();
          });
          req.response()
            .onComplete(onFailure(err -> {
              assertSameEventLoop(clientCtx, Vertx.currentContext());
              complete();
            }));
          req.sendHead();
        }));
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
    Buffer data = randomBuffer(1024 * 16);
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
      server.listen(testAddress).onComplete(onSuccess(s -> {
        assertSameEventLoop(serverCtx, Vertx.currentContext());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    CountDownLatch latch2 = new CountDownLatch(1);
    Context clientCtx = vertx.getOrCreateContext();
    clientCtx.runOnContext(v -> {
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(numReqs));
    });
    waitUntil(() -> client != null);
    // There should be a context per request
    List<ContextInternal> contexts = Stream.generate(() -> ((VertxInternal) vertx).createEventLoopContext())
      .limit(numReqs)
      .collect(Collectors.toList());
    Set<Thread> expectedThreads = new HashSet<>();
    for (Context ctx : contexts) {
      CompletableFuture<Thread> th = new CompletableFuture<>();
      ctx.runOnContext(v -> {
        th.complete(Thread.currentThread());
      });
      expectedThreads.add(th.get());
    }
    Set<Thread> threads = ConcurrentHashMap.newKeySet();
    for (int i = 0; i < numReqs; i++) {
      ContextInternal requestCtx = contexts.get(i);
      CompletableFuture<Long> cf = new CompletableFuture<>();
      String path = "/" + i;
      requestResumeMap.put(path, cf);
      requestCtx.runOnContext(v -> {
        Thread t = Thread.currentThread();
        client.request(new RequestOptions(requestOptions).setURI(path))
          .onComplete(onSuccess(req -> {
            assertSame(t, Thread.currentThread());
            req.setChunked(true)
              .exceptionHandler(this::fail)
              .response()
              .onComplete(onSuccess(resp -> {
                assertSameEventLoop(requestCtx, Vertx.currentContext());
                assertEquals(200, resp.statusCode());
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
                    assertEquals(expectedThreads, new HashSet<>(threads));
                    latch2.countDown();
                  }
                });
              }));
            req.drainHandler(v2 -> {
              assertSameEventLoop(requestCtx, Vertx.currentContext());
              req.end();
            });
            req.sendHead().onComplete(version -> {
              assertSameEventLoop(requestCtx, Vertx.currentContext());
              fill(data, req, cf::complete);
            });
          }));
      });
    }
    awaitLatch(latch2, 40, TimeUnit.SECONDS);
    // Close should be in own context
    new Thread(() -> {
      server.close().onComplete(onSuccess(v -> {
        ContextInternal closeContext = ((VertxInternal) vertx).getContext();
        assertFalse(contexts.contains(closeContext));
        assertNotSame(serverCtx, closeContext);
        assertFalse(contexts.contains(serverCtx));
        testComplete();
      }));
      server = null;
    }).start();

    await();
  }

  @Test
  public void testRequestHandlerNotCalledInvalidRequest() throws Exception {
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress);
    vertx.createNetClient(new NetClientOptions()).connect(testAddress).onComplete(onSuccess(socket -> {
      socket.closeHandler(r -> {
        testComplete();
      });
      socket.write("GET HTTP1/1\r\n");

      // trigger another write to be sure we detect that the other peer has closed the connection.
      socket.write("X-Header: test\r\n");
    }));
    await();
  }

  @Test
  public void testTwoServersSameAddressDifferentContext() throws Exception {
    vertx.deployVerticle(() -> new AbstractVerticle() {
      private HttpServer server;
        @Override
        public void start(Promise<Void> startPromise) {
          server = vertx.createHttpServer()
            .requestHandler(req -> req.response().end());
          server
            .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST)
            .<Void>mapEmpty()
            .onComplete(startPromise);
        }
      }, new DeploymentOptions().setInstances(2))
      .onComplete(onSuccess(id -> {
        testComplete();
      }));
    await();
  }

  /*
  Fix for https://bugs.eclipse.org/bugs/show_bug.cgi?id=475017
  Also see https://groups.google.com/forum/?fromgroups#!topic/vertx/N_wSoQlvMMs
   */
  @Test
  public void testPauseResumeClientResponse() throws Exception {
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
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient();
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
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
        }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setChunked(true)
        .response().onComplete(onFailure(err -> {
        }));
      int times = buffer.length() / 8192;
      send(req, buffer, 0, times);
    }));
    await();
  }

  private void send(HttpClientRequest req, Buffer buffer, int count, int times) {
    if (count < times) {
      req.write(buffer.slice(count * 8192, (count + 1) * 8192));
      vertx.runOnContext(v -> {
        send(req, buffer, count + 1, times);
      });
    } else {
      req.end();
    }
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
    waitFor(2);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }));
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }));
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
    waitFor(2);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 2;i++) {
      client.request(new RequestOptions(requestOptions).setMethod(PUT))
        .compose(req -> req
          .send(Buffer.buffer("1"))
          .expecting(that(resp -> assertEquals(200, resp.statusCode())))
          .compose(HttpClientResponse::end))
        .onComplete(onSuccess(v -> complete()));
    }
    await();
  }

  @Test
  public void testMultipleRecursiveCallsAndPipelining() throws Exception {
    int sendRequests = 100;
    AtomicInteger receivedRequests = new AtomicInteger();
    server.requestHandler(x -> {
        x.response().end("hello");
      });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions()
      .setKeepAlive(true)
      .setPipelining(true)
    );
    IntStream.range(0, 5).forEach(i -> recursiveCall(client, receivedRequests, sendRequests));
    await();
  }

  private void recursiveCall(HttpClient client, AtomicInteger receivedRequests, int sendRequests){
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(r -> {
        int numRequests = receivedRequests.incrementAndGet();
        if (numRequests == sendRequests) {
          testComplete();
        } else if (numRequests < sendRequests) {
          recursiveCall(client, receivedRequests, sendRequests);
        }
      });
  }

  @Ignore
  @Test
  public void testUnsupportedHttpVersion() throws Exception {
    testUnsupported("GET /someuri HTTP/1.7\r\nHost: localhost\r\n\r\n", false);
  }

  private void testUnsupported(String rawReq, boolean method) throws Exception {
    server
      .requestHandler(req -> {
        // Should never be called
        fail();
      });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    // Send a raw request
    client.connect(testAddress).onComplete(onSuccess(conn -> {
      conn.write(rawReq);
      Buffer respBuff = Buffer.buffer();
      conn.handler(respBuff::appendBuffer);
      conn.closeHandler(v -> {
        // Server should automatically close it after sending back 501
        assertTrue("Unexpected response " + respBuff, respBuff.toString().contains("501 Not Implemented"));
        client.close();
        testComplete();
      });
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
    }).listen(testAddress).onComplete(onSuccess(s -> {
      latch1.countDown();
    }));
    HttpServer server2 = vertx.createHttpServer().requestHandler(req -> {
      server2Count.incrementAndGet();
      req.response().end();
    });
    server2.listen(testAddress).onComplete(onSuccess(s -> {
      latch1.countDown();
    }));
    awaitLatch(latch1);

    HttpClient[] clients = new HttpClient[5];
    for (int i = 0;i < 5;i++) {
      clients[i] = vertx.createHttpClient(createBaseClientOptions());
    }
    for (int i = 0; i < 2; i++) {
      CountDownLatch latch2 = new CountDownLatch(1);
      clients[i].request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          latch2.countDown();
        }));
      awaitLatch(latch2);
    }

    // Now close server 2
    CountDownLatch latch3 = new CountDownLatch(1);
    server2.close().onComplete(onSuccess(v -> {
      latch3.countDown();
    }));
    awaitLatch(latch3);
    // Send some more requests
    for (int i = 0; i < 2; i++) {
      CountDownLatch latch2 = new CountDownLatch(1);
      clients[2 + i].request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          latch2.countDown();
        }));
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
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }));
    await();
  }

  @Test
  public void testServerMaxInitialLineLength() throws Exception {
    testServerMaxInitialLineLength(HttpServerOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH);
  }

  @Test
  public void testServerMaxInitialLineLengthOption() throws Exception {
    // 5017 = 5000 for longParam and 18=17+1 for the rest in the following line - "GET /?t=longParam HTTP/1.1"
    testServerMaxInitialLineLength(5018);
  }

  private void testServerMaxInitialLineLength(int maxInitialLength) throws Exception {
    String longParam = TestUtils.randomAlphaString(5000);
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setMaxInitialLineLength(maxInitialLength))
      .requestHandler(req -> {
      assertEquals(req.getParam("t"), longParam);
      req.response().end();
    });
    startServer(testAddress);
    vertx.createHttpClient()
      .request(new RequestOptions(requestOptions).setURI("/?t=" + longParam))
      .compose(req -> req.send().compose(resp -> {
        if (maxInitialLength > HttpServerOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH) {
          assertEquals(200, resp.statusCode());
          return resp.end();
        } else {
          assertEquals(414, resp.statusCode());
          return Future.future(p -> {
            resp.request().connection().closeHandler(v -> p.complete());
          });
        }
      }))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testServerInvalidHttpMessage() throws Exception {
    server.requestHandler(req -> {
        fail();
      });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient();
    client
      .request(new RequestOptions(requestOptions).setURI("/?ab c=1"))
      .onComplete(onSuccess(req -> req
        .send()
        .onComplete(onSuccess(resp -> {
          assertEquals(400, resp.statusCode());
          resp.request().connection().closeHandler(v -> {
            testComplete();
          });
        }))));
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
      server.listen(testAddress).onComplete(onSuccess(v -> {
        client.close();
        client = vertx.createHttpClient(new HttpClientOptions().setMaxInitialLineLength(6000));
        client
          .request(new RequestOptions(requestOptions).setURI("/?t=" + longParam))
          .compose(HttpClientRequest::send)
          .onComplete(onSuccess(resp -> {
            resp.bodyHandler(body -> {
              assertEquals("0123456789", body.toString());
              testComplete();
            });
          }));
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
    }).listen(testAddress).onComplete(onSuccess(res -> {
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxHeaderSize(10000));
      client
        .request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals(resp.getHeader("t"), longHeader);
          testComplete();
        }));
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
      }).listen(testAddress).onComplete(onSuccess(res -> {
        client.close();
        client = vertx.createHttpClient(new HttpClientOptions());
        client
          .request(requestOptions)
          .compose(req -> req.putHeader("t", longHeader).send())
          .onComplete(
            onSuccess(resp -> {
              if (maxHeaderSize > HttpServerOptions.DEFAULT_MAX_HEADER_SIZE) {
                assertEquals(200, resp.statusCode());
                testComplete();
              } else {
                assertEquals(431, resp.statusCode());
                resp.request().connection().closeHandler(v -> {
                  testComplete();
                });
              }
            }));
      }));

    await();
  }

  @Test
  public void testPipelinedInvalidHttpResponse() {

    waitFor(2);

    AtomicInteger count = new AtomicInteger(0);
    NetServer server  = vertx.createNetServer();
    String match = "GET /somepath HTTP/1.1\r\nhost: " + DEFAULT_HTTP_HOST_AND_PORT+ "\r\n\r\n";
    server.connectHandler(so -> {
      StringBuilder content = new StringBuilder();
      so.handler(buff -> {
        content.append(buff);
        while (content.toString().startsWith(match)) {
          content.delete(0, match.length());
          switch (count.getAndIncrement()) {
            case 0:
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
    }).listen(testAddress).onComplete(onSuccess(s -> {

      // We force two pipelined requests to check that the second request does not get stuck after the first failing
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true).setPipelining(true), new PoolOptions().setHttp1MaxSize(1));

      for (int i = 0;i < 2;i++) {
        AtomicBoolean failed = new AtomicBoolean();
        client.request(new RequestOptions(requestOptions).setURI("/somepath"))
          .compose(HttpClientRequest::send)
          .onComplete(onFailure(err -> {
            if (failed.compareAndSet(false, true)) {
              assertEquals(IllegalArgumentException.class, err.getClass()); // invalid version format
              complete();
            }
          }));
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
    server.close();

    NetServerOptions serverOptions = new NetServerOptions();

    CountDownLatch serverLatch = new CountDownLatch(1);
    vertx.createNetServer(serverOptions).connectHandler(connectHandler).listen(testAddress).onComplete(result -> {
      if (result.succeeded()) {
        serverLatch.countDown();
      } else {
        fail();
      }
    });

    awaitLatch(serverLatch);

    int poolSize = 5;

    HttpClientOptions clientOptions = new HttpClientOptions()
        .setDefaultHost("localhost")
        .setKeepAlive(true)
        .setPipelining(false);
    client.close();
    client = vertx.createHttpClient(clientOptions, new PoolOptions().setHttp1MaxSize(1));

    int requests = poolSize * 2 + 1;
    AtomicInteger count = new AtomicInteger(requests);

    for (int i = 0; i < requests; i++) {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.response().onComplete(onSuccess(resp -> {
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
      }));
    }

    await();
  }

  @Test
  public void testDoNotReuseConnectionWhenResponseEndsBeforeRequest() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
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

    client.request(new RequestOptions(requestOptions).setURI("/1")).onComplete(onSuccess(req -> {
      req.response().onComplete(
        onSuccess(resp -> {
          resp.endHandler(v1 -> {
            // End request after the response ended
            vertx.setTimer(100, v2 -> {
              req1Ended.set(true);
              req.end();
            });
          });
        }));
      // Send head to the server and trigger the request handler
      req
        .setChunked(true)
        .sendHead();
    }));

    client.request(new RequestOptions(requestOptions).setURI("/2"))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        testComplete();
      }));

    await();
  }

  @Test
  public void testRecyclePipelinedConnection() throws Exception {
    CountDownLatch doneLatch = new CountDownLatch(2);
    List<String> responses = new ArrayList<>();
    server.requestHandler(req-> {
      responses.add(req.path());
      req.response().end();
      doneLatch.countDown();
    });
    startServer(testAddress);
    client.close();
    AtomicInteger connCount = new AtomicInteger();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions().setPipelining(true).setKeepAlive(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(conn -> connCount.incrementAndGet())
      .build();
    CountDownLatch respLatch = new CountDownLatch(2);
    client.request(new RequestOptions(requestOptions).setURI("/first")).onComplete(onSuccess(req1 -> {
      req1.response().onComplete(onFailure(err -> {
        // Should never happen
      }));
      assertTrue(req1.reset(0).succeeded());
      for (String uri : Arrays.asList("/second", "/third")) {
        client
          .request(new RequestOptions(requestOptions).setURI(uri))
          .compose(req -> req
            .send()
            .expecting(that(resp -> assertEquals(200, resp.statusCode())))
            .compose(HttpClientResponse::end))
          .onComplete(onSuccess(v -> respLatch.countDown()));
      }
    }));
    awaitLatch(doneLatch);
    assertEquals(Arrays.asList("/second", "/third"), responses);
    awaitLatch(respLatch);
    server.close();
    assertEquals(1, connCount.get());
  }

  @Test
  public void testClientConnectionExceptionHandler() throws Exception {
    NetServer server = vertx.createNetServer();
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.connectHandler(so -> {
      so.write(Buffer.buffer(TestUtils.randomAlphaString(40) + "\r\n"));
    }).listen(testAddress).onComplete( onSuccess(v -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        conn.exceptionHandler(err -> {
          testComplete();
        });
      })
      .build();
    client.request(requestOptions)
      .onComplete(onSuccess(HttpClientRequest::sendHead));
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
    client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(resp1 -> {
        client.request(requestOptions)
          .onComplete(onSuccess(req -> {
            req.putHeader("the_header", TestUtils.randomAlphaString(10000));
            req.sendHead();
          }));
      });
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .putHeader("the_header", TestUtils.randomAlphaString(10000))
        .sendHead();
    }));
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
      }).listen(0, DEFAULT_HTTP_HOST).onComplete(onSuccess(s -> {
        int port = s.actualPort();
        ports.add(port);
        client.request(new RequestOptions()
          .setHost(DEFAULT_HTTP_HOST)
          .setPort(port))
          .onComplete(onSuccess(req -> {
            req.send().onComplete(onSuccess(resp -> {
              if (count.incrementAndGet() == numServers) {
                assertEquals(numServers, ports.size());
                testComplete();
              }
            }));
          }));
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
      assertEquals(DEFAULT_HTTP_HOST_AND_PORT, req.headers().get("host"));
      req.bodyHandler(buffer -> {
        assertEquals(expected, buffer.toString());
        req.response().end();
      });
    });

    startServer(testAddress);
    client
      .request(new RequestOptions(requestOptions)
        .setMethod(PUT)
        .putHeader("Content-Encoding", "gzip"))
      .compose(req -> req.send(Buffer.buffer(dataGzipped)))
      .onComplete(onSuccess(v -> testComplete()));

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
      server
        .listen(testAddress)
        .onComplete(onSuccess(v -> {
          latch.countDown();
        }));
      awaitLatch(latch);
      client.close();
      // There might be a race between the request write and the request reset
      // so we do it on the context thread to avoid it
      vertx.runOnContext(v -> {
        client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(keepAlive).setPipelining(pipelined), new PoolOptions().setHttp1MaxSize(1));
        client.request(new RequestOptions(requestOptions).setMethod(PUT))
          .onComplete(onSuccess(req -> {
            req.response().onComplete(onFailure(err -> {
            }));
            req.reset().onComplete(onSuccess(v2 -> {
              client.request(new RequestOptions(requestOptions).setURI("some-uri"))
                .compose(HttpClientRequest::send)
                .onComplete(resp -> {
                  assertEquals(1, numReq.get());
                  complete();
                });
            }));
          }));
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
              "host: " + DEFAULT_HTTP_HOST_AND_PORT +"\r\n" +
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
      server.listen(testAddress).onComplete(onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      AtomicInteger status = new AtomicInteger();
      client.close();
      client = vertx.httpClientBuilder()
        .with(new HttpClientOptions().setPipelining(false).setKeepAlive(true))
        .with(new PoolOptions().setHttp1MaxSize(1))
        .withConnectHandler(conn -> {
          conn.closeHandler(v -> {
            assertEquals(1, status.getAndIncrement());
            complete();
          });
        })
        .build();
      client.request(new RequestOptions(requestOptions).setURI("/somepath"))
        .onComplete(onSuccess(req1 -> {
          req1.send().onComplete(onSuccess(resp -> {
            assertEquals(0, status.getAndIncrement());
          }));
          client.request(requestOptions).onSuccess(req2 -> {
            req2
              .response().onComplete(onFailure(err -> complete()));
            req2.sendHead().onComplete(onSuccess(v -> {
              assertTrue(req2.reset().succeeded());
            }));
          });
      }));
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testResetPipelinedClientRequest() throws Exception {
    waitFor(3);
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
            "host: " + DEFAULT_HTTP_HOST_AND_PORT + "\r\n" +
            "\r\n" +
            "POST /somepath HTTP/1.1\r\n" +
            "host: " + DEFAULT_HTTP_HOST_AND_PORT + "\r\n" +
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
      server.listen(testAddress).onComplete(onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client = vertx.httpClientBuilder()
        .with(createBaseClientOptions().setPipelining(true).setKeepAlive(true))
        .with(new PoolOptions().setHttp1MaxSize(1))
        .withConnectHandler(conn -> {
          conn.closeHandler(v -> {
            complete();
          });
        })
        .build();
      vertx.runOnContext(v1 -> {
        client.request(new RequestOptions(requestOptions)
          .setURI("/somepath")
        ).compose(HttpClientRequest::send);
        client.request(new RequestOptions(requestOptions)
          .setURI("/somepath")
          .setMethod(HttpMethod.POST)
        ).onComplete(onSuccess(req2 -> {
          req2.response().onComplete(onFailure(resp -> complete()));
          req2.sendHead();
          doReset.thenAccept(v2 -> {
            assertTrue(req2.reset().succeeded());
          });
        }));
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
                  "host: " + DEFAULT_HTTP_HOST_AND_PORT + "\r\n" +
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
                  "host: " + DEFAULT_HTTP_HOST_AND_PORT  +"\r\n" +
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
      server.listen(testAddress).onComplete(onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions().setPipelining(pipelined).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
      client
        .request(requestOptions)
        .onComplete(onSuccess(req1 -> {
          if (pipelined) {
            HttpConnection conn = req1.connection();
            conn.closeHandler(v2 -> {
              client.request(new RequestOptions(requestOptions).setURI("/somepath"))
                .compose(req -> req
                  .send()
                  .expecting(that(resp -> assertEquals(200, resp.statusCode())))
                  .compose(HttpClientResponse::body))
                .onComplete(onSuccess(body -> {
                  assertEquals("Hello world", body.toString());
                  complete();
                }));
            });
            req1.sendHead().onComplete(v -> {
              assertTrue(req1.reset().succeeded());
            });
          } else {
            req1.sendHead().onComplete(v -> {
              assertTrue(req1.reset().succeeded());
            });
            client.request(new RequestOptions(requestOptions).setURI("/somepath"))
              .compose(req -> req
                .send()
                .expecting(that(resp -> assertEquals(200, resp.statusCode())))
                .compose(HttpClientResponse::body))
              .onComplete(onSuccess(body -> {
                assertEquals("Hello world", body.toString());
                complete();
              }));
          }
      }));
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
                  "host: " + DEFAULT_HTTP_HOST_AND_PORT + "\r\n" +
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
                  "host: " + DEFAULT_HTTP_HOST_AND_PORT + "\r\n" +
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
      server.listen(testAddress).onComplete(onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setPipelining(pipelined).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
      if (pipelined) {
        client.request(new RequestOptions(requestOptions).setURI("/somepath"))
          .onComplete(onSuccess(req1 -> {
          req1.send().onComplete(
            onSuccess(resp1 -> {
              resp1.handler(buff -> {
                // Since we pipeline we must be sure that the first request is closed before running a new one
                resp1.request().connection().closeHandler(v -> {
                  client.request(new RequestOptions(requestOptions).setURI("/somepath")).onComplete(onSuccess(req2 -> {
                    req2.send().onComplete(onSuccess(resp -> {
                      assertEquals(200, resp.statusCode());
                      resp.bodyHandler(body -> {
                        assertEquals("Hello world", body.toString());
                        complete();
                      });
                    }));
                  }));
                });
                resp1.request().reset();
              });
            }));
        }));
      } else {
        client.request(new RequestOptions(requestOptions).setURI("/somepath")).onComplete(onSuccess(req -> {
          req.send().onComplete(
            onSuccess(resp -> {
              resp.handler(buff -> {
                resp.request().reset();
              });
            }));
        }));
        client.request(new RequestOptions(requestOptions).setURI("/somepath")).onComplete(onSuccess(req -> {
          req.send().onComplete(onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
            resp.bodyHandler(body -> {
              assertEquals("Hello world", body.toString());
              complete();
            });
          }));
        }));
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
                  "host: " + DEFAULT_HTTP_HOST_AND_PORT + "\r\n" +
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
                  "host: " + DEFAULT_HTTP_HOST_AND_PORT + "\r\n" +
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
      server.listen(testAddress).onComplete(onSuccess(v -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.close();
      client = vertx.createHttpClient(createBaseClientOptions().setPipelining(pipelined).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
      client.request(new RequestOptions(requestOptions).setURI("/1"))
        .onComplete(onSuccess(req1 -> {
          requestReceived.thenAccept(v -> {
            req1.reset();
          });
          if (pipelined) {
            HttpConnection conn = req1.connection();
            conn.closeHandler(v2 -> {
              client.request(new RequestOptions(requestOptions).setURI("/2"))
                .compose(HttpClientRequest::send)
                .compose(resp -> {
                  assertEquals(200, resp.statusCode());
                  return resp.body();
                })
                .onComplete(onSuccess(body -> {
                  assertEquals("Hello world", body.toString());
                  complete();
                }));
            });
            req1.end();
          } else {
            req1.end();
            client.request(new RequestOptions(requestOptions).setURI("/2"))
              .compose(HttpClientRequest::send)
              .compose(resp -> {
                assertEquals(200, resp.statusCode());
                return resp.body();
              })
              .onComplete(onSuccess(body -> {
                assertEquals("Hello world", body.toString());
                complete();
              }));
          }
      }));
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
      client.connect(testAddress).onComplete(onSuccess(so -> {
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
      assertEquals(TooLongHttpHeaderException.class, errors.get(0).getClass());
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
    client.connect(testAddress).onComplete(onSuccess(so -> {
      current.set(so);
      so.write("POST /somepath HTTP/1.1\r\n");
      so.write("Transfer-Encoding: chunked\r\n");
      so.write("\r\n");
    }));
    await();
  }

  @Test
  public void testInvalidChunkInHttpClientResponse() throws Exception {
    NetServer server = vertx.createNetServer();
    CountDownLatch listenLatch = new CountDownLatch(1);
    CompletableFuture<Void> cont = new CompletableFuture<>();
    server.connectHandler(so -> {
      so.handler(buff -> {
        so.handler(null);
        so.write("HTTP/1.1 200 OK\r\n" + "Transfer-Encoding: chunked\r\n" + "\r\n");
      });
      cont.whenComplete((v,e) -> {
        so.write("invalid\r\n"); // Invalid chunk
      });
    }).listen(testAddress).onComplete(onSuccess(v -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    AtomicInteger status = new AtomicInteger();
    testHttpClientResponseDecodeError(cont::complete, err -> {
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
    NetServer server = vertx.createNetServer();
    CountDownLatch listenLatch = new CountDownLatch(1);
    CompletableFuture<Void> cont = new CompletableFuture<>();
    server.connectHandler(so -> {
      so.handler(buff -> {
        so.handler(null);
        so.write("HTTP/1.1 200 OK\r\n" +
          "Transfer-Encoding: chunked\r\n" +
          "\r\n" +
          "0\r\n"); // Empty chunk
        // Send large trailer
      });
      cont.whenComplete((v, e) -> {
        for (int i = 0;i < 2000;i++) {
          so.write("01234567");
        }
      });
    }).listen(testAddress).onComplete(onSuccess(v -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    AtomicInteger status = new AtomicInteger();
    testHttpClientResponseDecodeError(cont::complete, err -> {
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

  private void testHttpClientResponseDecodeError(Handler<Void> continuation, Handler<Throwable> errorHandler) throws Exception {
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          resp.exceptionHandler(errorHandler);
          continuation.handle(null);
        }));
      }));
    await();
  }

  @Test
  public void testEmptyHttpVersion() throws Exception {
    String expectedMessage;
    try {
      io.netty.handler.codec.http.HttpVersion.valueOf("");
      fail();
      return;
    } catch (IllegalArgumentException e) {
      expectedMessage = e.getMessage();
    }
    server.requestHandler(req -> {
      req.response().end();
    });
    server.connectionHandler(conn -> {
      conn.exceptionHandler(error -> {
        assertEquals(expectedMessage, error.getMessage());
        assertEquals(IllegalArgumentException.class, error.getClass());
        testComplete();
      });
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    try {
      client.connect(testAddress).onComplete(onSuccess(so -> {
        so.write("GET /\r\n\r\n");
      }));
      await();
    } finally {
      client.close();
    }
  }

  @Test
  public void testReceiveResponseWithNoRequestInProgress() throws Exception {
    NetServer server = vertx.createNetServer();
    CountDownLatch listenLatch = new CountDownLatch(1);
    Promise<Void> promise = Promise.promise();
    server.connectHandler(so -> {
      promise.future().onSuccess(v -> {
        so.write("HTTP/1.1 200 OK\r\n" +
          "Transfer-Encoding: chunked\r\n" +
          "\r\n");
      });
    }).listen(testAddress).onComplete(onSuccess(v -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        AtomicBoolean failed = new AtomicBoolean();
        conn.exceptionHandler(err -> {
          failed.set(true);
        });
        conn.closeHandler(v -> {
          assertTrue(failed.get());
          testComplete();
        });
      })
      .build();
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        promise.complete();
      }));
    await();
  }

  @Test
  public void testPerHostPooling() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setKeepAlive(true)
      .setPipelining(false), new PoolOptions().setHttp1MaxSize(1));
    testPerXXXPooling((i) -> client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost("host" + i)
      .setURI("/somepath"))
      .onSuccess(req -> req.putHeader("key", "host" + i)), req -> req.getHeader("key"));
  }

  @Test
  public void testPerPeerPooling() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setKeepAlive(true)
        .setPipelining(false), new PoolOptions().setHttp1MaxSize(1));
    testPerXXXPooling((i) -> client.request(new RequestOptions()
      .setServer(SocketAddress.inetSocketAddress(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST))
      .setPort(DEFAULT_HTTP_PORT)
      .setHost("host" + i)
      .setURI("/somepath")), req -> req.authority().toString());
  }

  @Test
  public void testPerPeerPoolingWithProxy() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
        .setKeepAlive(true)
        .setPipelining(false).setProxyOptions(new ProxyOptions()
            .setType(ProxyType.HTTP)
            .setHost(DEFAULT_HTTP_HOST)
            .setPort(DEFAULT_HTTP_PORT)), new PoolOptions().setHttp1MaxSize(1));
    testPerXXXPooling((i) -> client.request(new RequestOptions()
      .setPort(80)
      .setHost("host" + i)
      .setURI("/somepath")), req -> req.authority().toString());
  }

  private void testPerXXXPooling(Function<Integer, Future<HttpClientRequest>> requestProvider, Function<HttpServerRequest, String> keyExtractor) throws Exception {
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
        Future<HttpClientRequest> request = requestProvider.apply(i);
        request
          .onComplete(onSuccess(req -> {
            req.send().onComplete(onSuccess(resp -> {
              assertEquals(200, resp.statusCode());
              if (remaining.decrementAndGet() == 0) {
                complete();
              }
            }));
          }));
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
        req.response().sendFile(f.getAbsolutePath()).onComplete(onFailure(err -> {
          // Broken pipe
          testComplete();
        }));
      } catch (Exception e) {
        // this was the bug reported with issues/issue-80
        fail(e);
      }
    });
    startServer(testAddress);
    vertx.createNetClient().connect(testAddress).onComplete(onSuccess(socket -> {
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
      client.connect(testAddress).onComplete(ar -> {
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
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertNull(resp.getHeader("Content-Length"));
        testComplete();
      }));
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
    client.connect(testAddress).onComplete(onSuccess(so -> {
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
    client.connect(testAddress).onComplete(onSuccess(so -> {
      so.closeHandler(v -> {
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testTLSDisablesH2CHandlers() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get())
    ).connectionHandler(conn -> {
      Channel channel = ((Http1xServerConnection) conn).channel();
      for (Map.Entry<String, ChannelHandler> stringChannelHandlerEntry : channel.pipeline()) {
        ChannelHandler handler = stringChannelHandlerEntry.getValue();
        assertFalse(handler instanceof Http1xUpgradeToH2CHandler);
        assertFalse(handler instanceof Http1xOrH2CHandler);
      }
    }).requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setTrustAll(true)
      .setSsl(true));
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(v -> {
      testComplete();
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
    client.connect(testAddress).onComplete(onSuccess(so -> {
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
    client.request(requestOptions)
      .compose(req -> req
        .putHeader("Connection", "close")
        .send()
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(body -> {
      assertEquals(expected, body);
      testComplete();
    }));
    await();
  }

  @Test
  public void testKeepAliveTimeoutHeader() throws Exception {
    AtomicBoolean sent = new AtomicBoolean();
    server.requestHandler(req -> {
      if (sent.compareAndSet(false, true)) {
        req.response().putHeader("keep-alive", "timeout=3").end();
      }
    });
    testKeepAliveTimeout(new HttpClientOptions().setKeepAliveTimeout(30), new PoolOptions().setHttp1MaxSize(1), 1);
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
    testKeepAliveTimeout(new HttpClientOptions().setKeepAliveTimeout(30), new PoolOptions().setHttp1MaxSize(1), 2);
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
    testKeepAliveTimeout(new HttpClientOptions().setKeepAliveTimeout(30), new PoolOptions().setHttp1MaxSize(1), 2);
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
    client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0; i < numRequests; i++) {
      client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(onSuccess(req -> {
        req.send(Buffer.buffer("small")).onComplete(resp -> {
          complete();
        });
      }));
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
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp1 -> {
        h.handle(resp1);
        vertx.setTimer(10, timerId -> {
          // The connection should be resumed as it's ended
          client.request(requestOptions)
            .compose(HttpClientRequest::send)
            .onComplete(onSuccess(resp2 -> {
              assertSame(resp1.request().connection(), resp2.request().connection());
              resp2.endHandler(v -> testComplete());
            }));
        });
      }));
    }));
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
    client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(2));
    // Make two concurrent requests and finish one first
    List<HttpConnection> connections = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch = new CountDownLatch(2);
    // Use one event loop to be sure about response ordering
    vertx.runOnContext(v0 -> {
      for (int i = 0; i < 2; i++) {
        client.request(requestOptions)
          .onComplete(onSuccess(req -> {
            req.send().onComplete(onSuccess(resp -> {
              resp.endHandler(v1 -> {
                // Use runOnContext to be sure the connections is put back in the pool
                vertx.runOnContext(v2 -> {
                  connections.add(resp.request().connection());
                  latch.countDown();
                });
              });
            }));
          }));
      }
    });
    awaitLatch(latch);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertSame(resp.request().connection(), connections.get(1));
        testComplete();
      }));
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
      client.request(requestOptions)
        .onComplete(onSuccess(req -> {
          req.send().onComplete(onSuccess(resp -> {
            ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
            ctx.exceptionHandler(err -> {
              if (err == failure) {
                complete();
              }
            });
            handler.accept(resp, failure);
          }));
        }));
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
    CountDownLatch latch = new CountDownLatch(1);
    client.request(new RequestOptions(requestOptions).setMethod(PUT))
      .onComplete(onSuccess(put -> {
        put.response().onComplete(onSuccess(res -> {
          continuation.complete(null);
        }));
        put.setChunked(true);
        put.write(TestUtils.randomBuffer(10000));
        put.exceptionHandler(x-> {
          if (count.incrementAndGet() == 1) {
            vertx.setTimer(100, id -> {
              latch.countDown();
            });
          }
        });
    }));
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
    client.request(new RequestOptions(requestOptions).setMethod(PUT))
      .compose(req -> req
        .send(expected)
        .expecting(that(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(body -> {
        assertEquals(expected, body);
        testComplete();
      }));
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
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    vertx.runOnContext(v -> {
      // Run on context so requests are enqueued with a predictable ordering
      for (int i = 0;i < numReq;i++) {
        String expected = "" + i;
        client.request(new RequestOptions(requestOptions).setMethod(PUT))
          .compose(req -> req
            .send(TestUtils.randomAlphaString(1024))
            .expecting(that(resp -> assertEquals(200, resp.statusCode())))
            .compose(HttpClientResponse::body))
          .onComplete(onSuccess(body -> {
            assertEquals(expected, body.toString());
            complete();
          }));
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
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    vertx.runOnContext(v -> {
      // Run on context so requests are enqueued with a predictable ordering
      for (int i = 0;i < numReq;i++) {
        String expected = "" + i;
        client.request(new RequestOptions(requestOptions).setMethod(PUT))
          .compose(req -> req
            .send(TestUtils.randomAlphaString(1024))
            .expecting(that(resp -> assertEquals(200, resp.statusCode())))
            .compose(HttpClientResponse::body))
          .onComplete(onSuccess(body -> {
            assertEquals(expected, body.toString());
            complete();
          }));
      }
    });
    await();
  }

  @Test
  public void testPipelinedPostRequestStartedByResponseSent() throws Exception {
    String chunk1 = TestUtils.randomAlphaString(1024);
    String chunk2 = TestUtils.randomAlphaString(1024);
    Promise<Void> latch2 = Promise.promise();
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
          latch2.complete();
          req.bodyHandler(body -> {
            assertEquals(chunk1 + chunk2, body.toString());
            req.response().end();
          });
          break;
      }
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    CountDownLatch latch1 = new CountDownLatch(1);
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(onSuccess(req -> {
      req.end(TestUtils.randomAlphaString(1024)).onComplete( onSuccess(v -> {
        latch1.countDown();
      }));
    }));
    awaitLatch(latch1);
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(onSuccess(req -> {
      req.response().onComplete(resp -> {
          testComplete();
        });
      req.setChunked(true);
      req.write(chunk1);
      latch2.future().onComplete(onSuccess(v -> {
        req.end(chunk2);
      }));
    }));
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
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST)).onComplete(onSuccess(req -> {
      req.end(Buffer.buffer(TestUtils.randomAlphaString(1024)));
    }));
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testBeginPipelinedRequestByResponseSentBeforeRequestCompletion() throws Exception {
    server.requestHandler(req -> {
      if (req.method() == PUT) {
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
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(onSuccess(req -> {
      req.end(Buffer.buffer(TestUtils.randomAlphaString(1024)));
    }));
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        testComplete();
      }));
    }));
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
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.response().onComplete(onSuccess(resp -> complete()));
        req.sendHead();
        client.request(requestOptions).compose(HttpClientRequest::send).onComplete(resp -> complete());
        client.request(requestOptions).compose(HttpClientRequest::send).onComplete(resp -> complete());
        // Need to wait a little so requests 2 and 3 are appended to the first request
        vertx.setTimer(300, id -> {
          // This will end request 1 and make requests 2 and 3 progress
          req.end();
        });
    }));
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
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions).onComplete(onSuccess(req1 -> {
      req1.send().onComplete(onSuccess(resp1 -> {
        // Response is paused but request is put back in the pool since the HTTP response fully arrived
        // but the response it's not yet delivered to the application as we pause the response
        resp1.pause();
        // Do a request on the same connection
        client.request(requestOptions).onComplete(onSuccess(req2 -> {
          req2.send().onComplete(onSuccess(resp2 -> {
            resp2.bodyHandler(body2 -> {
              // When the response arrives -> resume the first request
              assertEquals("HelloWorld2", body2.toString());
              resp1.bodyHandler(body1 -> {
                assertEquals("HelloWorld1", body1.toString());
                testComplete();
              });
              resp1.resume();
            });
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testHttpClientResumeConnectionOnResponseOnLastMessage() throws Exception {
    server.requestHandler(req -> req.response().end("ok"));
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions).onComplete(onSuccess(req1 -> {
      req1.send().onComplete(onSuccess(resp1 -> {
        resp1.pause();
        // The connection resume is asynchronous and the end message will be received before connection resume happens
        resp1.resume();
        client.request(requestOptions)
          .compose(HttpClientRequest::send)
          .onComplete(onSuccess(resp2 -> {
            testComplete();
          }));
      }));
    }));
    await();
  }

  @Test
  public void testSetChunkedToFalse() throws Exception {
    server.requestHandler(req -> req.response().setChunked(false).end());
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .setChunked(false)
        .send()
        .compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testHttpServerRequestShouldCallExceptionHandlerWhenTheClosedHandlerIsCalled() throws Exception {
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
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        vertx.setTimer(1000, id -> {
          resp.request().connection().close();
        });
      }));
    await();
  }

  @Test
  public void testHttpClientRequestShouldCallExceptionHandlerWhenTheClosedHandlerIsCalled() throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      vertx.setTimer(1000, id -> {
        req.connection().close();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions)
      .setMethod(PUT)
    ).onComplete(onSuccess(req -> {
      req.setChunked(true);
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
      req.sendHead().onComplete(onSuccess(v -> {
        connected.set(true);
        sender.send();
      }));
    }));
    await();
  }

  @Test
  public void testHeaderNameValidation() {
    for (char c : "\u001c\u001d\u001e\u001f\0\t\n\u000b\f\r ,:;=\u0080".toCharArray()) {
      try {
        HttpUtils.validateHeaderName(Character.toString(c));
        fail("Char 0x" + Integer.toHexString(c) + " should not be valid");
      } catch (IllegalArgumentException ignore) {
        // Ok
      }
    }
  }

  private static List<String> invalidCharsForHeaders() {
    return Arrays.asList(
      "\u0000", "\u0001", "\u0002", "\u0003", "\u0004", "\u0005", "\u0006", "\u0007", "\u0008", /* HTAB */ /* LF */
      "\u000b", "\u000c", /* CR  */ "\u000e", "\u000f", "\u0010", "\u0011", "\u0012", "\u0013", "\u0014", "\u0015",
      "\u0016", "\u0017", "\u0018", "\u0019", "\u001a", "\u001b", "\u001c", "\u001d", "\u001e", "\u001f", /* SP */
      /* u0021-u007e */
      "\u007f"
      /* u0080-u00FF obsolete but still accepted for header value */
    );
  }

  @Test
  public void testHeaderValueValidation() {
    List<String> invalid = new ArrayList<>(invalidCharsForHeaders());
    invalid.addAll(Arrays.asList("\r\n3", "\r3", "\n3", "\n\r"));
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
  public void testHeaderNameStartsOrEndsWithControlChars() throws Exception {
    AtomicInteger invalidRequests = new AtomicInteger();
    server.invalidRequestHandler(req -> {
      invalidRequests.incrementAndGet();
      req.connection().close();
    });
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    try {
      List<String> chars = new ArrayList<>(invalidCharsForHeaders());
      chars.addAll(Arrays.asList(
        // Forbidden chars in a header name part
        // Note : '\t' and ' ' are forbidden too, but still used in the obsolete line folding syntax
        // Note : '\n', '\r' and ':' are forbidden too, but they are used as header boundaries, so it will never fail.
        "\"", "(", ")", ",", "/", ";", "<", ">", "=", "?", "@", "[", "]", "\\", "{", "}",
        // Outside the ASCII range
        "\u0080", "\u0090", "\u00a0", "\u00b0", "\u00c0", "\u00d0", "\u00e0", "\u00f0", "\u00ff"));
      boolean[] positions = { true, false };
      for (boolean position : positions) {
        for (String invalid : chars) {
          int current = invalidRequests.get();
          CountDownLatch latch = new CountDownLatch(1);
          client.connect(testAddress).onComplete(onSuccess(so -> {
            so.write("GET /some/path HTTP/1.1\r\n" + "Host: vertx.io\r\n" + (position ? invalid : "") + "Transfer-encoding" + (position ? "" : invalid) + ": chunked\r\n\r\n");
            so.closeHandler(v -> latch.countDown());
          }));
          awaitLatch(latch);
          waitUntil(() -> invalidRequests.get() == current + 1);
        }
      }
    } finally {
      client.close();
    }
  }

  @Test
  public void testInvalidHttpRequestHeaderResponse() throws Exception {
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    try {
      CountDownLatch latch = new CountDownLatch(1);
      Buffer response = Buffer.buffer();
      client.connect(testAddress).onComplete(onSuccess(so -> {
        so.write("GET /some/path HTTP/1.1\r\n" +
          "Host: vertx.io\r\n" +
          "\uD83D\uDE31: val\r\n" +
          "\r\n");
        so.handler(response::appendBuffer);
        so.closeHandler(v -> {
          latch.countDown();
        });
      }));
      awaitLatch(latch);
      assertTrue(response.toString().startsWith("HTTP/1.1 400 Bad Request\r\n"));
    } finally {
      client.close();
    }
  }

  @Test
  public void testInvalidHttpResponseHeader() throws Exception {
    waitFor(2);
    NetServer server = vertx.createNetServer().connectHandler(so -> {
      AtomicBoolean sent = new AtomicBoolean();
      so.handler(buff -> {
        if (sent.compareAndSet(false, true)) {
          so.write("" +
            "HTTP/1.1 200 OK\r\n" +
            "Content-Length: 0\r\n" +
            "\uD83D\uDE31: val\r\n" +
            "\r\n");
        }
      });
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen(testAddress).onComplete(onSuccess(s -> latch.countDown()));
    awaitLatch(latch);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.connection().exceptionHandler(err -> {
        assertEquals(IllegalArgumentException.class, err.getClass());
        complete();
      });
      req.send().onComplete(onFailure(err -> {
        assertEquals(IllegalArgumentException.class, err.getClass());
        complete();
      }));
    }));
    await();
  }

  @Test
  public void testChunkedServerResponse() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      assertTrue(resp.isChunked());
      resp.write("the-chunk");
      vertx.setTimer(1, id -> {
        resp.end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.send().compose(resp -> {
        assertEquals("chunked", resp.getHeader("transfer-encoding"));
        return resp.body();
      }))
      .onComplete(onSuccess(body -> {
        assertEquals("the-chunk", body.toString());
        testComplete();
      }));
    await();
  }

  @Test
  public void testChunkedClientRequest() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      assertEquals("chunked", req.getHeader("transfer-encoding"));
      req.bodyHandler(body -> {
        assertEquals("the-chunk", body.toString());
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(onSuccess(req -> {
      req.setChunked(true)
        .response()
        .onComplete(onSuccess(resp -> testComplete()));
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
    vertx.close().onComplete(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    servers.forEach(server -> {
      assertTrue(server.isClosed());
    });
  }

  @Test
  public void testRandomSharedPortInVerticle() {
    testRandomPortInVerticle(3, new int[]{-1}, 1);
  }

  @Test
  public void testRandomSharedPortsInVerticle() {
    testRandomPortInVerticle(3, new int[]{-1, -2}, 2);
  }

  @Test
  public void testRandomPortsInVerticle1() {
    testRandomPortInVerticle(3, new int[]{0}, 3);
  }

  @Test
  public void testRandomPortsInVerticle2() {
    testRandomPortInVerticle(3, new int[]{0, 0}, 6);
  }

  private void testRandomPortInVerticle(int instances, int[] bindPorts, int expectedPorts) {
    Assume.assumeTrue("Domain socket don't pass this test", testAddress.isInetSocket());
    waitFor(instances);
    Set<Integer> ports = Collections.synchronizedSet(new HashSet<>());
    vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startFuture) {
        List<Future<HttpServer>> futures = new ArrayList<>();
        for (int bindPort : bindPorts) {
          futures.add(vertx.createHttpServer().requestHandler(req -> {
            req.response().end();
          }).listen(bindPort, DEFAULT_HTTP_HOST));
        }
        Future.all(futures).onComplete(onSuccess(cf -> {
          futures.stream()
            .map(Future::result)
            .map(HttpServer::actualPort)
            .forEach(port -> {
            assertTrue(port > 0);
            ports.add(port);
          });
          startFuture.complete();
        }));
      }
    }, new DeploymentOptions().setInstances(instances)).onComplete(onSuccess(id -> {
      assertEquals(expectedPorts, ports.size());
      int port = ports.iterator().next();
      for (int i = 0;i < instances;i++) {
        client.request(new RequestOptions()
          .setHost(DEFAULT_HTTP_HOST)
          .setPort(port)).onComplete(onSuccess(req -> {
          req.send().onComplete(onSuccess(v -> {
            complete();
          }));
        }));
      }
    }));
    await();
  }

  @Test
  public void testHttpServerWithIdleTimeoutSendChunkedFile() throws Exception {
    // Does not pass reliably in CI (timeout)
    Assume.assumeTrue(!vertx.isNativeTransportEnabled() && !Utils.isWindows());
    int expected = 32 * 1024 * 1024;
    File file = TestUtils.tmpFile(".dat", expected);
    // Estimate the delay to transfer a file with a 1ms pause in chunks
    int delay = retrieveFileFromServer(file, createBaseServerOptions());
    // Now test with timeout relative to this delay
    int timeout = delay / 2;
    delay = retrieveFileFromServer(file, createBaseServerOptions().setIdleTimeout(timeout).setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
    assertTrue(delay > timeout);
  }

  private int retrieveFileFromServer(File file, HttpServerOptions options) throws Exception {
    server.close().await();
    server = vertx
      .createHttpServer(options)
      .requestHandler(
        req -> {
          req.response().sendFile(file.getAbsolutePath());
        });
    startServer(testAddress);
    long now = System.currentTimeMillis();
    Integer len = getFile().await();
    assertEquals((int)len, file.length());
    return (int) (System.currentTimeMillis() - now);
  }

  private Future<Integer> getFile() {
    int[] length = {0};
    return client.request(requestOptions)
      .compose(req -> req.send()
        .compose(resp -> {
          resp.handler(buff -> {
            length[0] += buff.length();
            resp.pause();
            vertx.setTimer(1, id -> {
              resp.resume();
            });
          });
          resp.exceptionHandler(this::fail);
          return resp.end();
        }))
      .map(v -> length[0]);
  }

  @Test
  public void testSendFilePipelined() throws Exception {
    int n = 2;
    waitFor(n);
    File sent = TestUtils.tmpFile(".dat", 16 * 1024);
    server.requestHandler(
      req -> {
        req.response().sendFile(sent.getAbsolutePath());
      });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < n;i++) {
      client.request(requestOptions)
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(onSuccess(body -> {
          complete();
        }));
    }
    await();
  }

  @Test
  public void testSendFileWithConnectionCloseHeader() throws Exception {
    String content = TestUtils.randomUnicodeString(1024 * 1024 * 2);
    sendFile("test-send-file.html", content, false,
      () -> client.request(requestOptions).map(req -> req.putHeader(HttpHeaders.CONNECTION, "close")));
  }

  @Test
  public void testResponseEndHandlersConnectionClose() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().endHandler(v -> complete());
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.putHeader(HttpHeaders.CONNECTION, "close");
        req.send().onComplete(onSuccess(res -> {
          assertEquals(200, res.statusCode());
          complete();
        }));
      }));
    await();
  }

  @Test
  public void testUnsolicitedHttpResponse() throws Exception {
    waitFor(2);
    NetServer server = vertx.createNetServer().connectHandler(so -> {
      AtomicBoolean sent = new AtomicBoolean();
      so.handler(buff -> {
        if (sent.compareAndSet(false, true)) {
          so.write("" +
            "HTTP/1.1 200 OK\r\n" +
            "Content-Length: 0\r\n" +
            "\r\n" +
            "HTTP/1.1 200 OK\r\n" +
            "\r\n");
          so.close();
        }
      });
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen(testAddress).onComplete(onSuccess(s -> latch.countDown()));
    awaitLatch(latch);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        AtomicBoolean failed = new AtomicBoolean();
        conn.exceptionHandler(err -> failed.set(true));
        conn.closeHandler(v -> {
          assertTrue(failed.get());
          complete();
        });
      })
      .build();
    client.request(requestOptions).compose(req -> req
        .send()
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(body -> complete()));
    await();
  }

  @Test
  public void testClientConnectionGracefulShutdown() throws Exception {
    waitFor(2);
    int numReq = 3;
    AtomicReference<HttpConnection> clientConnection = new AtomicReference<>();
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      if (count.getAndIncrement() == 0) {
        clientConnection
          .get()
          .shutdown()
          .onComplete(onSuccess(v -> complete()));
      }
      req.response().end();
    });
    startServer(testAddress);
    AtomicInteger responses = new AtomicInteger();
    client.close();
    AtomicReference<Handler<HttpConnection>> connectHandler = new AtomicReference<>();
    connectHandler.set(conn -> {
      AtomicBoolean failed = new AtomicBoolean();
      conn.exceptionHandler(err -> failed.set(true));
      conn.closeHandler(v -> {
        assertTrue(failed.get());
        complete();
      });
    });
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions().setPipelining(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(conn -> connectHandler.get().handle(conn))
      .build();
    vertx.runOnContext(v1 -> {
      connectHandler.set(conn -> {
        conn.closeHandler(v2 -> {
          assertEquals(3, responses.get());
          complete();
        });
        clientConnection.set(conn);
      });
      for (int i = 0;i < numReq;i++) {
        client.request(requestOptions).onComplete(onSuccess(req -> {
          req.send().onComplete(onSuccess(resp -> {
            responses.incrementAndGet();
          }));
        }));
      }
    });
    await();
  }

  @Test
  public void testClientConnectionGracefulShutdownWhenRequestCompletedAfterResponse() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(onSuccess(req -> {
      req.response().onComplete(onSuccess(resp -> {
          AtomicBoolean requestEnded = new AtomicBoolean();
          HttpConnection conn = req.connection();
          conn.closeHandler(v -> {
            assertTrue(requestEnded.get());
            complete();
          });
          conn.shutdown().onComplete(onSuccess(v -> complete()));
          resp.endHandler(v -> {
            vertx.runOnContext(v2 -> {
              requestEnded.set(true);
              req.end();
            });
          });
        }));
      req.setChunked(true).sendHead();
    }));
    await();
  }

  @Test
  public void testClientConnectionShutdownTimedOut() throws Exception {
    AtomicReference<HttpConnection> clientConnectionRef = new AtomicReference<>();
    int numReq = 3;
    waitFor(numReq + 2);
    server.requestHandler(req -> {
      HttpConnection clientConnection = clientConnectionRef.getAndSet(null);
      if (clientConnection != null) {
        long now = System.currentTimeMillis();
        clientConnection
          .shutdown(500, TimeUnit.MILLISECONDS)
          .onComplete(onSuccess(v -> {
            assertTrue(System.currentTimeMillis() - now >= 500L);
            complete();
        }));
      }
    });
    startServer(testAddress);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions().setPipelining(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(conn -> {
        clientConnectionRef.set(conn);
        long now = System.currentTimeMillis();
        conn.closeHandler(v -> {
          assertTrue(System.currentTimeMillis() - now >= 500L);
          complete();
        });
      })
      .build();
    for (int i = 0;i < numReq;i++) {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send().onComplete(onFailure(err -> complete()));
      }));
    }
    await();
  }

  @Test
  public void testClientConnectionShutdownNow() throws Exception {
    AtomicReference<HttpConnection> clientConnectionRef = new AtomicReference<>();
    waitFor(2);
    server.requestHandler(req -> {
      long now = System.currentTimeMillis();
      clientConnectionRef.get().close()
        .onComplete(onSuccess(v -> {
          assertTrue(System.currentTimeMillis() - now <= 2000L);
          complete();
      }));
    });
    startServer(testAddress);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions().setPipelining(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(clientConnectionRef::set)
      .build();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onFailure(err -> complete()));
    }));
    await();
  }

  @Test
  public void testServerConnectionGracefulShutdown() throws Exception {
    waitFor(3);
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      AtomicBoolean ended = new AtomicBoolean();
      conn.closeHandler(v -> {
        assertTrue(ended.get());
        complete();
      });
      Future<Void> shutdown = conn.shutdown(10, TimeUnit.SECONDS);
      shutdown.onComplete(onSuccess(v -> {
        assertTrue(ended.get());
        complete();
      }));
      vertx.setTimer(500, id -> {
        ended.set(true);
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
        .send()
        .expecting(that(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      )
      .onComplete(onSuccess(body -> complete()));
    await();
  }

  @Test
  public void testServerConnectionGracefulShutdownWithPipelinedRequest() throws Exception {
    waitFor(4);
    AtomicInteger count = new AtomicInteger();
    AtomicInteger status = new AtomicInteger();
    server.requestHandler(req -> {
      switch (count.getAndIncrement()) {
        case 0:
          vertx.setTimer(1, id1 -> {
            HttpConnection conn = req.connection();
            conn.closeHandler(v -> {
              assertEquals(2, status.get());
              complete();
            });
            Future<Void> shutdown = conn.shutdown(10, TimeUnit.SECONDS);
            shutdown.onComplete(onSuccess(v -> {
              assertEquals(2, status.get());
              complete();
            }));
            vertx.setTimer(500, id2 -> {
              assertEquals(0, status.getAndIncrement());
              req.response().end();
            });
          });
          break;
        case 1:
          assertEquals(1, status.getAndIncrement());
          req.response().end();
          break;
      }
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 2;i++) {
      client.request(requestOptions).compose(req -> req
          .send()
          .expecting(that(resp -> assertEquals(200, resp.statusCode())))
          .compose(HttpClientResponse::body)
        )
        .onComplete(onSuccess(body -> complete()));
    }
    await();
  }

  @Test
  public void testServerConnectionShutdownTimedOut() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      long now = System.currentTimeMillis();
      conn.shutdown(1, TimeUnit.SECONDS);
      conn.closeHandler(v -> {
        assertTrue(System.currentTimeMillis() - now >= 1000);
        complete();
      });
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onFailure(body -> complete()));
    await();
  }

  @Test
  public void testClientNetSocketPooling() throws Exception {
    int maxPoolSize = 5; // Default
    int num = 6;
    waitFor(num);
    server.requestHandler(req -> {
      req.toNetSocket().onComplete(onSuccess(so -> {
        vertx.setTimer(200, id -> {
          so.close();
        });
      }));
    });
    startServer();
    AtomicInteger count = new AtomicInteger();
    for (int i = 0;i < num;i++) {
      client.request(new RequestOptions()
        .setMethod(HttpMethod.CONNECT)
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI(DEFAULT_TEST_URI)
      ).onComplete(onSuccess(req -> {
        req.connect().onComplete(onSuccess(resp -> {
          NetSocket sock = resp.netSocket();
          int val = count.incrementAndGet();
          assertTrue("Expected " + val + " <= " + maxPoolSize, val <= maxPoolSize);
          sock.closeHandler(v -> {
            count.decrementAndGet();
            complete();
          });
        }));
      }));
    }
    await();
  }

  @Test
  public void testHttpUpgrade() {
    testHttpConnect(new RequestOptions(requestOptions).setMethod(HttpMethod.GET).addHeader(HttpHeaders.CONNECTION, "UpGrAdE"), 101);
  }

  @Test
  public void testServerResponseReset() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().reset();
    });
    startServer(testAddress);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> conn.closeHandler(v -> complete()))
      .build();
    client.request(requestOptions).compose(HttpClientRequest::send)
      .onComplete(onFailure(err -> {
        complete();
    }));
    await();
  }

  @Test
  public void testNetSocketUpgradeSuccess() {
    testNetSocketUpgradeSuccess(null);
  }

  @Test
  public void testNetSocketUpgradeSuccessWithPayload() {
    testNetSocketUpgradeSuccess(Buffer.buffer("the-payload"));
  }

  private void testNetSocketUpgradeSuccess(Buffer payload) {
    server.requestHandler(req -> {
      req.body().onComplete(onSuccess(body -> {
        if (payload != null) {
          assertEquals(payload, body);
        }
        req.response().setStatusCode(101);
        req.toNetSocket().onComplete(onSuccess(so -> {
          so.handler(buff -> {
            assertEquals("ping", buff.toString());
            so.write("pong");
            so.close();
          });
        }));
      }));
    });
    server.listen(testAddress).onComplete(onSuccess(s -> {
      RequestOptions request = new RequestOptions(requestOptions)
        .setMethod(HttpMethod.GET)
        .addHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE);
      if (payload != null) {
        request.addHeader(HttpHeaders.CONTENT_LENGTH, "" + payload.length());
      }
      client.request(request
      ).onComplete(onSuccess(req -> {
        req.connect().onComplete(onSuccess(resp -> {
          NetSocket so = resp.netSocket();
          so.write("ping");
          so.handler(buff -> {
            assertEquals("pong", buff.toString());
            so.close().onComplete(onSuccess(v -> {
              testComplete();
            }));
          });
        }));
        if (payload != null) {
          req.end(payload);
        }
      }));
    }));
    await();
  }

  @Test
  public void testClientEventLoopSize() throws Exception {
    Assume.assumeTrue("Domain socket don't pass this test", testAddress.isInetSocket());
    int size = 4;
    int maxPoolSize = size + 2;
    List<HttpServerRequest> requests = new ArrayList<>();
    server.requestHandler(req -> {
      requests.add(req);
      if (requests.size() == maxPoolSize) {
        requests.forEach(_req -> _req.response().end());
      } else if (requests.size() > maxPoolSize) {
        req.response().end();
      }
    });
    startServer();
    client.close();
    List<EventLoop> eventLoops = Collections.synchronizedList(new ArrayList<>());
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .with(new PoolOptions()
        .setHttp1MaxSize(maxPoolSize)
        .setEventLoopSize(size))
      .withConnectHandler(conn -> eventLoops.add(((ContextInternal)Vertx.currentContext()).nettyEventLoop()))
      .build();
    List<Future<Buffer>> futures = new ArrayList<>();
    for (int i = 0;i < size * 2;i++) {
      futures.add(client
        .request(requestOptions)
        .compose(request -> request
          .send()
          .compose(HttpClientResponse::body)));
    }
    Future.all(futures).onComplete(onSuccess(v -> {
      assertEquals(maxPoolSize, eventLoops.size());
      assertEquals(size, new HashSet<>(eventLoops).size());
      testComplete();
    }));
    await();
  }

  @Test
  public void testServerResponseChunkedSend() throws Exception {
    testServerResponseSend(true);
  }

  @Test
  public void testClientCloseWaiters() throws Exception {
    int num = 4;
    waitFor(num);
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      latch.countDown();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < num;i++) {
      int val = i;
      client
        .request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onFailure(err -> {
        if (val == 0) {
          assertEquals("Connection was closed", err.getMessage());
        } else {
          assertTrue("Expected " + err.getMessage() + " to contain with <closed>", err.getMessage().contains("closed"));
        }
        complete();
      }));
      if (i == 0) {
        awaitLatch(latch);
      }
    }
    awaitFuture(client.close());
    await();
  }

  @Test
  public void testClientShutdownClose() throws Exception {
    int num = 4;
    waitFor(num);
    AtomicReference<HttpServerRequest> ref = new AtomicReference<>();
    server.requestHandler(req -> {
      ref.compareAndSet(null, req);
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < num;i++) {
      int val = i;
      client.request(requestOptions)
        .compose(request -> request.send()
          .compose(HttpClientResponse::end))
        .onComplete(ar -> {
          if (val == 0) {
            assertTrue(ar.succeeded());
          } else {
            assertTrue(ar.failed());
            assertTrue(ar.cause().getMessage().contains("closed"));
          }
          complete();
        });
      if (i == 0) {
        assertWaitUntil(() -> ref.get() != null);
      }
    }
    Future<Void> shutdown = client.shutdown(2, TimeUnit.SECONDS);
    ref.get().response().end("hello");
    awaitFuture(shutdown);
    await();
  }

  @Test
  public void testServerShutdownClose() throws Exception {
    int num = 4;
    waitFor(num);
    AtomicInteger inflight = new AtomicInteger();
    List<Long> closures = Collections.synchronizedList(new ArrayList<>());
    long now = System.currentTimeMillis();
    server.requestHandler(req -> {
      inflight.incrementAndGet();
      HttpConnection conn = req.connection();
      conn.shutdownHandler(v -> {
        // SHOULD SEND RESP TO TEST
      });
      conn.closeHandler(v -> {
        closures.add(System.currentTimeMillis());
      });
    });
    startServer(testAddress);
    for (int i = 0;i < num;i++) {
      client.request(requestOptions)
        .compose(request -> request.send()
          .compose(HttpClientResponse::end))
        .onComplete(onFailure(err -> {
          complete();
        }));
    }
    assertWaitUntil(() -> inflight.get() == 4);
    Future<Void> shutdown = server.shutdown(2, TimeUnit.SECONDS);
    awaitFuture(shutdown);
    assertTrue(System.currentTimeMillis() - now > 2000);
    assertWaitUntil(() -> closures.size() == 4);
    for (Long ts : closures) {
      assertTrue(ts - now > 2000);
    }
    await();
  }

  @Test
  public void testEmptyHostHeader() throws Exception {
    testEmptyHostPortionOfHostHeader("", -1);
  }

  @Test
  public void testEmptyHostPortionOfHostHeader() throws Exception {
    testEmptyHostPortionOfHostHeader(":" + DEFAULT_HTTP_PORT, DEFAULT_HTTP_PORT);
  }

  private void testEmptyHostPortionOfHostHeader(String hostHeader, int expectedPort) throws Exception {
    server.requestHandler(req -> {
      assertEquals("", req.authority().host());
      assertTrue(((HttpServerRequestInternal) req).isValidAuthority());
      assertEquals("", req.authority().host());
      assertEquals(expectedPort, req.authority().port());
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions().setServer(testAddress).putHeader(HttpHeaderNames.HOST, hostHeader))
      .compose(req -> req
        .send()
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testMissingHostHeader() throws Exception {
    server.requestHandler(req -> {
      assertEquals(null, req.authority());
      assertFalse(((HttpServerRequestInternal) req).isValidAuthority());
      testComplete();
    });
    startServer(testAddress);
    NetClient nc = vertx.createNetClient();
    nc.connect(testAddress).onComplete(onSuccess(so -> {
      so.write("GET / HTTP/1.1\r\n\r\n");
    }));
    await();
  }

  @Test
  public void testCanUpgradeToWebSocket() throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.GET)
      .putHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)
      .putHeader(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
    doTestCanUpgradeToWebSocket(config, true);
  }

  @Test
  public void testCanUpgradeToWebSocketFirefox() throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.GET)
      .putHeader(HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE + ", " + HttpHeaders.UPGRADE)
      .putHeader(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
    doTestCanUpgradeToWebSocket(config, true);
  }

  @Test
  public void testCannotUpgradePostRequestToWebSocket() throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.POST)
      .putHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)
      .putHeader(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
    doTestCanUpgradeToWebSocket(config, false);
  }

  @Test
  public void testCannotUpgradeToWebSocketIfConnectionHeaderIsMissing() throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.GET)
      .putHeader(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
    doTestCanUpgradeToWebSocket(config, false);
  }

  @Test
  public void testCannotUpgradeToWebSocketIfUpgradeDoesNotContainWebsocket() throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.GET)
      .putHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)
      .putHeader(HttpHeaders.UPGRADE, "foo");
    doTestCanUpgradeToWebSocket(config, false);
  }

  private void doTestCanUpgradeToWebSocket(UnaryOperator<RequestOptions> config, boolean shouldSucceed) throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      if (req.canUpgradeToWebSocket()) {
        resp.headers()
          .set(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)
          .set(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
        req.toNetSocket().onComplete(onSuccess(sock -> {
          sock.write(Buffer.buffer("foo"));
        }));
      } else {
        resp.setStatusCode(500).end();
      }
    });
    startServer(testAddress);
    client.request(config.apply(new RequestOptions(requestOptions))).onComplete(onSuccess(req -> {
      req.response().onComplete(onSuccess(resp -> {
        if (shouldSucceed) {
          assertEquals(101, resp.statusCode());
          resp.netSocket().handler(buffer -> {
            assertEquals("foo", buffer.toString());
            testComplete();
          });
        } else {
          assertEquals(500, resp.statusCode());
          testComplete();
        }
      }));
      req.send();
    }));
    await();
  }

  @Test
  public void testDoNotRecycleWhenRequestIsNotEnded() throws Exception {
    waitFor(2);
    server.requestHandler(request -> {
      HttpServerResponse resp = request.response();
      resp.end().onComplete(onSuccess(v -> {
        request.connection().close();
      }));
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 2;i++) {
      int val = i;
      Future<HttpClientRequest> fut = client.request(requestOptions);
      fut.onComplete(ar -> {
        switch (val) {
          case 0:
            assertTrue(ar.succeeded());
            HttpClientRequest req = ar.result();
            req.sendHead();
            req.response().onComplete(onSuccess(resp -> {
              complete();
            }));
            break;
          case 1:
            assertTrue(ar.succeeded());
            complete();
            break;
        }
      });
    }
    await();
  }

  @Test
  public void testRecycleConnectionOnRequestEnd() throws Exception {
    int numRequests = 10;
    waitFor(numRequests);
    server.requestHandler(request -> {
      request.response().end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < numRequests;i++) {
      Future<HttpClientRequest> fut = client.request(requestOptions);
      fut.onComplete(onSuccess(request -> {
        request.setChunked(true).sendHead();
        request.response().compose(HttpClientResponse::body).onComplete(onSuccess(v -> {
          vertx.setTimer(10, id -> request.end());
          complete();
        }));
      }));
    }
    await();
  }

  @Test
  public void testFailPendingRequestAllocationWhenConnectionIsClosed() throws Exception {
    waitFor(2);
    server.requestHandler(request -> {
      HttpServerResponse resp = request.response();
      resp.end().onComplete(onSuccess(v -> {
        request.connection().close();
      }));
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 2;i++) {
      int val = i;
      Future<HttpClientRequest> fut = client.request(requestOptions);
      fut.onComplete(ar -> {
        switch (val) {
          case 0:
            assertTrue(ar.succeeded());
            HttpClientRequest req = ar.result();
            req.sendHead();
            req.response().onComplete(onSuccess(resp -> {
              complete();
            }));
            break;
          case 1:
            assertTrue(ar.failed());
            complete();
            break;
        }
      });
    }
    await();
  }

  @Ignore
  @Test
  public void testWorkerSlowDrain() throws Exception {
    server.requestHandler(req -> {
      req.handler(chunk -> {
        try {
          Thread.sleep(8);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });
    });
    startServer(testAddress, ((VertxInternal)vertx).createWorkerContext());
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
      req.setChunked(true);
      vertx.setPeriodic(10, id -> {
        if (req.writeQueueFull()) {
          vertx.cancelTimer(id);
        } else {
          req.write("chunk");
        }
      });
    }));

    await();
  }

  @Test
  public void testServerRollingUpdate() throws Exception {

    class WebServer extends AbstractVerticle {
      HttpServer server;
      final String msg;
      WebServer(String msg) {
        this.msg = msg;
      }
      @Override
      public void start(Promise<Void> startPromise) {
        server = vertx.createHttpServer().requestHandler(req -> {
          req
            .connection()
            .shutdownHandler(v -> {
              vertx.setTimer(500, id -> {
                req.response().end();
              });
            });
          req.response().setChunked(true).write(msg);
        });
        server
          .listen(8080, "localhost")
          .<Void>mapEmpty()
          .onComplete(startPromise);
      }
      @Override
      public void stop(Promise<Void> stopPromise) {
        server
          .shutdown()
          .<Void>mapEmpty()
          .onComplete(stopPromise);
      }
    }

    List<WebServer> servers = new ArrayList<>();
    for (int i = 0;i < 3;i++) {
      WebServer server = new WebServer("server-" + i);
      awaitFuture(vertx.deployVerticle(server));
      servers.add(server);
    }

    Set<String> responses = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < 3;i++) {
      client.request(new RequestOptions()
        .setPort(8080)
        .setHost("localhost")).onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          resp.handler(buff -> {
            responses.add(buff.toString());
          });
        }));
      }));
    }

    assertWaitUntil(() -> responses.size() == 3);
    Set<String> expected = new HashSet<>();
    expected.add("server-0");
    expected.add("server-1");
    expected.add("server-2");
    assertEquals(expected, responses);

    long now = System.currentTimeMillis();
    awaitFuture(vertx.undeploy(servers.remove(0).deploymentID()));
    assertTrue(System.currentTimeMillis() - now >= 500);

    WebServer server = new WebServer("server-" + 3);
    awaitFuture(vertx.deployVerticle(server));
    servers.add(server);

    responses.clear();
    for (int i = 0;i < 3;i++) {
      client.request(new RequestOptions()
        .setPort(8080)
        .setHost("localhost")).onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          resp.handler(buff -> {
            responses.add(buff.toString());
          });
        }));
      }));
    }

    assertWaitUntil(() -> responses.size() == 3);
    expected.remove("server-0");
    expected.add("server-3");
    assertEquals(expected, responses);
  }

}
