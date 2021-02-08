package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;


/**
 * A context which is built from a delegate context with some differences.
 * <br><br>
 * Compared to the delegate, this context has
 * <ul>
 *   <li>the same concurrency</li>
 *   <li>the same exception handler</li>
 *   <li>the same deployment</li>
 *   <li>the same config</li>
 *   <li>the same classloader</li>
 * </ul>
 * This context might have its own
 * <ul>
 *   <li>context data</li>
 *   <li>worker pool (includes internal)</li>
 *   <li>blocking task queues (includes internal)</li>
 * </ul>
 * This will have its own local context data.
 * <br><br>
 * Using substitutes should be avoided if at all possible as they break many of the assumption that surround contexts
 * in vertx. Handlers that return a substituted context should always be documented to avoid end user issues.
 *
 * @see ContextSubstitution
 *
 * @author <a href="mailto:greg@thetracys.net">Gregory Tracy (Gattag)</a>
 */
final class SubstitutedContext extends AbstractContext {

  private final AbstractContext root;
  private final AbstractContext parent;
  private final ContextSubstitution substitution;
  private ConcurrentMap<Object, Object> localData;

  SubstitutedContext(AbstractContext parent, Function<ContextInternal, ContextSubstitution> builder){
    this.parent = parent;
    this.root = parent.root();
    this.substitution = builder.apply(this);
  }

  @Override
  public final String deploymentID() {
    return this.root.deploymentID();
  }

  @Override
  public final @Nullable JsonObject config() {
    return this.root.config();
  }

  @Override
  public final boolean isEventLoopContext() {
    return this.root.isEventLoopContext();
  }

  @Override
  final boolean inThread() {
    return this.root.inThread();
  }

  @Override
  public final CloseHooks closeHooks() {
    return this.root.closeHooks();
  }

  @Override
  public final void runOnContext(Handler<Void> action) {
    this.root.runOnContext(this, action);
  }

  @Override
  final void runOnContext(AbstractContext ctx, Handler<Void> action) {
    this.root.runOnContext(this, action);
  }

  @Override
  public final void execute(Runnable task) {
    this.root.execute(this, task);
  }

  @Override
  final <T> void execute(AbstractContext ctx, Runnable task) {
    this.root.execute(this, task);
  }

  @Override
  public final <T> void execute(T argument, Handler<T> task) {
    this.root.execute(this, argument, task);
  }

  @Override
  final <T> void execute(AbstractContext ctx, T argument, Handler<T> task) {
    this.root.execute(this, argument, task);
  }

  @Override
  public final <T> void emit(T argument, Handler<T> task) {
    this.root.emit(this, argument, task);
  }

  @Override
  final <T> void emit(AbstractContext ctx, T argument, Handler<T> task) {
    this.root.emit(this, argument, task);
  }

  @Override
  public final EventLoop nettyEventLoop() {
    return this.root.nettyEventLoop();
  }

  @Override
  public final <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
    return ContextImpl.executeBlocking(this, action, this.workerPoolInternal(), this.orderedTasksInternal());
  }

  @Override
  public final <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered) {
    return ContextImpl.executeBlocking(this, action, this.workerPoolInternal(), ordered ? this.orderedTasksInternal() : null);
  }

  @Override
  public final <T> Future<T> executeBlocking(Handler<Promise<T>> action, boolean ordered) {
    return ContextImpl.executeBlocking(this, action, this.workerPool(), ordered ? this.orderedTasks(): null);
  }

  @Override
  public final <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
    return ContextImpl.executeBlocking(this, blockingCodeHandler, this.workerPool(), queue);
  }

  @Override
  public final Deployment getDeployment() {
    return this.root.getDeployment();
  }

  @Override
  public final VertxInternal owner() {
    return this.root.owner();
  }

  @Override
  public final int getInstanceCount() {
    return this.root.getInstanceCount();
  }

  @Override
  public final Context exceptionHandler(@Nullable Handler<Throwable> handler) {
    return this.root.exceptionHandler(handler);
  }

  @Override
  public final @Nullable Handler<Throwable> exceptionHandler() {
    return this.root.exceptionHandler();
  }

  @Override
  public final void reportException(Throwable t) {
    this.root.reportException(t);
  }

  @Override
  public final ConcurrentMap<Object, Object> contextData() {
    return this.substitution.contextData();
  }

  @Override
  public final synchronized ConcurrentMap<Object, Object> localContextData() {
    if (this.localData == null) {
      this.localData = new ConcurrentHashMap<>();
    }
    return this.localData;
  }

  @Override
  public final ClassLoader classLoader() {
    return this.root.classLoader();
  }

  @Override
  public final WorkerPool workerPool() {
    return this.substitution.workerPool();
  }

  @Override
  public final WorkerPool workerPoolInternal() {
    return this.substitution.workerPoolInternal();
  }

  @Override
  public final TaskQueue orderedTasks() {
    return this.substitution.orderedTasks();
  }

  @Override
  public final TaskQueue orderedTasksInternal() {
    return this.substitution.orderedTasksInternal();
  }

  @Override
  public final VertxTracer tracer() {
    return this.root.tracer();
  }

  @Override
  public final ContextInternal duplicate() {
    return new DuplicatedContext(this);
  }

  @Override
  public final ContextInternal duplicateDelegate() {
    return this;
  }

  @Override
  public final ContextInternal substituteParent() {
    return this.parent;
  }

  @Override
  public final AbstractContext root() {
    return this.root;
  }

  @Override
  public final boolean isDeployment() {
    return this.root.isDeployment();
  }

  @Override
  public final void addCloseHook(Closeable hook) {
    this.root.addCloseHook(hook);
  }

  @Override
  public final void removeCloseHook(Closeable hook) {
    this.root.removeCloseHook(hook);
  }
}
