/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.parsetools;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonEvent;
import io.vertx.core.parsetools.JsonEventType;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.test.core.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JsonParserTest {

  @Test
  public void testParseEmptyObject() {
    JsonParser parser = JsonParser.newParser();
    AtomicInteger status = new AtomicInteger();
    parser.handler(event -> {
      assertNull(event.fieldName());
      assertNull(event.value());
      switch (status.getAndIncrement()) {
        case 0:
          Assert.assertEquals(JsonEventType.START_OBJECT, event.type());
          break;
        case 1:
          assertEquals(JsonEventType.END_OBJECT, event.type());
          break;
        default:
          fail();
      }
    });
    AtomicInteger count = new AtomicInteger();
    parser.endHandler(v -> count.incrementAndGet());
    parser.handle(Buffer.buffer("{}"));
    assertEquals(2, status.get());
    assertEquals(0, count.get());
    parser.end();
    assertEquals(1, count.get());
    assertEquals(2, status.get());
    try {
      parser.end();
      fail();
    } catch (IllegalStateException ignore) {
      // expected
    }
  }

  @Test
  public void testParseEmptyArray() {
    JsonParser parser = JsonParser.newParser();
    AtomicInteger status = new AtomicInteger();
    parser.handler(event -> {
      assertNull(event.fieldName());
      assertNull(event.value());
      switch (status.getAndIncrement()) {
        case 0:
          assertEquals(JsonEventType.START_ARRAY, event.type());
          break;
        case 1:
          assertEquals(JsonEventType.END_ARRAY, event.type());
          break;
        default:
          fail();
      }
    });
    parser.handle(Buffer.buffer("[]"));
    assertEquals(2, status.get());
  }

  @Test
  public void parseUnfinishedThrowingException() {
    StringBuilder events = new StringBuilder();
    JsonParser parser = JsonParser.newParser();
    parser.handler(e -> events.append("json,"));
    parser.endHandler(v -> events.append("end,"));
    parser.handle(Buffer.buffer("{\"un\":\"finished\""));
    try {
      parser.end();
      fail();
    } catch (DecodeException expected) {
    }
    assertEquals("json,json,", events.toString());
  }

  @Test
  public void parseUnfinishedExceptionHandler() {
    StringBuilder events = new StringBuilder();
    JsonParser parser = JsonParser.newParser();
    parser.handler(e -> events.append("json,"));
    parser.endHandler(v -> events.append("end,"));
    parser.exceptionHandler(e -> events.append("exception,"));
    parser.handle(Buffer.buffer("{\"un\":\"finished\""));
    parser.end();
    assertEquals("json,json,exception,end,", events.toString());
  }

  @Test
  public void testParseWithErrors() {
    Buffer data = Buffer.buffer("{\"foo\":\"foo_value\"},{\"bar\":\"bar_value\"},{\"juu\":\"juu_value\"}");
    JsonParser parser = JsonParser.newParser();
    List<JsonObject> objects = new ArrayList<>();
    List<Throwable> errors = new ArrayList<>();
    AtomicInteger endCount = new AtomicInteger();
    parser.objectValueMode()
      .handler(event -> objects.add(event.objectValue()))
      .exceptionHandler(errors::add)
      .endHandler(v -> endCount.incrementAndGet());
    parser.write(data);
    assertEquals(3, objects.size());
    List<JsonObject> expected = Arrays.asList(
      new JsonObject().put("foo", "foo_value"),
      new JsonObject().put("bar", "bar_value"),
      new JsonObject().put("juu", "juu_value")
    );
    assertEquals(expected, objects);
    assertEquals(2, errors.size());
    assertEquals(0, endCount.get());
    objects.clear();
    errors.clear();
    parser.end();
    assertEquals(Collections.emptyList(), objects);
    assertEquals(Collections.emptyList(), errors);
    assertEquals(1, endCount.get());
  }

  @Test
  public void testParseObjectValue() {
    JsonParser parser = JsonParser.newParser();
    AtomicInteger status = new AtomicInteger();
    parser.objectValueMode();
    JsonObject expected = new JsonObject()
      .put("number", 3)
      .put("floating", 3.5d)
      .put("true", true)
      .put("false", false)
      .put("string", "s")
      .put("object", new JsonObject().put("foo", "bar"))
      .put("array", new JsonArray().add(0).add(1).add(2))
      .putNull("null")
      .put("bytes", new byte[]{1, 2, 3});
    parser.handler(event -> {
      assertEquals(0, status.getAndIncrement());
      assertEquals(JsonEventType.VALUE, event.type());
      assertEquals(expected, event.value());
    });
    parser.handle(expected.toBuffer());
    assertEquals(1, status.get());
  }

  @Test
  public void testParseArrayValue() {
    JsonParser parser = JsonParser.newParser();
    AtomicInteger status = new AtomicInteger();
    parser.arrayValueMode();
    JsonArray expected = new JsonArray()
      .add(3)
      .add(3.5d)
      .add(true)
      .add(false)
      .add("s")
      .addNull()
      .add(new JsonObject().put("foo", "bar"))
      .add(new JsonArray().add(0).add(1).add(2))
      .add(new byte[]{1, 2, 3});
    parser.handler(event -> {
      assertEquals(expected, event.value());
      assertEquals(0, status.getAndIncrement());
    });
    parser.handle(expected.toBuffer());
    assertEquals(1, status.get());
  }

  private void assertThrowCCE(JsonEvent event, Consumer<JsonEvent>... checks) {
    for (Consumer<JsonEvent> check : checks) {
      try {
        check.accept(event);
        fail();
      } catch (ClassCastException ignore) {
        // Expected
      }
    }
  }

  @Test
  public void testStringValue() {
    testValue("\"bar\"", event -> {
      assertEquals("bar", event.value());
      assertFalse(event.isArray());
      assertFalse(event.isObject());
      assertFalse(event.isNumber());
      assertFalse(event.isNull());
      assertFalse(event.isBoolean());
      assertTrue(event.isString());
      assertEquals("bar", event.stringValue());
      assertThrowCCE(event,
        JsonEvent::integerValue,
        JsonEvent::longValue,
        JsonEvent::floatValue,
        JsonEvent::doubleValue,
        JsonEvent::booleanValue,
        JsonEvent::objectValue,
        JsonEvent::arrayValue);
      try {
        event.instantValue();
        fail();
      } catch (DateTimeParseException ignore) {
        // Expected
      }
    });
  }

  @Test
  public void testInstantValue() {
    Instant value = Instant.now();
    String encoded = ISO_INSTANT.format(value);
    testValue('"' + encoded + '"', event -> {
      assertEquals(encoded, event.value());
      assertFalse(event.isArray());
      assertFalse(event.isObject());
      assertFalse(event.isNumber());
      assertFalse(event.isNull());
      assertFalse(event.isBoolean());
      assertTrue(event.isString());
      assertEquals(encoded, event.stringValue());
      assertEquals(value, event.instantValue());
      assertThrowCCE(event,
        JsonEvent::integerValue,
        JsonEvent::longValue,
        JsonEvent::floatValue,
        JsonEvent::doubleValue,
        JsonEvent::booleanValue,
        JsonEvent::objectValue,
        JsonEvent::arrayValue);
    });
  }

  @Test
  public void testBinaryValue() {
    byte[] value = TestUtils.randomByteArray(10);
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    testValue('"' + encoded + '"', event -> {
      assertEquals(encoded, event.value());
      assertFalse(event.isArray());
      assertFalse(event.isObject());
      assertFalse(event.isNumber());
      assertFalse(event.isNull());
      assertFalse(event.isBoolean());
      assertTrue(event.isString());
      assertEquals(encoded, event.stringValue());
      assertEquals(Buffer.buffer(value), event.binaryValue());
      assertThrowCCE(event,
        JsonEvent::integerValue,
        JsonEvent::longValue,
        JsonEvent::floatValue,
        JsonEvent::doubleValue,
        JsonEvent::booleanValue,
        JsonEvent::objectValue,
        JsonEvent::arrayValue);
      try {
        event.instantValue();
        fail();
      } catch (DateTimeParseException ignore) {
        // Expected
      }
    });
  }

  @Test
  public void testNullValue() {
    testValue("null", event -> {
      assertEquals(null, event.value());
      assertFalse(event.isArray());
      assertFalse(event.isObject());
      assertFalse(event.isNumber());
      assertTrue(event.isNull());
      assertFalse(event.isBoolean());
      assertFalse(event.isString());
      assertNull(event.integerValue());
      assertNull(event.longValue());
      assertNull(event.floatValue());
      assertNull(event.doubleValue());
      assertNull(event.binaryValue());
      assertNull(event.instantValue());
      assertNull(event.objectValue());
      assertNull(event.arrayValue());
      assertNull(event.stringValue());
      assertNull(event.binaryValue());
    });
  }

  @Test
  public void testLongValue() {
    testValue("567", event -> {
      assertEquals(567L, event.value());
      assertFalse(event.isArray());
      assertFalse(event.isObject());
      assertTrue(event.isNumber());
      assertFalse(event.isNull());
      assertFalse(event.isBoolean());
      assertFalse(event.isString());
      assertEquals(567, (long)event.integerValue());
      assertEquals(567L, (long)event.longValue());
      assertEquals(567f, event.floatValue(), 0.01f);
      assertEquals(567d, event.doubleValue(), 0.01d);
      assertThrowCCE(event,
        JsonEvent::stringValue,
        JsonEvent::booleanValue,
        JsonEvent::binaryValue,
        JsonEvent::instantValue,
        JsonEvent::objectValue,
        JsonEvent::arrayValue);
    });
  }

  @Test
  public void testDoubleValue() {
    testValue("567.45", event -> {
      assertEquals(567.45d, event.value());
      assertFalse(event.isArray());
      assertFalse(event.isObject());
      assertTrue(event.isNumber());
      assertFalse(event.isNull());
      assertFalse(event.isBoolean());
      assertFalse(event.isString());
      assertEquals(567, (long)event.integerValue());
      assertEquals(567L, (long)event.longValue());
      assertEquals(567.45f, (float)event.floatValue(), 0.01f);
      assertEquals(567.45d, (double)event.doubleValue(), 0.01d);
      assertThrowCCE(event,
        JsonEvent::stringValue,
        JsonEvent::booleanValue,
        JsonEvent::binaryValue,
        JsonEvent::instantValue,
        JsonEvent::objectValue,
        JsonEvent::arrayValue);
    });
  }

  @Test
  public void testBigInteger() {
    String expected = "18446744073709551615";
    testValue(expected, event -> {
      BigInteger big = new BigInteger(expected);
      assertEquals(big, event.value());
      assertFalse(event.isArray());
      assertFalse(event.isObject());
      assertTrue(event.isNumber());
      assertFalse(event.isNull());
      assertFalse(event.isBoolean());
      assertFalse(event.isString());
      assertEquals(big.intValue(), (int)event.integerValue());
      assertEquals(big.longValue(), (long)event.longValue());
      assertEquals(big.floatValue(), event.floatValue(), 0.01f);
      assertEquals(big.doubleValue(), event.doubleValue(), 0.01d);
      assertThrowCCE(event,
        JsonEvent::stringValue,
        JsonEvent::booleanValue,
        JsonEvent::binaryValue,
        JsonEvent::instantValue,
        JsonEvent::objectValue,
        JsonEvent::arrayValue);
    });
  }

  @Test
  public void testBooleanValue() {
    testValue("true", event -> {
      assertEquals(true, event.value());
      assertFalse(event.isArray());
      assertFalse(event.isObject());
      assertFalse(event.isNumber());
      assertFalse(event.isNull());
      assertTrue(event.isBoolean());
      assertFalse(event.isString());
      assertTrue(event.booleanValue());
      assertThrowCCE(event,
        JsonEvent::integerValue,
        JsonEvent::longValue,
        JsonEvent::floatValue,
        JsonEvent::doubleValue,
        JsonEvent::stringValue,
        JsonEvent::binaryValue,
        JsonEvent::instantValue,
        JsonEvent::objectValue,
        JsonEvent::arrayValue);
    });
    testValue("false", event -> {
      assertEquals(false, event.value());
      assertFalse(event.isArray());
      assertFalse(event.isObject());
      assertFalse(event.isNumber());
      assertFalse(event.isNull());
      assertTrue(event.isBoolean());
      assertFalse(event.isString());
      assertFalse(event.booleanValue());
      assertThrowCCE(event,
        JsonEvent::integerValue,
        JsonEvent::longValue,
        JsonEvent::floatValue,
        JsonEvent::doubleValue,
        JsonEvent::stringValue,
        JsonEvent::binaryValue,
        JsonEvent::instantValue,
        JsonEvent::objectValue,
        JsonEvent::arrayValue);
    });
  }

  private void testValue(String jsonValue, Handler<JsonEvent> checker) {
    JsonParser parser = JsonParser.newParser();
    AtomicInteger status = new AtomicInteger();
    parser.handler(event -> {
      switch (status.getAndIncrement()) {
        case 0:
          assertEquals(JsonEventType.START_OBJECT, event.type());
          assertNull(event.fieldName());
          assertNull(event.value());
          break;
        case 1:
          assertEquals(JsonEventType.VALUE, event.type());
          checker.handle(event);
          assertEquals("foo", event.fieldName());
          break;
        case 2:
          assertEquals(JsonEventType.END_OBJECT, event.type());
          assertNull(event.fieldName());
          assertNull(event.value());
          break;
      }
    });
    parser.handle(Buffer.buffer("{\"foo\":" + jsonValue + "}"));
    assertEquals(3, status.get());
  }

  @Test
  public void testParseObjectValueMembers() {
    JsonParser parser = JsonParser.newParser();
    AtomicInteger status = new AtomicInteger();
    parser.handler(event -> {
      switch (status.getAndIncrement()) {
        case 0:
          assertEquals(JsonEventType.START_OBJECT, event.type());
          parser.objectValueMode();
          break;
        case 1:
          assertEquals(JsonEventType.VALUE, event.type());
          assertTrue(event.isObject());
          assertEquals(new JsonObject(), event.value());
          assertEquals("foo", event.fieldName());
          break;
        case 2:
          assertEquals("bar", event.fieldName());
          assertTrue(event.isObject());
          assertEquals(JsonEventType.VALUE, event.type());
          assertEquals(new JsonObject(), event.value());
          break;
        case 3:
          assertEquals(JsonEventType.END_OBJECT, event.type());
          break;
        default:
          fail();
          break;
      }
    });
    parser.handle(Buffer.buffer("{\"foo\":{},\"bar\":{}}"));
    assertEquals(4, status.get());
  }

  @Test
  public void testParseObjectValueList() {
    JsonParser parser = JsonParser.newParser();
    AtomicInteger status = new AtomicInteger();
    parser.objectValueMode();
    parser.handler(event -> {
      switch (status.getAndIncrement()) {
        case 0:
          assertEquals(JsonEventType.START_ARRAY, event.type());
          break;
        case 1:
          assertEquals(JsonEventType.VALUE, event.type());
          assertTrue(event.isObject());
          assertEquals(new JsonObject().put("one", 1), event.value());
          break;
        case 2:
          assertEquals(JsonEventType.VALUE, event.type());
          assertTrue(event.isObject());
          assertEquals(new JsonObject().put("two", 2), event.value());
          break;
        case 3:
          assertEquals(JsonEventType.VALUE, event.type());
          assertTrue(event.isObject());
          assertEquals(new JsonObject().put("three", 3), event.value());
          break;
        case 4:
          assertEquals(JsonEventType.END_ARRAY, event.type());
          break;
      }
    });
    parser.handle(Buffer.buffer("[" +
      "{\"one\":1}," +
      "{\"two\":2}," +
      "{\"three\":3}" +
      "]"));
    assertEquals(5, status.get());
  }

  @Test
  public void testObjectHandlerScope() {
    JsonParser parser = JsonParser.newParser();
    List<JsonObject> objects = new ArrayList<>();
    AtomicInteger ends = new AtomicInteger();
    AtomicBoolean obj = new AtomicBoolean();
    parser.handler(event -> {
      switch (event.type()) {
        case START_OBJECT:
          parser.objectValueMode();
          break;
        case VALUE:
          if (obj.get()) {
            objects.add((JsonObject) event.value());
          }
          break;
        case END_OBJECT:
          ends.incrementAndGet();
          obj.set(true);
          break;
      }
    });
    parser.handle(Buffer.buffer("[" +
      "{\"one\":1}," +
      "{\"two\":2}," +
      "{\"three\":3}" +
      "]"));
    assertEquals(1, ends.get());
    assertEquals(Arrays.asList(new JsonObject().put("two", 2), new JsonObject().put("three", 3)), objects);
  }

  @Test
  public void testParseTopValues() {
    Map<String, Object> tests = new HashMap<>();
    tests.put("\"a-string\"", "a-string");
    tests.put("true", true);
    tests.put("false", false);
    tests.put("1234", 1234L);
    tests.put("" + Long.MAX_VALUE, Long.MAX_VALUE);
    tests.forEach((test, expected) -> {
      JsonParser parser = JsonParser.newParser();
      List<Object> values = new ArrayList<>();
      parser.handler(event -> values.add(event.value()));
      parser.handle(Buffer.buffer(test));
      parser.end();
      assertEquals(Collections.singletonList(expected), values);
    });
  }

  @Test
  public void testObjectMapping() {
    JsonParser parser = JsonParser.newParser();
    List<Object> values = new ArrayList<>();
    parser.objectValueMode();
    parser.pause();
    parser.handler(event -> values.add(event.mapTo(TheObject.class)));
    parser.handle(Buffer.buffer("{\"f\":\"the-value-1\"}{\"f\":\"the-value-2\"}"));
    assertEquals(Collections.emptyList(), values);
    parser.fetch(1);
    assertEquals(Collections.singletonList(new TheObject("the-value-1")), values);
    parser.fetch(1);
    assertEquals(Arrays.asList(new TheObject("the-value-1"), new TheObject("the-value-2")), values);
  }

  @Test
  public void testObjectMappingError() {
    List<Object> values = new ArrayList<>();
    List<Throwable> errors = new ArrayList<>();
    JsonParser.newParser().objectValueMode().handler(event -> values.add(event.mapTo(TheObject.class))).exceptionHandler(errors::add).write(Buffer.buffer("{\"destination\":\"unknown\"}")).end();
    assertEquals(Collections.emptyList(), values);
    assertEquals(1, errors.size());
    try {
      JsonParser.newParser().objectValueMode().handler(event -> values.add(event.mapTo(TheObject.class))).write(Buffer.buffer("{\"destination\":\"unknown\"}")).end();
      fail();
    } catch (DecodeException expected) {
    }
    assertEquals(Collections.emptyList(), values);
    assertEquals(1, errors.size());
  }

    @Test
    public void testArrayMapping() {
      JsonParser parser = JsonParser.newParser();
      List<Object> values = new ArrayList<>();
      parser.arrayValueMode();
      parser.handler(event -> {
        values.add(event.mapTo(LinkedList.class));
      });
      parser.handle(new JsonArray().add(0).add(1).add(2).toBuffer());
      assertEquals(Collections.singletonList(Arrays.asList(0L, 1L, 2L)), values);
      assertEquals(LinkedList.class, values.get(0).getClass());
    }

    @Test
    public void testArrayMappingError() {
      List<Object> values = new ArrayList<>();
      List<Throwable> errors = new ArrayList<>();
      JsonParser.newParser().arrayValueMode().handler(event -> values.add(event.mapTo(TheObject.class))).exceptionHandler(errors::add).write(Buffer.buffer("[]")).end();
      assertEquals(Collections.emptyList(), values);
      assertEquals(1, errors.size());
      try {
        JsonParser.newParser().arrayValueMode().handler(event -> {
          values.add(event.mapTo(TheObject.class));
        }).write(Buffer.buffer("[]")).end();
        fail();
      } catch (DecodeException expected) {
      }
      assertEquals(Collections.emptyList(), values);
      assertEquals(1, errors.size());
    }

  public static class TheObject {

    private String f;

    public TheObject() {
    }

    public TheObject(String f) {
      this.f = f;
    }

    public void setF(String f) {
      this.f = f;
    }

    @Override
    public boolean equals(Object obj) {
      TheObject that = (TheObject) obj;
      return Objects.equals(f, that.f);
    }
  }

  @Test
  public void testParseConcatedJSONStream() {
    JsonParser parser = JsonParser.newParser();
    AtomicInteger startCount = new AtomicInteger();
    AtomicInteger endCount = new AtomicInteger();

    parser.handler(event -> {
      switch (event.type()) {
        case START_OBJECT:
          startCount.incrementAndGet();
          break;
        case END_OBJECT:
          endCount.incrementAndGet();
          break;
        default:
          fail();
          break;
      }
    });
    parser.handle(Buffer.buffer("{}{}"));
    assertEquals(2, startCount.get());
    assertEquals(2, endCount.get());
  }

  @Test
  public void testParseLineDelimitedJSONStream() {
    JsonParser parser = JsonParser.newParser();
    AtomicInteger startCount = new AtomicInteger();
    AtomicInteger endCount = new AtomicInteger();
    AtomicInteger numberCount = new AtomicInteger();
    AtomicInteger nullCount = new AtomicInteger();
    AtomicInteger stringCount = new AtomicInteger();
    parser.handler(event -> {
      switch (event.type()) {
        case START_OBJECT:
          startCount.incrementAndGet();
          break;
        case END_OBJECT:
          endCount.incrementAndGet();
          break;
        case VALUE:
          if (event.isNull()) {
            nullCount.incrementAndGet();
          } else if (event.isNumber()) {
            numberCount.incrementAndGet();
          } else if (event.isString()) {
            stringCount.incrementAndGet();
          } else {
            fail("Unexpected " + event.type());
          }
          break;
        default:
          fail("Unexpected " + event.type());
          break;
      }
    });
    parser.handle(Buffer.buffer("{}\r\n1\r\nnull\r\n\"foo\""));
    assertEquals(1, startCount.get());
    assertEquals(1, endCount.get());
    assertEquals(1, numberCount.get());
    assertEquals(1, nullCount.get());
    assertEquals(1, stringCount.get());
  }

  @Test
  public void testStreamHandle() {
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(events::add);
    stream.handle("{}");
    assertFalse(stream.isPaused());
    assertEquals(2, events.size());
  }

  @Test
  public void testStreamPause() {
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    stream.handle("1234");
    assertTrue(stream.isPaused());
    assertEquals(0, events.size());
  }

  @Test
  public void testStreamResume() {
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    stream.handle("{}");
    parser.resume();
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testStreamFetch() {
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    stream.handle("{}");
    parser.fetch(1);
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testStreamFetchNames() {
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(events::add);
    parser.pause();
    stream.handle("{\"foo\":\"bar\"}");
    parser.fetch(3);
    assertEquals(3, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testStreamPauseInHandler() {
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(event -> {
      assertTrue(events.isEmpty());
      events.add(event);
      parser.pause();
    });
    stream.handle("{}");
    assertEquals(1, events.size());
    assertTrue(stream.isPaused());
  }

  @Test
  public void testStreamFetchInHandler() {
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
      stream.fetch(1);
    });
    stream.pause();
    stream.fetch(1);
    stream.handle("{}");
    assertEquals(2, events.size());
    assertFalse(stream.isPaused());
  }

  @Test
  public void testStreamEnd() {
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(events::add);
    AtomicInteger ended = new AtomicInteger();
    parser.endHandler(v -> ended.incrementAndGet());
    stream.end();
    assertEquals(0, events.size());
    assertEquals(1, ended.get());
    //regression check for #2790 - ensure that by accident resume method is not called.
    assertEquals(0, stream.pauseCount());
    assertEquals(0, stream.resumeCount());
  }

  @Test
  public void testStreamPausedEnd() {
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(events::add);
    AtomicInteger ended = new AtomicInteger();
    parser.endHandler(v -> ended.incrementAndGet());
    parser.pause();
    stream.handle("{}");
    stream.end();
    assertEquals(0, ended.get());
    assertEquals(0, events.size());
    parser.fetch(1);
    assertEquals(1, events.size());
    assertEquals(0, ended.get());
    parser.fetch(1);
    assertEquals(2, events.size());
    assertEquals(1, ended.get());
  }


  @Test
  public void testPauseAndResumeInHandler() throws Exception {
    AtomicInteger counter = new AtomicInteger(0);
    FakeStream stream = new FakeStream();
    JsonParser parser = JsonParser.newParser(stream);
    parser.objectValueMode();
    parser.handler(event -> {
      parser.pause(); // Needed pause for doing something

      assertNotNull(event);
      assertEquals(JsonEventType.VALUE, event.type());
      counter.incrementAndGet();

      parser.resume(); // There and then resume
    });
    parser.exceptionHandler(t -> {
      throw new AssertionError(t);
    });
    CountDownLatch latch = new CountDownLatch(1);
    parser.endHandler(end -> {
      assertEquals(4, counter.get());
      latch.countDown();
    });

    stream.handle("" + "{\"field_0\": \"value");
    stream.handle("_0\"}{\"field_1\": \"value_1\"}");
    stream.handle("{\"field_2\": \"val");
    stream.handle("ue_2\"}{\"field_3\": \"value_3\"}");

    stream.end();

    assertTrue(latch.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void testStreamResume3886() {
    JsonParser parser = JsonParser.newParser();
    List<JsonEvent> events = new ArrayList<>();
    parser.handler(event -> {
      events.add(event);
    });
    parser.pause();

    Buffer b = Buffer.buffer("{ \"a\":\"y\" }");
    parser.handle(b);
    parser.handle(b);
    parser.resume();
    parser.end();

    assertEquals(3 * 2, events.size());
  }
}
