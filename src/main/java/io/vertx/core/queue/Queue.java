/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.queue;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.queue.impl.BufferedQueue;

/**
 * An event-driven queue.
 * <p/>
 * The queue is softly unbounded, i.e the producer can {@link #add} any number of elements in the queue and shall
 * cooperate with the queue to not overload it.
 *
 * <h3>Producing to the queue</h3>
 * When the producer adds an element to the queue, the boolean flag returned by the {@link #add} method indicates whether
 * it can continue safely adding more elements in the queue or stop.
 * <p/>
 * The producer can set a {@link #writableHandler} to be signaled when it can resume reading and add elements to the queue again.
 *
 * <h3>Consuming from the queue</h3>
 * The consumer should set an {@link #handler} to receive the elements from the queue to consume elements in an
 * event driven manner.
 *
 * <h3>Queue mode</h3>
 * The queue is either in <i>flowing</i> or <i>fetch</i> mode.
 * <ul>
 *   <i>Initially the queue is in <i>flowing</i> mode.</i>
 *   <li>When the queue is in <i>flowing</i> mode, elements in the queue are delivered to the {@code handler}.</li>
 *   <li>When the queue is in <i>fetch</i> mode, only the number of requested elements will be delivered to the {@code handler}.</li>
 * </ul>
 * The consumer can change the mode with the {@link #pause()}, {@link #resume()} and {@link #take} methods:
 * <ul>
 *   <li>Calling {@link #resume()} sets the queue in <i>flowing</i> mode</li>
 *   <li>Calling {@link #pause()} sets the queue in <i>fetch</i> mode and resets the demand to {@code 0}</li>
 *   <li>Calling {@link #take(long)} requests a specific amount of elements and adds it to the actual queue demand</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 *
 * The queue is thread safe, i.e it can be called from any thread and is re-entrant (i.e it can be called from an event handler).
 * <p/>
 * At most one thread at a time will call the consumer.
 * <ul>
 *   <li>When the queue is created with a {@link Context}, the handler will be called on the context thread.</li>
 *   <li>When the queue is created without a context, the handler will be called by the thread that operates the queue,
 *       e.g the thread calling {@link #add(Object)}, {@link #resume()} or {@link #take(long)}.</li>
 * </ul>
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 * <p/>
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * <h3>Element delivery</h3>
 * When the queue operates on a context and many elements are pushed simultaneously, the queue will deliver at most
 * {@code 16} elements by iteration. If the handler can consume more elements, the next elements will be delivered
 * on another event loop tick.
 *
 * <h3>Buffering</h3>
 * The queue holds an internal buffer to hold the pending elements until the consumer demands them. When the buffer
 * is full, the producer should cooperate to not add more elements to the queue.
 * <p/>
 * When the buffer is drained, the producer will be called to signal it can resume writing to the queue.
 *
 * <p/>
 * <strong>WARNING</strong>: this queue is mostly useful for implementing the {@link io.vertx.core.streams.ReadStream}
 * and has little or no use within a regular application.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Queue<T> {

  /**
   * Create a new empty queue.
   *
   * @return the created queue
   */
  static <T> Queue<T> queue() {
    return new BufferedQueue<>(null, QueueOptions.DEFAULT_MAX_SIZE, QueueOptions.DEFAULT_HIGH_WATER_MARK);
  }

  /**
   * Create a new empty queue with a specified {@code highWaterMark}
   *
   * @param highWaterMark the queue high water mark
   * @return the created queue
   */
  static <T> Queue<T> queue(long highWaterMark) {
    return new BufferedQueue<>(null, highWaterMark, QueueOptions.DEFAULT_HIGH_WATER_MARK);
  }

  /**
   * Create a new empty queue using the provided {@code options}
   *
   * @param options the queue options
   * @return the created queue
   */
  static <T> Queue<T> queue(QueueOptions options) {
    return new BufferedQueue<>(null, options.getHighWaterMark(), options.getMaxElementsPerTick());
  }

  /**
   * Create a new empty queue using the provided {@code context}
   *
   * @param context the queue context
   * @return the created queue
   */
  static <T> Queue<T> queue(Context context) {
    return new BufferedQueue<>(context, QueueOptions.DEFAULT_MAX_SIZE, QueueOptions.DEFAULT_HIGH_WATER_MARK);
  }

  /**
   * Create a new empty queue using the provided {@code context} and with a specified {@code highWaterMark}
   *
   * @param context the queue context
   * @param highWaterMark the queue high water mark
   * @return the created queue
   */
  static <T> Queue<T> queue(Context context, long highWaterMark) {
    return new BufferedQueue<>(context, highWaterMark, QueueOptions.DEFAULT_HIGH_WATER_MARK);
  }

  /**
   * Create a new empty queue using the provided {@code context} and with the specified {@code options}
   *
   * @param context the queue context
   * @param options the queue options
   * @return the created queue
   */
  static <T> Queue<T> queue(Context context, QueueOptions options) {
    return new BufferedQueue<>(context, options.getHighWaterMark(), options.getMaxElementsPerTick());
  }

  /**
   * Add an {@code element} in the queue.
   *
   * @param element the element to enqueue
   * @return {@code true} if the queue can accept more values
   */
  boolean add(T element);

  /**
   * Add an {@code iterable} of elements in the queue.
   *
   * @param iterable the elements to enqueue
   * @return {@code true} if the queue can accept more values
   */
  boolean addAll(Iterable<T> iterable);

  /**
   * Set an {@code handler} to be called when the queue is drained and the producer can resume writing to the queue.
   *
   * @param handler the handler to be called
   * @return a reference to this, so the API can be used fluently
   */
  Queue<T> writableHandler(Handler<Void> handler);

  /**
   * Set an {@code handler} to be called when the queue becomes empty.
   *
   * @param handler the handler to be called
   * @return a reference to this, so the API can be used fluently
   */
  Queue<T> emptyHandler(Handler<Void> handler);

  /**
   * Set an {@code handler} to be called with elements available from this queue.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  Queue<T> handler(Handler<T> handler);

  /**
   * Set an {@code handler} to be called when an exception is thrown by an handler.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  Queue<T> exceptionHandler(Handler<Throwable> handler);

  /**
   * Pause the queue, it sets the queue in {@code fetch} mode and clears the actual demand.
   *
   * @return a reference to this, so the API can be used fluently
   */
  Queue<T> pause();

  /**
   * Request a specific {@code amount} of elements to be consumed asynchronously, the amount is added to the actual demand.
   * <p/>
   * The elements will be delivered on the queue handler.
   * </p>
   * When the queue is in {@code flowing} mode, nothing happens.
   *
   * @return a reference to this, so the API can be used fluently
   */
  Queue<T> take(long amount);

  /**
   * Resume the queue, it sets the queue in {@code flowing} mode.
   *
   * @return a reference to this, so the API can be used fluently
   */
  Queue<T> resume();

  /**
   * @return whether the queue is writable
   */
  boolean isWritable();

  /**
   * @return whether the queue is paused, i.e it is in {@code fetch} mode and the demand is {@code 0}.
   */
  boolean isPaused();

  /**
   * @return the queue size
   */
  int size();

  /**
   * @return whether the queue is empty
   */
  boolean isEmpty();

  /**
   * poll the most recent element synchronously.
   * <p/>
   * The drain handler might be called.
   *
   * @return the most recent element or {@code null}
   */
  T poll();

  /**
   * Poll all the elements synchronously, the queue will be in an empty state.
   * <p/>
   *
   * @return an iterable containing all the elements
   */
  Iterable<T> pollAll();

  /**
   * Clear the queue elements synchronously, no event will be triggerd.
   */
  void clear();

}
