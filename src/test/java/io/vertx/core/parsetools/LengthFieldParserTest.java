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
  public void test_illegal_arguments_length() {
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(0, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(-1, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(5, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(6, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(7, 0, 1, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(9, 0, 1, null));
  }

  @Test
  public void test_illegal_arguments_offset() {
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(1, -1, 1, null));
  }

  @Test
  public void test_illegal_arguments_max() {
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(1, 0, 0, null));
    assertIllegalArgumentException(() -> LengthFieldParser.newParser(1, 0, -1, null));
  }

  @Test
  public void test_byte() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(byteField.copy()));
  }

  @Test
  public void test_byte_offset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(byteField.copy()));
  }

  @Test
  public void test_byte_offset_max() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(byteField.copy()));
  }

  @Test
  public void test_byte_offset_max_exception() {
    LengthFieldParser parser = LengthFieldParser.newParser(1, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(byteField.copy()));
    assertEquals(1, errors.size());
  }

    @Test
    public void test_byte_offset_max_invalid_length_exception() {
      LengthFieldParser parser = LengthFieldParser.newParser(1, 18, msg.length(), null);
      List<Throwable> errors = new ArrayList<>();
      parser.exceptionHandler(errors::add);
      parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(
        byteField.copy().setByte(0, (byte) -1)
      ));
      assertEquals(1, errors.size());
    }

    @Test
    public void test_short() {
      AtomicInteger count = new AtomicInteger();
      LengthFieldParser parser = LengthFieldParser.newParser(2, 0, Integer.MAX_VALUE, null);
      parser.endHandler(v -> count.incrementAndGet());
      parser.handler(buf -> {
        assertNotNull(buf);
        assertEquals(msg.length(), buf.length());
        assertEquals(msg, buf.toString());
      });
      parser.handle(Buffer.buffer().appendBuffer(shortField.copy()));
    }

  @Test
  public void test_short_offset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(shortField.copy()));
  }

  @Test
  public void test_short_offset_max() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(shortField.copy()));
  }

  @Test
  public void test_short_offset_max_exception() {
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(shortField.copy()));
    assertEquals(1, errors.size());
  }

  @Test
  public void test_short_offset_max_invalid_length_exception() {
    LengthFieldParser parser = LengthFieldParser.newParser(2, 18, msg.length(), null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(
      shortField.copy().setShort(0, (short) -1)
    ));
    assertEquals(1, errors.size());
  }

  @Test
  public void test_medium() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 0, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(medField.copy()));
  }

  @Test
  public void test_medium_offset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(medField.copy()));
  }

  @Test
  public void test_medium_offset_max() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(medField.copy()));
  }

  @Test
  public void test_medium_offset_max_exception() {
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(medField.copy()));
    assertEquals(1, errors.size());
  }

  @Test
  public void test_medium_offset_max_invalid_length_exception() {
    LengthFieldParser parser = LengthFieldParser.newParser(3, 18, msg.length(), null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(
      medField.copy().setMedium(0, -1)
    ));
    assertEquals(1, errors.size());
  }


  @Test
  public void test_int() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 0, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(intField.copy()));
  }

  @Test
  public void test_int_offset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(intField.copy()));
  }

  @Test
  public void test_int_offset_max() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(intField.copy()));
  }

  @Test
  public void test_int_offset_max_exception() {
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(intField.copy()));
    assertEquals(1, errors.size());
  }

  @Test
  public void test_int_offset_max_invalid_length_exception() {
    LengthFieldParser parser = LengthFieldParser.newParser(4, 18, msg.length(), null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(
      intField.copy().setInt(0, -1)
    ));
    assertEquals(1, errors.size());
  }

  @Test
  public void test_long() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 0, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(longField.copy()));
  }

  @Test
  public void test_long_offset() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, Integer.MAX_VALUE, null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(longField.copy()));
  }

  @Test
  public void test_long_offset_max() {
    AtomicInteger count = new AtomicInteger();
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, msg.length(), null);
    parser.endHandler(v -> count.incrementAndGet());
    parser.handler(buf -> {
      assertNotNull(buf);
      assertEquals(msg.length(), buf.length());
      assertEquals(msg, buf.toString());
    });
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(longField.copy()));
  }

  @Test
  public void test_long_offset_max_exception() {
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, 5, null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(longField.copy()));
    assertEquals(1, errors.size());
  }

  @Test
  public void test_long_offset_max_invalid_length_exception() {
    LengthFieldParser parser = LengthFieldParser.newParser(8, 18, msg.length(), null);
    List<Throwable> errors = new ArrayList<>();
    parser.exceptionHandler(errors::add);
    parser.handle(Buffer.buffer().appendBuffer(preField.copy()).appendBuffer(
      longField.copy().setLong(0, -1)
    ));
    assertEquals(1, errors.size());
  }


  @Test
  public void test_byte_stream_handle() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    for (int i = 0; i < 10; i++) {
      stream.handle(Buffer.buffer().appendBuffer(byteField.copy()).appendBuffer(byteField.copy()));
    }
    assertFalse(stream.isPaused());
    assertEquals(20, events.size());
  }

  @Test
  public void test_byte_stream_pause() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    parser.handle(Buffer.buffer().appendBuffer(byteField.copy()));
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void test_byte_stream_resume() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    stream.handle(Buffer.buffer()
      .appendBuffer(byteField.copy())
      .appendBuffer(byteField.copy())
      .appendBuffer(byteField.copy()));
    parser.resume();
    assertEquals(3, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void test_byte_stream_fetch() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    stream.handle(Buffer.buffer().appendBuffer(byteField.copy()).appendBuffer(byteField.copy()));
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void test_byte_stream_pause_in_handler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    stream.handle(Buffer.buffer().appendBuffer(byteField.copy()));
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void test_byte_stream_fetch_in_handler() {
    FakeStream stream = new FakeStream();
    LengthFieldParser parser = LengthFieldParser.newParser(1, 0, Integer.MAX_VALUE, stream);
    List<Buffer> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    stream.handle(Buffer.buffer().appendBuffer(byteField.copy()).appendBuffer(byteField.copy()));
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

}
