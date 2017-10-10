/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
   * Default value of whether keep-alive is enabled = true
   */
  public static final boolean DEFAULT_KEEP_ALIVE = true;

  /**
   * Default value of whether pipe-lining is enabled = false
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
   * The default value for maximum websocket messages (could be assembled from multiple frames) is 4 full frames
   * worth of data
   */
  public static final int DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE = 65536 * 4;

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
   * Default using HTTP/1.1 upgrade for establishing an <i>h2C</i> connection = true
   */
  public static final boolean DEFAULT_HTTP2_CLEAR_TEXT_UPGRADE = true;

  /**
   * Default WebSocket Masked bit is true as depicted by RFC. PerformingUnMasking will be false
   */
  public static final boolean DEFAULT_SEND_UNMASKED_FRAMES = false;

  /*
   * Default max redirect = 16
   */
  public static final int DEFAULT_MAX_REDIRECTS = 16;

  /*
   * Default force SNI = false
   */
  public static final boolean DEFAULT_FORCE_SNI = false;

  /**
   * Default initial buffer size for HttpObjectDecoder = 128 bytes
   */
  public static final int DEFAULT_DECODER_INITIAL_BUFFER_SIZE = 128;

  /**
   * Default offer WebSocket Deflate Frame compression = false
   */
  public static final boolean DEFAULT_TRY_USE_WEBSOCKET_DEFLATE_FRAME = false;

  /**
   * Default offer WebSocket Permessage Deflate Compression = false
   */
  public static final boolean DEFAULT_TRY_USE_WEBSOCKET_PERMESSAGE_DEFLATE = false;

  /**
   * Default WebSocket compression level = 6
   */
  public static final int DEFAULT_WEBSOCKET_COMPRESSION_LEVEL = 6;

  /**
   * Default WebSocket Compression client no context allowance = false
   */
  public static final boolean DEFAULT_WEBSOCKET_COMPRESSION_ALLOW_CLIENT_NO_CONTEXT = false;

  /**
   * Default WebSocket Compression server no context allowance = false
   */
  public static final boolean DEFAULT_WEBSOCKET_COMPRESSION_REQUEST_SERVER_NO_CONTEXT = false;


  /**
   * Default pool cleaner period = 1000 ms (1 second)
   */
  public static final int DEFAULT_POOL_CLEANER_PERIOD = 1000;

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
  private int maxWebsocketFrameSize;
  private int maxWebsocketMessageSize;
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

  private boolean websocketTryUseDeflateFrame;
  private boolean websocketTryUsePermessageDeflate;
  private int websocketCompressionLevel;
  private boolean websocketAllowClientNoContext;
  private boolean websocketRequestServerNoContext;


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
    this.maxWebsocketFrameSize = other.maxWebsocketFrameSize;
    this.maxWebsocketMessageSize = other.maxWebsocketMessageSize;
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
    this.websocketTryUseDeflateFrame = other.websocketTryUseDeflateFrame;
    this.websocketTryUsePermessageDeflate = other.websocketTryUsePermessageDeflate;
    this.websocketAllowClientNoContext = other.websocketAllowClientNoContext;
    this.websocketCompressionLevel = other.websocketCompressionLevel;
    this.websocketRequestServerNoContext = other.websocketRequestServerNoContext;
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
    maxWebsocketFrameSize = DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
    maxWebsocketMessageSize = DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE;
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
    websocketTryUseDeflateFrame = DEFAULT_TRY_USE_WEBSOCKET_DEFLATE_FRAME;
    websocketTryUsePermessageDeflate = DEFAULT_TRY_USE_WEBSOCKET_PERMESSAGE_DEFLATE;
    websocketCompressionLevel = DEFAULT_WEBSOCKET_COMPRESSION_LEVEL;
    websocketAllowClientNoContext = DEFAULT_WEBSOCKET_COMPRESSION_ALLOW_CLIENT_NO_CONTEXT;
    websocketRequestServerNoContext = DEFAULT_WEBSOCKET_COMPRESSION_REQUEST_SERVER_NO_CONTEXT;
    poolCleanerPeriod = DEFAULT_POOL_CLEANER_PERIOD;
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
   * @return the keep alive timeout value in seconds for HTTP/1.x connections
   */
  public int getKeepAliveTimeout() {
    return keepAliveTimeout;
  }

  /**
   * Set the keep alive timeout for HTTP/1.x, in seconds.
   * <p/>
   * This value determines how long a connection remains unused in the pool before being evicted and closed.
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
  * is masking frame skipped ?
  * @return
  */
  public boolean isSendUnmaskedFrames() {
    return sendUnmaskedFrames;
  }

  /**
   * Set true when the client wants to skip frame masking.
   * You may want to set it true on server by server websocket communication: In this case you are by passing RFC6455 protocol.
   * It's false as default.
   *
   * @param sendUnmaskedFrames  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setSendUnmaskedFrames(boolean sendUnmaskedFrames) {
    this.sendUnmaskedFrames = sendUnmaskedFrames;
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
   * Get the maximum websocket message size to use
   *
   * @return  the max websocket message size
   */
  public int getMaxWebsocketMessageSize() {
    return maxWebsocketMessageSize;
  }

  /**
   * Set the max websocket message size
   *
   * @param maxWebsocketMessageSize  the max message size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientOptions setMaxWebsocketMessageSize(int maxWebsocketMessageSize) {
    this.maxWebsocketMessageSize = maxWebsocketMessageSize;
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
   * @return true when an <i>h2c</i> connection is established using an HTTP/1.1 upgrade request, false when directly
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
   * @param forceSni true when the client should always use SNI on TLS/SSL connections
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
  public HttpClientOptions setLocalAddress(String localAddress) {
    return (HttpClientOptions) super.setLocalAddress(localAddress);
  }

  @Override
  public HttpClientOptions setLogActivity(boolean logEnabled) {
    return (HttpClientOptions) super.setLogActivity(logEnabled);
  }

  /**
   * Set option to offer Deflate Frame websocket compression
   * @param tryDeflateFrame
   * @return  a reference to this, so the API can be used fluently
   */
 public HttpClientOptions setTryUsePerFrameWebsocketCompression (boolean tryUsePerFrameWebsocketCompression )
 {
	 this.websocketTryUseDeflateFrame = tryUsePerFrameWebsocketCompression;
	 return this;
 }

 /**
  *
  * @return Whether Deflate Frame websocket compression will be offered
  */
 public boolean tryWebsocketDeflateFrameCompression()
 {
	 return this.websocketTryUseDeflateFrame;
 }

/**
 * Set option to offer Permessage Deflate websocket compression
 * @param tryPermessageDeflate
 * @return  a reference to this, so the API can be used fluently
 */
 public HttpClientOptions setTryUsePerMessageWebsocketCompression (boolean tryUsePerMessageWebsocketCompression )
 {
	 this.websocketTryUsePermessageDeflate = tryUsePerMessageWebsocketCompression;
	 return this;
 }

 /**
  *
  * @return whether Permessage Deflate websocket compression will be offered
  */
 public boolean tryUsePerMessageWebsocketCompression ()
 {
	 return this.websocketTryUsePermessageDeflate;
 }

 /**
  * Set websocket compression level
  * @param compressionLevel
  * @return a reference to this, so the API can be used fluently
  */
 public HttpClientOptions setWebsocketCompressionLevel (int websocketCompressionLevel)
 {
	 this.websocketCompressionLevel = websocketCompressionLevel;
	 return this;
 }

 /**
  *
  * @return websocket compression level
  */
 public int websocketCompressionLevel()
 {
	 return this.websocketCompressionLevel;
 }

 /**
  * Set the websocket compression allow client no context option
  * @param allowClientNoContext
  * @return a reference to this, so the API can be used fluently
  */
 public HttpClientOptions setWebsocketCompressionAllowClientNoContext(boolean allowClientNoContext)
 {
	 this.websocketAllowClientNoContext = allowClientNoContext;
	 return this;
 }

 /**
  *
  * @return the current websocket compression client no context setting
  */
 public boolean getWebsocketCompressionAllowClientNoContext()
 {
	 return this.websocketAllowClientNoContext;
 }

 /**
  * Set the websocket compression server no context option
  * @param requestServerNoContext
  * @return a reference to this, so the API can be used fluently
  */
 public HttpClientOptions setWebsocketCompressionRequestServerNoContext(boolean requestServerNoContext)
 {
	this.websocketRequestServerNoContext = requestServerNoContext;
	return this;
 }

 /**
  *
  * @return current setting of the websocket compression server no context option
  */
 public boolean getWebsocketCompressionRequestServerNoContext()
 {
	 return this.websocketRequestServerNoContext;
 }

  /**
   * @return the initial buffer size for the HTTP decoder
   */
  public int getDecoderInitialBufferSize() { return decoderInitialBufferSize; }

  /**
   * set to {@code initialBufferSizeHttpDecoder} the initial buffer of the HttpDecoder.
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HttpClientOptions)) return false;
    if (!super.equals(o)) return false;

    HttpClientOptions that = (HttpClientOptions) o;

    if (defaultPort != that.defaultPort) return false;
    if (keepAlive != that.keepAlive) return false;
    if (maxPoolSize != that.maxPoolSize) return false;
    if (http2MultiplexingLimit != that.http2MultiplexingLimit) return false;
    if (maxWebsocketFrameSize != that.maxWebsocketFrameSize) return false;
    if (maxWebsocketMessageSize != that.maxWebsocketMessageSize) return false;
    if (pipelining != that.pipelining) return false;
    if (pipeliningLimit != that.pipeliningLimit) return false;
    if (tryUseCompression != that.tryUseCompression) return false;
    if (verifyHost != that.verifyHost) return false;
    if (!defaultHost.equals(that.defaultHost)) return false;
    if (protocolVersion != that.protocolVersion) return false;
    if (maxChunkSize != that.maxChunkSize) return false;
    if (maxWaitQueueSize != that.maxWaitQueueSize) return false;
    if (initialSettings == null ? that.initialSettings != null : !initialSettings.equals(that.initialSettings)) return false;
    if (alpnVersions == null ? that.alpnVersions != null : !alpnVersions.equals(that.alpnVersions)) return false;
    if (http2ClearTextUpgrade != that.http2ClearTextUpgrade) return false;
    if (http2ConnectionWindowSize != that.http2ConnectionWindowSize) return false;
    if (sendUnmaskedFrames != that.sendUnmaskedFrames) return false;
    if (maxRedirects != that.maxRedirects) return false;
    if (decoderInitialBufferSize != that.decoderInitialBufferSize) return false;
    if (keepAliveTimeout != that.keepAliveTimeout) return false;
    if (http2KeepAliveTimeout != that.http2KeepAliveTimeout) return false;
    if (poolCleanerPeriod != that.poolCleanerPeriod) return false;

    if (websocketTryUseDeflateFrame != that.websocketTryUseDeflateFrame) return false;
    if (websocketTryUsePermessageDeflate != that.websocketTryUsePermessageDeflate) return false;
    if (websocketCompressionLevel != that.websocketCompressionLevel) return false;
    if (websocketAllowClientNoContext != that.websocketAllowClientNoContext) return false;
    if (websocketRequestServerNoContext != that.websocketRequestServerNoContext) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (verifyHost ? 1 : 0);
    result = 31 * result + maxPoolSize;
    result = 31 * result + http2MultiplexingLimit;
    result = 31 * result + (keepAlive ? 1 : 0);
    result = 31 * result + (pipelining ? 1 : 0);
    result = 31 * result + pipeliningLimit;
    result = 31 * result + (tryUseCompression ? 1 : 0);
    result = 31 * result + maxWebsocketFrameSize;
    result = 31 * result + maxWebsocketMessageSize;
    result = 31 * result + defaultHost.hashCode();
    result = 31 * result + defaultPort;
    result = 31 * result + protocolVersion.hashCode();
    result = 31 * result + maxChunkSize;
    result = 31 * result + maxWaitQueueSize;
    result = 31 * result + (initialSettings != null ? initialSettings.hashCode() : 0);
    result = 31 * result + (alpnVersions != null ? alpnVersions.hashCode() : 0);
    result = 31 * result + (http2ClearTextUpgrade ? 1 : 0);
    result = 31 * result + http2ConnectionWindowSize;
    result = 31 * result + (sendUnmaskedFrames ? 1 : 0);
    result = 31 * result + maxRedirects;
    result = 31 * result + decoderInitialBufferSize;
    result = 31 * result + keepAliveTimeout;
    result = 31 * result + http2KeepAliveTimeout;
    result = 31 * result + poolCleanerPeriod;
    result = 31 * result + (websocketTryUseDeflateFrame ? 1 : 0);
    result = 31 * result + (websocketTryUsePermessageDeflate ? 1 : 0);
    result = 31 * result + websocketCompressionLevel;
    result = 31 * result + (websocketAllowClientNoContext ? 1 : 0);
    result = 31 * result + (websocketRequestServerNoContext ? 1: 0);
    return result;
  }

}
