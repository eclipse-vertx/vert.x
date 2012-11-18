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
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.lang.Args;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Deployment may contain several modules. @TODO is that true? 
 * Every deployment will be deployed in a modRoot subdir like <code>modRoot/myDep</code>.  
 * <p>
 * Monitor the file system where the modules are deployed. In case any directory or file 
 * within that directory tree gets modified, wait for a short while (grace period) for all 
 * copies, zip exports etc. to finish. Than initiate a redeploy of that deployment (or module?).
 * 
 * @author Juergen Donnerstag
 */
public class Redeployer {

  private static final Logger log = LoggerFactory.getLogger(Redeployer.class);

  // Periodic timer: process the file system events
  private static final long CHECK_PERIOD = 200;

  private final VertxInternal vertx;
  
  // Modules get deployed in a subdir: modRoot/myMod. There can only be 
  // one modRoot. Something like class paths are not yet supported.
  private final File modRoot;
  
  // ModuleReloader will be informed about changes and actually redeploy the module
  private final ModuleReloader reloader;
  
  // Periodic timer: every CHECK_PERIOD
  private final long timerID;
  
  // The underlying file system watcher
  private final FolderWatcher watchService;
  
  // All processing and all changes are done in the timer context => single thread
  private Context ctx;
  
  // True, if the Redeployer has been closed (shutdown)
  private boolean closed;

  // The deployments actively monitored.
  private final Map<Path, Set<Deployment>> watchedDeployments = new HashMap<>();

  // Asynchronously ask Redeployer to register or unregister additional Modules.
  // Actual processing happens in the timer thread => single threaded.
  private final Queue<Deployment> toDeploy = new ConcurrentLinkedQueue<>();
  private final Queue<Deployment> toUndeploy = new ConcurrentLinkedQueue<>();

  /**
   * Constructor
   * 
   * @param vertx
   * @param modRoot
   * @param reloader
   */
  public Redeployer(final VertxInternal vertx, final File modRoot, final ModuleReloader reloader) {
    this.vertx = Args.notNull(vertx, "vertx");
    this.modRoot = Args.notNull(modRoot, "modRoot");
    this.reloader = Args.notNull(reloader, "reloader");

    // Get and start the watch service
    watchService = newFolderWatcher();
    if (watchService == null) {
    	throw new NullPointerException("newFolderWatcher() must not return null");
    }
    
    // Start a new periodic timer to regularly process the watcher events
    timerID = vertx.setPeriodic(CHECK_PERIOD, new Handler<Long>() {
      public void handle(Long id) {
      	// Timer shutdown is asynchronous and might not have completed yet.
      	if (closed) {
      		vertx.cancelTimer(timerID);
      		return;
      	}
      	
        if (ctx == null) {
          ctx = Redeployer.this.vertx.getContext();
        } else {
          checkContext();
        }
        
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
  	return vertx.folderWatcher(true);
  }

  /**
   * Process module registration and unregistration, and any file system events.
   * {@link #onGraceEvent(WatchDirContext)} can be subclassed to change how file system
   * changes get handled.
   */
  private void onTimerEvent() {
    processUndeployments();
    processDeployments();
    watchService.processEvents();
  }

  /**
   * Shutdown the service. Free up all resources.
   */
  public void close() {
  	this.closed = true;
    this.vertx.cancelTimer(timerID);

    // Stop the folder watcher only if it's not the vertx default folder watcher
    if ((watchService != null) && (watchService != vertx.folderWatcher(false))) {
			watchService.close();
    }
  }

  /**
   * Inform Redeployer that a new module has been deployed. Start monitoring it.
   * 
   * @param deployment
   */
  public final void moduleDeployed(final Deployment deployment) {
  	Args.notNull(deployment, "deployment");
  	if (!closed) {
  		toDeploy.add(deployment);
  	} else {
  		log.warn("Redeployer is closed. Module deployment information will be ignored: " + deployment.modName);
  	}
  }

  /**
   * Inform the Redeployer that a module has been undeployed. Stop monitoring it.
   * 
   * @param deployment
   */
  public final void moduleUndeployed(final Deployment deployment) {
  	Args.notNull(deployment, "deployment");
  	if (!closed) {
      toUndeploy.add(deployment);
  	} else {
  		log.warn("Redeployer is closed. Module undeployment information will be ignored: " + deployment.modName);
  	}
  }

  /**
   * Process the deployment requests.
   * <p>
   * All deployments and undeployments, and all processing, happens on the same context (thread). 
   * => No need to synchronize between them.
   */
  private void processDeployments() {
    Deployment dep;
    while ((dep = toDeploy.poll()) != null) {
    	Path modDir = new File(modRoot, dep.modName).toPath();
   		log.info("Register new Module: " + modDir);
    	if (closed) {
      	log.error("Redeployer has been closed. New Deployments can not be registered: " + modDir);
    		continue;
    	}

    	// @TODO how can there be more than 1 deployment in a subdir? What exactly the relationship between Deployment and Module?
      Set<Deployment> deps = watchedDeployments.get(modDir);
      if (deps == null) {
        deps = new HashSet<>();
        watchedDeployments.put(modDir, deps);
        try {
	        watchService.register(modDir, true, new ChangeListener() {
						@Override
						public void onGraceEvent(final WatchDirContext wdir) {
							Redeployer.this.onGraceEvent(wdir);
						}
					});
        } catch (ClosedWatchServiceException ex) {
        	// FolderWatcher has (accidently) been closed behind the scene ...
        	log.error("FolderWatcher has been closed. New Deployments can not be registered: " + modDir);
        }
      }

      // Associated the deployment with the path
      // @TODO how can there be more than 1 deployment for the same path??
      if (deps.contains(dep) == false) {
      	deps.add(dep);
      }
    }
  }

  /**
   * Process the undeployment requests
   * <p>
   * All deployments and undeployments, and all processing, happens on the same context (thread). 
   * => No need to synchronize between them.
   */
  private void processUndeployments() {
    Deployment dep;
    while ((dep = toUndeploy.poll()) != null) {
      Path modDir = new File(modRoot, dep.modName).toPath();
    	log.info("Unregister Module: " + modDir);

    	// Process unregister() even if Redeployer has been closed already.
    	// It doesn't do any harm, but might release strained resources.
    	
      Set<Deployment> deps = watchedDeployments.get(modDir);
      deps.remove(dep);
      if (deps.isEmpty()) {
        watchedDeployments.remove(modDir);
       	watchService.unregister(modDir);
      }
    }
  }

  /**
   * Something has changed on the file system (dir and subdirs) and the grace period passed
   * by without any new changes.
   * 
   * @param wdir The context of the monitored root directory
   */
  protected void onGraceEvent(final WatchDirContext wdir) {
    log.info("Module has changed - redeploying module from directory " + wdir.dir().toString());
    Set<Deployment> deps = watchedDeployments.get(wdir.dir());
    if (deps != null) {
    	reloader.reloadModules(deps);
    } else {
      log.info("Bug??? No Deployment was previously registered with this directory: " + wdir.dir());
    }
  }

  private void checkContext() {
    // Sanity check
    if (vertx.getContext() != ctx) {
      throw new IllegalStateException("Got context: " + vertx.getContext() + ". Expected: " + ctx);
    }
  }
}