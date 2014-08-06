/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.AsyncResultHandler;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.Registration;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.WorkerContext;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.CaOptions;
import io.vertx.core.net.JKSOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.PKCS12Options;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetTest extends VertxTestBase {

  private NetServer server;
  private NetClient client;

  @Before
  public void before() throws Exception {
    client = vertx.createNetClient(NetClientOptions.options().setConnectTimeout(1000));
    server = vertx.createNetServer(NetServerOptions.options().setPort(1234).setHost("localhost"));
  }

  protected void awaitClose(NetServer server) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    server.close((asyncResult) -> {
      latch.countDown();
    });
    awaitLatch(latch);
  }

  @After
  public void after() throws Exception {
    if (client != null) {
      client.close();
    }
    if (server != null) {
      awaitClose(server);
    }
  }

  @Test
  public void testClientOptions() {
    NetClientOptions options = NetClientOptions.options();

    assertEquals(-1, options.getSendBufferSize());
    int rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    try {
      options.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setSendBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertEquals(-1, options.getReceiveBufferSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    try {
      options.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setReceiveBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(false));
    assertFalse(options.isReuseAddress());

    assertEquals(-1, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    try {
      options.setTrafficClass(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setTrafficClass(256);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isTcpNoDelay());
    assertEquals(options, options.setTcpNoDelay(false));
    assertFalse(options.isTcpNoDelay());

    boolean tcpKeepAlive = SocketDefaults.instance.isTcpKeepAlive();
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(options, options.setTcpKeepAlive(!tcpKeepAlive));
    assertEquals(!tcpKeepAlive, options.isTcpKeepAlive());

    int soLinger = SocketDefaults.instance.getSoLinger();
    assertEquals(soLinger, options.getSoLinger());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSoLinger(rand));
    assertEquals(rand, options.getSoLinger());
    try {
      options.setSoLinger(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertFalse(options.isUsePooledBuffers());
    assertEquals(options, options.setUsePooledBuffers(true));
    assertTrue(options.isUsePooledBuffers());

    rand = TestUtils.randomPositiveInt();
    assertEquals(0, options.getIdleTimeout());
    assertEquals(options, options.setIdleTimeout(rand));
    assertEquals(rand, options.getIdleTimeout());

    assertFalse(options.isSsl());
    assertEquals(options, options.setSsl(true));
    assertTrue(options.isSsl());

    assertNull(options.getKeyStoreOptions());
    JKSOptions keyStoreOptions = JKSOptions.options().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setKeyStoreOptions(keyStoreOptions));
    assertEquals(keyStoreOptions, options.getKeyStoreOptions());

    assertNull(options.getTrustStoreOptions());
    JKSOptions trustStoreOptions = JKSOptions.options().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setTrustStoreOptions(trustStoreOptions));
    assertEquals(trustStoreOptions, options.getTrustStoreOptions());

    assertFalse(options.isTrustAll());
    assertEquals(options, options.setTrustAll(true));
    assertTrue(options.isTrustAll());

    assertEquals(0, options.getReconnectAttempts());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReconnectAttempts(rand));
    assertEquals(rand, options.getReconnectAttempts());

    assertEquals(1000, options.getReconnectInterval());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReconnectInterval(rand));
    assertEquals(rand, options.getReconnectInterval());

    assertTrue(options.getEnabledCipherSuites().isEmpty());
    assertEquals(options, options.addEnabledCipherSuite("foo"));
    assertEquals(options, options.addEnabledCipherSuite("bar"));
    assertNotNull(options.getEnabledCipherSuites());
    assertTrue(options.getEnabledCipherSuites().contains("foo"));
    assertTrue(options.getEnabledCipherSuites().contains("bar"));

    testComplete();
  }



  @Test
  public void testServerOptions() {
    NetServerOptions options = NetServerOptions.options();

    assertEquals(-1, options.getSendBufferSize());
    int rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    try {
      options.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setSendBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertEquals(-1, options.getReceiveBufferSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    try {
      options.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setReceiveBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(false));
    assertFalse(options.isReuseAddress());

    assertEquals(-1, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    try {
      options.setTrafficClass(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setTrafficClass(256);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isTcpNoDelay());
    assertEquals(options, options.setTcpNoDelay(false));
    assertFalse(options.isTcpNoDelay());

    boolean tcpKeepAlive = SocketDefaults.instance.isTcpKeepAlive();
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(options, options.setTcpKeepAlive(!tcpKeepAlive));
    assertEquals(!tcpKeepAlive, options.isTcpKeepAlive());

    int soLinger = SocketDefaults.instance.getSoLinger();
    assertEquals(soLinger, options.getSoLinger());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSoLinger(rand));
    assertEquals(rand, options.getSoLinger());
    try {
      options.setSoLinger(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertFalse(options.isUsePooledBuffers());
    assertEquals(options, options.setUsePooledBuffers(true));
    assertTrue(options.isUsePooledBuffers());

    rand = TestUtils.randomPositiveInt();
    assertEquals(0, options.getIdleTimeout());
    assertEquals(options, options.setIdleTimeout(rand));
    assertEquals(rand, options.getIdleTimeout());

    try {
      options.setIdleTimeout(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertFalse(options.isSsl());
    assertEquals(options, options.setSsl(true));
    assertTrue(options.isSsl());

    assertNull(options.getKeyStoreOptions());
    JKSOptions keyStoreOptions = JKSOptions.options().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setKeyStoreOptions(keyStoreOptions));
    assertEquals(keyStoreOptions, options.getKeyStoreOptions());

    assertNull(options.getTrustStoreOptions());
    JKSOptions trustStoreOptions = JKSOptions.options().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setTrustStoreOptions(trustStoreOptions));
    assertEquals(trustStoreOptions, options.getTrustStoreOptions());

    assertEquals(1024, options.getAcceptBacklog());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setAcceptBacklog(rand));
    assertEquals(rand, options.getAcceptBacklog());

    assertEquals(0, options.getPort());
    assertEquals(options, options.setPort(1234));
    assertEquals(1234, options.getPort());
    try {
      options.setPort(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setPort(65536);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

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

    testComplete();
  }

  @Test
  public void testCopyClientOptions() {
    NetClientOptions options = NetClientOptions.options();
    int sendBufferSize = TestUtils.randomPositiveInt();
    int receiverBufferSize = TestUtils.randomPortInt();
    Random rand = new Random();
    boolean reuseAddress = rand.nextBoolean();
    int trafficClass = TestUtils.randomByte() + 127;
    boolean tcpNoDelay = rand.nextBoolean();
    boolean tcpKeepAlive = rand.nextBoolean();
    int soLinger = TestUtils.randomPositiveInt();
    boolean usePooledBuffers = rand.nextBoolean();
    int idleTimeout = TestUtils.randomPositiveInt();
    boolean ssl = rand.nextBoolean();
    JKSOptions keyStoreOptions = JKSOptions.options();
    String ksPassword = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPassword(ksPassword);
    JKSOptions trustStoreOptions = JKSOptions.options();
    String tsPassword = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPassword(tsPassword);
    String enabledCipher = TestUtils.randomAlphaString(100);
    int connectTimeout = TestUtils.randomPositiveInt();
    boolean trustAll = rand.nextBoolean();
    String crlPath = TestUtils.randomUnicodeString(100);
    Buffer crlValue = TestUtils.randomBuffer(100);
    int reconnectAttempts = TestUtils.randomPositiveInt();
    long reconnectInterval = TestUtils.randomPositiveInt();
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
    options.setReconnectAttempts(reconnectAttempts);
    options.setReconnectInterval(reconnectInterval);
    NetClientOptions copy = NetClientOptions.copiedOptions(options);
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
    assertNotSame(keyStoreOptions, copy.getKeyStoreOptions());
    assertEquals(ksPassword, ((JKSOptions) copy.getKeyStoreOptions()).getPassword());
    assertNotSame(trustStoreOptions, copy.getTrustStoreOptions());
    assertEquals(tsPassword, ((JKSOptions)copy.getTrustStoreOptions()).getPassword());
    assertEquals(1, copy.getEnabledCipherSuites().size());
    assertTrue(copy.getEnabledCipherSuites().contains(enabledCipher));
    assertEquals(connectTimeout, copy.getConnectTimeout());
    assertEquals(trustAll, copy.isTrustAll());
    assertEquals(1, copy.getCrlPaths().size());
    assertEquals(crlPath, copy.getCrlPaths().get(0));
    assertEquals(1, copy.getCrlValues().size());
    assertEquals(crlValue, copy.getCrlValues().get(0));
    assertEquals(reconnectAttempts, copy.getReconnectAttempts());
    assertEquals(reconnectInterval, copy.getReconnectInterval());
  }

  @Test
  public void testClientOptionsJson() {
    int sendBufferSize = TestUtils.randomPositiveInt();
    int receiverBufferSize = TestUtils.randomPortInt();
    Random rand = new Random();
    boolean reuseAddress = rand.nextBoolean();
    int trafficClass = TestUtils.randomByte() + 127;
    boolean tcpNoDelay = rand.nextBoolean();
    boolean tcpKeepAlive = rand.nextBoolean();
    int soLinger = TestUtils.randomPositiveInt();
    boolean usePooledBuffers = rand.nextBoolean();
    int idleTimeout = TestUtils.randomPositiveInt();
    boolean ssl = rand.nextBoolean();
    JKSOptions keyStoreOptions = JKSOptions.options();
    String ksPassword = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPassword(ksPassword);
    String ksPath = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPath(ksPath);
    JKSOptions trustStoreOptions = JKSOptions.options();
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

    JsonObject json = new JsonObject();
    json.putNumber("sendBufferSize", sendBufferSize)
        .putNumber("receiveBufferSize", receiverBufferSize)
        .putBoolean("reuseAddress", reuseAddress)
        .putNumber("trafficClass", trafficClass)
        .putBoolean("tcpNoDelay", tcpNoDelay)
        .putBoolean("tcpKeepAlive", tcpKeepAlive)
        .putNumber("soLinger", soLinger)
        .putBoolean("usePooledBuffers", usePooledBuffers)
        .putNumber("idleTimeout", idleTimeout)
        .putBoolean("ssl", ssl)
        .putArray("enabledCipherSuites", new JsonArray().addString(enabledCipher))
        .putNumber("connectTimeout", connectTimeout)
        .putBoolean("trustAll", trustAll)
        .putArray("crlPaths", new JsonArray().addString(crlPath))
        .putObject("keyStoreOptions", new JsonObject().putString("type", "jks").putString("password", ksPassword).putString("path", ksPath))
        .putObject("trustStoreOptions", new JsonObject().putString("type", "jks").putString("password", tsPassword).putString("path", tsPath))
        .putNumber("reconnectAttempts", reconnectAttempts)
        .putNumber("reconnectInterval", reconnectInterval);

    NetClientOptions options = NetClientOptions.optionsFromJson(json);
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
    assertNotSame(keyStoreOptions, options.getKeyStoreOptions());
    assertEquals(ksPassword, ((JKSOptions) options.getKeyStoreOptions()).getPassword());
    assertEquals(ksPath, ((JKSOptions) options.getKeyStoreOptions()).getPath());
    assertNotSame(trustStoreOptions, options.getTrustStoreOptions());
    assertEquals(tsPassword, ((JKSOptions) options.getTrustStoreOptions()).getPassword());
    assertEquals(tsPath, ((JKSOptions) options.getTrustStoreOptions()).getPath());
    assertEquals(1, options.getEnabledCipherSuites().size());
    assertTrue(options.getEnabledCipherSuites().contains(enabledCipher));
    assertEquals(connectTimeout, options.getConnectTimeout());
    assertEquals(trustAll, options.isTrustAll());
    assertEquals(1, options.getCrlPaths().size());
    assertEquals(crlPath, options.getCrlPaths().get(0));
    assertEquals(reconnectAttempts, options.getReconnectAttempts());
    assertEquals(reconnectInterval, options.getReconnectInterval());

    // Test other keystore/truststore types
    json.putObject("keyStoreOptions", new JsonObject().putString("type", "pkcs12").putString("password", ksPassword))
      .putObject("trustStoreOptions", new JsonObject().putString("type", "pkcs12").putString("password", tsPassword));
    options = NetClientOptions.optionsFromJson(json);
    assertTrue(options.getTrustStoreOptions() instanceof PKCS12Options);
    assertTrue(options.getKeyStoreOptions() instanceof PKCS12Options);

    json.putObject("keyStoreOptions", new JsonObject().putString("type", "keyCert"))
      .putObject("trustStoreOptions", new JsonObject().putString("type", "ca"));
    options = NetClientOptions.optionsFromJson(json);
    assertTrue(options.getTrustStoreOptions() instanceof CaOptions);
    assertTrue(options.getKeyStoreOptions() instanceof KeyCertOptions);


    // Invalid types
    json.putObject("keyStoreOptions", new JsonObject().putString("type", "foo"));
    try {
      NetClientOptions.optionsFromJson(json);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    json.putObject("trustStoreOptions", new JsonObject().putString("type", "foo"));
    try {
      NetClientOptions.optionsFromJson(json);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testCopyServerOptions() {
    NetServerOptions options = NetServerOptions.options();
    int sendBufferSize = TestUtils.randomPositiveInt();
    int receiverBufferSize = TestUtils.randomPortInt();
    Random rand = new Random();
    boolean reuseAddress = rand.nextBoolean();
    int trafficClass = TestUtils.randomByte() + 127;boolean tcpNoDelay = rand.nextBoolean();
    boolean tcpKeepAlive = rand.nextBoolean();
    int soLinger = TestUtils.randomPositiveInt();
    boolean usePooledBuffers = rand.nextBoolean();
    int idleTimeout = TestUtils.randomPositiveInt();
    boolean ssl = rand.nextBoolean();
    JKSOptions keyStoreOptions = JKSOptions.options();
    String ksPassword = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPassword(ksPassword);
    JKSOptions trustStoreOptions = JKSOptions.options();
    String tsPassword = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPassword(tsPassword);
    String enabledCipher = TestUtils.randomAlphaString(100);
    String crlPath = TestUtils.randomUnicodeString(100);
    Buffer crlValue = TestUtils.randomBuffer(100);
    int port = 1234;
    String host = TestUtils.randomAlphaString(100);
    int acceptBacklog = TestUtils.randomPortInt();
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
    NetServerOptions copy = NetServerOptions.copiedOptions(options);
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
    assertNotSame(keyStoreOptions, copy.getKeyStoreOptions());
    assertEquals(ksPassword, ((JKSOptions) copy.getKeyStoreOptions()).getPassword());
    assertNotSame(trustStoreOptions, copy.getTrustStoreOptions());
    assertEquals(tsPassword, ((JKSOptions)copy.getTrustStoreOptions()).getPassword());
    assertEquals(1, copy.getEnabledCipherSuites().size());
    assertTrue(copy.getEnabledCipherSuites().contains(enabledCipher));
    assertEquals(1, copy.getCrlPaths().size());
    assertEquals(crlPath, copy.getCrlPaths().get(0));
    assertEquals(1, copy.getCrlValues().size());
    assertEquals(crlValue, copy.getCrlValues().get(0));
    assertEquals(port, copy.getPort());
    assertEquals(host, copy.getHost());
    assertEquals(acceptBacklog, copy.getAcceptBacklog());
  }

  @Test
  public void testServerOptionsJson() {
    int sendBufferSize = TestUtils.randomPositiveInt();
    int receiverBufferSize = TestUtils.randomPortInt();
    Random rand = new Random();
    boolean reuseAddress = rand.nextBoolean();
    int trafficClass = TestUtils.randomByte() + 127;
    boolean tcpNoDelay = rand.nextBoolean();
    boolean tcpKeepAlive = rand.nextBoolean();
    int soLinger = TestUtils.randomPositiveInt();
    boolean usePooledBuffers = rand.nextBoolean();
    int idleTimeout = TestUtils.randomInt();
    boolean ssl = rand.nextBoolean();
    JKSOptions keyStoreOptions = JKSOptions.options();
    String ksPassword = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPassword(ksPassword);
    String ksPath = TestUtils.randomAlphaString(100);
    keyStoreOptions.setPath(ksPath);
    JKSOptions trustStoreOptions = JKSOptions.options();
    String tsPassword = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPassword(tsPassword);
    String tsPath = TestUtils.randomAlphaString(100);
    trustStoreOptions.setPath(tsPath);
    String enabledCipher = TestUtils.randomAlphaString(100);
    String crlPath = TestUtils.randomUnicodeString(100);
    int port = 1234;
    String host = TestUtils.randomAlphaString(100);
    int acceptBacklog = TestUtils.randomPortInt();

    JsonObject json = new JsonObject();
    json.putNumber("sendBufferSize", sendBufferSize)
      .putNumber("receiveBufferSize", receiverBufferSize)
      .putBoolean("reuseAddress", reuseAddress)
      .putNumber("trafficClass", trafficClass)
      .putBoolean("tcpNoDelay", tcpNoDelay)
      .putBoolean("tcpKeepAlive", tcpKeepAlive)
      .putNumber("soLinger", soLinger)
      .putBoolean("usePooledBuffers", usePooledBuffers)
      .putNumber("idleTimeout", idleTimeout)
      .putBoolean("ssl", ssl)
      .putArray("enabledCipherSuites", new JsonArray().addString(enabledCipher))
      .putArray("crlPaths", new JsonArray().addString(crlPath))
      .putObject("keyStoreOptions", new JsonObject().putString("type", "jks").putString("password", ksPassword).putString("path", ksPath))
      .putObject("trustStoreOptions", new JsonObject().putString("type", "jks").putString("password", tsPassword).putString("path", tsPath))
      .putNumber("port", port)
      .putString("host", host)
      .putNumber("acceptBacklog", acceptBacklog);

    NetServerOptions options = NetServerOptions.optionsFromJson(json);
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
    assertNotSame(keyStoreOptions, options.getKeyStoreOptions());
    assertEquals(ksPassword, ((JKSOptions) options.getKeyStoreOptions()).getPassword());
    assertEquals(ksPath, ((JKSOptions) options.getKeyStoreOptions()).getPath());
    assertNotSame(trustStoreOptions, options.getTrustStoreOptions());
    assertEquals(tsPassword, ((JKSOptions) options.getTrustStoreOptions()).getPassword());
    assertEquals(tsPath, ((JKSOptions) options.getTrustStoreOptions()).getPath());
    assertEquals(1, options.getEnabledCipherSuites().size());
    assertTrue(options.getEnabledCipherSuites().contains(enabledCipher));
    assertEquals(1, options.getCrlPaths().size());
    assertEquals(crlPath, options.getCrlPaths().get(0));
    assertEquals(port, options.getPort());
    assertEquals(host, options.getHost());
    assertEquals(acceptBacklog, options.getAcceptBacklog());

    // Test other keystore/truststore types
    json.putObject("keyStoreOptions", new JsonObject().putString("type", "pkcs12").putString("password", ksPassword))
      .putObject("trustStoreOptions", new JsonObject().putString("type", "pkcs12").putString("password", tsPassword));
    options = NetServerOptions.optionsFromJson(json);
    assertTrue(options.getTrustStoreOptions() instanceof PKCS12Options);
    assertTrue(options.getKeyStoreOptions() instanceof PKCS12Options);

    json.putObject("keyStoreOptions", new JsonObject().putString("type", "keyCert"))
      .putObject("trustStoreOptions", new JsonObject().putString("type", "ca"));
    options = NetServerOptions.optionsFromJson(json);
    assertTrue(options.getTrustStoreOptions() instanceof CaOptions);
    assertTrue(options.getKeyStoreOptions() instanceof KeyCertOptions);


    // Invalid types
    json.putObject("keyStoreOptions", new JsonObject().putString("type", "foo"));
    try {
      NetServerOptions.optionsFromJson(json);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    json.putObject("trustStoreOptions", new JsonObject().putString("type", "foo"));
    try {
      NetServerOptions.optionsFromJson(json);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testEchoBytes() {
    Buffer sent = TestUtils.randomBuffer(100);
    testEcho(sock -> sock.writeBuffer(sent), buff -> TestUtils.buffersEqual(sent, buff), sent.length());
  }

  @Test
  public void testEchoString() {
    String sent = TestUtils.randomUnicodeString(100);
    Buffer buffSent = Buffer.buffer(sent);
    testEcho(sock -> sock.writeString(sent), buff -> TestUtils.buffersEqual(buffSent, buff), buffSent.length());
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
    testEcho(sock -> sock.writeString(sent, encoding), buff -> TestUtils.buffersEqual(buffSent, buff), buffSent.length());
  }

  void testEcho(Consumer<NetSocket> writer, Consumer<Buffer> dataChecker, int length) {
    Handler<AsyncResult<NetSocket>> clientHandler = (asyncResult) -> {
      if (asyncResult.succeeded()) {
        NetSocket sock = asyncResult.result();
        Buffer buff = Buffer.buffer();
        sock.dataHandler((buffer) -> {
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
    startEchoServer(s -> client.connect(1234, "localhost", clientHandler));
    await();
  }

  void startEchoServer(Handler<AsyncResult<NetServer>> listenHandler) {
    Handler<NetSocket> serverHandler = socket -> socket.dataHandler(socket::writeBuffer);
    server.connectHandler(serverHandler).listen(listenHandler);
  }

  @Test
  public void testConnectLocalHost() {
    connect(1234, "localhost");
  }

  void connect(int port, String host) {
    startEchoServer(s -> {
      final int numConnections = 100;
      final AtomicInteger connCount = new AtomicInteger(0);
      for (int i = 0; i < numConnections; i++) {
        AsyncResultHandler<NetSocket> handler = res -> {
          if (res.succeeded()) {
            res.result().close();
            if (connCount.incrementAndGet() == numConnections) {
              testComplete();
            }
          }
        };
        client.connect(port, host, handler);
      }
    });
    await();
  }

  @Test
  public void testConnectInvalidPort() {
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
    client.connect(1234, "127.0.0.2", res -> {
      assertTrue(res.failed());
      assertFalse(res.succeeded());
      assertNotNull(res.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenInvalidPort() {
    server.close();
    server = vertx.createNetServer(NetServerOptions.options().setPort(80));
    server.connectHandler((netSocket) -> {
    }).listen(ar -> {
      assertTrue(ar.failed());
      assertFalse(ar.succeeded());
      assertNotNull(ar.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenInvalidHost() {
    server.close();
    server = vertx.createNetServer(NetServerOptions.options().setPort(1234).setHost("uhqwduhqwudhqwuidhqwiudhqwudqwiuhd"));
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
    server = vertx.createNetServer(NetServerOptions.options().setPort(0));
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
    startEchoServer(s -> clientCloseHandlers(true));
    await();
  }

  @Test
  public void testClientCloseHandlersCloseFromServer() {
    server.connectHandler((netSocket) -> netSocket.close()).listen((s) -> clientCloseHandlers(false));
    await();
  }

  void clientCloseHandlers(boolean closeFromClient) {
    client.connect(1234, "localhost", ar -> {
      AtomicInteger counter = new AtomicInteger(0);
      ar.result().endHandler(v -> assertEquals(1, counter.incrementAndGet()));
      ar.result().closeHandler(v -> {
        assertEquals(2, counter.incrementAndGet());
        testComplete();
      });
      if (closeFromClient) {
        ar.result().close();
      }
    });
  }

  @Test
  public void testServerCloseHandlersCloseFromClient() {
    serverCloseHandlers(false, s -> client.connect(1234, "localhost", ar -> ar.result().close()));
    await();
  }

  @Test
  public void testServerCloseHandlersCloseFromServer() {
    serverCloseHandlers(true, s -> client.connect(1234, "localhost", ar -> {
    }));
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
    }).listen(listenHandler);
  }

  @Test
  public void testClientDrainHandler() {
    pausingServer((s) -> {
      client.connect(1234, "localhost", ar -> {
        NetSocket sock = ar.result();
        assertFalse(sock.writeQueueFull());
        sock.setWriteQueueMaxSize(1000);
        Buffer buff = TestUtils.randomBuffer(10000);
        vertx.setPeriodic(1, id -> {
          sock.writeBuffer(buff.copy());
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
      });
    });
    await();
  }

  void pausingServer(Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler(sock -> {
      sock.pause();
      Handler<Message<Buffer>> resumeHandler = (m) -> sock.resume();
      Registration reg = vertx.eventBus().registerHandler("server_resume", resumeHandler);
      sock.closeHandler(v -> reg.unregister());
    }).listen(listenHandler);
  }

  @Test
  public void testServerDrainHandler() {
    drainingServer(s -> {
      client.connect(1234, "localhost", ar -> {
        NetSocket sock = ar.result();
        sock.pause();
        setHandlers(sock);
        sock.dataHandler(buf -> {
        });
      });
    });
    await();
  }

  void setHandlers(NetSocket sock) {
    Handler<Message<Buffer>> resumeHandler = m -> sock.resume();
    Registration reg = vertx.eventBus().registerHandler("client_resume", resumeHandler);
    sock.closeHandler(v -> reg.unregister());
  }

  void drainingServer(Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler(sock -> {
      assertFalse(sock.writeQueueFull());
      sock.setWriteQueueMaxSize(1000);

      Buffer buff = TestUtils.randomBuffer(10000);
      //Send data until the buffer is full
      vertx.setPeriodic(1, id -> {
        sock.writeBuffer(buff.copy());
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
    }).listen(listenHandler);
  }

  @Test
  public void testReconnectAttemptsInfinite() {
    reconnectAttempts(-1);
  }

  @Test
  public void testReconnectAttemptsMany() {
    reconnectAttempts(100000);
  }

  void reconnectAttempts(int attempts) {
    client.close();
    client = vertx.createNetClient(NetClientOptions.options().setReconnectAttempts(attempts).setReconnectInterval(10));

    //The server delays starting for a a few seconds, but it should still connect
    client.connect(1234, "localhost", (res) -> {
      assertTrue(res.succeeded());
      assertFalse(res.failed());
      testComplete();
    });

    // Start the server after a delay
    vertx.setTimer(2000, id -> startEchoServer(s -> {
    }));

    await();
  }

  @Test
  public void testReconnectAttemptsNotEnough() {
    client.close();
    client = vertx.createNetClient(NetClientOptions.options().setReconnectAttempts(100).setReconnectInterval(10));

    client.connect(1234, "localhost", (res) -> {
      assertFalse(res.succeeded());
      assertTrue(res.failed());
      testComplete();
    });

    await();
  }

  @Test
  public void testServerIdleTimeout() {
    server.close();
    server = vertx.createNetServer(NetServerOptions.options().setPort(1234).setHost("localhost").setIdleTimeout(1));
    server.connectHandler(s -> {}).listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", res -> {
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
    client = vertx.createNetClient(NetClientOptions.options().setIdleTimeout(1));

    server.connectHandler(s -> {}).listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", res -> {
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
    testTLS(false, false, true, false, false, true, true, true);
  }

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll() throws Exception {
    testTLS(false, false, true, false, false, true, true, false);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(false, true, true, false, false, false, true, false);
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(false, false, true, false, false, false, false, false);
  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(true, true, true, true, false, false, true, false);
  }

  @Test
  //Client specifies cert and it's not required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(true, true, true, true, true, false, true, false);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(false, true, true, true, true, false, false, false);
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(true, true, true, false, true, false, false, false);
  }

  @Test
  // Specify some cipher suites
  public void testTLSCipherSuites() throws Exception {
    testTLS(false, false, true, false, false, true, true, false, ENABLED_CIPHER_SUITES);
  }

  void testTLS(boolean clientCert, boolean clientTrust,
               boolean serverCert, boolean serverTrust,
               boolean requireClientAuth, boolean clientTrustAll,
               boolean shouldPass, boolean startTLS,
               String... enabledCipherSuites) throws Exception {
    server.close();
    NetServerOptions options = NetServerOptions.options();
    if (!startTLS) {
      options.setSsl(true);
    }
    if (serverTrust) {
      options.setTrustStoreOptions(JKSOptions.options().setPath(findFileOnClasspath("tls/server-truststore.jks")).setPassword("wibble"));
    }
    if (serverCert) {
      options.setKeyStoreOptions(JKSOptions.options().setPath(findFileOnClasspath("tls/server-keystore.jks")).setPassword("wibble"));
    }
    if (requireClientAuth) {
      options.setClientAuthRequired(true);
    }
    for (String suite: enabledCipherSuites) {
      options.addEnabledCipherSuite(suite);
    }

    options.setPort(4043);
    server = vertx.createNetServer(options);
    Handler<NetSocket> serverHandler = socket -> {
      AtomicBoolean upgradedServer = new AtomicBoolean();
      socket.dataHandler(buff -> {
        socket.writeBuffer(buff); // echo the data
        if (startTLS && !upgradedServer.get()) {
          assertFalse(socket.isSsl());
          socket.ssl(v -> assertTrue(socket.isSsl()));
          upgradedServer.set(true);
        } else {
          assertTrue(socket.isSsl());
        }
      });
    };
    server.connectHandler(serverHandler).listen(ar -> {
      client.close();
      NetClientOptions clientOptions = NetClientOptions.options();
      if (!startTLS) {
        clientOptions.setSsl(true);
        if (clientTrustAll) {
          clientOptions.setTrustAll(true);
        }
        if (clientTrust) {
          clientOptions.setTrustStoreOptions(JKSOptions.options().setPath(findFileOnClasspath("tls/client-truststore.jks")).setPassword("wibble"));
        }
        if (clientCert) {
          clientOptions.setKeyStoreOptions(JKSOptions.options().setPath(findFileOnClasspath("tls/client-keystore.jks")).setPassword("wibble"));
        }
        for (String suite: enabledCipherSuites) {
          clientOptions.addEnabledCipherSuite(suite);
        }
      }
      client = vertx.createNetClient(clientOptions);
      client.connect(4043, "localhost", ar2 -> {
        if (ar2.succeeded()) {
          if (!shouldPass) {
            fail("Should not connect");
            return;
          }
          final int numChunks = 100;
          final int chunkSize = 100;
          final Buffer received = Buffer.buffer();
          final Buffer sent = Buffer.buffer();
          final NetSocket socket = ar2.result();

          final AtomicBoolean upgradedClient = new AtomicBoolean();
          socket.dataHandler(buffer -> {
            received.appendBuffer(buffer);
            if (received.length() == sent.length()) {
              TestUtils.buffersEqual(sent, received);
              testComplete();
            }
            if (startTLS && !upgradedClient.get()) {
              assertFalse(socket.isSsl());
              socket.ssl(v -> {
                assertTrue(socket.isSsl());
                // Now send the rest
                for (int i = 1; i < numChunks; i++) {
                  sendBuffer(socket, sent, chunkSize);
                }
              });
            } else {
              assertTrue(socket.isSsl());
            }
          });

          //Now send some data
          int numToSend = startTLS ? 1 : numChunks;
          for (int i = 0; i < numToSend; i++) {
            sendBuffer(socket, sent, chunkSize);
          }
        } else {
          if (shouldPass) {
            fail("Should not fail to connect");
          } else {
            testComplete();
          }
        }
      });
    });
    await();
  }

  void sendBuffer(NetSocket socket, Buffer sent, int chunkSize) {
    Buffer buff = TestUtils.randomBuffer(chunkSize);
    sent.appendBuffer(buff);
    socket.writeBuffer(buff);
  }

  @Test
  public void testSharedServersRoundRobin() throws Exception {

    int numServers = 5;
    int numConnections = numServers * 100;

    List<NetServer> servers = new ArrayList<>();
    Set<NetServer> connectedServers = new ConcurrentHashSet<>();
    Map<NetServer, Integer> connectCount = new ConcurrentHashMap<>();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numConnections);
    for (int i = 0; i < numServers; i++) {
      NetServer theServer = vertx.createNetServer(NetServerOptions.options().setHost("localhost").setPort(1234));
      servers.add(theServer);
      theServer.connectHandler(sock -> {
        connectedServers.add(theServer);
        Integer cnt = connectCount.get(theServer);
        int icnt = cnt == null ? 0 : cnt;
        icnt++;
        connectCount.put(theServer, icnt);
        latchConns.countDown();
      }).listen(ar -> {
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
    client = vertx.createNetClient(NetClientOptions.options());
    CountDownLatch latchClient = new CountDownLatch(numConnections);
    for (int i = 0; i < numConnections; i++) {
      client.connect(1234, "localhost", res -> {
        if (res.succeeded()) {
          res.result().closeHandler(v -> {
            latchClient.countDown();
          });
          res.result().close();
        } else {
          res.cause().printStackTrace();
          fail("Failed to connect");
        }
      });
    }

    assertTrue(latchClient.await(10, TimeUnit.SECONDS));
    assertTrue(latchConns.await(10, TimeUnit.SECONDS));

    assertEquals(numServers, connectedServers.size());
    for (NetServer server : servers) {
      assertTrue(connectedServers.contains(server));
    }
    assertEquals(numServers, connectCount.size());
    for (int cnt : connectCount.values()) {
      assertEquals(numConnections / numServers, cnt);
    }

    CountDownLatch closeLatch = new CountDownLatch(numServers);

    for (NetServer server : servers) {
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
    CountDownLatch latch = new CountDownLatch(1);
    // Have a server running on a different port to make sure it doesn't interact
    server.close();
    server = vertx.createNetServer(NetServerOptions.options().setPort(4321));
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
    server = vertx.createNetServer(NetServerOptions.options().setPort(1234));
    server.connectHandler(sock -> {
      fail("Should not connect");
    }).listen(ar -> {
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
  // This tests using NetSocket.writeHandlerID (on the server side)
  // Send some data and make sure it is fanned out to all connections
  public void testFanout() throws Exception {

    CountDownLatch latch = new CountDownLatch(1);
    Set<String> connections = new ConcurrentHashSet<>();
    server.connectHandler(socket -> {
      connections.add(socket.writeHandlerID());
      socket.dataHandler(buffer -> {
        for (String actorID : connections) {
          vertx.eventBus().publish(actorID, buffer);
        }
      });
      socket.closeHandler(v -> {
        connections.remove(socket.writeHandlerID());
      });
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);

    int numConnections = 10;
    CountDownLatch connectLatch = new CountDownLatch(numConnections);
    CountDownLatch receivedLatch = new CountDownLatch(numConnections);
    for (int i = 0; i < numConnections; i++) {
      client.connect(1234, "localhost", res -> {
        connectLatch.countDown();
        res.result().dataHandler(data -> {
          receivedLatch.countDown();
        });
      });
    }
    assertTrue(connectLatch.await(10, TimeUnit.SECONDS));

    // Send some data
    client.connect(1234, "localhost", res -> {
      res.result().writeString("foo");
    });
    assertTrue(receivedLatch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void testRemoteAddress() throws Exception {
    server.connectHandler(socket -> {
      SocketAddress addr = socket.remoteAddress();
      assertEquals("127.0.0.1", addr.hostAddress());
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      vertx.createNetClient(NetClientOptions.options()).connect(1234, "localhost", result -> {
        NetSocket socket = result.result();
        SocketAddress addr = socket.remoteAddress();
        assertEquals("127.0.0.1", addr.hostAddress());
        assertEquals(addr.hostPort(), 1234);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testWriteSameBufferMoreThanOnce() throws Exception {
    server.connectHandler(socket -> {
      Buffer received = Buffer.buffer();
      socket.dataHandler(buff -> {
        received.appendBuffer(buff);
        if (received.toString().equals("foofoo")) {
          testComplete();
        }
      });
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", result -> {
        NetSocket socket = result.result();
        Buffer buff = Buffer.buffer("foo");
        socket.writeBuffer(buff);
        socket.writeBuffer(buff);
      });
    });
    await();
  }

  @Test
  public void sendFileClientToServer() throws Exception {
    File fDir = Files.createTempDirectory("vertx-test").toFile();
    String content = TestUtils.randomUnicodeString(10000);
    File file = setupFile(fDir.toString(), "some-file.txt", content);
    Buffer expected = Buffer.buffer(content);
    Buffer received = Buffer.buffer();
    server.connectHandler(sock -> {
      sock.dataHandler(buff -> {
        received.appendBuffer(buff);
        if (received.length() == expected.length()) {
          assertTrue(TestUtils.buffersEqual(expected, received));
          testComplete();
        }
      });
      // Send some data to the client to trigger the sendfile
      sock.writeString("foo");
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
        NetSocket sock = ar2.result();
        sock.dataHandler(buf -> {
          sock.sendFile(file.getAbsolutePath());
        });
      });
    });

    await();
  }

  @Test
  public void sendFileServerToClient() throws Exception {
    File fDir = Files.createTempDirectory("vertx-test").toFile();
    String content = TestUtils.randomUnicodeString(10000);
    File file = setupFile(fDir.toString(), "some-file.txt", content);
    Buffer expected = Buffer.buffer(content);
    Buffer received = Buffer.buffer();
    server.connectHandler(sock -> {
      sock.dataHandler(buf -> {
        sock.sendFile(file.getAbsolutePath());
      });
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
        NetSocket sock = ar2.result();
        sock.dataHandler(buff -> {
          received.appendBuffer(buff);
          if (received.length() == expected.length()) {
            assertTrue(TestUtils.buffersEqual(expected, received));
            testComplete();
          }
        });
        sock.writeString("foo");
      });
    });

    await();
  }

  @Test
  public void testSendFileDirectory() throws Exception {
    File fDir = Files.createTempDirectory("vertx-test").toFile();
    fDir.deleteOnExit();
    server.connectHandler(socket -> {
      SocketAddress addr = socket.remoteAddress();
      assertEquals("127.0.0.1", addr.hostAddress());
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, "localhost", result -> {
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
    NetServerOptions options = NetServerOptions.options().setPort(1234);
    NetServer server = vertx.createNetServer(options);
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
    NetClientOptions options = NetClientOptions.options();
    client = vertx.createNetClient(options);
    options.setSsl(true);
    // Now change something - but server should ignore this
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
  public void testListenWithNoHandler() {
    try {
      server.listen();
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testListenWithNoHandler2() {
    try {
      server.listen(ar -> {
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
    server.listen();
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
    server.listen(ar -> {
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
    server.listen();
    try {
      server.listen(sock -> {
      });
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testListenTwice2() {
    server.connectHandler(sock -> {
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      try {
        server.listen(sock -> {
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
      client.connect(1234, "localhost", ar -> {
      });
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
  }

  @Test
  public void testClientMultiThreaded() throws Exception {
    int numThreads = 10;
    Thread[] threads = new Thread[numThreads];
    CountDownLatch latch = new CountDownLatch(numThreads);
    server.connectHandler(socket -> {
      socket.dataHandler(socket::writeBuffer);
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      for (int i = 0; i < numThreads; i++) {
        threads[i] = new Thread() {
          public void run() {
            client.connect(1234, "localhost", result -> {
              assertTrue(result.succeeded());
              Buffer buff = TestUtils.randomBuffer(100000);
              NetSocket sock = result.result();
              sock.writeBuffer(buff);
              Buffer received = Buffer.buffer();
              sock.dataHandler(rec -> {
                received.appendBuffer(rec);
                if (received.length() == buff.length()) {
                  assertTrue(TestUtils.buffersEqual(buff, received));
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

  @Test
  public void testInWorkerVerticle() throws Exception {
    testInVerticle(true);
  }

  private void testInVerticle(boolean worker) throws Exception {
    client.close();
    server.close();
    class MyVerticle extends AbstractVerticle {
      Context ctx;
      @Override
      public void start() {
        ctx = vertx.currentContext();
        if (worker) {
          assertTrue(ctx instanceof WorkerContext);
        } else {
          assertTrue(ctx instanceof EventLoopContext);
        }
        Thread thr = Thread.currentThread();
        server = vertx.createNetServer(NetServerOptions.options().setPort(1234));
        server.connectHandler(sock -> {
          sock.dataHandler(buff -> {
            sock.writeBuffer(buff);
          });
          assertSame(ctx, vertx.currentContext());
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
        });
        server.listen(ar -> {
          assertTrue(ar.succeeded());
          assertSame(ctx, vertx.currentContext());
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
          client = vertx.createNetClient(NetClientOptions.options());
          client.connect(1234, "localhost", ar2 -> {
            assertSame(ctx, vertx.currentContext());
            if (!worker) {
              assertSame(thr, Thread.currentThread());
            }
            assertTrue(ar2.succeeded());
            NetSocket sock = ar2.result();
            Buffer buff = TestUtils.randomBuffer(10000);
            sock.writeBuffer(buff);
            Buffer brec = Buffer.buffer();
            sock.dataHandler(rec -> {
              assertSame(ctx, vertx.currentContext());
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options().setWorker(worker));
    await();
  }

  @Test
  public void testInMultithreadedWorker() throws Exception {
    class MyVerticle extends AbstractVerticle {
      @Override
      public void start() {
        try {
          server = vertx.createNetServer(NetServerOptions.options());
          fail("Should throw exception");
        } catch (IllegalStateException e) {
          // OK
        }
        try {
          client = vertx.createNetClient(NetClientOptions.options());
          fail("Should throw exception");
        } catch (IllegalStateException e) {
          // OK
        }
        testComplete();
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options().setWorker(true).setMultiThreaded(true));
    await();
  }

  @Test
  public void testContexts() throws Exception {
    Set<ContextImpl> contexts = new ConcurrentHashSet<>();
    AtomicInteger cnt = new AtomicInteger();
    AtomicReference<ContextImpl> serverConnectContext = new AtomicReference<>();
    // Server connect handler should always be called with same context
    server.connectHandler(sock -> {
      sock.dataHandler(sock::writeBuffer);
      ContextImpl serverContext = ((VertxInternal) vertx).getContext();
      if (serverConnectContext.get() != null) {
        assertSame(serverConnectContext.get(), serverContext);
      } else {
        serverConnectContext.set(serverContext);
      }
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
    int numConns = 10;
    // Each connect should be in its own context
    for (int i = 0; i < numConns; i++) {
      client.connect(1234, "localhost", conn -> {
        contexts.add(((VertxInternal) vertx).getContext());
        if (cnt.incrementAndGet() == numConns) {
          assertEquals(numConns, contexts.size());
          latch2.countDown();
        }
      });
    }
    awaitLatch(latch2);
    // Close should be in own context
    server.close(ar -> {
      assertTrue(ar.succeeded());
      ContextImpl closeContext = ((VertxInternal) vertx).getContext();
      assertFalse(contexts.contains(closeContext));
      assertNotSame(serverConnectContext.get(), closeContext);
      assertFalse(contexts.contains(listenContext.get()));
      assertSame(serverConnectContext.get(), listenContext.get());
      testComplete();
    });

    server = null;
    await();
  }


  private File setupFile(String testDir, String fileName, String content) throws Exception {
    File file = new File(testDir, fileName);
    if (file.exists()) {
      file.delete();
    }
    file.deleteOnExit();
    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    out.write(content);
    out.close();
    return file;
  }

}
