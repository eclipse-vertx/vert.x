/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core;

import io.netty.buffer.AbstractDerivedByteBuf;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static io.vertx.test.core.TestUtils.assertWaitUntil;
import static org.junit.Assert.assertFalse;

/**
 * Checks that buffers handed over to a component are released by it.
 *
 * <p>A buffer is pinned: retained by the test, so it cannot be deallocated and recycled while the test observes it, a
 * recycled buffer reporting the reference count of its next use. The component released the buffer when the pins are
 * the only references left.
 *
 * <p>A derived buffer, e.g. a slice, can share the reference count of the buffer it derives from: several pinned
 * buffers sharing a reference count are expected to have as many references as pins.
 */
@SuppressWarnings("deprecation")
public class PooledByteBufChecker {

  // null once the pins are released
  private List<ByteBuf> pinned = new ArrayList<>();

  /**
   * Pin {@code buffer}, unless the pins were released.
   *
   * @return {@code buffer}
   */
  public synchronized ByteBuf pin(ByteBuf buffer) {
    if (pinned != null) {
      pinned.add(buffer.retain());
    }
    return buffer;
  }

  public synchronized boolean isEmpty() {
    return pinned == null || pinned.isEmpty();
  }

  /**
   * @return the readable bytes of the pinned buffers
   */
  public synchronized int readableBytes() {
    if (pinned == null) {
      return 0;
    } else {
      return pinned.stream().mapToInt(ByteBuf::readableBytes).sum();
    }
  }

  /**
   * @return whether the pins are the only references left on the pinned buffers
   */
  public synchronized boolean onlyPinned() {
    if (pinned != null) {
      Map<ByteBuf, Integer> pins = new IdentityHashMap<>();
      for (ByteBuf buffer : pinned) {
        pins.merge(counter(buffer), 1, Integer::sum);
      }
      for (Map.Entry<ByteBuf, Integer> entry : pins.entrySet()) {
        if (entry.getKey().refCnt() != entry.getValue()) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Wait until this {@code PooledByteBufChecker} has exclusive ownership of the pinned buffers, then release the pins.
   */
  public void awaitOnlyPinnedAndUnpin() {
    assertFalse(isEmpty());
    try {
      assertWaitUntil(this::onlyPinned);
    } finally {
      unpin();
    }
  }

  /**
   * Release the pins, buffers are no longer pinned afterwards.
   */
  public synchronized void unpin() {
    if (pinned != null) {
      pinned.forEach(ByteBuf::release);
      pinned = null;
    }
  }

  /**
   * @return the buffer holding the reference count of {@code buffer}
   */
  private static ByteBuf counter(ByteBuf buffer) {
    while (buffer instanceof AbstractDerivedByteBuf) {
      buffer = buffer.unwrap();
    }
    return buffer;
  }
}
