/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.fakestream;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class FakeStreamTest extends AsyncTestBase {

  private FakeStream<Integer> stream;
  private List<Integer> emitted;
  private int drained;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    emitted = new ArrayList<>();
    stream = new FakeStream<>();
    stream.handler(emitted::add);
    stream.drainHandler(v -> drained++);
    drained = 0;
  }

  @Test
  public void testEmit() {
    assertTrue(stream.emit(0));
    assertEquals(Collections.singletonList(0), emitted);
    assertTrue(stream.emit(1));
    assertTrue(stream.emit(2));
    assertEquals(Arrays.asList(0, 1, 2), emitted);
  }

  @Test
  public void testPause() {
    stream.pause();
    assertTrue(stream.emit(IntStream.range(0, 16).boxed()));
    assertFalse(stream.emit(16));
    assertEquals(Collections.emptyList(), emitted);
  }

  @Test
  public void testResume() {
    stream.pause();
    assertFalse(stream.emit(IntStream.range(0, 17).boxed()));
    stream.resume();
    assertEquals(1, drained);
    assertEquals(IntStream.range(0, 17).boxed().collect(Collectors.toList()), emitted);
  }

  @Test
  public void testFetch() {
    stream.pause();
    assertFalse(stream.emit(IntStream.range(0, 17).boxed()));
    for (int i = 1;i < 17;i++) {
      stream.fetch(1);
      assertEquals(IntStream.range(0, i).boxed().collect(Collectors.toList()), emitted);
      assertEquals(0, drained);
    }
    stream.fetch(1);
    assertEquals(IntStream.range(0, 17).boxed().collect(Collectors.toList()), emitted);
    assertEquals(1, drained);
  }

  @Test
  public void testWriteQueueFull() {
    stream.pause();
    int count = 0;
    while (stream.emit(count++)) {
      assertFalse(stream.writeQueueFull());
    }
    assertTrue(stream.writeQueueFull());
    stream.fetch(1);
    assertFalse(stream.writeQueueFull());
  }

  @Test
  public void testEmitReentrancy() {
    AtomicInteger count = new AtomicInteger(2);
    AtomicBoolean emitting = new AtomicBoolean();
    stream.pause();
    stream.fetch(3);
    stream.handler(item -> {
      assertFalse(emitting.getAndSet(true));
      emitted.add(item);
      stream.emit(count.getAndIncrement());
      emitting.set(false);
    });
    stream.emit(Stream.of(0, 1));
    assertEquals(Arrays.asList(0, 1, 2), emitted);
  }

  @Test
  public void testFetchReentrancy() {
    AtomicInteger count = new AtomicInteger(2);
    stream.pause();
    AtomicBoolean emitting = new AtomicBoolean();
    stream.handler(item -> {
      assertFalse(emitting.getAndSet(true));
      emitted.add(item);
      stream.emit(count.getAndIncrement());
      emitting.set(false);
    });
    stream.write(0);
    stream.write(1);
    stream.fetch(3);
    assertEquals(Arrays.asList(0, 1, 2), emitted);
  }

  @Test
  public void testFetchAfterEnd() {
    AtomicInteger ended = new AtomicInteger();
    AtomicReference<AsyncResult> endRes = new AtomicReference<>();
    stream.endHandler(v -> ended.incrementAndGet());
    stream.end(endRes::set);
    assertEquals(1, ended.get());
    assertTrue(endRes.get().succeeded());
    stream.fetch(1);
    assertEquals(1, ended.get());
    assertTrue(endRes.get().succeeded());
  }

  @Test
  public void testAsyncEnd() {
    Promise<Void> end = Promise.promise();
    AtomicInteger ended = new AtomicInteger();
    AtomicReference<AsyncResult> endRes = new AtomicReference<>();
    stream.setEnd(end.future());
    stream.endHandler(v -> ended.incrementAndGet());
    stream.end(endRes::set);
    assertEquals(0, ended.get());
    assertNull(endRes.get());
    end.complete();
    assertEquals(1, ended.get());
    assertTrue(endRes.get().succeeded());
  }

  @Test
  public void testAsyncEndDeferred() {
    Promise<Void> end = Promise.promise();
    AtomicInteger ended = new AtomicInteger();
    AtomicReference<AsyncResult> endRes = new AtomicReference<>();
    stream.setEnd(end.future());
    stream.pause();
    stream.emit(3);
    stream.endHandler(v -> ended.incrementAndGet());
    stream.end(endRes::set);
    assertEquals(0, ended.get());
    assertNull(endRes.get());
    end.complete();
    assertEquals(0, ended.get());
    assertNull(endRes.get());
    stream.fetch(1);
    assertEquals(0, ended.get());
    assertNull(endRes.get());
    stream.fetch(1);
    assertEquals(1, ended.get());
    assertTrue(endRes.get().succeeded());
  }

  @Test
  public void testAck() {
    stream.pause();
    Future<Void> ack0 = stream.write(0);
    Future<Void> ack1 = stream.write(1);
    Future<Void> ack2 = stream.write(2);
    assertFalse(ack0.isComplete());
    assertFalse(ack1.isComplete());
    assertFalse(ack2.isComplete());
    stream.fetch(1);
    assertTrue(ack0.isComplete());
    assertFalse(ack1.isComplete());
    assertFalse(ack2.isComplete());
    stream.fetch(2);
    assertTrue(ack0.isComplete());
    assertTrue(ack1.isComplete());
    assertTrue(ack2.isComplete());
  }

  @Test
  public void testAckFailure() {
    RuntimeException failure = new RuntimeException();
    stream.pause();
    stream.handler(item -> {
      throw failure;
    });
    Future<Void> ack = stream.write(0);
    assertFalse(ack.isComplete());
    stream.fetch(1);
    assertTrue(ack.failed());
    assertEquals(failure, ack.cause());
  }
}
