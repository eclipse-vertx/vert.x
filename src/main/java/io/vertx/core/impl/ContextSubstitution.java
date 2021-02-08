package io.vertx.core.impl;

import io.vertx.core.Context;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Used to substitute certain functionality of a context. The constructor should be supplied to
 * {@link ContextInternal#substitute(Function)} to get a context that substitutes some of the parent behavior with what
 * is provided by this.
 *
 * @author <a href="mailto:greg@thetracys.net">Gregory Tracy (Gattag)</a>
 */
public abstract class ContextSubstitution {

  private final ContextInternal context;

  public ContextSubstitution(ContextInternal context) {
    this.context = context;
  }

  /**
   * @return the context which has the substituted items
   */
  public final ContextInternal getContext(){
    return this.context;
  }

  /**
   * @return the delegate of the substituted context ({@link #getContext()})
   */
  public final ContextInternal getParent(){
    return this.context.substituteParent();
  }

  /**
   * @return the base implementing context (a {@link ContextImpl})
   */
  public final ContextInternal getRoot() { return ((AbstractContext)this.context).root(); }

  /**
   * @return the {@link ConcurrentMap} used to store context data
   * @see Context#get(String)
   * @see Context#put(String, Object)
   */
  ConcurrentMap<Object, Object> contextData(){
    return this.getParent().contextData();
  }

  /**
   * @return the context worker pool
   */
  WorkerPool workerPool(){
    return this.getParent().workerPool();
  }

  /**
   * @return the context internal worker pool
   */
  WorkerPool workerPoolInternal(){
    return this.getParent().workerPoolInternal();
  }

  /**
   * @return the context task queue
   */
  TaskQueue orderedTasks(){
    return this.getParent().orderedTasks();
  }

  /**
   * @return the context internal task queue
   */
  TaskQueue orderedTasksInternal(){
    return this.getParent().orderedTasksInternal();
  }

  public static final Function<ContextInternal, ContextSubstitution> TASK_QUEUE = c -> new ContextSubstitution(c) {

    private TaskQueue taskQueue;

    @Override
    synchronized TaskQueue orderedTasks() {
      if(this.taskQueue == null){
        this.taskQueue = new TaskQueue();
      }
      return this.taskQueue;
    }
  };

}
