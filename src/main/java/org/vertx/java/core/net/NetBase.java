/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net;

import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.vertx.java.core.Vertx;

import javax.net.ssl.SSLContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for net clients or servers
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class NetBase {

  protected boolean ssl;
  protected String keyStorePath;
  protected String keyStorePassword;
  protected String trustStorePath;
  protected String trustStorePassword;
  protected boolean trustAll;

  protected Boolean tcpNoDelay = true;
  protected Integer tcpSendBufferSize;
  protected Integer tcpReceiveBufferSize;
  protected Boolean tcpKeepAlive = true;
  protected Boolean reuseAddress;
  protected Boolean soLinger;
  protected Integer trafficClass;

  protected SSLContext context;
  protected Thread th;
  protected long contextID;

  protected NetBase() {
    Long cid = Vertx.instance.getContextID();
    if (cid == null) {
      throw new IllegalStateException("Can only be used from an event loop");
    }
    this.contextID = cid;
    this.th = Thread.currentThread();
  }

  protected void checkSSL() {
    if (ssl) {
      context = TLSHelper.createContext(keyStorePath, keyStorePassword, trustStorePath, trustStorePassword, trustAll);
    }
  }

  protected void checkThread() {
    // All ops must always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread, actual: " + Thread.currentThread() + " expected: " + th);
    }

    if (Vertx.instance.getContextID() != contextID) {
      throw new IllegalStateException("Invoked with wrong contextID");
    }
  }

  protected enum ClientAuth {
    NONE, REQUEST, REQUIRED
  }

  /*
  Currently Netty does not provide all events for a connection on the same thread - e.g. connection open
  connection bound etc are provided on the acceptor thread.
  In vert.x we must ensure all events are executed on the correct event loop for the context
  So for now we need to do this manually by checking the thread and executing it on the event loop
  thread if it's not the right one.
  This code will go away if Netty acts like a proper event loop.
   */
  protected void runOnCorrectThread(NioSocketChannel nch, Runnable runnable) {
    if (Thread.currentThread() != nch.getWorker().getThread()) {
      nch.getWorker().scheduleOtherTask(runnable);
    } else {
      runnable.run();
    }
  }

  protected Map<String, Object> generateConnectionOptions() {
    Map<String, Object> options = new HashMap<>();
    if (tcpNoDelay != null) {
      options.put("child.tcpNoDelay", tcpNoDelay);
    }
    if (tcpSendBufferSize != null) {
      options.put("child.sendBufferSize", tcpSendBufferSize);
    }
    if (tcpReceiveBufferSize != null) {
      options.put("child.receiveBufferSize", tcpReceiveBufferSize);
    }
    if (reuseAddress != null) {
      options.put("reuseAddress", reuseAddress);
    }
    if (soLinger != null) {
      options.put("child.soLinger", soLinger);
    }
    if (trafficClass != null) {
      options.put("child.trafficClass", trafficClass);
    }
    return options;
  }

  public Boolean isTCPNoDelay() {
    return tcpNoDelay;
  }

  public Integer getSendBufferSize() {
    return tcpSendBufferSize;
  }

  public Integer getReceiveBufferSize() {
    return tcpReceiveBufferSize;
  }

  public Boolean isTCPKeepAlive() {
    return tcpKeepAlive;
  }

  public Boolean isReuseAddress() {
    return reuseAddress;
  }

  public Boolean isSoLinger() {
    return soLinger;
  }

  public Integer getTrafficClass() {
    return trafficClass;
  }

  public boolean isSSL() {
    return ssl;
  }


  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetBase setTCPNoDelay(Boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
    return this;
  }

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetBase setSendBufferSize(Integer size) {
    if (size < 1) {
      throw new IllegalArgumentException("TCP send buffer size must be >= 1");
    }
    this.tcpSendBufferSize = size;
    return this;
  }

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetBase setReceiveBufferSize(Integer size) {
    if (size < 1) {
      throw new IllegalArgumentException("TCP receive buffer size must be >= 1");
    }
    this.tcpReceiveBufferSize = size;
    return this;
  }

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetBase setTCPKeepAlive(Boolean keepAlive) {
    this.tcpKeepAlive = keepAlive;
    return this;
  }

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetBase setReuseAddress(Boolean reuse) {
    this.reuseAddress = reuse;
    return this;
  }

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetBase setSoLinger(Boolean linger) {
    this.soLinger = linger;
    return this;
  }

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetBase setTrafficClass(Integer trafficClass) {
    this.trafficClass = trafficClass;
    return this;
  }


  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetBase setSSL(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public String getKeyStorePath() {
    return keyStorePath;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  public String getTrustStorePath() {
    return trustStorePath;
  }

  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and, if on the server side will contain the server certificate. If
   * on the client side it will contain the client certificate. Client certificates are only required if the server
   * requests client authentication.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetBase setKeyStorePath(String path) {
    this.keyStorePath = path;
    return this;
  }

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetBase setKeyStorePassword(String pwd) {
    this.keyStorePassword = pwd;
    return this;
  }

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and, if on the server side it should contain the certificates of
   * any clients that the server trusts - this is only necessary if client authentication is enabled. If on the
   * client side, it should contain the certificates of any servers the client trusts.
   * If you wish the client to trust all server certificates you can use the {@link NetClientBase#setTrustAll(boolean)} method.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetBase setTrustStorePath(String path) {
    this.trustStorePath = path;
    return this;
  }

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetBase setTrustStorePassword(String pwd) {
    this.trustStorePassword = pwd;
    return this;
  }
}
