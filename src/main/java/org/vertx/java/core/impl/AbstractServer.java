/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.impl;

import org.vertx.java.core.Server;
import org.vertx.java.core.net.impl.TCPSSLHelper;
import org.vertx.java.core.net.impl.TCPSSLHelper.ClientAuth;

import javax.net.ssl.SSLContext;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class AbstractServer<S> implements Server<S> {

  private final VertxInternal vertx;
  private final Context ctx;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();

  protected AbstractServer(VertxInternal vertx) {
    this.vertx = vertx;
    ctx = vertx.getOrAssignContext();
    if (vertx.isWorker()) {
      throw new IllegalStateException("Cannot be used in a worker application");
    }
    ctx.addCloseHook(new Runnable() {
      public void run() {
        close();
      }
    });
  }
  
  protected VertxInternal getVertxInternal() {
	  return vertx;
  }
  
  protected Context getContext() {
	  return ctx;
  }

  protected TCPSSLHelper getTCPSSLHelper() {
	  return tcpHelper;
  }
  
  public final Boolean isTCPNoDelay() {
    return tcpHelper.isTCPNoDelay();
  }

  public final Integer getSendBufferSize() {
    return tcpHelper.getSendBufferSize();
  }

  public final Integer getReceiveBufferSize() {
    return tcpHelper.getReceiveBufferSize();
  }

  public final Boolean isTCPKeepAlive() {
    return tcpHelper.isTCPKeepAlive();
  }

  public final Boolean isReuseAddress() {
    return tcpHelper.isReuseAddress();
  }

  public final Boolean isSoLinger() {
    return tcpHelper.isSoLinger();
  }

  public final Integer getTrafficClass() {
    return tcpHelper.getTrafficClass();
  }

  public final Integer getAcceptBacklog() {
    return tcpHelper.getAcceptBacklog();
  }

  public final Server<S> setTCPNoDelay(boolean tcpNoDelay) {
    tcpHelper.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  public final Server<S> setSendBufferSize(int size) {
    tcpHelper.setSendBufferSize(size);
    return this;
  }

  public final Server<S> setReceiveBufferSize(int size) {
    tcpHelper.setReceiveBufferSize(size);
    return this;
  }

  public final Server<S> setTCPKeepAlive(boolean keepAlive) {
    tcpHelper.setTCPKeepAlive(keepAlive);
    return this;
  }

  public final Server<S> setReuseAddress(boolean reuse) {
    tcpHelper.setReuseAddress(reuse);
    return this;
  }

  public final Server<S> setSoLinger(boolean linger) {
    tcpHelper.setSoLinger(linger);
    return this;
  }

  public final Server<S> setTrafficClass(int trafficClass) {
    tcpHelper.setTrafficClass(trafficClass);
    return this;
  }

  public final Server<S> setAcceptBacklog(int backlog) {
    tcpHelper.setAcceptBacklog(backlog);
    return this;
  }

  public final boolean isSSL() {
    return tcpHelper.isSSL();
  }

  public final String getKeyStorePath() {
    return tcpHelper.getKeyStorePath();
  }

  public final String getKeyStorePassword() {
    return tcpHelper.getKeyStorePassword();
  }

  public final String getTrustStorePath() {
    return tcpHelper.getTrustStorePath();
  }

  public final String getTrustStorePassword() {
    return tcpHelper.getTrustStorePassword();
  }

  public final TCPSSLHelper.ClientAuth getClientAuth() {
    return tcpHelper.getClientAuth();
  }

  public final SSLContext getSSLContext() {
    return tcpHelper.getSSLContext();
  }

  public final Server<S> setSSL(boolean ssl) {
    tcpHelper.setSSL(ssl);
    return this;
  }

  public final Server<S> setKeyStorePath(String path) {
    tcpHelper.setKeyStorePath(path);
    return this;
  }

  public final Server<S> setKeyStorePassword(String pwd) {
    tcpHelper.setKeyStorePassword(pwd);
    return this;
  }

  public final Server<S> setTrustStorePath(String path) {
    tcpHelper.setTrustStorePath(path);
    return this;
  }

  public final Server<S> setTrustStorePassword(String pwd) {
    tcpHelper.setTrustStorePassword(pwd);
    return this;
  }

  public final Server<S> setClientAuthRequired(boolean required) {
    tcpHelper.setClientAuthRequired(required);
    return this;
  }

}
