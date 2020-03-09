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
package io.vertx.core.streams;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class WriteStreamTest extends AsyncTestBase {

  static class StreamBase<T> implements WriteStream<T> {
    @Override public StreamBase<T> exceptionHandler(Handler<Throwable> handler) { throw new UnsupportedOperationException(); }
    @Override public Future<Void> write(T data) { throw new UnsupportedOperationException(); }
    @Override public void write(T data, Handler<AsyncResult<Void>> handler) { throw new UnsupportedOperationException(); }
    @Override public void end(Handler<AsyncResult<Void>> handler) { throw new UnsupportedOperationException(); }
    @Override public StreamBase<T> setWriteQueueMaxSize(int maxSize) { throw new UnsupportedOperationException(); }
    @Override public boolean writeQueueFull() { throw new UnsupportedOperationException(); }
    @Override public StreamBase<T> drainHandler(@Nullable Handler<Void> handler) { throw new UnsupportedOperationException(); }
    @Override public final Future<Void> end() { return WriteStream.super.end(); }
    @Override public final Future<Void> end(T t) { return WriteStream.super.end(t); }
    @Override public final void end(T t, Handler<AsyncResult<Void>> handler) { WriteStream.super.end(t, handler); }
  }

  static class EndWithItemStreamAsync extends StreamBase<Object> {
    AtomicInteger writeCount = new AtomicInteger();
    Promise<Void> writeFut = Promise.promise();
    AtomicInteger endCount = new AtomicInteger();
    Promise<Void> endFut = Promise.promise();
    @Override
    public void write(Object data, Handler<AsyncResult<Void>> handler) {
      writeCount.incrementAndGet();
      writeFut.future().onComplete(handler);
    }
    @Override
    public void end(Handler<AsyncResult<Void>> handler) {
      endCount.incrementAndGet();
      endFut.future().onComplete(handler);
    }
  }

  @Test
  public void testEndWithItemStreamAsync() {
    Object item = new Object();
    Throwable cause = new Throwable();
    EndWithItemStreamAsync src = new EndWithItemStreamAsync();
    Future<Void> resolvedFut = src.end(item);
    assertEquals(1, src.writeCount.get());
    assertEquals(0, src.endCount.get());
    assertFalse(resolvedFut.isComplete());
    src.writeFut.complete();
    assertEquals(1, src.writeCount.get());
    assertEquals(1, src.endCount.get());
    assertFalse(resolvedFut.isComplete());
    src.endFut.complete();
    assertEquals(1, src.writeCount.get());
    assertEquals(1, src.endCount.get());
    assertTrue(resolvedFut.succeeded());
    assertNull(resolvedFut.result());

    src = new EndWithItemStreamAsync();
    resolvedFut = src.end(item);
    src.writeFut.fail(cause);
    assertEquals(1, src.writeCount.get());
    assertEquals(0, src.endCount.get());
    assertTrue(resolvedFut.failed());
    assertSame(cause, resolvedFut.cause());

    src = new EndWithItemStreamAsync();
    resolvedFut = src.end(item);
    src.writeFut.complete();
    src.endFut.fail(cause);
    assertEquals(1, src.writeCount.get());
    assertEquals(1, src.endCount.get());
    assertTrue(resolvedFut.failed());
    assertSame(cause, resolvedFut.cause());
  }
}
