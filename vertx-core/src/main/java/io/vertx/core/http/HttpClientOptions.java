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

import io.netty.handler.logging.ByteBufFormat;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.tracing.TracingPolicy;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Options describing how an {@link HttpClient} will make connections.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class HttpClientOptions extends ClientOptionsBase {

  /**
   * The default maximum number of concurrent streams per connection for HTTP/2 = -1
   */
  public static final int DEFAULT_HTTP2_MULTIPLEXING_LIMIT = -1;

  /**
   * The default connection window size for HTTP/2 = -1
   */
  public static final int DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE = -1;

  /**
   * The default keep alive timeout for HTTP/2 connection can send = 60 seconds
   */
  public static final int DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT = 60;

  /**
   * Default value of whether keep-alive is enabled = {@code true}
   */
  public static final boolean DEFAULT_KEEP_ALIVE = true;

  /**
   * Default value of whether pipe-lining is enabled = {@code false}
   */
  public static final boolean DEFAULT_PIPELINING = false;

  /**
   * The default maximum number of requests an HTTP/1.1 pipe-lined connection can send = 10
   */
  public static final int DEFAULT_PIPELINING_LIMIT = 10;

  /**
   * The default keep alive timeout for HTTP/1.1 connection can send = 60 seconds
   */
  public static final int DEFAULT_KEEP_ALIVE_TIMEOUT = 60;

  /**
   * Whether the client should send requests with an {@code accepting-encoding} header set to a compression algorithm by default = {@code false}
   */
  public static final boolean DEFAULT_DECOMPRESSION_SUPPORTED = false;

  /**
   * Default value of whether hostname verification (for SSL/TLS) is enabled = {@code true}
   */
  public static final boolean DEFAULT_VERIFY_HOST = true;

  /**
   * The default value for host name = "localhost"
   */
  public static final String DEFAULT_DEFAULT_HOST = "localhost";

  /**
   * The default value for port = 80
   */
  public static final int DEFAULT_DEFAULT_PORT = 80;

  /**
   * The default protocol version = HTTP/1.1
   */
  public static final HttpVersion DEFAULT_PROTOCOL_VERSION = HttpVersion.HTTP_1_1;

  /**
   * Default max HTTP chunk size = 8192
   */
  public static final int DEFAULT_MAX_CHUNK_SIZE = 8192;

  /**
   * Default max length of the initial line (e.g. {@code "HTTP/1.1 200 OK"}) = 4096
   */
  public static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4096;

  /**
   * Default max length of all headers = 8192
   */
  public static final int DEFAULT_MAX_HEADER_SIZE = 8192;

  /**
   * Default Application-Layer Protocol Negotiation versions = [] (automatic according to protocol version)
   */
  public static final List<HttpVersion> DEFAULT_ALPN_VERSIONS = Collections.emptyList();

  /**
   * Default using HTTP/1.1 upgrade for establishing an <i>h2C</i> connection = {@code true}
   */
  public static final boolean DEFAULT_HTTP2_CLEAR_TEXT_UPGRADE = true;

  /**
   * Default to use a preflight OPTIONS request for <i>h2C</i> without prior knowledge connection = {@code false}
   */
  public static final boolean DEFAULT_HTTP2_CLEAR_TEXT_UPGRADE_WITH_PREFLIGHT_REQUEST = false;

  /*
   * Default max redirect = 16
   */
  public static final int DEFAULT_MAX_REDIRECTS = 16;

  /*
   * Default force SNI = {@code false}
   */
  public static final boolean DEFAULT_FORCE_SNI = false;

  /**
   * Default initial buffer size for HttpObjectDecoder = 128 bytes
   */
  public static final int DEFAULT_DECODER_INITIAL_BUFFER_SIZE = 128;

  /**
   * Default tracing control = {@link TracingPolicy#PROPAGATE}
   */
  public static final TracingPolicy DEFAULT_TRACING_POLICY = TracingPolicy.PROPAGATE;

  /**
   * Default shared client = {@code false}
   */
  public static final boolean DEFAULT_SHARED = false;

  /**
   * Actual name of anonymous shared client = {@code __vertx.DEFAULT}
   */
  public static final String DEFAULT_NAME = "__vertx.DEFAULT";

  /**
   * The default maximum number of concurrent streams per connection for HTTP/3 = -1
   */
  public static final int DEFAULT_HTTP3_MULTIPLEXING_LIMIT = -1;

  private boolean verifyHost = true;
  private boolean keepAlive;
  private int keepAliveTimeout;
  private int pipeliningLimit;
  private boolean pipelining;
  private int http2MultiplexingLimit;
  private int http2ConnectionWindowSize;
  private int http2KeepAliveTimeout;

  private boolean decompressionSupported;
  private String defaultHost;
  private int defaultPort;
  private int maxChunkSize;
  private int maxInitialLineLength;
  private int maxHeaderSize;
  private Http2Settings initialSettings;
  private Http3Settings initialHttp3Settings;
  private List<HttpVersion> alpnVersions;
  private boolean http2ClearTextUpgrade;
  private boolean http2ClearTextUpgradeWithPreflightRequest;
  private int maxRedirects;
  private boolean forceSni;
  private int decoderInitialBufferSize;

  private TracingPolicy tracingPolicy;

  private boolean shared;
  private String name;

  private int http3MultiplexingLimit;

  /**
   * Default constructor
   */
  public HttpClientOptions() {
    super();
    init();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public HttpClientOptions(ClientOptionsBase other) {
    super(other);
    init();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public HttpClientOptions(HttpClientOptions other) {
    super(other);
    this.verifyHost = other.isVerifyHost();
    this.keepAlive = other.isKeepAlive();
    this.keepAliveTimeout = other.getKeepAliveTimeout();
    this.pipelining = other.isPipelining();
    this.pipeliningLimit = other.getPipeliningLimit();
    this.http2MultiplexingLimit = other.http2MultiplexingLimit;
    this.http2ConnectionWindowSize = other.http2ConnectionWindowSize;
    this.http2KeepAliveTimeout = other.getHttp2KeepAliveTimeout();
    this.decompressionSupported = other.decompressionSupported;
    this.defaultHost = other.defaultHost;
    this.defaultPort = other.defaultPort;
    this.maxChunkSize = other.maxChunkSize;
    this.maxInitialLineLength = other.getMaxInitialLineLength();
    this.maxHeaderSize = other.getMaxHeaderSize();
    this.initialSettings = other.initialSettings != null ? new Http2Settings(other.initialSettings) : null;
    this.initialHttp3Settings = other.initialHttp3Settings != null ? new Http3Settings(other.initialHttp3Settings) : null;
    this.alpnVersions = other.alpnVersions != null ? new ArrayList<>(other.alpnVersions) : null;
    this.http2ClearTextUpgrade = other.http2ClearTextUpgrade;
    this.http2ClearTextUpgradeWithPreflightRequest = other.http2ClearTextUpgradeWithPreflightRequest;
    this.maxRedirects = other.maxRedirects;
    this.forceSni = other.forceSni;
    this.decoderInitialBufferSize = other.getDecoderInitialBufferSize();
    this.tracingPolicy = other.tracingPolicy;
    this.shared = other.shared;
    this.name = other.name;
    this.http3MultiplexingLimit = other.http3MultiplexingLimit;
  }

  /**
   * Constructor to create an options from JSON
   *
   * @param json  the JSON
   */
  public HttpClientOptions(JsonObject json) {
    super(json);
    init();
    HttpClientOptionsConverter.fromJson(json, this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    HttpClientOptionsConverter.toJson(this, json);
    return json;
  }

  private void init() {
    verifyHost = DEFAULT_VERIFY_HOST;
    keepAlive = DEFAULT_KEEP_ALIVE;
    keepAliveTimeout = DEFAULT_KEEP_ALIVE_TIMEOUT;
    pipelining = DEFAULT_PIPELINING;
    pipeliningLimit = DEFAULT_PIPELINING_LIMIT;
    http2MultiplexingLimit = DEFAULT_HTTP2_MULTIPLEXING_LIMIT;
    http2ConnectionWindowSize = DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE;
    http2KeepAliveTimeout = DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT;
    decompressionSupported = DEFAULT_DECOMPRESSION_SUPPORTED;
    defaultHost = DEFAULT_DEFAULT_HOST;
    defaultPort = DEFAULT_DEFAULT_PORT;
    maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    maxInitialLineLength = DEFAULT_MAX_INITIAL_LINE_LENGTH;
    maxHeaderSize = DEFAULT_MAX_HEADER_SIZE;
    initialSettings = new Http2Settings();
    initialHttp3Settings = new Http3Settings();
    alpnVersions = new ArrayList<>(DEFAULT_ALPN_VERSIONS);
    http2ClearTextUpgrade = DEFAULT_HTTP2_CLEAR_TEXT_UPGRADE;
    http2ClearTextUpgradeWithPreflightRequest = DEFAULT_HTTP2_CLEAR_TEXT_UPGRADE_WITH_PREFLIGHT_REQUEST;
    maxRedirects = DEFAULT_MAX_REDIRECTS;
    forceSni = DEFAULT_FORCE_SNI;
    decoderInitialBufferSize = DEFAULT_DECODER_INITIAL_BUFFER_SIZE;
    tracingPolicy = DEFAULT_TRACING_POLICY;
    shared = DEFAULT_SHARED;
    name = DEFAULT_NAME;
    http3MultiplexingLimit = DEFAULT_HTTP3_MULTIPLEXING_LIMIT;
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
  public HttpClientOptions setReusePort(boolean reusePort) {
    super.setReusePort(reusePort);
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
  public HttpClientOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public HttpClientOptions setReadIdleTimeout(int idleTimeout) {
    super.setReadIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public HttpClientOptions setWriteIdleTimeout(int idleTimeout) {
    super.setWriteIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public HttpClientOptions setIdleTimeoutUnit(TimeUnit idleTimeoutUnit) {
    super.setIdleTimeoutUnit(idleTimeoutUnit);
    return this;
  }

  @Override
  public HttpClientOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public HttpClientOptions setKeyCertOptions(KeyCertOptions options) {
    super.setKeyCertOptions(options);
    return this;
  }

  @Override
  public HttpClientOptions setTrustOptions(TrustOptions options) {
    super.setTrustOptions(options);
    return this;
  }

  @Override
  public HttpClientOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public HttpClientOptions removeEnabledCipherSuite(String suite) {
    super.removeEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public HttpClientOptions addEnabledSecureTransportProtocol(final String protocol) {
    super.addEnabledSecureTransportProtocol(protocol);
    return this;
  }

  @Override
  public HttpClientOptions removeEnabledSecureTransportProtocol(String protocol) {
    return (HttpClientOptions) super.removeEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public HttpClientOptions setTcpFastOpen(boolean tcpFastOpen) {
    return (HttpClientOptions) super.setTcpFastOpen(tcpFastOpen);
  }

  @Override
  public HttpClientOptions setTcpCork(boolean tcpCork) {
    return (HttpClientOptions) super.setTcpCork(tcpCork);
  }

  @Override
  public HttpClientOptions setTcpQuickAck(boolean tcpQuickAck) {
    return (HttpClientOptions) super.setTcpQuickAck(tcpQuickAck);
  }

  @Override
  public HttpClientOptions setTcpUserTimeout(int tcpUserTimeout) {
    return (HttpClientOptions) super.setTcpUserTimeout(tcpUserTimeout);
  }

  @Override
  public HttpClientOptions addCrlPath(String crlPath) throws NullPointerException {
    return (HttpClientOptions) super.addCrlPath(crlPath);
  }

  @Override
  public HttpClientOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    return (HttpClientOptions) super.addCrlValue(crlValue);
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

  @Override
  public HttpClientOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
    return this;
  }

  @Override
  public HttpClientOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    super.setSslHandshakeTimeout(sslHandshakeTimeout);
    return this;
  }

  @Override
  public HttpClientOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    super.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
    return this;
  }

  /**
   * @return the maximum number of concurrent streams for an HTTP/2 connection, {@code -1} means
   * the value sent by the server
   */
  public int getHttp2MultiplexingLimit() {
    return http2MultiplexingLimit;
  }

  /**
   * Set a client limit of the number concurrent streams for each HTTP/2 connection, this limits the number
   * of streams the client can create for a connection. The effective number of streams for a
   * connection is the min of this value and the server's initial settings.
   * <p/>
   * Setting the value to {@code -1} means to use the value sent by the server's initial settings.
   * {@code -1} is the default value.
   *
   * @param limit the maximum concurrent for an HTTP/2 connection
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setHttp2MultiplexingLimit(int limit) {
    if (limit == 0 || limit < -1) {
      throw new IllegalArgumentException("maxPoolSize must be > 0 or -1 (disabled)");
    }
    this.http2MultiplexingLimit = limit;
    return this;
  }

  /**
   * @return the default HTTP/2 connection window size
   */
  public int getHttp2ConnectionWindowSize() {
    return http2ConnectionWindowSize;
  }

  /**
   * Set the default HTTP/2 connection window size. It overrides the initial window
   * size set by {@link Http2Settings#getInitialWindowSize}, so the connection window size
   * is greater than for its streams, in order the data throughput.
   * <p/>
   * A value of {@code -1} reuses the initial window size setting.
   *
   * @param http2ConnectionWindowSize the window size applied to the connection
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setHttp2ConnectionWindowSize(int http2ConnectionWindowSize) {
    this.http2ConnectionWindowSize = http2ConnectionWindowSize;
    return this;
  }

  /**
   * @return the keep alive timeout value in seconds for HTTP/2 connections
   */
  public int getHttp2KeepAliveTimeout() {
    return http2KeepAliveTimeout;
  }

  /**
   * Set the keep alive timeout for HTTP/2 connections, in seconds.
   * <p/>
   * This value determines how long a connection remains unused in the pool before being evicted and closed.
   * <p/>
   * A timeout of {@code 0} means there is no timeout.
   *
   * @param keepAliveTimeout the timeout, in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setHttp2KeepAliveTimeout(int keepAliveTimeout) {
    if (keepAliveTimeout < 0) {
      throw new IllegalArgumentException("HTTP/2 keepAliveTimeout must be >= 0");
    }
    this.http2KeepAliveTimeout = keepAliveTimeout;
    return this;
  }

  /**
   * Is keep alive enabled on the client?
   *
   * @return {@code true} if enabled
   */
  public boolean isKeepAlive() {
    return keepAlive;
  }

  /**
   * Set whether keep alive is enabled on the client
   *
   * @param keepAlive {@code true} if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  /**
   * @return the keep alive timeout value in seconds for HTTP/1.x connections
   */
  public int getKeepAliveTimeout() {
    return keepAliveTimeout;
  }

  /**
   * Set the keep alive timeout for HTTP/1.x, in seconds.
   * <p/>
   * This value determines how long a connection remains unused in the pool before being evicted and closed.
   * <p/>
   * A timeout of {@code 0} means there is no timeout.
   *
   * @param keepAliveTimeout the timeout, in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setKeepAliveTimeout(int keepAliveTimeout) {
    if (keepAliveTimeout < 0) {
      throw new IllegalArgumentException("keepAliveTimeout must be >= 0");
    }
    this.keepAliveTimeout = keepAliveTimeout;
    return this;
  }

  /**
   * Is pipe-lining enabled on the client
   *
   * @return {@code true} if pipe-lining is enabled
   */
  public boolean isPipelining() {
    return pipelining;
  }

  /**
   * Set whether pipe-lining is enabled on the client
   *
   * @param pipelining {@code true} if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setPipelining(boolean pipelining) {
    this.pipelining = pipelining;
    return this;
  }

  /**
   * @return the limit of pending requests a pipe-lined HTTP/1 connection can send
   */
  public int getPipeliningLimit() {
    return pipeliningLimit;
  }

  /**
   * Set the limit of pending requests a pipe-lined HTTP/1 connection can send.
   *
   * @param limit the limit of pending requests
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setPipeliningLimit(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("pipeliningLimit must be > 0");
    }
    this.pipeliningLimit = limit;
    return this;
  }

  /**
   * Is hostname verification (for SSL/TLS) enabled?
   *
   * @return {@code true} if enabled
   */
  public boolean isVerifyHost() {
    return verifyHost;
  }

  /**
   * Set whether hostname verification is enabled
   *
   * @param verifyHost {@code true} if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setVerifyHost(boolean verifyHost) {
    this.verifyHost = verifyHost;
    return this;
  }

  /**
   * @return {@code true} if the client should send requests with an {@code accepting-encoding} header set to a compression algorithm, {@code false} otherwise
   */
  public boolean isDecompressionSupported() {
    return decompressionSupported;
  }

  /**
   * Whether the client should send requests with an {@code accepting-encoding} header set to a compression algorithm.
   *
   * @param decompressionSupported {@code true} if the client should send a request with an {@code accepting-encoding} header set to a compression algorithm, {@code false} otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setDecompressionSupported(boolean decompressionSupported) {
    this.decompressionSupported = decompressionSupported;
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
  public HttpClientOptions setProtocolVersion(HttpVersion protocolVersion) {
    super.setProtocolVersion(protocolVersion);
    return this;
  }

  /**
   * Set the maximum HTTP chunk size
   * @param maxChunkSize the maximum chunk size
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxChunkSize(int maxChunkSize) {
    this.maxChunkSize = maxChunkSize;
    return this;
  }

  /**
   * Returns the maximum HTTP chunk size
   * @return the maximum HTTP chunk size
   */
  public int getMaxChunkSize() {
    return maxChunkSize;
  }

  /**
   * @return the maximum length of the initial line for HTTP/1.x (e.g. {@code "GET / HTTP/1.0"})
   */
  public int getMaxInitialLineLength() {
    return maxInitialLineLength;
  }

  /**
   * Set the maximum length of the initial line for HTTP/1.x (e.g. {@code "HTTP/1.1 200 OK"})
   *
   * @param maxInitialLineLength the new maximum initial length
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxInitialLineLength(int maxInitialLineLength) {
    this.maxInitialLineLength = maxInitialLineLength;
    return this;
  }

  /**
   * @return Returns the maximum length of all headers for HTTP/1.x
   */
  public int getMaxHeaderSize() {
    return maxHeaderSize;
  }

  /**
   * Set the maximum length of all headers for HTTP/1.x .
   *
   * @param maxHeaderSize the new maximum length
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxHeaderSize(int maxHeaderSize) {
    this.maxHeaderSize = maxHeaderSize;
    return this;
  }

  /**
   * @return the initial HTTP/2 connection settings
   */
  public Http2Settings getInitialSettings() {
    return initialSettings;
  }

  /**
   * Set the HTTP/2 connection settings immediately sent by to the server when the client connects.
   *
   * @param settings the settings value
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setInitialSettings(Http2Settings settings) {
    this.initialSettings = settings;
    return this;
  }

  /**
   * @return the initial HTTP/3 connection settings
   */
  public Http3Settings getInitialHttp3Settings() {
    return initialHttp3Settings;
  }

  /**
   * Set the HTTP/3 connection settings immediately sent by to the server when the client connects.
   *
   * @param settings the settings value
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setInitialHttp3Settings(Http3Settings settings) {
    this.initialHttp3Settings = settings;
    return this;
  }

  @Override
  public HttpClientOptions setUseAlpn(boolean useAlpn) {
    return (HttpClientOptions) super.setUseAlpn(useAlpn);
  }

  @Override
  public HttpClientOptions setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    return (HttpClientOptions) super.setSslEngineOptions(sslEngineOptions);
  }

  /**
   * @return the list of protocol versions to provide during the Application-Layer Protocol Negotiation. When
   * the list is empty, the client provides a best effort list according to {@link #setProtocolVersion}
   */
  public List<HttpVersion> getAlpnVersions() {
    return alpnVersions;
  }

  /**
   * Set the list of protocol versions to provide to the server during the Application-Layer Protocol Negotiation.
   * When the list is empty, the client provides a best effort list according to {@link #setProtocolVersion}:
   *
   * <ul>
   *   <li>{@link HttpVersion#HTTP_2}: [ "h2", "http/1.1" ]</li>
   *   <li>otherwise: [{@link #getProtocolVersion()}]</li>
   * </ul>
   *
   * @param alpnVersions the versions
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setAlpnVersions(List<HttpVersion> alpnVersions) {
    this.alpnVersions = alpnVersions;
    return this;
  }

  /**
   * @return {@code true} when an <i>h2c</i> connection is established using an HTTP/1.1 upgrade request, {@code false} when directly
   */
  public boolean isHttp2ClearTextUpgrade() {
    return http2ClearTextUpgrade;
  }

  /**
   * Set to {@code true} when an <i>h2c</i> connection is established using an HTTP/1.1 upgrade request, and {@code false}
   * when an <i>h2c</i> connection is established directly (with prior knowledge).
   *
   * @param value the upgrade value
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setHttp2ClearTextUpgrade(boolean value) {
    this.http2ClearTextUpgrade = value;
    return this;
  }

  /**
   * @return {@code true} when an <i>h2c</i> connection established using an HTTP/1.1 upgrade request should perform
   *         a preflight {@code OPTIONS} request to the origin server to establish the <i>h2c</i> connection
   */
  public boolean isHttp2ClearTextUpgradeWithPreflightRequest() {
    return http2ClearTextUpgradeWithPreflightRequest;
  }

  /**
   * Set to {@code true} when an <i>h2c</i> connection established using an HTTP/1.1 upgrade request should perform
   * a preflight {@code OPTIONS} request to the origin server to establish the <i>h2c</i> connection.
   *
   * @param value the upgrade value
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setHttp2ClearTextUpgradeWithPreflightRequest(boolean value) {
    this.http2ClearTextUpgradeWithPreflightRequest = value;
    return this;
  }

  /**
   * @return the maximum number of redirection a request can follow
   */
  public int getMaxRedirects() {
    return maxRedirects;
  }

  /**
   * Set to {@code maxRedirects} the maximum number of redirection a request can follow.
   *
   * @param maxRedirects the maximum number of redirection
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxRedirects(int maxRedirects) {
    this.maxRedirects = maxRedirects;
    return this;
  }

  /**
   * @return whether the client should always use SNI on TLS/SSL connections
   */
  public boolean isForceSni() {
    return forceSni;
  }

  /**
   * By default, the server name is only sent for Fully Qualified Domain Name (FQDN), setting
   * this property to {@code true} forces the server name to be always sent.
   *
   * @param forceSni {@code true} when the client should always use SNI on TLS/SSL connections
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setForceSni(boolean forceSni) {
    this.forceSni = forceSni;
    return this;
  }

  public HttpClientOptions setMetricsName(String metricsName) {
    return (HttpClientOptions) super.setMetricsName(metricsName);
  }

  public HttpClientOptions setProxyOptions(ProxyOptions proxyOptions) {
    return (HttpClientOptions) super.setProxyOptions(proxyOptions);
  }

  @Override
  public HttpClientOptions setNonProxyHosts(List<String> nonProxyHosts) {
    return (HttpClientOptions) super.setNonProxyHosts(nonProxyHosts);
  }

  @Override
  public HttpClientOptions addNonProxyHost(String nonProxyHost) {
    return (HttpClientOptions) super.addNonProxyHost(nonProxyHost);
  }

  @Override
  public HttpClientOptions setLocalAddress(String localAddress) {
    return (HttpClientOptions) super.setLocalAddress(localAddress);
  }

  @Override
  public HttpClientOptions setLogActivity(boolean logEnabled) {
    return (HttpClientOptions) super.setLogActivity(logEnabled);
  }

  @Override
  public HttpClientOptions setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    return (HttpClientOptions) super.setActivityLogDataFormat(activityLogDataFormat);
  }

  /**
   * @return the initial buffer size for the HTTP decoder
   */
  public int getDecoderInitialBufferSize() { return decoderInitialBufferSize; }

  /**
   * set to {@code initialBufferSizeHttpDecoder} the initial buffer of the HttpDecoder.
   *
   * @param decoderInitialBufferSize the initial buffer size
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setDecoderInitialBufferSize(int decoderInitialBufferSize) {
    Arguments.require(decoderInitialBufferSize > 0, "initialBufferSizeHttpDecoder must be > 0");
    this.decoderInitialBufferSize = decoderInitialBufferSize;
    return this;
  }

  /**
   * @return the tracing policy
   */
  public TracingPolicy getTracingPolicy() {
    return tracingPolicy;
  }

  /**
   * Set the tracing policy for the client behavior when Vert.x has tracing enabled.
   *
   * @param tracingPolicy the tracing policy
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setTracingPolicy(TracingPolicy tracingPolicy) {
    this.tracingPolicy = tracingPolicy;
    return this;
  }

  /**
   * @return whether the pool is shared
   */
  public boolean isShared() {
    return shared;
  }

  /**
   * Set to {@code true} to share the client.
   *
   * <p> There can be multiple shared clients distinguished by {@link #getName()}, when no specific
   * name is set, the {@link #DEFAULT_NAME} is used.
   *
   * @param shared {@code true} to use a shared client
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setShared(boolean shared) {
    this.shared = shared;
    return this;
  }

  /**
   * @return the client name used for sharing
   */
  public String getName() {
    return name;
  }

  /**
   * Set the client name, used when the client is shared, otherwise ignored.
   * @param name the new name
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setName(String name) {
    Objects.requireNonNull(name, "Client name cannot be null");
    this.name = name;
    return this;
  }

  /**
   * @return the maximum number of concurrent streams for an HTTP/3 connection, {@code -1} means
   * the value sent by the server
   */
  public int getHttp3MultiplexingLimit() {
    return http3MultiplexingLimit;
  }

  /**
   * Set a client limit of the number concurrent streams for each HTTP/3 connection, this limits the number
   * of streams the client can create for a connection. The effective number of streams for a
   * connection is the min of this value and the server's initial settings.
   * <p/>
   * Setting the value to {@code -1} means to use the value sent by the server's initial settings.
   * {@code -1} is the default value.
   *
   * @param limit the maximum concurrent for an HTTP/3 connection
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setHttp3MultiplexingLimit(int limit) {
    if (limit == 0 || limit < -1) {
      throw new IllegalArgumentException("maxPoolSize must be > 0 or -1 (disabled)");
    }
    this.http3MultiplexingLimit = limit;
    return this;
  }
}
