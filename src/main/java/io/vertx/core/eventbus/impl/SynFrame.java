package io.vertx.core.eventbus.impl;

import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

public class SynFrame implements Frame {

  public static final MessageCodec<SynFrame, SynFrame> CODEC = new MessageCodec<>() {
    @Override
    public void encodeToWire(Buffer buffer, SynFrame synFrame) {
      byte[] strBytes = synFrame.src.getBytes(CharsetUtil.UTF_8);
      buffer.appendInt(strBytes.length);
      buffer.appendBytes(strBytes);
      byte[] dstBytes = synFrame.dst.getBytes(CharsetUtil.UTF_8);
      buffer.appendInt(dstBytes.length);
      buffer.appendBytes(dstBytes);
    }

    @Override
    public SynFrame decodeFromWire(int pos, Buffer buffer) {
      int length = buffer.getInt(pos);
      pos += 4;
      byte[] bytes = buffer.getBytes(pos, pos + length);
      String src = new String(bytes, CharsetUtil.UTF_8);
      pos += length;
      length = buffer.getInt(pos);
      pos += 4;
      bytes = buffer.getBytes(pos, pos + length);
      String dst = new String(bytes, CharsetUtil.UTF_8);
      pos += length;
      return new SynFrame(src, dst);
    }

    @Override
    public SynFrame transform(SynFrame synFrame) {
      return synFrame;
    }

    @Override
    public String name() {
      return "frame.syn";
    }

    @Override
    public byte systemCodecID() {
      return 18;
    }
  };

  final String src;
  final String dst;

  public SynFrame(String src, String dst) {
    this.src = src;
    this.dst = dst;
  }
}
