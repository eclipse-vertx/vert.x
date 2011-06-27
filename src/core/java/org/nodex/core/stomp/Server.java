package org.nodex.core.stomp;


import org.nodex.core.Callback;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:33
 * To change this template use File | Settings | File Templates.
 */
public class Server {

  public static Server createServer(Callback<StompFrame> callback) {
    return new Server(callback);
  }

  public void listen(int port, String host){
  }

  public void close() {
  }

  private final Callback<StompFrame> callback;

  private Server(Callback<StompFrame> callback) {
    this.callback = callback;
  }
}
