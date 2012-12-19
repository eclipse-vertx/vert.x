/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * Run specific blocking actions on the worker pool.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BlockingAction<T> {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(BlockingAction.class);

	// The context when run() was invoked. Which is the context the handler() will 
	// executed in (compared the background "action")
	private Context context;

	private final VertxInternal vertx;
	private final AsyncResultHandler<T> handler;

	// Make sure the user provided handler gets executed only once
	private boolean done;
	
	/**
	 * Constructor
	 * 
	 * @param vertx
	 */
	public BlockingAction(final VertxInternal vertx) {
		this(vertx, null);
	}

	/**
	 * Constructor
	 * 
	 * @param vertx
	 * @param handler
	 */
	public BlockingAction(final VertxInternal vertx, final AsyncResultHandler<T> handler) {
		this.vertx = vertx;
		this.handler = handler;
	}
	
	/**
	 * Determines the Context once the BlockingAction has started execution
	 * (run()). Via this reader method it's made available to the handler.
	 * 
	 * @return
	 */
	public final Context context() {
		return context;
	}

	/**
	 * Run the blocking action using a thread from the worker pool.
	 * 
	 * @param timeout
	 * @param unit
	 * @param message
	 * @return The Future used to wait for the background job
	 */
	public ActionFuture<T> run(final long timeout, final TimeUnit unit, final String message) {
		ActionFuture<T> future = run();
		if (future.get(timeout, unit) == null) {
      onTimeout(message != null ? message : "Timeout: Background job did not finish in due time", future);
		}
		return future;
	}
	
	/**
	 * Run the blocking action using a thread from the worker pool.
	 * 
	 * @return A Future which can be used to wait for the completion of the background job
	 */
	public ActionFuture<T> run() {
	  final ActionFuture<T> future = new ActionFuture<T>();

		Runnable runner = new Runnable() {
			@Override
			public void run() {
				// Execute the action and handle the return status
				AsyncResult<T> res;
				try {
					final T result = action();
					res = new AsyncResult<>(result);
				} catch (final Exception e) {
					res = new AsyncResult<>(e);
				}

				executeInContext(res, future);
			}
		};

		// Execute the background job
		this.context = vertx.getOrAssignContext();
		context.executeOnWorker(runner);
		
		return future;
	}

	/**
	 * 
	 * @param res
	 */
	private void executeInContext(final AsyncResult<T> res, final ActionFuture<T> future) {
		context.execute(new Runnable() {
			@Override
			public void run() {
				doHandle(res, future);
			}
		});
	}

	/**
	 * Either execute the done-handler provided (if handler != null), or the
	 * subclassed handle() method. In any case, execute in the correct
	 * context.
	 * <p>
	 * Make sure the handler gets executed only once
	 * 
	 * @param res
	 * @param future
	 */
	private void doHandle(final AsyncResult<T> res, final ActionFuture<T> future) {
		if (this.done == false) {
			synchronized (this) {
				if (this.done == false) {
					this.done = true;
					if (handler != null) {
						handler.handle(res);
					} else {
						BlockingAction.this.handle(res);
					}
			    future.countDown(res);
				}
			}
		}
	}
	
	/**
	 * The result handler gets executed after the action was executed.
	 * Subclasses may provide respective functionality. It is only invoked if
	 * handler == null. It gets executed by the right context.
	 * 
	 * @param result
	 */
	protected void handle(final AsyncResult<T> result) {
	}

	/**
	 * The actual action to be performed in the background
	 * 
	 * @return
	 * @throws Exception
	 */
	public abstract T action() throws Exception;

	/**
	 * Invoked if the background job did not finish in time (and timeout was activated: timeout > 0).
	 * By default it invokes the handler with a TimeoutException.
	 * 
	 * @param timeoutMessage
	 */
	protected void onTimeout(final String timeoutMessage, final ActionFuture<T> future) {
    doHandle(new AsyncResult<T>(new TimeoutException(timeoutMessage)), future);
	}
}
