package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.net.Socket;

/**
 * User: tim
 * Date: 27/06/11
 * Time: 18:31
 */
public class Connection {
   private final Socket socket;

   protected Connection(Socket socket) {
     this.socket = socket;
   }

   public void write(Frame frame) {
     socket.write(frame.toBuffer());
   }

   public void data(Callback<Frame> frameCallback) {
     socket.data(new Parser(frameCallback));
   }

   public void close() {
     socket.close();
   }

}
