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

package org.vertx.java.deploy.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.file.impl.ChangeListener;
import org.vertx.java.core.file.impl.FolderWatcher;
import org.vertx.java.core.file.impl.FolderWatcher.WatchDirContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;

/**
 * This is a a FolderWatcher (NIO WatchService) based implementation of Redeployer. 
 * Other implementations may e.g. use homegrown polling services.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class DefaultRedeployer extends Redeployer {

  private static final Logger log = LoggerFactory.getLogger(DefaultRedeployer.class);

  // Periodic timer: process the file system events
  private final long checkMillis;

  // Periodic timer: every CHECK_PERIOD
  private final long timerID;
  
  // The underlying file system watcher
  private final FolderWatcher watchService;

  /**
   * Constructor
   * 
   * @param vertx
   * @param modRoot
   * @param reloader
   */
  public DefaultRedeployer(final VertxInternal vertx, final File modRoot, final ModuleReloader reloader) {
  	this(vertx, modRoot, reloader, 200);
  }
  
  /**
   * Constructor
   * 
   * @param vertx
   * @param modRoot
   * @param reloader
   */
  public DefaultRedeployer(final VertxInternal vertx, final File modRoot, final ModuleReloader reloader, final long checkMillis) {
  	super(vertx, modRoot, reloader);

  	// E.g. -DDefaultRedeployer.checkMillis=500
  	this.checkMillis = getProperty(".checkMillis", checkMillis);
  	
    // Get and start the watch service
    watchService = newFolderWatcher();
    if (watchService == null) {
    	throw new NullPointerException("newFolderWatcher() must not return null");
    }
    
    // Start a new periodic timer to regularly process the watcher events
    timerID = vertx.setPeriodic(this.checkMillis, new Handler<Long>() {
      public void handle(Long id) {
      	// Timer shutdown is asynchronous and might not have completed yet.
      	if (closed()) {
      		vertx.cancelTimer(timerID);
      		return;
      	}
      	
        checkContext();
        
        try {
        	onTimerEvent();
        } catch (ClosedWatchServiceException e) {
        	// Should never happen ...
          log.warn("FolderWatcher has been closed already");
        } catch (Exception e) {
          log.error("Error while checking file system events", e);
        }
      }
    });
  }
  
  /**
   * @return By default, the vertx provided folder watcher gets used.
   */
  public FolderWatcher newFolderWatcher() {
  	return vertx().folderWatcher(true);
  }

  /**
   * Process module registration and unregistration, and any file system events.
   * {@link #onGraceEvent(WatchDirContext)} can be subclassed to change how file system
   * changes get handled.
   */
  protected void onTimerEvent() {
    processUndeployments();
    processDeployments();
    watchService.processEvents();
  }

  /**
   * Shutdown the service. Free up all resources.
   */
  @Override
  public void close() {
  	super.close();
    vertx().cancelTimer(timerID);

    // Stop the folder watcher only if it's not the vertx default folder watcher
    if ((watchService != null) && (watchService != vertx().folderWatcher(false))) {
			watchService.close();
    }
  }

  /**
   * register a deployment
   */
  @Override
  protected void registerDeployment(final Path modDir) {
    try {
      watchService.register(modDir, true, new ChangeListener() {
				@Override
				public void onGraceEvent(final WatchDirContext wdir) {
					DefaultRedeployer.this.onGraceEvent(wdir.dir());
				}
			});
    } catch (ClosedWatchServiceException ex) {
    	// FolderWatcher has (accidently) been closed behind the scene ...
    	log.error("FolderWatcher has been closed. New Deployments can not be registered: " + modDir);
    }
  }

  /**
   * Unregister a deployment
   */
  protected void unregisterDeployment(final Path modDir) {
   	watchService.unregister(modDir);
  }
}