package org.nodex.core.buffer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * Created by IntelliJ IDEA.
 * User: tfox
 * Date: 24/06/11
 * Time: 16:46
 * To change this template use File | Settings | File Templates.
 */
public class Buffer {
    private ChannelBuffer buffer;

    public Buffer(ChannelBuffer buffer) {
        this.buffer = buffer;
    }

    public Buffer(int size) {
      buffer = ChannelBuffers.buffer(size);
    }

    public Buffer(byte[] bytes) {
      buffer = ChannelBuffers.wrappedBuffer(bytes);
    }

    public Buffer(String str, String enc) {
       buffer = ChannelBuffers.copiedBuffer(str, Charset.forName(enc));
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

    public String toString(String enc) {
      return buffer.toString(Charset.forName(enc));
    }

    public ChannelBuffer _toChannelBuffer() {
        return buffer;
    }


}
