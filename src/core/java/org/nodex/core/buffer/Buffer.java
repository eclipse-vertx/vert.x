package org.nodex.core.buffer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class Buffer {
  private ChannelBuffer buffer;

  public static Buffer newFixed(int size) {
    return new Buffer(ChannelBuffers.buffer(size));
  }

  public static Buffer newDynamic(int size) {
    return new Buffer(ChannelBuffers.dynamicBuffer(size));
  }

  public static Buffer fromChannelBuffer(ChannelBuffer buffer) {
    return new Buffer(buffer);
  }

  public static Buffer newWrapped(byte[] bytes) {
    return new Buffer(ChannelBuffers.wrappedBuffer(bytes));
  }

  public static Buffer fromString(String str, String enc) {
    return new Buffer(ChannelBuffers.copiedBuffer(str, Charset.forName(enc)));
  }

  public static Buffer fromString(String str) {
    return Buffer.fromString(str, "UTF-8");
  }

  public Buffer(ChannelBuffer buffer) {
    this.buffer = buffer;
  }

  public void write(String str, int offset, String enc) {
    try {
      byte[] bytes = str.getBytes(enc);
      buffer.writerIndex(offset);
      buffer.writeBytes(bytes);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public byte byteAt(int pos) {
    return buffer.getByte(pos);
  }

  public String toString() {
    return buffer.toString(Charset.forName("UTF-8"));
  }

  public String toString(String enc) {
    return buffer.toString(Charset.forName(enc));
  }

  public Buffer append(Buffer buff) {
    buffer.writeBytes(buff._toChannelBuffer());
    return this;
  }

  public Buffer append(byte[] bytes) {
    buffer.writeBytes(bytes);
    return this;
  }

  public Buffer append(byte b) {
    buffer.writeByte(b);
    return this;
  }

  public Buffer append(String str, String enc) {
    try {
      byte[] bytes = str.getBytes(enc);
      buffer.writeBytes(bytes);
      return this;
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public int length() {
    return buffer.writerIndex();
  }

  public Buffer slice(int start, int end) {
    return new Buffer(buffer.slice(start, end - start));
  }

  public Buffer copy(int start, int end) {
    return new Buffer(buffer.copy(start, end - start));
  }

  public ChannelBuffer _toChannelBuffer() {
    return buffer;
  }
}
