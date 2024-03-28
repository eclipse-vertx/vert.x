package io.vertx.core.eventbus.impl;

import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

class FinFrame implements Frame {

  public static final MessageCodec<FinFrame, FinFrame> CODEC = new MessageCodec<>() {
    @Override
    public void encodeToWire(Buffer buffer, FinFrame synFrame) {
      byte[] strBytes = synFrame.addr.getBytes(CharsetUtil.UTF_8);
      buffer.appendInt(strBytes.length);
      buffer.appendBytes(strBytes);
    }

    @Override
    public FinFrame decodeFromWire(int pos, Buffer buffer) {
      int length = buffer.getInt(pos);
      pos += 4;
      byte[] bytes = buffer.getBytes(pos, pos + length);
      String src = new String(bytes, CharsetUtil.UTF_8);
      return new FinFrame(src);
    }

    @Override
    public FinFrame transform(FinFrame finFrame) {
      return finFrame;
    }

    @Override
    public String name() {
      return "frame.fin";
    }

    @Override
    public byte systemCodecID() {
      return 19;
    }
  };

  final String addr;

  public FinFrame(String addr) {
    this.addr = addr;
  }
}
