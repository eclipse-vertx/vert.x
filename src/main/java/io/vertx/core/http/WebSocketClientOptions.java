/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.http.HttpClientOptions.DEFAULT_NAME;
import static io.vertx.core.http.HttpClientOptions.DEFAULT_SHARED;

@DataObject
@JsonGen(publicConverter = false)
public class WebSocketClientOptions extends ClientOptionsBase {

  private String defaultHost;
  private int defaultPort;
  private boolean verifyHost;
  private int maxFrameSize;
  private int maxMessageSize;
  private int maxConnections;
  private boolean sendUnmaskedFrames;
  private boolean tryUsePerFrameCompression;
  private boolean tryUsePerMessageCompression;
  private int compressionLevel;
  private boolean allowClientNoContext;
  private boolean requestServerNoContext;
  private int closingTimeout;
  private boolean shared;
  private String name;

  /**
   * Default constructor
   */
  public WebSocketClientOptions() {
    init();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public WebSocketClientOptions(WebSocketClientOptions other) {
    super(other);

    this.defaultHost = other.defaultHost;
    this.defaultPort = other.defaultPort;
    this.verifyHost = other.verifyHost;
    this.maxFrameSize = other.maxFrameSize;
    this.maxMessageSize = other.maxMessageSize;
    this.maxConnections = other.maxConnections;
    this.sendUnmaskedFrames = other.sendUnmaskedFrames;
    this.tryUsePerFrameCompression = other.tryUsePerFrameCompression;
    this.tryUsePerMessageCompression = other.tryUsePerMessageCompression;
    this.allowClientNoContext = other.allowClientNoContext;
    this.compressionLevel = other.compressionLevel;
    this.requestServerNoContext = other.requestServerNoContext;
    this.closingTimeout = other.closingTimeout;
    this.shared = other.shared;
    this.name = other.name;
  }

  /**
   * Constructor to create an options from JSON
   *
   * @param json  the JSON
   */
  public WebSocketClientOptions(JsonObject json) {
    super(json);
    init();
    WebSocketClientOptionsConverter.fromJson(json, this);
  }

  private void init() {
    verifyHost = true;
    defaultHost = HttpClientOptions.DEFAULT_DEFAULT_HOST;
    defaultPort = HttpClientOptions.DEFAULT_DEFAULT_PORT;
    maxFrameSize = HttpClientOptions.DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
    maxMessageSize = HttpClientOptions.DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE;
    maxConnections = HttpClientOptions.DEFAULT_MAX_WEBSOCKETS;
    sendUnmaskedFrames = HttpClientOptions.DEFAULT_SEND_UNMASKED_FRAMES;
    tryUsePerFrameCompression = HttpClientOptions.DEFAULT_TRY_USE_PER_FRAME_WEBSOCKET_COMPRESSION;
    tryUsePerMessageCompression = HttpClientOptions.DEFAULT_TRY_USE_PER_MESSAGE_WEBSOCKET_COMPRESSION;
    compressionLevel = HttpClientOptions.DEFAULT_WEBSOCKET_COMPRESSION_LEVEL;
    allowClientNoContext = HttpClientOptions.DEFAULT_WEBSOCKET_ALLOW_CLIENT_NO_CONTEXT;
    requestServerNoContext = HttpClientOptions.DEFAULT_WEBSOCKET_REQUEST_SERVER_NO_CONTEXT;
    closingTimeout = HttpClientOptions.DEFAULT_WEBSOCKET_CLOSING_TIMEOUT;
    shared = DEFAULT_SHARED;
    name = DEFAULT_NAME;
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
  public WebSocketClientOptions setDefaultHost(String defaultHost) {
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
  public WebSocketClientOptions setDefaultPort(int defaultPort) {
    this.defaultPort = defaultPort;
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
  public WebSocketClientOptions setVerifyHost(boolean verifyHost) {
    this.verifyHost = verifyHost;
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
  public WebSocketClientOptions setSendUnmaskedFrames(boolean sendUnmaskedFrames) {
    this.sendUnmaskedFrames = sendUnmaskedFrames;
    return this;
  }

  /**
   * Get the maximum WebSocket frame size to use
   *
   * @return  the max WebSocket frame size
   */
  public int getMaxFrameSize() {
    return maxFrameSize;
  }

  /**
   * Set the max WebSocket frame size
   *
   * @param maxFrameSize  the max frame size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketClientOptions setMaxFrameSize(int maxFrameSize) {
    this.maxFrameSize = maxFrameSize;
    return this;
  }

  /**
   * Get the maximum WebSocket message size to use
   *
   * @return  the max WebSocket message size
   */
  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  /**
   * Set the max WebSocket message size
   *
   * @param maxMessageSize  the max message size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketClientOptions setMaxMessageSize(int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
    return this;
  }

  /**
   * Get the maximum of WebSockets per endpoint.
   *
   * @return  the max number of WebSockets
   */
  public int getMaxConnections() {
    return maxConnections;
  }

  /**
   * Set the max number of WebSockets per endpoint.
   *
   * @param maxConnections  the max value
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketClientOptions setMaxConnections(int maxConnections) {
    if (maxConnections == 0 || maxConnections < -1) {
      throw new IllegalArgumentException("maxWebSockets must be > 0 or -1 (disabled)");
    }
    this.maxConnections = maxConnections;
    return this;
  }

  /**
   * Set whether the client will offer the WebSocket per-frame deflate compression extension.
   *
   * @param offer {@code true} to offer the per-frame deflate compression extension
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketClientOptions setTryUsePerFrameCompression(boolean offer) {
    this.tryUsePerFrameCompression = offer;
    return this;
  }

  /**
   * @return {@code true} when the WebSocket per-frame deflate compression extension will be offered
   */
  public boolean getTryUsePerFrameCompression() {
    return this.tryUsePerFrameCompression;
  }

  /**
   * Set whether the client will offer the WebSocket per-message deflate compression extension.
   *
   * @param offer {@code true} to offer the per-message deflate compression extension
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketClientOptions setTryUsePerMessageCompression(boolean offer) {
    this.tryUsePerMessageCompression = offer;
    return this;
  }

  /**
   * @return {@code true} when the WebSocket per-message deflate compression extension will be offered
   */
  public boolean getTryUsePerMessageCompression() {
    return this.tryUsePerMessageCompression;
  }

  /**
   * Set the WebSocket deflate compression level.
   *
   * @param compressionLevel the WebSocket deflate compression level
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketClientOptions setCompressionLevel(int compressionLevel) {
    this.compressionLevel = compressionLevel;
    return this;
  }

  /**
   * @return the WebSocket deflate compression level
   */
  public int getCompressionLevel() {
    return this.compressionLevel;
  }

  /**
   * Set whether the {@code client_no_context_takeover} parameter of the WebSocket per-message
   * deflate compression extension will be offered.
   *
   * @param offer {@code true} to offer the {@code client_no_context_takeover} parameter
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketClientOptions setCompressionAllowClientNoContext(boolean offer) {
    this.allowClientNoContext = offer;
    return this;
  }

  /**
   * @return {@code true} when the {@code client_no_context_takeover} parameter for the WebSocket per-message
   * deflate compression extension will be offered
   */
  public boolean getCompressionAllowClientNoContext() {
    return this.allowClientNoContext;
  }

  /**
   * Set whether the {@code server_no_context_takeover} parameter of the WebSocket per-message
   * deflate compression extension will be offered.
   *
   * @param offer {@code true} to offer the {@code server_no_context_takeover} parameter
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketClientOptions setCompressionRequestServerNoContext(boolean offer) {
    this.requestServerNoContext = offer;
    return this;
  }

  /**
   * @return {@code true} when the {@code server_no_context_takeover} parameter for the WebSocket per-message
   * deflate compression extension will be offered
   */
  public boolean getCompressionRequestServerNoContext() {
    return this.requestServerNoContext;
  }

  /**
   * @return the amount of time (in seconds) a client WebSocket will wait until it closes TCP connection after receiving a close frame
   */
  public int getClosingTimeout() {
    return closingTimeout;
  }

  /**
   * Set the amount of time a client WebSocket will wait until it closes the TCP connection after sending a close frame.
   *
   * <p> When a WebSocket is closed, the server should close the TCP connection. This timeout will close
   * the TCP connection on the client when the server has not responded with a close frame and closed the connection
   * in a timely manner.
   *
   * <p> Setting to {@code 0L} closes the TCP connection after receiving the close frame, note this should
   * not be avoided when interacting with misbehaving server that do not respond with close frames.
   *
   * <p> Set to a negative value to disable it.
   *
   * @param closingTimeout the timeout is seconds
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketClientOptions setClosingTimeout(int closingTimeout) {
    this.closingTimeout = closingTimeout;
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
  public WebSocketClientOptions setShared(boolean shared) {
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
  public WebSocketClientOptions setName(String name) {
    Objects.requireNonNull(name, "Client name cannot be null");
    this.name = name;
    return this;
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    WebSocketClientOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public WebSocketClientOptions setTrustAll(boolean trustAll) {
    return (WebSocketClientOptions)super.setTrustAll(trustAll);
  }

  @Override
  public WebSocketClientOptions setConnectTimeout(int connectTimeout) {
    return (WebSocketClientOptions)super.setConnectTimeout(connectTimeout);
  }

  @Override
  public WebSocketClientOptions setMetricsName(String metricsName) {
    return (WebSocketClientOptions)super.setMetricsName(metricsName);
  }

  @Override
  public WebSocketClientOptions setProxyOptions(ProxyOptions proxyOptions) {
    return (WebSocketClientOptions)super.setProxyOptions(proxyOptions);
  }

  @Override
  public WebSocketClientOptions setNonProxyHosts(List<String> nonProxyHosts) {
    return (WebSocketClientOptions)super.setNonProxyHosts(nonProxyHosts);
  }

  @Override
  public WebSocketClientOptions setLocalAddress(String localAddress) {
    return (WebSocketClientOptions)super.setLocalAddress(localAddress);
  }

  @Override
  public WebSocketClientOptions setLogActivity(boolean logEnabled) {
    return (WebSocketClientOptions)super.setLogActivity(logEnabled);
  }

  @Override
  public WebSocketClientOptions setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    return (WebSocketClientOptions)super.setActivityLogDataFormat(activityLogDataFormat);
  }

  @Override
  public WebSocketClientOptions setTcpNoDelay(boolean tcpNoDelay) {
    return (WebSocketClientOptions)super.setTcpNoDelay(tcpNoDelay);
  }

  @Override
  public WebSocketClientOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    return (WebSocketClientOptions)super.setTcpKeepAlive(tcpKeepAlive);
  }

  @Override
  public WebSocketClientOptions setSoLinger(int soLinger) {
    return (WebSocketClientOptions)super.setSoLinger(soLinger);
  }

  @Override
  public WebSocketClientOptions setIdleTimeout(int idleTimeout) {
    return (WebSocketClientOptions)super.setIdleTimeout(idleTimeout);
  }

  @Override
  public WebSocketClientOptions setReadIdleTimeout(int idleTimeout) {
    return (WebSocketClientOptions)super.setReadIdleTimeout(idleTimeout);
  }

  @Override
  public WebSocketClientOptions setWriteIdleTimeout(int idleTimeout) {
    return (WebSocketClientOptions)super.setWriteIdleTimeout(idleTimeout);
  }

  @Override
  public WebSocketClientOptions setIdleTimeoutUnit(TimeUnit idleTimeoutUnit) {
    return (WebSocketClientOptions)super.setIdleTimeoutUnit(idleTimeoutUnit);
  }

  @Override
  public WebSocketClientOptions setSsl(boolean ssl) {
    return (WebSocketClientOptions)super.setSsl(ssl);
  }

  @Override
  public WebSocketClientOptions setKeyCertOptions(KeyCertOptions options) {
    return (WebSocketClientOptions)super.setKeyCertOptions(options);
  }

  @Deprecated
  @Override
  public WebSocketClientOptions setKeyStoreOptions(JksOptions options) {
    return (WebSocketClientOptions)super.setKeyStoreOptions(options);
  }

  @Deprecated
  @Override
  public WebSocketClientOptions setPfxKeyCertOptions(PfxOptions options) {
    return (WebSocketClientOptions)super.setPfxKeyCertOptions(options);
  }

  @Deprecated
  @Override
  public WebSocketClientOptions setPemKeyCertOptions(PemKeyCertOptions options) {
    return (WebSocketClientOptions)super.setPemKeyCertOptions(options);
  }

  @Override
  public WebSocketClientOptions setTrustOptions(TrustOptions options) {
    return (WebSocketClientOptions)super.setTrustOptions(options);
  }

  @Deprecated
  @Override
  public WebSocketClientOptions setTrustStoreOptions(JksOptions options) {
    return (WebSocketClientOptions)super.setTrustStoreOptions(options);
  }

  @Deprecated
  @Override
  public WebSocketClientOptions setPfxTrustOptions(PfxOptions options) {
    return (WebSocketClientOptions)super.setPfxTrustOptions(options);
  }

  @Deprecated
  @Override
  public WebSocketClientOptions setPemTrustOptions(PemTrustOptions options) {
    return (WebSocketClientOptions)super.setPemTrustOptions(options);
  }

  @Override
  public WebSocketClientOptions setUseAlpn(boolean useAlpn) {
    return (WebSocketClientOptions)super.setUseAlpn(useAlpn);
  }

  @Override
  public WebSocketClientOptions setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    return (WebSocketClientOptions)super.setSslEngineOptions(sslEngineOptions);
  }

  @Deprecated
  @Override
  public WebSocketClientOptions setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return (WebSocketClientOptions)super.setJdkSslEngineOptions(sslEngineOptions);
  }

  @Deprecated
  @Override
  public WebSocketClientOptions setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
    return (WebSocketClientOptions)super.setOpenSslEngineOptions(sslEngineOptions);
  }

  @Override
  public WebSocketClientOptions setSendBufferSize(int sendBufferSize) {
    return (WebSocketClientOptions)super.setSendBufferSize(sendBufferSize);
  }

  @Override
  public WebSocketClientOptions setReceiveBufferSize(int receiveBufferSize) {
    return (WebSocketClientOptions)super.setReceiveBufferSize(receiveBufferSize);
  }

  @Override
  public WebSocketClientOptions setReuseAddress(boolean reuseAddress) {
    return (WebSocketClientOptions)super.setReuseAddress(reuseAddress);
  }

  @Override
  public WebSocketClientOptions setReusePort(boolean reusePort) {
    return (WebSocketClientOptions)super.setReusePort(reusePort);
  }

  @Override
  public WebSocketClientOptions setTrafficClass(int trafficClass) {
    return (WebSocketClientOptions)super.setTrafficClass(trafficClass);
  }

  @Override
  public WebSocketClientOptions setTcpFastOpen(boolean tcpFastOpen) {
    return (WebSocketClientOptions)super.setTcpFastOpen(tcpFastOpen);
  }

  @Override
  public WebSocketClientOptions setTcpCork(boolean tcpCork) {
    return (WebSocketClientOptions)super.setTcpCork(tcpCork);
  }

  @Override
  public WebSocketClientOptions setTcpQuickAck(boolean tcpQuickAck) {
    return (WebSocketClientOptions)super.setTcpQuickAck(tcpQuickAck);
  }

  @Override
  public WebSocketClientOptions setTcpUserTimeout(int tcpUserTimeout) {
    return (WebSocketClientOptions)super.setTcpUserTimeout(tcpUserTimeout);
  }

  @Override
  public WebSocketClientOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    return (WebSocketClientOptions)super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
  }

  @Override
  public WebSocketClientOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    return (WebSocketClientOptions)super.setSslHandshakeTimeout(sslHandshakeTimeout);
  }

  @Override
  public WebSocketClientOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    return (WebSocketClientOptions)super.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
  }

  @Override
  public WebSocketClientOptions addNonProxyHost(String host) {
    return (WebSocketClientOptions)super.addNonProxyHost(host);
  }

  @Override
  public WebSocketClientOptions addEnabledCipherSuite(String suite) {
    return (WebSocketClientOptions)super.addEnabledCipherSuite(suite);
  }

  @Override
  public WebSocketClientOptions removeEnabledCipherSuite(String suite) {
    return (WebSocketClientOptions)super.removeEnabledCipherSuite(suite);
  }

  @Override
  public WebSocketClientOptions addCrlPath(String crlPath) throws NullPointerException {
    return (WebSocketClientOptions)super.addCrlPath(crlPath);
  }

  @Override
  public WebSocketClientOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    return (WebSocketClientOptions)super.addCrlValue(crlValue);
  }

  @Override
  public WebSocketClientOptions addEnabledSecureTransportProtocol(String protocol) {
    return (WebSocketClientOptions)super.addEnabledSecureTransportProtocol(protocol);
  }
}
