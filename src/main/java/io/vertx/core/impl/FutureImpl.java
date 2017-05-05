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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

// TODO: 16/12/29 by zmyer
class FutureImpl<T> implements Future<T>, Handler<AsyncResult<T>> {
    //失败标记
    private boolean failed;
    //成功标记
    private boolean succeeded;
    //结果处理对象
    private Handler<AsyncResult<T>> handler;
    //结果对象
    private T result;
    //异常对象
    private Throwable throwable;

    /**
     * Create a future that hasn't completed yet
     */
    FutureImpl() {
    }

    /**
     * Create a future that has already failed
     *
     * @param t the throwable
     */
    FutureImpl(Throwable t) {
        fail(t != null ? t : new NoStackTraceThrowable(null));
    }

    /**
     * Create a future that has already failed
     *
     * @param failureMessage the failure message
     */
    FutureImpl(String failureMessage) {
        this(new NoStackTraceThrowable(failureMessage));
    }

    /**
     * Create a future that has already succeeded
     *
     * @param result the result
     */
    FutureImpl(T result) {
        complete(result);
    }

    /**
     * The result of the operation. This will be null if the operation failed.
     */
    public T result() {
        return result;
    }

    /**
     * An exception describing failure. This will be null if the operation succeeded.
     */
    public Throwable cause() {
        return throwable;
    }

    /**
     * Did it succeeed?
     */
    public boolean succeeded() {
        return succeeded;
    }

    /**
     * Did it fail?
     */
    public boolean failed() {
        return failed;
    }

    /**
     * Has it completed?
     */
    public boolean isComplete() {
        return failed || succeeded;
    }

    /**
     * Set a handler for the result. It will get called when it's complete
     */
    // TODO: 16/12/29 by zmyer
    public Future<T> setHandler(Handler<AsyncResult<T>> handler) {
        this.handler = handler;
        checkCallHandler();
        return this;
    }

    /**
     * Set the result. Any handler will be called, if there is one
     */
    // TODO: 16/12/29 by zmyer
    public void complete(T result) {
        checkComplete();
        this.result = result;
        succeeded = true;
        checkCallHandler();
    }

    @Override
    public void complete() {
        complete(null);
    }

    // TODO: 16/12/29 by zmyer
    public void handle(Future<T> ar) {
        if (ar.succeeded()) {
            complete(ar.result());
        } else {
            fail(ar.cause());
        }
    }

    @Override
    public Handler<AsyncResult<T>> completer() {
        return this;
    }

    @Override
    public void handle(AsyncResult<T> ar) {
        if (ar.succeeded()) {
            complete(ar.result());
        } else {
            fail(ar.cause());
        }
    }

    /**
     * Set the failure. Any handler will be called, if there is one
     */
    // TODO: 16/12/29 by zmyer
    public void fail(Throwable throwable) {
        checkComplete();
        this.throwable = throwable != null ? throwable : new NoStackTraceThrowable(null);
        failed = true;
        checkCallHandler();
    }

    @Override
    public void fail(String failureMessage) {
        fail(new NoStackTraceThrowable(failureMessage));
    }

    // TODO: 16/12/29 by zmyer
    private void checkCallHandler() {
        if (handler != null && isComplete()) {
            handler.handle(this);
        }
    }

    private void checkComplete() {
        if (succeeded || failed) {
            throw new IllegalStateException("Result is already complete: " + (succeeded ? "succeeded" : "failed"));
        }
    }
}
