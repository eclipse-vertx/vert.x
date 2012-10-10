/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * Modified from original form by Tim Fox
 */

package org.vertx.java.core.http.impl.ws;

import org.jboss.netty.channel.*;

public class WebSocketDisconnectionNegotiator implements ChannelDownstreamHandler, ChannelUpstreamHandler {

  @Override
  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
    if (this.closeRequest != null) {
      if (e instanceof MessageEvent) {
        Object message = ((MessageEvent) e).getMessage();
        if (message instanceof WebSocketFrame) {
          WebSocketFrame frame = (WebSocketFrame) message;

          if (frame.getType() == WebSocketFrame.FrameType.CLOSE) {
            ctx.sendDownstream(this.closeRequest);
            return;
          }
        }
      }
    }

    ctx.sendUpstream(e);
  }

  @Override
  public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
    if (e instanceof ChannelStateEvent) {
      ChannelState state = ((ChannelStateEvent) e).getState();
      if (state == ChannelState.OPEN && Boolean.FALSE.equals(((ChannelStateEvent) e).getValue())) {
        closeRequested(ctx, (ChannelStateEvent) e);
        return;
      }
    }

    ctx.sendDownstream(e);
  }

  public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    this.closeRequest = e;
    DefaultWebSocketFrame closeFrame = new DefaultWebSocketFrame(WebSocketFrame.FrameType.CLOSE);
    Channels.write(ctx.getChannel(), closeFrame);
  }

  private ChannelStateEvent closeRequest;
}
