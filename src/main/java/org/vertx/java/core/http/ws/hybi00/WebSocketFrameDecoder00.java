package org.vertx.java.core.http.ws.hybi00;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;
import org.jboss.netty.handler.codec.replay.VoidEnum;
import org.vertx.java.core.http.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.ws.WebSocketFrame;
import org.vertx.java.core.logging.Logger;

import static org.vertx.java.core.http.ws.WebSocketFrame.FrameType;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketFrameDecoder00 extends ReplayingDecoder<VoidEnum> {

  private static final Logger log = Logger.getLogger(WebSocketFrameDecoder00.class);

  public static final int DEFAULT_MAX_FRAME_SIZE = 16384;

  private final int maxFrameSize;

  WebSocketFrameDecoder00() {
    this(DEFAULT_MAX_FRAME_SIZE);
  }

  /**
   * Creates a new instance of {@code WebSocketFrameDecoder} with the specified {@code maxFrameSize}.  If the client
   * sends a frame size larger than {@code maxFrameSize}, the channel will be closed.
   *
   * @param maxFrameSize the maximum frame size to decode
   */
  WebSocketFrameDecoder00(int maxFrameSize) {
    this.maxFrameSize = maxFrameSize;
  }

  @Override
  protected Object decode(ChannelHandlerContext ctx, Channel channel,
                          ChannelBuffer buffer, VoidEnum state) throws Exception {
    byte type = buffer.readByte();
    if ((type & 0x80) == 0x80) {
      // If the MSB on type is set, decode the frame length
      return decodeBinaryFrame(type, buffer);
    } else {
      // Decode a 0xff terminated UTF-8 string
      return decodeTextFrame(type, buffer);
    }
  }

  private WebSocketFrame decodeBinaryFrame(int type, ChannelBuffer buffer) throws TooLongFrameException {
    long frameSize = 0;
    int lengthFieldSize = 0;
    byte b;
    do {
      b = buffer.readByte();
      frameSize <<= 7;
      frameSize |= b & 0x7f;
      if (frameSize > maxFrameSize) {
        throw new TooLongFrameException();
      }
      lengthFieldSize++;
      if (lengthFieldSize > 8) {
        // Perhaps a malicious peer?
        throw new TooLongFrameException();
      }
    } while ((b & 0x80) == 0x80);

    if (frameSize == 0 && type == 0xFF) {
      return new DefaultWebSocketFrame(FrameType.CLOSE);
    }

    return new DefaultWebSocketFrame(FrameType.BINARY, buffer.readBytes((int) frameSize));
  }

  private WebSocketFrame decodeTextFrame(int type, ChannelBuffer buffer) throws TooLongFrameException {
    int ridx = buffer.readerIndex();
    int rbytes = actualReadableBytes();
    int delimPos = buffer.indexOf(ridx, ridx + rbytes, (byte) 0xFF);
    if (delimPos == -1) {
      // Frame delimiter (0xFF) not found
      if (rbytes > maxFrameSize) {
        // Frame length exceeded the maximum
        throw new TooLongFrameException();
      } else {
        // Wait until more data is received
        return null;
      }
    }

    int frameSize = delimPos - ridx;
    if (frameSize > maxFrameSize) {
      throw new TooLongFrameException();
    }

    ChannelBuffer binaryData = buffer.readBytes(frameSize);
    buffer.skipBytes(1);
    DefaultWebSocketFrame frame = new DefaultWebSocketFrame(FrameType.TEXT, binaryData);
    return frame;
  }
}
