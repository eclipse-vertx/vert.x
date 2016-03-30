/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents options used by an {@link io.vertx.core.http.HttpServer} instance
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true)
public class HttpServerOptions extends NetServerOptions {

  /**
   * Default port the server will listen on = 80
   */
  public static final int DEFAULT_PORT = 80;  // Default port is 80 for HTTP not 0 from HttpServerOptions

  /**
   * Default value of whether compression is supported = false
   */
  public static final boolean DEFAULT_COMPRESSION_SUPPORTED = false;

  /**
   * Default max websocket framesize = 65536
   */
  public static final int DEFAULT_MAX_WEBSOCKET_FRAME_SIZE = 65536;

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
   * Default value of whether 100-Continue should be handled automatically
   */
  public static final boolean DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY = false;

  public static final List<HttpVersion> DEFAULT_ALPN_VERSIONS = Collections.unmodifiableList(Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1));

  private boolean compressionSupported;
  private int maxWebsocketFrameSize;
  private String websocketSubProtocols;
  private boolean handle100ContinueAutomatically;
  private int maxChunkSize;
  private int maxInitialLineLength;
  private int maxHeaderSize;
  private Http2Settings initialSettings;
  private List<HttpVersion> alpnVersions;

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
    this.maxWebsocketFrameSize = other.getMaxWebsocketFrameSize();
    this.websocketSubProtocols = other.getWebsocketSubProtocols();
    this.handle100ContinueAutomatically = other.handle100ContinueAutomatically;
    this.maxChunkSize = other.getMaxChunkSize();
    this.maxInitialLineLength = other.getMaxInitialLineLength();
    this.maxHeaderSize = other.getMaxHeaderSize();
    this.initialSettings = other.initialSettings != null ? new Http2Settings(other.initialSettings) : null;
    this.alpnVersions = other.alpnVersions != null ? new ArrayList<>(other.alpnVersions) : null;
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

  private void init() {
    compressionSupported = DEFAULT_COMPRESSION_SUPPORTED;
    maxWebsocketFrameSize = DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
    handle100ContinueAutomatically = DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY;
    maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    maxInitialLineLength = DEFAULT_MAX_INITIAL_LINE_LENGTH;
    maxHeaderSize = DEFAULT_MAX_HEADER_SIZE;
    initialSettings = new Http2Settings();
    alpnVersions = new ArrayList<>(DEFAULT_ALPN_VERSIONS);
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
  public HttpServerOptions setUsePooledBuffers(boolean usePooledBuffers) {
    super.setUsePooledBuffers(usePooledBuffers);
    return this;
  }

  @Override
  public HttpServerOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
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
  public HttpServerOptions setSslEngine(SSLEngine sslEngine) {
    super.setSslEngine(sslEngine);
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
  @Deprecated
  public HttpServerOptions setClientAuthRequired(boolean clientAuthRequired) {
    super.setClientAuthRequired(clientAuthRequired);
    return this;
  }

  @Override
  public HttpServerOptions setClientAuth(ClientAuth clientAuth) {
    super.setClientAuth(clientAuth);
    return this;
  }


  /**
   * @return true if the server supports compression
   */
  public boolean isCompressionSupported() {
    return compressionSupported;
  }

  /**
   * Set whether the server supports compression
   *
   * @param compressionSupported true if compression supported
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setCompressionSupported(boolean compressionSupported) {
    this.compressionSupported = compressionSupported;
    return this;
  }

  /**
   * @return  the maximum websocket framesize
   */
  public int getMaxWebsocketFrameSize() {
    return maxWebsocketFrameSize;
  }

  /**
   * Set the maximum websocket frames size
   *
   * @param maxWebsocketFrameSize  the maximum frame size in bytes.
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxWebsocketFrameSize(int maxWebsocketFrameSize) {
    this.maxWebsocketFrameSize = maxWebsocketFrameSize;
    return this;
  }

  /**
   * Set the websocket subprotocols supported by the server.
   *
   * @param subProtocols  comma separated list of subprotocols
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setWebsocketSubProtocols(String subProtocols) {
    websocketSubProtocols = subProtocols;
    return this;
  }

  /**
   * @return Get the websocket subprotocols
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
   * @param handle100ContinueAutomatically true if it should be handled automatically
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setHandle100ContinueAutomatically(boolean handle100ContinueAutomatically) {
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    return this;
  }

  /**
   * Set the maximum HTTP chunk size
   * @param maxChunkSize the maximum chunk size
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerOptions setMaxChunkSize(int maxChunkSize) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    HttpServerOptions that = (HttpServerOptions) o;

    if (compressionSupported != that.compressionSupported) return false;
    if (maxWebsocketFrameSize != that.maxWebsocketFrameSize) return false;
    if (handle100ContinueAutomatically != that.handle100ContinueAutomatically) return false;
    if (maxChunkSize != that.maxChunkSize) return false;
    if (maxInitialLineLength != that.maxInitialLineLength) return false;
    if (maxHeaderSize != that.maxHeaderSize) return false;
    if (initialSettings == null ? that.initialSettings != null : !initialSettings.equals(that.initialSettings)) return false;
    return !(websocketSubProtocols != null ? !websocketSubProtocols.equals(that.websocketSubProtocols) : that.websocketSubProtocols != null);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (compressionSupported ? 1 : 0);
    result = 31 * result + maxWebsocketFrameSize;
    result = 31 * result + (websocketSubProtocols != null ? websocketSubProtocols.hashCode() : 0);
    result = 31 * result + (initialSettings != null ? initialSettings.hashCode() : 0);
    result = 31 * result + (handle100ContinueAutomatically ? 1 : 0);
    result = 31 * result + maxChunkSize;
    result = 31 * result + maxInitialLineLength;
    result = 31 * result + maxHeaderSize;
    return result;
  }
}
