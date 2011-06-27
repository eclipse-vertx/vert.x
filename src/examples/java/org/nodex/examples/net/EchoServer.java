package org.nodex.examples.net;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.net.Socket;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 09:19
 * To change this template use File | Settings | File Templates.
 */
public class EchoServer {
  public static void main(String[] args) throws Exception {
    Net.createServer(new Callback<Socket>() {
      public void onEvent(final Socket socket) {
        socket.data(new Callback<Buffer>() {
          public void onEvent(Buffer buffer) {
            socket.write(buffer);
          }
        });
      }
    }).listen(8080, "localhost");
    System.out.println("Any key to exit");
    System.in.read();
  }
}
