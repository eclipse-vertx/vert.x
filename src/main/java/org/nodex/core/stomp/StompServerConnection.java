package org.nodex.core.stomp;

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

  private FrameHandler frameHandler;

  protected void frameHandler(FrameHandler frameHandler) {
    this.frameHandler = frameHandler;
  }

  @Override
  protected void handleFrame(Frame frame) {
    frameHandler.onFrame(frame);
  }
}
