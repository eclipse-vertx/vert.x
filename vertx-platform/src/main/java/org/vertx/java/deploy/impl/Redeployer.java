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

import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.Args;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Deployment may contain several modules. @TODO is that true? 
 * Every deployment will be deployed in a modRoot subdir like <code>modRoot/myDep</code>.  
 * <p>
 * Monitor the file system where the modules are deployed. In case any directory or file 
 * within that directory tree gets modified, wait for a short while (grace period) for all 
 * copies, zip exports etc. to finish. Than initiate a redeploy of that deployment (or module?).
 * <p>
 * This is an abstract base class providing common functionalities. Subclasses may e.g. use 
 * different type of filesystem watch services.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public abstract class Redeployer {

  private static final Logger log = LoggerFactory.getLogger(Redeployer.class);

  private final VertxInternal vertx;
  
  // Modules get deployed in a subdir: modRoot/myMod. There can only be 
  // one modRoot. Something like class paths are not yet supported.
  private final File modRoot;
  
  // ModuleReloader will be informed about changes and actually redeploy the module
  private final ModuleReloader reloader;
  
  // All processing and all changes are done in the timer context => single thread
  private Context ctx;
  
  // True, if the Redeployer has been closed (shutdown)
  private boolean closed;

  // The deployments actively monitored.
  private final Map<Path, Set<Deployment>> watchedDeployments = new ConcurrentHashMap<>();

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
  }

  protected final VertxInternal vertx() { return vertx; }

  protected final File modRoot() { return modRoot; }

  /**
   * Shutdown the service. Free up all resources.
   */
  public void close() {
  	this.closed = true;
  }

  protected boolean closed() { return closed; }
  
  /**
   * Inform Redeployer that a new module has been deployed. Start monitoring it.
   * 
   * @param deployment
   */
  public void moduleDeployed(final Deployment deployment) {
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
  public void moduleUndeployed(final Deployment deployment) {
  	Args.notNull(deployment, "deployment");
  	if (!closed) {
      toUndeploy.add(deployment);
  	} else {
  		log.warn("Redeployer is closed. Module undeployment information will be ignored: " + deployment.modName);
  	}
  }

  /**
   * Process the deployment requests.
   */
  protected final void processDeployments() {
    Deployment dep;
    while ((dep = toDeploy.poll()) != null) {
    	Path modDir = new File(modRoot, dep.modName).toPath();
   		log.info("Register new Module: " + modDir);
    	if (closed) {
      	log.error("Redeployer has been closed. New Deployments can not be registered: " + modDir);
    		continue;
    	}

      Set<Deployment> deps = watchedDeployments.get(modDir);
      if (deps == null) {
        deps = new HashSet<>();
        watchedDeployments.put(modDir, deps);
       	registerDeployment(modDir);
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
   */
  protected final void processUndeployments() {
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
       	unregisterDeployment(modDir);
      }
    }
  }

  protected abstract void registerDeployment(Path modDir);

  protected abstract void unregisterDeployment(Path modDir);
  
  /**
   * Something has changed on the file system (dir and subdirs) and the grace period passed
   * by without any new changes.
   * 
   * @param wdir The context of the monitored root directory
   */
  protected final void onGraceEvent(final Path dir) {
    log.info("Module has changed - redeploying module from directory " + dir.toString());
    Set<Deployment> deps = watchedDeployments.get(dir);
    if (deps != null) {
    	reloader.reloadModules(deps);
    } else {
      log.info("Bug??? No Deployment was previously registered with this directory: " + dir);
    }
  }

  /**
   * Little helper. Get a Long system property 
   * 
   * @param name
   * @param defValue
   * @return
   */
  protected long getProperty(final String name, final long defValue) {
  	Args.notEmpty(name, "name");

  	// Unfortunately the following statement doesn't throw an exception
  	// long rtn = Long.getLong(this.getClass().getSimpleName() + name, defValue);
  	
  	String val = System.getProperty(this.getClass().getSimpleName() + name);
  	if (val == null) {
  		return defValue;
  	}
  	try {
  		return Long.parseLong(val);
  	} catch (NumberFormatException ex) {
  		log.error("Invalid property value format. Property: " + name + "; Value: " + val + 
  				". Must be a number. Using default: " + defValue);
  	}
		return defValue;
  }

  /**
   * Assign the context if needed. Else make sure we are in the right context (same thread).
   */
  protected final void checkContext() {
    if (ctx == null) {
      ctx = vertx.getContext();
    } else {
      if (vertx.getContext() != ctx) {
        throw new IllegalStateException("Got context: " + vertx.getContext() + ". Expected: " + ctx);
      }
    }
  }
}