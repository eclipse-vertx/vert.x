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

import io.netty.handler.codec.http3.Http3;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.net.QuicServerOptions;
import io.vertx.core.net.ServerSSLOptions;

import java.time.Duration;
import java.util.Arrays;

/**
 * Configuration of a Quic {@link HttpServer}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class QuicHttpServerConfig extends HttpServerConfig {

  private static QuicServerOptions httpEndpointQuicConfig() {
    QuicServerOptions config = new QuicServerOptions();
    config.getSslOptions().setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));
    config.getTransportOptions().setInitialMaxData(10000000L);
    config.getTransportOptions().setInitialMaxStreamDataBidiLocal(1000000L);
    config.getTransportOptions().setInitialMaxStreamDataBidiRemote(1000000L);
    config.getTransportOptions().setInitialMaxStreamDataUni(1000000L);
    config.getTransportOptions().setInitialMaxStreamsBidi(100L);
    config.getTransportOptions().setInitialMaxStreamsUni(100L);
    return config;
  }

  /**
   * Default port the server will listen on = 443
   */
  public static final int DEFAULT_PORT = 443;

  /**
   * The default host to listen on = "0.0.0.0" (meaning listen on all available interfaces).
   */
  public static final String DEFAULT_HOST = "0.0.0.0";

  private int port;
  private String host;
  private QuicServerOptions endpointConfig;
  private Http3ServerConfig http3Config;

  public QuicHttpServerConfig() {
    super();

    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.endpointConfig = httpEndpointQuicConfig();
    this.http3Config = new Http3ServerConfig();
  }

  public QuicHttpServerConfig(QuicHttpServerConfig other) {
    super(other);

    this.port = other.port;
    this.host = other.host;
    this.endpointConfig = other.endpointConfig != null ? new QuicServerOptions(other.endpointConfig) : httpEndpointQuicConfig();
    this.http3Config = other.http3Config != null ? new Http3ServerConfig(other.http3Config) : new Http3ServerConfig();
  }

  @Override
  public ServerSSLOptions getSslOptions() {
    return endpointConfig.getSslOptions();
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public QuicHttpServerConfig setPort(int port) {
    if (port > 65535) {
      throw new IllegalArgumentException("port must be <= 65535");
    }
    this.port = port;
    return this;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public QuicHttpServerConfig setHost(String host) {
    this.host = host;
    return this;
  }

  @Override
  public Duration getIdleTimeout() {
    return endpointConfig.getStreamIdleTimeout();
  }

  @Override
  public QuicHttpServerConfig setIdleTimeout(Duration idleTimeout) {
    endpointConfig.setStreamIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public Duration getReadIdleTimeout() {
    return endpointConfig.getStreamReadIdleTimeout();
  }

  @Override
  public QuicHttpServerConfig setReadIdleTimeout(Duration idleTimeout) {
    endpointConfig.setStreamReadIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public Duration getWriteIdleTimeout() {
    return endpointConfig.getStreamWriteIdleTimeout();
  }

  @Override
  public QuicHttpServerConfig setWriteIdleTimeout(Duration idleTimeout) {
    endpointConfig.setStreamWriteIdleTimeout(idleTimeout);
    return this;
  }

  public QuicServerOptions getEndpointConfig() {
    return endpointConfig;
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
  public QuicHttpServerConfig setHttp1Config(Http3ServerConfig config) {
    this.http3Config = config;
    return this;
  }
}
