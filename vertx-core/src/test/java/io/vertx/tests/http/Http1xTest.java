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

import io.netty.channel.*;
import io.netty.channel.Channel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.TooLongHttpHeaderException;
import io.vertx.core.Future;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpResponseHead;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.headers.Http1xHeaders;
import io.vertx.core.http.impl.tcp.Http1xOrH2CHandler;
import io.vertx.core.http.impl.http1.Http1ServerConnection;
import io.vertx.core.http.impl.http2.codec.Http1xUpgradeToH2CHandler;
import io.vertx.core.impl.SysProps;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.WriteStream;
import io.vertx.core.transport.Transport;
import io.vertx.test.core.*;
import io.vertx.test.fakedns.Host;
import io.vertx.test.fakedns.Hosts;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.tls.Cert;
import org.junit.*;

import java.time.Duration;
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
import static io.vertx.test.core.VertxTestBase.TRANSPORT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.Assert.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class Http1xTest extends HttpTest {

  public Http1xTest() {
    super(HttpConfig.Http1x.DEFAULT);
  }

  @Test
  // Regression test for https://github.com/eclipse-vertx/vert.x/issues/6038
  public void testResponseBodyAfterResponseEnd() throws Exception {
    server.requestHandler(req -> req.response().setStatusCode(204).end());
    startServer(testAddress);
    Buffer body = client.request(requestOptions)
      .compose(HttpClientRequest::send)
      // Ensure body() is invoked after response end has been processed.
      .compose(resp -> vertx.timer(50).compose(v -> resp.body()))
      .timeout(5, TimeUnit.SECONDS)
      .await();
    assertEquals(0, body.length());
  }

  @Test
  // Regression test for https://github.com/eclipse-vertx/vert.x/issues/6038
  public void testResponseEndAfterResponseEnd() throws Exception {
    server.requestHandler(req -> req.response().setStatusCode(204).end());
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      // Ensure end() is invoked after response end has been processed.
      .compose(resp -> vertx.timer(50).compose(v -> resp.end()))
      .timeout(5, TimeUnit.SECONDS)
      .await();
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

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_UPGRADE_MAX_CONTENT_LENGTH, options.getHttp2UpgradeMaxContentLength());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setHttp2UpgradeMaxContentLength(rand));
    assertEquals(rand, options.getHttp2UpgradeMaxContentLength());
    assertEquals(options, options.setHttp2UpgradeMaxContentLength(-1));
    assertEquals(-1, options.getHttp2UpgradeMaxContentLength());

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
    int http2UpgradeMaxContentLength = TestUtils.randomPositiveInt();
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
    options.setHttp2UpgradeMaxContentLength(http2UpgradeMaxContentLength);
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
    assertEquals(def.getHttp2UpgradeMaxContentLength(), json.getHttp2UpgradeMaxContentLength());
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
    boolean verifyHost = rand.nextBoolean();
    int maxPoolSize = TestUtils.randomPositiveInt();
    boolean keepAlive = rand.nextBoolean();
    boolean pipelining = rand.nextBoolean();
    int pipeliningLimit = TestUtils.randomPositiveInt();
    int http2MaxPoolSize = TestUtils.randomPositiveInt();
    int http2MultiplexingLimit = TestUtils.randomPositiveInt();
    int http2ConnectionWindowSize = TestUtils.randomPositiveInt();
    int http2UpgradeMaxContentLength = TestUtils.randomPositiveInt();
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
      .put("verifyHost", verifyHost)
      .put("maxPoolSize", maxPoolSize)
      .put("keepAlive", keepAlive)
      .put("pipelining", pipelining)
      .put("pipeliningLimit", pipeliningLimit)
      .put("http2MaxPoolSize", http2MaxPoolSize)
      .put("http2MultiplexingLimit", http2MultiplexingLimit)
      .put("http2ConnectionWindowSize", http2ConnectionWindowSize)
      .put("http2UpgradeMaxContentLength", http2UpgradeMaxContentLength)
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
    assertEquals(tcpUserTimeout, options.getTcpUserTimeout());
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
    assertEquals(http2UpgradeMaxContentLength, options.getHttp2UpgradeMaxContentLength());
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
        Assert.fail();
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
      .put("tcpUserTimeout", tcpUserTimeout)
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
    assertEquals(tcpUserTimeout, options.getTcpUserTimeout());
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
        Assert.fail();
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
  public void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(Checkpoint checkpoint) throws Exception {
    testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(checkpoint, 0);
  }

  // Extra tests

  @Test
  public void testPipeliningOrder(Checkpoint checkpoint) throws Exception {
    Assume.assumeFalse(TRANSPORT == Transport.IO_URING);
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
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


    CountDownLatch latch = checkpoint.asLatch(requests);

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
          .onComplete(TestUtils.onSuccess(v -> latch.countDown()));
      }
    });
  }

  @Test
  public void testPipeliningLimit(Checkpoint checkpoint) throws Exception {
    int limit = 25;
    int requests = limit * 4;
    client = vertx.createHttpClient(new HttpClientOptions().
        setKeepAlive(true).
        setPipelining(true).
        setPipeliningLimit(limit), new PoolOptions().setHttp1MaxSize(1));
    AtomicInteger count = new AtomicInteger();
    String data = "GET /somepath HTTP/1.1\r\n" +
        "host: " + config.host() + ":" + config.port() + "\r\n" +
        "\r\n";
    NetServer server = vertx.createNetServer(new NetServerOptions().setPort(config.port()).setHost(config.host()));
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
        .expecting(HttpResponseExpectation.SC_OK)
        .onComplete(TestUtils.onSuccess(resp -> {
          if (responses.incrementAndGet() == requests) {
            checkpoint.succeed();
          }
        }));
    }
  }

  @Test
  @Repeat(times = 10)
  public void testCloseServerConnectionWithPendingMessages(Checkpoint checkpoint) throws Exception {
    int n = 5;
    server.requestHandler(req -> {
      vertx.setTimer(100, id -> {
        req.connection().close();
      });
    });
    startServer(testAddress);
    AtomicBoolean completed = new AtomicBoolean();
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setPipelining(true))
      .with(new PoolOptions().setHttp1MaxSize(n))
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          if (completed.compareAndSet(false, true)) {
            checkpoint.succeed();
          }
        });
      })
      .build();
    for (int i = 0; i < n * 2; i++) {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(TestUtils.onFailure(resp -> {}));
    }
  }

  @Test
  public void testPipeliningFailure(Checkpoint checkpoint) throws Exception {
    int n = 5;
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
        checkpoint.succeed();
      }
    };
    for (int i = 0;i < n * 2;i++) {
      boolean countDown = i + 1 == n;
      client.request(new RequestOptions(requestOptions).setURI("/" + i)).onComplete(TestUtils.onSuccess(req -> {
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
  }

  /**
   * A test that stress HTTP server pipe-lining.
   */
  @Test
  public void testPipelineStress(Checkpoint checkpoint) throws Exception {

    int numConn = 32;
    CountDownLatch latch = checkpoint.asLatch(numConn);

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
          latch.countDown();
        });
        send();
        vertx.setTimer(1000, id -> {
          so.close();
        });
      }
    }

    // We want to be aware of uncaught exceptions and fail the test when it happens
    vertx.exceptionHandler(err -> {
      Assert.fail(err.getMessage());
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
    for (int i = 0;i < numConn;i++) {
      tcpClient.connect(testAddress).onComplete(TestUtils.onSuccess(so -> {
        Client client = new Client(so);
        client.run();
      }));
    }
  }

  @Test
  public void testPipeliningPauseRequest(Checkpoint checkpoint) throws Exception {
    int n = 10;
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
      client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(TestUtils.onSuccess(req -> {
        req.send(Buffer.buffer(TestUtils.randomAlphaString(16))).onComplete(TestUtils.onSuccess(resp -> {
          resp.endHandler(v -> {
            if (remaining.decrementAndGet() == 0) {
              checkpoint.succeed();
            }
          });
        }));
      }));
    }
  }

  @Test
  public void testServerPipeliningConnectionConcurrency(Checkpoint checkpoint) throws Exception {
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
    client.connect(testAddress).onComplete(TestUtils.onSuccess(so -> {
      so.closeHandler(v -> checkpoint.succeed());
      so.write(requests);
    }));
  }

  @Test
  public void testServerConnectionCloseBeforeRequestEnded(Checkpoint checkpoint) throws Exception {
    testServerConnectionClose(checkpoint, true);
  }

  @Test
  public void testServerConnectionCloseAfterRequestEnded(Checkpoint checkpoint) throws Exception {
    testServerConnectionClose(checkpoint, false);
  }

  private void testServerConnectionClose(Checkpoint checkpoint, boolean sendEarlyResponse) throws Exception {
    CompletableFuture<HttpServerRequest> requestLatch = new CompletableFuture<>();
    server.requestHandler(requestLatch::complete);
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress).onComplete(TestUtils.onSuccess(so -> {
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
        checkpoint.succeed();
      });
    }));
  }

  @Test
  public void testServerConnectionCloseDoesNotProcessHTTPMessages(Checkpoint checkpoint) throws Exception {
    AtomicInteger requestCount = new AtomicInteger();
    server.requestHandler(req -> {
      requestCount.incrementAndGet();
      req.response().end();
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress).onComplete(TestUtils.onSuccess(so -> {
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
        checkpoint.succeed();
      });
    }));
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
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(keepAlive).setPipelining(false), new PoolOptions().setHttp1MaxSize(poolSize));
    int requests = 100;

    // Start the servers
    HttpServer[] servers = new HttpServer[numServers];
    Set<HttpServer> connectedServers = ConcurrentHashMap.newKeySet();
    for (int i = 0; i < numServers; i++) {
      HttpServer server = vertx.createHttpServer();
      server.requestHandler(req -> {
        connectedServers.add(server);
        req.response().end();
      });
      server.listen(testAddress).await();
      servers[i] = server;
    }

    CountDownLatch reqLatch = new CountDownLatch(requests);

    // We make sure we execute all the requests on the same context otherwise some responses can come beack when there
    // are no waiters resulting in it being closed so a a new connection is made for the next request resulting in the
    // number of total connections being > pool size (which is correct)
    for (int count = 0; count < requests; count++) {
      client.request(new RequestOptions(requestOptions))
        .compose(HttpClientRequest::send)
        .onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          reqLatch.countDown();
        }));
    }

    TestUtils.awaitLatch(reqLatch);

    //client.dispConnCount();
    assertEquals(expectedConnectedServers, connectedServers.size());

    CountDownLatch serverCloseLatch = new CountDownLatch(numServers);
    for (HttpServer server: servers) {
      server.close().onComplete(TestUtils.onSuccess(s -> {
        serverCloseLatch.countDown();
      }));
    }

    TestUtils.awaitLatch(serverCloseLatch);
  }

  @Test
  public void testPoolingKeepAliveAndPipelining(Checkpoint checkpoint) throws Exception {
    testPooling(checkpoint, true, true);
  }

  @Test
  public void testPoolingKeepAliveNoPipelining(Checkpoint checkpoint) throws Exception {
    testPooling(checkpoint, true, false);
  }

  @Test
  public void testPoolingNoKeepAliveNoPipelining(Checkpoint checkpoint) throws Exception {
    testPooling(checkpoint, false, false);
  }

  private void testPooling(Checkpoint checkpoint, boolean keepAlive, boolean pipelining) throws Exception {
    String path = "foo.txt";
    int numGets = 100;
    int maxPoolSize = 10;
    client = vertx.createHttpClient(new HttpClientOptions()
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
            resp.exceptionHandler(err -> Assert.fail(err.getMessage()));
            assertEquals(200, resp.statusCode());
            assertEquals(theCount, Integer.parseInt(resp.headers().get("count")));
          }))
          .compose(HttpClientResponse::end))
        .onComplete(TestUtils.onSuccess(resp -> {
          if (cnt.incrementAndGet() == numGets) {
            checkpoint.succeed();
          }
        }));
    }
  }

  @Test
  public void testPoolingNoKeepAliveAndPipelining() {
    try {
      vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false).setPipelining(true));
      Assert.fail();
    } catch (IllegalStateException ignore) {
    }
  }

  @Test
  public void testMaxWaitQueueSizeIsRespected(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(false), new PoolOptions().setHttp1MaxSize(1).setMaxWaitQueueSize(0));

    server.requestHandler(req -> {
      assertEquals("/1", req.path());
      checkpoint1.succeed();
    });

    startServer(testAddress);

    client.request(new RequestOptions(requestOptions).setURI("/1")).onComplete(TestUtils.onSuccess(req -> {
      Future<HttpClientRequest> request = client.request(new RequestOptions(requestOptions).setURI("/1"));
      request.onComplete(TestUtils.onFailure(err -> {
        req.end();
        checkpoint2.succeed();
      }));
    }));
  }

  @Test
  public void testServerWebSocketIdleTimeout(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1));
    WebSocketClient client = vertx.createWebSocketClient();
    server
      .webSocketHandler(ws -> {})
      .listen(config.port(), config.host())
      .await();
    client.connect(config.port(), config.host(), "/")
      .onComplete(onSuccess(ws -> {
        ws.closeHandler(v2 -> checkpoint.succeed());
      }));
    checkpoint.awaitSuccess();
  }

  @Test
  public void testClientWebSocketIdleTimeout(Checkpoint checkpoint) {
    WebSocketClient client = vertx.createWebSocketClient(new WebSocketClientOptions().setIdleTimeout(1));
    server
      .webSocketHandler(ws -> {})
      .listen().onComplete(TestUtils.onSuccess(v1 -> {
      client.connect(config.port(), config.host(), "/")
        .onComplete(TestUtils.onSuccess(ws -> {
          ws.closeHandler(v2 -> checkpoint.succeed());
        }));
    }));
  }

  @Test
  public void testSharedServersRoundRobin() throws Exception {
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false), new PoolOptions().setHttp1MaxSize(1));
    int numServers = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2- 1;
    int numRequests = numServers * 100;

    List<HttpServer> servers = Collections.synchronizedList(new ArrayList<>());
    Set<HttpServer> connectedServers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    Map<HttpServer, Integer> requestCount = new ConcurrentHashMap<>();

    CountDownLatch latchConns = new CountDownLatch(numRequests);
    Set<Thread> threads = ConcurrentHashMap.newKeySet();
    Future<String> listenLatch = vertx.deployVerticle(() -> new VerticleBase() {
      Thread thread;
      @Override
      public Future<?> start() throws Exception {
        HttpServer theServer = vertx.createHttpServer();
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
        });
        return theServer
          .listen(testAddress)
          .andThen(TestUtils.onSuccess(s -> {
            if (s.actualPort() > 0) {
              assertEquals(config.port(), s.actualPort());
            }
          }));
      }
    }, new DeploymentOptions().setInstances(numServers));
    listenLatch.await();


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
        .onComplete(TestUtils.onSuccess(v -> closeLatch.countDown()));
    }

    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void testDefaultHttpVersion() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(request -> request.send().compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testIncorrectHttpVersion(Checkpoint checkpoint) throws Exception {
    NetServer server = vertx.createNetServer();
    server.connectHandler(so -> {
      Buffer content = Buffer.buffer();
      so.handler(buff -> {
        content.appendBuffer(buff);
        if (content.toString().endsWith("\r\n\r\n")) {
          so.write(Buffer.buffer("HTTP/1.2 200 OK\r\nContent-Length:5\r\n\r\nHELLO"));
        }
      });
    });
    server
      .listen(testAddress)
      .await();
    AtomicBoolean a = new AtomicBoolean();
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions())
      .withConnectHandler(conn -> conn.closeHandler(v -> checkpoint.succeed()))
      .build();
    client.request(requestOptions)
      .compose(req -> req.putHeader("connection", "close").send())
      .onComplete(TestUtils.onFailure(err -> {
      if (a.compareAndSet(false, true)) {
        assertTrue("message " + err.getMessage() + " should contain HTTP/1.2", err.getMessage().contains("HTTP/1.2"));
      }
    }));
  }

  @Test
  public void testHttp11PersistentConnectionNotClosed() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      assertNull(req.getHeader("Connection"));
      req.response().end();
      assertFalse(req.response().closed());
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_1_1)
      .setKeepAlive(true)
    );
    MultiMap headers = client.request(new RequestOptions(requestOptions))
      .compose(request -> request
        .send()
        .map(HttpClientResponse::headers))
      .await();
    assertNull(headers.get("Connection"));
    assertEquals("0", headers.get("Content-Length"));
  }

  @Test
  public void testHttp11NonPersistentConnectionClosed() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      assertEquals("close", req.getHeader("Connection"));
      req.response().end();
      assertTrue(req.response().closed());
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setKeepAlive(false));
    MultiMap headers = client.request(requestOptions)
      .compose(request -> request
        .send()
        .map(HttpClientResponse::headers))
      .await();
    assertEquals("close", headers.get("Connection"));
  }

  @Test
  public void testHttp10KeepAliveConnectionNotClosed() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_0, req.version());
      assertEquals("keep-alive", req.getHeader("Connection"));
      req.response().end();
      assertFalse(req.response().closed());
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true));
    MultiMap headers = client.request(new RequestOptions(requestOptions).setProtocolVersion(HttpVersion.HTTP_1_0))
      .compose(request -> request
        .send()
        .map(HttpClientResponse::headers))
      .await();
    assertEquals("keep-alive", headers.get("Connection"));
    assertEquals("0", headers.get("Content-Length"));
  }

  @Test
  public void testHttp10RequestNonKeepAliveConnectionClosed() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_0, req.version());
      assertNull(req.getHeader("Connection"));
      req.response().end();
      assertTrue(req.response().closed());
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false));
    MultiMap headers = client.request(new RequestOptions(requestOptions).setProtocolVersion(HttpVersion.HTTP_1_0))
      .compose(request -> request
        .send()
        .map(HttpClientResponse::headers))
      .await();
    assertNull(headers.get("Connection"));
  }

  @Test
  public void testHttp10ResponseNonKeepAliveConnectionClosed() throws Exception {
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
    server
      .listen(testAddress)
      .await();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 3;i++) {
      client.request(requestOptions).compose(req -> req
          .send()
          .expecting(that(resp -> assertEquals(200, resp.statusCode())))
          .compose(HttpClientResponse::end))
        .await();
    }
  }

  @Test
  public void requestAbsNoPort() {
    client.request(new RequestOptions().setAbsoluteURI("http://www.google.com"))
      .compose(HttpClientRequest::send)
      .await();
  }

  @Test
  public void testServerOptionsCopiedBeforeUse() throws Exception {
    HttpServerOptions options = new HttpServerOptions();
    server = vertx.createHttpServer(options);
    // Now change something - but server should still listen at previous port
    options.setPort(config.port() + 1);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK))
      .await();
  }

  @Test
  public void testClientOptionsCopiedBeforeUse() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    HttpClientOptions options = new HttpClientOptions();
    client = vertx.createHttpClient(options);
    // Now change something - but server should ignore this
    options.setSsl(true);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK))
      .await();
  }

  @Test
  public void testRequestExceptionHandlerContext(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.requestHandler(req -> {
      req.connection().close();
    });
    startServer(testAddress);
    Context clientCtx = vertx.getOrCreateContext();
    clientCtx.runOnContext(v -> {
      client.request(requestOptions)
        .onComplete(TestUtils.onSuccess(req -> {
          req.exceptionHandler(err -> {
            TestUtils.assertSameEventLoop(clientCtx, Vertx.currentContext());
            checkpoint1.succeed();
          });
          req.response()
            .onComplete(TestUtils.onFailure(err -> {
              TestUtils.assertSameEventLoop(clientCtx, Vertx.currentContext());
              checkpoint2.succeed();
            }));
          req.writeHead();
        }));
    });
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
        TestUtils.assertSameEventLoop(serverCtx, Vertx.currentContext());
        Buffer body = Buffer.buffer();
        requestResumeMap.get(req.path()).thenAccept(amount -> {
          req.resume();
          req.endHandler(v2 -> {
            TestUtils.assertSameEventLoop(serverCtx, Vertx.currentContext());
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
                    TestUtils.assertSameEventLoop(serverCtx, Vertx.currentContext());
                    resp.end();
                  });
                });
              });
            }
          });
        });
        req.handler(chunk -> {
          TestUtils.assertSameEventLoop(serverCtx, Vertx.currentContext());
          body.appendBuffer(chunk);
        });
      });
      server.listen(testAddress).onComplete(TestUtils.onSuccess(s -> {
        TestUtils.assertSameEventLoop(serverCtx, Vertx.currentContext());
        latch.countDown();
      }));
    });
    TestUtils.awaitLatch(latch);
    CountDownLatch latch2 = new CountDownLatch(1);
    Context clientCtx = vertx.getOrCreateContext();
    clientCtx.runOnContext(v -> {
      client = vertx.createHttpClient(new HttpClientOptions(), new PoolOptions().setHttp1MaxSize(numReqs));
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
          .onComplete(TestUtils.onSuccess(req -> {
            assertSame(t, Thread.currentThread());
            req.setChunked(true)
              .exceptionHandler(err -> Assert.fail(err.getMessage()))
              .response()
              .onComplete(TestUtils.onSuccess(resp -> {
                TestUtils.assertSameEventLoop(requestCtx, Vertx.currentContext());
                assertEquals(200, resp.statusCode());
                threads.add(Thread.currentThread());
                resp.pause();
                responseResumeMap.get(path).thenAccept(v2 -> resp.resume());
                resp.handler(chunk -> {
                  TestUtils.assertSameEventLoop(requestCtx, Vertx.currentContext());
                });
                resp.exceptionHandler(err -> Assert.fail(err.getMessage()));
                resp.endHandler(v2 -> {
                  TestUtils.assertSameEventLoop(requestCtx, Vertx.currentContext());
                  if (cnt.incrementAndGet() == numReqs) {
                    assertEquals(expectedThreads, new HashSet<>(threads));
                    latch2.countDown();
                  }
                });
              }));
            req.drainHandler(v2 -> {
              TestUtils.assertSameEventLoop(requestCtx, Vertx.currentContext());
              req.end();
            });
            req.writeHead().onComplete(version -> {
              TestUtils.assertSameEventLoop(requestCtx, Vertx.currentContext());
              fill(data, req, cf::complete);
            });
          }));
      });
    }
    TestUtils.awaitLatch(latch2, 40, TimeUnit.SECONDS);
    // Close should be in own context
    Thread thread = new Thread(() -> {
      server.close().andThen(onSuccess(v -> {
        ContextInternal closeContext = ((VertxInternal) vertx).getContext();
        assertFalse(contexts.contains(closeContext));
        assertNotSame(serverCtx, closeContext);
        assertFalse(contexts.contains(serverCtx));

      })).await();
      server = null;
    });
    thread.start();
    thread.join(20_000);
  }

  @Test
  public void testRequestHandlerNotCalledInvalidRequest(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
      Assert.fail();
    });
    startServer(testAddress);
    vertx.createNetClient(new NetClientOptions()).connect(testAddress).onComplete(TestUtils.onSuccess(socket -> {
      socket.closeHandler(r -> {
        checkpoint.succeed();
      });
      socket.write("GET HTTP1/1\r\n");

      // trigger another write to be sure we detect that the other peer has closed the connection.
      socket.write("X-Header: test\r\n");
    }));
  }

  @Test
  public void testTwoServersSameAddressDifferentContext() throws Exception {
    vertx.deployVerticle(() -> new VerticleBase() {
      private HttpServer server;
        @Override
        public Future<?> start() {
          server = vertx.createHttpServer()
            .requestHandler(req -> req.response().end());
          return server
            .listen(testAddress);
        }
      }, new DeploymentOptions().setInstances(2))
      .await();
  }

  /*
  Fix for https://bugs.eclipse.org/bugs/show_bug.cgi?id=475017
  Also see https://groups.google.com/forum/?fromgroups#!topic/vertx/N_wSoQlvMMs
   */
  @Test
  public void testPauseResumeClientResponse(Checkpoint checkpoint) throws Exception {
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
    client = vertx.createHttpClient();
    client.request(requestOptions)
      .onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
          resp.handler(b -> {
            readBuffer.appendBuffer(b);
            for (int i = 0; i < 64; i++) {
              vertx.setTimer(1, n -> {
                try {
                  Thread.sleep(0);
                } catch (InterruptedException ignore) {
                }
              });
            }
            resp.endHandler(v -> {
              byte[] expectedData = buffer.getBytes();
              byte[] actualData = readBuffer.getBytes();
              assertArrayEquals(expectedData, actualData);
              checkpoint.succeed();
            });
          });
        }));
      }));
  }

  /*
   A reproducer for this issue https://github.com/eclipse/vert.x/issues/2230
   */
  @Test
  public void testPauseResumeServerRequestFromAnotherThread(Checkpoint checkpoint) throws Exception {
    ExecutorService exec = Executors.newSingleThreadExecutor();
    Buffer buffer = TestUtils.randomBuffer(32 * 1024 * 1024);
    Buffer readBuffer = Buffer.buffer(32 * 1024 * 1024);
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
        assertArrayEquals(expectedData, actualData);
        checkpoint.succeed();
      });
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
      req
        .setChunked(true)
        .response().onComplete(TestUtils.onFailure(err -> {
        }));
      int times = buffer.length() / 8192;
      send(req, buffer, 0, times);
    }));
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
  public void testAckHttpClientResponseReadWindowOnEnd(Checkpoint checkpoint) throws Exception {
    int highWaterMark = 65536;
    int numRequests = 10;
    CountDownLatch latch = checkpoint.asLatch(numRequests);
    Buffer buffer = TestUtils.randomBuffer(highWaterMark);
    server.requestHandler(req -> {
      req.response().end(buffer);
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    List<HttpClientResponse> responses = new ArrayList<>();
    for (int i = 0;i < numRequests;i++) {
      client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
          resp.pause();
          resp.endHandler(v -> latch.countDown());
          List<HttpClientResponse> responsesToResume;
          synchronized (responses) {
            responses.add(resp);
            if (responses.size() < 10) {
              return;
            }
            responsesToResume = new ArrayList<>(responses);
          }
          for (HttpClientResponse responseToResume : responsesToResume) {
            responseToResume.resume();
          }
        }));
      }));
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
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 5;i++) {
      client.request(requestOptions)
        .compose(req -> req
          .send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::end))
        .await();
    }
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
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 2;i++) {
      client.request(new RequestOptions(requestOptions).setMethod(PUT))
        .compose(req -> req
          .send(Buffer.buffer("1"))
          .expecting(that(resp -> assertEquals(200, resp.statusCode())))
          .compose(HttpClientResponse::end))
        .await();
    }
  }

  @Test
  public void testMultipleRecursiveCallsAndPipelining(Checkpoint checkpoint) throws Exception {
    int sendRequests = 100;
    AtomicInteger receivedRequests = new AtomicInteger();
    server.requestHandler(x -> {
        x.response().end("hello");
      });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions()
      .setKeepAlive(true)
      .setPipelining(true)
    );
    IntStream.range(0, 5).forEach(i -> recursiveCall(checkpoint, receivedRequests, sendRequests));
  }

  private void recursiveCall(Checkpoint checkpoint, AtomicInteger receivedRequests, int sendRequests){
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(r -> {
        int numRequests = receivedRequests.incrementAndGet();
        if (numRequests == sendRequests) {
          checkpoint.succeed();
        } else if (numRequests < sendRequests) {
          recursiveCall(checkpoint, receivedRequests, sendRequests);
        }
      });
  }

  @Ignore
  @Test
  public void testUnsupportedHttpVersion(Checkpoint checkpoint) throws Exception {
    testUnsupported(checkpoint, "GET /someuri HTTP/1.7\r\nHost: localhost\r\n\r\n", false);
  }

  private void testUnsupported(Checkpoint checkpoint, String rawReq, boolean method) throws Exception {
    server
      .requestHandler(req -> {
        // Should never be called
        Assert.fail();
      });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    // Send a raw request
    client.connect(testAddress).onComplete(TestUtils.onSuccess(conn -> {
      conn.write(rawReq);
      Buffer respBuff = Buffer.buffer();
      conn.handler(respBuff::appendBuffer);
      conn.closeHandler(v -> {
        // Server should automatically close it after sending back 501
        assertTrue("Unexpected response " + respBuff, respBuff.toString().contains("501 Not Implemented"));
        checkpoint.succeed();
      });
    }));
  }

  @Test
  public void testTwoServersDifferentEventLoopsCloseOne() throws Exception {
    CountDownLatch latch1 = new CountDownLatch(2);
    AtomicInteger server1Count = new AtomicInteger();
    AtomicInteger server2Count = new AtomicInteger();
    server.requestHandler(req -> {
      server1Count.incrementAndGet();
      req.response().end();
    }).listen(testAddress).onComplete(TestUtils.onSuccess(s -> {
      latch1.countDown();
    }));
    HttpServer server2 = vertx.createHttpServer().requestHandler(req -> {
      server2Count.incrementAndGet();
      req.response().end();
    });
    server2.listen(testAddress).onComplete(TestUtils.onSuccess(s -> {
      latch1.countDown();
    }));
    TestUtils.awaitLatch(latch1);

    HttpClient[] clients = new HttpClient[5];
    for (int i = 0;i < 5;i++) {
      clients[i] = vertx.createHttpClient(new HttpClientOptions());
    }
    for (int i = 0; i < 2; i++) {
      CountDownLatch latch2 = new CountDownLatch(1);
      clients[i].request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          latch2.countDown();
        }));
      TestUtils.awaitLatch(latch2);
    }

    // Now close server 2
    CountDownLatch latch3 = new CountDownLatch(1);
    server2.close().onComplete(TestUtils.onSuccess(v -> {
      latch3.countDown();
    }));
    TestUtils.awaitLatch(latch3);
    // Send some more requests
    for (int i = 0; i < 2; i++) {
      CountDownLatch latch2 = new CountDownLatch(1);
      clients[2 + i].request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          latch2.countDown();
        }));
      TestUtils.awaitLatch(latch2);
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
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .await();
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
    server = vertx.createHttpServer(new HttpServerOptions().setMaxInitialLineLength(maxInitialLength))
      .requestHandler(req -> {
      assertEquals(req.getParam("t"), longParam);
      req.response().end();
    });
    startServer(testAddress);
    vertx.createHttpClient()
      .request(new RequestOptions(requestOptions).setURI("/?t=" + longParam))
      .compose(req -> req
        .send()
        .compose(resp -> {
          if (maxInitialLength > HttpServerOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH) {
            assertEquals(200, resp.statusCode());
            return resp.end();
          } else {
            assertEquals(414, resp.statusCode());
            return Future.future(p -> resp
              .request()
              .connection()
              .closeHandler(v -> p.complete()));
          }
        })).await();
  }

  @Test
  public void testServerInvalidHttpMessage() throws Exception {
    server.requestHandler(req -> Assert.fail());
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    try {
      NetSocket connection = client.connect(testAddress).await();
      Future<Buffer> fut = connection.collect(Collectors.reducing(Buffer.buffer(), Buffer::appendBuffer));
      connection.write(Buffer.buffer("GET /?ab\rc=1 HTTP/1.1\r\n"));
      String res = fut.await().toString();
      String expected = "HTTP/1.0 400 ";
      assertEquals(expected, res.substring(0, expected.length()));
    } finally {
      client.close().await();
    }
  }

  @Test
  public void testClientInvalidHttpMessage() throws Exception {
    server.requestHandler(req -> Assert.fail());
    startServer(testAddress);
    try {
      client
        .request(new RequestOptions(requestOptions).setURI("/a\rb"))
        .compose(HttpClientRequest::send)
        .await();
      Assert.fail();
    } catch (IllegalArgumentException expected) {
    }
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
      server.listen(testAddress).await();
      client = vertx.createHttpClient(new HttpClientOptions().setMaxInitialLineLength(6000));
      Buffer body = client
        .request(new RequestOptions(requestOptions).setURI("/?t=" + longParam))
        .compose(request -> request
          .send()
          .compose(HttpClientResponse::body))
        .await();
      assertEquals("0123456789", body.toString());
    } finally {
      server.close();
    }
  }

  @Test
  public void testClientMaxHeaderSizeOption() throws Exception {

    String longHeader = TestUtils.randomAlphaString(9000);

    // min 9023 = 9000 for longHeader and 23 for "Content-Length: 0 t: "
    server.requestHandler(req -> {
      // Add longHeader
      req.response().putHeader("t", longHeader).end();
    });

    startServer(testAddress);

    client = vertx.createHttpClient(new HttpClientOptions().setMaxHeaderSize(10000));
    MultiMap headers = client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(response -> response
          .end()
          .map(response.headers())))
      .await();
    assertEquals(headers.get("t"), longHeader);
  }

  @Test
  public void testServerMaxHeaderSize() throws Exception {
    testServerMaxHeaderSize(HttpServerOptions.DEFAULT_MAX_HEADER_SIZE);
  }

  @Test
  public void testServerMaxHeaderSizeOption() throws Exception {
    // min 9023 = 9000 for longHeader and 23 for "Content-Length: 0 t: "
    testServerMaxHeaderSize(10000);
  }

  private void testServerMaxHeaderSize(int maxHeaderSize) throws Exception {

    String longHeader = TestUtils.randomAlphaString(9000);

    server = vertx.createHttpServer(new HttpServerOptions().setMaxHeaderSize(maxHeaderSize))
      .requestHandler(req -> {
        assertEquals(req.getHeader("t"), longHeader);
        req.response().end();
      });

    startServer(testAddress);

    Integer sc = client
      .request(requestOptions)
      .compose(req -> req
        .putHeader("t", longHeader)
        .send().compose(response -> response
          .end()
          .map(response.statusCode())))
      .await();
    if (maxHeaderSize > HttpServerOptions.DEFAULT_MAX_HEADER_SIZE) {
      assertEquals(200, (int) sc);
    } else {
      assertEquals(431, (int) sc);
    }
  }

  @Test
  public void testPipelinedInvalidHttpResponse(Checkpoint checkpoint) {

    AtomicInteger count = new AtomicInteger(0);
    NetServer server  = vertx.createNetServer();
    String match = "GET /somepath HTTP/1.1\r\nhost: " + config.host() + ":" + config.port()+ "\r\n\r\n";
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
              Assert.fail();
              break;
          }
        }
      });
    }).listen(testAddress)
      .await();

    // We force two pipelined requests to check that the second request does not get stuck after the first failing
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true), new PoolOptions().setHttp1MaxSize(1));

    CountDownLatch latch = checkpoint.asLatch(2);
    for (int i = 0;i < 2;i++) {
      AtomicBoolean failed = new AtomicBoolean();
      client.request(new RequestOptions(requestOptions).setURI("/somepath"))
        .compose(HttpClientRequest::send)
        .onComplete(TestUtils.onFailure(err -> {
          if (failed.compareAndSet(false, true)) {
            assertEquals(IllegalArgumentException.class, err.getClass()); // invalid version format
            latch.countDown();
          }
        }));
    }
  }

  @Test
  public void testConnectionCloseHttp_1_0_NoClose(Checkpoint checkpoint) throws Exception {
    testConnectionClose(checkpoint, req -> {
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
  public void testConnectionCloseHttp_1_0_Close(Checkpoint checkpoint) throws Exception {
    testConnectionClose(checkpoint, req -> {
      req.putHeader("Connection", "close");
      req.end();
    }, socket -> {
      AtomicBoolean firstRequest = new AtomicBoolean(true);
      socket.handler(RecordParser.newDelimited("\r\n\r\n", buffer -> {
        if (firstRequest.getAndSet(false)) {
          socket.write("HTTP/1.0 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 4\r\n"
              + "Connection: keep-alive\r\n" + "\r\n" + "xxx\r\n");
        } else {
          socket.write("HTTP/1.0 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 1\r\n"
              + "\r\n" + "\r\n");
          socket.close();
        }
      }));
    });
  }

  @Test
  public void testConnectionCloseHttp_1_1_NoClose(Checkpoint checkpoint) throws Exception {
    testConnectionClose(checkpoint, HttpClientRequest::end, socket -> {
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
  public void testConnectionCloseHttp_1_1_Close(Checkpoint checkpoint) throws Exception {
    testConnectionClose(checkpoint, HttpClientRequest::end, socket -> {
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

  private void testConnectionClose(Checkpoint checkpoint, Handler<HttpClientRequest> clientRequest,  Handler<NetSocket> connectHandler) throws Exception {
    NetServerOptions serverOptions = new NetServerOptions();
    vertx.createNetServer(serverOptions)
      .connectHandler(connectHandler)
      .listen(testAddress)
      .await();
    int poolSize = 5;
    HttpClientOptions clientOptions = new HttpClientOptions()
        .setDefaultHost("localhost")
        .setKeepAlive(true)
        .setPipelining(false);
    client = vertx.createHttpClient(clientOptions, new PoolOptions().setHttp1MaxSize(1));
    int requests = poolSize * 2 + 1;
    AtomicInteger count = new AtomicInteger(requests);
    for (int i = 0; i < requests; i++) {
      client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
        req.response().onComplete(TestUtils.onSuccess(resp -> {
          resp.handler(buffer -> {
            // Should check
          });
          resp.endHandler(v -> {
            if (count.decrementAndGet() == 0) {
              checkpoint.succeed();
            }
          });
          resp.exceptionHandler(err -> Assert.fail(err.getMessage()));
        }));
        clientRequest.handle(req);
      }));
    }
  }

  @Test
  public void testDoNotReuseConnectionWhenResponseEndsBeforeRequest() throws Exception {
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

    client.request(new RequestOptions(requestOptions).setURI("/1")).onComplete(TestUtils.onSuccess(req -> {
      req.response().onComplete(
        TestUtils.onSuccess(resp -> {
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
        .writeHead();
    }));

    client.request(new RequestOptions(requestOptions).setURI("/2"))
      .compose(HttpClientRequest::send)
      .await();
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
    AtomicInteger connCount = new AtomicInteger();
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setPipelining(true).setKeepAlive(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(conn -> connCount.incrementAndGet())
      .build();
    CountDownLatch respLatch = new CountDownLatch(2);
    client.request(new RequestOptions(requestOptions).setURI("/first")).onComplete(TestUtils.onSuccess(req1 -> {
      req1.response().onComplete(TestUtils.onFailure(err -> {
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
          .onComplete(TestUtils.onSuccess(v -> respLatch.countDown()));
      }
    }));
    TestUtils.awaitLatch(doneLatch);
    assertEquals(Arrays.asList("/second", "/third"), responses);
    TestUtils.awaitLatch(respLatch);
    assertEquals(1, connCount.get());
  }

  @Test
  public void testClientConnectionExceptionHandler(Checkpoint checkpoint) throws Exception {
    NetServer server = vertx.createNetServer();
    server.connectHandler(so -> {
      so.write(Buffer.buffer(TestUtils.randomAlphaString(40) + "\r\n"));
    })
      .listen(testAddress)
      .await();
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions())
      .withConnectHandler(conn -> {
        conn.exceptionHandler(err -> {
          checkpoint.succeed();
        });
      })
      .build();
    client.request(requestOptions)
      .compose(HttpClientRequest::writeHead)
      .await();
  }

  @Test
  public void testServerConnectionExceptionHandler(Checkpoint checkpoint) throws Exception {
    server.connectionHandler(conn -> {
      conn.exceptionHandler(err -> {
        assertTrue(err instanceof TooLongFrameException);
        checkpoint.succeed();
      });
    });
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .await();
    client.request(requestOptions)
      .compose(req -> req
        .putHeader("the_header", TestUtils.randomAlphaString(10000))
        .writeHead())
      .await();
  }

  @Test
  public void testServerExceptionHandler(Checkpoint checkpoint) throws Exception {
    Context serverCtx = vertx.getOrCreateContext();
    server.exceptionHandler(err -> {
      assertSame(serverCtx, Vertx.currentContext());
      assertTrue(err instanceof TooLongFrameException);
      checkpoint.succeed();
    });
    server.requestHandler(req -> {
      Assert.fail();
    });
    startServer(testAddress, serverCtx);
    client
      .request(requestOptions)
      .compose(req -> req
          .putHeader("the_header", TestUtils.randomAlphaString(10000))
          .writeHead()
      ).await();
  }

  @Test
  public void testRandomPorts() {
    int numServers = 10;
    Set<Integer> ports = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < numServers;i++) {
      HttpServer s = vertx.createHttpServer().requestHandler(req -> {
        req.response().end();
      }).listen(0, config.host()).await();
      int port = s.actualPort();
      ports.add(port);
      client.request(new RequestOptions().setHost(config.host()).setPort(port))
        .compose(request -> request
          .send()
          .compose(HttpClientResponse::end))
        .await();
    }
  }

  @Test
  public void testContentDecompression() throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions().setDecompressionSupported(true));
    String expected = TestUtils.randomAlphaString(1000);
    Buffer dataGzipped = Buffer.buffer(TestUtils.compressGzip(expected));
    server.requestHandler(req -> {
      assertEquals(config.host() + ":" + config.port(), req.headers().get("host"));
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
      .compose(req -> req
        .send(dataGzipped).compose(HttpClientResponse::end))
      .await();
  }

  @Test
  public void testResetClientRequestNotYetSent(Checkpoint checkpoint) throws Exception {
    testResetClientRequestNotYetSent(checkpoint, false, false);
  }

  @Test
  public void testResetKeepAliveClientRequestNotYetSent(Checkpoint checkpoint) throws Exception {
    testResetClientRequestNotYetSent(checkpoint, true, false);
  }

  @Test
  public void testResetPipelinedClientRequestNotYetSent(Checkpoint checkpoint) throws Exception {
    testResetClientRequestNotYetSent(checkpoint, true, true);
  }

  private void testResetClientRequestNotYetSent(Checkpoint checkpoint, boolean keepAlive, boolean pipelined) throws Exception {
    NetServer server = vertx.createNetServer();
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
          checkpoint.succeed();
        }
      });
    });
    server
      .listen(testAddress)
      .await();
    // There might be a race between the request write and the request reset
    // so we do it on the context thread to avoid it
    client = vertx.createHttpClient(new HttpClientOptions()
      .setKeepAlive(keepAlive)
      .setPipelining(pipelined), new PoolOptions().setHttp1MaxSize(1));
    RequestOptions opts = new RequestOptions(requestOptions).setMethod(PUT);
    HttpClientRequest request = client.request(opts).await();
    assertWaitUntil(() -> numReq.get() == 1);
    request.reset().await();
    client.request(new RequestOptions(requestOptions).setURI("some-uri"))
      .compose(r -> r
        .send()
        .compose(HttpClientResponse::end))
      .await();
    assertEquals(1, numReq.get());
  }

  @Test
  public void testResetKeepAliveClientRequest(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3) throws Exception {
    NetServer server = vertx.createNetServer();
    AtomicInteger count = new AtomicInteger();
    server.connectHandler(so -> {
      assertEquals(0, count.getAndIncrement());
      Buffer total = Buffer.buffer();
      so.handler(buff -> {
        total.appendBuffer(buff);
        if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
          "host: " + config.host() + ":" + config.port() +"\r\n" +
          "\r\n")) {
          so.write(
            "HTTP/1.1 200 OK\r\n" +
              "Content-Type: text/plain\r\n" +
              "Content-Length: 11\r\n" +
              "\r\n" +
              "Hello world");
          so.closeHandler(v -> {
            checkpoint1.succeed();
          });
        }
      });
    });
    server.listen(testAddress).await();
    AtomicInteger status = new AtomicInteger();
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setPipelining(false).setKeepAlive(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          assertEquals(1, status.getAndIncrement());
          checkpoint2.succeed();
        });
      })
      .build();
    client.request(new RequestOptions(requestOptions).setURI("/somepath"))
      .onComplete(TestUtils.onSuccess(req1 -> {
        req1.send().onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(0, status.getAndIncrement());
        }));
        client.request(requestOptions).onSuccess(req2 -> {
          req2
            .response()
            .onComplete(TestUtils.onFailure(err -> checkpoint3.succeed()));
          req2.writeHead().onComplete(TestUtils.onSuccess(v -> {
            assertTrue(req2.reset().succeeded());
          }));
        });
      }));
  }

  @Test
  public void testResetPipelinedClientRequest(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3) throws Exception {
    CompletableFuture<Void> doReset = new CompletableFuture<>();
    NetServer server = vertx.createNetServer();
    AtomicInteger count = new AtomicInteger();
    server.connectHandler(so -> {
      assertEquals(0, count.getAndIncrement());
      Buffer total = Buffer.buffer();
      so.handler(buff -> {
        total.appendBuffer(buff);
        if (total.toString().equals("GET /somepath HTTP/1.1\r\n" +
            "host: " + config.host() + ":" + config.port() + "\r\n" +
            "\r\n" +
            "POST /somepath HTTP/1.1\r\n" +
            "host: " + config.host() + ":" + config.port() + "\r\n" +
            "transfer-encoding: chunked\r\n" +
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
        checkpoint1.succeed();
      });
    });
    server.listen(testAddress).await();
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setPipelining(true).setKeepAlive(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          checkpoint2.succeed();
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
      ).onComplete(TestUtils.onSuccess(req2 -> {
        req2
          .response()
          .onComplete(TestUtils.onFailure(resp -> checkpoint3.succeed()));
        req2.writeHead();
        doReset.thenAccept(v2 -> {
          assertTrue(req2.reset().succeeded());
        });
      }));
    });
  }

  @Test
  public void testCloseTheConnectionAfterResetKeepAliveClientRequest(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testCloseTheConnectionAfterResetPersistentClientRequest(checkpoint1, checkpoint2, false);
  }

  @Test
  public void testCloseTheConnectionAfterResetPipelinedClientRequest(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testCloseTheConnectionAfterResetPersistentClientRequest(checkpoint1, checkpoint2, true);
  }

  private void testCloseTheConnectionAfterResetPersistentClientRequest(Checkpoint checkpoint1, Checkpoint checkpoint2, boolean pipelined) throws Exception {
    NetServer server = vertx.createNetServer();
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
              checkpoint1.succeed();
            }
          });
          break;
        default:
          Assert.fail("Invalid count");
          break;

      }
    });
    server
      .listen(testAddress)
      .await();
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(pipelined).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client
      .request(requestOptions)
      .onComplete(TestUtils.onSuccess(req1 -> {
        if (pipelined) {
          HttpConnection conn = req1.connection();
          conn.closeHandler(v2 -> {
            client.request(new RequestOptions(requestOptions).setURI("/somepath"))
              .compose(req -> req
                .send()
                .expecting(that(resp -> assertEquals(200, resp.statusCode())))
                .compose(HttpClientResponse::body))
              .onComplete(TestUtils.onSuccess(body -> {
                assertEquals("Hello world", body.toString());
                checkpoint2.succeed();
              }));
          });
          req1.writeHead().onComplete(v -> {
            assertTrue(req1.reset().succeeded());
          });
        } else {
          req1.writeHead().onComplete(v -> {
            assertTrue(req1.reset().succeeded());
          });
          client.request(new RequestOptions(requestOptions).setURI("/somepath"))
            .compose(req -> req
              .send()
              .expecting(that(resp -> assertEquals(200, resp.statusCode())))
              .compose(HttpClientResponse::body))
            .onComplete(TestUtils.onSuccess(body -> {
              assertEquals("Hello world", body.toString());
              checkpoint2.succeed();
            }));
        }
      }));
  }

  @Test
  public void testCloseTheConnectionAfterResetKeepAliveClientResponse(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testCloseTheConnectionAfterResetPersistentClientResponse(checkpoint1, checkpoint2, false);
  }

  @Test
  public void testCloseTheConnectionAfterResetPipelinedClientResponse(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testCloseTheConnectionAfterResetPersistentClientResponse(checkpoint1, checkpoint2, true);
  }

  private void testCloseTheConnectionAfterResetPersistentClientResponse(Checkpoint checkpoint1, Checkpoint checkpoint2, boolean pipelined) throws Exception {
    NetServer server = vertx.createNetServer();
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
              checkpoint1.succeed();
            }
          });
          break;
        default:
          Assert.fail("Invalid count");
          break;

      }
    });
    server
      .listen(testAddress)
      .await();
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(pipelined).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    if (pipelined) {
      client.request(new RequestOptions(requestOptions).setURI("/somepath"))
        .onComplete(TestUtils.onSuccess(req1 -> {
          req1.send().onComplete(
            TestUtils.onSuccess(resp1 -> {
              resp1.handler(buff -> {
                // Since we pipeline we must be sure that the first request is closed before running a new one
                resp1.request().connection().closeHandler(v -> {
                  client.request(new RequestOptions(requestOptions).setURI("/somepath")).onComplete(TestUtils.onSuccess(req2 -> {
                    req2.send().onComplete(TestUtils.onSuccess(resp -> {
                      assertEquals(200, resp.statusCode());
                      resp.bodyHandler(body -> {
                        assertEquals("Hello world", body.toString());
                        checkpoint2.succeed();
                      });
                    }));
                  }));
                });
                resp1.request().reset();
              });
            }));
        }));
    } else {
      client.request(new RequestOptions(requestOptions).setURI("/somepath")).onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(
          TestUtils.onSuccess(resp -> {
            resp.handler(buff -> {
              resp.request().reset();
            });
          }));
      }));
      client.request(new RequestOptions(requestOptions).setURI("/somepath")).onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            assertEquals("Hello world", body.toString());
            checkpoint2.succeed();
          });
        }));
      }));
    }
  }

  @Test
  public void testCloseTheConnectionAfterResetBeforePipelinedResponseReceived(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testCloseTheConnectionAfterResetBeforeResponseReceived(checkpoint1, checkpoint2, true);
  }

  @Test
  public void testCloseTheConnectionAfterResetBeforeKeepAliveResponseReceived(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testCloseTheConnectionAfterResetBeforeResponseReceived(checkpoint1, checkpoint2, false);
  }

  private void testCloseTheConnectionAfterResetBeforeResponseReceived(Checkpoint checkpoint1, Checkpoint checkpoint2, boolean pipelined) throws Exception {
    NetServer server = vertx.createNetServer();
    CompletableFuture<Void> requestReceived = new CompletableFuture<>();
    CompletableFuture<Void> sendResponse = new CompletableFuture<>();
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
              checkpoint1.succeed();
            }
          });
          break;
        default:
          Assert.fail("Invalid count");
          break;

      }
    });
    server
      .listen(testAddress)
      .await();
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(pipelined).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(new RequestOptions(requestOptions).setURI("/1"))
      .onComplete(TestUtils.onSuccess(req1 -> {
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
              .onComplete(TestUtils.onSuccess(body -> {
                assertEquals("Hello world", body.toString());
                checkpoint2.succeed();
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
            .onComplete(TestUtils.onSuccess(body -> {
              assertEquals("Hello world", body.toString());
              checkpoint2.succeed();
            }));
        }
      }));
  }

  @Test
  public void testTooLongContentInHttpServerRequest(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    server.connectionHandler(conn -> {
      conn.exceptionHandler(error -> {
        assertEquals(IllegalArgumentException.class, error.getClass());
        checkpoint.succeed();
      });
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    NetSocket socket = client.connect(testAddress).await();
    socket
      .write("POST / HTTP/1.1\r\nContent-Length: 4\r\n\r\ntoolong\r\n")
      .await();
  }
  @Test
  public void testInvalidTrailerInHttpServerRequest(Checkpoint checkpoint) throws Exception {
    testHttpServerRequestDecodeError(checkpoint, so -> {
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
  public void testInvalidChunkInHttpServerRequest(Checkpoint checkpoint) throws Exception {
    testHttpServerRequestDecodeError(checkpoint, so -> {
      so.write("invalid\r\n"); // Empty chunk
    }, errors -> {
      assertEquals(2, errors.size());
      assertEquals(NumberFormatException.class, errors.get(0).getClass());
    });
  }

  private void testHttpServerRequestDecodeError(Checkpoint checkpoint, Handler<NetSocket> bodySender, Handler<List<Throwable>> errorsChecker) throws Exception {
    AtomicReference<NetSocket> current = new AtomicReference<>();
    server.requestHandler(req -> {
      List<Throwable> errors = new ArrayList<>();
      Handler<Throwable> handler = err -> {
        errors.add(err);
        if (errors.size() == 2) {
          errorsChecker.handle(errors);
          checkpoint.succeed();
        }
      };
      req.exceptionHandler(handler);
      bodySender.handle(current.get());
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    NetSocket so = client
      .connect(testAddress)
      .await();
    current.set(so);
    so.write("POST /somepath HTTP/1.1\r\n");
    so.write("Transfer-Encoding: chunked\r\n");
    so.write("\r\n");
  }

  @Test
  public void testInvalidChunkInHttpClientResponse(Checkpoint checkpoint) throws Exception {
    NetServer server = vertx.createNetServer();
    CompletableFuture<Void> cont = new CompletableFuture<>();
    server
      .connectHandler(so -> {
      so.handler(buff -> {
        so.handler(null);
        so.write("HTTP/1.1 200 OK\r\n" + "Transfer-Encoding: chunked\r\n" + "\r\n");
      });
      cont.whenComplete((v,e) -> {
        so.write("invalid\r\n"); // Invalid chunk
      });
    }).listen(testAddress)
      .await();
    testHttpClientResponseDecodeError(checkpoint, cont::complete, err -> assertTrue(err instanceof NumberFormatException));
  }

  @Ignore("fixme - does not pass anymore")
  @Test
  public void testInvalidTrailersInHttpClientResponse(Checkpoint checkpoint) throws Exception {
    NetServer server = vertx.createNetServer();
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
    }).listen(testAddress).await();
    testHttpClientResponseDecodeError(checkpoint, cont::complete, err -> assertTrue(err instanceof TooLongFrameException));
  }

  private void testHttpClientResponseDecodeError(Checkpoint checkpoint, Handler<Void> continuation, Handler<Throwable> errorHandler) {
    AtomicInteger status = new AtomicInteger();
    AtomicBoolean connectionClosed = new AtomicBoolean();
    client.request(requestOptions)
      .onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
          resp.request().connection().closeHandler(v -> {
            connectionClosed.set(true);
            if (status.get() == 1) {
              checkpoint.succeed();
            }
          });
          resp.exceptionHandler(err -> {
            int current = status.incrementAndGet();
            if (current == 1) {
              errorHandler.handle(err);
              if (connectionClosed.get()) {
                checkpoint.succeed();
              }
            } else {
              Assert.fail("Unexpected extra response exception callback: " + err);
            }
          });
          continuation.handle(null);
        }));
      }));
  }

  @Test
  public void testEmptyHttpVersion(Checkpoint checkpoint) throws Exception {
    String expectedMessage;
    try {
      io.netty.handler.codec.http.HttpVersion.valueOf("");
      Assert.fail();
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
        checkpoint.succeed();
      });
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    NetSocket socket = client
      .connect(testAddress)
      .await();
    socket
      .write("GET /\r\n\r\n")
      .await();
  }

  @Test
  public void testReceiveResponseWithNoRequestInProgress(Checkpoint checkpoint) throws Exception {
    NetServer server = vertx.createNetServer();
    Promise<?> promise = Promise.promise();
    server.connectHandler(so -> {
      promise.future().onSuccess(v -> {
        so.write("HTTP/1.1 200 OK\r\n" +
          "Transfer-Encoding: chunked\r\n" +
          "\r\n");
      });
    });
    server
      .listen(testAddress)
      .await();
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions())
      .withConnectHandler(conn -> {
        AtomicBoolean failed = new AtomicBoolean();
        conn.exceptionHandler(err -> {
          failed.set(true);
        });
        conn.closeHandler(v -> {
          assertTrue(failed.get());
          checkpoint.succeed();
        });
      })
      .build();
    client
      .request(requestOptions)
      .await();
    promise.succeed();
  }

  @Hosts({@Host(name = "host0"), @Host(name = "host1") , @Host(name = "host2")})
  @Test
  public void testPerHostPooling(@ProvidedBy(VertxProviderWithResolver.class) Vertx vertx, Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setKeepAlive(true)
      .setPipelining(false), new PoolOptions().setHttp1MaxSize(1));
    testPerXXXPooling(vertx, checkpoint1, checkpoint2, (i) -> client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost("host" + i)
      .setURI("/somepath"))
      .onSuccess(req -> req.putHeader("key", "host" + i)), req -> req.getHeader("key"));
  }

  @Test
  public void testPerPeerPooling(@ProvidedBy(VertxProviderWithResolver.class) Vertx vertx, Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
        .setKeepAlive(true)
        .setPipelining(false), new PoolOptions().setHttp1MaxSize(1));
    testPerXXXPooling(vertx, checkpoint1, checkpoint2, (i) -> client.request(new RequestOptions()
      .setServer(SocketAddress.inetSocketAddress(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST))
      .setPort(DEFAULT_HTTP_PORT)
      .setHost("host" + i)
      .setURI("/somepath")), req -> req.authority().toString());
  }

  @Test
  public void testPerPeerPoolingWithProxy(@ProvidedBy(VertxProviderWithResolver.class) Vertx vertx, Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
        .setKeepAlive(true)
        .setPipelining(false).setProxyOptions(new ProxyOptions()
            .setType(ProxyType.HTTP)
            .setHost(DEFAULT_HTTP_HOST)
            .setPort(DEFAULT_HTTP_PORT)), new PoolOptions().setHttp1MaxSize(1));
    testPerXXXPooling(vertx, checkpoint1, checkpoint2, (i) -> client.request(new RequestOptions()
      .setPort(80)
      .setHost("host" + i)
      .setURI("/somepath")), req -> req.authority().toString());
  }

  private void testPerXXXPooling(Vertx vertx,  Checkpoint checkpoint1, Checkpoint checkpoint2, Function<Integer, Future<HttpClientRequest>> requestProvider, Function<HttpServerRequest, String> keyExtractor) throws Exception {
    // Even though we use the same server host, we pool per peer host
    int numPeers = 3;
    int numRequests = 5;
    Map<String, HttpServerResponse> map = new HashMap<>();
    AtomicInteger count = new AtomicInteger();
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      String key = keyExtractor.apply(req);
      assertFalse(map.containsKey(key));
      map.put(key, req.response());
      if (map.size() == numPeers) {
        map.values().forEach(HttpServerResponse::end);
        map.clear();
        if (count.incrementAndGet() == numRequests) {
          checkpoint1.succeed();
        }
      }
    });
    server.listen(testAddress).await();
    AtomicInteger remaining = new AtomicInteger(numPeers * numRequests);
    for (int i = 0;i < numPeers;i++) {
      for (int j = 0;j < numRequests;j++) {
        Future<HttpClientRequest> request = requestProvider.apply(i);
        request
          .onComplete(TestUtils.onSuccess(req -> {
            req.send().onComplete(TestUtils.onSuccess(resp -> {
              assertEquals(200, resp.statusCode());
              if (remaining.decrementAndGet() == 0) {
                checkpoint2.succeed();
              }
            }));
          }));
      }
    }
    checkpoint2.awaitSuccess();
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
      assertEquals(HttpVersion.HTTP_1_0, req.version());
      HttpServerResponse resp = req.response();
      resp.write("Some-String");
      resp.end();
    });
    startServer(testAddress);
    MultiMap headers = client.request(new RequestOptions(requestOptions)
        .setProtocolVersion(HttpVersion.HTTP_1_0))
      .compose(request -> request
        .send().
        map(HttpResponseHead::headers))
      .await();
    assertNull(headers.get("Content-Length"));
  }

  @Test
  public void testPartialH2CAmbiguousRequest(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.POST, req.method());
      checkpoint.succeed();
    });
    Buffer fullRequest = Buffer.buffer("POST /whatever HTTP/1.1\r\n\r\n");
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress).onComplete(TestUtils.onSuccess(so -> {
      so.write(fullRequest.slice(0, 1));
      vertx.setTimer(1000, id -> {
        so.write(fullRequest.slice(1, fullRequest.length()));
      });
    }));
  }

  @Test
  public void testIdleTimeoutWithPartialH2CRequest(Checkpoint checkpoint) throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setIdleTimeout(1));
    server.requestHandler(req -> fail());
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress).onComplete(TestUtils.onSuccess(so -> {
      so.closeHandler(v -> {
        checkpoint.succeed();
      });
    }));
  }

  @Test
  public void testTLSDisablesH2CHandlers() throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get())
    ).connectionHandler(conn -> {
      Channel channel = ((Http1ServerConnection) conn).channel();
      for (Map.Entry<String, ChannelHandler> stringChannelHandlerEntry : channel.pipeline()) {
        ChannelHandler handler = stringChannelHandlerEntry.getValue();
        assertFalse(handler instanceof Http1xUpgradeToH2CHandler);
        assertFalse(handler instanceof Http1xOrH2CHandler);
      }
    }).requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions()
      .setTrustAll(true)
      .setSsl(true));
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testIdleTimeoutInfiniteSkipOfControlCharactersState(Checkpoint checkpoint) throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1));
    server.requestHandler(req -> {
      Assert.fail();
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    client.connect(testAddress)
      .onComplete(TestUtils.onSuccess(so -> {
      long id = vertx.setPeriodic(1, timerID -> {
        so.write(Buffer.buffer().setInt(0, 0xD));
      });
      so.closeHandler(v -> {
        vertx.cancelTimer(id);
        checkpoint.succeed();
      });
    }));
  }

  @Test
  public void testCompressedResponseWithConnectionCloseAndNoCompressionHeader() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(2048));
    server = vertx.createHttpServer(new HttpServerOptions().setCompressionSupported(true));
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer(testAddress);
    Buffer body = client.request(requestOptions)
      .compose(req -> req
        .putHeader("Connection", "close")
        .send()
        .compose(HttpClientResponse::body))
      .await();
    assertEquals(expected, body);
  }

  @Test
  public void testKeepAliveTimeoutHeader(Checkpoint checkpoint) throws Exception {
    AtomicBoolean sent = new AtomicBoolean();
    server.requestHandler(req -> {
      if (sent.compareAndSet(false, true)) {
        req.response().putHeader("keep-alive", "timeout=3").end();
      }
    });
    testKeepAliveTimeout(checkpoint, config.forClient().setKeepAliveTimeout(Duration.ofSeconds(30)), new PoolOptions().setHttp1MaxSize(1), 1);
  }

  @Test
  public void testKeepAliveTimeoutHeaderReusePrevious(Checkpoint checkpoint) throws Exception {
    AtomicBoolean sent = new AtomicBoolean();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      if (sent.compareAndSet(false, true)) {
        resp.putHeader("keep-alive", "timeout=3");
      }
      resp.end();
    });
    testKeepAliveTimeout(checkpoint, config.forClient().setKeepAliveTimeout(Duration.ofSeconds(30)), new PoolOptions().setHttp1MaxSize(1), 2);
  }

  @Test
  public void testKeepAliveTimeoutHeaderOverwritePrevious(Checkpoint checkpoint) throws Exception {
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
    testKeepAliveTimeout(checkpoint, config.forClient().setKeepAliveTimeout(Duration.ofSeconds(30)), new PoolOptions().setHttp1MaxSize(1), 2);
  }

  @Test
  public void testPausedHttpServerRequestPauseTheConnectionAtRequestEnd(Checkpoint checkpoint) throws Exception {
    int numRequests = 20;
    CountDownLatch latch = checkpoint.asLatch(numRequests);
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
    client = vertx.createHttpClient(new HttpClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0; i < numRequests; i++) {
      client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(TestUtils.onSuccess(req -> {
        req.send(Buffer.buffer("small")).onComplete(resp -> {
          latch.countDown();
        });
      }));
    }
  }

  @Test
  public void testPausedHttpClientResponseUnpauseTheConnectionAtRequestEnd(Checkpoint checkpoint) throws Exception {
    testHttpClientResponsePause(checkpoint, resp -> {
      resp.handler(buff -> {
        // Pausing the request here should have no effect since it's the last chunk
        assertEquals("ok", buff.toString());
        resp.pause();
      });
    });
  }

  @Test
  public void testHttpClientResponsePauseIsIgnoredAtRequestEnd(Checkpoint checkpoint) throws Exception {
    testHttpClientResponsePause(checkpoint, resp -> {
      resp.endHandler(v -> {
        // Pausing the request in end handler should be a no-op
        resp.pause();
      });
    });
  }

  private void testHttpClientResponsePause(Checkpoint checkpoint, Handler<HttpClientResponse> h) throws Exception {
    server.requestHandler(req -> req.response().end("ok"));
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
      req.send().onComplete(TestUtils.onSuccess(resp1 -> {
        h.handle(resp1);
        vertx.setTimer(10, timerId -> {
          // The connection should be resumed as it's ended
          client.request(requestOptions)
            .compose(HttpClientRequest::send)
            .onComplete(TestUtils.onSuccess(resp2 -> {
              assertSame(resp1.request().connection(), resp2.request().connection());
              resp2.endHandler(v -> checkpoint.succeed());
            }));
        });
      }));
    }));
  }

  @Test
  public void testPoolLIFOPolicy() throws Exception {
    List<HttpServerRequest> requests = Collections.synchronizedList(new ArrayList<>());
    server.requestHandler(requests::add);
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions(), new PoolOptions().setHttp1MaxSize(2));
    // Make two concurrent requests and finish one first
    List<Future<HttpConnection>> connections = Collections.synchronizedList(new ArrayList<>());
    // Use one event loop to be sure about response ordering
    for (int i = 0; i < 2; i++) {
      Future<HttpConnection> future = client.request(requestOptions)
        .compose(request -> request
          .send()
          .compose(response -> response
            .end()
            .map(b -> response.request().connection())));
      connections.add(future);
      int val = i;
      assertWaitUntil(() -> requests.size() == val + 1);
    }
    for (int i = 0;i < 2;i++) {
      requests.get(i).response().end();
      connections.get(i).await();
      // Make sure that connection lastResponseReceivedTimestamp are distinct
      Thread.sleep(5);
    }
    Future<HttpConnection> future = client.request(requestOptions)
      .compose(request -> request
        .send()
        .compose(response -> response
          .end()
          .map(b -> response.request().connection())));
    assertWaitUntil(() -> requests.size() == 3);
    requests.get(2).response().end();
    HttpConnection connection = future.await();
    assertSame(connection, connections.get(1).result());
  }

  @Test
  public void testHttpClientResponseThrowsExceptionInResponseHandler(Checkpoint checkpoint) throws Exception {
    testHttpClientResponseThrowsExceptionInHandler(checkpoint, null, (resp, failure) -> {
      throw failure;
    });
  }

  @Test
  public void testHttpClientResponseThrowsExceptionInChunkHandler(Checkpoint checkpoint) throws Exception {
    testHttpClientResponseThrowsExceptionInHandler(checkpoint, "blah", (resp, failure) -> {
      resp.handler(chunk -> {
        throw failure;
      });
    });
  }

  @Test
  public void testHttpClientResponseThrowsExceptionInEndHandler(Checkpoint checkpoint) throws Exception {
    testHttpClientResponseThrowsExceptionInHandler(checkpoint, null, (resp, failure) -> {
      resp.endHandler(v -> {
        throw failure;
      });
    });
  }

  private void testHttpClientResponseThrowsExceptionInHandler(
    Checkpoint checkpoint,
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
    CountDownLatch latch = checkpoint.asLatch(num);
    RuntimeException failure = new RuntimeException();
    for (int i = 0;i < num;i++) {
      client.request(requestOptions)
        .onComplete(TestUtils.onSuccess(req -> {
          req.send().onComplete(TestUtils.onSuccess(resp -> {
            ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
            ctx.exceptionHandler(err -> {
              if (err == failure) {
                latch.countDown();
              }
            });
            handler.accept(resp, failure);
          }));
        }));
    }
  }

  @Test
  public void testConnectionCloseDuringShouldCallHandleExceptionOnlyOnce(Checkpoint checkpoint) throws Exception {
    CompletableFuture<Void> continuation = new CompletableFuture<>();
    server.requestHandler(req -> {
      req.response().setChunked(true).write("chunk");
      continuation.thenAccept(v -> {
        req.connection().close();
      });
    });
    AtomicInteger count = new AtomicInteger();
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(PUT))
      .onComplete(TestUtils.onSuccess(put -> {
        put.response().onComplete(TestUtils.onSuccess(res -> {
          continuation.complete(null);
        }));
        put.setChunked(true);
        put.write(TestUtils.randomBuffer(10000));
        put.exceptionHandler(x-> {
          if (count.incrementAndGet() == 1) {
            vertx.setTimer(100, id -> {
              checkpoint.succeed();
            });
          }
        });
    }));
    // then stall until timeout and the exception handler will be called.
    checkpoint.awaitSuccess();
    assertEquals(1, count.get());
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
    Buffer body = client.request(new RequestOptions(requestOptions).setMethod(PUT))
      .compose(req -> req
        .send(expected)
        .expecting(that(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body))
      .await();
    assertEquals(expected, body);
  }

  @Test
  public void testPipelinedWithResponseSent(Checkpoint checkpoint) throws Exception {
    int numReq = 10;
    CountDownLatch latch = checkpoint.asLatch(numReq * 2);
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
        latch.countDown();
      });
      vertx.setTimer(1000, v -> {
        req.resume();
      });
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    vertx.runOnContext(v -> {
      // Run on context so requests are enqueued with a predictable ordering
      for (int i = 0;i < numReq;i++) {
        String expected = "" + i;
        client.request(new RequestOptions(requestOptions).setMethod(PUT))
          .compose(req -> req
            .send(TestUtils.randomAlphaString(1024))
            .expecting(that(resp -> assertEquals(200, resp.statusCode())))
            .compose(HttpClientResponse::body))
          .onComplete(TestUtils.onSuccess(body -> {
            assertEquals(expected, body.toString());
            latch.countDown();
          }));
      }
    });
  }

  @Test
  public void testPipelinedWithPendingResponse(Checkpoint checkpoint) throws Exception {
    int numReq = 10;
    CountDownLatch latch = checkpoint.asLatch(numReq);
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
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    vertx.runOnContext(v -> {
      // Run on context so requests are enqueued with a predictable ordering
      for (int i = 0;i < numReq;i++) {
        String expected = "" + i;
        client.request(new RequestOptions(requestOptions).setMethod(PUT))
          .compose(req -> req
            .send(TestUtils.randomAlphaString(1024))
            .expecting(that(resp -> assertEquals(200, resp.statusCode())))
            .compose(HttpClientResponse::body))
          .onComplete(TestUtils.onSuccess(body -> {
            assertEquals(expected, body.toString());
            latch.countDown();
          }));
      }
    });
  }

  @Test
  public void testPipelinedPostRequestStartedByResponseSent() throws Exception {
    String chunk1 = TestUtils.randomAlphaString(1024);
    String chunk2 = TestUtils.randomAlphaString(1024);
    Promise<Void> latch = Promise.promise();
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
          latch.complete();
          req.bodyHandler(body -> {
            assertEquals(chunk1 + chunk2, body.toString());
            req.response().end();
          });
          break;
      }
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    CountDownLatch latch1 = new CountDownLatch(1);
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(TestUtils.onSuccess(req -> {
      req.end(TestUtils.randomAlphaString(1024)).onComplete( TestUtils.onSuccess(v -> {
        latch1.countDown();
      }));
    }));
    TestUtils.awaitLatch(latch1);
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).compose(req -> {
      req
        .setChunked(true)
        .write(chunk1);
      latch.future().onComplete(TestUtils.onSuccess(v -> {
        req.end(chunk2);
      }));
      return req.response();
    }).await();
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
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST)).onComplete(TestUtils.onSuccess(req -> {
      req.end(Buffer.buffer(TestUtils.randomAlphaString(1024)));
    }));
    client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::end))
      .await();
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
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(TestUtils.onSuccess(req -> {
      req.end(Buffer.buffer(TestUtils.randomAlphaString(1024)));
    }));
    client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::end))
      .await();
  }

  @Test
  public void testPipeliningQueueDelayed(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3) throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true).setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions)
      .onComplete(TestUtils.onSuccess(req -> {
        req.response().onComplete(checkpoint1);
        req.writeHead();
        client.request(requestOptions)
          .compose(HttpClientRequest::send)
          .onComplete(checkpoint2);
        client.request(requestOptions)
          .compose(HttpClientRequest::send)
          .onComplete(checkpoint3);
        // Need to wait a little so requests 2 and 3 are appended to the first request
        vertx.setTimer(300, id -> {
          // This will end request 1 and make requests 2 and 3 progress
          req.end();
        });
    }));
  }

  @Test
  public void testHttpClientResponseBufferedWithPausedEnd(Checkpoint checkpoint) throws Exception {
    AtomicInteger i = new AtomicInteger();
    server.requestHandler(req -> {
      req.response().end("HelloWorld" + i.incrementAndGet());
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req1 -> {
      req1.send().onComplete(TestUtils.onSuccess(resp1 -> {
        // Response is paused but request is put back in the pool since the HTTP response fully arrived
        // but the response it's not yet delivered to the application as we pause the response
        resp1.pause();
        // Do a request on the same connection
        client.request(requestOptions).onComplete(TestUtils.onSuccess(req2 -> {
          req2.send().onComplete(TestUtils.onSuccess(resp2 -> {
            resp2.bodyHandler(body2 -> {
              // When the response arrives -> resume the first request
              assertEquals("HelloWorld2", body2.toString());
              resp1.bodyHandler(body1 -> {
                assertEquals("HelloWorld1", body1.toString());
                checkpoint.succeed();
              });
              resp1.resume();
            });
          }));
        }));
      }));
    }));
  }

  @Test
  public void testHttpClientResumeConnectionOnResponseOnLastMessage(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> req.response().end("ok"));
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req1 -> {
      req1.send().onComplete(TestUtils.onSuccess(resp1 -> {
        resp1.pause();
        // The connection resume is asynchronous and the end message will be received before connection resume happens
        resp1.resume();
        client.request(requestOptions)
          .compose(HttpClientRequest::send)
          .onComplete(checkpoint);
      }));
    }));
  }

  @Test
  public void testClientSetChunkedAutomatically(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
      MultiMap headers = req.headers();
      assertEquals(HttpHeaders.CHUNKED.toString(), headers.get(HttpHeaders.TRANSFER_ENCODING));
      checkpoint.succeed();
    });
    startServer(testAddress);
    HttpClientRequest request = client.request(requestOptions).await();
    assertFalse(request.isChunked());
    request.write("chunk");
    assertTrue(request.isChunked());
  }

  @Test
  public void testServerSetChunkedAutomatically() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse response = req.response();
      assertFalse(response.isChunked());
      response.write("chunk");
      assertTrue(response.isChunked());
      response.end();
    });
    startServer(testAddress);
    MultiMap headers = client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(response -> response
          .end()
          .map(response.headers()))
      ).await();
    assertEquals(HttpHeaders.CHUNKED.toString(), headers.get(HttpHeaders.TRANSFER_ENCODING));
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
      .await();
  }

  @Test
  public void testHttpServerRequestShouldCallExceptionHandlerWhenTheClosedHandlerIsCalled(Checkpoint checkpoint) throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), resp);
      sender.send();
      resp.closeHandler(v -> {
        Throwable failure = sender.close();
        if (failure != null) {
          Assert.fail(failure.getMessage());
        } else {
          checkpoint.succeed();
        }
      });
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(TestUtils.onSuccess(resp -> {
        vertx.setTimer(1000, id -> {
          resp.request().connection().close();
        });
      }));
  }

  @Test
  public void testHttpClientRequestShouldCallExceptionHandlerWhenTheClosedHandlerIsCalled(Checkpoint checkpoint) throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      vertx.setTimer(1000, id -> {
        req.connection().close();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions)
      .setMethod(PUT)
    ).onComplete(TestUtils.onSuccess(req -> {
      req.setChunked(true);
      CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), req);
      AtomicBoolean connected = new AtomicBoolean();
      AtomicBoolean done = new AtomicBoolean();
      req.exceptionHandler(err -> {
        assertTrue(connected.get());
        Throwable failure = sender.close();
        if (failure != null) {
          Assert.fail(failure.getMessage());
        } else if (done.compareAndSet(false, true)) {
          checkpoint.succeed();
        }
      });
      req.writeHead().onComplete(TestUtils.onSuccess(v -> {
        connected.set(true);
        sender.send();
      }));
    }));
  }

  @Test
  public void testHeaderNameValidation() {
    for (char c : "\u001c\u001d\u001e\u001f\0\t\n\u000b\f\r ,:;=\u0080".toCharArray()) {
      try {
        HttpUtils.validateHeaderName(Character.toString(c));
        Assert.fail("Char 0x" + Integer.toHexString(c) + " should not be valid");
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
        Assert.fail("String \"" + test + "\" should not be valid");
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
      Assert.fail();
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
          client.connect(testAddress).onComplete(TestUtils.onSuccess(so -> {
            so.write("GET /some/path HTTP/1.1\r\n" + "Host: vertx.io\r\n" + (position ? invalid : "") + "Transfer-encoding" + (position ? "" : invalid) + ": chunked\r\n\r\n");
            so.closeHandler(v -> latch.countDown());
          }));
          TestUtils.awaitLatch(latch);
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
      Assert.fail();
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    try {
      CountDownLatch latch = new CountDownLatch(1);
      Buffer response = Buffer.buffer();
      client.connect(testAddress).onComplete(TestUtils.onSuccess(so -> {
        so.write("GET /some/path HTTP/1.1\r\n" +
          "Host: vertx.io\r\n" +
          "\uD83D\uDE31: val\r\n" +
          "\r\n");
        so.handler(response::appendBuffer);
        so.closeHandler(v -> {
          latch.countDown();
        });
      }));
      TestUtils.awaitLatch(latch);
      assertTrue(response.toString().startsWith("HTTP/1.1 400 Bad Request\r\n"));
    } finally {
      client.close();
    }
  }

  @Test
  public void testInvalidHttpResponseHeader(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
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
    server
      .listen(testAddress)
      .await();
    client
      .request(requestOptions)
      .onComplete(TestUtils.onSuccess(req -> {
      req.connection().exceptionHandler(err -> {
        assertEquals(IllegalArgumentException.class, err.getClass());
        checkpoint1.succeed();
      });
      req.send().onComplete(TestUtils.onFailure(err -> {
        assertEquals(IllegalArgumentException.class, err.getClass());
        checkpoint2.succeed();
      }));
    }));
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
    Buffer body = client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals("chunked", resp.getHeader("transfer-encoding"));
          return resp.body();
        }))
      .await();
    assertEquals("the-chunk", body.toString());
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
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).compose(req -> {
      req
        .setChunked(true)
        .write("the-chunk");
      vertx.setTimer(1, id -> {
        req.end();
      });
      return req
        .response()
        .compose(HttpClientResponse::end);
    }).await();
  }

  @Test
  public void testClosingVertxCloseSharedServers() throws Exception {
    int numServers = 2;
    List<HttpServerInternal> servers = new ArrayList<>();
    Vertx vertx = Vertx.vertx();
    try {
      for (int i = 0;i < numServers;i++) {
        HttpServer server = vertx.createHttpServer().requestHandler(req -> {

        });
        startServer(testAddress, server);
        servers.add((HttpServerInternal) server);
      }
    } finally {
      vertx.close().await();
    }
    servers.forEach(server -> {
      assertTrue(server.isClosed());
    });
  }

  @Test
  public void testRandomSharedPortInVerticle(Checkpoint checkpoint) {
    testRandomPortInVerticle(checkpoint, 3, new int[]{-1}, 1);
  }

  @Test
  public void testRandomSharedPortsInVerticle(Checkpoint checkpoint) {
    testRandomPortInVerticle(checkpoint, 3, new int[]{-1, -2}, 2);
  }

  @Test
  public void testRandomPortsInVerticle1(Checkpoint checkpoint) {
    testRandomPortInVerticle(checkpoint, 3, new int[]{0}, 3);
  }

  @Test
  public void testRandomPortsInVerticle2(Checkpoint checkpoint) {
    testRandomPortInVerticle(checkpoint, 3, new int[]{0, 0}, 6);
  }

  private void testRandomPortInVerticle(Checkpoint checkpoint, int instances, int[] bindPorts, int expectedPorts) {
    CountDownLatch latch = checkpoint.asLatch(instances);
    Assume.assumeTrue("Domain socket don't pass this test", testAddress.isInetSocket());
    Set<Integer> ports = Collections.synchronizedSet(new HashSet<>());
    vertx.deployVerticle(() -> new VerticleBase() {
      @Override
      public Future<?> start() {
        List<Future<HttpServer>> futures = new ArrayList<>();
        for (int bindPort : bindPorts) {
          futures.add(vertx.createHttpServer().requestHandler(req -> {
            req.response().end();
          }).listen(bindPort, DEFAULT_HTTP_HOST));
        }
        return Future
          .all(futures)
          .andThen(TestUtils.onSuccess(cf -> {
          futures.stream()
            .map(Future::result)
            .map(HttpServer::actualPort)
            .forEach(port -> {
            assertTrue(port > 0);
            ports.add(port);
          });
        }));
      }
    }, new DeploymentOptions().setInstances(instances)).onComplete(TestUtils.onSuccess(id -> {
      assertEquals(expectedPorts, ports.size());
      int port = ports.iterator().next();
      for (int i = 0;i < instances;i++) {
        client.request(new RequestOptions()
          .setHost(DEFAULT_HTTP_HOST)
          .setPort(port)).onComplete(TestUtils.onSuccess(req -> {
          req.send().onComplete(TestUtils.onSuccess(v -> {
            latch.countDown();
          }));
        }));
      }
    }));
  }

  @Test
  public void testResponseEndHandlersConnectionClose(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
      req.response().endHandler(v -> checkpoint.succeed());
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(request -> request
        .putHeader(HttpHeaders.CONNECTION, "close")
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .await();
  }

  @Test
  public void testUnsolicitedHttpResponse(Checkpoint checkpoint) throws Exception {
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
    server
      .listen(testAddress)
      .await();
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions())
      .withConnectHandler(conn -> {
        AtomicBoolean failed = new AtomicBoolean();
        conn.exceptionHandler(err -> failed.set(true));
        conn.closeHandler(v -> {
          assertTrue(failed.get());
          checkpoint.succeed();
        });
      })
      .build();
    client.request(requestOptions).compose(req -> req
        .send()
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testClientConnectionGracefulShutdown(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    int numReq = 3;
    AtomicReference<HttpConnection> clientConnection = new AtomicReference<>();
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      if (count.getAndIncrement() == 0) {
        clientConnection
          .get()
          .shutdown()
          .onComplete(checkpoint1);
      }
      req.response().end();
    });
    startServer(testAddress);
    AtomicInteger responses = new AtomicInteger();
    AtomicReference<Handler<HttpConnection>> connectHandler = new AtomicReference<>();
    connectHandler.set(conn -> {
      AtomicBoolean failed = new AtomicBoolean();
      conn.exceptionHandler(err -> failed.set(true));
      conn.closeHandler(v -> {
        assertTrue(failed.get());
        checkpoint2.succeed();
      });
    });
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setPipelining(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(conn -> connectHandler.get().handle(conn))
      .build();
    vertx.runOnContext(v1 -> {
      connectHandler.set(conn -> {
        conn.closeHandler(v2 -> {
          assertEquals(3, responses.get());
          checkpoint2.succeed();
        });
        clientConnection.set(conn);
      });
      for (int i = 0;i < numReq;i++) {
        client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
          req.send().onComplete(TestUtils.onSuccess(resp -> {
            responses.incrementAndGet();
          }));
        }));
      }
    });
  }

  @Test
  public void testClientConnectionGracefulShutdownWhenRequestCompletedAfterResponse(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(TestUtils.onSuccess(req -> {
      req.response().onComplete(TestUtils.onSuccess(resp -> {
          AtomicBoolean requestEnded = new AtomicBoolean();
          HttpConnection conn = req.connection();
          conn.closeHandler(v -> {
            assertTrue(requestEnded.get());
            checkpoint1.succeed();
          });
          conn.shutdown().onComplete(checkpoint2);
          resp.endHandler(v -> {
            vertx.runOnContext(v2 -> {
              requestEnded.set(true);
              req.end();
            });
          });
        }));
      req.setChunked(true).writeHead();
    }));
  }

  @Ignore("fixme - does not pass anymore")
  @Test
  public void testClientConnectionShutdownTimedOut(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3) throws Exception {
    AtomicReference<HttpConnection> clientConnectionRef = new AtomicReference<>();
    int numReq = 3;
    CountDownLatch latch = checkpoint1.asLatch(numReq + 2);
    server.requestHandler(req -> {
      HttpConnection clientConnection = clientConnectionRef.getAndSet(null);
      if (clientConnection != null) {
        long now = System.currentTimeMillis();
        clientConnection
          .shutdown(500, TimeUnit.MILLISECONDS)
          .onComplete(TestUtils.onSuccess(v -> {
            assertTrue(System.currentTimeMillis() - now >= 500L);
            checkpoint2.succeed();
        }));
      }
    });
    startServer(testAddress);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setPipelining(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(conn -> {
        clientConnectionRef.set(conn);
        long now = System.currentTimeMillis();
        conn.closeHandler(v -> {
          assertTrue(System.currentTimeMillis() - now >= 500L);
          checkpoint3.succeed();
        });
      })
      .build();
    for (int i = 0;i < numReq;i++) {
      client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onFailure(err -> latch.countDown()));
      }));
    }
  }

  @Test
  public void testClientConnectionShutdownNow(Checkpoint checkpoint) throws Exception {
    AtomicReference<HttpConnection> clientConnectionRef = new AtomicReference<>();
    server.requestHandler(req -> {
      long now = System.currentTimeMillis();
      clientConnectionRef.get().close()
        .onComplete(TestUtils.onSuccess(v -> {
          assertTrue(System.currentTimeMillis() - now <= 2000L);
          checkpoint.succeed();
      }));
    });
    startServer(testAddress);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setPipelining(true))
      .with(new PoolOptions().setHttp1MaxSize(1))
      .withConnectHandler(clientConnectionRef::set)
      .build();
    assertThatThrownBy(() -> {
      client
        .request(requestOptions)
        .compose(HttpClientRequest::send)
        .await();
    });
  }

  @Test
  public void testServerConnectionGracefulShutdown(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      AtomicBoolean ended = new AtomicBoolean();
      conn.closeHandler(v -> {
        assertTrue(ended.get());
        checkpoint1.succeed();
      });
      Future<Void> shutdown = conn.shutdown(10, TimeUnit.SECONDS);
      shutdown.onComplete(TestUtils.onSuccess(v -> {
        assertTrue(ended.get());
        checkpoint2.succeed();
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
      ).await();
  }

  @Test
  public void testServerConnectionGracefulShutdownWithPipelinedRequest(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3) throws Exception {
    AtomicInteger count = new AtomicInteger();
    AtomicInteger status = new AtomicInteger();
    server.requestHandler(req -> {
      switch (count.getAndIncrement()) {
        case 0:
          vertx.setTimer(1, id1 -> {
            HttpConnection conn = req.connection();
            conn.closeHandler(v -> {
              assertEquals(2, status.get());
              checkpoint1.succeed();
            });
            Future<Void> shutdown = conn.shutdown(10, TimeUnit.SECONDS);
            shutdown.onComplete(TestUtils.onSuccess(v -> {
              assertEquals(2, status.get());
              checkpoint2.succeed();
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
    CountDownLatch latch = checkpoint3.asLatch(2);
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 2;i++) {
      client.request(requestOptions).compose(req -> req
          .send()
          .expecting(that(resp -> assertEquals(200, resp.statusCode())))
          .compose(HttpClientResponse::body)
        )
        .onComplete(TestUtils.onSuccess(body -> latch.countDown()));
    }
  }

  @Test
  public void testServerConnectionShutdownTimedOut(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      long now = System.currentTimeMillis();
      conn.shutdown(1, TimeUnit.SECONDS);
      conn.closeHandler(v -> {
        assertTrue(System.currentTimeMillis() - now >= 1000);
        checkpoint.succeed();
      });
    });
    startServer(testAddress);
    assertThatThrownBy(() -> {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .await();
    });
  }

  @Test
  public void testClientNetSocketPooling(Checkpoint checkpoint) throws Exception {
    int maxPoolSize = 5; // Default
    int num = 6;
    CountDownLatch latch = checkpoint.asLatch(num);
    server.requestHandler(req -> {
      req.toNetSocket().onComplete(TestUtils.onSuccess(so -> {
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
      ).onComplete(TestUtils.onSuccess(req -> {
        req.connect().onComplete(TestUtils.onSuccess(resp -> {
          NetSocket sock = resp.netSocket();
          int val = count.incrementAndGet();
          assertTrue("Expected " + val + " <= " + maxPoolSize, val <= maxPoolSize);
          sock.closeHandler(v -> {
            count.decrementAndGet();
            latch.countDown();
          });
        }));
      }));
    }
  }

  @Test
  public void testHttpUpgrade(Checkpoint checkpoint) {
    testHttpConnect(checkpoint, new RequestOptions(requestOptions).setMethod(HttpMethod.GET).addHeader(HttpHeaders.CONNECTION, "UpGrAdE"), 101);
  }

  @Test
  public void testServerResponseReset(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
      req.response().reset();
    });
    startServer(testAddress);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions())
      .withConnectHandler(conn -> conn.closeHandler(v -> checkpoint.succeed()))
      .build();
    assertThatThrownBy(() -> client
      .request(requestOptions)
      .compose(HttpClientRequest::send)
      .await());
  }

  @Test
  public void testNetSocketUpgradeSuccess(Checkpoint checkpoint) {
    testNetSocketUpgradeSuccess(checkpoint,  null);
  }

  @Test
  public void testNetSocketUpgradeSuccessWithPayload(Checkpoint checkpoint) {
    testNetSocketUpgradeSuccess(checkpoint, Buffer.buffer("the-payload"));
  }

  private void testNetSocketUpgradeSuccess(Checkpoint checkpoint, Buffer payload) {
    server.requestHandler(req -> {
      req.body().onComplete(TestUtils.onSuccess(body -> {
        if (payload != null) {
          assertEquals(payload, body);
        }
        req.response().setStatusCode(101);
        req.toNetSocket().onComplete(TestUtils.onSuccess(so -> {
          so.handler(buff -> {
            assertEquals("ping", buff.toString());
            so.write("pong");
            so.close();
          });
        }));
      }));
    });
    server.listen(testAddress).onComplete(TestUtils.onSuccess(s -> {
      RequestOptions request = new RequestOptions(requestOptions)
        .setMethod(HttpMethod.GET)
        .addHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE);
      if (payload != null) {
        request.addHeader(HttpHeaders.CONTENT_LENGTH, "" + payload.length());
      }
      client.request(request
      ).onComplete(TestUtils.onSuccess(req -> {
        req.connect().onComplete(TestUtils.onSuccess(resp -> {
          NetSocket so = resp.netSocket();
          so.write("ping");
          so.handler(buff -> {
            assertEquals("pong", buff.toString());
            so.close()
              .onComplete(checkpoint);
          });
        }));
        if (payload != null) {
          req.end(payload);
        }
      }));
    }));
  }

  @Test
  public void testClientEventLoopSize(Checkpoint checkpoint) throws Exception {
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
    List<EventLoop> eventLoops = Collections.synchronizedList(new ArrayList<>());
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions())
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
    Future.all(futures).onComplete(TestUtils.onSuccess(v -> {
      assertEquals(maxPoolSize, eventLoops.size());
      assertEquals(size, new HashSet<>(eventLoops).size());
      checkpoint.succeed();
    }));
  }

  @Test
  public void testServerResponseChunkedSend() throws Exception {
    testServerResponseSend(true);
  }

  @Test
  public void testClientCloseWaiters(Checkpoint checkpoint) throws Exception {
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      count.incrementAndGet();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    int num = 4;
    CountDownLatch latch = checkpoint.asLatch(num);
    for (int i = 0;i < num;i++) {
      int val = i;
      client
        .request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(TestUtils.onFailure(err -> {
        if (val == 0) {
          assertEquals("Connection was closed", err.getMessage());
        } else {
          assertTrue("Expected " + err.getMessage() + " to contain with <closed> or be <Resource manager shutdown> instead of \""
            + err.getMessage() +  "\"", err.getMessage().contains("closed") || err.getMessage().equals("Resource manager shutdown"));
        }
        latch.countDown();
      }));
      if (i == 0) {
        TestUtils.assertWaitUntil(() -> count.get() > 0);
      }
    }
    client
      .close()
      .await();
  }

  @Test
  public void testClientShutdownClose(Checkpoint checkpoint) throws Exception {
    int num = 4;
    CountDownLatch latch = checkpoint.asLatch(num);
    AtomicReference<HttpServerRequest> ref = new AtomicReference<>();
    server.requestHandler(req -> {
      ref.compareAndSet(null, req);
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions(), new PoolOptions().setHttp1MaxSize(1));
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
            String msg = ar.cause().getMessage();
            assertTrue("Expected " + msg + " to contain 'closed' or 'shutdown'", msg.contains("closed") || msg.contains("shutdown"));
          }
          latch.countDown();
        });
      if (i == 0) {
        assertWaitUntil(() -> ref.get() != null);
      }
    }
    Future<Void> shutdown = client.shutdown(2, TimeUnit.SECONDS);
    ref.get().response().end("hello");
    shutdown.await();
  }

  @Test
  public void testServerShutdownClose(Checkpoint checkpoint) throws Exception {
    int num = 4;
    CountDownLatch latch = checkpoint.asLatch(num);
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
        .onComplete(TestUtils.onFailure(err -> {
          latch.countDown();
        }));
    }
    assertWaitUntil(() -> inflight.get() == 4);
    Future<Void> shutdown = server.shutdown(2, TimeUnit.SECONDS);
    shutdown.await();
    assertTrue(System.currentTimeMillis() - now > 2000);
    assertWaitUntil(() -> closures.size() == 4);
    for (Long ts : closures) {
      assertTrue(ts - now > 2000);
    }
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
      .await();
  }

  @Test
  public void testServerMissingHostHeader(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
      assertNull(req.authority());
      assertFalse(((HttpServerRequestInternal) req).isValidAuthority());
      checkpoint.succeed();
    });
    startServer(testAddress);
    NetClient nc = vertx.createNetClient();
    nc.connect(testAddress).onComplete(TestUtils.onSuccess(so -> {
      so.write("GET / HTTP/1.1\r\n\r\n");
    }));
  }

  @Test
  public void testClientMissingHostHeader() throws Exception {
    server.requestHandler(req -> {
      assertNull(req.authority());
      assertFalse(((HttpServerRequestInternal) req).isValidAuthority());
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(request -> request
        .authority(null)
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .await();
  }

  @Test
  public void testCanUpgradeToWebSocket(Checkpoint checkpoint) throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.GET)
      .putHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)
      .putHeader(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
    doTestCanUpgradeToWebSocket(checkpoint, config, true);
  }

  @Test
  public void testCanUpgradeToWebSocketFirefox(Checkpoint checkpoint) throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.GET)
      .putHeader(HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE + ", " + HttpHeaders.UPGRADE)
      .putHeader(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
    doTestCanUpgradeToWebSocket(checkpoint, config, true);
  }

  @Test
  public void testCannotUpgradePostRequestToWebSocket(Checkpoint checkpoint) throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.POST)
      .putHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)
      .putHeader(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
    doTestCanUpgradeToWebSocket(checkpoint, config, false);
  }

  @Test
  public void testCannotUpgradeToWebSocketIfConnectionHeaderIsMissing(Checkpoint checkpoint) throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.GET)
      .putHeader(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
    doTestCanUpgradeToWebSocket(checkpoint, config, false);
  }

  @Test
  public void testCannotUpgradeToWebSocketIfUpgradeDoesNotContainWebsocket(Checkpoint checkpoint) throws Exception {
    UnaryOperator<RequestOptions> config = ro -> ro.setMethod(HttpMethod.GET)
      .putHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)
      .putHeader(HttpHeaders.UPGRADE, "foo");
    doTestCanUpgradeToWebSocket(checkpoint, config, false);
  }

  private void doTestCanUpgradeToWebSocket(Checkpoint checkpoint, UnaryOperator<RequestOptions> config, boolean shouldSucceed) throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      if (req.canUpgradeToWebSocket()) {
        resp.headers()
          .set(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)
          .set(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET);
        req.toNetSocket().onComplete(TestUtils.onSuccess(sock -> {
          sock.write(Buffer.buffer("foo"));
        }));
      } else {
        resp.setStatusCode(500).end();
      }
    });
    startServer(testAddress);
    client.request(config.apply(new RequestOptions(requestOptions))).onComplete(TestUtils.onSuccess(req -> {
      req.response().onComplete(TestUtils.onSuccess(resp -> {
        if (shouldSucceed) {
          assertEquals(101, resp.statusCode());
          resp.netSocket().handler(buffer -> {
            assertEquals("foo", buffer.toString());
            checkpoint.succeed();
          });
        } else {
          assertEquals(500, resp.statusCode());
          checkpoint.succeed();
        }
      }));
      req.send();
    }));
  }

  @Test
  public void testDoNotRecycleWhenRequestIsNotEnded(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.requestHandler(request -> {
      HttpServerResponse resp = request.response();
      resp.end().onComplete(TestUtils.onSuccess(v -> {
        request.connection().close();
      }));
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 2;i++) {
      int val = i;
      Future<HttpClientRequest> fut = client.request(requestOptions);
      fut.onComplete(ar -> {
        switch (val) {
          case 0:
            assertTrue(ar.succeeded());
            HttpClientRequest req = ar.result();
            req.writeHead();
            req.response().onComplete(checkpoint1);
            break;
          case 1:
            assertTrue(ar.succeeded());
            checkpoint2.succeed();
            break;
        }
      });
    }
  }

  @Test
  public void testRecycleConnectionOnRequestEnd(Checkpoint checkpoint) throws Exception {
    int numRequests = 10;
    CountDownLatch latch = checkpoint.asLatch(numRequests);
    server.requestHandler(request -> {
      request.response().end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < numRequests;i++) {
      Future<HttpClientRequest> fut = client.request(requestOptions);
      fut.onComplete(TestUtils.onSuccess(request -> {
        request.setChunked(true).writeHead();
        request.response().compose(HttpClientResponse::body).onComplete(TestUtils.onSuccess(v -> {
          vertx.setTimer(10, id -> request.end());
          latch.countDown();
        }));
      }));
    }
  }

  @Test
  public void testFailPendingRequestAllocationWhenConnectionIsClosed(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.requestHandler(request -> {
      HttpServerResponse resp = request.response();
      resp.end().onComplete(TestUtils.onSuccess(v -> {
        request.connection().close();
      }));
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < 2;i++) {
      int val = i;
      Future<HttpClientRequest> fut = client.request(requestOptions);
      fut.onComplete(ar -> {
        switch (val) {
          case 0:
            assertTrue(ar.succeeded());
            HttpClientRequest req = ar.result();
            req.writeHead();
            req.response().onComplete(checkpoint1);
            break;
          case 1:
            assertTrue(ar.failed());
            checkpoint2.succeed();
            break;
        }
      });
    }
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
      .onComplete(TestUtils.onSuccess(req -> {
      req.setChunked(true);
      vertx.setPeriodic(10, id -> {
        if (req.writeQueueFull()) {
          vertx.cancelTimer(id);
        } else {
          req.write("chunk");
        }
      });
    }));
  }

  @Test
  public void testServerRollingUpdate() throws Exception {

    class WebServer extends VerticleBase {
      HttpServer server;
      final String msg;
      WebServer(String msg) {
        this.msg = msg;
      }

      @Override
      public Future<?> start() {
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
        return server.listen(8080, "localhost");
      }
      @Override
      public Future<?> stop() {
        return server.shutdown();
      }
    }

    List<WebServer> servers = new ArrayList<>();
    for (int i = 0;i < 3;i++) {
      WebServer server = new WebServer("server-" + i);
      vertx.deployVerticle(server).await();
      servers.add(server);
    }

    Set<String> responses = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < 3;i++) {
      client.request(new RequestOptions()
        .setPort(8080)
        .setHost("localhost")).onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
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
    vertx.undeploy(servers.remove(0).deploymentID()).await();
    assertTrue(System.currentTimeMillis() - now >= 500);

    WebServer server = new WebServer("server-" + 3);
    vertx.deployVerticle(server).await();
    servers.add(server);

    responses.clear();
    for (int i = 0;i < 3;i++) {
      client.request(new RequestOptions()
        .setPort(8080)
        .setHost("localhost")).onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
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


  @Test
  public void testUnsolicitedMessagesAreTreatedAsInvalid(Checkpoint checkpoint) throws Exception {
    NetServer server = vertx
      .createNetServer()
      .connectHandler(so -> {
        so.write("HTTP/1.1 200 OK\r\n" +
          "Content-Type: text/plain\r\n" +
          "Content-Length: 11\r\n" +
          "\r\n" +
          "Hello World");
      });
    server.listen(testAddress).await();

    HttpClientAgent client = vertx.httpClientBuilder().withConnectHandler(conn -> {
      List<Object> invalidMessages = new ArrayList<>();
      ((HttpClientConnection) conn).invalidMessageHandler(invalidMessages::add);
      conn.exceptionHandler(err -> {
      });
      conn.closeHandler(v -> {
        assertEquals(2, invalidMessages.size());
        assertTrue(invalidMessages.get(0) instanceof io.netty.handler.codec.http.HttpResponse);
        assertTrue(invalidMessages.get(1) instanceof io.netty.handler.codec.http.LastHttpContent);
        checkpoint.succeed();
      });
    }).build();

    client.request(requestOptions);
  }

  @Test
  public void testImmutableHeaders() throws Exception {
    MultiMap headers = HttpHeaders
      .headers()
      .set(HttpHeaders.CONTENT_LENGTH, "11")
      .set(HttpHeaders.CONTENT_TYPE, "text/plain")
      .copy(false);
    AtomicReference<MultiMap> headersRef = new AtomicReference<>();
    server.connectionHandler(conn -> {
      HttpServerConnection serverConn = (HttpServerConnection) conn;
      ChannelPipeline pipeline = serverConn.channelHandlerContext().pipeline();
      pipeline.addBefore("handler", "filter", new ChannelOutboundHandlerAdapter() {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
          if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            headersRef.set((MultiMap) response.headers());
          }
          super.write(ctx, msg, promise);
        }
      });
    });
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.headers().setAll(headers);
      resp
        .end("Hello World");
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end)
      ).await();
    assertSame(((Http1xHeaders) headersRef.get()).iteratorCharSequence().next(), ((Http1xHeaders) headers).iteratorCharSequence().next());
  }

  @Test
  public void testStrictThreadMode() throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions().setStrictThreadMode(true));
    server.requestHandler(request -> {
      Context ctx = vertx.getOrCreateContext();
      new Thread(() -> {
        HttpServerResponse response = request.response();
        try {
          response.end();
          Assert.fail();
        } catch (IllegalStateException e) {
          ctx.runOnContext(v -> {
            response.end();
          });
        }
      }).start();
    });
    startServer(testAddress);

    client.request(requestOptions)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end)
      ).await();
  }

  @Test
  public void testRequestHeadersConstant() throws Exception {
    System.setProperty(SysProps.INTERN_COMMON_HTTP_REQUEST_HEADERS_TO_LOWER_CASE.name, "true");
    try {
      server.requestHandler(request -> {
        IdentityHashMap<CharSequence, CharSequence> expected = new IdentityHashMap<>();
        expected.put(HttpHeaders.CONNECTION, HttpHeaders.CONNECTION);
        expected.put(HttpHeaders.ACCEPT, HttpHeaders.ACCEPT);
        expected.put(HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_TYPE);
        expected.put(HttpHeaders.CONTENT_LENGTH, HttpHeaders.CONTENT_LENGTH);
        expected.put(HttpHeaders.HOST, HttpHeaders.HOST);
        io.netty.handler.codec.http.HttpHeaders nettyHeaders = (io.netty.handler.codec.http.HttpHeaders) request.headers();
        Iterator<Map.Entry<CharSequence, CharSequence>> it = nettyHeaders.iteratorCharSequence();
        while (it.hasNext()) {
          Map.Entry<CharSequence, CharSequence> entry = it.next();
          expected.remove(entry.getKey());
        }
        assertEquals(Collections.emptySet(), expected.keySet());
        request.response().end();
      });
      startServer(testAddress);

      client.request(requestOptions)
        .compose(request -> request
          .putHeader("host", "localhost:8080")
          .putHeader("connection", "keep-alive")
          .putHeader("accept", "text/plain")
          .putHeader("content-type", "text/plain")
          .putHeader("content-length", "11")
          .send("Hello World")
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::end)
        ).await();
      client.request(requestOptions)
        .compose(request -> request
          .putHeader("Host", "localhost:8080")
          .putHeader("Connection", "keep-alive")
          .putHeader("Accept", "text/plain")
          .putHeader("Content-Type", "text/plain")
          .putHeader("Content-Length", "11")
          .send("Hello World")
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::end)
        ).await();
    } finally {
      System.clearProperty(SysProps.INTERN_COMMON_HTTP_REQUEST_HEADERS_TO_LOWER_CASE.name);
    }
  }

  @Test
  public void testEagerCreateRequestInboundQueueForWorkers() throws Exception {
    server.requestHandler(req -> {
      AtomicBoolean paused = new AtomicBoolean(true);
      req.pause();
      req.endHandler(v -> {
        assertFalse(paused.get());
        req.response().end();
      });
      vertx.runOnContext(v1 -> {
        paused.set(false);
        req.resume();
      });
    });
    Context ctx = ((VertxInternal)vertx).createWorkerContext();
    startServer(testAddress, ctx, server);

    client.request(requestOptions).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testListenSocketAddress(Checkpoint checkpoint) throws Exception {
    NetClient netClient = vertx.createNetClient();
    server.requestHandler(req -> req.response().end());
    SocketAddress sockAddress = SocketAddress.inetSocketAddress(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
    startServer(sockAddress);
    netClient
      .connect(sockAddress)
      .onComplete(TestUtils.onSuccess(sock -> {
        sock.handler(buf -> {
          assertTrue("Response is not an http 200", buf.toString("UTF-8").startsWith("HTTP/1.1 200 OK"));
          checkpoint.succeed();
        });
        sock.write("GET / HTTP/1.1\r\n\r\n");
      }));

    try {
      checkpoint.await();
    } finally {
      netClient.close();
    }
  }

  // Host with no port (common when a proxy forwards hostname-only Host header)
  @Test
  public void testAbsoluteURINoPortInHostHeader() throws Exception {
    server.requestHandler(req -> req.response().end(req.absoluteURI()));
    startServer(testAddress);
    Buffer body = client.request(new RequestOptions(requestOptions).setURI("/path"))
      .compose(req -> {
        req.putHeader("Host", "example.com");
        return req.send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::body);
      })
      .await();
    Assert.assertEquals("absoluteURI() should not synthesize ':-1'", "http://example.com/path", body.toString());
  }

}
