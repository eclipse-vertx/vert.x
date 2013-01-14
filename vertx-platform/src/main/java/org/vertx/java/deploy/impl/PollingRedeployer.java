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
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is a Redeployer based on old fashioned file tree scanning for OS'es where Java's NIO 
 * implementation is perceived flaky.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PollingRedeployer extends Redeployer {

  private static final Logger log = LoggerFactory.getLogger(PollingRedeployer.class);

  // Periodic timer: process the file system events
  private final long checkMillis;

  private final long gracePeriod;
  
  // Periodic timer: every CHECK_PERIOD
  private final long timerID;

  // The directories we are monitoring
  private Map<Path, Long> dirs = new ConcurrentHashMap<>();
  
  /**
   * Constructor
   * 
   * @param vertx
   * @param modRoot
   * @param reloader
   */
  public PollingRedeployer(final VertxInternal vertx, final File modRoot, final ModuleReloader reloader) {
  	this(vertx, modRoot, reloader, 2_000, 2_000);
  }
  
  /**
   * Constructor
   * 
   * @param vertx
   * @param modRoot
   * @param reloader
   */
  public PollingRedeployer(final VertxInternal vertx, final File modRoot, final ModuleReloader reloader, 
  		final long checkMillis, final long gracePeriod) {
  	super(vertx, modRoot, reloader);

  	this.checkMillis = getProperty(".checkMillis", checkMillis);
  	this.gracePeriod = getProperty(".gracePeriod", gracePeriod);
  	
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
        } catch (Exception e) {
          log.error("Error while checking file system events", e);
        }
      }
    });
  }
  
  /**
   * Process module registration and unregistration, and any file system events.
   * {@link #onGraceEvent(Path)} can be subclassed to change how file system
   * changes get handled.
   */
  protected void onTimerEvent() {
//  	log.info("Next processing cycle: " + System.currentTimeMillis());
  	
    processUndeployments();
    processDeployments();

    scanDirectories();
  }

  /**
   * Scan the registered directories for modified files or directories
   */
  private void scanDirectories() {
    // Some Linux filesystems only have second resolution
  	final long relevantSystemTime = System.currentTimeMillis() - gracePeriod;
  	
  	// For all registered directories
  	for(final Map.Entry<Path, Long> e: this.dirs.entrySet()) {
      try {
      	final long oldValue = e.getValue();
      	final long[] newValue = { oldValue };
      	
      	Files.walkFileTree(e.getKey(), new SimpleFileVisitor<Path>() {
      		@Override
      		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
//      			log.error("dir: " + dir.toString() + " " + attrs.creationTime().toMillis() + " " + attrs.lastModifiedTime().toMillis());
				  	return check(dir, attrs);
      		}
      		
				  @Override
				  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//      			log.error("file: " + file.toString() + " " + attrs.creationTime().toMillis() + " " + attrs.lastModifiedTime().toMillis());
				  	return check(file, attrs);
				  }
				  
				  private FileVisitResult check(Path file, BasicFileAttributes attrs) {
				    long max = Math.max(attrs.lastModifiedTime().toMillis(), attrs.creationTime().toMillis());
				    if (max > newValue[0]) {
				    	newValue[0] = max;
				    }
			    	return FileVisitResult.CONTINUE;
				  }
				});

      	// If modified previously, but not modified since then, than ...
      	long newVal = newValue[0];
//      	log.error(relevantSystemTime + ": " + oldValue + " -> " + newVal);
      	if ((newVal > oldValue) && (newVal < relevantSystemTime)) {
      		e.setValue(newVal);
					onGraceEvent(e.getKey());
				}
			} catch (IOException ex) {
				log.error("Error while scanning module directory: " + e.getKey(), ex);
			}
  	}
  }

  /**
   * Shutdown the service. Free up all resources.
   */
  @Override
  public void close() {
  	super.close();
    vertx().cancelTimer(timerID);
  }
  
  /**
   * Inform Redeployer that a new module has been deployed. Start monitoring it.
   * 
   * @param deployment
   */
  public void moduleDeployed(final Deployment deployment) {
  	super.moduleDeployed(deployment);
  	processDeployments();
  }

  /**
   * Inform the Redeployer that a module has been undeployed. Stop monitoring it.
   * 
   * @param deployment
   */
  public void moduleUndeployed(final Deployment deployment) {
  	super.moduleUndeployed(deployment);
  	processUndeployments();
  }

  /**
   * register a deployment
   */
  @Override
  protected void registerDeployment(final Path modDir) {
  	if (this.dirs.containsKey(modDir) == false) {
  		// Initialize entry with time of registration
  		this.dirs.put(modDir, System.currentTimeMillis());
  	}
  }

  /**
   * Unregister a deployment
   */
  protected void unregisterDeployment(final Path modDir) {
		this.dirs.remove(modDir);
  }
}