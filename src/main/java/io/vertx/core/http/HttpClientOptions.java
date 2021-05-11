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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.tracing.TracingPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Options describing how an {@link HttpClient} will make connections.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class HttpClientOptions extends ClientOptionsBase {

  /**
   * The default maximum number of HTTP/1 connections a client will pool = 5
   */
  public static final int DEFAULT_MAX_POOL_SIZE = 5;

  /**
   * The default maximum number of connections an HTTP/2 client will pool = 1
   */
  public static final int DEFAULT_HTTP2_MAX_POOL_SIZE = 1;

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
   * Default value of whether the client will attempt to use compression = {@code false}
   */
  public static final boolean DEFAULT_TRY_USE_COMPRESSION = false;

  /**
   * Default value of whether hostname verification (for SSL/TLS) is enabled = {@code true}
   */
  public static final boolean DEFAULT_VERIFY_HOST = true;

  /**
   * The default value for maximum WebSocket frame size = 65536 bytes
   */
  public static final int DEFAULT_MAX_WEBSOCKET_FRAME_SIZE = 65536;

  /**
   * The default value for maximum WebSocket messages (could be assembled from multiple frames) is 4 full frames
   * worth of data
   */
  public static final int DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE = 65536 * 4;

  /**
   * The default value for the maximum number of WebSocket = 50
   */
  public static final int DEFAULT_MAX_WEBSOCKETS = 50;

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
   * Default max wait queue size = -1 (unbounded)
   */
  public static final int DEFAULT_MAX_WAIT_QUEUE_SIZE = -1;

  /**
   * Default Application-Layer Protocol Negotiation versions = [] (automatic according to protocol version)
   */
  public static final List<HttpVersion> DEFAULT_ALPN_VERSIONS = Collections.emptyList();

  /**
   * Default using HTTP/1.1 upgrade for establishing an <i>h2C</i> connection = {@code true}
   */
  public static final boolean DEFAULT_HTTP2_CLEAR_TEXT_UPGRADE = true;

  /**
   * Default WebSocket masked bit is true as depicted by RFC = {@code false}
   */
  public static final boolean DEFAULT_SEND_UNMASKED_FRAMES = false;

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
   * Default offer WebSocket per-frame deflate compression extension = {@code false}
   */
  public static final boolean DEFAULT_TRY_USE_PER_FRAME_WEBSOCKET_COMPRESSION = false;

  /**
   * Default offer WebSocket per-message deflate compression extension = {@code false}
   */
  public static final boolean DEFAULT_TRY_USE_PER_MESSAGE_WEBSOCKET_COMPRESSION = false;

  /**
   * Default WebSocket deflate compression level = 6
   */
  public static final int DEFAULT_WEBSOCKET_COMPRESSION_LEVEL = 6;

  /**
   * Default offering of the {@code server_no_context_takeover} WebSocket parameter deflate compression extension = {@code false}
   */
  public static final boolean DEFAULT_WEBSOCKET_ALLOW_CLIENT_NO_CONTEXT = false;

  /**
   * Default offering of the {@code client_no_context_takeover} WebSocket parameter deflate compression extension = {@code false}
   */
  public static final boolean DEFAULT_WEBSOCKET_REQUEST_SERVER_NO_CONTEXT = false;

  /**
   * Default pool cleaner period = 1000 ms (1 second)
   */
  public static final int DEFAULT_POOL_CLEANER_PERIOD = 1000;

  /**
   * Default WebSocket closing timeout = 10 second
   */
  public static final int DEFAULT_WEBSOCKET_CLOSING_TIMEOUT = 10;

  /**
   * Default tracing control = {@link TracingPolicy#PROPAGATE}
   */
  public static final TracingPolicy DEFAULT_TRACING_POLICY = TracingPolicy.PROPAGATE;

  private boolean verifyHost = true;
  private int maxPoolSize;
  private boolean keepAlive;
  private int keepAliveTimeout;
  private int pipeliningLimit;
  private boolean pipelining;
  private int http2MaxPoolSize;
  private int http2MultiplexingLimit;
  private int http2ConnectionWindowSize;
  private int http2KeepAliveTimeout;
  private int poolCleanerPeriod;

  private boolean tryUseCompression;
  private int maxWebSocketFrameSize;
  private int maxWebSocketMessageSize;
  private int maxWebSockets;
  private String defaultHost;
  private int defaultPort;
  private HttpVersion protocolVersion;
  private int maxChunkSize;
  private int maxInitialLineLength;
  private int maxHeaderSize;
  private int maxWaitQueueSize;
  private Http2Settings initialSettings;
  private List<HttpVersion> alpnVersions;
  private boolean http2ClearTextUpgrade;
  private boolean sendUnmaskedFrames;
  private int maxRedirects;
  private boolean forceSni;
  private int decoderInitialBufferSize;

  private boolean tryUsePerFrameWebSocketCompression;
  private boolean tryUsePerMessageWebSocketCompression;
  private int webSocketCompressionLevel;
  private boolean webSocketAllowClientNoContext;
  private boolean webSocketRequestServerNoContext;
  private int webSocketClosingTimeout;

  private TracingPolicy tracingPolicy;

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
  public HttpClientOptions(HttpClientOptions other) {
    super(other);
    this.verifyHost = other.isVerifyHost();
    this.maxPoolSize = other.getMaxPoolSize();
    this.keepAlive = other.isKeepAlive();
    this.keepAliveTimeout = other.getKeepAliveTimeout();
    this.pipelining = other.isPipelining();
    this.pipeliningLimit = other.getPipeliningLimit();
    this.http2MaxPoolSize = other.getHttp2MaxPoolSize();
    this.http2MultiplexingLimit = other.http2MultiplexingLimit;
    this.http2ConnectionWindowSize = other.http2ConnectionWindowSize;
    this.http2KeepAliveTimeout = other.getHttp2KeepAliveTimeout();
    this.tryUseCompression = other.isTryUseCompression();
    this.maxWebSocketFrameSize = other.maxWebSocketFrameSize;
    this.maxWebSocketMessageSize = other.maxWebSocketMessageSize;
    this.maxWebSockets = other.maxWebSockets;
    this.defaultHost = other.defaultHost;
    this.defaultPort = other.defaultPort;
    this.protocolVersion = other.protocolVersion;
    this.maxChunkSize = other.maxChunkSize;
    this.maxInitialLineLength = other.getMaxInitialLineLength();
    this.maxHeaderSize = other.getMaxHeaderSize();
    this.maxWaitQueueSize = other.maxWaitQueueSize;
    this.initialSettings = other.initialSettings != null ? new Http2Settings(other.initialSettings) : null;
    this.alpnVersions = other.alpnVersions != null ? new ArrayList<>(other.alpnVersions) : null;
    this.http2ClearTextUpgrade = other.http2ClearTextUpgrade;
    this.sendUnmaskedFrames = other.isSendUnmaskedFrames();
    this.maxRedirects = other.maxRedirects;
    this.forceSni = other.forceSni;
    this.decoderInitialBufferSize = other.getDecoderInitialBufferSize();
    this.poolCleanerPeriod = other.getPoolCleanerPeriod();
    this.tryUsePerFrameWebSocketCompression = other.tryUsePerFrameWebSocketCompression;
    this.tryUsePerMessageWebSocketCompression = other.tryUsePerMessageWebSocketCompression;
    this.webSocketAllowClientNoContext = other.webSocketAllowClientNoContext;
    this.webSocketCompressionLevel = other.webSocketCompressionLevel;
    this.webSocketRequestServerNoContext = other.webSocketRequestServerNoContext;
    this.webSocketClosingTimeout = other.webSocketClosingTimeout;
    this.tracingPolicy = other.tracingPolicy;
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
    maxPoolSize = DEFAULT_MAX_POOL_SIZE;
    keepAlive = DEFAULT_KEEP_ALIVE;
    keepAliveTimeout = DEFAULT_KEEP_ALIVE_TIMEOUT;
    pipelining = DEFAULT_PIPELINING;
    pipeliningLimit = DEFAULT_PIPELINING_LIMIT;
    http2MultiplexingLimit = DEFAULT_HTTP2_MULTIPLEXING_LIMIT;
    http2MaxPoolSize = DEFAULT_HTTP2_MAX_POOL_SIZE;
    http2ConnectionWindowSize = DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE;
    http2KeepAliveTimeout = DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT;
    tryUseCompression = DEFAULT_TRY_USE_COMPRESSION;
    maxWebSocketFrameSize = DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
    maxWebSocketMessageSize = DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE;
    maxWebSockets = DEFAULT_MAX_WEBSOCKETS;
    defaultHost = DEFAULT_DEFAULT_HOST;
    defaultPort = DEFAULT_DEFAULT_PORT;
    protocolVersion = DEFAULT_PROTOCOL_VERSION;
    maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    maxInitialLineLength = DEFAULT_MAX_INITIAL_LINE_LENGTH;
    maxHeaderSize = DEFAULT_MAX_HEADER_SIZE;
    maxWaitQueueSize = DEFAULT_MAX_WAIT_QUEUE_SIZE;
    initialSettings = new Http2Settings();
    alpnVersions = new ArrayList<>(DEFAULT_ALPN_VERSIONS);
    http2ClearTextUpgrade = DEFAULT_HTTP2_CLEAR_TEXT_UPGRADE;
    sendUnmaskedFrames = DEFAULT_SEND_UNMASKED_FRAMES;
    maxRedirects = DEFAULT_MAX_REDIRECTS;
    forceSni = DEFAULT_FORCE_SNI;
    decoderInitialBufferSize = DEFAULT_DECODER_INITIAL_BUFFER_SIZE;
    tryUsePerFrameWebSocketCompression = DEFAULT_TRY_USE_PER_FRAME_WEBSOCKET_COMPRESSION;
    tryUsePerMessageWebSocketCompression = DEFAULT_TRY_USE_PER_MESSAGE_WEBSOCKET_COMPRESSION;
    webSocketCompressionLevel = DEFAULT_WEBSOCKET_COMPRESSION_LEVEL;
    webSocketAllowClientNoContext = DEFAULT_WEBSOCKET_ALLOW_CLIENT_NO_CONTEXT;
    webSocketRequestServerNoContext = DEFAULT_WEBSOCKET_REQUEST_SERVER_NO_CONTEXT;
    webSocketClosingTimeout = DEFAULT_WEBSOCKET_CLOSING_TIMEOUT;
    poolCleanerPeriod = DEFAULT_POOL_CLEANER_PERIOD;
    tracingPolicy = DEFAULT_TRACING_POLICY;
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
  public HttpClientOptions setKeyStoreOptions(JksOptions options) {
    super.setKeyStoreOptions(options);
    return this;
  }

  @Override
  public HttpClientOptions setPfxKeyCertOptions(PfxOptions options) {
    return (HttpClientOptions) super.setPfxKeyCertOptions(options);
  }

  @Override
  public HttpClientOptions setTrustOptions(TrustOptions options) {
    super.setTrustOptions(options);
    return this;
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
  public HttpClientOptions setPfxTrustOptions(PfxOptions options) {
    return (HttpClientOptions) super.setPfxTrustOptions(options);
  }

  @Override
  public HttpClientOptions setPemTrustOptions(PemTrustOptions options) {
    return (HttpClientOptions) super.setPemTrustOptions(options);
  }

  @Override
  public HttpClientOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
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
   * Get the maximum pool size for HTTP/2 connections
   *
   * @return  the maximum pool size
   */
  public int getHttp2MaxPoolSize() {
    return http2MaxPoolSize;
  }

  /**
   * Set the maximum pool size for HTTP/2 connections
   *
   * @param max  the maximum pool size
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setHttp2MaxPoolSize(int max) {
    if (max < 1) {
      throw new IllegalArgumentException("http2MaxPoolSize must be > 0");
    }
    this.http2MaxPoolSize = max;
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
   * Is compression enabled on the client?
   *
   * @return {@code true} if enabled
   */
  public boolean isTryUseCompression() {
    return tryUseCompression;
  }

  /**
   * Set whether compression is enabled
   *
   * @param tryUseCompression {@code true} if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setTryUseCompression(boolean tryUseCompression) {
    this.tryUseCompression = tryUseCompression;
    return this;
  }


  /**
  * @return {@code true} when frame masking is skipped
  */
  public boolean isSendUnmaskedFrames() {
    return sendUnmaskedFrames;
  }

  /**
   * Set {@code true} when the client wants to skip frame masking.
   * <p>
   * You may want to set it {@code true} on server by server WebSocket communication: in this case you are by passing
   * RFC6455 protocol.
   * <p>
   * It's {@code false} as default.
   *
   * @param sendUnmaskedFrames  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setSendUnmaskedFrames(boolean sendUnmaskedFrames) {
    this.sendUnmaskedFrames = sendUnmaskedFrames;
    return this;
  }

  /**
   * Get the maximum WebSocket frame size to use
   *
   * @return  the max WebSocket frame size
   */
  public int getMaxWebSocketFrameSize() {
    return maxWebSocketFrameSize;
  }

  /**
   * Set the max WebSocket frame size
   *
   * @param maxWebSocketFrameSize  the max frame size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxWebSocketFrameSize(int maxWebSocketFrameSize) {
    this.maxWebSocketFrameSize = maxWebSocketFrameSize;
    return this;
  }

  /**
   * Get the maximum WebSocket message size to use
   *
   * @return  the max WebSocket message size
   */
  public int getMaxWebSocketMessageSize() {
    return maxWebSocketMessageSize;
  }

  /**
   * Set the max WebSocket message size
   *
   * @param maxWebSocketMessageSize  the max message size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxWebSocketMessageSize(int maxWebSocketMessageSize) {
    this.maxWebSocketMessageSize = maxWebSocketMessageSize;
    return this;
  }

  /**
   * Get the maximum of WebSockets per endpoint.
   *
   * @return  the max number of WebSockets
   */
  public int getMaxWebSockets() {
    return maxWebSockets;
  }

  /**
   * Set the max number of WebSockets per endpoint.
   *
   * @param maxWebSockets  the max value
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxWebSockets(int maxWebSockets) {
    if (maxWebSockets == 0 || maxWebSockets < -1) {
      throw new IllegalArgumentException("maxWebSockets must be > 0 or -1 (disabled)");
    }
    this.maxWebSockets = maxWebSockets;
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

  /**
   * Get the protocol version.
   *
   * @return the protocol version
   */
  public HttpVersion getProtocolVersion() {
    return protocolVersion;
  }

  /**
   * Set the protocol version.
   *
   * @param protocolVersion the protocol version
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setProtocolVersion(HttpVersion protocolVersion) {
    if (protocolVersion == null) {
      throw new IllegalArgumentException("protocolVersion must not be null");
    }
    this.protocolVersion = protocolVersion;
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
   * Set the maximum requests allowed in the wait queue, any requests beyond the max size will result in
   * a ConnectionPoolTooBusyException.  If the value is set to a negative number then the queue will be unbounded.
   * @param maxWaitQueueSize the maximum number of waiting requests
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxWaitQueueSize(int maxWaitQueueSize) {
    this.maxWaitQueueSize = maxWaitQueueSize;
    return this;
  }

  /**
   * Returns the maximum wait queue size
   * @return the maximum wait queue size
   */
  public int getMaxWaitQueueSize() {
    return maxWaitQueueSize;
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

  @Override
  public HttpClientOptions setUseAlpn(boolean useAlpn) {
    return (HttpClientOptions) super.setUseAlpn(useAlpn);
  }

  @Override
  public HttpClientOptions setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    return (HttpClientOptions) super.setSslEngineOptions(sslEngineOptions);
  }

  @Override
  public HttpClientOptions setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return (HttpClientOptions) super.setSslEngineOptions(sslEngineOptions);
  }

  @Override
  public HttpClientOptions setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
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

  /**
   * Set whether the client will offer the WebSocket per-frame deflate compression extension.
   *
   * @param offer {@code true} to offer the per-frame deflate compression extension
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setTryUsePerFrameWebSocketCompression(boolean offer) {
    this.tryUsePerFrameWebSocketCompression = offer;
    return this;
  }

  /**
   * @return {@code true} when the WebSocket per-frame deflate compression extension will be offered
   */
  public boolean getTryWebSocketDeflateFrameCompression() {
    return this.tryUsePerFrameWebSocketCompression;
  }

  /**
   * Set whether the client will offer the WebSocket per-message deflate compression extension.
   *
   * @param offer {@code true} to offer the per-message deflate compression extension
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setTryUsePerMessageWebSocketCompression(boolean offer) {
    this.tryUsePerMessageWebSocketCompression = offer;
    return this;
  }

  /**
   * @return {@code true} when the WebSocket per-message deflate compression extension will be offered
   */
  public boolean getTryUsePerMessageWebSocketCompression() {
    return this.tryUsePerMessageWebSocketCompression;
  }

  /**
   * Set the WebSocket deflate compression level.
   *
   * @param compressionLevel the WebSocket deflate compression level
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setWebSocketCompressionLevel(int compressionLevel) {
    this.webSocketCompressionLevel = compressionLevel;
    return this;
  }

  /**
   * @return the WebSocket deflate compression level
   */
  public int getWebSocketCompressionLevel() {
    return this.webSocketCompressionLevel;
  }

  /**
   * Set whether the {@code client_no_context_takeover} parameter of the WebSocket per-message
   * deflate compression extension will be offered.
   *
   * @param offer {@code true} to offer the {@code client_no_context_takeover} parameter
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setWebSocketCompressionAllowClientNoContext(boolean offer) {
    this.webSocketAllowClientNoContext = offer;
    return this;
  }

  /**
   * @return {@code true} when the {@code client_no_context_takeover} parameter for the WebSocket per-message
   * deflate compression extension will be offered
   */
  public boolean getWebSocketCompressionAllowClientNoContext() {
    return this.webSocketAllowClientNoContext;
  }

  /**
   * Set whether the {@code server_no_context_takeover} parameter of the WebSocket per-message
   * deflate compression extension will be offered.
   *
   * @param offer {@code true} to offer the {@code server_no_context_takeover} parameter
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setWebSocketCompressionRequestServerNoContext(boolean offer) {
    this.webSocketRequestServerNoContext = offer;
    return this;
  }

  /**
   * @return {@code true} when the {@code server_no_context_takeover} parameter for the WebSocket per-message
   * deflate compression extension will be offered
   */
  public boolean getWebSocketCompressionRequestServerNoContext() {
    return this.webSocketRequestServerNoContext;
  }

  /**
   * @return the amount of time (in seconds) a client WebSocket will wait until it closes TCP connection after receiving a close frame
   */
  public int getWebSocketClosingTimeout() {
    return webSocketClosingTimeout;
  }

  /**
   * Set the amount of time a client WebSocket will wait until it closes the TCP connection after receiving a close frame.
   *
   * <p> When a WebSocket is closed, the server should close the TCP connection. This timeout will close
   * the TCP connection on the client when it expires.
   *
   * <p> Set to {@code 0L} closes the TCP connection immediately after receiving the close frame.
   *
   * <p> Set to a negative value to disable it.
   *
   * @param webSocketClosingTimeout the timeout is seconds
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setWebSocketClosingTimeout(int webSocketClosingTimeout) {
    this.webSocketClosingTimeout = webSocketClosingTimeout;
    return this;
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
   * @return the connection pool cleaner period in ms.
   */
  public int getPoolCleanerPeriod() {
    return poolCleanerPeriod;
  }

  /**
   * Set the connection pool cleaner period in milli seconds, a non positive value disables expiration checks and connections
   * will remain in the pool until they are closed.
   *
   * @param poolCleanerPeriod the pool cleaner period
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setPoolCleanerPeriod(int poolCleanerPeriod) {
    this.poolCleanerPeriod = poolCleanerPeriod;
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
}
