package org.vertx.java.core.http.ws.hybi00;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.vertx.java.core.http.ws.WebSocketFrame;
import org.vertx.java.core.logging.Logger;

import static org.vertx.java.core.http.ws.WebSocketFrame.FrameType;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketFrameEncoder00 extends OneToOneEncoder {

  private static final Logger log = Logger.getLogger(WebSocketFrameDecoder00.class);

  protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
    if (msg instanceof WebSocketFrame) {
      WebSocketFrame frame = (WebSocketFrame) msg;

      FrameType frameType = frame.getType();

      switch (frameType) {
        case CLOSE: {
          ChannelBuffer encoded = channel.getConfig().getBufferFactory().getBuffer(2);
          encoded.writeByte(0xFF);
          encoded.writeByte(0x00);
          return encoded;
        }
        case TEXT: {
          ChannelBuffer data = frame.getBinaryData();
          ChannelBuffer encoded = channel.getConfig().getBufferFactory().getBuffer(data.order(), data.readableBytes() + 2);
          encoded.writeByte(0x00);
          encoded.writeBytes(data, data.readableBytes());
          encoded.writeByte((byte) 0xFF);
          return encoded;
        }
        case BINARY: {
          ChannelBuffer data = frame.getBinaryData();
          int dataLen = data.readableBytes();
          ChannelBuffer encoded = channel.getConfig().getBufferFactory().getBuffer(data.order(), dataLen + 5);
          encoded.writeByte((byte) 0x80);
          encoded.writeByte((byte) (dataLen >>> 28 & 0x7F | 0x80));
          encoded.writeByte((byte) (dataLen >>> 14 & 0x7F | 0x80));
          encoded.writeByte((byte) (dataLen >>> 7 & 0x7F | 0x80));
          encoded.writeByte((byte) (dataLen & 0x7F));
          encoded.writeBytes(data, dataLen);
          return encoded;
        }
        default:
          break;
      }
    }
    return msg;
  }
}
