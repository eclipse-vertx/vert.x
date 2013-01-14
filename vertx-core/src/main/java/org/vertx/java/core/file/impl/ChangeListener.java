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

package org.vertx.java.core.file.impl;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;

import org.vertx.java.core.file.impl.FolderWatcher.WatchDirContext;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * Used by FolderWatcher to inform the application about file system 
 * events that happened.
 * 
 * @author Juergen Donnerstag
 */
public abstract class ChangeListener {

	private static final Logger log = LoggerFactory.getLogger(ChangeListener.class);

	// The events the listener is interested in
	private final Kind<?>[] eventTypes;

	// Will be injected prior to calling any of the onXXX() handler 
	// (and removed again afterwards)
	private FolderWatcher watcher;
	private Path rootDir;
	
	/**
	 * Constructor: All events
	 */
	public ChangeListener() {
		this(StandardWatchEventKinds.ENTRY_CREATE, 
				StandardWatchEventKinds.ENTRY_DELETE, 
				StandardWatchEventKinds.ENTRY_MODIFY);
	}
	
	/**
	 * Constructor
	 * 
	 * @param eventTypes
	 */
	public ChangeListener(final Kind<?>...eventTypes) {
		this.eventTypes = eventTypes;
	}

	/**
	 * @return The events the listener interested in
	 */
	public Kind<?>[] getEventTypes() {
		return eventTypes;
	}

	/**
	 * NOT PART OF THE PUBLIC API. 
	 * 
	 * @param key
	 */
	public void _injectData(final FolderWatcher watcher, final Path rootDir) {
		this.watcher = watcher;
		this.rootDir = rootDir;
	}

	/**
	 * Cancel the registration for this listener. Remaining events for this key will
	 * still be processed.
	 */
	public void cancel() {
		if (watcher != null && rootDir != null) {
			watcher.unregister(rootDir);
		} else {
			log.error("Can't cancel WatchService registration: Either 'watcher' or 'rootDir' is null.");
		}
	}
	
	/**
	 * This is the most fine grained event handler which gets invoked for each and every event.
	 * 
	 * @param event
	 * @param wdir
	 */
	public void onEvent(final WatchEvent<Path> event, final WatchDirContext wdir) {
	}
	
	/**
	 * This (aggregation) event handler gets invoked when something (whatever) has changed in any of 
	 * the registered directories or subdirectories. It gets called only once per file tree.
	 * <p>
	 * {@link WatchDirContext#isRoot()} may be used to determine if this is the registered root 
	 * directory in case of recursive registrations.
	 * 
	 * @param wdir
	 * @param currentMillis
	 */
	public void onDirectoryChanged(final WatchDirContext wdir, final long currentMillis) {
	}

	/**
	 * Similar to {@link #onDirectoryChanged(WatchDirContext, long)}, but its only invoked if 
	 * no new changes occurred during the last X millis (grace period).
	 * 
	 * @param wdir
	 */
	public void onGraceEvent(final WatchDirContext wdir) {
	}
}