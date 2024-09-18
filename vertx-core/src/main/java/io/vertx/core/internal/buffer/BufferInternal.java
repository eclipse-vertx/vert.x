package io.vertx.core.internal.buffer;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.net.impl.VertxHandler;

import java.nio.ByteBuffer;
import java.util.Objects;

public interface BufferInternal extends Buffer {

  /**
   * Create a new Vert.x buffer from a Netty {@code ByteBuf}. Pooled {@code byteBuf} are copied and released,
   * otherwise it is wrapped.
   *
   * @param byteBuf the buffer
   * @return a Vert.x buffer to use
   */
  static BufferInternal safeBuffer(ByteBuf byteBuf) {
    return buffer(VertxHandler.safeBuffer(byteBuf));
  }

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
  static BufferInternal buffer(ByteBuf byteBuf) {
    Objects.requireNonNull(byteBuf);
    return new BufferImpl(byteBuf);
  }

  static BufferInternal buffer(int initialSizeHint) {
    return new BufferImpl(initialSizeHint);
  }

  static BufferInternal buffer() {
    return new BufferImpl();
  }

  static BufferInternal buffer(String str) {
    return new BufferImpl(str);
  }

  static BufferInternal buffer(String str, String enc) {
    return new BufferImpl(str, enc);
  }

  static BufferInternal buffer(byte[] bytes) {
    return new BufferImpl(bytes);
  }

  @Override
  BufferInternal appendBuffer(Buffer buff);

  @Override
  BufferInternal appendBuffer(Buffer buff, int offset, int len);

  @Override
  BufferInternal appendBytes(byte[] bytes);

  @Override
  BufferInternal appendBytes(byte[] bytes, int offset, int len);

  @Override
  BufferInternal appendByte(byte b);

  @Override
  BufferInternal appendUnsignedByte(short b);

  @Override
  BufferInternal appendInt(int i);

  @Override
  BufferInternal appendIntLE(int i);

  @Override
  BufferInternal appendUnsignedInt(long i);

  @Override
  BufferInternal appendUnsignedIntLE(long i);

  @Override
  BufferInternal appendMedium(int i);

  @Override
  BufferInternal appendMediumLE(int i);

  @Override
  BufferInternal appendLong(long l);

  @Override
  BufferInternal appendLongLE(long l);

  @Override
  BufferInternal appendShort(short s);

  @Override
  BufferInternal appendShortLE(short s);

  @Override
  BufferInternal appendUnsignedShort(int s);

  @Override
  BufferInternal appendUnsignedShortLE(int s);

  @Override
  BufferInternal appendFloat(float f);

  @Override
  BufferInternal appendFloatLE(float f);

  @Override
  BufferInternal appendDouble(double d);

  @Override
  BufferInternal appendDoubleLE(double d);

  @Override
  BufferInternal appendString(String str, String enc);

  @Override
  BufferInternal appendString(String str);

  @Override
  BufferInternal setByte(int pos, byte b);

  @Override
  BufferInternal setUnsignedByte(int pos, short b);

  @Override
  BufferInternal setInt(int pos, int i);

  @Override
  BufferInternal setIntLE(int pos, int i);

  @Override
  BufferInternal setUnsignedInt(int pos, long i);

  @Override
  BufferInternal setUnsignedIntLE(int pos, long i);

  @Override
  BufferInternal setMedium(int pos, int i);

  @Override
  BufferInternal setMediumLE(int pos, int i);

  @Override
  BufferInternal setLong(int pos, long l);

  @Override
  BufferInternal setLongLE(int pos, long l);

  @Override
  BufferInternal setDouble(int pos, double d);

  @Override
  BufferInternal setDoubleLE(int pos, double d);

  @Override
  BufferInternal setFloat(int pos, float f);

  @Override
  BufferInternal setFloatLE(int pos, float f);

  @Override
  BufferInternal setShort(int pos, short s);

  @Override
  BufferInternal setShortLE(int pos, short s);

  @Override
  BufferInternal setUnsignedShort(int pos, int s);

  @Override
  BufferInternal setUnsignedShortLE(int pos, int s);

  @Override
  BufferInternal setBuffer(int pos, Buffer b);

  @Override
  BufferInternal setBuffer(int pos, Buffer b, int offset, int len);

  @Override
  BufferInternal setBytes(int pos, ByteBuffer b);

  @Override
  BufferInternal setBytes(int pos, byte[] b);

  @Override
  BufferInternal setBytes(int pos, byte[] b, int offset, int len);

  @Override
  BufferInternal setString(int pos, String str);

  @Override
  BufferInternal setString(int pos, String str, String enc);

  @Override
  BufferInternal copy();

  @Override
  BufferInternal slice();

  @Override
  BufferInternal slice(int start, int end);

  /**
   * Returns the Buffer as a Netty {@code ByteBuf}.
   *
   * <p> The returned buffer is a duplicate that maintain its own indices.
   */
  ByteBuf getByteBuf();
}
