package io.vertx.core.http.impl.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;

/**
 * @author Francesco Guardiani @slinkydeveloper
 */
public class WebSocketCloseFrameCodes {

  public static final ByteBuf CLOSE_1000 = Unpooled.copiedBuffer(
    Unpooled.copyShort(1000),
    Unpooled.copiedBuffer("Connection closed", Charset.forName("UTF-8"))
  );

}
