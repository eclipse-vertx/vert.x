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
    .appendByte(Byte.MAX_VALUE) // 1
    .appendShort(Short.MAX_VALUE) // 2
    .appendMedium(Short.MAX_VALUE) // 3
    .appendInt(Integer.MAX_VALUE) // 4
    .appendLong(Long.MAX_VALUE); // 8

  @Test
  public void testIllegalArgumentsLength() {
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(0, 0, true, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(-1, 0, true, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(5, 0, true, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(6, 0, true, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(7, 0, true, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(9, 0, true, 1, null));
  }

  @Test
  public void testIllegalArgumentsOffset() {
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(1, -1, true, 1, null));
  }

  @Test
  public void testIllegalArgumentsMax() {
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(1, 0, true, 0, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(1, 0, true, -1, null));
  }

  @Test
  public void testByteSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, true, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer data = byteField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(data);
  }

  @Test
  public void testByte() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer data = byteField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(data.length(), buf.length());
      assertEquals(data.getByte(0), buf.getByte(0));
      assertEquals(data.getString(1, data.length()), buf.getString(1, buf.length()));
    });
    parser.handle(data);
  }

  @Test
  public void testByteOffsetSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = byteField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testByteOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = byteField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getByte(18), buf.getByte(18));
      assertEquals(all.getString(19, all.length()), buf.getString(19, buf.length()));
      assertEquals(msg.length(), buf.getByte(18));
      assertEquals(msg, buf.getString(19, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testByteOffsetSkipMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = byteField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testByteOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = byteField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getByte(18), buf.getByte(18));
      assertEquals(all.getString(19, all.length()), buf.getString(19, buf.length()));
      assertEquals(msg.length(), buf.getByte(18));
      assertEquals(msg, buf.getString(19, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testByteOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, 5, null);
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
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, msg.length(), null);
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
  public void testShortSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, true, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer data = shortField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(data);
  }

  @Test
  public void testShort() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer data = shortField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(data.length(), buf.length());
      assertEquals(data.getShort(0), buf.getShort(0));
      assertEquals(data.getString(2, data.length()), buf.getString(2, buf.length()));
    });
    parser.handle(data);
  }

  @Test
  public void testShortOffsetSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = shortField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testShortOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = shortField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all, buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getShort(18), buf.getShort(18));
      assertEquals(all.getString(20, all.length()), buf.getString(20, buf.length()));
      assertEquals(msg.length(), buf.getShort(18));
      assertEquals(msg, buf.getString(20, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testShortOffsetMaxSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = shortField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(preFields.appendBuffer(data));
  }

  @Test
  public void testShortOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = shortField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all, buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getShort(18), buf.getShort(18));
      assertEquals(all.getString(20, all.length()), buf.getString(20, buf.length()));
      assertEquals(msg.length(), buf.getShort(18));
      assertEquals(msg, buf.getString(20, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testShortOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, 5, null);
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
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, msg.length(), null);
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
  public void testMediumSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, true, Integer.MAX_VALUE, null);
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
  public void testMedium() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer data = medField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(data, buf);
      assertEquals(data.length(), buf.length());
      assertEquals(data.getMedium(0), buf.getMedium(0));
      assertEquals(data.getString(3, data.length()), buf.getString(3, buf.length()));
      assertEquals(msg, buf.getString(3, buf.length()));
    });
    parser.handle(data);
  }

  @Test
  public void testMediumOffsetSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = medField.copy();
    parser.handle(preFields.appendBuffer(data));

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
  }

  @Test
  public void testMediumOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = medField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all, buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getMedium(18), buf.getMedium(18));
      assertEquals(all.getString(21, all.length()), buf.getString(21, buf.length()));
      assertEquals(msg.length(), buf.getMedium(18));
      assertEquals(msg, buf.getString(21, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testMediumOffsetMaxSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = medField.copy();
    parser.handle(preFields.appendBuffer(data));

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
  }

  @Test
  public void testMediumOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = medField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all, buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getMedium(18), buf.getMedium(18));
      assertEquals(all.getString(21, all.length()), buf.getString(21, buf.length()));
      assertEquals(msg.length(), buf.getMedium(18));
      assertEquals(msg, buf.getString(21, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testMediumOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, 5, null);
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
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, msg.length(), null);
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
  public void testIntSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, true, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer data = intField.copy();
    parser.handle(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
  }

  @Test
  public void testInt() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer data = intField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(data, buf);
      assertEquals(data.length(), buf.length());
      assertEquals(data.getInt(0), buf.getInt(0));
      assertEquals(data.getString(4, data.length()), buf.getString(4, buf.length()));
      assertEquals(msg, buf.getString(4, buf.length()));
    });
    parser.handle(data);
  }

  @Test
  public void testIntOffsetSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = intField.copy();
    parser.handle(preFields.appendBuffer(data));

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
  }

  @Test
  public void testIntOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = intField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all, buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getInt(18), buf.getInt(18));
      assertEquals(all.getString(22, all.length()), buf.getString(22, buf.length()));
      assertEquals(msg.length(), buf.getInt(18));
      assertEquals(msg, buf.getString(22, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testIntOffsetMaxSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = intField.copy();
    parser.handle(preFields.appendBuffer(data));

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
  }

  @Test
  public void testIntOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = intField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all, buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getInt(18), buf.getInt(18));
      assertEquals(all.getString(22, all.length()), buf.getString(22, buf.length()));
      assertEquals(msg.length(), buf.getInt(18));
      assertEquals(msg, buf.getString(22, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testIntOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, 5, null);
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
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, msg.length(), null);
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
  public void testLongSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, true, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer data = longField.copy();
    parser.handle(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
  }

  @Test
  public void testLong() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer data = longField.copy();

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(data, buf);
      assertEquals(data.length(), buf.length());
      assertEquals(data.getLong(0), buf.getLong(0));
      assertEquals(data.getString(8, data.length()), buf.getString(8, buf.length()));
      assertEquals(msg, buf.getString(8, buf.length()));
    });
    parser.handle(data);
  }

  @Test
  public void testLongOffsetSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = longField.copy();
    parser.handle(preFields.appendBuffer(data));

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
  }

  @Test
  public void testLongOffset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = longField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all, buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getLong(18), buf.getLong(18));
      assertEquals(all.getString(26, all.length()), buf.getString(26, buf.length()));
      assertEquals(msg.length(), buf.getLong(18));
      assertEquals(msg, buf.getString(26, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testLongOffsetMaxSkip() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = longField.copy();
    parser.handle(preFields.appendBuffer(data));

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
  }

  @Test
  public void testLongOffsetMax() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());

    Buffer preFields = preField.copy();
    Buffer data = longField.copy();
    Buffer all = preFields.appendBuffer(data);

    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(all, buf);
      assertEquals(all.length(), buf.length());
      assertEquals(all.getByte(0), buf.getByte(0));
      assertEquals(all.getShort(1), buf.getShort(1));
      assertEquals(all.getMedium(3), buf.getMedium(3));
      assertEquals(all.getInt(6), buf.getInt(6));
      assertEquals(all.getLong(10), buf.getLong(10));
      assertEquals(all.getLong(18), buf.getLong(18));
      assertEquals(all.getString(26, all.length()), buf.getString(26, buf.length()));
      assertEquals(msg.length(), buf.getLong(18));
      assertEquals(msg, buf.getString(26, buf.length()));
    });
    parser.handle(all);
  }

  @Test
  public void testLongOffsetMaxException() {
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, 5, null);
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
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, msg.length(), null);
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
  public void testByteStreamHandleSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, true, Integer.MAX_VALUE, stream);
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
  public void testByteStreamHandle() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, false, Integer.MAX_VALUE, stream);
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
  public void testByteStreamHandleOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(byteField.copy());
      Buffer data2 = preField.copy().appendBuffer(byteField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testByteStreamHandleOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(byteField.copy());
      Buffer data2 = preField.copy().appendBuffer(byteField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testByteStreamHandleOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(byteField.copy());
      Buffer data2 = preField.copy().appendBuffer(byteField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testByteStreamHandleOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(byteField.copy());
      Buffer data2 = preField.copy().appendBuffer(byteField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testByteStreamPauseSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = byteField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testByteStreamPauseInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, true, Integer.MAX_VALUE, stream);
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
  public void testByteStreamPause() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = byteField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testByteStreamPauseInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, false, Integer.MAX_VALUE, stream);
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
  public void testByteStreamPauseOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(byteField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testByteStreamPauseInHandlerOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testByteStreamPauseOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(byteField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testByteStreamPauseInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testByteStreamPauseOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(byteField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testByteStreamPauseInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testByteStreamPauseOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(byteField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testByteStreamPauseInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testByteStreamResumeSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, true, Integer.MAX_VALUE, stream);
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
  public void testByteStreamResume() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, false, Integer.MAX_VALUE, stream);
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
  public void testByteStreamResumeOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(byteField.copy());
    Buffer all2 = preField.copy().appendBuffer(byteField.copy());
    Buffer all3 = preField.copy().appendBuffer(byteField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testByteStreamResumeOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(byteField.copy());
    Buffer all2 = preField.copy().appendBuffer(byteField.copy());
    Buffer all3 = preField.copy().appendBuffer(byteField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testByteStreamResumeOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(byteField.copy());
    Buffer all2 = preField.copy().appendBuffer(byteField.copy());
    Buffer all3 = preField.copy().appendBuffer(byteField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testByteStreamResumeOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(byteField.copy());
    Buffer all2 = preField.copy().appendBuffer(byteField.copy());
    Buffer all3 = preField.copy().appendBuffer(byteField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testByteStreamFetchSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, true, Integer.MAX_VALUE, stream);
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
  public void testByteStreamFetchInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, true, Integer.MAX_VALUE, stream);
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

  @Test
  public void testByteStreamFetch() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, false, Integer.MAX_VALUE, stream);
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
  public void testByteStreamFetchInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, false, Integer.MAX_VALUE, stream);
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

  @Test
  public void testByteStreamFetchOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data);
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testByteStreamFetchInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(byteField.copy());
    Buffer data2 = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testByteStreamFetchOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(byteField.copy());
    Buffer data2 = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testByteStreamFetchInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(byteField.copy());
    Buffer data2 = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testByteStreamFetchOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(byteField.copy());
    Buffer data2 = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testByteStreamFetchInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(byteField.copy());
    Buffer data2 = preField.copy().appendBuffer(byteField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamHandleSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = shortField.copy();
      Buffer data2 = shortField.copy();
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testShortStreamHandle() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = shortField.copy();
      Buffer data2 = shortField.copy();
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testShortStreamHandleOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(shortField.copy());
      Buffer data2 = preField.copy().appendBuffer(shortField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testShortStreamHandleOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(shortField.copy());
      Buffer data2 = preField.copy().appendBuffer(shortField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testShortStreamHandleOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(shortField.copy());
      Buffer data2 = preField.copy().appendBuffer(shortField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testShortStreamHandleOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(shortField.copy());
      Buffer data2 = preField.copy().appendBuffer(shortField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testShortStreamPauseSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = shortField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testShortStreamPauseInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data1 = shortField.copy();
    stream.handle(data1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamPause() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = shortField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testShortStreamPauseInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data1 = shortField.copy();
    stream.handle(data1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamPauseOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(shortField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testShortStreamPauseInHandlerOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamPauseOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(shortField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testShortStreamPauseInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamPauseOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(shortField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testShortStreamPauseInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamPauseOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(shortField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testShortStreamPauseInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamResumeSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = shortField.copy();
    Buffer data2 = shortField.copy();
    Buffer data3 = shortField.copy();
    stream.handle(data1.appendBuffer(data2.appendBuffer(data3)));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamResume() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = shortField.copy();
    Buffer data2 = shortField.copy();
    Buffer data3 = shortField.copy();
    stream.handle(data1.appendBuffer(data2.appendBuffer(data3)));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamResumeOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(shortField.copy());
    Buffer all2 = preField.copy().appendBuffer(shortField.copy());
    Buffer all3 = preField.copy().appendBuffer(shortField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamResumeOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(shortField.copy());
    Buffer all2 = preField.copy().appendBuffer(shortField.copy());
    Buffer all3 = preField.copy().appendBuffer(shortField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamResumeOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(shortField.copy());
    Buffer all2 = preField.copy().appendBuffer(shortField.copy());
    Buffer all3 = preField.copy().appendBuffer(shortField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamResumeOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(shortField.copy());
    Buffer all2 = preField.copy().appendBuffer(shortField.copy());
    Buffer all3 = preField.copy().appendBuffer(shortField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamFetchSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = shortField.copy();
    Buffer data2 = shortField.copy();
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamFetchInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = shortField.copy();
    Buffer data2 = shortField.copy();
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamFetch() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = shortField.copy();
    Buffer data2 = shortField.copy();
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamFetchInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = shortField.copy();
    Buffer data2 = shortField.copy();
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamFetchOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data);
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamFetchInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(shortField.copy());
    Buffer data2 = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamFetchOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(shortField.copy());
    Buffer data2 = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamFetchInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(shortField.copy());
    Buffer data2 = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testShortStreamFetchOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(shortField.copy());
    Buffer data2 = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testShortStreamFetchInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(shortField.copy());
    Buffer data2 = preField.copy().appendBuffer(shortField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamHandleSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = medField.copy();
      Buffer data2 = medField.copy();
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testMediumStreamHandle() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = medField.copy();
      Buffer data2 = medField.copy();
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testMediumStreamHandleOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(medField.copy());
      Buffer data2 = preField.copy().appendBuffer(medField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testMediumStreamHandleOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(medField.copy());
      Buffer data2 = preField.copy().appendBuffer(medField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testMediumStreamHandleOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(medField.copy());
      Buffer data2 = preField.copy().appendBuffer(medField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testMediumStreamHandleOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(medField.copy());
      Buffer data2 = preField.copy().appendBuffer(medField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testMediumStreamPauseSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = medField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testMediumStreamPauseInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data1 = medField.copy();
    stream.handle(data1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamPause() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = medField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testMediumStreamPauseInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data1 = medField.copy();
    stream.handle(data1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamPauseOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(medField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testMediumStreamPauseInHandlerOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(medField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamPauseOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(medField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testMediumStreamPauseInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(medField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamPauseOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(medField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testMediumStreamPauseInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(medField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamPauseOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(medField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testMediumStreamPauseInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(medField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamResumeSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = medField.copy();
    Buffer data2 = medField.copy();
    Buffer data3 = medField.copy();
    stream.handle(data1.appendBuffer(data2.appendBuffer(data3)));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamResume() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = medField.copy();
    Buffer data2 = medField.copy();
    Buffer data3 = medField.copy();
    stream.handle(data1.appendBuffer(data2.appendBuffer(data3)));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamResumeOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(medField.copy());
    Buffer all2 = preField.copy().appendBuffer(medField.copy());
    Buffer all3 = preField.copy().appendBuffer(medField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamResumeOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(medField.copy());
    Buffer all2 = preField.copy().appendBuffer(medField.copy());
    Buffer all3 = preField.copy().appendBuffer(medField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamResumeOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(medField.copy());
    Buffer all2 = preField.copy().appendBuffer(medField.copy());
    Buffer all3 = preField.copy().appendBuffer(medField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamResumeOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(medField.copy());
    Buffer all2 = preField.copy().appendBuffer(medField.copy());
    Buffer all3 = preField.copy().appendBuffer(medField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetchSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = medField.copy();
    Buffer data2 = medField.copy();
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetchInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = medField.copy();
    Buffer data2 = medField.copy();
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetch() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = medField.copy();
    Buffer data2 = medField.copy();
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetchInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = medField.copy();
    Buffer data2 = medField.copy();
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetchOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(medField.copy());
    stream.handle(data);
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetchInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(medField.copy());
    Buffer data2 = preField.copy().appendBuffer(medField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetchOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(medField.copy());
    Buffer data2 = preField.copy().appendBuffer(medField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetchInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(medField.copy());
    Buffer data2 = preField.copy().appendBuffer(medField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetchOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(medField.copy());
    Buffer data2 = preField.copy().appendBuffer(medField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testMediumStreamFetchInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(medField.copy());
    Buffer data2 = preField.copy().appendBuffer(medField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamHandleSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = intField.copy();
      Buffer data2 = intField.copy();
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testIntStreamHandle() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = intField.copy();
      Buffer data2 = intField.copy();
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testIntStreamHandleOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(intField.copy());
      Buffer data2 = preField.copy().appendBuffer(intField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testIntStreamHandleOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(intField.copy());
      Buffer data2 = preField.copy().appendBuffer(intField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testIntStreamHandleOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(intField.copy());
      Buffer data2 = preField.copy().appendBuffer(intField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testIntStreamHandleOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(intField.copy());
      Buffer data2 = preField.copy().appendBuffer(intField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testIntStreamPauseSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = intField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testIntStreamPauseInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data1 = intField.copy();
    stream.handle(data1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamPause() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = intField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testIntStreamPauseInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data1 = intField.copy();
    stream.handle(data1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamPauseOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(intField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testIntStreamPauseInHandlerOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(intField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamPauseOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(intField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testIntStreamPauseInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(intField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamPauseOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(intField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testIntStreamPauseInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(intField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamPauseOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(intField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testIntStreamPauseInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(intField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamResumeSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = intField.copy();
    Buffer data2 = intField.copy();
    Buffer data3 = intField.copy();
    stream.handle(data1.appendBuffer(data2.appendBuffer(data3)));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamResume() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = intField.copy();
    Buffer data2 = intField.copy();
    Buffer data3 = intField.copy();
    stream.handle(data1.appendBuffer(data2.appendBuffer(data3)));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamResumeOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(intField.copy());
    Buffer all2 = preField.copy().appendBuffer(intField.copy());
    Buffer all3 = preField.copy().appendBuffer(intField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamResumeOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(intField.copy());
    Buffer all2 = preField.copy().appendBuffer(intField.copy());
    Buffer all3 = preField.copy().appendBuffer(intField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamResumeOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(intField.copy());
    Buffer all2 = preField.copy().appendBuffer(intField.copy());
    Buffer all3 = preField.copy().appendBuffer(intField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamResumeOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(intField.copy());
    Buffer all2 = preField.copy().appendBuffer(intField.copy());
    Buffer all3 = preField.copy().appendBuffer(intField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamFetchSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = intField.copy();
    Buffer data2 = intField.copy();
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamFetchInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = intField.copy();
    Buffer data2 = intField.copy();
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamFetch() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = intField.copy();
    Buffer data2 = intField.copy();
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamFetchInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = intField.copy();
    Buffer data2 = intField.copy();
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamFetchOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(intField.copy());
    stream.handle(data);
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamFetchInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(intField.copy());
    Buffer data2 = preField.copy().appendBuffer(intField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamFetchOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(intField.copy());
    Buffer data2 = preField.copy().appendBuffer(intField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamFetchInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(intField.copy());
    Buffer data2 = preField.copy().appendBuffer(intField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testIntStreamFetchOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(intField.copy());
    Buffer data2 = preField.copy().appendBuffer(intField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testIntStreamFetchInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(intField.copy());
    Buffer data2 = preField.copy().appendBuffer(intField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamHandleSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = longField.copy();
      Buffer data2 = longField.copy();
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testLongStreamHandle() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = longField.copy();
      Buffer data2 = longField.copy();
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testLongStreamHandleOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(longField.copy());
      Buffer data2 = preField.copy().appendBuffer(longField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testLongStreamHandleOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(longField.copy());
      Buffer data2 = preField.copy().appendBuffer(longField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testLongStreamHandleOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(longField.copy());
      Buffer data2 = preField.copy().appendBuffer(longField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testLongStreamHandleOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      Buffer data1 = preField.copy().appendBuffer(longField.copy());
      Buffer data2 = preField.copy().appendBuffer(longField.copy());
      stream.handle(data1.appendBuffer(data2));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void testLongStreamPauseSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = longField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testLongStreamPauseInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data1 = longField.copy();
    stream.handle(data1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamPause() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = longField.copy();
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testLongStreamPauseInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data1 = longField.copy();
    stream.handle(data1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamPauseOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(longField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testLongStreamPauseInHandlerOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(longField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamPauseOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(longField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testLongStreamPauseInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(longField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamPauseOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(longField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testLongStreamPauseInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(longField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamPauseOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(longField.copy());
    parser.handle(data);
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testLongStreamPauseInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    Buffer data = preField.copy().appendBuffer(longField.copy());
    stream.handle(data);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamResumeSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = longField.copy();
    Buffer data2 = longField.copy();
    Buffer data3 = longField.copy();
    stream.handle(data1.appendBuffer(data2.appendBuffer(data3)));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamResume() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = longField.copy();
    Buffer data2 = longField.copy();
    Buffer data3 = longField.copy();
    stream.handle(data1.appendBuffer(data2.appendBuffer(data3)));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamResumeOffsetSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(longField.copy());
    Buffer all2 = preField.copy().appendBuffer(longField.copy());
    Buffer all3 = preField.copy().appendBuffer(longField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamResumeOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(longField.copy());
    Buffer all2 = preField.copy().appendBuffer(longField.copy());
    Buffer all3 = preField.copy().appendBuffer(longField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamResumeOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(longField.copy());
    Buffer all2 = preField.copy().appendBuffer(longField.copy());
    Buffer all3 = preField.copy().appendBuffer(longField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamResumeOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer all1 = preField.copy().appendBuffer(longField.copy());
    Buffer all2 = preField.copy().appendBuffer(longField.copy());
    Buffer all3 = preField.copy().appendBuffer(longField.copy());
    stream.handle(all1.appendBuffer(all2).appendBuffer(all3));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamFetchSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = longField.copy();
    Buffer data2 = longField.copy();
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamFetchInHandlerSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, true, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = longField.copy();
    Buffer data2 = longField.copy();
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamFetch() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = longField.copy();
    Buffer data2 = longField.copy();
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamFetchInHandler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = longField.copy();
    Buffer data2 = longField.copy();
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamFetchOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data = preField.copy().appendBuffer(longField.copy());
    stream.handle(data);
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamFetchInHandlerOffset() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(longField.copy());
    Buffer data2 = preField.copy().appendBuffer(longField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamFetchOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(longField.copy());
    Buffer data2 = preField.copy().appendBuffer(longField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamFetchInHandlerOffsetMaxSkip() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, true, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(longField.copy());
    Buffer data2 = preField.copy().appendBuffer(longField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testLongStreamFetchOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    Buffer data1 = preField.copy().appendBuffer(longField.copy());
    Buffer data2 = preField.copy().appendBuffer(longField.copy());
    stream.handle(data1.appendBuffer(data2));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testLongStreamFetchInHandlerOffsetMax() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, false, msg.length(), stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    Buffer data1 = preField.copy().appendBuffer(longField.copy());
    Buffer data2 = preField.copy().appendBuffer(longField.copy());
    stream.handle(data1.appendBuffer(data2));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }
}
