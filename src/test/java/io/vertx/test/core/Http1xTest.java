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

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientRequestImpl;
import io.vertx.core.impl.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.Pump;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

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

    assertEquals(HttpClientOptions.DEFAULT_MAX_WAIT_QUEUE_SIZE, options.getMaxWaitQueueSize());
    assertEquals(options, options.setMaxWaitQueueSize(100));
    assertEquals(100, options.getMaxWaitQueueSize());

    Http2Settings initialSettings = randomHttp2Settings();
    assertEquals(new Http2Settings(), options.getInitialSettings());
    assertEquals(options, options.setInitialSettings(initialSettings));
    assertEquals(initialSettings, options.getInitialSettings());
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

    Http2Settings initialSettings = randomHttp2Settings();
    assertEquals(new Http2Settings(), options.getInitialSettings());
    assertEquals(options, options.setInitialSettings(initialSettings));
    assertEquals(initialSettings, options.getInitialSettings());
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

    boolean verifyHost = rand.nextBoolean();
    int maxPoolSize = TestUtils.randomPositiveInt();
    boolean keepAlive = rand.nextBoolean();
    boolean pipelining = rand.nextBoolean();
    boolean tryUseCompression = rand.nextBoolean();
    HttpVersion protocolVersion = HttpVersion.HTTP_1_0;
    int maxWaitQueueSize = TestUtils.randomPositiveInt();
    HttpVersion alpnFallbackProtocolVersion = TestUtils.randomBoolean() ? HttpVersion.HTTP_1_1 : HttpVersion.HTTP_1_0;
    Http2Settings initialSettings = randomHttp2Settings();

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
    options.setKeyStoreOptions(keyStoreOptions);
    options.setTrustStoreOptions(trustStoreOptions);
    options.addEnabledCipherSuite(enabledCipher);
    options.setConnectTimeout(connectTimeout);
    options.setTrustAll(trustAll);
    options.addCrlPath(crlPath);
    options.addCrlValue(crlValue);
    options.setVerifyHost(verifyHost);
    options.setMaxPoolSize(maxPoolSize);
    options.setKeepAlive(keepAlive);
    options.setPipelining(pipelining);
    options.setTryUseCompression(tryUseCompression);
    options.setProtocolVersion(protocolVersion);
    options.setMaxWaitQueueSize(maxWaitQueueSize);
    options.setInitialSettings(initialSettings);
    HttpClientOptions copy = new HttpClientOptions(options);
    assertEquals(sendBufferSize, copy.getSendBufferSize());
    assertEquals(receiverBufferSize, copy.getReceiveBufferSize());
    assertEquals(reuseAddress, copy.isReuseAddress());
    assertEquals(trafficClass, copy.getTrafficClass());
    assertEquals(tcpNoDelay, copy.isTcpNoDelay());
    assertEquals(tcpKeepAlive, copy.isTcpKeepAlive());
    assertEquals(soLinger, copy.getSoLinger());
    assertEquals(usePooledBuffers, copy.isUsePooledBuffers());
    assertEquals(idleTimeout, copy.getIdleTimeout());
    assertEquals(ssl, copy.isSsl());
    assertNotSame(keyStoreOptions, copy.getKeyCertOptions());
    assertEquals(ksPassword, ((JksOptions) copy.getKeyCertOptions()).getPassword());
    assertNotSame(trustStoreOptions, copy.getTrustOptions());
    assertEquals(tsPassword, ((JksOptions)copy.getTrustOptions()).getPassword());
    assertEquals(1, copy.getEnabledCipherSuites().size());
    assertTrue(copy.getEnabledCipherSuites().contains(enabledCipher));
    assertEquals(connectTimeout, copy.getConnectTimeout());
    assertEquals(trustAll, copy.isTrustAll());
    assertEquals(1, copy.getCrlPaths().size());
    assertEquals(crlPath, copy.getCrlPaths().get(0));
    assertEquals(1, copy.getCrlValues().size());
    assertEquals(crlValue, copy.getCrlValues().get(0));
    assertEquals(verifyHost, copy.isVerifyHost());
    assertEquals(maxPoolSize, copy.getMaxPoolSize());
    assertEquals(keepAlive, copy.isKeepAlive());
    assertEquals(pipelining, copy.isPipelining());
    assertEquals(tryUseCompression, copy.isTryUseCompression());
    assertEquals(protocolVersion, copy.getProtocolVersion());
    assertEquals(maxWaitQueueSize, copy.getMaxWaitQueueSize());
    assertEquals(initialSettings, copy.getInitialSettings());
  }

  @Test
  public void testDefaultClientOptionsJson() {
    HttpClientOptions def = new HttpClientOptions();
    HttpClientOptions json = new HttpClientOptions(new JsonObject());
    assertEquals(def.getMaxPoolSize(), json.getMaxPoolSize());
    assertEquals(def.isKeepAlive(), json.isKeepAlive());
    assertEquals(def.isPipelining(), json.isPipelining());
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
    assertEquals(def.getInitialSettings(), json.getInitialSettings());
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
    boolean tryUseCompression = rand.nextBoolean();
    HttpVersion protocolVersion = HttpVersion.HTTP_1_1;
    int maxWaitQueueSize = TestUtils.randomPositiveInt();
    HttpVersion alpnFallbackProtocolVersion = TestUtils.randomBoolean() ? HttpVersion.HTTP_1_1 : HttpVersion.HTTP_1_0;
    Http2Settings initialSettings = randomHttp2Settings();

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
      .put("tryUseCompression", tryUseCompression)
      .put("protocolVersion", protocolVersion.name())
      .put("maxWaitQueueSize", maxWaitQueueSize)
      .put("alpnFallbackProtocolVersion", alpnFallbackProtocolVersion)
      .put("initialSettings", new JsonObject()
          .put("pushEnabled", initialSettings.isPushEnabled())
          .put("headerTableSize", initialSettings.getHeaderTableSize())
          .put("maxHeaderListSize", initialSettings.getMaxHeaderListSize())
          .put("maxConcurrentStreams", initialSettings.getMaxConcurrentStreams())
          .put("initialWindowSize", initialSettings.getInitialWindowSize())
          .put("maxFrameSize", initialSettings.getMaxFrameSize()));

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
    assertEquals(tryUseCompression, options.isTryUseCompression());
    assertEquals(protocolVersion, options.getProtocolVersion());
    assertEquals(maxWaitQueueSize, options.getMaxWaitQueueSize());
    assertEquals(initialSettings, options.getInitialSettings());

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
    boolean compressionSupported = rand.nextBoolean();
    int maxWebsocketFrameSize = TestUtils.randomPositiveInt();
    String wsSubProtocol = TestUtils.randomAlphaString(10);
    boolean is100ContinueHandledAutomatically = rand.nextBoolean();
    int maxChunkSize = rand.nextInt(10000);
    boolean useAlpn = rand.nextBoolean();
    Http2Settings initialSettings = randomHttp2Settings();
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
    options.setKeyStoreOptions(keyStoreOptions);
    options.setTrustStoreOptions(trustStoreOptions);
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
    options.setInitialSettings(initialSettings);
    HttpServerOptions copy = new HttpServerOptions(options);
    assertEquals(sendBufferSize, copy.getSendBufferSize());
    assertEquals(receiverBufferSize, copy.getReceiveBufferSize());
    assertEquals(reuseAddress, copy.isReuseAddress());
    assertEquals(trafficClass, copy.getTrafficClass());
    assertEquals(tcpNoDelay, copy.isTcpNoDelay());
    assertEquals(tcpKeepAlive, copy.isTcpKeepAlive());
    assertEquals(soLinger, copy.getSoLinger());
    assertEquals(usePooledBuffers, copy.isUsePooledBuffers());
    assertEquals(idleTimeout, copy.getIdleTimeout());
    assertEquals(ssl, copy.isSsl());
    assertNotSame(keyStoreOptions, copy.getKeyCertOptions());
    assertEquals(ksPassword, ((JksOptions) copy.getKeyCertOptions()).getPassword());
    assertNotSame(trustStoreOptions, copy.getTrustOptions());
    assertEquals(tsPassword, ((JksOptions) copy.getTrustOptions()).getPassword());
    assertEquals(1, copy.getEnabledCipherSuites().size());
    assertTrue(copy.getEnabledCipherSuites().contains(enabledCipher));
    assertEquals(1, copy.getCrlPaths().size());
    assertEquals(crlPath, copy.getCrlPaths().get(0));
    assertEquals(1, copy.getCrlValues().size());
    assertEquals(crlValue, copy.getCrlValues().get(0));
    assertEquals(port, copy.getPort());
    assertEquals(host, copy.getHost());
    assertEquals(acceptBacklog, copy.getAcceptBacklog());
    assertEquals(compressionSupported, copy.isCompressionSupported());
    assertEquals(maxWebsocketFrameSize, copy.getMaxWebsocketFrameSize());
    assertEquals(wsSubProtocol, copy.getWebsocketSubProtocols());
    assertEquals(is100ContinueHandledAutomatically, copy.isHandle100ContinueAutomatically());
    assertEquals(maxChunkSize, copy.getMaxChunkSize());
    assertEquals(useAlpn, copy.isUseAlpn());
    assertEquals(initialSettings, copy.getInitialSettings());
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
    assertEquals(def.isUseAlpn(), json.isUseAlpn());
    assertEquals(def.getInitialSettings(), json.getInitialSettings());
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
    boolean useAlpn = rand.nextBoolean();
    HttpVersion enabledProtocol = HttpVersion.values()[rand.nextInt(HttpVersion.values().length)];
    Http2Settings initialSettings = TestUtils.randomHttp2Settings();

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
      .put("useAlpn", useAlpn)
      .put("enabledProtocols", new JsonArray().add(enabledProtocol.name()))
      .put("initialSettings", new JsonObject()
          .put("pushEnabled", initialSettings.isPushEnabled())
          .put("headerTableSize", initialSettings.getHeaderTableSize())
          .put("maxHeaderListSize", initialSettings.getMaxHeaderListSize())
          .put("maxConcurrentStreams", initialSettings.getMaxConcurrentStreams())
          .put("initialWindowSize", initialSettings.getInitialWindowSize())
          .put("maxFrameSize", initialSettings.getMaxFrameSize()));

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
    assertEquals(useAlpn, options.isUseAlpn());
    assertEquals(initialSettings, options.getInitialSettings());

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
          req.exceptionHandler(ex -> latch.countDown());
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
      }).listen(onSuccess(s -> latchListen.countDown()));
    }
    assertTrue(latchListen.await(10, TimeUnit.SECONDS));


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
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true).setMaxPoolSize(1));
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
    server.requestHandler(req -> req.response().end()).listen(ar -> {
      assertTrue(ar.succeeded());
      serverLatch.countDown();;
    });
    awaitLatch(serverLatch);
    CountDownLatch req1Latch = new CountDownLatch(1);
    AtomicReference<Context> c = new AtomicReference<>();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
      c.set(Vertx.currentContext());
      res.endHandler(v -> req1Latch.countDown());
    });
    awaitLatch(req1Latch);
    CountDownLatch req2Latch = new CountDownLatch(2);
    HttpClientRequest req2 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
      assertSame(Vertx.currentContext(), c.get());
      req2Latch.countDown();
    }).sendHead();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
      assertSame(Vertx.currentContext(), c.get());
      req2Latch.countDown();
    });
    req2.end();



    awaitLatch(req2Latch);
    vertx.getOrCreateContext().runOnContext(v -> {
      client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
        // This should warn in the log (console) as we are called back on the connection context
        // and not on the context doing the request
        assertSame(Vertx.currentContext(), c.get());
        testComplete();
      });
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
      vertx.createNetClient(new NetClientOptions()).connect(8080, "127.0.0.1", result -> {
        NetSocket socket = result.result();
        socket.closeHandler(r -> {
          testComplete();
        });
        socket.write("GET HTTP1/1\r\n");

        // trigger another write to be sure we detect that the other peer has closed the connection.
        socket.write("X-Header: test\r\n");
      });
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
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(request -> {
      request.response().setChunked(true);
      for (int i = 0; i < buffer.length() / 8192; i++) {
        request.response().write(buffer.slice(i * 8192, (i + 1) * 8192));
      }
      request.response().end();
    });
    httpServer.listen(10000);
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
    await();
  }

  @Test
  public void testMultipleRecursiveCallsAndPipelining() throws Exception {
    int sendRequests = 100;
    AtomicInteger receivedRequests = new AtomicInteger();
    vertx.createHttpServer()
      .requestHandler(x -> {
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
  public void testUnsupportedMethod() throws Exception {
    testUnsupportedMethod("XTRACK /someuri HTTP/1.1\r\nHost: localhost\r\n\r\n", true);
  }

  @Test
  public void testUnsupportedHttpVersion() throws Exception {
    testUnsupportedMethod("GET /someuri HTTP/1.7\r\nHost: localhost\r\n\r\n", false);
  }

  private void testUnsupportedMethod(String rawReq, boolean method) throws Exception {
    vertx.createHttpServer()
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
    vertx.createHttpServer().requestHandler(req -> {
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
  public void testMaxInitialLineLengthOption() {
	  
    String longParam = TestUtils.randomAlphaString(5000);
	
    // 5017 = 5000 for longParam and 17 for the rest in the following line - "GET /?t=longParam HTTP/1.1"
    vertx.createHttpServer(new HttpServerOptions().setMaxInitialLineLength(5017)
    						.setHost("localhost").setPort(8080)).requestHandler(req -> {
      assertEquals(req.getParam("t"), longParam);
      req.response().end();
    }).listen(onSuccess(res -> {
      vertx.createHttpClient(new HttpClientOptions())
      		.request(HttpMethod.GET, 8080, "localhost", "/?t=" + longParam, resp -> {
        testComplete();
      }).end();
    }));
    
    await();
  }
  
  @Test
  public void testMaxHeaderSizeOption() {
	  
    String longHeader = TestUtils.randomAlphaString(9000);
	
    // min 9023 = 9000 for longHeader and 23 for "Content-Length: 0 t: "
    vertx.createHttpServer(new HttpServerOptions().setMaxHeaderSize(10000)
    						.setHost("localhost").setPort(8080)).requestHandler(req -> {
      assertEquals(req.getHeader("t"), longHeader);
      req.response().end();
    }).listen(onSuccess(res -> {
      HttpClientRequest req = vertx.createHttpClient(new HttpClientOptions())
      		.request(HttpMethod.GET, 8080, "localhost", "/", resp -> {
        testComplete();
      });
      // Add longHeader
      req.putHeader("t", longHeader);
      req.end();
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
}
