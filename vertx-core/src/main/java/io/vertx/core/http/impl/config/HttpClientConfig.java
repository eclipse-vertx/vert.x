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
package io.vertx.core.http.impl.config;

import io.netty.handler.logging.ByteBufFormat;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.http.Http3ClientOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.*;
import io.vertx.core.tracing.TracingPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration of a {@link NetClient}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
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

  public static final List<HttpVersion> DEFAULT_SUPPORTED_VERSIONS = List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);

  private TcpOptions tcpOptions;
  private QuicOptions quicOptions;
  private ClientSSLOptions sslOptions;
  private SSLEngineOptions sslEngineOptions;
  private Duration connectTimeout;
  private String metricsName;
  private ProxyOptions proxyOptions;
  private List<String> nonProxyHosts;
  private Duration idleTimeout;
  private Duration readIdleTimeout;
  private Duration writeIdleTimeout;
  private boolean logActivity;
  private ByteBufFormat activityLogDataFormat;
  private boolean ssl;

  private List<HttpVersion> supportedVersions;
  private Http1ClientConfig http1Config;
  private Http2ClientConfig http2Config;
  private Http3ClientConfig http3Config;
  private boolean verifyHost;
  private boolean decompressionSupported;
  private String defaultHost;
  private int defaultPort;
  private int maxRedirects;
  private boolean forceSni;
  private TracingPolicy tracingPolicy;
  private boolean shared;
  private String name;
  private boolean followAlternativeServices;

  public HttpClientConfig() {
    this.tcpOptions = new TcpOptions();
    this.quicOptions = new QuicOptions();
    this.sslOptions = new ClientSSLOptions().setUseAlpn(true);
    this.sslEngineOptions = TCPSSLOptions.DEFAULT_SSL_ENGINE;
    this.connectTimeout = Duration.ofMillis(ClientOptionsBase.DEFAULT_CONNECT_TIMEOUT);
    this.metricsName = ClientOptionsBase.DEFAULT_METRICS_NAME;
    this.proxyOptions = null;
    this.nonProxyHosts = null;
    this.idleTimeout = null;
    this.readIdleTimeout = null;
    this.writeIdleTimeout = null;
    this.logActivity = NetworkOptions.DEFAULT_LOG_ENABLED;
    this.activityLogDataFormat = NetworkOptions.DEFAULT_LOG_ACTIVITY_FORMAT;
    this.ssl = TCPSSLOptions.DEFAULT_SSL;
    this.supportedVersions = new ArrayList<>(DEFAULT_SUPPORTED_VERSIONS);
    this.http1Config = new Http1ClientConfig();
    this.http2Config = new Http2ClientConfig();
    this.http3Config = new Http3ClientConfig();
    this.verifyHost = HttpClientOptions.DEFAULT_VERIFY_HOST;
    this.decompressionSupported = HttpClientOptions.DEFAULT_DECOMPRESSION_SUPPORTED;
    this.defaultHost = HttpClientOptions.DEFAULT_DEFAULT_HOST;
    this.defaultPort = HttpClientOptions.DEFAULT_DEFAULT_PORT;
    this.maxRedirects = HttpClientOptions.DEFAULT_MAX_REDIRECTS;
    this.forceSni = HttpClientOptions.DEFAULT_FORCE_SNI;
    this.tracingPolicy = HttpClientOptions.DEFAULT_TRACING_POLICY;
    this.shared = HttpClientOptions.DEFAULT_SHARED;
    this.name = HttpClientOptions.DEFAULT_NAME;
    this.followAlternativeServices = HttpClientOptions.DEFAULT_FOLLOW_ALTERNATIVE_SERVICES;
  }

  public HttpClientConfig(HttpClientConfig other) {
    this.tcpOptions = other.tcpOptions != null ? new TcpOptions(other.tcpOptions) : null;
    this.quicOptions = other.quicOptions != null ? new QuicOptions(other.quicOptions) : null;
    this.sslOptions = other.sslOptions != null ? new ClientSSLOptions(other.sslOptions) : null;
    this.sslEngineOptions = other.sslEngineOptions != null ? other.sslEngineOptions.copy() : null;
    this.connectTimeout = other.connectTimeout;
    this.metricsName = other.metricsName;
    this.proxyOptions = other.proxyOptions != null ? new ProxyOptions(other.proxyOptions) : null;
    this.nonProxyHosts = other.nonProxyHosts != null ? new ArrayList<>(other.nonProxyHosts) : null;
    this.idleTimeout = other.idleTimeout;
    this.readIdleTimeout = other.readIdleTimeout;
    this.writeIdleTimeout = other.writeIdleTimeout;
    this.logActivity = other.logActivity;
    this.activityLogDataFormat = other.activityLogDataFormat;
    this.ssl = other.ssl;
    this.supportedVersions = new ArrayList<>(other.supportedVersions != null ? other.supportedVersions : DEFAULT_SUPPORTED_VERSIONS);
    this.http1Config = other.http1Config != null ? new Http1ClientConfig(other.http1Config) : null;
    this.http2Config = other.http2Config != null ? new Http2ClientConfig(other.http2Config) : null;
    this.http3Config = other.http3Config != null ? new Http3ClientConfig(other.http3Config) : null;
    this.verifyHost = other.isVerifyHost();
    this.decompressionSupported = other.decompressionSupported;
    this.defaultHost = other.defaultHost;
    this.defaultPort = other.defaultPort;
    this.maxRedirects = other.maxRedirects;
    this.forceSni = other.forceSni;
    this.tracingPolicy = other.tracingPolicy;
    this.shared = other.shared;
    this.name = other.name;
    this.followAlternativeServices = other.followAlternativeServices;
  }

  public HttpClientConfig(HttpClientOptions other) {
    this.tcpOptions = new TcpOptions(other.getTransportOptions());
    this.quicOptions = new QuicOptions();
    this.sslOptions = other.getSslOptions() != null ? new ClientSSLOptions(other.getSslOptions()) : new ClientSSLOptions();
    this.sslEngineOptions = other.getSslEngineOptions() != null ? other.getSslEngineOptions().copy() : null;
    this.connectTimeout = Duration.ofMillis(other.getConnectTimeout());
    this.metricsName = other.getMetricsName();
    this.proxyOptions = other.getProxyOptions() != null ? new ProxyOptions(other.getProxyOptions()) : null;
    this.nonProxyHosts = other.getNonProxyHosts() != null ? new ArrayList<>(other.getNonProxyHosts()) : null;
    this.idleTimeout = other.getIdleTimeout() > 0 ? Duration.of(other.getIdleTimeout(), other.getIdleTimeoutUnit().toChronoUnit()) : null;
    this.readIdleTimeout = other.getReadIdleTimeout() > 0 ? Duration.of(other.getReadIdleTimeout(), other.getIdleTimeoutUnit().toChronoUnit()) : null;
    this.writeIdleTimeout = other.getWriteIdleTimeout() > 0 ? Duration.of(other.getWriteIdleTimeout(), other.getIdleTimeoutUnit().toChronoUnit()) : null;
    this.logActivity = other.getLogActivity();
    this.activityLogDataFormat = other.getActivityLogDataFormat();
    this.ssl = other.isSsl();
    this.supportedVersions = new ArrayList<>(toSupportedVersion(other.getProtocolVersion()));
    this.http1Config = other.getHttp1Config();
    this.http2Config = other.getHttp2Config();
    this.http3Config = new Http3ClientConfig();
    this.verifyHost = other.isVerifyHost();
    this.decompressionSupported = other.isDecompressionSupported();
    this.defaultHost = other.getDefaultHost();
    this.defaultPort = other.getDefaultPort();
    this.maxRedirects = other.getMaxRedirects();
    this.forceSni = other.isForceSni();
    this.tracingPolicy = other.getTracingPolicy();
    this.shared = other.isShared();
    this.name = other.getName();
    this.followAlternativeServices = other.getFollowAlternativeServices();
  }

  public HttpClientConfig(Http3ClientOptions other) {

    Http3ClientConfig config = new Http3ClientConfig();
    config.setKeepAliveTimeout(other.getKeepAliveTimeout());
    config.setInitialSettings(other.getInitialSettings());

    this.quicOptions = new QuicOptions(other.getTransportOptions());
    this.sslOptions = other.getSslOptions() != null ? new ClientSSLOptions(other.getSslOptions()) : null;
    this.connectTimeout = other.getConnectTimeout();
    this.metricsName = other.getMetricsName();
    this.idleTimeout = other.getStreamIdleTimeout();
    this.readIdleTimeout = other.getStreamReadIdleTimeout();
    this.writeIdleTimeout = other.getStreamWriteIdleTimeout();
    this.verifyHost = other.isVerifyHost();
    this.defaultHost = other.getDefaultHost();
    this.defaultPort = other.getDefaultPort();
    this.maxRedirects = other.getMaxRedirects();
    this.supportedVersions = new ArrayList<>(List.of(HttpVersion.HTTP_3));
    this.http1Config = null;
    this.http2Config = null;
    this.http3Config = config;
  }

  /**
   * @return the client TCP transport options
   */
  public TcpOptions getTcpOptions() {
    return tcpOptions;
  }

  /**
   * Set the client TCP transport options.
   *
   * @param tcpOptions the transport options
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setTcpOptions(TcpOptions tcpOptions) {
    this.tcpOptions = tcpOptions;
    return this;
  }

  /**
   * @return the client QUIC transport options
   */
  public QuicOptions getQuicOptions() {
    return quicOptions;
  }

  /**
   * Set the client QUIC transport options.
   *
   * @param quicOptions the transport options
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setQuicOptions(QuicOptions quicOptions) {
    this.quicOptions = quicOptions;
    return this;
  }

  /**
   * @return the client SSL options.
   */
  public ClientSSLOptions getSslOptions() {
    return sslOptions;
  }

  /**
   * Set the client SSL options.
   *
   * @param sslOptions the options
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setSslOptions(ClientSSLOptions sslOptions) {
    this.sslOptions = sslOptions;
    return this;
  }

  /**
   * @return the SSL engine implementation to use
   */
  public SSLEngineOptions getSslEngineOptions() {
    return sslEngineOptions;
  }

  /**
   * Set to use SSL engine implementation to use.
   *
   * @param sslEngineOptions the ssl engine to use
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    this.sslEngineOptions = sslEngineOptions;
    return this;
  }

  /**
   * @return the value of connect timeout
   */
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Set the connect timeout
   *
   * @param connectTimeout  connect timeout, in ms
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setConnectTimeout(Duration connectTimeout) {
    if (connectTimeout.isNegative() || connectTimeout.isZero()) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
    return this;
  }

  /**
   * @return the metrics name identifying the reported metrics.
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
   * Get proxy options for connections
   *
   * @return proxy options
   */
  public ProxyOptions getProxyOptions() {
    return proxyOptions;
  }

  /**
   * Set proxy options for connections via CONNECT proxy (e.g. Squid) or a SOCKS proxy.
   *
   * @param proxyOptions proxy options object
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setProxyOptions(ProxyOptions proxyOptions) {
    this.proxyOptions = proxyOptions;
    return this;
  }

  /**
   * @return the list of non proxies hosts
   */
  public List<String> getNonProxyHosts() {
    return nonProxyHosts;
  }

  /**
   * Set a list of remote hosts that are not proxied when the client is configured to use a proxy. This
   * list serves the same purpose than the JVM {@code nonProxyHosts} configuration.
   *
   * <p> Entries can use the <i>*</i> wildcard character for pattern matching, e.g <i>*.example.com</i> matches
   * <i>www.example.com</i>.
   *
   * @param nonProxyHosts the list of non proxies hosts
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setNonProxyHosts(List<String> nonProxyHosts) {
    this.nonProxyHosts = nonProxyHosts;
    return this;
  }

  /**
   * Add a {@code host} to the {@link #getNonProxyHosts()} list.
   *
   * @param host the added host
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig addNonProxyHost(String host) {
    if (nonProxyHosts == null) {
      nonProxyHosts = new ArrayList<>();
    }
    nonProxyHosts.add(host);
    return this;
  }

  /**
   * @return the idle timeout
   */
  public Duration getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * Set the idle timeout, default time unit is seconds. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is received nor sent within the timeout.
   *
   * @param idleTimeout  the timeout
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("idleTimeout must be >= 0");
    }
    this.idleTimeout = idleTimeout;
    return this;
  }

  /**
   * Set the read idle timeout. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is received within the timeout.
   *
   * @param idleTimeout  the read timeout
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setReadIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("readIdleTimeout must be >= 0");
    }
    this.readIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the read idle timeout
   */
  public Duration getReadIdleTimeout() {
    return readIdleTimeout;
  }

  /**
   * Set the write idle timeout, default time unit is seconds. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is sent within the timeout.
   *
   * @param idleTimeout  the write timeout
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setWriteIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("writeIdleTimeout must be >= 0");
    }
    this.writeIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the write idle timeout.
   */
  public Duration getWriteIdleTimeout() {
    return writeIdleTimeout;
  }

  /**
   * @return true when network activity logging is enabled
   */
  public boolean getLogActivity() {
    return logActivity;
  }

  /**
   * Set to true to enabled network activity logging: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param logActivity true for logging the network activity
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setLogActivity(boolean logActivity) {
    this.logActivity = logActivity;
    return this;
  }

  /**
   * @return Netty's logging handler's data format.
   */
  public ByteBufFormat getActivityLogDataFormat() {
    return activityLogDataFormat;
  }

  /**
   * Set the value of Netty's logging handler's data format: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param activityLogDataFormat the format to use
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    this.activityLogDataFormat = activityLogDataFormat;
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
  public List<HttpVersion> getSupportedVersions() {
    return supportedVersions;
  }

  /**
   * <p>Set the list of {@link HttpVersion} supported by the client, the first element of the list is considered
   * as the default protocol to choose when none is specified at the request level.</p>
   *
   * @param supportedVersions the ordered list of supported versions
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setSupportedVersions(List<HttpVersion> supportedVersions) {
    this.supportedVersions = Objects.requireNonNull(supportedVersions);
    return this;
  }

  /**
   * @return the default protocol version
   */
  public HttpVersion getDefaultProtocolVersion() {
    return supportedVersions.isEmpty() ? null : supportedVersions.get(0);
  }

  /**
   * Set the default protocol version.
   *
   * @param protocolVersion the protocol version
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setDefaultProtocolVersion(HttpVersion protocolVersion) {
    setSupportedVersions(toSupportedVersion(protocolVersion));
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
  public boolean isDecompressionSupported() {
    return decompressionSupported;
  }

  /**
   * Whether the client should send requests with an {@code accepting-encoding} header set to a compression algorithm.
   *
   * @param decompressionSupported {@code true} if the client should send a request with an {@code accepting-encoding} header set to a compression algorithm, {@code false} otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public HttpClientConfig setDecompressionSupported(boolean decompressionSupported) {
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
