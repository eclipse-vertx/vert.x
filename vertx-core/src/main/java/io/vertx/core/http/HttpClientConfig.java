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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.net.*;
import io.vertx.core.tracing.TracingPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration of a {@link HttpClient}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class HttpClientConfig {

  private static List<HttpVersion> toSupportedVersion(HttpVersion version) {
    switch (version) {
      case HTTP_1_0:
        return List.of(HttpVersion.HTTP_1_0);
      case HTTP_1_1:
        return List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
      case HTTP_2:
        return List.of(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1);
      default:
        throw new IllegalArgumentException();
    }
  }

  private static QuicClientConfig defaultQuicConfig() {
    QuicClientConfig config = new QuicClientConfig();
    config.getTransportConfig().setInitialMaxData(DEFAULT_QUIC_INITIAL_MAX_DATA);
    config.getTransportConfig().setInitialMaxStreamDataBidiLocal(DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_BIDI_LOCAL);
    config.getTransportConfig().setInitialMaxStreamDataBidiRemote(DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE);
    config.getTransportConfig().setInitialMaxStreamDataUni(DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_UNI);
    config.getTransportConfig().setInitialMaxStreamsBidi(DEFAULT_QUIC_INITIAL_MAX_STREAM_BIDI);
    config.getTransportConfig().setInitialMaxStreamsUni(DEFAULT_QUIC_INITIAL_MAX_STREAM_UNI);
    return config;
  }

  public static final List<HttpVersion> DEFAULT_SUPPORTED_VERSIONS = List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
  public static final long DEFAULT_QUIC_INITIAL_MAX_DATA = 10_485_760L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_BIDI_LOCAL = 1_048_576L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE = 0L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_DATA_UNI = 1_048_576L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_BIDI = 0L;
  public static final long DEFAULT_QUIC_INITIAL_MAX_STREAM_UNI = 1L;

  private TcpClientConfig tcpConfig;
  private QuicClientConfig quicConfig;
  private boolean ssl;

  private List<HttpVersion> versions;
  private Http1ClientConfig http1Config;
  private Http2ClientConfig http2Config;
  private Http3ClientConfig http3Config;
  private boolean verifyHost;
  private boolean decompressionEnabled;
  private String defaultHost;
  private int defaultPort;
  private int maxRedirects;
  private boolean forceSni;
  private String metricsName;
  private TracingPolicy tracingPolicy;
  private boolean shared;
  private String name;
  private boolean followAlternativeServices;

  public HttpClientConfig() {
    this.tcpConfig = new TcpClientConfig();
    this.quicConfig = defaultQuicConfig();
    this.ssl = TCPSSLOptions.DEFAULT_SSL;
    this.versions = new ArrayList<>(DEFAULT_SUPPORTED_VERSIONS);
    this.http1Config = null;
    this.http2Config = null;
    this.http3Config = null;
    this.verifyHost = HttpClientOptions.DEFAULT_VERIFY_HOST;
    this.decompressionEnabled = HttpClientOptions.DEFAULT_DECOMPRESSION_SUPPORTED;
    this.defaultHost = HttpClientOptions.DEFAULT_DEFAULT_HOST;
    this.defaultPort = HttpClientOptions.DEFAULT_DEFAULT_PORT;
    this.maxRedirects = HttpClientOptions.DEFAULT_MAX_REDIRECTS;
    this.forceSni = HttpClientOptions.DEFAULT_FORCE_SNI;
    this.metricsName = null;
    this.tracingPolicy = HttpClientOptions.DEFAULT_TRACING_POLICY;
    this.shared = HttpClientOptions.DEFAULT_SHARED;
    this.name = HttpClientOptions.DEFAULT_NAME;
    this.followAlternativeServices = HttpClientOptions.DEFAULT_FOLLOW_ALTERNATIVE_SERVICES;
  }

  public HttpClientConfig(HttpClientConfig other) {
    this.tcpConfig = other.tcpConfig != null ? new TcpClientConfig(other.tcpConfig) : null;
    this.quicConfig = other.quicConfig != null ? new QuicClientConfig(other.quicConfig) : null;
    this.ssl = other.ssl;
    this.versions = new ArrayList<>(other.versions != null ? other.versions : DEFAULT_SUPPORTED_VERSIONS);
    this.http1Config = other.http1Config != null ? new Http1ClientConfig(other.http1Config) : null;
    this.http2Config = other.http2Config != null ? new Http2ClientConfig(other.http2Config) : null;
    this.http3Config = other.http3Config != null ? new Http3ClientConfig(other.http3Config) : null;
    this.verifyHost = other.isVerifyHost();
    this.decompressionEnabled = other.decompressionEnabled;
    this.defaultHost = other.defaultHost;
    this.defaultPort = other.defaultPort;
    this.maxRedirects = other.maxRedirects;
    this.forceSni = other.forceSni;
    this.metricsName = other.metricsName;
    this.tracingPolicy = other.tracingPolicy;
    this.shared = other.shared;
    this.name = other.name;
    this.followAlternativeServices = other.followAlternativeServices;
  }

  public HttpClientConfig(HttpClientOptions other) {
    this.tcpConfig = new TcpClientConfig(other);
    this.quicConfig = defaultQuicConfig();
    this.ssl = other.isSsl();
    this.versions = new ArrayList<>(toSupportedVersion(other.getProtocolVersion()));
    this.http1Config = other.getHttp1Config();
    this.http2Config = other.getHttp2Config();
    this.http3Config = new Http3ClientConfig();
    this.verifyHost = other.isVerifyHost();
    this.decompressionEnabled = other.isDecompressionSupported();
    this.defaultHost = other.getDefaultHost();
    this.defaultPort = other.getDefaultPort();
    this.maxRedirects = other.getMaxRedirects();
    this.forceSni = other.isForceSni();
    this.metricsName = other.getMetricsName();
    this.tracingPolicy = other.getTracingPolicy();
    this.shared = other.isShared();
    this.name = other.getName();
    this.followAlternativeServices = other.getFollowAlternativeServices();
  }

  /**
   * @return the client TCP transport config
   */
  public TcpClientConfig getTcpConfig() {
    return tcpConfig;
  }

  /**
   * @return the client QUIC transport config
   */
  public QuicClientConfig getQuicConfig() {
    return quicConfig;
  }

  /**
   * Set the connect timeout
   *
   * @param connectTimeout  connect timeout, in ms
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setConnectTimeout(Duration connectTimeout) {
    tcpConfig.setConnectTimeout(connectTimeout);
    quicConfig.setConnectTimeout(connectTimeout);
    return this;
  }

  /**
   * <p>Set the keep alive timeout for HTTP connections. This value determines how long a connection remains
   * unused in the pool before being evicted and closed. A timeout of zero or {@code null} means there is no timeout.</p>
   * <p>This configures each HTTP version with the same value, you can override this and configure each
   * version separately.</p>
   *
   * @param keepAliveTimeout the timeout, in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setKeepAliveTimeout(Duration keepAliveTimeout) {
    if (keepAliveTimeout != null && (keepAliveTimeout.isNegative())) {
      throw new IllegalArgumentException("HTTP keepAliveTimeout must be >= 0");
    }
    http1Config.setKeepAliveTimeout(keepAliveTimeout);
    http2Config.setKeepAliveTimeout(keepAliveTimeout);
    http3Config.setKeepAliveTimeout(keepAliveTimeout);
    return this;
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
  public HttpClientConfig setIdleTimeout(Duration idleTimeout) {
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
  public HttpClientConfig setReadIdleTimeout(Duration idleTimeout) {
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
  public HttpClientConfig setWriteIdleTimeout(Duration idleTimeout) {
    tcpConfig.setWriteIdleTimeout(idleTimeout);
    quicConfig.setWriteIdleTimeout(idleTimeout);
    return this;
  }

  /**
   *
   * @return is SSL/TLS enabled?
   */
  public boolean isSsl() {
    return ssl;
  }

  /**
   * Set whether SSL/TLS is enabled
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  /**
   * @return the ordered list of the versions supported by the client
   */
  public List<HttpVersion> getVersions() {
    return versions;
  }

  /**
   * <p>Set the list of {@link HttpVersion} supported by the client, the first element of the list is considered
   * as the default protocol to choose when none is specified at the request level.</p>
   *
   * @param versions the ordered list of supported versions
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setVersions(List<HttpVersion> versions) {
    this.versions = Objects.requireNonNull(versions);
    return this;
  }

  /**
   * Like {@link #setVersions(List)} using an array.
   *
   * @param versions the ordered list of supported versions
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public HttpClientConfig setVersions(HttpVersion... versions) {
    this.versions = List.of(versions);
    return this;
  }

  /**
   * @return the configuration specific to the HTTP/1.x protocol.
   */
  public Http1ClientConfig getHttp1Config() {
    return http1Config;
  }

  /**
   * Set the HTTP/1.x configuration to use
   *
   * @param config the config
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setHttp1Config(Http1ClientConfig config) {
    this.http1Config = config;
    return this;
  }

  /**
   * @return the configuration specific to the HTTP/2 protocol.
   */
  public Http2ClientConfig getHttp2Config() {
    return http2Config;
  }

  /**
   * Set the HTTP/2 configuration to use
   *
   * @param config the config
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setHttp2Config(Http2ClientConfig config) {
    this.http2Config = config;
    return this;
  }

  /**
   * @return the configuration specific to the HTTP/3 protocol.
   */
  public Http3ClientConfig getHttp3Config() {
    return http3Config;
  }

  /**
   * Set the HTTP/2 configuration to use
   *
   * @param config the config
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setHttp3Config(Http3ClientConfig config) {
    this.http3Config = config;
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
  public HttpClientConfig setVerifyHost(boolean verifyHost) {
    this.verifyHost = verifyHost;
    return this;
  }

  /**
   * @return {@code true} if the client should send requests with an {@code accepting-encoding} header set to a compression algorithm, {@code false} otherwise
   */
  public boolean isDecompressionEnabled() {
    return decompressionEnabled;
  }

  /**
   * Whether the client should send requests with an {@code accepting-encoding} header set to a compression algorithm.
   *
   * @param decompressionEnabled {@code true} if the client should send a request with an {@code accepting-encoding} header set to a compression algorithm, {@code false} otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setDecompressionEnabled(boolean decompressionEnabled) {
    this.decompressionEnabled = decompressionEnabled;
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
  public HttpClientConfig setDefaultHost(String defaultHost) {
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
  public HttpClientConfig setDefaultPort(int defaultPort) {
    this.defaultPort = defaultPort;
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
  public HttpClientConfig setMaxRedirects(int maxRedirects) {
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
  public HttpClientConfig setForceSni(boolean forceSni) {
    this.forceSni = forceSni;
    return this;
  }

  /**
   * @return the metrics name identifying the reported metrics
   */
  public String getMetricsName() {
    return metricsName;
  }

  /**
   * Set the metrics name identifying the reported metrics, useful for grouping metrics
   * with the same name.
   *
   * @param metricsName the metrics name
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setMetricsName(String metricsName) {
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
   * Set the tracing policy for the client behavior when Vert.x has tracing enabled.
   *
   * @param tracingPolicy the tracing policy
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setTracingPolicy(TracingPolicy tracingPolicy) {
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
   * name is set, the {@link HttpClientOptions#DEFAULT_NAME} is used.
   *
   * @param shared {@code true} to use a shared client
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setShared(boolean shared) {
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
  public HttpClientConfig setName(String name) {
    Objects.requireNonNull(name, "Client name cannot be null");
    this.name = name;
    return this;
  }

  /**
   * @return whether the client follows alternative services advertisements
   */
  @Unstable
  public boolean getFollowAlternativeServices() {
    return followAlternativeServices;
  }

  /**
   * <p>Configure whether the client follows alternative services advertisements, the default
   * setting does not.</p>
   *
   * <p>Setting this to true, instructs the client to use most appropriate alternative services advertised by
   * HTTP servers.</p>
   *
   * <p>The client only follows alternative services it can trust for a given origin, in practice this means
   * this only the {@code https} scheme is supported and alternatives handshake uses the alternative origin.</p>
   *
   * @param followAlternativeServices the config value
   */
  public HttpClientConfig setFollowAlternativeServices(boolean followAlternativeServices) {
    this.followAlternativeServices = followAlternativeServices;
    return this;
  }
}
