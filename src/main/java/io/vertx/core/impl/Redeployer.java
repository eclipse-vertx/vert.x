/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Redeployer implements Action<Boolean> {

  private static final Logger log = LoggerFactory.getLogger(Redeployer.class);

  private final long gracePeriod;
  private final Map<File, Map<File, FileInfo>> fileMap = new HashMap<>();
  private final Set<File> filesToWatch = new HashSet<>();
  private long lastChange = -1;

  public Redeployer(Set<File> files, long gracePeriod) {
    this.gracePeriod = gracePeriod;
    for (File file : files) {
      addFileToWatch(file);
    }
  }

  private void addFileToWatch(File file) {
    // log.trace("Adding file to watch: " + file);
    filesToWatch.add(file);
    Map<File, FileInfo> map = new HashMap<>();
    if (file.isDirectory()) {
      // We're watching a directory contents and its children for changes
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          map.put(child, new FileInfo(child.lastModified(), child.length()));
          if (child.isDirectory()) {
            addFileToWatch(child);
          }
        }
      }
    } else {
      // Not a directory - we're watching a specific file - e.g. a jar
      map.put(file, new FileInfo(file.lastModified(), file.length()));
    }
    fileMap.put(file, map);
  }

  private boolean changesHaveOccurred() {

    boolean changed = false;
    for (File toWatch : new HashSet<>(filesToWatch)) {
      // The new files in the directory
      Map<File, File> newFiles = new HashMap<>();
      if (toWatch.isDirectory()) {
        File[] files = toWatch.exists() ? toWatch.listFiles() : new File[]{};
        for (File file : files) {
          newFiles.put(file, file);
        }
      } else {
        newFiles.put(toWatch, toWatch);
      }
      // Lookup the old list for that file/directory
      Map<File, FileInfo> currentFileMap = fileMap.get(toWatch);
      for (Map.Entry<File, FileInfo> currentEntry: new HashMap<>(currentFileMap).entrySet()) {
        File currFile = currentEntry.getKey();
        FileInfo currInfo = currentEntry.getValue();
        File newFile = newFiles.get(currFile);
        if (newFile == null) {
          // File has been deleted
          currentFileMap.remove(currFile);
          if (currentFileMap.isEmpty()) {
            fileMap.remove(toWatch);
            filesToWatch.remove(toWatch);
          }
          log.trace("File: " + currFile + " has been deleted");
          changed = true;
        } else if (newFile.lastModified() != currInfo.lastModified || newFile.length() != currInfo.length) {
          // File has been modified
          currentFileMap.put(newFile, new FileInfo(newFile.lastModified(), newFile.length()));
          log.trace("File: " + currFile + " has been modified");
          changed = true;
        }
      }
      // Now process any added files
      for (File newFile: newFiles.keySet()) {
        if (!currentFileMap.containsKey(newFile)) {
          // Add new file
          currentFileMap.put(newFile, new FileInfo(newFile.lastModified(), newFile.length()));
          if (newFile.isDirectory()) {
            addFileToWatch(newFile);
          }
          log.trace("File was added: " + newFile);
          changed = true;
        }
      }
    }

    long now = System.currentTimeMillis();
    if (changed) {
      lastChange = now;
    }

    if (lastChange != -1 && now - lastChange >= gracePeriod) {
      lastChange = -1;
      return true;
    }

    return false;
  }

  @Override
  public Boolean perform() {
    return changesHaveOccurred();
  }

  private static final class FileInfo {
    long lastModified;
    long length;

    private FileInfo(long lastModified, long length) {
      this.lastModified = lastModified;
      this.length = length;
    }
  }
}
