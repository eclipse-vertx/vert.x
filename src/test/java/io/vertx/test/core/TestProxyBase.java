/**
 * 
 */
package io.vertx.test.core;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 */
public abstract class TestProxyBase {

  protected final String username;
  protected String lastUri;
  protected String forceUri;

  public TestProxyBase(String username) {
    this.username = username;
  }

  /**
   * check the last accessed host:ip
   * 
   * @return the lastUri
   */
  public String getLastUri() {
    return lastUri;
  }

  /**
   * check the last HTTP method
   *
   * @return the last method
   */
  public HttpMethod getLastMethod() {
    throw new UnsupportedOperationException();
  }

  /**
   * force uri to connect to a given string (e.g. "localhost:4443") this is used to simulate a host that only resolves
   * on the proxy
   */
  public void setForceUri(String uri) {
    forceUri = uri;
  }

  public MultiMap getLastRequestHeaders() {
    throw new UnsupportedOperationException();
  }

  public abstract int getPort();
  
  public abstract void start(Vertx vertx, Handler<Void> finishedHandler);
  public abstract void stop();

}
