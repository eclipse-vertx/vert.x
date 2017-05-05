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

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/15 by zmyer
public class EventLoopContext extends ContextImpl {

    private static final Logger log = LoggerFactory.getLogger(EventLoopContext.class);

    // TODO: 16/12/15 by zmyer
    public EventLoopContext(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, String deploymentID, JsonObject config,
                            ClassLoader tccl) {
        super(vertx, internalBlockingPool, workerPool, deploymentID, config, tccl);
    }

    // TODO: 16/12/15 by zmyer
    public void executeAsync(Handler<Void> task) {
        // No metrics, we are on the event loop.
        //在netty事件处理对象中执行指定的任务
        nettyEventLoop().execute(wrapTask(null, task, true, null));
    }

    @Override
    public boolean isEventLoopContext() {
        return true;
    }

    @Override
    public boolean isMultiThreadedWorkerContext() {
        return false;
    }

    // TODO: 16/12/15 by zmyer
    @Override
    protected void checkCorrectThread() {
        //获取当前的执行线程
        Thread current = Thread.currentThread();
        //如果当前的执行线程不是vertx线程
        if (!(current instanceof VertxThread)) {
            throw new IllegalStateException("Expected to be on Vert.x thread, but actually on: " + current);
        } else if (contextThread != null && current != contextThread) {
            throw new IllegalStateException("Event delivered on unexpected thread " + current + " expected: " + contextThread);
        }
    }

}
