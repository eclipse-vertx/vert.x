package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.net.impl.TCPSSLHelper;

import java.util.concurrent.TimeUnit;

/**
 * Parameters for creating an instance of an SharedHttpClient. This class is in no way threadsafe and is designed
 * to be used by only a single thread.
 *
 * @author Nathan Pahucki, <a href="mailto:nathan@gmail.com"> nathan@gmail.com</a>
 */
public final class HttpClientParams {


  private final String host;
  private int port = -1;
  private int maxPoolSize = 1;
  private boolean keepAlive;
  private TCPSSLHelper helper;
  private Handler<Exception> exceptionHandler;


  /**
   * Create a new HttpClientParams object, specifying at a minimum, the host to connect to.
   * @param host The host to connect to.
   */
  public HttpClientParams(final String host) {
    this(host, new TCPSSLHelper());
    // NOTE: By forcing the user to specify a host, we don't violate the principal of least surprise - there is no way to forget to set the host.
    assert host != null;
  }

  /**
   * For Unit Testing
   */
  HttpClientParams(final String host, TCPSSLHelper helper) {
    this.host = host;
    this.helper = helper;
  }



  /**
   * Set the maximum pool size<p>
   * The client will maintain up to {@code maxConnections} HTTP connections in an internal pool<p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClientParams setMaxPoolSize(int maxConnections) {
    maxPoolSize = maxConnections;
    return this;
  }

  /**
   * Set the port that the client will attempt to connect to the server on to {@code port}. The default value is
   * {@code 80}
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClientParams setPort(int port) {
    this.port = port;
    return this;
  }

  /**
   * If {@code keepAlive} is {@code true} then, after the request has ended the connection will be returned to the pool
   * where it can be used by another request. In this manner, many HTTP requests can be pipe-lined over an HTTP connection.
   * Keep alive connections will not be closed until the {@link #close() close()} method is invoked.<p>
   * If {@code keepAlive} is {@code false} then a new connection will be created for each request and it won't ever go in the pool,
   * the connection will closed after the response has been received. Even with no keep alive,
   * the client will not allow more than {@link #getMaxPoolSize()} connections to be created at any one time. <p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClientParams setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  /**
   * Sets the exception handler that will be used by the SharedHttpClient. This exception handler will
   * handle general connection errors if there is not exception handler set on a specific request.
   *
   * @param exceptionHandler The handler.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClientParams setExceptionHandler(Handler<Exception> exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
    return this;
  }

  //////////////////////////////////// All setters below here delegated to TCPSSLHelper

  /**
   * Set the number of boss threads to use. Boss threads are used to make connections.
   *
   * @return a reference to this so multiple method calls can be chained together
   */
  public HttpClientParams setBossThreads(int threads) {
    helper.setClientBossThreads(threads);
    return this;
  }

  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClientParams setSSL(boolean ssl) {
    helper.setSSL(ssl);
    return this;
  }

    /**
     * If {@code verifyHost} is {@code true}, then the client will try to validate the remote server's certificate
     * hostname against the requested host. Should default to 'true'.
     * This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)} has been set to {@code true}.
     * @return A reference to this, so multiple invocations can be chained together.
     */
    public HttpClientParams setVerifyHost(boolean verifyHost) {
    helper.setVerifyHost(verifyHost);
    return this;
  }

    /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and will contain the client certificate. Client certificates are
   * only required if the server requests client authentication.<p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClientParams setKeyStorePath(String path) {
    helper.setKeyStorePath(path);
    return this;
  }

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClientParams setKeyStorePassword(String pwd) {
    helper.setKeyStorePassword(pwd);
    return this;
  }

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and should contain the certificates of any servers that the client trusts.
   * If you wish the client to trust all server certificates you can use the {@link #setTrustAll(boolean)} method.<p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClientParams setTrustStorePath(String path) {
    helper.setTrustStorePath(path);
    return this;
  }

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClientParams setTrustStorePassword(String pwd) {
    helper.setTrustStorePassword(pwd);
    return this;
  }

  /**
   * If you want an SSL client to trust *all* server certificates rather than match them
   * against those in its trust store, you can set this to true.<p>
   * Use this with caution as you may be exposed to "main in the middle" attacks
   *
   * @param trustAll Set to true if you want to trust all server certificates
   */
  public HttpClientParams setTrustAll(boolean trustAll) {
    helper.setTrustAll(trustAll);
    return this;
  }

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   *
   * @return a reference to this so multiple method calls can be chained together
   */
  public HttpClientParams setTCPNoDelay(boolean tcpNoDelay) {
    helper.setTCPNoDelay(tcpNoDelay);
    return this;

  }

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   *
   * @return a reference to this so multiple method calls can be chained together
   */
  public HttpClientParams setSendBufferSize(int size) {
    helper.setSendBufferSize(size);
    return this;
  }

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   *
   * @return a reference to this so multiple method calls can be chained together
   */
  public HttpClientParams setReceiveBufferSize(int size) {
    helper.setReceiveBufferSize(size);
    return this;
  }

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   *
   * @return a reference to this so multiple method calls can be chained together
   */
  public HttpClientParams setTCPKeepAlive(boolean keepAlive) {
    helper.setTCPKeepAlive(keepAlive);
    return this;
  }

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   *
   * @return a reference to this so multiple method calls can be chained together
   */
  public HttpClientParams setReuseAddress(boolean reuse) {
    helper.setReuseAddress(reuse);
    return this;
  }

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code linger}.
   *
   * @return a reference to this so multiple method calls can be chained together
   */
  public HttpClientParams setSoLinger(boolean linger) {
    helper.setSoLinger(linger);
    return this;
  }

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code trafficClass}.
   *
   * @return a reference to this so multiple method calls can be chained together
   */
  public HttpClientParams setTrafficClass(int trafficClass) {
    helper.setTrafficClass(trafficClass);
    return this;
  }

  /**
   * Set the connect timeout in milliseconds.
   *
   * @return a reference to this so multiple method calls can be chained together
   */
  public HttpClientParams setConnectTimeout(long timeoutMs) {
    helper.setConnectTimeout(timeoutMs);
    return this;
  }


  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public boolean isKeepAlive() {
    return keepAlive;
  }

  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  public Handler<Exception> getExceptionHandler() {
    return exceptionHandler;
  }

  public TCPSSLHelper getTCPHelper() {
    return helper;
  }

}

