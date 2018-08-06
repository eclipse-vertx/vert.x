/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.ReadStream;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.test.core.TestUtils.assertNullPointerException;
import static org.junit.Assert.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:larsdtimm@gmail.com">Lars Timm</a>
 */
public class RecordParserTest {

  @Test
  public void testIllegalArguments() throws Exception {
    assertNullPointerException(() -> RecordParser.newDelimited((Buffer) null, handler -> {}));
    assertNullPointerException(() -> RecordParser.newDelimited((String) null, handler -> {}));

    RecordParser parser = RecordParser.newDelimited("", handler -> {});
    assertNullPointerException(() -> parser.setOutput(null));
    assertNullPointerException(() -> parser.delimitedMode((Buffer) null));
    assertNullPointerException(() -> parser.delimitedMode((String) null));
  }

  @Test
  /*
  Test parsing with delimiters
   */
  public void testDelimited() {
    delimited(Buffer.buffer().appendByte((byte)'\n'));
    delimited(Buffer.buffer().appendByte((byte) '\r').appendByte((byte) '\n'));
    delimited(Buffer.buffer(new byte[]{0, 3, 2, 5, 6, 4, 6}));
  }

  @Test
  /*
  Test parsing with fixed size records
   */
  public void testFixed() {
    int lines = 50;
    Buffer[] expected = new Buffer[lines];

    //We create lines of length zero to <lines> and shuffle them
    List<Buffer> lineList = generateLines(lines, false, (byte) 0);

    expected = lineList.toArray(expected);
    int totLength = lines * (lines - 1) / 2; // The sum of 0...(lines - 1)
    Buffer inp = Buffer.buffer(totLength);
    for (int i = 0; i < lines; i++) {
      inp.appendBuffer(expected[i]);
    }

    //We then try every combination of chunk size up to twice the input string length
    for (int i = 1; i < inp.length() * 2; i++) {
      doTestFixed(inp, new Integer[]{i}, expected);
    }

    //Then we try a sequence of random chunk sizes
    List<Integer> chunkSizes = generateChunkSizes(lines);

    //Repeat a few times
    for (int i = 0; i < 10; i++) {
      Collections.shuffle(chunkSizes);
      doTestFixed(inp, chunkSizes.toArray(new Integer[]{}), expected);
    }
  }

  @Test
  /*
  Test mixture of fixed and delimited
   */
  public void testMixed() {
    final int lines = 8;
    final List<Object> types = new ArrayList<Object>();

    class MyHandler implements Handler<Buffer> {
      RecordParser parser = RecordParser.newFixed(10, this);
      int pos;

      public void handle(Buffer buff) {
        if (pos < lines) {
          Object type = types.get(pos);
          if (type instanceof byte[]) {
            byte[] bytes = (byte[]) type;
            parser.delimitedMode(Buffer.buffer(bytes));
          } else {
            int length = (Integer) type;
            parser.fixedSizeMode(length);
          }
        }
      }
    }

    MyHandler out = new MyHandler();
    Buffer[] expected = new Buffer[lines];
    Buffer input = Buffer.buffer(100);
    expected[0] = TestUtils.randomBuffer(10);
    input.appendBuffer(expected[0]);
    types.add(expected[0].length());
    expected[1] = TestUtils.randomBuffer(100);
    input.appendBuffer(expected[1]);
    types.add(expected[1].length());
    byte[] delim = new byte[]{23, -120, 100, 3};
    expected[2] = TestUtils.randomBuffer(50, true, delim[0]);
    input.appendBuffer(expected[2]);
    types.add(delim);
    input.appendBuffer(Buffer.buffer(delim));
    expected[3] = TestUtils.randomBuffer(1000);
    input.appendBuffer(expected[3]);
    types.add(expected[3].length());
    expected[4] = TestUtils.randomBuffer(230, true, delim[0]);
    input.appendBuffer(expected[4]);
    types.add(delim);
    input.appendBuffer(Buffer.buffer(delim));
    delim = new byte[]{17};
    expected[5] = TestUtils.randomBuffer(341, true, delim[0]);
    input.appendBuffer(expected[5]);
    types.add(delim);
    input.appendBuffer(Buffer.buffer(delim));
    delim = new byte[]{54, -32, 0};
    expected[6] = TestUtils.randomBuffer(1234, true, delim[0]);
    input.appendBuffer(expected[6]);
    types.add(delim);
    input.appendBuffer(Buffer.buffer(delim));
    expected[7] = TestUtils.randomBuffer(100);
    input.appendBuffer(expected[7]);
    types.add(expected[7].length());

    feedChunks(input, out.parser, new Integer[]{50, 10, 3});
  }

  /*
  We create some input dataHandler which contains <lines> lines of lengths in randm order between 0 and lines
  And then passes them into the RecordParser in chunk sizes from 0 to twice the total input buffer size
   */
  private void delimited(Buffer delim) {
    int lines = 50;
    Buffer[] expected = new Buffer[lines];

    //We create lines of length zero to <lines> and shuffle them
    List<Buffer> lineList = generateLines(lines, true, delim.getByte(0));

    expected = lineList.toArray(expected);
    int totLength = lines * (lines - 1) / 2; // The sum of 0...(lines - 1)
    Buffer inp = Buffer.buffer(totLength + lines * delim.length());
    for (int i = 0; i < lines; i++) {
      inp.appendBuffer(expected[i]);
      inp.appendBuffer(delim);
    }

    //We then try every combination of chunk size up to twice the input string length
    for (int i = 1; i < inp.length() * 2; i++) {
      doTestDelimited(inp, delim, new Integer[]{i}, expected);
    }

    //Then we try a sequence of random chunk sizes
    List<Integer> chunkSizes = generateChunkSizes(lines);

    //Repeat a few times
    for (int i = 0; i < 10; i++) {
      Collections.shuffle(chunkSizes);
      doTestDelimited(inp, delim, chunkSizes.toArray(new Integer[]{}), expected);
    }
  }

  private void doTestDelimited(final Buffer input, Buffer delim, Integer[] chunkSizes, final Buffer... expected) {
    final Buffer[] results = new Buffer[expected.length];
    Handler<Buffer> out = new Handler<Buffer>() {
      int pos;

      public void handle(Buffer buff) {
        results[pos++] = buff;
      }
    };
    RecordParser parser = RecordParser.newDelimited(delim, out);
    feedChunks(input, parser, chunkSizes);

    checkResults(expected, results);
  }


  private void doTestFixed(final Buffer input, Integer[] chunkSizes, final Buffer... expected) {
    final Buffer[] results = new Buffer[expected.length];

    class MyHandler implements Handler<Buffer> {
      int pos;
      RecordParser parser = RecordParser.newFixed(expected[0].length(), this);

      public void handle(Buffer buff) {
        results[pos++] = buff;
        if (pos < expected.length) {
          parser.fixedSizeMode(expected[pos].length());
        }
      }
    }

    MyHandler out = new MyHandler();
    feedChunks(input, out.parser, chunkSizes);

    checkResults(expected, results);
  }

  private void feedChunks(Buffer input, RecordParser parser, Integer[] chunkSizes) {
    int pos = 0;
    int chunkPos = 0;
    while (pos < input.length()) {
      int chunkSize = chunkSizes[chunkPos++];
      if (chunkPos == chunkSizes.length) chunkPos = 0;
      int end = pos + chunkSize;
      end = end <= input.length() ? end : input.length();
      Buffer sub = input.getBuffer(pos, end);
      parser.handle(sub);
      pos += chunkSize;
    }
  }

  private void checkResults(Buffer[] expected, Buffer[] results) {
    for (int i = 0; i < expected.length; i++) {
      assertEquals("Expected:" + expected[i] + " length:" + expected[i].length() +
        " Actual:" + results[i] + " length:" + results[i].length(), expected[i], results[i]);
    }
  }

  private List<Buffer> generateLines(int lines, boolean delim, byte delimByte) {
    //We create lines of length one to <lines> and shuffle them
    List<Buffer> lineList = new ArrayList<Buffer>();
    for (int i = 0; i < lines; i++) {
      lineList.add(TestUtils.randomBuffer(i + 1, delim, delimByte));
    }
    Collections.shuffle(lineList);
    return lineList;
  }

  private List<Integer> generateChunkSizes(int lines) {
    //Then we try a sequence of random chunk sizes
    List<Integer> chunkSizes = new ArrayList<Integer>();
    for (int i = 1; i < lines / 5; i++) {
      chunkSizes.add(i);
    }
    return chunkSizes;
  }

  @Test
  /*
   * test issue-209
   */
  public void testSpreadDelimiter() {
    doTestDelimited(Buffer.buffer("start-a-b-c-dddabc"), Buffer.buffer("abc"),
      new Integer[] { 18 }, Buffer.buffer("start-a-b-c-ddd"));
    doTestDelimited(Buffer.buffer("start-abc-dddabc"), Buffer.buffer("abc"),
      new Integer[] { 18 }, Buffer.buffer("start-"), Buffer.buffer("-ddd"));
    doTestDelimited(Buffer.buffer("start-ab-c-dddabc"), Buffer.buffer("abc"),
      new Integer[] { 18 }, Buffer.buffer("start-ab-c-ddd"));
  }

  @Test
  public void testWrapReadStream() {
    AtomicBoolean paused = new AtomicBoolean();
    AtomicReference<Handler<Buffer>> eventHandler = new AtomicReference<>();
    AtomicReference<Handler<Void>> endHandler = new AtomicReference<>();
    AtomicReference<Handler<Throwable>> exceptionHandler = new AtomicReference<>();
    ReadStream<Buffer> original = new ReadStream<Buffer>() {
      @Override
      public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
        exceptionHandler.set(handler);
        return this;
      }
      @Override
      public ReadStream<Buffer> handler(Handler<Buffer> handler) {
        eventHandler.set(handler);
        return this;
      }
      @Override
      public ReadStream<Buffer> fetch(long amount) {
        throw new UnsupportedOperationException();
      }
      @Override
      public ReadStream<Buffer> pause() {
        paused.set(true);
        return this;
      }
      @Override
      public ReadStream<Buffer> resume() {
        paused.set(false);
        return this;
      }
      @Override
      public ReadStream<Buffer> endHandler(Handler<Void> handler) {
        endHandler.set(handler);
        return this;
      }
    };
    RecordParser parser = RecordParser.newDelimited("\r\n", original);
    AtomicInteger ends = new AtomicInteger();
    parser.endHandler(v -> ends.incrementAndGet());
    Deque<String> records = new ArrayDeque<>();
    parser.handler(record -> records.add(record.toString()));
    assertFalse(paused.get());
    eventHandler.get().handle(Buffer.buffer("first\r\nsecond\r\nthird"));
    assertEquals("first", records.poll());
    assertEquals("second", records.poll());
    assertNull(records.poll());
    eventHandler.get().handle(Buffer.buffer("\r\n"));
    assertEquals("third", records.poll());
    assertNull(records.poll());
    assertEquals(0, ends.get());
    Throwable cause = new Throwable();
    exceptionHandler.get().handle(cause);
    List<Throwable> failures = new ArrayList<>();
    parser.exceptionHandler(failures::add);
    exceptionHandler.get().handle(cause);
    assertEquals(Collections.singletonList(cause), failures);

    assertFalse(paused.get());
    parser.pause();
    assertFalse(paused.get());
    int count = 0;
    do {
      eventHandler.get().handle(Buffer.buffer("item-" + count++ + "\r\n"));
    } while (!paused.get());
    assertNull(records.poll());
    parser.resume();
    for (int i = 0;i < count;i++) {
      assertEquals("item-" + i, records.poll());
    }
    assertNull(records.poll());

    endHandler.get().handle(null);
    assertEquals(1, ends.get());
  }
}
