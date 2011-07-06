package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.net.NetSocket;

/**
 * User: tim
 * Date: 29/06/11
 * Time: 09:37
 */
class StompServerConnection extends StompConnection {
  protected StompServerConnection(NetSocket socket) {
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
