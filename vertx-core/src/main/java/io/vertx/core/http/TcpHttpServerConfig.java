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
import io.netty.handler.logging.ByteBufFormat;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.TcpOptions;
import io.vertx.core.net.impl.tcp.NetServerConfig;
import io.vertx.core.tracing.TracingPolicy;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration of a {@link HttpServer}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TcpHttpServerConfig extends HttpServerConfig {

  private NetServerConfig endpointConfig;
  private Http1ServerConfig http1Config;
  private Http2ServerConfig http2Config;
  private WebSocketServerConfig webSocketConfig;
  private HttpCompressionConfig compression; // Currently here as we do not support compression for QUIC

  public TcpHttpServerConfig(HttpServerOptions options) {
    super(options);

    List<CompressionOptions> compressors = options.getCompression().getCompressors();
    if (compressors == null) {
      int compressionLevel = options.getCompressionLevel();
      compressors = Arrays.asList(StandardCompressionOptions.gzip(compressionLevel, 15, 8), StandardCompressionOptions.deflate(compressionLevel, 15, 8));
    }
    HttpCompressionConfig compression = new HttpCompressionConfig();
    compression.setCompressionEnabled(options.isCompressionSupported());
    compression.setDecompressionEnabled(options.isDecompressionSupported());
    compression.setContentSizeThreshold(options.getCompressionContentSizeThreshold());
    compression.setCompressors(compressors);

    this.endpointConfig = new NetServerConfig(options);
    this.http1Config = new Http1ServerConfig(options.getHttp1Config());
    this.http2Config = new Http2ServerConfig(options.getHttp2Config());
    this.webSocketConfig = new WebSocketServerConfig(options.getWebSocketConfig());
    this.compression = compression;
  }

  @Override
  public TcpHttpServerConfig setHandle100ContinueAutomatically(boolean handle100ContinueAutomatically) {
    return (TcpHttpServerConfig)super.setHandle100ContinueAutomatically(handle100ContinueAutomatically);
  }

  @Override
  public TcpHttpServerConfig setStrictThreadMode(boolean strictThreadMode) {
    return (TcpHttpServerConfig)super.setStrictThreadMode(strictThreadMode);
  }

  @Override
  public TcpHttpServerConfig setTracingPolicy(TracingPolicy tracingPolicy) {
    return (TcpHttpServerConfig)super.setTracingPolicy(tracingPolicy);
  }

  @Override
  public ServerSSLOptions getSslOptions() {
    return endpointConfig.getSslOptions();
  }

  @Override
  public TcpHttpServerConfig setSslOptions(ServerSSLOptions sslOptions) {
    endpointConfig.setSslOptions(sslOptions);
    return this;
  }

  @Override
  public SSLEngineOptions getSslEngineOptions() {
    return endpointConfig.getSslEngineOptions();
  }

  @Override
  public TcpHttpServerConfig setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    endpointConfig.setSslEngineOptions(sslEngineOptions);
    return this;
  }

  @Override
  public int getPort() {
    return endpointConfig.getPort();
  }

  @Override
  public HttpServerConfig setPort(int port) {
    endpointConfig.setPort(port);
    return this;
  }

  @Override
  public String getHost() {
    return endpointConfig.getHost();
  }

  @Override
  public HttpServerConfig setHost(String host) {
    endpointConfig.setHost(host);
    return this;
  }

  @Override
  public Duration getIdleTimeout() {
    return endpointConfig.getIdleTimeout();
  }

  @Override
  public TcpHttpServerConfig setIdleTimeout(Duration idleTimeout) {
    endpointConfig.setIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public Duration getReadIdleTimeout() {
    return endpointConfig.getReadIdleTimeout();
  }

  @Override
  public TcpHttpServerConfig setReadIdleTimeout(Duration idleTimeout) {
    endpointConfig.setReadIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public Duration getWriteIdleTimeout() {
    return endpointConfig.getWriteIdleTimeout();
  }

  @Override
  public TcpHttpServerConfig setWriteIdleTimeout(Duration idleTimeout) {
    endpointConfig.setWriteIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public boolean isSsl() {
    return endpointConfig.isSsl();
  }

  @Override
  public TcpHttpServerConfig setSsl(boolean ssl) {
    endpointConfig.setSsl(ssl);
    return this;
  }

  public NetServerConfig getEndpointConfig() {
    return endpointConfig;
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
  public void setWebSocketConfig(WebSocketServerConfig webSocketConfig) {
    this.webSocketConfig = webSocketConfig;
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
  public TcpHttpServerConfig setCompression(HttpCompressionConfig compression) {
    this.compression = compression == null ? new HttpCompressionConfig() : compression;
    return this;
  }
}
