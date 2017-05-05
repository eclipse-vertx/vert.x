/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.vertx.core.*;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/15 by zmyer
public abstract class ContextImpl implements ContextInternal {

    private static final Logger log = LoggerFactory.getLogger(ContextImpl.class);

    private static final String THREAD_CHECKS_PROP_NAME = "vertx.threadChecks";
    private static final String DISABLE_TIMINGS_PROP_NAME = "vertx.disableContextTimings";
    private static final String DISABLE_TCCL_PROP_NAME = "vertx.disableTCCL";
    private static final boolean THREAD_CHECKS = Boolean.getBoolean(THREAD_CHECKS_PROP_NAME);
    private static final boolean DISABLE_TIMINGS = Boolean.getBoolean(DISABLE_TIMINGS_PROP_NAME);
    private static final boolean DISABLE_TCCL = Boolean.getBoolean(DISABLE_TCCL_PROP_NAME);

    //所属的vertx对象
    protected final VertxInternal owner;
    protected final String deploymentID;
    //配置信息
    protected final JsonObject config;
    //部署对象
    private Deployment deployment;
    //关闭hook对象
    private CloseHooks closeHooks;
    //类加载器对象
    private final ClassLoader tccl;
    //事件处理器
    private final EventLoop eventLoop;
    //vertx线程对象
    protected VertxThread contextThread;
    //上下文对象中的属性数据
    private Map<String, Object> contextData;
    //异步事件处理对象
    private volatile Handler<Throwable> exceptionHandler;
    //工作对象池
    protected final WorkerPool workerPool;
    //内部阻塞式工作对象池
    protected final WorkerPool internalBlockingPool;
    //内部顺序执行线程对象
    protected final Executor orderedInternalPoolExec;
    //工作线程对象
    protected final Executor workerExec;

    // TODO: 16/12/15 by zmyer
    protected ContextImpl(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, String deploymentID, JsonObject config,
                          ClassLoader tccl) {
        if (DISABLE_TCCL && !tccl.getClass().getName().equals("sun.misc.Launcher$AppClassLoader")) {
            log.warn("You have disabled TCCL checks but you have a custom TCCL to set.");
        }
        this.deploymentID = deploymentID;
        this.config = config;
        EventLoopGroup group = vertx.getEventLoopGroup();
        if (group != null) {
            this.eventLoop = group.next();
        } else {
            this.eventLoop = null;
        }
        this.tccl = tccl;
        this.owner = vertx;
        this.workerPool = workerPool;
        this.internalBlockingPool = internalBlockingPool;
        this.orderedInternalPoolExec = internalBlockingPool.createOrderedExecutor();
        this.workerExec = workerPool.createOrderedExecutor();
        this.closeHooks = new CloseHooks(log);
    }

    // TODO: 16/12/15 by zmyer
    public static void setContext(ContextImpl context) {
        Thread current = Thread.currentThread();
        if (current instanceof VertxThread) {
            //设置当前线程执行上下文对象
            setContext((VertxThread) current, context);
        } else {
            throw new IllegalStateException("Attempt to setContext on non Vert.x thread " + Thread.currentThread());
        }
    }

    // TODO: 16/12/15 by zmyer
    private static void setContext(VertxThread thread, ContextImpl context) {
        //设置线程的执行上下文对象
        thread.setContext(context);
        if (!DISABLE_TCCL) {
            if (context != null) {
                context.setTCCL();
            } else {
                //设置当前线程的类加载器对象
                Thread.currentThread().setContextClassLoader(null);
            }
        }
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public void addCloseHook(Closeable hook) {
        closeHooks.add(hook);
    }

    public void removeCloseHook(Closeable hook) {
        closeHooks.remove(hook);
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public WorkerExecutor createWorkerExecutor() {
        return new WorkerExecutorImpl(this, workerPool, false);
    }

    // TODO: 16/12/15 by zmyer
    public void runCloseHooks(Handler<AsyncResult<Void>> completionHandler) {
        closeHooks.run(completionHandler);
        // Now remove context references from threads
        VertxThreadFactory.unsetContext(this);
    }

    protected abstract void executeAsync(Handler<Void> task);

    @Override
    public abstract boolean isEventLoopContext();

    @Override
    public abstract boolean isMultiThreadedWorkerContext();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) contextData().get(key);
    }

    @Override
    public void put(String key, Object value) {
        contextData().put(key, value);
    }

    @Override
    public boolean remove(String key) {
        return contextData().remove(key) != null;
    }

    @Override
    public boolean isWorkerContext() {
        return !isEventLoopContext();
    }

    public static boolean isOnWorkerThread() {
        return isOnVertxThread(true);
    }

    public static boolean isOnEventLoopThread() {
        return isOnVertxThread(false);
    }

    public static boolean isOnVertxThread() {
        Thread t = Thread.currentThread();
        return (t instanceof VertxThread);
    }

    // TODO: 16/12/15 by zmyer
    private static boolean isOnVertxThread(boolean worker) {
        Thread t = Thread.currentThread();
        if (t instanceof VertxThread) {
            VertxThread vt = (VertxThread) t;
            return vt.isWorker() == worker;
        }
        return false;
    }

    // This is called to execute code where the origin is IO (from Netty probably).
    // In such a case we should already be on an event loop thread (as Netty manages the event loops)
    // but check this anyway, then execute directly
    // TODO: 16/12/15 by zmyer
    public void executeFromIO(ContextTask task) {
        if (THREAD_CHECKS) {
            checkCorrectThread();
        }
        // No metrics on this, as we are on the event loop.
        wrapTask(task, null, true, null).run();
    }

    protected abstract void checkCorrectThread();

    // Run the task asynchronously on this same context
    // TODO: 16/12/15 by zmyer
    @Override
    public void runOnContext(Handler<Void> task) {
        try {
            //异步执行任务
            executeAsync(task);
        } catch (RejectedExecutionException ignore) {
            // Pool is already shut down
        }
    }

    @Override
    public String deploymentID() {
        return deploymentID;
    }

    @Override
    public JsonObject config() {
        return config;
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public List<String> processArgs() {
        // As we are maintaining the launcher and starter class, choose the right one.
        List<String> processArgument = VertxCommandLauncher.getProcessArguments();
        return processArgument != null ? processArgument : Starter.PROCESS_ARGS;
    }

    public EventLoop nettyEventLoop() {
        return eventLoop;
    }

    public Vertx owner() {
        return owner;
    }

    // Execute an internal task on the internal blocking ordered executor
    // TODO: 16/12/15 by zmyer
    public <T> void executeBlocking(Action<T> action, Handler<AsyncResult<T>> resultHandler) {
        //阻塞式调用任务
        executeBlocking(action, null, resultHandler, orderedInternalPoolExec, internalBlockingPool.metrics());
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
        executeBlocking(null, blockingCodeHandler, resultHandler, ordered ? workerExec : workerPool.executor(), workerPool.metrics());
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
        executeBlocking(blockingCodeHandler, true, resultHandler);
    }

    // TODO: 16/12/15 by zmyer
    <T> void executeBlocking(Action<T> action, Handler<Future<T>> blockingCodeHandler,
                             Handler<AsyncResult<T>> resultHandler,
                             Executor exec, PoolMetrics metrics) {
        //获取队列的统计对象
        Object queueMetric = metrics != null ? metrics.submitted() : null;
        try {
            //开始执行任务
            exec.execute(() -> {
                Object execMetric = null;
                if (metrics != null) {
                    //启动统计对象
                    execMetric = metrics.begin(queueMetric);
                }
                Future<T> res = Future.future();
                try {
                    if (blockingCodeHandler != null) {
                        //设置执行上下文对象
                        ContextImpl.setContext(this);
                        //开始处理任务
                        blockingCodeHandler.handle(res);
                    } else {
                        //获取任务执行结果
                        T result = action.perform();
                        //触发异步对象
                        res.complete(result);
                    }
                } catch (Throwable e) {
                    res.fail(e);
                }

                if (metrics != null) {
                    //结束统计
                    metrics.end(execMetric, res.succeeded());
                }

                if (resultHandler != null) {
                    //
                    runOnContext(v -> res.setHandler(resultHandler));
                }
            });
        } catch (RejectedExecutionException e) {
            // Pool is already shut down
            if (metrics != null) {
                metrics.rejected(queueMetric);
            }
            throw e;
        }
    }

    // TODO: 16/12/15 by zmyer
    protected synchronized Map<String, Object> contextData() {
        if (contextData == null) {
            contextData = new ConcurrentHashMap<>();
        }
        return contextData;
    }

    // TODO: 16/12/15 by zmyer
    protected Runnable wrapTask(ContextTask cTask, Handler<Void> hTask, boolean checkThread, PoolMetrics metrics) {
        Object metric = metrics != null ? metrics.submitted() : null;
        return () -> {
            //读取当前的线程对象
            Thread th = Thread.currentThread();
            if (!(th instanceof VertxThread)) {
                throw new IllegalStateException("Uh oh! Event loop context executing with wrong thread! Expected " + contextThread + " got " + th);
            }
            VertxThread current = (VertxThread) th;
            if (THREAD_CHECKS && checkThread) {
                if (contextThread == null) {
                    //设置上下文线程对象
                    contextThread = current;
                } else if (contextThread != current && !contextThread.isWorker()) {
                    throw new IllegalStateException("Uh oh! Event loop context executing with wrong thread! Expected " + contextThread + " got " + current);
                }
            }
            if (metrics != null) {
                //启动统计对象
                metrics.begin(metric);
            }
            if (!DISABLE_TIMINGS) {
                //线程开始执行
                current.executeStart();
            }
            try {
                //设置线程执行上下文对象
                setContext(current, ContextImpl.this);
                if (cTask != null) {
                    //启动上下文任务
                    cTask.run();
                } else {
                    //开始处理任务对象
                    hTask.handle(null);
                }
                if (metrics != null) {
                    //结束统计
                    metrics.end(metric, true);
                }
            } catch (Throwable t) {
                log.error("Unhandled exception", t);
                Handler<Throwable> handler = this.exceptionHandler;
                if (handler == null) {
                    handler = owner.exceptionHandler();
                }
                if (handler != null) {
                    //开始进行异常处理
                    handler.handle(t);
                }
                if (metrics != null) {
                    metrics.end(metric, false);
                }
            } finally {
                // We don't unset the context after execution - this is done later when the context is closed via
                // VertxThreadFactory
                if (!DISABLE_TIMINGS) {
                    //结束线程
                    current.executeEnd();
                }
            }
        };
    }

    // TODO: 16/12/15 by zmyer
    private void setTCCL() {
        Thread.currentThread().setContextClassLoader(tccl);
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public Context exceptionHandler(Handler<Throwable> handler) {
        exceptionHandler = handler;
        return this;
    }

    @Override
    public Handler<Throwable> exceptionHandler() {
        return exceptionHandler;
    }

    // TODO: 16/12/15 by zmyer
    public int getInstanceCount() {
        // the no verticle case
        if (deployment == null) {
            return 0;
        }

        // the single verticle without an instance flag explicitly defined
        if (deployment.deploymentOptions() == null) {
            return 1;
        }
        return deployment.deploymentOptions().getInstances();
    }
}
