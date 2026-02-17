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

import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.logging.ByteBufFormat;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.tracing.TracingPolicy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Represents options used by an {@link io.vertx.core.http.HttpServer} instance
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class HttpServerOptions extends NetServerOptions {

  /**
   * Default port the server will listen on = 80
   */
  public static final int DEFAULT_PORT = 80;  // Default port is 80 for HTTP not 0 from HttpServerOptions

  /**
   * Default value of whether compression is supported = {@code false}
   */
  public static final boolean DEFAULT_COMPRESSION_SUPPORTED = false;

  /**
   * Default gzip/deflate compression level = 6 (Netty legacy)
   */
  public static final int DEFAULT_COMPRESSION_LEVEL = 6;

  /**
   * Default content size threshold for compression = 0 (Netty default)
   */
  public static final int DEFAULT_COMPRESSION_CONTENT_SIZE_THRESHOLD = 0;

  /**
   * Default max WebSocket frame size = 65536
   */
  public static final int DEFAULT_MAX_WEBSOCKET_FRAME_SIZE = 65536;

  /**
   * Default max WebSocket message size (could be assembled from multiple frames) is 4 full frames
   * worth of data
   */
  public static final int DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE = 65536 * 4;

  /**
   * Default max HTTP chunk size = 8192
   */
  public static final int DEFAULT_MAX_CHUNK_SIZE = 8192;

  /**
   * Default max length of the initial line (e.g. {@code "GET / HTTP/1.0"}) = 4096
   */
  public static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4096;

  /**
   * Default max length of all headers = 8192
   */
  public static final int DEFAULT_MAX_HEADER_SIZE = 8192;

  /**
   * Default max size of a form attribute = 8192
   */
  public static final int DEFAULT_MAX_FORM_ATTRIBUTE_SIZE = 8192;

  /**
   * Default max number of form fields = 256
   */
  public static final int DEFAULT_MAX_FORM_FIELDS = 256;

  /**
   * Default max number buffered bytes when decoding a form = 1024
   */
  public static final int DEFAULT_MAX_FORM_BUFFERED_SIZE = 1024;

  /**
   * Default value of whether 100-Continue should be handled automatically = {@code false}
   */
  public static final boolean DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY = false;

  /**
   * Default Application-Layer Protocol Negotiation versions = [HTTP/2,HTTP/1.1]
   */
  public static final List<HttpVersion> DEFAULT_ALPN_VERSIONS = Collections.unmodifiableList(Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1));

  /**
   * Default H2C is enabled = {@code true}
   */
  public static final boolean DEFAULT_HTTP2_CLEAR_TEXT_ENABLED = true;

  /**
   * The default initial settings max concurrent stream for an HTTP/2 server = 100
   */
  public static final long DEFAULT_INITIAL_SETTINGS_MAX_CONCURRENT_STREAMS = 100;

  /**
   * The default HTTP/2 connection window size = -1
   */
  public static final int DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE = -1;

  /**
   * Default value of whether decompression is supported = {@code false}
   */
  public static final boolean DEFAULT_DECOMPRESSION_SUPPORTED = false;

  /**
   * Default WebSocket Masked bit is true as depicted by RFC = {@code false}
   */
  public static final boolean DEFAULT_ACCEPT_UNMASKED_FRAMES = false;

  /**
   * Default initial buffer size for HttpObjectDecoder = 128 bytes
   */
  public static final int DEFAULT_DECODER_INITIAL_BUFFER_SIZE = 128;

  /**
   * Default support for WebSockets per-frame deflate compression extension = {@code true}
   */
  public static final boolean DEFAULT_PER_FRAME_WEBSOCKET_COMPRESSION_SUPPORTED = true;

  /**
   * Default support for WebSockets per-message deflate compression extension = {@code true}
   */
  public static final boolean DEFAULT_PER_MESSAGE_WEBSOCKET_COMPRESSION_SUPPORTED = true;

  /**
   * Default WebSocket deflate compression level = 6
   */
  public static final int DEFAULT_WEBSOCKET_COMPRESSION_LEVEL = 6;

  /**
   * Default allowance of the {@code server_no_context_takeover} WebSocket parameter deflate compression extension = {@code false}
   */
  public static final boolean DEFAULT_WEBSOCKET_ALLOW_SERVER_NO_CONTEXT = false;

  /**
   * Default allowance of the {@code client_no_context_takeover} WebSocket parameter deflate compression extension = {@code false}
   */
  public static final boolean DEFAULT_WEBSOCKET_PREFERRED_CLIENT_NO_CONTEXT = false;

  /**
   * Default WebSocket closing timeout = 10 second)
   */
  public static final int DEFAULT_WEBSOCKET_CLOSING_TIMEOUT = 10;

  /**
   * Default tracing control = {@link TracingPolicy#ALWAYS}
   */
  public static final TracingPolicy DEFAULT_TRACING_POLICY = TracingPolicy.ALWAYS;

  /**
   * The default value of the server metrics = "":
   */
  public static final String DEFAULT_METRICS_NAME = "";

  /**
   * Whether write-handlers for server websockets should be registered by default = false.
   */
  public static final boolean DEFAULT_REGISTER_WEBSOCKET_WRITE_HANDLERS = false;

  /**
   * HTTP/2 RST floods DDOS protection, max number of RST frame per time window allowed = 200.
   */
  public static final int DEFAULT_HTTP2_RST_FLOOD_MAX_RST_FRAME_PER_WINDOW = 200;

  /**
   * HTTP/2 RST floods DDOS protection, time window duration = 30.
   */
  public static final int DEFAULT_HTTP2_RST_FLOOD_WINDOW_DURATION = 30;

  /**
   * HTTP/2 RST floods DDOS protection, time window duration unit = {@link TimeUnit#SECONDS}.
   */
  public static final TimeUnit DEFAULT_HTTP2_RST_FLOOD_WINDOW_DURATION_TIME_UNIT = TimeUnit.SECONDS;

  /**
   * Strict thread mode = false.
   */
  public static final boolean DEFAULT_STRICT_THREAD_MODE_STRICT = false;

  /**
   * Use HTTP/2 multiplex implementation = {@code false}
   */
  public static final boolean DEFAULT_HTTP_2_MULTIPLEX_IMPLEMENTATION = false;

  private int maxFormAttributeSize;
  private int maxFormFields;
  private int maxFormBufferedBytes;
  private Http1ServerConfig http1Config;
  private Http2ServerConfig http2Config;
  private WebSocketServerConfig webSocketConfig;
  private int compressionLevel;
  private HttpCompressionConfig compression;
  private boolean handle100ContinueAutomatically;
  private String metricsName;
  private TracingPolicy tracingPolicy;
  private boolean registerWebSocketWriteHandlers;
  private TimeUnit http2RstFloodWindowDurationTimeUnit;
  private boolean strictThreadMode;

  /**
   * Default constructor
   */
  public HttpServerOptions() {
    super();
    init();
    setPort(DEFAULT_PORT); // We override the default for port
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public HttpServerOptions(HttpServerOptions other) {
    super(other);
    this.maxFormAttributeSize = other.getMaxFormAttributeSize();
    this.maxFormFields = other.getMaxFormFields();
    this.maxFormBufferedBytes = other.getMaxFormBufferedBytes();
    this.compressionLevel = other.getCompressionLevel();
    this.compression = other.compression != null ? new HttpCompressionConfig(other.compression) : new HttpCompressionConfig();
    this.handle100ContinueAutomatically = other.handle100ContinueAutomatically;
    this.http1Config = new Http1ServerConfig(other.http1Config);
    this.http2Config = new Http2ServerConfig(other.http2Config);
    this.webSocketConfig = new WebSocketServerConfig(other.webSocketConfig);
    this.metricsName = other.metricsName;
    this.tracingPolicy = other.tracingPolicy;
    this.registerWebSocketWriteHandlers = other.registerWebSocketWriteHandlers;
    this.http2RstFloodWindowDurationTimeUnit = other.http2RstFloodWindowDurationTimeUnit;
    this.strictThreadMode = other.strictThreadMode;
  }

  /**
   * Create an options from JSON
   *
   * @param json  the JSON
   */
  public HttpServerOptions(JsonObject json) {
    super(json);
    init();
    setPort(json.getInteger("port", DEFAULT_PORT));
    HttpServerOptionsConverter.fromJson(json, this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    HttpServerOptionsConverter.toJson(this, json);
    return json;
  }

  private void init() {
    maxFormAttributeSize = DEFAULT_MAX_FORM_ATTRIBUTE_SIZE;
    maxFormFields = DEFAULT_MAX_FORM_FIELDS;
    maxFormBufferedBytes = DEFAULT_MAX_FORM_BUFFERED_SIZE;
    strictThreadMode = DEFAULT_STRICT_THREAD_MODE_STRICT;
    compression = new HttpCompressionConfig();
    handle100ContinueAutomatically = DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY;
    http1Config = new Http1ServerConfig();
    http2Config = new Http2ServerConfig();
    webSocketConfig = new WebSocketServerConfig();
    metricsName = DEFAULT_METRICS_NAME;
    tracingPolicy = DEFAULT_TRACING_POLICY;
    registerWebSocketWriteHandlers = DEFAULT_REGISTER_WEBSOCKET_WRITE_HANDLERS;
    http2RstFloodWindowDurationTimeUnit = DEFAULT_HTTP2_RST_FLOOD_WINDOW_DURATION_TIME_UNIT;
  }

  /**
   * Copy these options.
   *
   * @return a copy of this
   */
  public HttpServerOptions copy() {
    return new HttpServerOptions(this);
  }

  /**
   * @return the configuration specific to the HTTP/1.x protocol.
   */
  public Http1ServerConfig getHttp1Config() {
    return http1Config;
  }

  /**
   * @return the configuration specific to the HTTP/2 protocol.
   */
  public Http2ServerConfig getHttp2Config() {
    return http2Config;
  }

  /**
   * @return the configuration specific to the WebSocket protocol.
   */
  public WebSocketServerConfig getWebSocketConfig() {
    return webSocketConfig;
  }

  @Override
  protected ServerSSLOptions createSSLOptions() {
    return super.createSSLOptions().setApplicationLayerProtocols(HttpUtils.fromHttpAlpnVersions(DEFAULT_ALPN_VERSIONS));
  }

  @Override
  public HttpServerOptions setSslOptions(ServerSSLOptions sslOptions) {
    return (HttpServerOptions) super.setSslOptions(sslOptions);
  }

  @Override
  public HttpServerOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public HttpServerOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public HttpServerOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public HttpServerOptions setReusePort(boolean reusePort) {
    super.setReusePort(reusePort);
    return this;
  }

  @Override
  public HttpServerOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public HttpServerOptions setTcpNoDelay(boolean tcpNoDelay) {
    super.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public HttpServerOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    super.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  @Override
  public HttpServerOptions setSoLinger(int soLinger) {
    super.setSoLinger(soLinger);
    return this;
  }

  @Override
  public HttpServerOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public HttpServerOptions setReadIdleTimeout(int idleTimeout) {
    super.setReadIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public HttpServerOptions setWriteIdleTimeout(int idleTimeout) {
    super.setWriteIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public HttpServerOptions setIdleTimeoutUnit(TimeUnit idleTimeoutUnit) {
    super.setIdleTimeoutUnit(idleTimeoutUnit);
    return this;
  }

  @Override
  public HttpServerOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public boolean isUseAlpn() {
    return true;
  }

  /**
   * Alpn supported is automatically managed by the HTTP server depending on the client supported protocols.
   *
   * @param useAlpn ignored
   * @return this object
   */
  @Deprecated(forRemoval = true)
  @Override
  public HttpServerOptions setUseAlpn(boolean useAlpn) {
    return this;
  }

  @Override
  public HttpServerOptions setKeyCertOptions(KeyCertOptions options) {
    super.setKeyCertOptions(options);
    return this;
  }

  @Override
  public HttpServerOptions setTrustOptions(TrustOptions options) {
    super.setTrustOptions(options);
    return this;
  }

  @Override
  public HttpServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public HttpServerOptions removeEnabledCipherSuite(String suite) {
    super.removeEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public HttpServerOptions addEnabledSecureTransportProtocol(final String protocol) {
    super.addEnabledSecureTransportProtocol(protocol);
    return this;
  }

  @Override
  public HttpServerOptions removeEnabledSecureTransportProtocol(String protocol) {
    return (HttpServerOptions) super.removeEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public HttpServerOptions setTcpFastOpen(boolean tcpFastOpen) {
    return (HttpServerOptions) super.setTcpFastOpen(tcpFastOpen);
  }

  @Override
  public HttpServerOptions setTcpCork(boolean tcpCork) {
    return (HttpServerOptions) super.setTcpCork(tcpCork);
  }

  @Override
  public HttpServerOptions setTcpQuickAck(boolean tcpQuickAck) {
    return (HttpServerOptions) super.setTcpQuickAck(tcpQuickAck);
  }

  @Override
  public HttpServerOptions setTcpUserTimeout(int tcpUserTimeout) {
    return (HttpServerOptions) super.setTcpUserTimeout(tcpUserTimeout);
  }

  @Override
  public HttpServerOptions addCrlPath(String crlPath) throws NullPointerException {
    return (HttpServerOptions) super.addCrlPath(crlPath);
  }

  @Override
  public HttpServerOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    return (HttpServerOptions) super.addCrlValue(crlValue);
  }

  @Override
  public HttpServerOptions setAcceptBacklog(int acceptBacklog) {
    super.setAcceptBacklog(acceptBacklog);
    return this;
  }

  public HttpServerOptions setPort(int port) {
    super.setPort(port);
    return this;
  }

  @Override
  public HttpServerOptions setHost(String host) {
    super.setHost(host);
    return this;
  }

  @Override
  public HttpServerOptions setClientAuth(ClientAuth clientAuth) {
    super.setClientAuth(clientAuth);
    return this;
  }

  @Override
  public HttpServerOptions setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    super.setSslEngineOptions(sslEngineOptions);
    return this;
  }

  @Override
  public HttpServerOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    return (HttpServerOptions) super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
  }

  @Override
  public HttpServerOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    return (HttpServerOptions) super.setSslHandshakeTimeout(sslHandshakeTimeout);
  }

  @Override
  public HttpServerOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    return (HttpServerOptions) super.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
  }

  /**
   * @return {@code true} if the server supports gzip/deflate compression
   */
  public boolean isCompressionSupported() {
    return compression.isCompressionEnabled();
  }

  /**
   * Set whether the server should support gzip/deflate compression
   * (serving compressed responses to clients advertising support for them with Accept-Encoding header)
   *
   * @param compressionSupported {@code true} to enable compression support
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setCompressionSupported(boolean compressionSupported) {
    compression.setCompressionEnabled(compressionSupported);
    return this;
  }

  /**
   * @return the compression configuration
   */
  public HttpCompressionConfig getCompression() {
    return compression;
  }

  /**
   * Configure the server compression, this overwrites any previously configuration.
   *
   * @param compression the new configuration
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setCompression(HttpCompressionConfig compression) {
    this.compression = compression == null ? new HttpCompressionConfig() : compression;
    return this;
  }

  /**
   *
   * @return the server gzip/deflate 'compression level' to be used in responses when client and server support is turned on
   */
  public int getCompressionLevel() {
    return this.compressionLevel;
  }

  /**
   * This method allows to set the compression level to be used in http1.x/2 response bodies
   * when compression support is turned on (@see setCompressionSupported) and the client advertises
   * to support {@code deflate/gzip} compression in the {@code Accept-Encoding} header
   *
   * default value is : 6 (Netty legacy)
   *
   * The compression level determines how much the data is compressed on a scale from 1 to 9,
   * where '9' is trying to achieve the maximum compression ratio while '1' instead is giving
   * priority to speed instead of compression ratio using some algorithm optimizations and skipping
   * pedantic loops that usually gives just little improvements
   *
   * While one can think that best value is always the maximum compression ratio,
   * there's a trade-off to consider: the most compressed level requires the most
   * computational work to compress/decompress data, e.g. more dictionary lookups and loops.
   *
   * E.g. you have it set fairly high on a high-volume website, you may experience performance degradation
   * and latency on resource serving due to CPU overload, and, however - as the computational work is required also client side
   * while decompressing - setting an higher compression level can result in an overall higher page load time
   * especially nowadays when many clients are handled mobile devices with a low CPU profile.
   *
   * see also: http://www.gzip.org/algorithm.txt
   *
   * @param compressionLevel integer 1-9, 1 means use fastest algorithm, 9 slower algorithm but better compression ratio
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setCompressionLevel(int compressionLevel) {
    this.compressionLevel = compressionLevel;
    return this;
  }

  /**
   * @return the compression content size threshold
   */
  public int getCompressionContentSizeThreshold() {
    return compression.getContentSizeThreshold();
  }

  /**
   * Set the compression content size threshold if compression is enabled. This is only applicable for HTTP/1.x response bodies.
   * If the response content size in bytes is greater than this threshold, then the response is compressed. Otherwise, it is not compressed.
   *
   * @param compressionContentSizeThreshold integer greater than or equal to 0.
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setCompressionContentSizeThreshold(int compressionContentSizeThreshold) {
    compression.setContentSizeThreshold(compressionContentSizeThreshold);
    return this;
  }

  /**
   * @return the list of compressor to use
   */
  public List<CompressionOptions> getCompressors() {
    return compression.getCompressors();
  }

  /**
   * Add a compressor.
   *
   * @see #setCompressors(List)
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions addCompressor(CompressionOptions compressor) {
    compression.addCompressor(compressor);
    return this;
  }

  /**
   * Set the list of compressor to use instead of using the default gzip/deflate {@link #setCompressionLevel(int)} configuration.
   *
   * <p> This is only active when {@link #setCompressionSupported(boolean)} is {@code true}.
   *
   * @param compressors the list of compressors
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setCompressors(List<CompressionOptions> compressors) {
    compression.setCompressors(compressors);
    return this;
  }

  public boolean isAcceptUnmaskedFrames() {
    return webSocketConfig.isUseUnmaskedFrames();
  }

  /**
   * Set {@code true} when the server accepts unmasked frame.
   * As default Server doesn't accept unmasked frame, you can bypass this behaviour (RFC 6455) setting {@code true}.
   * It's set to {@code false} as default.
   *
   * @param acceptUnmaskedFrames {@code true} if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setAcceptUnmaskedFrames(boolean acceptUnmaskedFrames) {
    webSocketConfig.setUseUnmaskedFrames(acceptUnmaskedFrames);
    return this;
  }

  /**
   * @return the maximum WebSocket frame size
   */
  public int getMaxWebSocketFrameSize() {
    return webSocketConfig.getMaxFrameSize();
  }

  /**
   * Set the maximum WebSocket frames size
   *
   * @param maxWebSocketFrameSize  the maximum frame size in bytes.
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxWebSocketFrameSize(int maxWebSocketFrameSize) {
    webSocketConfig.setMaxFrameSize(maxWebSocketFrameSize);
    return this;
  }

  /**
   * @return  the maximum WebSocket message size
   */
  public int getMaxWebSocketMessageSize() {
    return webSocketConfig.getMaxMessageSize();
  }

  /**
   * Set the maximum WebSocket message size
   *
   * @param maxWebSocketMessageSize  the maximum message size in bytes.
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxWebSocketMessageSize(int maxWebSocketMessageSize) {
    webSocketConfig.setMaxMessageSize(maxWebSocketMessageSize);
    return this;
  }

  /**
   * Add a WebSocket sub-protocol to the list supported by the server.
   *
   * @param subProtocol the sub-protocol to add
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions addWebSocketSubProtocol(String subProtocol) {
    webSocketConfig.addWebSocketSubProtocol(subProtocol);
    return this;
  }
  /**
   * Set the WebSocket list of sub-protocol supported by the server.
   *
   * @param subProtocols  comma separated list of sub-protocols
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebSocketSubProtocols(List<String> subProtocols) {
    webSocketConfig.setSubProtocols(subProtocols);
    return this;
  }

  /**
   * @return Get the WebSocket list of sub-protocol
   */
  public List<String> getWebSocketSubProtocols() {
    return webSocketConfig.getSubProtocols();
  }

  /**
   * @return whether 100 Continue should be handled automatically
   */
  public boolean isHandle100ContinueAutomatically() {
    return handle100ContinueAutomatically;
  }

  /**
   * Set whether 100 Continue should be handled automatically
   * @param handle100ContinueAutomatically {@code true} if it should be handled automatically
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setHandle100ContinueAutomatically(boolean handle100ContinueAutomatically) {
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    return this;
  }

  /**
   * Set the maximum HTTP chunk size that {@link HttpServerRequest#handler(Handler)} will receive
   *
   * @param maxChunkSize the maximum chunk size
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxChunkSize(int maxChunkSize) {
    http1Config.setMaxChunkSize(maxChunkSize);
    return this;
  }

  /**
   * @return the maximum HTTP chunk size that {@link HttpServerRequest#handler(Handler)} will receive
   */
  public int getMaxChunkSize() {
    return http1Config.getMaxChunkSize();
  }


  /**
   * @return the maximum length of the initial line for HTTP/1.x (e.g. {@code "GET / HTTP/1.0"})
   */
  public int getMaxInitialLineLength() {
    return http1Config.getMaxInitialLineLength();
  }

  /**
   * Set the maximum length of the initial line for HTTP/1.x (e.g. {@code "GET / HTTP/1.0"})
   *
   * @param maxInitialLineLength the new maximum initial length
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxInitialLineLength(int maxInitialLineLength) {
    http1Config.setMaxInitialLineLength(maxInitialLineLength);
    return this;
  }

  /**
   * @return Returns the maximum length of all headers for HTTP/1.x
   */
  public int getMaxHeaderSize() {
    return http1Config.getMaxHeaderSize();
  }

  /**
   * Set the maximum length of all headers for HTTP/1.x .
   *
   * @param maxHeaderSize the new maximum length
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxHeaderSize(int maxHeaderSize) {
    http1Config.setMaxHeaderSize(maxHeaderSize);
    return this;
  }

  /**
   * @return Returns the maximum size of a form attribute
   */
  public int getMaxFormAttributeSize() {
    return maxFormAttributeSize;
  }

  /**
   * Set the maximum size of a form attribute. Set to {@code -1} to allow unlimited length
   *
   * @param maxSize the new maximum size
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxFormAttributeSize(int maxSize) {
    this.maxFormAttributeSize = maxSize;
    return this;
  }

  /**
   * @return Returns the maximum number of form fields
   */
  public int getMaxFormFields() {
    return maxFormFields;
  }

  /**
   * Set the maximum number of fields of a form. Set to {@code -1} to allow unlimited number of attributes
   *
   * @param maxFormFields the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxFormFields(int maxFormFields) {
    this.maxFormFields = maxFormFields;
    return this;
  }

  /**
   * @return Returns the maximum number of bytes a server can buffer when decoding a form
   */
  public int getMaxFormBufferedBytes() {
    return maxFormBufferedBytes;
  }

  /**
   * Set the maximum number of bytes a server can buffer when decoding a form. Set to {@code -1} to allow unlimited length
   *
   * @param maxFormBufferedBytes the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxFormBufferedBytes(int maxFormBufferedBytes) {
    this.maxFormBufferedBytes = maxFormBufferedBytes;
    return this;
  }

  /**
   * @return the initial HTTP/2 connection settings
   */
  public Http2Settings getInitialSettings() {
    return http2Config.getInitialSettings();
  }

  /**
   * Set the HTTP/2 connection settings immediatly sent by the server when a client connects.
   *
   * @param settings the settings value
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setInitialSettings(Http2Settings settings) {
    http2Config.setInitialSettings(settings);
    return this;
  }

  /**
   * @return {@code null}
   */
  public List<HttpVersion> getAlpnVersions() {
    return null;
  }

  /**
   * Does nothing, the list of supported alpn versions is managed by the HTTP server.
   *
   * @param alpnVersions ignored
   * @return a reference to this, so the API can be used fluently
   * @deprecated this should not be used anymore
   */
  @Deprecated(forRemoval = true)
  public HttpServerOptions setAlpnVersions(List<HttpVersion> alpnVersions) {
    return this;
  }

  /**
   * @return whether the server accepts HTTP/2 over clear text connections
   */
  public boolean isHttp2ClearTextEnabled() {
    return http2Config.isClearTextEnabled();
  }

  /**
   * Set whether HTTP/2 over clear text is enabled or disabled, default is enabled.
   *
   * @param http2ClearTextEnabled whether to accept HTTP/2 over clear text
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setHttp2ClearTextEnabled(boolean http2ClearTextEnabled) {
    http2Config.setClearTextEnabled(http2ClearTextEnabled);
    return this;
  }

  /**
   * @return the default HTTP/2 connection window size
   */
  public int getHttp2ConnectionWindowSize() {
    return http2Config.getConnectionWindowSize();
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
  public HttpServerOptions setHttp2ConnectionWindowSize(int http2ConnectionWindowSize) {
    http2Config.setConnectionWindowSize(http2ConnectionWindowSize);
    return this;
  }

  @Override
  public HttpServerOptions setLogActivity(boolean logEnabled) {
    return (HttpServerOptions) super.setLogActivity(logEnabled);
  }

  @Override
  public HttpServerOptions setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    return (HttpServerOptions) super.setActivityLogDataFormat(activityLogDataFormat);
  }

  @Override
  public HttpServerOptions setSni(boolean sni) {
    return (HttpServerOptions) super.setSni(sni);
  }

  @Override
  public HttpServerOptions setUseProxyProtocol(boolean useProxyProtocol) {
    return (HttpServerOptions) super.setUseProxyProtocol(useProxyProtocol);
  }

  @Override
  public HttpServerOptions setProxyProtocolTimeout(long proxyProtocolTimeout) {
    return (HttpServerOptions) super.setProxyProtocolTimeout(proxyProtocolTimeout);
  }

  @Override
  public HttpServerOptions setProxyProtocolTimeoutUnit(TimeUnit proxyProtocolTimeoutUnit) {
    return (HttpServerOptions) super.setProxyProtocolTimeoutUnit(proxyProtocolTimeoutUnit);
  }

  /**
   * @return {@code true} if the server supports decompression
   */
  public boolean isDecompressionSupported() {
    return compression.isDecompressionEnabled();
  }

  /**
   * Set whether the server supports decompression
   *
   * @param decompressionSupported {@code true} if decompression supported
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setDecompressionSupported(boolean decompressionSupported) {
    compression.setDecompressionEnabled(decompressionSupported);
    return this;
  }

  /**
   * @return the initial buffer size for the HTTP decoder
   */
  public int getDecoderInitialBufferSize() { return http1Config.getDecoderInitialBufferSize(); }

  /**
   * Set the initial buffer size for the HTTP decoder
   * @param decoderInitialBufferSize the initial size
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setDecoderInitialBufferSize(int decoderInitialBufferSize) {
    http1Config.setDecoderInitialBufferSize(decoderInitialBufferSize);
    return this;
  }

  /**
   * Enable or disable support for the WebSocket per-frame deflate compression extension.
   *
   * @param supported {@code true} when the per-frame deflate compression extension is supported
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setPerFrameWebSocketCompressionSupported(boolean supported) {
    webSocketConfig.setUsePerFrameCompression(supported);
    return this;
  }

  /**
   * Get whether WebSocket the per-frame deflate compression extension is supported.
   *
   * @return {@code true} if the http server will accept the per-frame deflate compression extension
   */
  public boolean getPerFrameWebSocketCompressionSupported() {
    return this.webSocketConfig.getUsePerFrameCompression();
  }

  /**
   * Enable or disable support for WebSocket per-message deflate compression extension.
   *
   * @param supported {@code true} when the per-message WebSocket compression extension is supported
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setPerMessageWebSocketCompressionSupported(boolean supported) {
    webSocketConfig.setUsePerMessageCompression(supported);
    return this;
  }

  /**
   * Get whether WebSocket per-message deflate compression extension is supported.
   *
   * @return {@code true} if the http server will accept the per-message deflate compression extension
   */
  public boolean getPerMessageWebSocketCompressionSupported() {
    return webSocketConfig.getUsePerMessageCompression();
  }

  /**
   * Set the WebSocket compression level.
   *
   * @param compressionLevel the compression level
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebSocketCompressionLevel(int compressionLevel) {
    webSocketConfig.setCompressionLevel(compressionLevel);
    return this;
  }

  /**
   * @return the current WebSocket deflate compression level
   */
  public int getWebSocketCompressionLevel() {
    return webSocketConfig.getCompressionLevel();
  }

  /**
   * Set whether the WebSocket server will accept the {@code server_no_context_takeover} parameter of the per-message
   * deflate compression extension offered by the client.
   *
   * @param accept {@code true} to accept the {@literal server_no_context_takeover} parameter when the client offers it
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebSocketAllowServerNoContext(boolean accept) {
    webSocketConfig.setUseServerNoContext(accept);
    return this;
  }

  /**
   * @return {@code true} when the WebSocket server will accept the {@code server_no_context_takeover} parameter for the per-message
   * deflate compression extension offered by the client
   */
  public boolean getWebSocketAllowServerNoContext() {
    return webSocketConfig.getUseServerNoContext();
  }

  /**
   * Set whether the WebSocket server will accept the {@code client_no_context_takeover} parameter of the per-message
   * deflate compression extension offered by the client.
   *
   * @param accept {@code true} to accept the {@code client_no_context_takeover} parameter when the client offers it
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebSocketPreferredClientNoContext(boolean accept) {
    webSocketConfig.setUseClientNoContext(accept);
    return this;
  }

  /**
   * @return {@code true} when the WebSocket server will accept the {@code client_no_context_takeover} parameter for the per-message
   * deflate compression extension offered by the client
   */
  public boolean getWebSocketPreferredClientNoContext() {
    return webSocketConfig.getUseClientNoContext();
  }

  /**
   * @return the amount of time (in seconds) a client WebSocket will wait until it closes TCP connection after receiving a close frame
   */
  public int getWebSocketClosingTimeout() {
    return (int)webSocketConfig.getClosingTimeout().toSeconds();
  }

  /**
   * Set the amount of time a server WebSocket will wait until it closes the TCP connection
   * after sending a close frame.
   *
   * <p> When a server closes a WebSocket, it should wait the client close frame to close the TCP connection.
   * This timeout will close the TCP connection on the server when it expires. When the TCP
   * connection is closed receiving the close frame, the {@link WebSocket#exceptionHandler} instead
   * of the {@link WebSocket#endHandler} will be called.
   *
   * <p> Set to {@code 0L} closes the TCP connection immediately after sending the close frame.
   *
   * <p> Set to a negative value to disable it.
   *
   * @param webSocketClosingTimeout the timeout is seconds
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebSocketClosingTimeout(int webSocketClosingTimeout) {
    webSocketConfig.setClosingTimeout(Duration.ofSeconds(webSocketClosingTimeout));
    return this;
  }

  @Override
  public HttpServerOptions setTrafficShapingOptions(TrafficShapingOptions trafficShapingOptions) {
    return (HttpServerOptions) super.setTrafficShapingOptions(trafficShapingOptions);
  }

  /**
   * @return the metrics name identifying the reported metrics.
   */
  public String getMetricsName() {
    return metricsName;
  }

  /**
   * Set the metrics name identifying the reported metrics, useful for naming the server metrics.
   *
   * @param metricsName the metrics name
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMetricsName(String metricsName) {
    this.metricsName = metricsName;
    return this;
  }

  /**
   * @return the tracing policy
   */
  public TracingPolicy getTracingPolicy() {
    return tracingPolicy;
  }

  /**
   * Set the tracing policy for the server behavior when Vert.x has tracing enabled.
   *
   * @param tracingPolicy the tracing policy
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setTracingPolicy(TracingPolicy tracingPolicy) {
    this.tracingPolicy = tracingPolicy;
    return this;
  }

  /**
   * @return {@code false}, does not apply to HTTP servers
   */
  @Override
  public boolean isRegisterWriteHandler() {
    return false;
  }

  /**
   * Has no effect on HTTP server options.
   */
  @Override
  public HttpServerOptions setRegisterWriteHandler(boolean registerWriteHandler) {
    return this;
  }

  /**
   * @return {@code true} if write-handlers for server websockets should be registered on the {@link io.vertx.core.eventbus.EventBus}, otherwise {@code false}
   */
  public boolean isRegisterWebSocketWriteHandlers() {
    return registerWebSocketWriteHandlers;
  }

  /**
   * Whether write-handlers for server websockets should be registered on the {@link io.vertx.core.eventbus.EventBus}.
   * <p>
   * Defaults to {@code false}.
   *
   * @param registerWebSocketWriteHandlers true to register write-handlers
   * @return a reference to this, so the API can be used fluently
   * @see WebSocketBase#textHandlerID()
   * @see WebSocketBase#binaryHandlerID()
   */
  public HttpServerOptions setRegisterWebSocketWriteHandlers(boolean registerWebSocketWriteHandlers) {
    this.registerWebSocketWriteHandlers = registerWebSocketWriteHandlers;
    return this;
  }

  /**
   * @return the max number of RST frame allowed per time window
   */
  public int getHttp2RstFloodMaxRstFramePerWindow() {
    return http2Config.getRstFloodMaxRstFramePerWindow();
  }

  /**
   * Set the max number of RST frame allowed per time window, this is used to prevent HTTP/2 RST frame flood DDOS
   * attacks. The default value is {@link #DEFAULT_HTTP2_RST_FLOOD_MAX_RST_FRAME_PER_WINDOW}, setting zero or a negative value, disables flood protection.
   *
   * @param http2RstFloodMaxRstFramePerWindow the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setHttp2RstFloodMaxRstFramePerWindow(int http2RstFloodMaxRstFramePerWindow) {
    http2Config.setRstFloodMaxRstFramePerWindow(http2RstFloodMaxRstFramePerWindow);
    return this;
  }

  /**
   * @return the duration of the time window when checking the max number of RST frames.
   */
  public int getHttp2RstFloodWindowDuration() {
    return (int)http2Config.getRstFloodWindowDuration().get(http2RstFloodWindowDurationTimeUnit.toChronoUnit());
  }

  /**
   * Set the duration of the time window when checking the max number of RST frames, this is used to prevent HTTP/2 RST frame flood DDOS
   * attacks. The default value is {@link #DEFAULT_HTTP2_RST_FLOOD_WINDOW_DURATION}, setting zero or a negative value, disables flood protection.
   *
   * @param http2RstFloodWindowDuration the new duration
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setHttp2RstFloodWindowDuration(int http2RstFloodWindowDuration) {
    http2Config.setRstFloodWindowDuration(Duration.of(http2RstFloodWindowDuration, http2RstFloodWindowDurationTimeUnit.toChronoUnit()));
    return this;
  }

  /**
   * @return the time unit of the duration of the time window when checking the max number of RST frames.
   */
  public TimeUnit getHttp2RstFloodWindowDurationTimeUnit() {
    return http2RstFloodWindowDurationTimeUnit;
  }

  /**
   * Set the time unit of the duration of the time window when checking the max number of RST frames, this is used to
   * prevent HTTP/2 RST frame flood DDOS attacks. The default value is {@link #DEFAULT_HTTP2_RST_FLOOD_WINDOW_DURATION_TIME_UNIT},
   * setting zero or a negative value, disables the flood protection.
   *
   * @param http2RstFloodWindowDurationTimeUnit the new duration
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setHttp2RstFloodWindowDurationTimeUnit(TimeUnit http2RstFloodWindowDurationTimeUnit) {
    if (http2RstFloodWindowDurationTimeUnit == null) {
      throw new NullPointerException();
    }
    Duration tmp = http2Config.getRstFloodWindowDuration();
    this.http2RstFloodWindowDurationTimeUnit = http2RstFloodWindowDurationTimeUnit;
    http2Config.setRstFloodWindowDuration(tmp);
    return this;
  }

  /**
   * @return whether the strict thread mode is used
   */
  @GenIgnore
  public boolean isFileRegionEnabled() {
    return !isCompressionSupported();
  }

  /**
   * @return whether to use the strict thread mode.
   */
  @Unstable("Experimental")
  public boolean getStrictThreadMode() {
    return strictThreadMode;
  }

  /**
   * Indicates the server that the HTTP request/response interactions will happen exclusively on the expected thread when
   * the threading model is event-loop.
   *
   * @param strictThreadMode whether to use the strict thread mode
   * @return a reference to this, so the API can be used fluently
   */
  @Unstable("Experimental")
  public HttpServerOptions setStrictThreadMode(boolean strictThreadMode) {
    this.strictThreadMode = strictThreadMode;
    return this;
  }

  /**
   * @return whether to use the HTTP/2 implementation based on multiplexed channel
   */
  public boolean getHttp2MultiplexImplementation() {
    return http2Config.getMultiplexImplementation();
  }

  /**
   * Set which HTTP/2 implementation to use
   *
   * @param http2MultiplexImplementation whether to use the HTTP/2 multiplex implementation
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setHttp2MultiplexImplementation(boolean http2MultiplexImplementation) {
    http2Config.setMultiplexImplementation(http2MultiplexImplementation);
    return this;
  }
}
