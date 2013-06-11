package org.vertx.java.core.http.impl.ws;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MessageList;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.*;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@ChannelHandler.Sharable
public class WebSocketConvertHandler extends MessageToMessageCodec<io.netty.handler.codec.http.websocketx.WebSocketFrame, WebSocketFrame> {

  public static final WebSocketConvertHandler INSTANCE = new WebSocketConvertHandler();

  @Override
  protected void encode(ChannelHandlerContext ctx, WebSocketFrame msg, MessageList<Object> out) throws Exception {
    // retain message as we will re-use the data contained in it
    ByteBufUtil.retain(msg);
    switch (msg.getType()) {
      case BINARY:
        out.add(new BinaryWebSocketFrame(msg.getBinaryData()));
        return;
      case TEXT:
        out.add(new TextWebSocketFrame(msg.getBinaryData()));
        return;
      case CLOSE:
        out.add(new CloseWebSocketFrame(true, 0, msg.getBinaryData()));
        return;
      case CONTINUATION:
        out.add(new ContinuationWebSocketFrame(msg.getBinaryData()));
        return;
      case PONG:
        out.add(new PongWebSocketFrame(msg.getBinaryData()));
        return;
      case PING:
        out.add(new PingWebSocketFrame(msg.getBinaryData()));
        return;
      default:
        throw new IllegalStateException("Unsupported websocket msg " + msg);
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, io.netty.handler.codec.http.websocketx.WebSocketFrame msg, MessageList<Object> out) throws Exception {
    if (msg instanceof BinaryWebSocketFrame) {
      out.add(new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, msg.content().retain()));
      return;
    }
    if (msg instanceof CloseWebSocketFrame) {
      out.add(new DefaultWebSocketFrame(WebSocketFrame.FrameType.CLOSE, msg.content().retain()));
      return;
    }
    if (msg instanceof PingWebSocketFrame) {
      out.add(new DefaultWebSocketFrame(WebSocketFrame.FrameType.PING, msg.content().retain()));
      return;
    }
    if (msg instanceof PongWebSocketFrame) {
      out.add(new DefaultWebSocketFrame(WebSocketFrame.FrameType.PONG, msg.content().retain()));
      return;
    }
    if (msg instanceof TextWebSocketFrame) {
      out.add(new DefaultWebSocketFrame(WebSocketFrame.FrameType.TEXT, msg.content().retain()));
      return;
    }
    if (msg instanceof ContinuationWebSocketFrame) {
      out.add(new DefaultWebSocketFrame(WebSocketFrame.FrameType.CONTINUATION, msg.content().retain()));
      return;
    }
    throw new IllegalStateException("Unsupported websocket msg " + msg);
  }
}
