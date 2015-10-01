/*
 *  Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.nio.file.WatchService;
import java.util.*;

/**
 * A file alteration monitor based on a home made file system scan and watching files matching a set of includes
 * patterns. These patterns are Ant patterns (can use {@literal **, * or ?}). This class takes 2 {@link Handler} as
 * parameter and orchestrate the redeployment method when a matching file is modified (created, updated or deleted).
 * Users have the possibility to execute a shell command during the redeployment. On a file change, the {@code undeploy}
 * {@link Handler} is called, followed by the execution of the user command. Then the {@code deploy} {@link Handler}
 * is invoked.
 * <p/>
 * The watcher watches all files from the current directory and sub-directories.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class Watcher implements Runnable {

  private final static Logger LOGGER = LoggerFactory.getLogger(Watcher.class);

  private final long gracePeriod;
  private final Map<File, Map<File, FileInfo>> fileMap = new HashMap<>();
  private final Set<File> filesToWatch = new HashSet<>();
  private final long scanPeriod;

  /**
   * This field is always access from the scan thread. No need to be volatile.
   */
  private long lastChange = -1;

  private final List<String> includes;
  private final File root;
  private final Handler<Handler<Void>> deploy;
  private final Handler<Handler<Void>> undeploy;
  private final String cmd;

  private volatile boolean closed;

  /**
   * Creates a new {@link Watcher}.
   *
   * @param root              the root directory
   * @param includes          the list of include patterns, should not be {@code null} or empty
   * @param deploy            the function called when deployment is required
   * @param undeploy          the function called when un-deployment is required
   * @param onRedeployCommand an optional command executed after the un-deployment and before the deployment
   * @param gracePeriod       the amount of time in milliseconds to wait between two redeploy even
   *                          if there are changes
   * @param scanPeriod        the time in millisecond between 2 file system scans
   */
  public Watcher(File root, List<String> includes, Handler<Handler<Void>> deploy, Handler<Handler<Void>> undeploy,
                 String onRedeployCommand, long gracePeriod, long scanPeriod) {
    this.gracePeriod = gracePeriod;
    this.root = root;
    this.includes = includes;
    this.deploy = deploy;
    this.undeploy = undeploy;
    this.cmd = onRedeployCommand;
    this.scanPeriod = scanPeriod;
    addFileToWatch(root);
  }

  private void addFileToWatch(File file) {
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

        if (files == null) {
          // something really bad happened to the file system.
          throw new IllegalStateException("Cannot scan the file system to detect file changes");
        }

        for (File file : files) {
          newFiles.put(file, file);
        }
      } else {
        newFiles.put(toWatch, toWatch);
      }

      // Lookup the old list for that file/directory
      Map<File, FileInfo> currentFileMap = fileMap.get(toWatch);
      for (Map.Entry<File, FileInfo> currentEntry : new HashMap<>(currentFileMap).entrySet()) {
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
          LOGGER.trace("File: " + currFile + " has been deleted");
          if (match(currFile)) {
            changed = true;
          }
        } else if (newFile.lastModified() != currInfo.lastModified || newFile.length() != currInfo.length) {
          // File has been modified
          currentFileMap.put(newFile, new FileInfo(newFile.lastModified(), newFile.length()));
          LOGGER.trace("File: " + currFile + " has been modified");
          if (match(currFile)) {
            changed = true;
          }
        }
      }

      // Now process any added files
      for (File newFile : newFiles.keySet()) {
        if (!currentFileMap.containsKey(newFile)) {
          // Add new file
          currentFileMap.put(newFile, new FileInfo(newFile.lastModified(), newFile.length()));
          if (newFile.isDirectory()) {
            addFileToWatch(newFile);
          }
          LOGGER.trace("File was added: " + newFile);
          if (match(newFile)) {
            changed = true;
          }
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


  /**
   * Checks whether the given file matches one of the {@link #includes} patterns.
   *
   * @param file the file
   * @return {@code true} if the file matches at least one pattern, {@code false} otherwise.
   */
  protected boolean match(File file) {
    // Compute relative path.
    if (!file.getAbsolutePath().startsWith(root.getAbsolutePath())) {
      return false;
    }
    String rel = file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
    for (String include : includes) {
      if (FileSelector.matchPath(include, rel)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Starts watching. The watch processing is made in another thread started in this method.
   *
   * @return the current watcher.
   */
  public Watcher watch() {
    new Thread(this).start();
    LOGGER.info("Starting the vert.x application in redeploy mode");
    deploy.handle(null);

    return this;
  }

  /**
   * Stops watching. This method stops the underlying {@link WatchService}.
   */
  public void close() {
    LOGGER.info("Stopping redeployment");
    // closing the redeployment thread. If waiting, it will shutdown at the next iteration.
    closed = true;
    // Un-deploy application on close.
    undeploy.handle(null);
  }

  /**
   * The watching thread runnable method.
   */
  @Override
  public void run() {
    try {
      while (!closed) {
        if (changesHaveOccurred()) {
          trigger();
        }
        // Wait for the next scan.
        Thread.sleep(scanPeriod);
      }
    } catch (Throwable e) {
      LOGGER.error("An error have been encountered while watching resources - leaving the redeploy mode", e);
      close();
    }
  }

  /**
   * Redeployment process.
   */
  private void trigger() {
    LOGGER.info("Redeploying!");
    // 1)
    undeploy.handle(v1 -> {
      // 2)
      executeUserCommand(v2 -> {
        // 3)
        deploy.handle(v3 -> LOGGER.info("Redeployment done"));
      });
    });
  }

  private void executeUserCommand(Handler<Void> onCompletion) {
    if (cmd != null) {
      try {
        List<String> command = new ArrayList<>();
        if (ExecUtils.isWindows()) {
          ExecUtils.addArgument(command, cmd);
        } else {
          ExecUtils.addArgument(command, "sh");
          ExecUtils.addArgument(command, "-c");
          ExecUtils.addArgument(command, cmd);
        }

        final Process process = new ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .start();

        process.waitFor();
      } catch (Throwable e) {
        System.err.println("Error while executing the onRedeploy command : '" + cmd + "'");
        e.printStackTrace();
      }
    }
    onCompletion.handle(null);
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
