/*
 * Copyright 2009 Red Hat, Inc.
 *
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
 *
 */

package io.vertx.core.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.Executor;

/**
 * A factory for producing executors that run all tasks in order, which delegate to a single common executor instance.
 *
 * @author <a href="david.lloyd@jboss.com">David Lloyd</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 */
// TODO: 16/12/20 by zmyer
public class OrderedExecutorFactory {
    // It must be package-protected rather than `private` in order to avoid the generation of an accessor, which would
    // cause Quasar 0.7.5-SNAPSHOT+ to print print an harmless error message during vert.x boot when using the agent
    // instrumentation method. See https://github.com/puniverse/quasar/issues/160#issuecomment-205977756 for details.
    static final Logger log = LoggerFactory.getLogger(OrderedExecutorFactory.class);
    //执行器对象
    private final Executor parent;

    /**
     * Construct a new instance delegating to the given parent executor.
     *
     * @param parent the parent executor
     */
    public OrderedExecutorFactory(Executor parent) {
        this.parent = parent;
    }

    /**
     * Get an executor that always executes tasks in order.
     *
     * @return an ordered executor
     */
    // TODO: 16/12/22 by zmyer
    public OrderedExecutor getExecutor() {
        return new OrderedExecutor(parent);
    }

    /**
     * An executor that always runs all tasks in order, using a delegate executor to run the tasks.
     * <p/>
     * More specifically, any call B to the {@link #execute(Runnable)} method that happens-after another call A to the
     * same method, will result in B's task running after A's.
     */
    // TODO: 16/12/22 by zmyer
    private static final class OrderedExecutor implements Executor {
        // @protectedby tasks
        //任务列表
        private final LinkedList<Runnable> tasks = new LinkedList<>();

        // @protectedby tasks
        //执行器是否运行标记
        private boolean running;
        //执行器
        private final Executor parent;
        //运行对象
        private final Runnable runner;

        /**
         * Construct a new instance.
         *
         * @param parent the parent executor
         */
        // TODO: 16/12/22 by zmyer
        public OrderedExecutor(Executor parent) {
            this.parent = parent;
            //创建运行对象
            runner = () -> {
                for (; ; ) {
                    final Runnable task;
                    synchronized (tasks) {
                        //从任务队列中获取需要执行的任务
                        task = tasks.poll();
                        if (task == null) {
                            //标记当前的列表为空
                            running = false;
                            return;
                        }
                    }
                    try {
                        //开始执行任务
                        task.run();
                    } catch (Throwable t) {
                        log.error("Caught unexpected Throwable", t);
                    }
                }
            };
        }

        /**
         * Run a task.
         *
         * @param command the task to run.
         */
        // TODO: 16/12/22 by zmyer
        public void execute(Runnable command) {
            synchronized (tasks) {
                //将需要执行的任务插入到任务列表中
                tasks.add(command);
                if (!running) {
                    //标记当前列表中存在需要执行的任务
                    running = true;
                    //开始执行任务
                    parent.execute(runner);
                }
            }
        }
    }
}
