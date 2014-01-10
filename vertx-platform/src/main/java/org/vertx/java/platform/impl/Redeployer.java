/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;

public class Redeployer {

  private static final Logger log = LoggerFactory.getLogger(Redeployer.class);

  private static final long CHECK_PERIOD = 240;
  private static final long GRACE_PERIOD = 1000;

  private final VertxInternal vertx;
  private final ModuleReloader reloader;

  private final Map<ModuleIdentifier, Set<Deployment>> deployments = new HashMap<>();
  private final Map<ModuleIdentifier, Set<File>> watchedDirs = new HashMap<>();
  private final Map<File, Map<File, FileInfo>> directoryFiles = new HashMap<>();
  private final Map<ModuleIdentifier, Long> changing = new HashMap<>();

  private long timerID;
  private boolean closed;
  private boolean scannerStarted;

  private Runnable checker = new Runnable() {
    public void run() {
      checkForChanges();
    }
  };

  private static final class FileInfo {
    long lastModified;
    long length;

    private FileInfo(long lastModified, long length) {
      this.lastModified = lastModified;
      this.length = length;
    }
  }

  public boolean isEmpty() {
    return deployments.isEmpty() && watchedDirs.isEmpty() && directoryFiles.isEmpty();
  }

  public Redeployer(VertxInternal vertx, ModuleReloader reloader) {
    this.vertx = vertx;
    this.reloader = reloader;
  }

  private void setTimer() {
    timerID = vertx.setTimer(CHECK_PERIOD, new Handler<Long>() {
      public void handle(Long id) {
        runInBackground(checker);
      }
    });
  }

  public synchronized void close() {
    vertx.cancelTimer(timerID);
    scannerStarted = false;
    closed = true;
  }

  public synchronized void moduleDeployed(Deployment deployment) {
    log.trace("Registering module: " + deployment.modID);
    Set<Deployment> deps = deployments.get(deployment.modID);
    if (deps == null) {
      Set<File> watched = new HashSet<>();
      List<URL> totCP = new ArrayList<>();
      // We need to watch not only the classpath of the modules but also the cp of any modules that it includes
      Collections.addAll(totCP, deployment.classpath);
      if (deployment.includedClasspath != null) {
        Collections.addAll(totCP, deployment.includedClasspath);
      }
      for (URL url: totCP) {
        String sfile = url.getFile();
        if (sfile != null) {
          File file = new File(sfile);
          if (file.isDirectory()) {
            addDirectory(watched, file);
          }
        }
      }
      watchedDirs.put(deployment.modID, watched);
      deps = new HashSet<>();
      deployments.put(deployment.modID, deps);
    }
    deps.add(deployment);
    if (!scannerStarted) {
      setTimer();
      scannerStarted = true;
    }
  }

  private void addDirectory(Set<File> dirs, File directory) {
    if (!directory.isDirectory()) {
      throw new IllegalStateException("Not directory: " + directory);
    }
    dirs.add(directory);
    File[] children = directory.listFiles();
    Map<File, FileInfo> map = new HashMap<>();
    for (File child: children) {
      map.put(child, new FileInfo(child.lastModified(), child.length()));
    }
    directoryFiles.put(directory, map);
    for (File child: children) {
      if (child.isDirectory()) {
        addDirectory(dirs, child);
      }
    }
  }

  public synchronized void moduleUndeployed(Deployment deployment) {
    Set<File> watched = watchedDirs.remove(deployment.modID);
    if (watched != null) {
      for (File dir: watched) {
        directoryFiles.remove(dir);
      }
    }
    Set<Deployment> deps = deployments.get(deployment.modID);
    if (deps != null) {
      deps.remove(deployment);
      if (deps.isEmpty()) {
        deployments.remove(deployment.modID);
      }
    }
  }

  private synchronized void checkForChanges() {
    if (closed) {
      return;
    }
    Set<ModuleIdentifier> haveChanged = new HashSet<>();
    for (Map.Entry<ModuleIdentifier, Set<File>> entry: watchedDirs.entrySet()) {
      ModuleIdentifier modID = entry.getKey();
      Set<File> dirs = entry.getValue();
      boolean changed = false;
      for (File dir: new HashSet<>(dirs)) {
        File[] files = dir.exists() ? dir.listFiles() : new File[] {};
        Map<File, File> newFiles = new HashMap<>();
        for (File file: files) {
          newFiles.put(file, file);
        }
        Map<File, FileInfo> currentFileMap = directoryFiles.get(dir);
        for (Map.Entry<File, FileInfo> currentEntry: new HashMap<>(currentFileMap).entrySet()) {
          File currFile = currentEntry.getKey();
          FileInfo currInfo = currentEntry.getValue();
          File newFile = newFiles.get(currFile);
          if (newFile == null) {
            // File has been deleted
            currentFileMap.remove(currFile);
            if (currentFileMap.isEmpty()) {
              directoryFiles.remove(dir);
              dirs.remove(dir);
            }
            changed = true;
          } else if (newFile.lastModified() != currInfo.lastModified || newFile.length() != currInfo.length) {
            // File has been modified
            currentFileMap.put(newFile, new FileInfo(newFile.lastModified(), newFile.length()));
            changed = true;
          }
        }
        // Now process any added files
        for (File newFile: files) {
          if (!currentFileMap.containsKey(newFile)) {
            // Add new file
            currentFileMap.put(newFile, new FileInfo(newFile.lastModified(), newFile.length()));
            if (newFile.isDirectory()) {
              Set<File> dirsToAdd = new HashSet<>();
              addDirectory(dirsToAdd, newFile);
              dirs.addAll(dirsToAdd);
            }
            changed = true;
          }
        }
      }
      if (changed) {
        haveChanged.add(modID);
      }
    }

    long now = System.currentTimeMillis();
    for (ModuleIdentifier modID: haveChanged) {
      changing.put(modID, now);
    }

    Set<ModuleIdentifier> toReload = new HashSet<>();
    Set<ModuleIdentifier> toNotReload = new HashSet<>();

    for (Map.Entry<ModuleIdentifier, Long> changeEntry: new HashMap<>(changing).entrySet()) {
      ModuleIdentifier modID = changeEntry.getKey();
      if (now - changeEntry.getValue() >= GRACE_PERIOD) {
        toReload.add(modID);
        changing.remove(modID);
      } else {
        toNotReload.add(modID);
      }
    }

    // We don't want to reload a module if ANY of it's files are still changing
    toReload.removeAll(toNotReload);

    for (ModuleIdentifier modID: toReload) {
      // Reload module
      log.info("Module " + modID + " has changed, reloading it.");
      Set<Deployment> deps = deployments.get(modID);
      reloader.reloadModules(deps);
    }

    setTimer();
  }

  private void runInBackground(final Runnable runnable) {
    vertx.getBackgroundPool().execute(new Runnable() {
      public void run() {
        vertx.setContext(null);
        try {
          runnable.run();
        } catch (Throwable t) {
          log.error("Failed to run task", t);
        } finally {
          vertx.setContext(null);
        }
      }
    });
  }

}
