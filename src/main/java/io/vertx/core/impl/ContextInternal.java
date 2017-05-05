/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

/**
 * This interface provides an api for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
// TODO: 16/12/29 by zmyer
public interface ContextInternal extends Context {

    /**
     * Return the Netty EventLoop used by this Context. This can be used to integrate
     * a Netty Server with a Vert.x runtime, specially the Context part.
     *
     * @return the EventLoop
     */
    EventLoop nettyEventLoop();

    /**
     * Create a worker executor using the underlying worker pool of the context.
     * <p>
     * The executor does not have to be closed, as the worker pool is managed by the context itself.
     * <p>
     * It should be used when a separate executor per context is needed.
     *
     * @return a new worker executor
     */
    WorkerExecutor createWorkerExecutor();

    /**
     * Execute the context task and switch on this context if necessary, this also associates the
     * current thread with the current context so {@link Vertx#currentContext()} returns this context.<p/>
     * <p>
     * The caller thread should be the the event loop thread of this context.<p/>
     * <p>
     * Any exception thrown from the {@literal stack} will be reported on this context.
     *
     * @param task the task to execute
     */
    void executeFromIO(ContextTask task);
}
