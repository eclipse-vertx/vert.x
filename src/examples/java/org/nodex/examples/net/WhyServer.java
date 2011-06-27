package org.nodex.examples.net;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.net.Server;
import org.nodex.core.net.Socket;
import org.nodex.core.parsetools.ParserTools;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 09:19
 * To change this template use File | Settings | File Templates.
 */
public class WhyServer {
  public static void main(String[] args) throws Exception {
    Server.createServer(new Callback<Socket>() {
      public void onEvent(final Socket socket) {
        socket.data(ParserTools.splitOnDelimiter((byte) '\n', new Callback<Buffer>() {
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
