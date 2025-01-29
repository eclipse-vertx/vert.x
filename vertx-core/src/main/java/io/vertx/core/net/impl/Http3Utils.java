package io.vertx.core.net.impl;

import io.netty.buffer.ByteBuf;

class Http3Utils {

  static final long MIN_RESERVED_FRAME_TYPE = 0x1f * 1 + 0x21;

  static void writeVariableLengthInteger(ByteBuf out, long value) {
    int numBytes = numBytesForVariableLengthInteger(value);
    writeVariableLengthInteger(out, value, numBytes);
  }

  static int numBytesForVariableLengthInteger(long value) {
    if (value <= 63) {
      return 1;
    }
    if (value <= 16383) {
      return 2;
    }
    if (value <= 1073741823) {
      return 4;
    }
    if (value <= 4611686018427387903L) {
      return 8;
    }
    throw new IllegalArgumentException();
  }

  static void writeVariableLengthInteger(ByteBuf out, long value, int numBytes) {
    int writerIndex = out.writerIndex();
    switch (numBytes) {
      case 1:
        out.writeByte((byte) value);
        break;
      case 2:
        out.writeShort((short) value);
        encodeLengthIntoBuffer(out, writerIndex, (byte) 0x40);
        break;
      case 4:
        out.writeInt((int) value);
        encodeLengthIntoBuffer(out, writerIndex, (byte) 0x80);
        break;
      case 8:
        out.writeLong(value);
        encodeLengthIntoBuffer(out, writerIndex, (byte) 0xc0);
        break;
      default:
        throw new IllegalArgumentException();
    }
  }

  private static void encodeLengthIntoBuffer(ByteBuf out, int index, byte b) {
    out.setByte(index, out.getByte(index) | b);
  }
}
