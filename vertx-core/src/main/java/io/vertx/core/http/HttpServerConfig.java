/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
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
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.net.*;
import io.vertx.core.tracing.TracingPolicy;

import java.time.Duration;
import java.util.*;

/**
 * Configuration of a {@link HttpServer}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class HttpServerConfig {

  /**
   * Default port the server will listen on = 443
   */
  public static final int DEFAULT_HTTP3_PORT = 443;

  public static final Set<HttpVersion> DEFAULT_VERSIONS = Collections.unmodifiableSet(EnumSet.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2));

  public static final long DEFAULT_QUIC_INITIAL_MAX_DATA = 10_485_760L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_BIDI_LOCAL = 0L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE = 1_048_576L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_UNI = 1_048_576L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_BIDI = 256L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_UNI = 1L;

  private static QuicServerConfig defaultQuicConfig() {
    QuicServerConfig config = new QuicServerConfig();
    config.getTransportConfig().setInitialMaxData(DEFAULT_QUIC_INITIAL_MAX_DATA);
    config.getTransportConfig().setInitialMaxStreamDataBidiLocal(DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_BIDI_LOCAL);
    config.getTransportConfig().setInitialMaxStreamDataBidiRemote(DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE);
    config.getTransportConfig().setInitialMaxStreamDataUni(DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_UNI);
    config.getTransportConfig().setInitialMaxStreamsBidi(DEFAULT_QUIC_INITIAL_MAX_STREAM_BIDI);
    config.getTransportConfig().setInitialMaxStreamsUni(DEFAULT_QUIC_INITIAL_MAX_STREAM_UNI);
    config.setPort(DEFAULT_HTTP3_PORT);
    return config;
  }

  private static TcpServerConfig defaultTcpServerConfig() {
    TcpServerConfig config = new TcpServerConfig();
    config.setPort(HttpServerOptions.DEFAULT_PORT);
    return config;
  }

  private Set<HttpVersion> versions;
  private int maxFormAttributeSize;
  private int maxFormFields;
  private int maxFormBufferedBytes;
  private boolean handle100ContinueAutomatically;
  private boolean strictThreadMode;
  private String metricsName;
  private TracingPolicy tracingPolicy;
  private Http1ServerConfig http1Config;
  private Http2ServerConfig http2Config;
  private Http3ServerConfig http3Config;
  private WebSocketServerConfig webSocketConfig;
  private HttpCompressionConfig compression;
  private final TcpServerConfig tcpConfig;
  private final QuicServerConfig quicConfig;

  /**
   * Copies the {@link HttpServerOptions}.
   * @param options the options to copy
   */
  public HttpServerConfig(HttpServerOptions options) {

    HttpCompressionConfig compression;
    if (options.isCompressionSupported() || options.isDecompressionSupported()) {
      List<CompressionOptions> compressors = options.getCompression().getCompressors();
      if (compressors == null) {
        int compressionLevel = options.getCompressionLevel();
        compressors = Arrays.asList(StandardCompressionOptions.gzip(compressionLevel, 15, 8), StandardCompressionOptions.deflate(compressionLevel, 15, 8));
      }
      compression = new HttpCompressionConfig();
      compression.setCompressionEnabled(options.isCompressionSupported());
      compression.setDecompressionEnabled(options.isDecompressionSupported());
      compression.setContentSizeThreshold(options.getCompressionContentSizeThreshold());
      compression.setCompressors(compressors);
    } else {
      compression = null;
    }

    this.versions = EnumSet.copyOf(DEFAULT_VERSIONS);
    this.maxFormAttributeSize = options.getMaxFormAttributeSize();
    this.maxFormFields = options.getMaxFormFields();
    this.maxFormBufferedBytes = options.getMaxFormBufferedBytes();
    this.handle100ContinueAutomatically = options.isHandle100ContinueAutomatically();
    this.strictThreadMode = options.getStrictThreadMode();
    this.metricsName = options.getMetricsName();
    this.tracingPolicy = options.getTracingPolicy();
    this.http1Config = new Http1ServerConfig(options.getHttp1Config());
    this.http2Config = new Http2ServerConfig(options.getHttp2Config());
    this.webSocketConfig = new WebSocketServerConfig(options.getWebSocketConfig());
    this.compression = compression;
    this.tcpConfig = new TcpServerConfig(options);
    this.quicConfig = defaultQuicConfig();
  }

  /**
   * Create a default configuration of a server accepting HTTP/1.1 and HTTP/2 protocols.
   */
  public HttpServerConfig() {
    this.versions = EnumSet.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
    this.maxFormAttributeSize = HttpServerOptions.DEFAULT_MAX_FORM_ATTRIBUTE_SIZE;
    this.maxFormFields = HttpServerOptions.DEFAULT_MAX_FORM_FIELDS;
    this.maxFormBufferedBytes = HttpServerOptions.DEFAULT_MAX_FORM_BUFFERED_SIZE;
    this.handle100ContinueAutomatically = HttpServerOptions.DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY;
    this.strictThreadMode = HttpServerOptions.DEFAULT_STRICT_THREAD_MODE_STRICT;
    this.metricsName = null;
    this.tracingPolicy = HttpServerOptions.DEFAULT_TRACING_POLICY;
    this.http1Config = null;
    this.http2Config = null;
    this.http3Config = null;
    this.webSocketConfig = null;
    this.compression = null;
    this.tcpConfig = defaultTcpServerConfig();
    this.quicConfig = defaultQuicConfig();
  }

  /**
   * Copy constructor
   *
   * @param other the config to be copied
   */
  public HttpServerConfig(HttpServerConfig other) {
    this.versions = EnumSet.copyOf(other.versions);
    this.maxFormAttributeSize = other.maxFormAttributeSize;
    this.maxFormFields = other.maxFormFields;
    this.maxFormBufferedBytes = other.maxFormBufferedBytes;
    this.handle100ContinueAutomatically = other.handle100ContinueAutomatically;
    this.strictThreadMode = other.strictThreadMode;
    this.metricsName = other.metricsName;
    this.tracingPolicy = other.tracingPolicy;
    this.http1Config = other.http1Config != null ? new Http1ServerConfig(other.http1Config) : new Http1ServerConfig();
    this.http2Config = other.http2Config != null ? new Http2ServerConfig(other.http2Config) : new Http2ServerConfig();
    this.http3Config = other.http3Config != null ? new Http3ServerConfig(other.http3Config) : new Http3ServerConfig();
    this.webSocketConfig = other.webSocketConfig != null ? new WebSocketServerConfig(other.webSocketConfig) : new WebSocketServerConfig();
    this.compression = other.compression != null ? new HttpCompressionConfig(other.compression) : new HttpCompressionConfig();
    this.tcpConfig = other.tcpConfig != null ? new TcpServerConfig(other.tcpConfig) : defaultTcpServerConfig();
    this.quicConfig = other.quicConfig != null ? new QuicServerConfig(other.quicConfig) : defaultQuicConfig();
  }

  /**
   * Set the idle timeout, zero or {@code null} means don't time out.
   * This determines if a connection will timeout and be closed if no data is received nor sent within the timeout.
   * <p>This configures both TCP and QUIC nested configurations, you can configure each of them separately
   * if you need to.</p>
   *
   * @param idleTimeout  the timeout
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setIdleTimeout(Duration idleTimeout) {
    tcpConfig.setIdleTimeout(idleTimeout);
    quicConfig.setIdleTimeout(idleTimeout);
    return this;
  }

  /**
   * <p>Set the read idle timeout, zero or {@code null} means or null means don't time out. This determines if a
   * connection will timeout and be closed if no data is received within the timeout.</p>
   * <p>This configures both TCP and QUIC nested configurations, you can configure each of them separately
   * if you need to.</p>
   *
   * @param idleTimeout  the read timeout
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setReadIdleTimeout(Duration idleTimeout) {
    tcpConfig.setReadIdleTimeout(idleTimeout);
    quicConfig.setReadIdleTimeout(idleTimeout);
    return this;
  }

  /**
   * <p>Set the write idle timeout, zero or {@code null} means don't time out. This determines if a
   * connection will timeout and be closed if no data is sent within the timeout.</p>
   * <p>This configures both TCP and QUIC nested configurations, you can configure each of them separately
   * if you need to.</p>
   *
   * @param idleTimeout  the write timeout
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setWriteIdleTimeout(Duration idleTimeout) {
    tcpConfig.setWriteIdleTimeout(idleTimeout);
    quicConfig.setWriteIdleTimeout(idleTimeout);
    return this;
  }

  /**
   * <p>Configure the per stream networking logging: Netty's stream pipeline is configured for logging on Netty's logger.</p>
   * <p>This configures both TCP and QUIC nested configurations, you can configure each of them separately
   * if you need to.</p>
   *
   * @param config the stream network logging config, {@code null} means disabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setNetworkLogging(NetworkLogging config) {
    tcpConfig.setNetworkLogging(config);
    quicConfig.setNetworkLogging(config);
    return this;
  }

  /**
   * @return the HTTP versions
   */
  public Set<HttpVersion> getVersions() {
    return versions;
  }

  /**
   * Set the HTTP versions.
   *
   * @param versions the versions
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setVersions(Set<HttpVersion> versions) {
    this.versions = Objects.requireNonNull(versions);
    return this;
  }

  /**
   * Set the HTTP versions.
   *
   * @param versions the versions
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public HttpServerConfig setVersions(HttpVersion... versions) {
    EnumSet<HttpVersion> s = EnumSet.noneOf(HttpVersion.class);
    Collections.addAll(s, versions);
    this.versions = s;
    return this;
  }

  /**
   *
   * @return is SSL/TLS enabled?
   */
  public boolean isSsl() {
    return tcpConfig.isSsl();
  }

  /**
   * Set whether SSL/TLS is enabled
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setSsl(boolean ssl) {
    tcpConfig.setSsl(ssl);
    return this;
  }

  /**
   * Set the port used to bind the server at, affecting both TCP and QUIC transports.
   *
   * @param port  the port
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setPort(int port) {
    tcpConfig.setPort(port);
    quicConfig.setPort(port);
    return this;
  }

  /**
   * Set the host used to bind the server at, affecting both TCP and QUIC transports.
   *
   * @param host  the host
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setHost(String host) {
    tcpConfig.setHost(host);
    quicConfig.setHost(host);
    return this;
  }

  /**
   * @return the port to bind the TCP server at
   */
  public int getTcpPort() {
    return tcpConfig.getPort();
  }

  /**
   * Set the port used to bind the TCP server.
   *
   * @param port  the port
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setTcpPort(int port) {
    tcpConfig.setPort(port);
    return this;
  }

  /**
   * @return the host
   */
  public String getTcpHost() {
    return tcpConfig.getHost();
  }

  /**
   * Set the host
   * @param host  the host
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setTcpHost(String host) {
    tcpConfig.setHost(host);
    return this;
  }

  /**
   * @return the port to bind the QUIC server at
   */
  public int getQuicPort() {
    return quicConfig.getPort();
  }

  /**
   * Set the port used to bind the QUIC server.
   *
   * @param port  the port
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setQuicPort(int port) {
    quicConfig.setPort(port);
    return this;
  }

  /**
   * @return the host
   */
  public String getQuicHost() {
    return quicConfig.getHost();
  }

  /**
   * Set the host
   * @param host  the host
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setQuicHost(String host) {
    quicConfig.setHost(host);
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
  public HttpServerConfig setMaxFormAttributeSize(int maxSize) {
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
  public HttpServerConfig setMaxFormFields(int maxFormFields) {
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
  public HttpServerConfig setMaxFormBufferedBytes(int maxFormBufferedBytes) {
    this.maxFormBufferedBytes = maxFormBufferedBytes;
    return this;
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
  public HttpServerConfig setHandle100ContinueAutomatically(boolean handle100ContinueAutomatically) {
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    return this;
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
  public HttpServerConfig setStrictThreadMode(boolean strictThreadMode) {
    this.strictThreadMode = strictThreadMode;
    return this;
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
  public HttpServerConfig setMetricsName(String metricsName) {
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
  public HttpServerConfig setTracingPolicy(TracingPolicy tracingPolicy) {
    this.tracingPolicy = tracingPolicy;
    return this;
  }

  /**
   * @return the configuration specific to the HTTP/1.x protocol.
   */
  public Http1ServerConfig getHttp1Config() {
    return http1Config;
  }

  /**
   * Set the HTTP/1.x configuration to use
   *
   * @param config the config
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setHttp1Config(Http1ServerConfig config) {
    this.http1Config = config;
    return this;
  }

  /**
   * @return the configuration specific to the HTTP/2 protocol.
   */
  public Http2ServerConfig getHttp2Config() {
    return http2Config;
  }

  /**
   * Set the HTTP/2 configuration to use
   *
   * @param config the config
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setHttp2Config(Http2ServerConfig config) {
    this.http2Config = config;
    return this;
  }

  /**
   * @return the configuration specific to the HTTP/1.x protocol.
   */
  public Http3ServerConfig getHttp3Config() {
    return http3Config;
  }

  /**
   * Set the HTTP/3 configuration to use
   *
   * @param config the config
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setHttp3Config(Http3ServerConfig config) {
    this.http3Config = config;
    return this;
  }

  /**
   * @return the configuration specific to the WebSocket protocol.
   */
  public WebSocketServerConfig getWebSocketConfig() {
    return webSocketConfig;
  }

  /**
   * Set the WebSocket protocol specific configuration.
   *
   * @param webSocketConfig the WebSocket config
   */
  public HttpServerConfig setWebSocketConfig(WebSocketServerConfig webSocketConfig) {
    this.webSocketConfig = webSocketConfig;
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
  public HttpServerConfig setCompression(HttpCompressionConfig compression) {
    this.compression = compression == null ? new HttpCompressionConfig() : compression;
    return this;
  }

  /**
   * @return the TCP transport config
   */
  public TcpServerConfig getTcpConfig() {
    return tcpConfig;
  }

  /**
   * @return the QUIC transport config
   */
  public QuicServerConfig getQuicConfig() {
    return quicConfig;
  }
}
