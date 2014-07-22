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

import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.TrustStoreOptions;

import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class HttpServerOptions extends NetServerOptions {

  // Server specific HTTP stuff

  private static final int DEFAULT_MAXWEBSOCKETFRAMESIZE = 65536;
  private static final int DEFAULT_PORT = 80;  // Default port is 80 for HTTP not 0 from NetServerOptions

  private boolean compressionSupported;
  private int maxWebsocketFrameSize;
  private Set<String> websocketSubProtocols = new HashSet<>();
  private int port;

  public HttpServerOptions() {
    super();
    this.maxWebsocketFrameSize = DEFAULT_MAXWEBSOCKETFRAMESIZE;
    this.port = DEFAULT_PORT;
  }

  public HttpServerOptions(HttpServerOptions other) {
    super(other);
    this.compressionSupported = other.compressionSupported;
    this.maxWebsocketFrameSize = other.maxWebsocketFrameSize;
    this.websocketSubProtocols = other.websocketSubProtocols != null ? new HashSet<>(other.websocketSubProtocols) : null;
    this.port = other.port;
  }

  public HttpServerOptions(JsonObject json) {
    super(json);
    this.compressionSupported = json.getBoolean("compressionSupported", false);
    this.maxWebsocketFrameSize = json.getInteger("maxWebsocketFrameSize", DEFAULT_MAXWEBSOCKETFRAMESIZE);
    JsonArray arr = json.getArray("websocketSubProtocols");
    this.websocketSubProtocols = arr == null ? null : new HashSet<String>(arr.toList());
    this.port = json.getInteger("port", DEFAULT_PORT);
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
  public HttpServerOptions addCrlPath(String crlPath) throws NullPointerException {
    super.addCrlPath(crlPath);
    return this;
  }

  @Override
  public HttpServerOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    super.addCrlValue(crlValue);
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
  public HttpServerOptions setKeyStore(KeyStoreOptions keyStore) {
    super.setKeyStore(keyStore);
    return this;
  }

  @Override
  public HttpServerOptions setTrustStore(TrustStoreOptions trustStore) {
    super.setTrustStore(trustStore);
    return this;
  }

  @Override
  public HttpServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

}
