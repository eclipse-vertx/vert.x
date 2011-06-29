package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.net.Socket;

import java.util.UUID;

/**
 * User: tim
 * Date: 29/06/11
 * Time: 09:37
 */
class ServerConnection extends Connection {
  protected ServerConnection(Socket socket) {
    super(socket);
  }

  private Callback<Frame> frameHandler;

  protected void frameHandler(Callback<Frame> frameHandler) {
    this.frameHandler = frameHandler;
  }

  @Override
  protected void handleFrame(Frame frame) {
    frameHandler.onEvent(frame);
  }
}
