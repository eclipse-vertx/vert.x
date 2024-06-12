package io.vertx.core.spi;

import io.netty.buffer.ByteBuf;
import io.vertx.core.ServiceHelper;
import io.vertx.core.buffer.Buffer;

public interface BufferFactory {

  BufferFactory INSTANCE = ServiceHelper.loadFactory(BufferFactory.class);

  /**
   * <p>
   * Create a new buffer from a Netty {@code ByteBuf}.
   * <i>Note that</i> the returned buffer is backed by given Netty ByteBuf,
   * so changes in the returned buffer are reflected in given Netty ByteBuf, and vice-versa.
   * </p>
   * <p>
   * For example, both buffers in the code below share their data:
   * </p>
   * <pre>
   *   Buffer src = Buffer.buffer();
   *   Buffer clone = Buffer.buffer(src.getByteBuf());
   * </pre>
   *
   * @param byteBuf the Netty ByteBuf
   * @return the buffer
   */
  Buffer buffer(ByteBuf byteBuf);

  Buffer buffer(int initialSizeHint);

  Buffer buffer();

  Buffer buffer(String str);

  Buffer buffer(String str, String enc);

  Buffer buffer(byte[] bytes);

}
