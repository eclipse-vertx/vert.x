package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
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

    socket.data(new Callback<Buffer>() {
      public void onEvent(Buffer buff) {

      }
    });
  }

  public void connect() {
    write(Frame.connectFrame());
  }

  public void connect(String username, String password) {
    write(Frame.connectFrame(username, password));
  }

  public void send(String dest, String body) {
    write(Frame.sendFrame(dest, body));
  }

  public void send(String dest, byte[] body) {
    write(Frame.sendFrame(dest, body));
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
