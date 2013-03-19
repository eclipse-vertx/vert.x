package org.vertx.java.core.http.impl.ws;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@ChannelHandler.Sharable
public class WebSocketConvertHandler extends MessageToMessageCodec<io.netty.handler.codec.http.websocketx.WebSocketFrame, WebSocketFrame> {

  public static final WebSocketConvertHandler INSTANCE = new WebSocketConvertHandler();

  @Override
  protected Object encode(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
    switch (msg.getType()) {
      case BINARY:
        return new BinaryWebSocketFrame(msg.getBinaryData());
      case TEXT:
        return new TextWebSocketFrame(msg.getBinaryData());
      case CLOSE:
        return new CloseWebSocketFrame(true, 0, msg.getBinaryData());
      case CONTINUATION:
        return new ContinuationWebSocketFrame(msg.getBinaryData());
      case PONG:
        return new PongWebSocketFrame(msg.getBinaryData());
      case PING:
        return new PingWebSocketFrame(msg.getBinaryData());
      default:
        throw new IllegalStateException("Unsupported websocket msg " + msg);
    }
  }

  @Override
  protected Object decode(ChannelHandlerContext ctx, io.netty.handler.codec.http.websocketx.WebSocketFrame msg) throws Exception {
    if (msg instanceof BinaryWebSocketFrame) {
      return new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, msg.data().copy());
    }
    if (msg instanceof CloseWebSocketFrame) {
      return new DefaultWebSocketFrame(WebSocketFrame.FrameType.CLOSE, msg.data().copy());
    }
    if (msg instanceof PingWebSocketFrame) {
      return new DefaultWebSocketFrame(WebSocketFrame.FrameType.PING, msg.data().copy());
    }
    if (msg instanceof PongWebSocketFrame) {
      return new DefaultWebSocketFrame(WebSocketFrame.FrameType.PONG, msg.data().copy());
    }
    if (msg instanceof TextWebSocketFrame) {
      return new DefaultWebSocketFrame(WebSocketFrame.FrameType.TEXT, msg.data().copy());
    }
    if (msg instanceof ContinuationWebSocketFrame) {
      return new DefaultWebSocketFrame(WebSocketFrame.FrameType.CONTINUATION, msg.data().copy());
    }
    throw new IllegalStateException("Unsupported websocket msg " + msg);
  }
}
