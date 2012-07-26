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
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
  private final Map<String, Deployment> deployments = new HashMap<>();
  private final Map<Path, Set<Deployment>> watchedDeployments = new HashMap<>();
  private final Map<WatchKey, Path> watchKeys = new HashMap<>();
  private final Map<Path, Path> moduleDirs = new HashMap<>();
  private final WatchService watchService;
  private final Vertx vertx;
  private final Map<Path, Long> changing = new HashMap<>();
  private final long timerID;
  private final Queue<Deployment> toDeploy = new ConcurrentLinkedQueue<>();
  private final Queue<Deployment> toUndeploy = new ConcurrentLinkedQueue<>();
  private Context ctx;

  private void dumpSizes() {
    log.info("watchkeys: " + watchKeys.size());
    log.info("moduleDirs: " + moduleDirs.size());
    log.info("changing: " + changing.size());
    int size = 0;
    for (Set<Deployment> s: this.watchedDeployments.values()) {
      size += s.size();
    }
    log.info("watcheddeployments:" + size);
  }

  private void checkContext() {
    //Sanity check
    if (Context.getContext() != ctx) {
      throw new IllegalStateException("Got context: " + Context.getContext() + " expected " + ctx);
    }
  }

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
        if (ctx == null) {
          ctx = Context.getContext();
        } else {
          checkContext();
        }
        try {
          checkEvents();
        } catch (Exception e) {
          log.error("Failed to check events", e);
        }
      }
    });
  }

  public void close() {
    vertx.cancelTimer(timerID);
  }

  public void moduleDeployed(Deployment deployment) {
    log.info("module deployed " + deployment.name);
    toDeploy.add(deployment);
  }

  public void moduleUndeployed(Deployment deployment) {
    log.info("module undeployed " + deployment.name);
    toUndeploy.add(deployment);
  }

  // We process all the deployments and undeployments on the same context as the
  // rest of the stuff
  // this means we don't have to synchronize between the deployment and
  // any stuff done on the timer
  private void processDeployments() {
    Deployment dep;
    while ((dep = toDeploy.poll()) != null) {
      deployments.put(dep.name, dep);
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
      log.info("processing undeployment");
      deployments.remove(dep.name);
      File modDir = new File(modRoot, dep.modName);
      Path pModDir = modDir.toPath();
      Set<Deployment> deps = watchedDeployments.get(pModDir);
      deps.remove(dep);
      if (deps.isEmpty()) {
        log.info("unwatching module " + dep.modName);
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
      this.dumpSizes();
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
        log.info("module hasn't changed for 1000 ms so redeploy it");
        toRedeploy.add(entry.getKey());
      }
    }
    if (!toRedeploy.isEmpty()) {
      Set<Deployment> parents = new HashSet<>();
      for (Path moduleDir: toRedeploy) {
        changing.remove(moduleDir);
        computeParents(moduleDir, parents);
      }
      reloader.reloadModules(parents);
    }
  }

  // Some of the modules that need to be redeployed might have been programmatically
  // deployed so we don't redeploy them directly. Instead we need to compute the
  // set of parent modules which are the ones we need to redeploy
  private void computeParents(Path modulePath, Set<Deployment> parents)  {
    Set<Deployment> deps = watchedDeployments.get(modulePath);
    for (Deployment d: deps) {
      if (d.parentDeploymentName != null) {
        Deployment parent = deployments.get(d.parentDeploymentName);
        File f = new File(modRoot, parent.modName);
        computeParents(f.toPath(), parents);
      } else {
        parents.add(d);
      }
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

      log.info("got event in directory " + dir);

      Path moduleDir = moduleDirs.get(dir);
      log.info("module dir is " + moduleDir);

      @SuppressWarnings("unchecked")
      WatchEvent<Path> ev = (WatchEvent<Path>) event;
      Path name = ev.context();

      Path child = dir.resolve(name);

      if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
        log.info("entry modified: " + child);
      } else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
        log.info("entry created: " + child);
        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
          try {
            registerAll(moduleDir, child);
          } catch (IOException e) {
            log.error("Failed to register child", e);
            throw new IllegalStateException(e.getMessage());
          }
        }
      } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
        log.info("entry deleted: " + child);
        moduleDirs.remove(child);
      }
      changed.add(moduleDir);
    }

    boolean valid = key.reset();
    if (!valid) {
      watchKeys.remove(key);
    }
  }

  private void register(Path modDir, Path dir) throws IOException {
    log.info("registering " + dir);
    WatchKey key = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
    watchKeys.put(key, dir);
    moduleDirs.put(dir, modDir);
  }

  private void registerAll(final Path modDir, final Path dir) throws IOException {
    log.info("registering all " + modDir);
    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        register(modDir, dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}