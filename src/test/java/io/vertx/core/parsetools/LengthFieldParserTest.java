package io.vertx.core.parsetools;

import io.vertx.core.buffer.Buffer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.test.core.TestUtils.*;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

public class LengthFieldParserTest {

  String msg = Buffer.buffer("Vert.x is really awesome!").toString();
  Buffer byteField = Buffer.buffer().appendByte((byte) msg.length()).appendString(msg);
  Buffer shortField = Buffer.buffer().appendShort((short) msg.length()).appendString(msg);
  Buffer medField = Buffer.buffer().appendMedium(msg.length()).appendString(msg);
  Buffer intField = Buffer.buffer().appendInt(msg.length()).appendString(msg);
  Buffer longField = Buffer.buffer().appendLong(msg.length()).appendString(msg);
  Buffer preField = Buffer.buffer()
    .appendByte(Byte.MAX_VALUE)
    .appendShort(Short.MAX_VALUE)
    .appendMedium(Integer.MAX_VALUE)
    .appendInt(Integer.MAX_VALUE)
    .appendLong(Integer.MAX_VALUE);

  @Test
  public void testIllegalArgumentsLength() {
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(0, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(-1, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(5, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(6, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(7, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(9, 0, 1, null));
  }

  @Test
  public void testIllegalArgumentsOffset() {
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(1, -1, 1, null));
  }

  @Test
  public void testIllegalArgumentsMax() {
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(1, 0, 0, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(1, 0, -1, null));
  }

  @Test
  public void testByte() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer data = byteField.copy();
    parser.handle(data);
  }

  @Test
  public void testByteOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = byteField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testByteOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = byteField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testByteOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {
    });
    Buffer preFields = preField.copy();
    Buffer data = byteField.copy();
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }

  @Test
  public void testByteOffsetMaxInvalidLengthException() {
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, msg.length(), null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {
    });
    Buffer preFields = preField.copy();
    Buffer data = byteField.copy();
    data.setByte(0, (byte) -1);
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }

  @Test
  public void testShort() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer data = shortField.copy();
    parser.handle(data);
  }

  @Test
  public void testShortOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = shortField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testShortOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = shortField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testShortOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {});
    Buffer preFields = preField.copy();
    Buffer data = shortField.copy();
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }

  @Test
  public void testShortOffsetMaxInvalidLengthException() {
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, msg.length(), null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {});
    Buffer preFields = preField.copy();
    Buffer data = shortField.copy();
    data.setShort(0, (short) -1);
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }

  @Test
  public void testMedium() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer data = medField.copy();
    parser.handle(data);
  }

  @Test
  public void testMediumOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = medField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testMediumOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = medField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testMediumOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {
    });
    Buffer preFields = preField.copy();
    Buffer data = medField.copy();
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }

  @Test
  public void testMediumOffsetMaxInvalidLengthException() {
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, msg.length(), null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {});
    Buffer preFields = preField.copy();
    Buffer data = medField.copy();
    data.setMedium(0, -1);
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }

  @Test
  public void testInt() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer data = intField.copy();
    parser.handle(data);
  }

  @Test
  public void testIntOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = intField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testIntOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = intField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testIntOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {});
    Buffer preFields = preField.copy();
    Buffer data = intField.copy();
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }

  @Test
  public void testIntOffsetMaxInvalidLengthException() {
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, msg.length(), null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {});
    Buffer preFields = preField.copy();
    Buffer data = intField.copy();
    data.setInt(0, -1);
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }

  @Test
  public void testLong() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer data = longField.copy();
    parser.handle(data);
  }

  @Test
  public void testLongOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = longField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testLongOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    Buffer preFields = preField.copy();
    Buffer data = longField.copy();
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testLongOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {});
    Buffer preFields = preField.copy();
    Buffer data = longField.copy();
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }

  @Test
  public void testLongOffsetMaxInvalidLengthException() {
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, msg.length(), null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handler(event -> {});
    Buffer preFields = preField.copy();
    Buffer data = longField.copy();
    data.setLong(0, -1);
    parser.handle(preFields.appendBuffer(data));
    assertEquals(1, errors.size());
  }


  @Test
  public void testStreamHandle() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = byteField.copy();
      Buffer data2 = byteField.copy();
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testStreamPause() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = byteField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testStreamResume() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = byteField.copy();
    Buffer data2 = byteField.copy();
    Buffer data3 = byteField.copy();
    stream.handle(data1.appendBuffer(data2.appendBuffer(data3)));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testStreamFetch() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = byteField.copy();
    Buffer data2 = byteField.copy();
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testStreamPauseInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data1 = byteField.copy();
    stream.handle(data1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testStreamFetchInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = byteField.copy();
    Buffer data2 = byteField.copy();
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

}
