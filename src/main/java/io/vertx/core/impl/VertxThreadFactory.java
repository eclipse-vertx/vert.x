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

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 17/1/1 by zmyer
public class VertxThreadFactory implements ThreadFactory {

    // We store all threads in a weak map - we retain this so we can unset context from threads when
    // context is undeployed
    private static final Object FOO = new Object();
    //线程集合
    private static Map<VertxThread, Object> weakMap = new WeakHashMap<>();

    // TODO: 17/1/1 by zmyer
    private static synchronized void addToMap(VertxThread thread) {
        weakMap.put(thread, FOO);
    }

    //线程前缀
    private final String prefix;
    //线程数统计
    private final AtomicInteger threadCount = new AtomicInteger(0);
    //线程检查对象
    private final BlockedThreadChecker checker;
    //是否是worker节点
    private final boolean worker;
    //最大执行时间
    private final long maxExecTime;

    // TODO: 17/1/1 by zmyer
    VertxThreadFactory(String prefix, BlockedThreadChecker checker, boolean worker, long maxExecTime) {
        this.prefix = prefix;
        this.checker = checker;
        this.worker = worker;
        this.maxExecTime = maxExecTime;
    }

    // TODO: 17/1/1 by zmyer
    public static synchronized void unsetContext(ContextImpl ctx) {
        for (VertxThread thread : weakMap.keySet()) {
            if (thread.getContext() == ctx) {
                thread.setContext(null);
            }
        }
    }

    // TODO: 17/1/1 by zmyer
    public Thread newThread(Runnable runnable) {
        //创建vertx线程对象
        VertxThread t = new VertxThread(runnable, prefix + threadCount.getAndIncrement(), worker, maxExecTime);
        // Vert.x threads are NOT daemons - we want them to prevent JVM exit so embededd user doesn't
        // have to explicitly prevent JVM from exiting.
        if (checker != null) {
            //在检查对象中注册该线程
            checker.registerThread(t);
        }
        //将创建的线程插入到线程集合中
        addToMap(t);
        // I know the default is false anyway, but just to be explicit-  Vert.x threads are NOT daemons
        // we want to prevent the JVM from exiting until Vert.x instances are closed
        //创建的线程为非守护线程
        t.setDaemon(false);
        //返回创建的线程
        return t;
    }
}
