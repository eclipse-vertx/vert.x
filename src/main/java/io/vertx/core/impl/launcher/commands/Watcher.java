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

import com.sun.nio.file.SensitivityWatchEventModifier;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * A file alteration monitor based on a {@link WatchService} and watching files matching a set of includes patterns.
 * These patterns are Ant patterns (can use {@literal **, * or ?}). This class takes 2 {@link Handler} as parameter
 * and orchestrate the redeployment method when a matching file is modified (created, updated or deleted). Users have
 * the possibility to execute a shell command during the redeployment. On a file change, the {@code undeploy}
 * {@link Handler} is called, followed by the execution of the user command. Then the {@code deploy} {@link Handler}
 * is invoked.
 * <p/>
 * The watcher watches all files from the current directory and sub-directories.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class Watcher implements Runnable {

  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  private final List<String> includes;
  private final File root;
  private final Handler<Handler<Void>> deploy;
  private final Handler<Handler<Void>> undeploy;
  private final String cmd;

  private WatchService service;
  private volatile boolean closed;

  private Map<WatchKey, File> keyToFile = new HashMap<>();

  /**
   * Creates a new {@link Watcher}.
   *
   * @param root              the root directory
   * @param includes          the list of include patterns, should not be {@code null} or empty
   * @param deploy            the function called when deployment is required
   * @param undeploy          the function called when un-deployment is required
   * @param onRedeployCommand an optional command executed after the un-deployment and before the deployment
   */
  public Watcher(File root, List<String> includes, Handler<Handler<Void>> deploy, Handler<Handler<Void>> undeploy,
                 String onRedeployCommand) {
    this.root = root;
    this.includes = includes;
    this.deploy = deploy;
    this.undeploy = undeploy;
    this.cmd = onRedeployCommand;
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
    try {
      service = FileSystems.getDefault().newWatchService();
    } catch (IOException e) {
      log.error("Cannot initialize the watcher", e);
      return this;
    }

    try {
      registerAll(root.toPath());
    } catch (IOException e) {
      log.error("Cannot register current directory into the watcher service", e);
      return this;
    }

    new Thread(this).start();
    log.info("Starting the vert.x application in redeploy mode");
    deploy.handle(null);

    return this;
  }

  /**
   * Stops watching. This method stops the underlying {@link WatchService}.
   */
  public void close() {
    log.info("Stopping redeployment");
    closed = true;
    if (service != null) {
      try {
        service.close();
      } catch (IOException e) {
        log.error("Cannot stop the watcher service", e);
      }
    }
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
        WatchKey key;
        try {
          key = service.poll(10, TimeUnit.MILLISECONDS);
        } catch (ClosedWatchServiceException | InterruptedException e) {
          // Closing the watch mode.
          return;
        }
        if (key != null) {
          for (WatchEvent<?> event : key.pollEvents()) {
            // get event type
            WatchEvent.Kind<?> kind = event.kind();

            // get file object
            @SuppressWarnings("unchecked")
            WatchEvent<Path> ev = (WatchEvent<Path>) event;
            Path fileName = ev.context();
            File theFile = new File(keyToFile.get(key), fileName.toFile().getName());

            if (kind == ENTRY_CREATE) {
              if (theFile.isDirectory()) {
                // A new directory is created, the WatchService is not very nice with this, we need to register it.
                try {
                  final WatchKey newKey = register(theFile.toPath());
                  keyToFile.put(newKey, theFile);
                } catch (IOException e) {
                  log.error("Cannot register directory " + theFile.getAbsolutePath());
                }
              } else {
                if (match(theFile)) {
                  trigger(theFile);
                }
              }
            } else if (kind == ENTRY_DELETE) {
              if (theFile.isDirectory()) {
                // If it is a directory, check whether or not it was registered, if it does, unregister it.
                // Notice that it does not trigger events for deleted sub-files files.
                removeDirectory(theFile);
              } else {
                if (match(theFile)) {
                  trigger(theFile);
                }
              }
            } else if (kind == ENTRY_MODIFY) {
              if (!theFile.isDirectory()) {
                if (match(theFile)) {
                  trigger(theFile);
                }
              }
            }
          }
          // IMPORTANT: The key must be reset after processed
          key.reset();
        }
      }
    } catch (Throwable e) {
      log.error("An error have been encountered while watching resources - leaving the redeploy mode", e);
    }
  }

  /**
   * Redeployment process.
   *
   * @param theFile the file having triggered the redeployment.
   */
  private void trigger(File theFile) {
    log.info("Trigger redeploy after a change of " + theFile.getAbsolutePath());
    // 1)
    undeploy.handle(v1 -> {
      // 2)
      executeUserCommand(v2 -> {
        // 3)
        deploy.handle(v3 -> log.info("Redeployment done"));
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

  private void removeDirectory(File theFile) {
    for (Map.Entry<WatchKey, File> entry : keyToFile.entrySet()) {
      if (entry.getValue().getAbsolutePath().equals(theFile.getAbsolutePath())) {
        keyToFile.remove(entry.getKey());
        return;
      }
    }
  }

  /**
   * Traverses the directory tree to register all directories in the watch service.
   *
   * @param root the root
   * @throws IOException cannot traverse the tree
   */
  private void registerAll(final Path root) throws IOException {
    // register directory and sub-directories
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
          throws IOException {
        final WatchKey key = register(dir);
        keyToFile.put(key, dir.toFile());
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Registers a directory into the {@link WatchService}. We listen for all events (creation, deletion and
   * modification). To increase reactivity we also pass the sensitivity modifier to 'high' (OpenJDK and Oracle JVM
   * only).
   *
   * @param dir the directory
   * @return the watch key
   * @throws IOException if it cannot be registered.
   */
  private WatchKey register(Path dir) throws IOException {
    try {
      // Detect whether or not the Sensitivity modifier is available. It not a class not found exception is thrown
      this.getClass().getClassLoader().loadClass("com.sun.nio.file.SensitivityWatchEventModifier");
      return dir.register(service, new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY},
          SensitivityWatchEventModifier.HIGH);
    } catch (ClassNotFoundException e) {
      return dir.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    }
  }
}
