package org.nodex.examples.net;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;
import org.nodex.core.parsetools.RecordParser;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 09:19
 * To change this template use File | Settings | File Templates.
 */
public class WhyServer {
  public static void main(String[] args) throws Exception {
    NetServer.createServer(new Callback<NetSocket>() {
      public void onEvent(final NetSocket socket) {
        socket.data(RecordParser.newDelimited("\n", "UTF-8", new Callback<Buffer>() {
          public void onEvent(Buffer buffer) {
            String line = buffer.toString("UTF-8");
            line = "Why? ".concat(line).concat("\n");
            socket.write(Buffer.fromString(line, "UTF-8"));
          }
        }));
      }
    }).listen(8080, "localhost");
    System.out.println("Any key to exit");
    System.in.read();
  }
}
