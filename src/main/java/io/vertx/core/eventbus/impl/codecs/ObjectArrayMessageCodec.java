package io.vertx.core.eventbus.impl.codecs;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * Binary codec for Object Array. Uses delegate codec for every element.
 *
 * <p>
 * <strong>Support only System and Default Codecs!</strong>
 *
 * @author <a href="https://github.com/eutkin">Eugene Utkin</a>
 */
public class ObjectArrayMessageCodec implements MessageCodec<Object[], Object[]> {

  private final BiFunction<Object, String, MessageCodec> codecSearcher;
  private final MessageCodec[] systemCodecs;

  public ObjectArrayMessageCodec(BiFunction<Object, String, MessageCodec> codecSearcher, MessageCodec[] systemCodecs) {
    this.codecSearcher = requireNonNull(codecSearcher, "Codec Searcher must not be null");
    this.systemCodecs = requireNonNull(systemCodecs, "System Codec must not be null");
  }

  @Override
  @SuppressWarnings("unchecked")
  public void encodeToWire(Buffer buffer, Object[] objects) {
    buffer.appendInt(objects.length);
    for (Object object : objects) {
      MessageCodec messageCodec = codecSearcher.apply(object, null);
      buffer.appendByte(messageCodec.systemCodecID());
      if (messageCodec.systemCodecID() == -1) {
        buffer.appendInt(messageCodec.name().length());
        buffer.appendString(messageCodec.name());
      }
      Buffer tempBuf = Buffer.buffer();
      messageCodec.encodeToWire(tempBuf, object);
      buffer.appendInt(tempBuf.length());
      buffer.appendBuffer(tempBuf);
    }
  }

  @Override
  public Object[] decodeFromWire(int pos, Buffer buffer) {
    int arrayLength = buffer.getInt(pos);
    pos += 4;
    Object[] result = new Object[arrayLength];
    for (int i = 0; i < arrayLength; i++) {
      int codeSystemId = buffer.getByte(pos);
      pos += 1;
      MessageCodec codec;
      if (codeSystemId > -1) {
        codec = systemCodecs[codeSystemId];
      } else {
        int codecNameLength = buffer.getInt(pos);
        pos += 4;
        int endPos = pos + codecNameLength;
        String codecName = buffer.getString(pos, endPos);
        codec = codecSearcher.apply(null, codecName);
        pos = endPos;
      }
      int dataLength = buffer.getInt(pos);
      pos += 4;
      int endPos = pos + dataLength;
      Buffer data = buffer.getBuffer(pos, endPos);
      pos = endPos;
      Object o = codec.decodeFromWire(0, data);
      result[i] = o;
    }
    return result;
  }

  @Override
  public Object[] transform(Object[] objects) {
    Object[] copied = new Object[objects.length];
    System.arraycopy(objects, 0, copied, 0, objects.length);
    return copied;
  }

  @Override
  public String name() {
    return "object-array";
  }

  @Override
  public byte systemCodecID() {
    return -1;
  }
}
