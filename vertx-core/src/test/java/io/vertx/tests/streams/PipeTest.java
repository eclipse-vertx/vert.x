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
package io.vertx.tests.streams;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.streams.Pipe;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.fakestream.FakeStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class PipeTest extends AsyncTestBase {

  private FakeStream<Object> dst;
  private List<Object> emitted;
  private Object o1 = new Object();
  private Object o2 = new Object();
  private Object o3 = new Object();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    dst = new FakeStream<>();
    emitted = new ArrayList<>();
    dst.handler(emitted::add);
  }

  @Test
  public void testSimple() {
    FakeStream<Object> src = new FakeStream<>();
    src.pipeTo(dst).onComplete(onSuccess(v -> {
      assertTrue(dst.isEnded());
      assertNull(src.handler());
      assertNull(src.exceptionHandler());
      assertNull(src.endHandler());
      assertEquals(Arrays.asList(o1, o2, o3), emitted);
      testComplete();
    }));
    src.write(o1);
    src.write(o2);
    src.write(o3);
    src.end();
    await();
  }

  @Test
  public void testEndStreamPrematurely() {
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    src.write(o1);
    src.end();
    pipe.to(dst).onComplete(onSuccess(v -> {
      assertTrue(dst.isEnded());
      assertEquals(Collections.singletonList(o1), emitted);
      testComplete();
    }));
    await();
  }

  @Test
  public void testFailStreamPrematurely() {
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    src.write(o1);
    Throwable failure = new Throwable();
    src.fail(failure);
    pipe.to(dst).onComplete(onFailure(err -> {
      assertSame(failure, err);
      assertTrue(dst.isEnded());
      assertEquals(Collections.singletonList(o1), emitted);
      testComplete();
    }));
    await();
  }

  @Test
  public void testEndWriteStreamOnReadStreamFailure() {
    Throwable expected = new Throwable();
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    Promise<Void> end = Promise.promise();
    dst.setEnd(end.future());
    pipe.to(dst).onComplete(onFailure(err -> {
      assertSame(expected, err);
      assertTrue(dst.isEnded());
      assertTrue(end.future().isComplete());
      testComplete();
    }));
    src.fail(expected);
    end.complete();
    await();
  }

  @Test
  public void testDoNotEndWriteStreamOnReadStreamFailure() {
    Throwable expected = new Throwable();
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    pipe.endOnFailure(false);
    pipe.to(dst).onComplete(onFailure(err -> {
      assertSame(expected, err);
      assertFalse(dst.isEnded());
      testComplete();
    }));
    src.fail(expected);
    await();
  }

  @Test
  public void testEndWriteStreamOnWriteStreamFailure() {
    RuntimeException expected = new RuntimeException();
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    dst.pause();
    Promise<Void> end = Promise.promise();
    pipe.to(dst).onComplete(onFailure(err -> {
      assertFalse(src.isPaused());
      assertSame(expected, err);
      assertTrue(dst.isEnded());
      assertTrue(end.future().succeeded());
      testComplete();
    }));
    while (!src.isPaused()) {
      src.write(o1);
    }
    dst.handler(item -> {
      throw expected;
    });

    dst.setEnd(end.future());
    dst.resume();
    end.complete();
    await();
  }

  @Test
  public void testDoNotEndWriteStreamOnSuccess() {
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    pipe.endOnSuccess(false);
    pipe.to(dst).onComplete(onSuccess(v -> {
      assertEquals(Arrays.asList(o1, o2, o3), emitted);
      assertFalse(dst.isEnded());
      testComplete();
    }));
    src.write(o1);
    src.write(o2);
    src.write(o3);
    src.end();
    await();
  }

  @Test
  public void testPauseResume() {
    FakeStream<Object> src = new FakeStream<>();
    dst.setWriteQueueMaxSize(5);
    dst.pause();
    src.pipeTo(dst);
    for (int i = 0; i < 10; i++) {   // Repeat a few times
      List<Object> inp = new ArrayList<>();
      for (int j = 0; j < 5; j++) {
        Object o = new Object();
        inp.add(o);
        src.write(o);
        assertFalse(src.isPaused());
        assertEquals(i, src.pauseCount());
        assertEquals(1 + i, src.resumeCount());
      }
      Object o = new Object();
      inp.add(o);
      src.write(o);
      assertTrue(src.isPaused());
      assertEquals(1 + i, src.pauseCount());
      assertEquals(1 + i, src.resumeCount());
      dst.resume();
      dst.pause();
      assertEquals(inp, emitted);
      emitted.clear();
      assertFalse(src.isPaused());
      assertEquals(i + 1, src.pauseCount());
      assertEquals(i + 2, src.resumeCount());
    }
  }

  @Test
  public void testClosePipeBeforeStart() {
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    assertTrue(src.isPaused());
    pipe.close();
    assertFalse(src.isPaused());
  }

  @Test
  public void testClosePipeBeforeEnd() {
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    pipe.to(dst);
    dst.pause();
    while (!src.isPaused()) {
      src.write(o1);
    }
    assertTrue(src.isPaused());
    pipe.close();
    assertNull(src.handler());
    assertNull(src.exceptionHandler());
    assertNull(dst.drainHandler());
    assertNull(dst.exceptionHandler());
    assertFalse(src.isPaused());
  }

  @Test
  public void testClosePipeAfterEnd() {
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    pipe.to(dst);
    dst.pause();
    while (!src.isPaused()) {
      src.write(o1);
    }
    src.end();
    assertTrue(src.isPaused());
    pipe.close();
  }

  @Test
  public void testEndWriteStreamSuccess() {
    Promise<Void> completion = Promise.promise();
    dst.setEnd(completion.future());
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    AtomicReference<AsyncResult<Void>> ended = new AtomicReference<>();
    pipe.to(dst).onComplete(ended::set);
    src.end();
    assertNull(ended.get());
    completion.complete();
    assertTrue(ended.get().succeeded());
  }

  @Test
  public void testEndWriteStreamFail() {
    Promise<Void> completion = Promise.promise();
    dst.setEnd(completion.future());
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    AtomicReference<AsyncResult<Void>> ended = new AtomicReference<>();
    pipe.to(dst).onComplete(ended::set);
    src.end();
    assertNull(ended.get());
    Exception failure = new Exception();
    completion.fail(failure);
    assertTrue(ended.get().failed());
    assertEquals(failure, ended.get().cause());
  }

  @Test
  public void testPipeCloseFailsTheResult() {
    FakeStream<Object> src = new FakeStream<>();
    Pipe<Object> pipe = src.pipe();
    List<AsyncResult<Void>> res = new ArrayList<>();
    pipe.to(dst).onComplete(event -> res.add(event));
    assertEquals(Collections.emptyList(), res);
    pipe.close();
    assertEquals(1, res.size());
    AsyncResult<Void> ar = res.get(0);
    assertTrue(ar.failed());
    assertEquals(ar.cause().getClass(), VertxException.class);
  }
}
