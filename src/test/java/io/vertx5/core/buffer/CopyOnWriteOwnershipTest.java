package io.vertx5.core.buffer;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.util.Send;
import io.vertx5.core.buffer.impl.CopyOnWriteNettyBuffer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class CopyOnWriteOwnershipTest {

  private static final BufferAllocator ALLOC = BufferAllocator.onHeapUnpooled();

  @Test
  public void testSend() {
    Buffer actual = ALLOC.copyOf("Hello", StandardCharsets.UTF_8);
    Buffer buff = new CopyOnWriteNettyBuffer(actual);
    Send<Buffer> send = buff.send();
    assertEquals(5, buff.readableBytes());
  }

  @Test
  public void testSplit() {
    Buffer actual = ALLOC.copyOf("HelloWorld", StandardCharsets.UTF_8);
    Buffer buff = new CopyOnWriteNettyBuffer(actual);
    Buffer split = buff.split(5);
    assertEquals(5, buff.readableBytes());
    assertEquals(5, split.readableBytes());
    Send<Buffer> send = split.send();
    assertEquals(5, split.readableBytes());
  }

  @Test
  public void testMutate() {
    Buffer actual = ALLOC.copyOf("Hello", StandardCharsets.UTF_8);
    Buffer buff = new CopyOnWriteNettyBuffer(actual);
    // Make readonly
    Send<Buffer> send = buff.send();
    buff.writeByte((byte)'!');
    send = buff.send();
    assertEquals(6, send.receive().readableBytes());
  }
}
