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

package io.vertx.core.net;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.gen.Options;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class NetServerOptions extends TCPOptions {

  // Server specific HTTP stuff

  private static final int DEFAULT_PORT = 0;
  private static final String DEFAULT_HOST = "0.0.0.0";
  private static final int DEFAULT_ACCEPT_BACKLOG = 1024;

  private int port;
  private String host;
  private int acceptBacklog;

  // Server specific SSL stuff

  private boolean clientAuthRequired;
  private ArrayList<String> crlPaths;
  private ArrayList<Buffer> crlValues;

  public NetServerOptions() {
    super();
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.acceptBacklog = DEFAULT_ACCEPT_BACKLOG;
    this.crlPaths = new ArrayList<>();
    this.crlValues = new ArrayList<>();
  }

  public NetServerOptions(JsonObject json) {
    super(json);
    this.port = json.getInteger("port", DEFAULT_PORT);
    this.host = json.getString("host", DEFAULT_HOST);
    this.acceptBacklog = json.getInteger("acceptBacklog", DEFAULT_ACCEPT_BACKLOG);
  }

  public NetServerOptions(NetServerOptions other) {
    super(other);
    this.port = other.port;
    this.host = other.host;
    this.acceptBacklog = other.acceptBacklog;
    this.crlPaths = new ArrayList<>(other.crlPaths);
    this.crlValues = new ArrayList<>(other.crlValues);
  }

  public boolean isClientAuthRequired() {
    return clientAuthRequired;
  }

  public NetServerOptions setClientAuthRequired(boolean clientAuthRequired) {
    this.clientAuthRequired = clientAuthRequired;
    return this;
  }

  public List<String> getCrlPaths() {
    return crlPaths;
  }

  public NetServerOptions addCrlPath(String crlPath) throws NullPointerException {
    if (crlPath == null) {
      throw new NullPointerException("No null crl accepted");
    }
    crlPaths.add(crlPath);
    return this;
  }

  public List<Buffer> getCrlValues() {
    return crlValues;
  }

  public NetServerOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    if (crlValue == null) {
      throw new NullPointerException("No null crl accepted");
    }
    crlValues.add(crlValue);
    return this;
  }

  public int getAcceptBacklog() {
    return acceptBacklog;
  }

  public NetServerOptions setAcceptBacklog(int acceptBacklog) {
    this.acceptBacklog = acceptBacklog;
    return this;
  }

  public int getPort() {
    return port;
  }

  public NetServerOptions setPort(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port p must be in range 0 <= p <= 65535");
    }
    this.port = port;
    return this;
  }

  public String getHost() {
    return host;
  }

  public NetServerOptions setHost(String host) {
    this.host = host;
    return this;
  }

  // Override common implementation

  @Override
  public NetServerOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public NetServerOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public NetServerOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public NetServerOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public NetServerOptions setTcpNoDelay(boolean tcpNoDelay) {
    super.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public NetServerOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    super.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  @Override
  public NetServerOptions setSoLinger(int soLinger) {
    super.setSoLinger(soLinger);
    return this;
  }

  @Override
  public NetServerOptions setUsePooledBuffers(boolean usePooledBuffers) {
    super.setUsePooledBuffers(usePooledBuffers);
    return this;
  }

  @Override
  public NetServerOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public NetServerOptions setKeyStore(KeyStoreOptions keyStore) {
    super.setKeyStore(keyStore);
    return this;
  }

  @Override
  public NetServerOptions setTrustStore(TrustStoreOptions trustStore) {
    super.setTrustStore(trustStore);
    return this;
  }

  @Override
  public NetServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }


}
