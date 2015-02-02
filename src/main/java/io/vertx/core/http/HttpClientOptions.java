/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemCaOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.CaOptions;
import io.vertx.core.net.TCPSSLOptions;

/**
 * Options describing how an {@link HttpClient} will make connections.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public class HttpClientOptions extends ClientOptionsBase {

  /**
   * The default maximum number of connections a client will pool = 5
   */
  public static final int DEFAULT_MAX_POOL_SIZE = 5;

  /**
   * Default value of whether keep-alive is enabled = true
   */
  public static final boolean DEFAULT_KEEP_ALIVE = true;

  /**
   * Default value of whether pipe-lining is enabled = false
   */
  public static final boolean DEFAULT_PIPELINING = false;

  /**
   * Default value of whether the client will attempt to use compression = false
   */
  public static final boolean DEFAULT_TRY_USE_COMPRESSION = false;

  /**
   * Default value of whether hostname verification (for SSL/TLS) is enabled = true
   */
  public static final boolean DEFAULT_VERIFY_HOST = true;

  /**
   * The default value for maximum websocket frame size = 65536 bytes
   */
  public static final int DEFAULT_MAX_WEBSOCKET_FRAME_SIZE = 65536;

  /**
   * The default value for host name = "localhost"
   */
  public static final String DEFAULT_DEFAULT_HOST = "localhost";

  /**
   * The default value for port = 80
   */
  public static final int DEFAULT_DEFAULT_PORT = 80;

  private boolean verifyHost = true;
  private int maxPoolSize;
  private boolean keepAlive;
  private boolean pipelining;
  private boolean tryUseCompression;
  private int maxWebsocketFrameSize;
  private String defaultHost;
  private int defaultPort;

  /**
   * Default constructor
   */
  public HttpClientOptions() {
    super();
    verifyHost = DEFAULT_VERIFY_HOST;
    maxPoolSize = DEFAULT_MAX_POOL_SIZE;
    keepAlive = DEFAULT_KEEP_ALIVE;
    pipelining = DEFAULT_PIPELINING;
    tryUseCompression = DEFAULT_TRY_USE_COMPRESSION;
    maxWebsocketFrameSize = DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
    defaultHost = DEFAULT_DEFAULT_HOST;
    defaultPort = DEFAULT_DEFAULT_PORT;
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public HttpClientOptions(HttpClientOptions other) {
    super(other);
    this.verifyHost = other.isVerifyHost();
    this.maxPoolSize = other.getMaxPoolSize();
    this.keepAlive = other.isKeepAlive();
    this.pipelining = other.isPipelining();
    this.tryUseCompression = other.isTryUseCompression();
    this.maxWebsocketFrameSize = other.maxWebsocketFrameSize;
    this.defaultHost = other.defaultHost;
    this.defaultPort = other.defaultPort;
  }

  /**
   * Constructor to create an options from JSON
   *
   * @param json  the JSON
   */
  public HttpClientOptions(JsonObject json) {
    super(json);
    this.verifyHost = json.getBoolean("verifyHost", DEFAULT_VERIFY_HOST);
    this.maxPoolSize = json.getInteger("maxPoolSize", DEFAULT_MAX_POOL_SIZE);
    this.keepAlive = json.getBoolean("keepAlive", DEFAULT_KEEP_ALIVE);
    this.pipelining = json.getBoolean("pipelining", DEFAULT_PIPELINING);
    this.tryUseCompression = json.getBoolean("tryUseCompression", DEFAULT_TRY_USE_COMPRESSION);
    this.maxWebsocketFrameSize = json.getInteger("maxWebsocketFrameSize", DEFAULT_MAX_WEBSOCKET_FRAME_SIZE);
    this.defaultHost = json.getString("defaultHost", DEFAULT_DEFAULT_HOST);
    this.defaultPort = json.getInteger("defaultPort", DEFAULT_DEFAULT_PORT);
  }

  @Override
  public HttpClientOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public HttpClientOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public HttpClientOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public HttpClientOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public HttpClientOptions setTcpNoDelay(boolean tcpNoDelay) {
    super.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public HttpClientOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    super.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  @Override
  public HttpClientOptions setSoLinger(int soLinger) {
    super.setSoLinger(soLinger);
    return this;
  }

  @Override
  public HttpClientOptions setUsePooledBuffers(boolean usePooledBuffers) {
    super.setUsePooledBuffers(usePooledBuffers);
    return this;
  }

  @Override
  public HttpClientOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public HttpClientOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public HttpClientOptions setKeyStoreOptions(JksOptions options) {
    super.setKeyStoreOptions(options);
    return this;
  }

  @Override
  public HttpClientOptions setPfxKeyCertOptions(PfxOptions options) {
    return (HttpClientOptions) super.setPfxKeyCertOptions(options);
  }

  @Override
  public HttpClientOptions setPemKeyCertOptions(PemKeyCertOptions options) {
    return (HttpClientOptions) super.setPemKeyCertOptions(options);
  }

  @Override
  public HttpClientOptions setTrustStoreOptions(JksOptions options) {
    super.setTrustStoreOptions(options);
    return this;
  }

  @Override
  public HttpClientOptions setPfxCaOptions(PfxOptions options) {
    return (HttpClientOptions) super.setPfxCaOptions(options);
  }

  @Override
  public HttpClientOptions setPemCaOptions(PemCaOptions options) {
    return (HttpClientOptions) super.setPemCaOptions(options);
  }

  @Override
  public HttpClientOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public HttpClientOptions setConnectTimeout(int connectTimeout) {
    super.setConnectTimeout(connectTimeout);
    return this;
  }

  @Override
  public HttpClientOptions setTrustAll(boolean trustAll) {
    super.setTrustAll(trustAll);
    return this;
  }

  /**
   * Get the maximum pool size for connections
   *
   * @return  the maximum pool size
   */
  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  /**
   * Set the maximum pool size for connections
   *
   * @param maxPoolSize  the maximum pool size
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxPoolSize(int maxPoolSize) {
    if (maxPoolSize < 1) {
      throw new IllegalArgumentException("maxPoolSize must be > 0");
    }
    this.maxPoolSize = maxPoolSize;
    return this;
  }

  /**
   * Is keep alive enabled on the client?
   *
   * @return true if enabled
   */
  public boolean isKeepAlive() {
    return keepAlive;
  }

  /**
   * Set whether keep alive is enabled on the client
   *
   * @param keepAlive  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  /**
   * Is pipe-lining enabled on the client
   *
   * @return  true if pipe-lining is enabled
   */
  public boolean isPipelining() {
    return pipelining;
  }

  /**
   * Set whether pipe-lining is enabled on the client
   *
   * @param pipelining  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setPipelining(boolean pipelining) {
    this.pipelining = pipelining;
    return this;
  }

  /**
   * Is hostname verification (for SSL/TLS) enabled?
   *
   * @return  true if enabled
   */
  public boolean isVerifyHost() {
    return verifyHost;
  }

  /**
   * Set whether hostname verification is enabled
   *
   * @param verifyHost  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setVerifyHost(boolean verifyHost) {
    this.verifyHost = verifyHost;
    return this;
  }

  /**
   * Is compression enabled on the client?
   *
   * @return  true if enabled
   */
  public boolean isTryUseCompression() {
    return tryUseCompression;
  }

  /**
   * Set whether compression is enabled
   *
   * @param tryUseCompression  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setTryUseCompression(boolean tryUseCompression) {
    this.tryUseCompression = tryUseCompression;
    return this;
  }

  /**
   * Get the maximum websocket framesize to use
   *
   * @return  the max websocket framesize
   */
  public int getMaxWebsocketFrameSize() {
    return maxWebsocketFrameSize;
  }

  /**
   * Set the max websocket frame size
   *
   * @param maxWebsocketFrameSize  the max frame size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxWebsocketFrameSize(int maxWebsocketFrameSize) {
    this.maxWebsocketFrameSize = maxWebsocketFrameSize;
    return this;
  }

  /**
   * Get the default host name to be used by this client in requests if none is provided when making the request.
   *
   * @return  the default host name
   */
  public String getDefaultHost() {
    return defaultHost;
  }

  /**
   * Set the default host name to be used by this client in requests if none is provided when making the request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setDefaultHost(String defaultHost) {
    this.defaultHost = defaultHost;
    return this;
  }

  /**
   * Get the default port to be used by this client in requests if none is provided when making the request.
   *
   * @return  the default port
   */
  public int getDefaultPort() {
    return defaultPort;
  }

  /**
   * Set the default port to be used by this client in requests if none is provided when making the request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setDefaultPort(int defaultPort) {
    this.defaultPort = defaultPort;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HttpClientOptions)) return false;
    if (!super.equals(o)) return false;

    HttpClientOptions that = (HttpClientOptions) o;

    if (defaultPort != that.defaultPort) return false;
    if (keepAlive != that.keepAlive) return false;
    if (maxPoolSize != that.maxPoolSize) return false;
    if (maxWebsocketFrameSize != that.maxWebsocketFrameSize) return false;
    if (pipelining != that.pipelining) return false;
    if (tryUseCompression != that.tryUseCompression) return false;
    if (verifyHost != that.verifyHost) return false;
    if (!defaultHost.equals(that.defaultHost)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (verifyHost ? 1 : 0);
    result = 31 * result + maxPoolSize;
    result = 31 * result + (keepAlive ? 1 : 0);
    result = 31 * result + (pipelining ? 1 : 0);
    result = 31 * result + (tryUseCompression ? 1 : 0);
    result = 31 * result + maxWebsocketFrameSize;
    result = 31 * result + defaultHost.hashCode();
    result = 31 * result + defaultPort;
    return result;
  }
}


