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

import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.TrustStoreOptions;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class HttpServerOptions extends NetServerOptions {

  public static final int DEFAULT_PORT = 80;  // Default port is 80 for HTTP not 0 from HttpServerOptions
  public static final boolean DEFAULT_COMPRESSION_SUPPORTED = false;  
  public static final int DEFAULT_MAX_WEBSOCKET_FRAME_SIZE = 65536;

  private boolean compressionSupported;
  private int maxWebsocketFrameSize;
  private String websocketSubProtocols;

  public HttpServerOptions(HttpServerOptions other) {
    super(other);
    this.compressionSupported = other.isCompressionSupported();
    this.maxWebsocketFrameSize = other.getMaxWebsocketFrameSize();
    this.websocketSubProtocols = other.getWebsocketSubProtocols();
  }

  public HttpServerOptions(JsonObject json) {
    super(json);
    this.compressionSupported = json.getBoolean("compressionSupported", DEFAULT_COMPRESSION_SUPPORTED);
    this.maxWebsocketFrameSize = json.getInteger("maxWebsocketFrameSize", DEFAULT_MAX_WEBSOCKET_FRAME_SIZE);
    this.websocketSubProtocols = json.getString("websocketSubProtocols", null);
    setPort(json.getInteger("port", DEFAULT_PORT));
  }

  public HttpServerOptions() {
    super();
    setPort(DEFAULT_PORT); // We override the default for port
    compressionSupported = DEFAULT_COMPRESSION_SUPPORTED;
    maxWebsocketFrameSize = DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
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
  public HttpServerOptions setKeyStoreOptions(KeyStoreOptions keyStore) {
    super.setKeyStoreOptions(keyStore);
    return this;
  }

  @Override
  public HttpServerOptions setTrustStoreOptions(TrustStoreOptions trustStore) {
    super.setTrustStoreOptions(trustStore);
    return this;
  }

  @Override
  public HttpServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
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

  public boolean isCompressionSupported() {
    return compressionSupported;
  }

  public HttpServerOptions setCompressionSupported(boolean compressionSupported) {
    this.compressionSupported = compressionSupported;
    return this;
  }

  public int getMaxWebsocketFrameSize() {
    return maxWebsocketFrameSize;
  }

  public HttpServerOptions setMaxWebsocketFrameSize(int maxWebsocketFrameSize) {
    this.maxWebsocketFrameSize = maxWebsocketFrameSize;
    return this;
  }

  public HttpServerOptions setWebsocketSubProtocol(String subProtocols) {
    websocketSubProtocols = subProtocols;
    return this;
  }

  public String getWebsocketSubProtocols() {
    return websocketSubProtocols;
  }
  
  @Override
  public HttpServerOptions setClientAuthRequired(boolean clientAuthRequired) {
    super.setClientAuthRequired(clientAuthRequired);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HttpServerOptions)) return false;
    if (!super.equals(o)) return false;

    HttpServerOptions that = (HttpServerOptions) o;

    if (compressionSupported != that.compressionSupported) return false;
    if (maxWebsocketFrameSize != that.maxWebsocketFrameSize) return false;
    if (websocketSubProtocols != that.websocketSubProtocols) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (compressionSupported ? 1 : 0);
    result = 31 * result + maxWebsocketFrameSize;
    result = 31 * result + (websocketSubProtocols != null ? websocketSubProtocols.hashCode() : 0);
    return result;
  }
}
