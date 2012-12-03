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

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.lang.Args;

/**
 * A generic FolderWatcher extending Java's WatchService with:
 * <ul>
 * <li>Easy testability</li>
 * <li>Recursive directory feature with easy access to original root directory</li>
 * <li>Easy cancellation of registrations (incl. recursion)</li>
 * <li>Aggregation handler: invoked once per directory irrespective of number 
 *     of events on that directory</li>
 * <li>Grace handler: invoked on root directory level after nothing has changed 
 *     in the file tree for at least a grace period</li>
 * <li>Support for change listener</li>
 * <li>Can start (in a separate thread) and wait for something to happen, or in case 
 *    you already have an event loop somewhere, call processEvents() to handle queued
 *    events.
 * </ul>
 * <p>
 * By default Java's default WatchService is used underneath, which means that FolderWatcher
 * has the very same limitations: 
 * <ul>
 * <li>what events are generated depends on the OS (filesystem). Quote from Java 7 API doc: 
 *  "The implementation that observes events from the file system is 
 *   intended to map directly on to the native file event notification 
 *   facility where available, or to use a primitive mechanism, such as 
 *   polling, when a native facility is not available. Consequently, many 
 *   of the details on how events are detected, their timeliness, and 
 *   whether their ordering is preserved are highly implementation specific."</li>
 * <li>More often than not, it does not work with remote filesystems (e.g. NFS is stateless). 
 *     Polling (Apache VFS DefaultFileMonitor) might be an alternative if needed. FolderWatcher
 *     allows to plugin any WatchService you like.</li>
 * </ul>
 * <p>
 * Subtleties:
 * <ul>
 * <li>Registered root directories (recursive == true) that have been removed on the file system 
 *    are not automatically watched again when mkdir is called to recreate it.</li>
 * </ul>
 * 
 * @author Juergen Donnerstag
 */
public class FolderWatcher {
	
	private static final Logger log = LoggerFactory.getLogger(FolderWatcher.class);

	// grace period in milli seconds (default)
	public static final long GRACE_PERIOD = 1000;
	
	// If started, the wake up timeout interval
	public static final long CHECK_PERIOD = 200;

	// The underlying watch service
	private final WatchService watchService;
	
	// WatchKey (as provided by WatchService) => WatchDir (context data per directory)
	private final Map<WatchKey, WatchDirContext> keys = new ConcurrentHashMap<>();

	// Time to wait before we fire a grace event
	private final long gracePeriod;
	
	// true, if the WatcherService has been closed
	private volatile boolean closed;
	
	/**
	 * Constructor
	 * 
	 * @throws IOException
	 */
	public FolderWatcher() throws IOException{
		this(GRACE_PERIOD);
	}

	/**
	 * Constructor
	 * 
	 * @throws IOException
	 */
	public FolderWatcher(long gracePeriod) throws IOException{
		this.watchService = newWatchService();
		if (this.watchService == null) {
			throw new NullPointerException("newWatchService() must not return null");
		}
		
		Args.isTrue(gracePeriod >= 0, "Argument 'gracePeriod' must not be < 0");
		this.gracePeriod = gracePeriod;
	}

	/**
	 * Provide a WatchService instance. 
	 * <p>
	 * You may provide your own service either via subclassing or via a java startup 
	 * parameter like <code>-D{this class name}={watchservice class name}</code>.
	 * 
	 * @return
	 * @throws IOException
	 */
	protected WatchService newWatchService() throws IOException {
		String className = System.getProperty(this.getClass().getName());
		if (className != null) {
			try {
				return (WatchService) this.getClass().getClassLoader().loadClass(className).newInstance();
			} catch (Exception ex) {
				log.error("Failed to instantiate WatchService. Will use default. Class name that failed: " + className, ex);
			}
		}
		return FileSystems.getDefault().newWatchService();
	}

	/**
	 * Shutdown the watch service
	 */
	public void close() {
		this.closed = true;

		// Release all monitors (otherwise rmdir might not work)
		for (WatchKey key: keys.keySet()) {
			key.cancel();
		}
		keys.clear();
		
		try {
			this.watchService.close();
		} catch (IOException ex) {
			// ignore
		}
		keys.clear();
	}

	/**
	 * Register a Path and optionally all its subdirectories
	 * 
	 * @param dir
	 * @param recursive
	 */
	public final void register(final Path dir, final boolean recursive) {
		register(dir, recursive, null);
	}

	/**
	 * Register a Path and optionally all its subdirectories. Upon 
	 * events the provided listener will be invoked.
	 * 
	 * @param dir
	 * @param recursive
	 * @param listener
	 */
	public final void register(final Path dir, final boolean recursive, final ChangeListener listener) {
		Args.notNull(dir, "dir");

		if (this.closed) {
			throw new ClosedWatchServiceException();
		}

		final WatchDirContext newRoot = newWatchDirContext(dir, null, recursive, listener);
		
		if (recursive == false) {
			doRegister(dir, newRoot);
		} else {
			try {
				// Walk the directory tree and register all directories recursively
		    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
	        @Override
	        public FileVisitResult preVisitDirectory(final Path curDir, final BasicFileAttributes attrs)
	            throws IOException {
	        	if (curDir.equals(dir)) {
	        		doRegister(curDir, newRoot);
	        	} else { 
	        		doRegister(curDir, newWatchDirContext(curDir, newRoot, true, listener));
	        	}
            return FileVisitResult.CONTINUE;
	        }
		    });
			} catch (IOException ex) {
				log.error("Failed to register file tree: " + dir, ex);
			}
		}
	}

	/**
	 * Internal helper to actually register a Path
	 * 
	 * @param dir
	 * @param context
	 */
	private void doRegister(final Path dir, final WatchDirContext context) {
		try {
			// Register the Path (may be a directory or a single file).
			// Initially ignore the event kinds possibly provided by listener 
			WatchKey key = dir.register(watchService, 
					StandardWatchEventKinds.ENTRY_CREATE, 
					StandardWatchEventKinds.ENTRY_DELETE, 
					StandardWatchEventKinds.ENTRY_MODIFY);
			
			// Associate some relevant data with the WatchKey
			keys.put(key, context);
		} catch (IOException ex) {
			log.error("Failed to register directory: " + dir, ex);
		}
	}
	
	/**
	 * If needed, provide your own, extended version with additional attributes, of WatchDirContext
	 * 
	 * @param dir
	 * @param root
	 * @param recursive
	 * @param listener
	 * @return
	 */
	protected WatchDirContext newWatchDirContext(final Path dir, final WatchDirContext root, final boolean recursive, final ChangeListener listener) {
		return new WatchDirContext(dir, root, recursive, listener);
	}
	
	/**
	 * Unregister an entry. If registered with recursive on, than unregister will be 
	 * recursive as well. If the provided path is not the root but a subdir, than only
	 * that subdir (and optionally its subdirs) are unregistered.
	 * 
	 * @param dir
	 */
	public int unregister(final Path dir) {
		Args.notNull(dir, "dir");

		int rtn = 0;
		
		// The amount of entries (dirs and subdirs) is likely not too large
		Iterator<Map.Entry<WatchKey, WatchDirContext>> iter = keys.entrySet().iterator();
		while(iter.hasNext()) {
			Map.Entry<WatchKey, WatchDirContext> entry = iter.next();
			WatchDirContext wdir = entry.getValue();
			
			// Apply the recursive option provided when it was registered
			if (wdir.recursive == false) {
				if (wdir.dir.equals(dir)) {
					entry.getKey().cancel();
					iter.remove();
					rtn += 1;
					break;
				}
			} else {
				if (dir.startsWith(wdir.dir)) {
					entry.getKey().cancel();
					iter.remove();
					rtn += 1;
				}
			}
		}
		return rtn;
	}

	/**
	 * Replace when testing to more easily simulate change in time
	 * 
	 * @return
	 */
	protected long currentTimeMillis() {
		return System.currentTimeMillis();
	}
	
	/**
	 * Check if WatchService events occurred that need care.
	 */
	public boolean processEvents() {
		if (closed == true) {
			throw new ClosedWatchServiceException();
		}

		boolean rtn = false;
		
		// Avoid excessive calls to System.currentTimeMillis()
		long currentMillis = currentTimeMillis();
		
		// Iterate over all dirs and subdirs to check the events. But also trigger grace events if necessary.
		for (Map.Entry<WatchKey, WatchDirContext> entry : keys.entrySet()) {
			WatchKey key = entry.getKey();
      WatchDirContext wdir = entry.getValue();

      rtn |= processEvents(key, wdir, currentMillis);
    }
		
		return rtn;
	}

	/**
	 * Check the events for a specific watch key and trigger 'grace' events if necessary. 
	 * 
	 * @param key
	 * @param wdir lazy "loaded" if null
	 * @param currentMillis lazy loaded with System.currentTimeMillis() if 0
	 */
	private boolean processEvents(final WatchKey key, WatchDirContext wdir, long currentMillis) {
		Args.notNull(key, "key");
		
		if (currentMillis == 0) {
			currentMillis = currentTimeMillis();
		}
		
		if (wdir == null) {
			wdir = keys.get(key);
		}

		// true, if an event was processed
		boolean rtn = false;

		// false, once the first key has been found
		boolean first = true;

    // Process all events for a specific key
		List<WatchEvent<?>> events = key.pollEvents();
    for (WatchEvent<?> event : events) {
    	// In case of FolderWatcher it's always a "Path"
    	@SuppressWarnings("unchecked")
      WatchEvent<Path> ev = (WatchEvent<Path>) event;

    	// Default processing for the event (internal)
      handleEventInternal(key, wdir, ev);

    	// Allow the entry to be canceled from within the listener
      if (first && (wdir.listener != null)) {
      	wdir.listener._injectData(this, wdir.root.dir);
      	first = false;
      }

      // Either invoke a user provided listener or a local handler (optionally 
      // provided / extended via subclassing)
     	_onEvent(ev, wdir);
     	
     	rtn = true;
    } 

    // reset the key and remove from set if directory is no longer accessible
    boolean valid = key.reset();
    if (!valid) {
      keys.remove(key);
    }

    // Invoke an aggregation handler in case the user is not interested 
    // in each event, but only in "something has changed"
    if (rtn == true) {
    	_onDirectoryChanged(wdir, currentMillis);
    }
    
    // Some (aggregation) events should only be triggered after a grace period.
    // The timestamp is set with current millis upon a "something has changed" 
    // event, and reset to 0 when the grace event was triggered.
    processGraceEvents(wdir, currentMillis);

    // Avoid any dangling references with the listeners
    if ((first == false) && (wdir.listener != null)) {
    	wdir.listener._injectData(null, null);
    }
    
    return rtn;
	}

	/**
	 * Check all entries and fire grace events when needed
	 * 
	 * @param now
	 */
	public void processAllGraceEvents(long now) {
		for (WatchDirContext wdir: keys.values()) {
			processGraceEvents(wdir, now);
		}
	}
	
	/**
	 * Check all registrations and fire grace events if needed
	 * @param wdir
	 * @param currentMillis
	 */
	private void processGraceEvents(final WatchDirContext wdir, final long currentMillis) {
    if (wdir.isRoot() && (wdir.timestamp >= 0)) {
    	long diff = currentMillis - wdir.timestamp;
    	if (diff >= gracePeriod) {
     		_onGraceEvent(wdir);
    		
    		// reset
    		wdir.timestamp = -1;
    	}
    }
	}
	
	/**
	 * A little helper to dispatch either to the listener or internal method.
	 * 
	 * @param event
	 * @param wdir
	 */
	private void _onEvent(final WatchEvent<Path> event, final WatchDirContext wdir) {
		try {
	    if (wdir.listener != null) {
	    	// Only invoke the listener if he is interested in the event
	    	for (WatchEvent.Kind<?> kind : wdir.listener.getEventTypes()) {
	    		if(kind.equals(event.kind())) {
	  	    	wdir.listener.onEvent(event, wdir);
	  	    	break;
	    		}
	    	}
	    } else {
	    	onEvent(event, wdir);
	    }
		} catch (Exception ex) {
			log.error("Error while executing event handler: " + ex.getMessage(), ex);
		}
	}

	/**
	 * This is the most fine grained event handler which gets invoked for each and every event.
	 * <p>
	 * If a listener was registered, than the corresponding listener event gets invoked instead.
	 * 
	 * @param event
	 * @param wdir
	 */
	protected void onEvent(final WatchEvent<Path> event, final WatchDirContext wdir) {
	}


	/**
	 * A little helper to dispatch either to the listener or internal method
	 * 
	 * @param event
	 * @param wdir
	 */
	private void _onDirectoryChanged(final WatchDirContext wdir, final long currentMillis) {
		try {
	    if (wdir.listener != null) {
	    	wdir.listener.onDirectoryChanged(wdir, currentMillis);
	    } else {
	      onDirectoryChanged(wdir, currentMillis);
	    }
		} catch (Exception ex) {
			log.error("Error while executing event handler: " + ex.getMessage(), ex);
		}

		// Update the root timestamp to handle grace events
  	wdir.root.timestamp = currentMillis;
	}

	/**
	 * This event handler gets invoked when something (whatever) has changed in a
	 * registered directory.
	 * <p>
	 * {@link #WatchDir.isRoot()} may be used to determine if it's the registered root 
	 * directory in case of recursive registration.
	 * <p>
	 * If a listener was registered, than the corresponding listener event gets invoked instead.
	 * 
	 * @param wdir
	 * @param currentMillis
	 */
	protected void onDirectoryChanged(final WatchDirContext wdir, final long currentMillis) {
	}

	/**
	 * A little helper to dispatch either to the listener or internal method
	 * 
	 * @param event
	 * @param wdir
	 */
	private void _onGraceEvent(final WatchDirContext wdir) {
		try {
	    if (wdir.listener != null) {
	    	wdir.listener.onGraceEvent(wdir);
	    } else {
	  		onGraceEvent(wdir);
	    }
		} catch (Exception ex) {
			log.error("Error while executing event handler: " + ex.getMessage(), ex);
		}
	}

	/**
	 * An event handler triggered after something has changed in a directory, including 
	 * all its subdirectories (if recursive), but only if no new change was detected within
	 * the grace period.
	 * <p>
	 * If a listener was registered, than the corresponding listener event gets invoked instead.
	 * 
	 * @param wdir
	 */
	protected void onGraceEvent(final WatchDirContext wdir) {
	}

	/**
	 * Manage overflows and newly created directories which must be registered if recursive is on.
	 * 
	 * @param key
	 * @param wdir
	 * @param event
	 */
  private void handleEventInternal(final WatchKey key, final WatchDirContext wdir, final WatchEvent<Path> event) {
  	WatchEvent.Kind<?> kind = event.kind();
    if (kind == StandardWatchEventKinds.OVERFLOW) {
      log.warn("Overflow event on watched directory");
    } else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
      Path child = wdir.resolve(event);
      if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
        onNewSubdirectory(child, wdir);
      }
    } 
  }

  /**
   * Allow subclasses to register newly created subdirectory upon appropriate events received.
   * 
   * @param subdir
   * @param wdir
   */
  protected final void registerNewSubdirectory(final Path subdir, final WatchDirContext wdir) {
  	// doRegister is private and we don't want to expose it.
  	doRegister(subdir, newWatchDirContext(subdir, wdir.root, wdir.recursive, wdir.listener));
  }
  
  /**
   * Gets invoked if a new subdir was created 
   * 
   * @param subdir
   * @param wdir
   */
  protected void onNewSubdirectory(final Path subdir, final WatchDirContext wdir) {
    if (wdir.recursive) {
    	registerNewSubdirectory(subdir, wdir);
    }
  }
  
  /**
   * A little utility to get the list of registered directories (only the root dirs in 
   * case where recursive is on).
   * 
   * @param startsWith might be null
   * @return
   */
  public List<Path> getRegisteredRootDirs(final Path startsWith) {
  	List<Path> list = new ArrayList<>();
  	
  	for(WatchDirContext ctx: keys.values()) {
  		if (ctx.isRoot()) {
  			if (startsWith == null) {
  				list.add(ctx.dir);
  			} else if (ctx.dir.startsWith(startsWith)) {
  				list.add(ctx.dir);
  			}
  		}
  	}
  	
  	return list;
  }
  
  /**
   * Context data for a key (== directory)
   */
	public class WatchDirContext {
		
		// Whether root was registered with recursive on
		private final boolean recursive;
		
		// The root directory (and its context data). Will never be null. 
		private final WatchDirContext root;
		
		// The actual (sub) directory
		private final Path dir;
		
		// Optional listener
		private final ChangeListener listener;
		
		// Time when Last change event occurred (only used in root context but for all subdirs)
		private long timestamp = -1;

		/**
		 * Constructor
		 * 
		 * @param dir
		 * @param root
		 * @param recursive
		 * @param listener
		 */
		protected WatchDirContext(final Path dir, final WatchDirContext root, final boolean recursive, final ChangeListener listener) {
			this.root = root != null ? root : this;
			this.dir = dir;
			this.recursive = recursive;
			this.listener = listener;
		}

		/** Whether root was registered with recursive on */
		public final boolean recursive() { return this.recursive; }
		
		/** The root directory (and its context data). Will never be null. */ 
		public final WatchDirContext root() { return this.root; }
		
		/** The actual (sub) directory. Might be equal to root, might be any subdir of root. */
		public final Path dir() { return this.dir; }
		
		/**
		 * @return may be 0 if nothing has changed recently or if not root
		 */
		public long getTimestamp() { return this.timestamp; }
		
		/**
		 * Set last change / event time
		 * 
		 * @param millis
		 */
		public void setTimestamp(long millis) {
			this.timestamp = millis;
		}
		
		/**
		 * Set last change / event time with system current millis
		 */
		public void setTimestamp() {
			this.timestamp = currentTimeMillis();
		}

		/**
		 * Unregister the directory and optionally (recursive) all its subdirectories. 
		 * If itself a subdirectory and not a root, it'll only unregister the subdir 
		 * and its children.
		 */
		public void unregister() {
			FolderWatcher.this.unregister(dir);
		}

		/**
		 * @return True, if this is a root context
		 */
		public boolean isRoot() {
			return root == this;
		}

		/**
		 * Combine dir + event path
		 * 
		 * @param event
		 * @return
		 */
		public Path resolve(final WatchEvent<Path> event) {
	    Path name = event.context();
	    return dir.resolve(name);
		}
	}

  /**
   * Start a thread in the background waiting for events to be processed.
   * The thread can be stopped via FolderWorker.cancel()
   * <p>
   * Event loops or any other external timer may call processEvents() in 
   * regular time intervals as an alternative.
   * 
   * @return
   */
  public FolderWorker start() {
  	return start(Executors.newCachedThreadPool());
  }
  
  /**
   * Start a thread in the background waiting for events to be processed.
   * The thread can be stopped via FolderWorker.cancel()
   * <p>
   * Event loops or any other external timer may call processEvents() in 
   * regular time intervals as an alternative.
   * 
   * @return
   */
  public FolderWorker start(ExecutorService executor) {
  	Args.notNull(executor, "executor");
  	FolderWorker worker = new FolderWorker();
  	executor.execute(worker);
  	return worker;
  }

  /**
   * Little helper class which waits for the WatchService to trigger an event, 
   * which than gets processed.
   */
  public class FolderWorker implements Runnable {
  	// True, to stop the thread / worker
  	private boolean cancel = false;

		@Override
		public void run() {
			// time when we last checked for 'graced' events
			long last = currentTimeMillis();
			
	  	// Endless loop until somebody calls cancel()
			while (cancel == false) {
				try {
					// Make sure we wake up regularly to check for 'graced' events
					WatchKey key = watchService.poll(CHECK_PERIOD, TimeUnit.MILLISECONDS);
					long now = currentTimeMillis();
					if (key != null) {
						// Something happened in the monitored dirs => process them
						processEvents(key, null, 0);
					}

					// Every now and than we need to process all registered paths,
					// to detect and fire 'grace' events
					if ((now - last) > CHECK_PERIOD) {
						processAllGraceEvents(now);
						
						// reset
						last = now;
					} 
				} catch (Exception ex) {
					log.error(ex);
				}
			}
		}
  	
		public void cancel() {
			cancel = true;
		}
  }
}