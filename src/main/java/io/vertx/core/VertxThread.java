package io.vertx.core;

import io.vertx.core.impl.BlockedThreadChecker;
import io.vertx.core.impl.ContextInternal;

public interface VertxThread extends BlockedThreadChecker.Task {

	/**
	 * @return the current context of this thread, this method must be called from
	 *         the current thread
	 */
	ContextInternal context();

	boolean isWorker();

	StackTraceElement[] getStackTrace();

	void setDaemon(boolean b);

	/**
	 * Check whether the thread execution time can be tracked.
	 * @return
	 */
	boolean isTrackable();

	/**
	 * Return the actual {@link Thread} that corresponds to the {@link VertxThread}.
	 * @return
	 */
	Thread getThread();

}
