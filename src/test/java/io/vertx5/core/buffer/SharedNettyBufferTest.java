package io.vertx5.core.buffer;

import io.vertx5.core.buffer.impl.SharedNettyBuffer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class SharedNettyBufferTest {


  @Test
  public void testWriteByte() {
    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    SharedNettyBuffer buffer = new SharedNettyBuffer(byteBuffer);
    buffer.writeByte((byte)5);
    Assert.assertEquals(1, buffer.readableBytes());
    Assert.assertEquals(5, byteBuffer.get(0));
  }

  @Test
  public void testSetByte() {
    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    SharedNettyBuffer buffer = new SharedNettyBuffer(byteBuffer);
    buffer.writerOffset(1);
    buffer.setByte(0, (byte)5);
    Assert.assertEquals(1, buffer.readableBytes());
    Assert.assertEquals(5, byteBuffer.get(0));
  }

  @Test
  public void testReadByte() {
    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    byteBuffer.put(0, (byte)1);
    SharedNettyBuffer buffer = new SharedNettyBuffer(byteBuffer);
    buffer.writerOffset(1);
    Assert.assertEquals(1, buffer.readByte());
  }
}
