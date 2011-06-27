package org.nodex.core.http;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:12
 * To change this template use File | Settings | File Templates.
 */
public class Server {

  public static Server createServer(HttpCallback callback) {
    return new Server(callback);
  }

  public void listen(int port, String host) {
  }

  public void listen(int port) {
    listen(port, "0.0.0.0");
  }

  public void stop() {
  }

  private final HttpCallback callback;

  private  Server(HttpCallback callback) {
    this.callback = callback;
  }
}
