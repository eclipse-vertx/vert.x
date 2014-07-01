/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core.http;

import io.vertx.core.net.NetServerOptions;

import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerOptions extends NetServerOptions {

  // Server specific HTTP stuff

  private boolean compressionSupported;
  private int maxWebsocketFrameSize = 65536;
  private Set<String> websocketSubProtocols;
  private int port = 80; // Default port is 80 for HTTP not 0 from NetServerOptions

  public HttpServerOptions() {
    super();
  }

  public HttpServerOptions(HttpServerOptions other) {
    super(other);
    this.compressionSupported = other.compressionSupported;
    this.maxWebsocketFrameSize = other.maxWebsocketFrameSize;
    this.websocketSubProtocols = other.websocketSubProtocols != null ? new HashSet<>(other.websocketSubProtocols) : null;
    this.port = other.port;
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

  public HttpServerOptions addWebsocketSubProtocol(String subProtocol) {
    if (websocketSubProtocols == null) {
      websocketSubProtocols = new HashSet<>();
    }
    websocketSubProtocols.add(subProtocol);
    return this;
  }

  public Set<String> getWebsocketSubProtocols() {
    return websocketSubProtocols;
  }

  // Override common implementation

  @Override
  public HttpServerOptions setClientAuthRequired(boolean clientAuthRequired) {
    super.setClientAuthRequired(clientAuthRequired);
    return this;
  }

  @Override
  public HttpServerOptions setAcceptBacklog(int acceptBacklog) {
    super.setAcceptBacklog(acceptBacklog);
    return this;
  }

  @Override
  public HttpServerOptions setPort(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port p must be in range 0 <= p <= 65535");
    }
    this.port = port;
    return this;
  }

  @Override
  public int getPort() {
    return this.port;
  }

  @Override
  public HttpServerOptions setHost(String host) {
    super.setHost(host);
    return this;
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
  public HttpServerOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
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
  public HttpServerOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public HttpServerOptions setKeyStorePath(String keyStorePath) {
    super.setKeyStorePath(keyStorePath);
    return this;
  }

  @Override
  public HttpServerOptions setKeyStorePassword(String keyStorePassword) {
    super.setKeyStorePassword(keyStorePassword);
    return this;
  }

  @Override
  public HttpServerOptions setTrustStorePath(String trustStorePath) {
    super.setTrustStorePath(trustStorePath);
    return this;
  }

  @Override
  public HttpServerOptions setTrustStorePassword(String trustStorePassword) {
    super.setTrustStorePassword(trustStorePassword);
    return this;
  }

  @Override
  public HttpServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

}
