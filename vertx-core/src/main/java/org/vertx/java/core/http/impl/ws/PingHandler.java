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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;

import static org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType;

@ChannelHandler.Sharable
public class PingHandler extends ChannelInboundMessageHandlerAdapter<WebSocketFrame> {

  @Override
  public void messageReceived(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
    ctx.channel().write(new DefaultWebSocketFrame(FrameType.PONG, frame.getBinaryData()));
  }

  @Override
  public boolean acceptInboundMessage(Object msg) throws Exception {
    if (msg instanceof WebSocketFrame) {
      return ((WebSocketFrame) msg).getType() == FrameType.PING;
    }
    return false;
  }
}
