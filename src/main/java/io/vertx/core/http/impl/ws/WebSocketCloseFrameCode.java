package io.vertx.core.http.impl.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;

/**
 * @author Francesco Guardiani @slinkydeveloper
 */
public enum WebSocketCloseFrameCode {

  NORMAL_CLOSE((short)1000, "Connection Closed");

  private short statusCode;
  private String reason;

  WebSocketCloseFrameCode(short statusCode, String reason) {
    this.statusCode = statusCode;
    this.reason = reason;
  }

  public ByteBuf byteBuf() {
    return generateByteBuffer(this.statusCode, this.reason);
  }

  public static ByteBuf generateByteBuffer(short statusCode, String reason) {
    if (reason != null)
      return Unpooled.copiedBuffer(
        Unpooled.copyShort(statusCode), // First two bytes are reserved for status code
        Unpooled.copiedBuffer(reason, Charset.forName("UTF-8"))
      );
    else
      return Unpooled.copyShort(statusCode);
  }

}
