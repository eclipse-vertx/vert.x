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
     //Need to duplicate the buffer since frame can be written to multiple connections concurrently
     //which will change the internal Netty readerIndex
     socket.write(frame.toBuffer().duplicate());
   }

   public void data(Callback<Frame> frameCallback) {
     socket.data(new Parser(frameCallback));
   }

   public void close() {
     socket.close();
   }

}
