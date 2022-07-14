package io.vertx5.core.buffer.impl;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.ByteCursor;
import io.netty5.buffer.api.ComponentIterator;
import io.netty5.buffer.api.ReadableComponent;
import io.netty5.buffer.api.ReadableComponentProcessor;
import io.netty5.buffer.api.WritableComponent;
import io.netty5.buffer.api.WritableComponentProcessor;
import io.netty5.buffer.api.internal.Statics;
import io.netty5.util.Resource;
import io.netty5.util.Send;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

public class CopyOnWriteNettyBuffer implements Buffer {

  private Buffer buffer;

  public CopyOnWriteNettyBuffer(Buffer buffer) {
    this.buffer = buffer;
  }

  private void mutationCheck() {
    if (buffer.readOnly()) {
      buffer = buffer.copy();
    }
  }

  @Override
  public Send<Buffer> send() {
    if (!buffer.readOnly()) {
      buffer.makeReadOnly();
    }
    return buffer.copy(true).send();
  }

  @Override
  public Buffer split(int splitOffset) {
    return new CopyOnWriteNettyBuffer(buffer.split(splitOffset));
  }

  @Override
  public Buffer split() {
    return new CopyOnWriteNettyBuffer(buffer.split());
  }

  @Override
  public Buffer readSplit(int length) {
    return new CopyOnWriteNettyBuffer(buffer.readSplit(length));
  }

  @Override
  public Buffer writeSplit(int length) {
    return new CopyOnWriteNettyBuffer(buffer.writeSplit(length));
  }

  @Override
  public void close() {
    buffer.close();
  }

  @Override
  public int capacity() {
    return buffer.capacity();
  }

  @Override
  public int readerOffset() {
    return buffer.readerOffset();
  }

  @Override
  public Buffer skipReadableBytes(int delta) {
    mutationCheck();
    return buffer.skipReadableBytes(delta);
  }

  @Override
  public Buffer readerOffset(int offset) {
    mutationCheck();
    return buffer.readerOffset(offset);
  }

  @Override
  public int writerOffset() {
    mutationCheck();
    return buffer.writerOffset();
  }

  @Override
  public Buffer skipWritableBytes(int delta) {
    mutationCheck();
    return buffer.skipWritableBytes(delta);
  }

  @Override
  public Buffer writerOffset(int offset) {
    mutationCheck();
    return buffer.writerOffset(offset);
  }

  @Override
  public int readableBytes() {
    return buffer.readableBytes();
  }

  @Override
  public int writableBytes() {
    return buffer.writableBytes();
  }

  @Override
  public Buffer fill(byte value) {
    mutationCheck();
    return buffer.fill(value);
  }

  @Override
  public Buffer makeReadOnly() {
    return buffer.makeReadOnly();
  }

  @Override
  public boolean readOnly() {
    return buffer.readOnly();
  }

  @Override
  public boolean isDirect() {
    return buffer.isDirect();
  }

  @Override
  public Buffer implicitCapacityLimit(int limit) {
    mutationCheck();
    return buffer.implicitCapacityLimit(limit);
  }

  @Override
  public int implicitCapacityLimit() {
    mutationCheck();
    return buffer.implicitCapacityLimit();
  }

  @Override
  public void copyInto(int srcPos, byte[] dest, int destPos, int length) {
    buffer.copyInto(srcPos, dest, destPos, length);
  }

  @Override
  public void copyInto(int srcPos, ByteBuffer dest, int destPos, int length) {
    buffer.copyInto(srcPos, dest, destPos, length);
  }

  @Override
  public void copyInto(int srcPos, Buffer dest, int destPos, int length) {
    buffer.copyInto(srcPos, dest, destPos, length);
  }

  @Override
  public int transferTo(WritableByteChannel channel, int length) throws IOException {
    return buffer.transferTo(channel, length);
  }

  @Override
  public int transferFrom(FileChannel channel, long position, int length) throws IOException {
    mutationCheck();
    return buffer.transferFrom(channel, position, length);
  }

  @Override
  public int transferFrom(ReadableByteChannel channel, int length) throws IOException {
    mutationCheck();
    return buffer.transferFrom(channel, length);
  }

  @Override
  public Buffer writeCharSequence(CharSequence source, Charset charset) {
    mutationCheck();
    return buffer.writeCharSequence(source, charset);
  }

  @Override
  public CharSequence readCharSequence(int length, Charset charset) {
    mutationCheck();
    return buffer.readCharSequence(length, charset);
  }

  @Override
  public Buffer writeBytes(Buffer source) {
    mutationCheck();
    return buffer.writeBytes(source);
  }

  @Override
  public Buffer writeBytes(byte[] source) {
    mutationCheck();
    return buffer.writeBytes(source);
  }

  @Override
  public Buffer writeBytes(byte[] source, int srcPos, int length) {
    mutationCheck();
    return buffer.writeBytes(source, srcPos, length);
  }

  @Override
  public Buffer writeBytes(ByteBuffer source) {
    mutationCheck();
    return buffer.writeBytes(source);
  }

  @Override
  public Buffer readBytes(ByteBuffer destination) {
    return buffer.readBytes(destination);
  }

  @Override
  public Buffer readBytes(byte[] destination, int destPos, int length) {
    return buffer.readBytes(destination, destPos, length);
  }

  @Override
  public Buffer resetOffsets() {
    mutationCheck();
    return buffer.resetOffsets();
  }

  @Override
  public int bytesBefore(byte needle) {
    return buffer.bytesBefore(needle);
  }

  @Override
  public int bytesBefore(Buffer needle) {
    return buffer.bytesBefore(needle);
  }

  @Override
  public ByteCursor openCursor() {
    return buffer.openCursor();
  }

  @Override
  public ByteCursor openCursor(int fromOffset, int length) {
    return buffer.openCursor(fromOffset, length);
  }

  @Override
  public ByteCursor openReverseCursor() {
    return buffer.openReverseCursor();
  }

  @Override
  public ByteCursor openReverseCursor(int fromOffset, int length) {
    return buffer.openReverseCursor(fromOffset, length);
  }

  @Override
  public Buffer ensureWritable(int size) {
    mutationCheck();
    return buffer.ensureWritable(size);
  }

  @Override
  public Buffer ensureWritable(int size, int minimumGrowth, boolean allowCompaction) {
    mutationCheck();
    return buffer.ensureWritable(size, minimumGrowth, allowCompaction);
  }

  @Override
  public Buffer copy() {
    return buffer.copy();
  }

  @Override
  public Buffer copy(int offset, int length) {
    return buffer.copy(offset, length);
  }

  @Override
  public Buffer copy(boolean readOnly) {
    return buffer.copy(readOnly);
  }

  @Override
  public Buffer copy(int offset, int length, boolean readOnly) {
    return buffer.copy(offset, length, readOnly);
  }

  @Override
  public Buffer compact() {
    mutationCheck();
    return buffer.compact();
  }

  @Override
  public int countComponents() {
    return buffer.countComponents();
  }

  @Override
  public int countReadableComponents() {
    return buffer.countReadableComponents();
  }

  @Override
  public int countWritableComponents() {
    return buffer.countWritableComponents();
  }

  @Override
  public <E extends Exception> int forEachReadable(int initialIndex, ReadableComponentProcessor<E> processor) throws E {
    return buffer.forEachReadable(initialIndex, processor);
  }

  @Override
  public <T extends ReadableComponent & ComponentIterator.Next> ComponentIterator<T> forEachReadable() {
    return buffer.forEachReadable();
  }

  @Override
  public <E extends Exception> int forEachWritable(int initialIndex, WritableComponentProcessor<E> processor) throws E {
    return buffer.forEachWritable(initialIndex, processor);
  }

  @Override
  public <T extends WritableComponent & ComponentIterator.Next> ComponentIterator<T> forEachWritable() {
    return buffer.forEachWritable();
  }

  @Override
  public String toString(Charset charset) {
    return buffer.toString(charset);
  }

  @Override
  public boolean isAccessible() {
    return buffer.isAccessible();
  }

  @Override
  public Buffer touch(Object hint) {
    return buffer.touch(hint);
  }

  public static void touch(Object obj, Object hint) {
    Resource.touch(obj, hint);
  }

  @Override
  public boolean readBoolean() {
    return buffer.readBoolean();
  }

  @Override
  public boolean getBoolean(int roff) {
    return buffer.getBoolean(roff);
  }

  @Override
  public Buffer writeBoolean(boolean value) {
    mutationCheck();
    return buffer.writeBoolean(value);
  }

  @Override
  public Buffer setBoolean(int woff, boolean value) {
    mutationCheck();
    return buffer.setBoolean(woff, value);
  }

  @Override
  public byte readByte() {
    mutationCheck();
    return buffer.readByte();
  }

  @Override
  public byte getByte(int roff) {
    return buffer.getByte(roff);
  }

  @Override
  public int readUnsignedByte() {
    mutationCheck();
    return buffer.readUnsignedByte();
  }

  @Override
  public int getUnsignedByte(int roff) {
    return buffer.getUnsignedByte(roff);
  }

  @Override
  public Buffer writeByte(byte value) {
    mutationCheck();
    return buffer.writeByte(value);
  }

  @Override
  public Buffer setByte(int woff, byte value) {
    mutationCheck();
    return buffer.setByte(woff, value);
  }

  @Override
  public Buffer writeUnsignedByte(int value) {
    mutationCheck();
    return buffer.writeUnsignedByte(value);
  }

  @Override
  public Buffer setUnsignedByte(int woff, int value) {
    mutationCheck();
    return buffer.setUnsignedByte(woff, value);
  }

  @Override
  public char readChar() {
    mutationCheck();
    return buffer.readChar();
  }

  @Override
  public char getChar(int roff) {
    return buffer.getChar(roff);
  }

  @Override
  public Buffer writeChar(char value) {
    mutationCheck();
    return buffer.writeChar(value);
  }

  @Override
  public Buffer setChar(int woff, char value) {
    mutationCheck();
    return buffer.setChar(woff, value);
  }

  @Override
  public short readShort() {
    mutationCheck();
    return buffer.readShort();
  }

  @Override
  public short getShort(int roff) {
    return buffer.getShort(roff);
  }

  @Override
  public int readUnsignedShort() {
    mutationCheck();
    return buffer.readUnsignedShort();
  }

  @Override
  public int getUnsignedShort(int roff) {
    return buffer.getUnsignedShort(roff);
  }

  @Override
  public Buffer writeShort(short value) {
    mutationCheck();
    return buffer.writeShort(value);
  }

  @Override
  public Buffer setShort(int woff, short value) {
    mutationCheck();
    return buffer.setShort(woff, value);
  }

  @Override
  public Buffer writeUnsignedShort(int value) {
    mutationCheck();
    return buffer.writeUnsignedShort(value);
  }

  @Override
  public Buffer setUnsignedShort(int woff, int value) {
    mutationCheck();
    return buffer.setUnsignedShort(woff, value);
  }

  @Override
  public int readMedium() {
    mutationCheck();
    return buffer.readMedium();
  }

  @Override
  public int getMedium(int roff) {
    return buffer.getMedium(roff);
  }

  @Override
  public int readUnsignedMedium() {
    mutationCheck();
    return buffer.readUnsignedMedium();
  }

  @Override
  public int getUnsignedMedium(int roff) {
    return buffer.getUnsignedMedium(roff);
  }

  @Override
  public Buffer writeMedium(int value) {
    mutationCheck();
    return buffer.writeMedium(value);
  }

  @Override
  public Buffer setMedium(int woff, int value) {
    mutationCheck();
    return buffer.setMedium(woff, value);
  }

  @Override
  public Buffer writeUnsignedMedium(int value) {
    mutationCheck();
    return buffer.writeUnsignedMedium(value);
  }

  @Override
  public Buffer setUnsignedMedium(int woff, int value) {
    mutationCheck();
    return buffer.setUnsignedMedium(woff, value);
  }

  @Override
  public int readInt() {
    mutationCheck();
    return buffer.readInt();
  }

  @Override
  public int getInt(int roff) {
    return buffer.getInt(roff);
  }

  @Override
  public long readUnsignedInt() {
    mutationCheck();
    return buffer.readUnsignedInt();
  }

  @Override
  public long getUnsignedInt(int roff) {
    return buffer.getUnsignedInt(roff);
  }

  @Override
  public Buffer writeInt(int value) {
    mutationCheck();
    return buffer.writeInt(value);
  }

  @Override
  public Buffer setInt(int woff, int value) {
    mutationCheck();
    return buffer.setInt(woff, value);
  }

  @Override
  public Buffer writeUnsignedInt(long value) {
    mutationCheck();
    return buffer.writeUnsignedInt(value);
  }

  @Override
  public Buffer setUnsignedInt(int woff, long value) {
    mutationCheck();
    return buffer.setUnsignedInt(woff, value);
  }

  @Override
  public float readFloat() {
    mutationCheck();
    return buffer.readFloat();
  }

  @Override
  public float getFloat(int roff) {
    return buffer.getFloat(roff);
  }

  @Override
  public Buffer writeFloat(float value) {
    mutationCheck();
    return buffer.writeFloat(value);
  }

  @Override
  public Buffer setFloat(int woff, float value) {
    mutationCheck();
    return buffer.setFloat(woff, value);
  }

  @Override
  public long readLong() {
    mutationCheck();
    return buffer.readLong();
  }

  @Override
  public long getLong(int roff) {
    return buffer.getLong(roff);
  }

  @Override
  public Buffer writeLong(long value) {
    mutationCheck();
    return buffer.writeLong(value);
  }

  @Override
  public Buffer setLong(int woff, long value) {
    mutationCheck();
    return buffer.setLong(woff, value);
  }

  @Override
  public double readDouble() {
    mutationCheck();
    return buffer.readDouble();
  }

  @Override
  public double getDouble(int roff) {
    return buffer.getDouble(roff);
  }

  @Override
  public Buffer writeDouble(double value) {
    mutationCheck();
    return buffer.writeDouble(value);
  }

  @Override
  public Buffer setDouble(int woff, double value) {
    mutationCheck();
    return buffer.setDouble(woff, value);
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
