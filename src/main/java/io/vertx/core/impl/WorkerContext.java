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

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/15 by zmyer
public class WorkerContext extends ContextImpl {

    // TODO: 16/12/15 by zmyer
    public WorkerContext(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, String deploymentID,
                         JsonObject config, ClassLoader tccl) {
        super(vertx, internalBlockingPool, workerPool, deploymentID, config, tccl);
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public void executeAsync(Handler<Void> task) {
        //在给定的工作对象池中执行给定的任务
        workerExec.execute(wrapTask(null, task, true, workerPool.metrics()));
    }

    @Override
    public boolean isEventLoopContext() {
        return false;
    }

    @Override
    public boolean isMultiThreadedWorkerContext() {
        return false;
    }

    @Override
    protected void checkCorrectThread() {
        // NOOP
    }

    // In the case of a worker context, the IO will always be provided on an event loop thread, not a worker thread
    // so we need to execute it on the worker thread
    // TODO: 16/12/15 by zmyer
    @Override
    public void executeFromIO(ContextTask task) {
        workerExec.execute(wrapTask(task, null, true, workerPool.metrics()));
    }

}
