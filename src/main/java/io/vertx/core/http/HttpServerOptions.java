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
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Represents options used by an {@link io.vertx.core.http.HttpServer} instance
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
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
   * Default value of whether 100-Continue should be handled automatically = {@code false}
   */
  public static final boolean DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY = false;

  /**
   * Default Application-Layer Protocol Negotiation versions = [HTTP/2,HTTP/1.1]
   */
  public static final List<HttpVersion> DEFAULT_ALPN_VERSIONS = Collections.unmodifiableList(Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1));

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

  private boolean compressionSupported;
  private int compressionLevel;
  private int maxWebsocketFrameSize;
  private int maxWebsocketMessageSize;
  private String websocketSubProtocols;
  private boolean handle100ContinueAutomatically;
  private int maxChunkSize;
  private int maxInitialLineLength;
  private int maxHeaderSize;
  private Http2Settings initialSettings;
  private List<HttpVersion> alpnVersions;
  private int http2ConnectionWindowSize;
  private boolean decompressionSupported;
  private boolean acceptUnmaskedFrames;
  private int decoderInitialBufferSize;
  private boolean perFrameWebsocketCompressionSupported;
  private boolean perMessageWebsocketCompressionSupported;
  private int websocketCompressionLevel;
  private boolean websocketAllowServerNoContext;
  private boolean websocketPreferredClientNoContext;

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
    this.compressionSupported = other.isCompressionSupported();
    this.compressionLevel = other.getCompressionLevel();
    this.maxWebsocketFrameSize = other.getMaxWebsocketFrameSize();
    this.maxWebsocketMessageSize = other.getMaxWebsocketMessageSize();
    this.websocketSubProtocols = other.getWebsocketSubProtocols();
    this.handle100ContinueAutomatically = other.handle100ContinueAutomatically;
    this.maxChunkSize = other.getMaxChunkSize();
    this.maxInitialLineLength = other.getMaxInitialLineLength();
    this.maxHeaderSize = other.getMaxHeaderSize();
    this.initialSettings = other.initialSettings != null ? new Http2Settings(other.initialSettings) : null;
    this.alpnVersions = other.alpnVersions != null ? new ArrayList<>(other.alpnVersions) : null;
    this.http2ConnectionWindowSize = other.http2ConnectionWindowSize;
    this.decompressionSupported = other.isDecompressionSupported();
    this.acceptUnmaskedFrames = other.isAcceptUnmaskedFrames();
    this.decoderInitialBufferSize = other.getDecoderInitialBufferSize();
    this.perFrameWebsocketCompressionSupported = other.perFrameWebsocketCompressionSupported;
    this.perMessageWebsocketCompressionSupported = other.perMessageWebsocketCompressionSupported;
    this.websocketCompressionLevel = other.websocketCompressionLevel;
    this.websocketPreferredClientNoContext = other.websocketPreferredClientNoContext;
    this.websocketAllowServerNoContext = other.websocketAllowServerNoContext;
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
    compressionSupported = DEFAULT_COMPRESSION_SUPPORTED;
    compressionLevel = DEFAULT_COMPRESSION_LEVEL;
    maxWebsocketFrameSize = DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
    maxWebsocketMessageSize = DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE;
    handle100ContinueAutomatically = DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY;
    maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    maxInitialLineLength = DEFAULT_MAX_INITIAL_LINE_LENGTH;
    maxHeaderSize = DEFAULT_MAX_HEADER_SIZE;
    initialSettings = new Http2Settings().setMaxConcurrentStreams(DEFAULT_INITIAL_SETTINGS_MAX_CONCURRENT_STREAMS);
    alpnVersions = new ArrayList<>(DEFAULT_ALPN_VERSIONS);
    http2ConnectionWindowSize = DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE;
    decompressionSupported = DEFAULT_DECOMPRESSION_SUPPORTED;
    acceptUnmaskedFrames = DEFAULT_ACCEPT_UNMASKED_FRAMES;
    decoderInitialBufferSize = DEFAULT_DECODER_INITIAL_BUFFER_SIZE;
    perFrameWebsocketCompressionSupported = DEFAULT_PER_FRAME_WEBSOCKET_COMPRESSION_SUPPORTED;
    perMessageWebsocketCompressionSupported = DEFAULT_PER_MESSAGE_WEBSOCKET_COMPRESSION_SUPPORTED;
    websocketCompressionLevel = DEFAULT_WEBSOCKET_COMPRESSION_LEVEL;
    websocketPreferredClientNoContext = DEFAULT_WEBSOCKET_PREFERRED_CLIENT_NO_CONTEXT;
    websocketAllowServerNoContext = DEFAULT_WEBSOCKET_ALLOW_SERVER_NO_CONTEXT;
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
  public HttpServerOptions setUseAlpn(boolean useAlpn) {
    super.setUseAlpn(useAlpn);
    return this;
  }

  @Override
  public HttpServerOptions setKeyCertOptions(KeyCertOptions options) {
    super.setKeyCertOptions(options);
    return this;
  }

  @Override
  public HttpServerOptions setKeyStoreOptions(JksOptions options) {
    super.setKeyStoreOptions(options);
    return this;
  }

  @Override
  public HttpServerOptions setPfxKeyCertOptions(PfxOptions options) {
    return (HttpServerOptions) super.setPfxKeyCertOptions(options);
  }

  @Override
  public HttpServerOptions setPemKeyCertOptions(PemKeyCertOptions options) {
    return (HttpServerOptions) super.setPemKeyCertOptions(options);
  }

  @Override
  public HttpServerOptions setTrustOptions(TrustOptions options) {
    super.setTrustOptions(options);
    return this;
  }

  @Override
  public HttpServerOptions setTrustStoreOptions(JksOptions options) {
    super.setTrustStoreOptions(options);
    return this;
  }

  @Override
  public HttpServerOptions setPemTrustOptions(PemTrustOptions options) {
    return (HttpServerOptions) super.setPemTrustOptions(options);
  }

  @Override
  public HttpServerOptions setPfxTrustOptions(PfxOptions options) {
    return (HttpServerOptions) super.setPfxTrustOptions(options);
  }

  @Override
  public HttpServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
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
  public HttpServerOptions setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return (HttpServerOptions) super.setSslEngineOptions(sslEngineOptions);
  }

  @Override
  public HttpServerOptions setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
    return (HttpServerOptions) super.setSslEngineOptions(sslEngineOptions);
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
    return compressionSupported;
  }

  /**
   * Set whether the server should support gzip/deflate compression
   * (serving compressed responses to clients advertising support for them with Accept-Encoding header)
   *
   * @param compressionSupported {@code true} to enable compression support
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setCompressionSupported(boolean compressionSupported) {
    this.compressionSupported = compressionSupported;
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

  public boolean isAcceptUnmaskedFrames() {
    return acceptUnmaskedFrames;
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
    this.acceptUnmaskedFrames = acceptUnmaskedFrames;
    return this;
  }

  /**
   * @return  the maximum WebSocket frame size
   */
  public int getMaxWebsocketFrameSize() {
    return maxWebsocketFrameSize;
  }

  /**
   * Set the maximum WebSocket frames size
   *
   * @param maxWebsocketFrameSize  the maximum frame size in bytes.
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxWebsocketFrameSize(int maxWebsocketFrameSize) {
    this.maxWebsocketFrameSize = maxWebsocketFrameSize;
    return this;
  }

  /**
   * @return  the maximum WebSocket message size
   */
  public int getMaxWebsocketMessageSize() {
    return maxWebsocketMessageSize;
  }

  /**
   * Set the maximum WebSocket message size
   *
   * @param maxWebsocketMessageSize  the maximum message size in bytes.
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxWebsocketMessageSize(int maxWebsocketMessageSize) {
    this.maxWebsocketMessageSize = maxWebsocketMessageSize;
    return this;
  }

  /**
   * Set the WebSocket sub-protocols supported by the server.
   *
   * @param subProtocols  comma separated list of subprotocols
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebsocketSubProtocols(String subProtocols) {
    websocketSubProtocols = subProtocols;
    return this;
  }

  /**
   * @return Get the WebSocket sub-protocols
   */
  public String getWebsocketSubProtocols() {
    return websocketSubProtocols;
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
    this.maxChunkSize = maxChunkSize;
    return this;
  }

  /**
   * @return the maximum HTTP chunk size that {@link HttpServerRequest#handler(Handler)} will receive
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
   * Set the maximum length of the initial line for HTTP/1.x (e.g. {@code "GET / HTTP/1.0"})
   *
   * @param maxInitialLineLength the new maximum initial length
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxInitialLineLength(int maxInitialLineLength) {
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
  public HttpServerOptions setMaxHeaderSize(int maxHeaderSize) {
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
   * Set the HTTP/2 connection settings immediatly sent by the server when a client connects.
   *
   * @param settings the settings value
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setInitialSettings(Http2Settings settings) {
    this.initialSettings = settings;
    return this;
  }

  /**
   * @return the list of protocol versions to provide during the Application-Layer Protocol Negotiatiation
   */
  public List<HttpVersion> getAlpnVersions() {
    return alpnVersions;
  }

  /**
   * Set the list of protocol versions to provide to the server during the Application-Layer Protocol Negotiatiation.
   *
   * @param alpnVersions the versions
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setAlpnVersions(List<HttpVersion> alpnVersions) {
    this.alpnVersions = alpnVersions;
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
  public HttpServerOptions setHttp2ConnectionWindowSize(int http2ConnectionWindowSize) {
    this.http2ConnectionWindowSize = http2ConnectionWindowSize;
    return this;
  }

  @Override
  public HttpServerOptions setLogActivity(boolean logEnabled) {
    return (HttpServerOptions) super.setLogActivity(logEnabled);
  }

  public HttpServerOptions setSni(boolean sni) {
    return (HttpServerOptions) super.setSni(sni);
  }

  /**
   * @return {@code true} if the server supports decompression
   */
  public boolean isDecompressionSupported() {
    return decompressionSupported;
  }

  /**
   * Set whether the server supports decompression
   *
   * @param decompressionSupported {@code true} if decompression supported
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setDecompressionSupported(boolean decompressionSupported) {
    this.decompressionSupported = decompressionSupported;
    return this;
  }

  /**
   * @return the initial buffer size for the HTTP decoder
   */
  public int getDecoderInitialBufferSize() { return decoderInitialBufferSize; }

  /**
   * Set the initial buffer size for the HTTP decoder
   * @param decoderInitialBufferSize the initial size
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setDecoderInitialBufferSize(int decoderInitialBufferSize) {
    Arguments.require(decoderInitialBufferSize > 0, "initialBufferSizeHttpDecoder must be > 0");
    this.decoderInitialBufferSize = decoderInitialBufferSize;
    return this;
  }

  /**
   * Enable or disable support for the WebSocket per-frame deflate compression extension.
   *
   * @param supported {@code true} when the per-frame deflate compression extension is supported
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setPerFrameWebsocketCompressionSupported(boolean supported ) {
	  this.perFrameWebsocketCompressionSupported = supported;
	  return this;
  }
  
  /** 
   * Get whether WebSocket the per-frame deflate compression extension is supported.
   *
   * @return {@code true} if the http server will accept the per-frame deflate compression extension
   */
  public boolean getPerFrameWebsocketCompressionSupported() {
	  return this.perFrameWebsocketCompressionSupported;
  }
  
  /**
   * Enable or disable support for WebSocket per-message deflate compression extension.
   *
   * @param supported {@code true} when the per-message Websocket compression extension is supported
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setPerMessageWebsocketCompressionSupported(boolean supported ) {
	  this.perMessageWebsocketCompressionSupported = supported;
	  return this;
  }
  
  /**
   * Get whether WebSocket per-message deflate compression extension is supported.
   *
   * @return {@code true} if the http server will accept the per-message deflate compression extension
   */
  public boolean getPerMessageWebsocketCompressionSupported() {
	  return this.perMessageWebsocketCompressionSupported;
  }
  
  /**
   * Set the WebSocket compression level.
   *
   * @param compressionLevel the compression level
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebsocketCompressionLevel(int compressionLevel) {
	  this.websocketCompressionLevel = compressionLevel;
	  return this;
  }
  
  /**
   * @return the current WebSocket deflate compression level
   */
  public int getWebsocketCompressionLevel() {
	  return this.websocketCompressionLevel;
  }
  
  /**
   * Set whether the WebSocket server will accept the {@code server_no_context_takeover} parameter of the per-message
   * deflate compression extension offered by the client.
   *
   * @param accept {@code true} to accept the {@literal server_no_context_takeover} parameter when the client offers it
   * @return  a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebsocketAllowServerNoContext(boolean accept) {
	  this.websocketAllowServerNoContext = accept;
	  return this;
  }
  
  /**
   * @return {@code true} when the WebSocket server will accept the {@code server_no_context_takeover} parameter for the per-message
   * deflate compression extension offered by the client
   */
  public boolean getWebsocketAllowServerNoContext () {
	  return this.websocketAllowServerNoContext;
  }
  
  /**
   * Set whether the WebSocket server will accept the {@code client_no_context_takeover} parameter of the per-message
   * deflate compression extension offered by the client.
   *
   * @param accept {@code true} to accept the {@code client_no_context_takeover} parameter when the client offers it
   * @return  a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebsocketPreferredClientNoContext(boolean accept) {
	  this.websocketPreferredClientNoContext = accept;
	  return this;
  }

  /**
   * @return {@code true} when the WebSocket server will accept the {@code client_no_context_takeover} parameter for the per-message
   * deflate compression extension offered by the client
   */
  public boolean getWebsocketPreferredClientNoContext() {
	  return this.websocketPreferredClientNoContext;
  }

}
