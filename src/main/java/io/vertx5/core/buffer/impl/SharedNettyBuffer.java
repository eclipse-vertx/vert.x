package io.vertx5.core.buffer.impl;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.ByteCursor;
import io.netty5.buffer.api.ComponentIterator;
import io.netty5.buffer.api.ReadableComponent;
import io.netty5.buffer.api.ReadableComponentProcessor;
import io.netty5.buffer.api.WritableComponent;
import io.netty5.buffer.api.WritableComponentProcessor;
import io.netty5.buffer.api.internal.Statics;
import io.netty5.util.Send;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class SharedNettyBuffer implements Buffer {

  private static final int READ_ONLY = 0x01;
  private static final int WRITE_ONLY = 0x02;

  ByteBuffer data;
  private int readerOffset;
  private int writerOffset;
  private int access;

  public SharedNettyBuffer() {
    this.data = ByteBuffer.allocate(1024);
    this.readerOffset = 0;
    this.writerOffset = 0;
    this.access = 0;
  }

  public SharedNettyBuffer(SharedNettyBuffer buffer) {
    this.data = buffer.data;
    this.readerOffset = buffer.readerOffset;
    this.writerOffset = buffer.writerOffset;
    this.access = 0;
  }

  public SharedNettyBuffer(ByteBuffer data) {
    this.data = data;
    this.readerOffset = 0;
    this.writerOffset = 0;
  }

  @Override
  public int capacity() {
    return data.capacity();
  }

  @Override
  public int readerOffset() {
    return readerOffset;
  }

  @Override
  public Buffer readerOffset(int offset) {
    readerOffset = offset;
    return this;
  }

  @Override
  public int writerOffset() {
    return writerOffset;
  }

  @Override
  public Buffer writerOffset(int offset) {
    writerOffset = offset;
    return this;
  }

  @Override
  public Buffer fill(byte value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer makeReadOnly() {
    access |= READ_ONLY;
    return this;
  }

  @Override
  public boolean readOnly() {
    return (access & READ_ONLY) != 0;
  }

  @Override
  public boolean isDirect() {
    return false;
  }

  @Override
  public Buffer implicitCapacityLimit(int limit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int implicitCapacityLimit() {
    return Integer.MAX_VALUE - 8;
  }

  @Override
  public void copyInto(int srcPos, byte[] dest, int destPos, int length) {
    if (srcPos < 0 || length < 0 || destPos < 0 || (srcPos + length) > data.capacity() || (destPos + length) > dest.length) {
      throw new IndexOutOfBoundsException();
    }
    ByteBuffer wrapper = ByteBuffer.wrap(dest, destPos, length);
    data.position(srcPos);
    data.limit(srcPos + length);
    wrapper.put(data);
    data.position(0);
  }

  @Override
  public void copyInto(int srcPos, ByteBuffer dest, int destPos, int length) {
    data.position(srcPos);
    data.limit(srcPos + length);
    int a = dest.position();
    int b = dest.limit();
    dest.position(destPos);
    dest.limit(destPos + length);
    try {
      dest.put(data);
    } finally {
      dest.position(a);
      dest.limit(b);
    }
  }

  @Override
  public void copyInto(int srcPos, Buffer dest, int destPos, int length) {
    data.position(srcPos);
    data.limit(srcPos + length);
    int offset = dest.writerOffset();
    dest.writerOffset(destPos);
    try {
      dest.writeBytes(data);
    } finally {
      dest.writerOffset(offset);
    }
  }

  @Override
  public int transferTo(WritableByteChannel channel, int length) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int transferFrom(FileChannel channel, long position, int length) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int transferFrom(ReadableByteChannel channel, int length) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int bytesBefore(byte needle) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int bytesBefore(Buffer needle) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ByteCursor openCursor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ByteCursor openCursor(int fromOffset, int length) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ByteCursor openReverseCursor(int fromOffset, int length) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer ensureWritable(int size, int minimumGrowth, boolean allowCompaction) {
    int available = writableBytes();
    if (size < available) {
      return this;
    }
    int exp = Math.max(size - available, minimumGrowth);
    int ns = data.capacity() + exp;
    ByteBuffer next = ByteBuffer.allocate(ns);
    data.position(0);
    data.limit(data.capacity());
    next.put(data);
    data = next;
    return this;
  }

  @Override
  public Buffer copy(int offset, int length, boolean readOnly) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer split(int splitOffset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer compact() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int countComponents() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int countReadableComponents() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int countWritableComponents() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E extends Exception> int forEachReadable(int initialIndex, ReadableComponentProcessor<E> processor) throws E {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T extends ReadableComponent & ComponentIterator.Next> ComponentIterator<T> forEachReadable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E extends Exception> int forEachWritable(int initialIndex, WritableComponentProcessor<E> processor) throws E {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T extends WritableComponent & ComponentIterator.Next> ComponentIterator<T> forEachWritable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte readByte() {
    if (readerOffset >= writerOffset) {
      throw new IndexOutOfBoundsException();
    }
    return data.get(readerOffset++);
  }

  @Override
  public byte getByte(int roff) {
    return data.get(roff);
  }

  @Override
  public int readUnsignedByte() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getUnsignedByte(int roff) {
    return getByte(roff) & 0xFF;
  }

  private void checkIncreaseWriterOffset(int amount) {
    ensureWritable(amount, amount, true);
  }

  @Override
  public Buffer writeByte(byte value) {
    checkIncreaseWriterOffset(1);
    data.put(writerOffset++, value);
    return this;
  }

  @Override
  public Buffer setByte(int woff, byte value) {
    data.put(woff, value);
    return this;
  }

  @Override
  public Buffer writeUnsignedByte(int value) {
    checkIncreaseWriterOffset(1);
    data.put(writerOffset++, (byte)(value & 0xFF));
    return this;
  }

  @Override
  public Buffer setUnsignedByte(int woff, int value) {
    data.put(woff, (byte)(value & 0xFF));
    return this;
  }

  @Override
  public char readChar() {
    throw new UnsupportedOperationException();
  }

  @Override
  public char getChar(int roff) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer writeChar(char value) {
    checkIncreaseWriterOffset(1);
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer setChar(int woff, char value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public short readShort() {
    throw new UnsupportedOperationException();
  }

  @Override
  public short getShort(int roff) {
    return data.getShort(roff);
  }

  @Override
  public int readUnsignedShort() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getUnsignedShort(int roff) {
    return data.getShort(roff) & 0xFFFF;
  }

  @Override
  public Buffer writeShort(short value) {
    checkIncreaseWriterOffset(2);
    data.putShort(writerOffset, value);
    writerOffset += 2;
    return this;
  }

  @Override
  public Buffer setShort(int woff, short value) {
    data.putShort(woff, value);
    return this;
  }

  @Override
  public Buffer writeUnsignedShort(int value) {
    checkIncreaseWriterOffset(2);
    data.putShort(writerOffset, (short)(value & 0xFFFF));
    writerOffset += 2;
    return this;
  }

  @Override
  public Buffer setUnsignedShort(int woff, int value) {
    data.putShort(woff, (short)(value & 0xFFFF));
    return this;
  }

  @Override
  public int readMedium() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getMedium(int roff) {
    return data.get(roff) << 16 | (data.get(roff + 1) & 0xFF) << 8 | data.get(roff + 2) & 0xFF;
  }

  @Override
  public int readUnsignedMedium() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getUnsignedMedium(int roff) {
    return (data.get(roff) << 16 | (data.get(roff + 1) & 0xFF) << 8 | data.get(roff + 2) & 0xFF) & 0xFFFFFF;
  }

  @Override
  public Buffer writeMedium(int value) {
    checkIncreaseWriterOffset(3);
    data.put(writerOffset++, (byte)((value & 0xFF0000) >> 16));
    data.put(writerOffset++, (byte)((value & 0xFF00) >> 8));
    data.put(writerOffset++, (byte)((value & 0xFF)));
    return this;
  }

  @Override
  public Buffer setMedium(int woff, int value) {
    data.put(woff, (byte)((value & 0xFF0000) >> 16));
    data.put(woff + 1, (byte)((value & 0xFF00) >> 8));
    data.put(woff + 2, (byte)((value & 0xFF)));
    return this;
  }

  @Override
  public Buffer writeUnsignedMedium(int value) {
    checkIncreaseWriterOffset(3);
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer setUnsignedMedium(int woff, int value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int readInt() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getInt(int roff) {
    return data.getInt(roff);
  }

  @Override
  public long readUnsignedInt() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getUnsignedInt(int roff) {
    return data.getInt(roff) & 0XFFFFFFFFL;
  }

  @Override
  public Buffer writeInt(int value) {
    checkIncreaseWriterOffset(4);
    data.putInt(writerOffset, value);
    writerOffset += 4;
    return this;
  }

  @Override
  public Buffer setInt(int woff, int value) {
    data.putInt(woff, value);
    return this;
  }

  @Override
  public Buffer writeUnsignedInt(long value) {
    checkIncreaseWriterOffset(4);
    data.putInt(writerOffset, (int)(value & 0xFFFFFFFFL));
    writerOffset += 4;
    return this;
  }

  @Override
  public Buffer setUnsignedInt(int woff, long value) {
    data.putInt(woff, (int)(value & 0xFFFFFFFFL));
    return this;
  }

  @Override
  public float readFloat() {
    throw new UnsupportedOperationException();
  }

  @Override
  public float getFloat(int roff) {
    return data.getFloat(roff);
  }

  @Override
  public Buffer writeFloat(float value) {
    checkIncreaseWriterOffset(4);
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer setFloat(int woff, float value) {
    data.putFloat(woff, value);
    return this;
  }

  @Override
  public long readLong() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLong(int roff) {
    return data.getLong(roff);
  }

  @Override
  public Buffer writeLong(long value) {
    checkIncreaseWriterOffset(8);
    data.putLong(writerOffset, value);
    writerOffset += 8;
    return this;
  }

  @Override
  public Buffer setLong(int woff, long value) {
    data.putLong(woff, value);
    return this;
  }

  @Override
  public double readDouble() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getDouble(int roff) {
    return data.getDouble(roff);
  }

  @Override
  public Buffer writeDouble(double value) {
    checkIncreaseWriterOffset(8);
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer setDouble(int woff, double value) {
    data.putDouble(woff, value);
    return this;
  }

  @Override
  public Send<Buffer> send() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {
    
  }

  @Override
  public boolean isAccessible() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Buffer && Statics.equals(this, (Buffer) obj);
  }

  @Override
  public int hashCode() {
    return Statics.hashCode(this);
  }
}
