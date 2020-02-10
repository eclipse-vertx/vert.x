package io.vertx.core.eventbus.impl.codecs;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.clustered.ClusteredMessage;

import java.io.*;


public class GenericMessageCodec<V, T> implements MessageCodec<V, T> {

  @Override
  public void encodeToWire(Buffer buffer, V v) {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(ClusteredMessage.ENCODE_TOWIRE_LENGTH);
    try (ObjectOutputStream object = new ObjectOutputStream(outputStream)) {
      object.writeObject(v);
    } catch (final IOException ex) {
      throw new IllegalArgumentException("Error to encode message using a generic codec. It must implement serializable interface.", ex);
    }
    byte[] bytes = outputStream.toByteArray();
    buffer.appendInt(bytes.length);
    buffer.appendBytes(bytes);
  }

  @Override
  public T decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] bytes = buffer.getBytes(pos, pos + length);
    T object = null;
    ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
    try (ObjectInputStream in = new ObjectInputStream(inputStream)) {
      object = (T)in.readObject();
    } catch (final ClassNotFoundException | IOException ex) {
      throw new IllegalArgumentException("Error to decode message using a generic codec");
    }
    return object;
  }

  @Override
  public T transform(V v) {
    return (T)v;
  }

  @Override
  public String name() {
    return "generic";
  }

  @Override
  public byte systemCodecID() {
    return 16;
  }
}
