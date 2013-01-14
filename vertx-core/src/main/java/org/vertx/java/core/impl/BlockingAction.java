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

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;

/**
 * Run specific blocking actions on the worker pool.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BlockingAction<T> {

	private Context context;

	private final VertxInternal vertx;
	private final AsyncResultHandler<T> handler;

	/**
	 * Constructor
	 * 
	 * @param vertx
	 */
	public BlockingAction(VertxInternal vertx) {
		this(vertx, null);
	}

	/**
	 * Constructor
	 * 
	 * @param vertx
	 * @param handler
	 */
	public BlockingAction(VertxInternal vertx, AsyncResultHandler<T> handler) {
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
	 */
	public void run() {
		context = vertx.getOrAssignContext();

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

				// Either execute the done-handler provided (if handler != null), or the
				// subclassed handle() method. In any case, execute in the correct
				// context.
				final AsyncResult<T> theRes = res;
				if (handler != null) {
					context.execute(new Runnable() {
						@Override
						public void run() {
							handler.handle(theRes);
						}
					});
				} else {
					context.execute(new Runnable() {
						@Override
						public void run() {
							handle(theRes);
						}
					});
				}
			}
		};

		// Execute the background job
		context.executeOnWorker(runner);
	}

	/**
	 * Subclasses may provide respective functionality. It is only invoked if
	 * handler == null. It gets executed by the right context.
	 * 
	 * @param result
	 */
	protected void handle(final AsyncResult<T> result) {
	}

	public abstract T action() throws Exception;
}
