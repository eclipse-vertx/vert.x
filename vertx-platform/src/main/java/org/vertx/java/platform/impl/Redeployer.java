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

package org.vertx.java.platform.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class Redeployer {

  private static final Logger log = LoggerFactory.getLogger(Redeployer.class);

  private static final long GRACE_PERIOD = 600;
  private static final long CHECK_PERIOD = 200;

  private final File modRoot;
  private final ModuleReloader reloader;
  private final Map<Path, Set<Deployment>> watchedDeployments = new HashMap<>();
  private final Map<WatchKey, Path> watchKeys = new HashMap<>();
  private final Map<Path, Path> moduleDirs = new HashMap<>();
  private final WatchService watchService;
  private final Vertx vertx;
  private final Map<Path, Long> changing = new HashMap<>();
  private final long timerID;
  private final Queue<Deployment> toDeploy = new ConcurrentLinkedQueue<>();
  private final Queue<Deployment> toUndeploy = new ConcurrentLinkedQueue<>();
  private Thread thread;
  private boolean closed;

  public Redeployer(Vertx vertx, File modRoot, ModuleReloader reloader) {
    this.modRoot = modRoot;
    this.reloader = reloader;
    try {
      watchService = FileSystems.getDefault().newWatchService();
    } catch (IOException e) {
      log.error("Failed to create redeployer", e);
      throw new IllegalArgumentException(e.getMessage());
    }

    this.vertx = vertx;
    timerID = vertx.setPeriodic(CHECK_PERIOD, new Handler<Long>() {
      public void handle(Long id) {
        synchronized (Redeployer.this) {
          if (!closed) {
            checkThread();
            try {
              checkEvents();
            } catch (Exception e) {
              log.error("Failed to check events", e);
            }
          }
        }
      }
    });
  }

  public synchronized void close() {
    vertx.cancelTimer(timerID);
    Set<Deployment>  deps = new HashSet<>();
    for (Map.Entry<Path, Set<Deployment>> entry: watchedDeployments.entrySet()) {
      deps.addAll(entry.getValue());
    }
    toUndeploy.addAll(deps);
    processUndeployments();
    try {
			watchService.close();
		} catch (IOException ex) {
			log.warn("Error while shutting down watch service: " + ex.getMessage(), ex);
		}
    closed = true;
  }

  public void moduleDeployed(Deployment deployment) {
    toDeploy.add(deployment);
  }

  public void moduleUndeployed(Deployment deployment) {
    toUndeploy.add(deployment);
  }

  // We process all the deployments and undeployments on the same context as the
  // rest of the stuff
  // this means we don't have to synchronize between the deployment and
  // any stuff done on the timer
  private void processDeployments() {
    Deployment dep;
    while ((dep = toDeploy.poll()) != null) {
      File fmodDir = new File(modRoot, dep.modName);
      Path modDir = fmodDir.toPath();
      Set<Deployment> deps = watchedDeployments.get(modDir);
      if (deps == null) {
        deps = new HashSet<>();
        watchedDeployments.put(modDir, deps);
        try {
          registerAll(modDir, modDir);
        } catch (IOException e) {
          log.error("Failed to register", e);
          throw new IllegalStateException(e.getMessage());
        }
      }
      deps.add(dep);
    }
  }

  // This can be optimised
  private void processUndeployments() {
    Deployment dep;
    while ((dep = toUndeploy.poll()) != null) {
      File modDir = new File(modRoot, dep.modName);
      Path pModDir = modDir.toPath();
      Set<Deployment> deps = watchedDeployments.get(pModDir);
      deps.remove(dep);
      if (deps.isEmpty()) {
        watchedDeployments.remove(pModDir);
        Set<Path> modPaths = new HashSet<>();
        for (Map.Entry<Path, Path> entry: moduleDirs.entrySet()) {
          if (entry.getValue().equals(pModDir)) {
            modPaths.add(entry.getKey());
          }
        }
        for (Path p: modPaths) {
          moduleDirs.remove(p);
          changing.remove(p);
        }
        Set<WatchKey> keys = new HashSet<>();
        for (Map.Entry<WatchKey, Path> entry: watchKeys.entrySet()) {
          if (modPaths.contains(entry.getValue())) {
            keys.add(entry.getKey());
          }
        }
        for (WatchKey key: keys) {
          key.cancel();
          watchKeys.remove(key);
        }
      }
    }
  }

  void checkEvents() {
    processUndeployments();
    processDeployments();
    Set<Path> changed = new HashSet<>();
    while (true) {
      WatchKey key = watchService.poll();
      if (key == null) {
        break;
      }
      handleEvent(key, changed);
    }
    long now = System.currentTimeMillis();
    for (Path modulePath: changed) {
      changing.put(modulePath, now);
    }
    Set<Path> toRedeploy = new HashSet<>();
    for (Map.Entry<Path, Long> entry: changing.entrySet()) {
      if (now - entry.getValue() > GRACE_PERIOD) {
        // Module has changed but no changes for GRACE_PERIOD ms
        // we can assume the redeploy has finished
        toRedeploy.add(entry.getKey());
      }
    }
    if (!toRedeploy.isEmpty()) {
      Set<Deployment> deployments = new HashSet<>();
      for (Path moduleDir: toRedeploy) {
        log.info("moduleDir is " + moduleDir);
        log.info("Module has changed - redeploying module from directory " + moduleDir.toString());
        changing.remove(moduleDir);
        deployments.addAll(watchedDeployments.get(moduleDir));
      }
      reloader.reloadModules(deployments);
    }
  }


  private void handleEvent(WatchKey key, Set<Path> changed) {
    Path dir = watchKeys.get(key);
    if (dir == null) {
      throw new IllegalStateException("Unrecognised watch key " + dir);
    }

    for (WatchEvent<?> event : key.pollEvents()) {
      WatchEvent.Kind<?> kind = event.kind();

      if (kind == StandardWatchEventKinds.OVERFLOW) {
        log.warn("Overflow event on watched directory");
        continue;
      }

      Path moduleDir = moduleDirs.get(dir);
      if (moduleDir != null) {

        @SuppressWarnings("unchecked")
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path name = ev.context();

        Path child = dir.resolve(name);

        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
          if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            try {
              registerAll(moduleDir, child);
            } catch (IOException e) {
              log.error("Failed to register child", e);
              throw new IllegalStateException(e.getMessage());
            }
          }
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
          moduleDirs.remove(child);
        }
        changed.add(moduleDir);
      }
    }

    boolean valid = key.reset();
    if (!valid) {
      watchKeys.remove(key);
    }
  }

  private void register(Path modDir, Path dir) throws IOException {
    WatchKey key = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
    watchKeys.put(key, dir);
    moduleDirs.put(dir, modDir);
  }

  private void registerAll(final Path modDir, final Path dir) throws IOException {
    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        register(modDir, dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

//  private void dumpSizes() {
//    log.info("watchkeys: " + watchKeys.size());
//    log.info("moduleDirs: " + moduleDirs.size());
//    log.info("changing: " + changing.size());
//    int size = 0;
//    for (Set<Deployment> s: this.watchedDeployments.values()) {
//      size += s.size();
//    }
//    log.info("watcheddeployments:" + size);
//  }

  private void checkThread() {
    //Sanity check
    Thread curr = Thread.currentThread();
    if (thread == null) {
      thread = curr;
    } else if (curr != thread) {
      throw new IllegalStateException("Wrong thread: " + curr + " expected " + thread);
    }
  }

}