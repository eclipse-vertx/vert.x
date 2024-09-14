/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.core.spi.context.storage.ContextLocal;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * The execution context of a {@link io.vertx.core.Handler} execution.
 * <p>
 * When Vert.x provides an event to a handler or calls the start or stop methods of a {@link io.vertx.core.Verticle},
 * the execution is associated with a {@code Context}.
 * <p>
 * Usually a context is an *event-loop context* and is tied to a specific event loop thread. So executions for that
 * context always occur on that exact same event loop thread.
 * <p>
 * In the case of worker verticles and running inline blocking code a worker context will be associated with the execution
 * which will use a thread from the worker thread pool.
 * <p>
 * When a handler is set by a thread associated with a specific context, the Vert.x will guarantee that when that handler
 * is executed, that execution will be associated with the same context.
 * <p>
 * If a handler is set by a thread not associated with a context (i.e. a non Vert.x thread). Then a new context will
 * be created for that handler.
 * <p>
 * In other words, a context is propagated.
 * <p>
 * This means that when a verticle is deployed, any handlers it sets will be associated with the same context - the context
 * of the verticle.
 * <p>
 * This means (in the case of a standard verticle) that the verticle code will always be executed with the exact same
 * thread, so you don't have to worry about multithreaded acccess to the verticle state, and you can code your application
 * as single threaded.
 * <p>
 * This class also allows arbitrary data to be {@link #put} and {@link #get} on the context, so it can be shared easily
 * amongst different handlers of, for example, a verticle instance.
 * <p>
 * This class also provides {@link #runOnContext} which allows an action to be executed asynchronously using the same context.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Context {

  /**
   * Is the current thread a worker thread?
   * <p>
   * NOTE! This is not always the same as calling {@link Context#isWorkerContext}. If you are running blocking code
   * from an event loop context, then this will return true but {@link Context#isWorkerContext} will return false.
   *
   * @return true if current thread is a worker thread, false otherwise
   */
  static boolean isOnWorkerThread() {
    Thread t = Thread.currentThread();
    return t instanceof VertxThread && ((VertxThread) t).isWorker();
  }

  /**
   * Is the current thread an event thread?
   * <p>
   * NOTE! This is not always the same as calling {@link Context#isEventLoopContext}. If you are running blocking code
   * from an event loop context, then this will return false but {@link Context#isEventLoopContext} will return true.
   *
   * @return true if current thread is an event thread, false otherwise
   */
  static boolean isOnEventLoopThread() {
    Thread t = Thread.currentThread();
    return t instanceof VertxThread && !((VertxThread) t).isWorker();
  }

  /**
   * Is the current thread a Vert.x thread? That's either a worker thread or an event loop thread
   *
   * @return true if current thread is a Vert.x thread, false otherwise
   */
  static boolean isOnVertxThread() {
    return Thread.currentThread() instanceof VertxThread;
  }

  /**
   * Run the specified action asynchronously on the same context, some time after the current execution has completed.
   *
   * @param action  the action to run
   */
  void runOnContext(Handler<Void> action);

  /**
   * Safely execute some blocking code.
   * <p>
   * Executes the blocking code in the handler {@code blockingCodeHandler} using a thread from the worker pool.
   * <p>
   * The returned future will be completed with the result on the original context (i.e. on the original event loop of the caller)
   * or failed when the handler throws an exception.
   * <p>
   * The blocking code should block for a reasonable amount of time (i.e. no more than a few seconds). Long blocking operations
   * or polling operations (i.e a thread that spin in a loop polling events in a blocking fashion) are precluded.
   * <p>
   * When the blocking operation lasts more than the 10 seconds, a message will be printed on the console by the
   * blocked thread checker.
   * <p>
   * Long blocking operations should use a dedicated thread managed by the application, which can interact with
   * verticles using the event-bus or {@link Context#runOnContext(Handler)}
   *
   * @param blockingCodeHandler  handler representing the blocking code to run
   * @param ordered  if true then if executeBlocking is called several times on the same context, the executions
   *                 for that context will be executed serially, not in parallel. if false then they will be no ordering
   *                 guarantees
   * @param <T> the type of the result
   * @return a future completed when the blocking code is complete
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <T> Future<@Nullable T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered);

  /**
   * Invoke {@link #executeBlocking(Callable, boolean)} with order = true.
   * @param blockingCodeHandler  handler representing the blocking code to run
   * @param <T> the type of the result
   * @return a future completed when the blocking code is complete
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default <T> Future<@Nullable T> executeBlocking(Callable<T> blockingCodeHandler) {
    return executeBlocking(blockingCodeHandler, true);
  }

  /**
   * If the context is associated with a Verticle deployment, this returns the deployment ID of that deployment.
   *
   * @return the deployment ID of the deployment or null if not a Verticle deployment
   */
  String deploymentID();

  /**
   * If the context is associated with a Verticle deployment, this returns the configuration that was specified when
   * the verticle was deployed.
   *
   * @return the configuration of the deployment or null if not a Verticle deployment
   */
  @Nullable JsonObject config();

  /**
   * @return an empty list
   * @deprecated As of version 5, Vert.x is no longer tightly coupled to the CLI
   */
  @Deprecated
  default List<String> processArgs() {
    return Collections.emptyList();
  }

  /**
   * Is the current context an event loop context?
   * <p>
   * NOTE! when running blocking code using {@link io.vertx.core.Vertx#executeBlocking(Callable)} from a
   * standard (not worker) verticle, the context will still an event loop context and this {@link #isEventLoopContext()}
   * will return true.
   *
   * @return {@code true} if the current context is an event-loop context, {@code false} otherwise
   */
  boolean isEventLoopContext();

  /**
   * Is the current context a worker context?
   * <p>
   * NOTE! when running blocking code using {@link io.vertx.core.Vertx#executeBlocking(Callable)} from a
   * standard (not worker) verticle, the context will still an event loop context and this {@link this#isWorkerContext()}
   * will return false.
   *
   * @return {@code true} if the current context is a worker context, {@code false} otherwise
   */
  boolean isWorkerContext();

  /**
   * @return the context threading model
   */
  ThreadingModel threadingModel();

  /**
   * Get some data from the context.
   *
   * @param key  the key of the data
   * @param <T>  the type of the data
   * @return the data
   */
  <T> T get(Object key);

  /**
   * Put some data in the context.
   * <p>
   * This can be used to share data between different handlers that share a context
   *
   * @param key  the key of the data
   * @param value  the data
   */
  void put(Object key, Object value);

  /**
   * Remove some data from the context.
   *
   * @param key  the key to remove
   * @return true if removed successfully, false otherwise
   */
  boolean remove(Object key);

  /**
   * @return The Vertx instance that created the context
   */
  Vertx owner();

  /**
   * @return  the number of instances of the verticle that were deployed in the deployment (if any) related
   * to this context
   */
  int getInstanceCount();

  /**
   * Set an exception handler called when the context runs an action throwing an uncaught throwable.<p/>
   *
   * When this handler is called, {@link Vertx#currentContext()} will return this context.
   *
   * @param handler the exception handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Context exceptionHandler(@Nullable Handler<Throwable> handler);

  /**
   * @return the current exception handler of this context
   */
  @GenIgnore
  @Nullable
  Handler<Throwable> exceptionHandler();

  /**
   * Get local data associated with {@code key} using the concurrent access mode.
   *
   * @param key  the key of the data
   * @param <T>  the type of the data
   * @return the local data
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default <T> T getLocal(ContextLocal<T> key) {
    return getLocal(key, AccessMode.CONCURRENT);
  }

  /**
   * <p>Get local data associated with {@code key} using the concurrent access mode.</p>
   *
   * <p>When it does not exist the {@code initialValueSupplier} is called to obtain the initial value.</p>
   *
   * <p> The {@code initialValueSupplier} might be called multiple times when multiple threads call this method concurrently.
   *
   * @param key  the key of the data
   * @param initialValueSupplier the supplier of the initial value optionally called
   * @param <T>  the type of the data
   * @return the local data
   */
  @GenIgnore
  default <T> T getLocal(ContextLocal<T> key, Supplier<? extends T> initialValueSupplier) {
    return getLocal(key, AccessMode.CONCURRENT, initialValueSupplier);
  }

  /**
   * <p>Associate local data with {@code key} using the concurrent access mode.</p>
   *
   * @param key  the key of the data
   * @param value  the data
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default <T> void putLocal(ContextLocal<T> key, T value) {
    putLocal(key, AccessMode.CONCURRENT, value);
  }

  /**
   * <p>Remove local data associated with {@code key} using the concurrent access mode.</p>
   *
   * @param key  the key to be removed
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default <T> void removeLocal(ContextLocal<T> key) {
    putLocal(key, null);
  }

  /**
   * <p>Get local data associated with {@code key} using the specified access mode.</p>
   *
   * @param key  the key of the data
   * @param accessMode the access mode
   * @param <T>  the type of the data
   * @return the local data
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <T> T getLocal(ContextLocal<T> key, AccessMode accessMode);

  /**
   * <p>Get local data associated with {@code key} using the specified access mode.</p>
   *
   * <p>When it does not exist the {@code initialValueSupplier} is called to obtain the initial value.</p>
   *
   * <p> The {@code initialValueSupplier} might be called multiple times when multiple threads call this method concurrently.
   *
   * @param key  the key of the data
   * @param initialValueSupplier the supplier of the initial value optionally called
   * @param <T>  the type of the data
   * @return the local data
   */
  @GenIgnore
  <T> T getLocal(ContextLocal<T> key, AccessMode accessMode, Supplier<? extends T> initialValueSupplier);

  /**
   * <p>Associate local data with {@code key} using the specified access mode.</p>
   *
   * @param key  the key of the data
   * @param accessMode the access mode
   * @param value  the data
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <T> void putLocal(ContextLocal<T> key, AccessMode accessMode, T value);

  /**
   * <p>Remove local data associated with {@code key} using the specified access mode.</p>
   *
   * @param key  the key to be removed
   * @param accessMode the access mode
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default <T> void removeLocal(ContextLocal<T> key, AccessMode accessMode) {
    putLocal(key, accessMode, null);
  }
}
