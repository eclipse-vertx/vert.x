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

package org.vertx.java.core.http;

import org.vertx.java.core.Handler;

import java.util.Map;

/**
 * An HTTP client that maintains a pool of connections to a specific host, at a specific port. The client supports
 * pipelining of requests.<p>
 * As well as HTTP requests, the client can act as a factory for {@code WebSocket websockets}.<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread then
 * and event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances cannot be used from worker verticles.
 *
 *
 * It's better to use the SharedHttpClient if the client will be shared along multiple unrelated code paths, as this
 * interface allows any code path to modify the HttpClient and leave it in a state that another code path
 * may not be expecting. Eventually, this interface should be deprecated and removed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface HttpClient extends SharedHttpClient {

  /**
   * Set an exception handler
   */
  void exceptionHandler(Handler<Exception> handler) ;

  /**
   * Set the maximum pool size<p>
   * The client will maintain up to {@code maxConnections} HTTP connections in an internal pool<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setMaxPoolSize(int maxConnections);

  /**
   * If {@code keepAlive} is {@code true} then, after the request has ended the connection will be returned to the pool
   * where it can be used by another request. In this manner, many HTTP requests can be pipe-lined over an HTTP connection.
   * Keep alive connections will not be closed until the {@link #close() close()} method is invoked.<p>
   * If {@code keepAlive} is {@code false} then a new connection will be created for each request and it won't ever go in the pool,
   * the connection will closed after the response has been received. Even with no keep alive,
   * the client will not allow more than {@link #getMaxPoolSize()} connections to be created at any one time. <p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setKeepAlive(boolean keepAlive);

  /**
   * Set the port that the client will attempt to connect to the server on to {@code port}. The default value is
   * {@code 80}
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setPort(int port);

  /**
   * Set the host that the client will attempt to connect to the server on to {@code host}. The default value is
   * {@code localhost}
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setHost(String host);

  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setSSL(boolean ssl);

  /**
   * If {@code verifyHost} is {@code true}, then the client will try to validate the remote server's certificate
   * hostname against the requested host. Should default to 'true'.
   * This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)} has been set to {@code true}.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setVerifyHost(boolean verifyHost);

  /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and will contain the client certificate. Client certificates are
   * only required if the server requests client authentication.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setKeyStorePath(String path);

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setKeyStorePassword(String pwd);

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and should contain the certificates of any servers that the client trusts.
   * If you wish the client to trust all server certificates you can use the {@link #setTrustAll(boolean)} method.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setTrustStorePath(String path);

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setTrustStorePassword(String pwd);

  /**
   * If you want an SSL client to trust *all* server certificates rather than match them
   * against those in its trust store, you can set this to true.<p>
   * Use this with caution as you may be exposed to "main in the middle" attacks
   * @param trustAll Set to true if you want to trust all server certificates
   */
  HttpClient setTrustAll(boolean trustAll);

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setTCPNoDelay(boolean tcpNoDelay);

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setSendBufferSize(int size);

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setReceiveBufferSize(int size) ;

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setTCPKeepAlive(boolean keepAlive);

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setReuseAddress(boolean reuse);

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code linger}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setSoLinger(boolean linger);

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code trafficClass}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setTrafficClass(int trafficClass);

  /**
   * Set the connect timeout in milliseconds.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setConnectTimeout(long timeout);

  /**
   * Set the number of boss threads to use. Boss threads are used to make connections.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setBossThreads(int threads);

}
