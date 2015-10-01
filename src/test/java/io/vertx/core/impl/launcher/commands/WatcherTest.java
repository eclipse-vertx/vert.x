/*
 *  Copyright (c) 2011-2015 The original author or authors
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the watching service behavior.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class WatcherTest extends CommandTestBase {

  // Note about sleep time - the watcher is configured with a short scan period to avoid that this tests take too
  // much time.

  private Watcher watcher;
  private AtomicInteger deploy;
  private AtomicInteger undeploy;
  private File root;

  @Before
  public void prepare() {
    root = new File("target/junk/watcher");
    deleteRecursive(root);
    root.mkdirs();

    deploy = new AtomicInteger();
    undeploy = new AtomicInteger();

    watcher = new Watcher(root, Collections.singletonList("**" + File.separator + "*.txt"),
        next -> {
          deploy.incrementAndGet();
          if (next != null) {
            next.handle(null);
          }
        },
        next -> {
          undeploy.incrementAndGet();
          if (next != null) {
            next.handle(null);
          }
        },
        null,
        10,
        10);
  }

  @After
  public void close() {
    watcher.close();
  }

  @Test
  public void testFileAddition() throws IOException {
    watcher.watch();

    // Initial deployment
    waitUntil(() -> deploy.get() == 1);

    File file = new File(root, "foo.txt");
    file.createNewFile();

    // undeployment followed by redeployment
    waitUntil(() -> undeploy.get() == 1 && deploy.get() == 2);
  }

  @Test
  public void testWithANonMatchingFile() throws IOException, InterruptedException {
    watcher.watch();

    // Initial deployment
    waitUntil(() -> deploy.get() == 1);

    File file = new File(root, "foo.nope");
    file.createNewFile();

    Thread.sleep(500);
    assertThat(undeploy.get()).isEqualTo(0);
    assertThat(deploy.get()).isEqualTo(1);
  }

  @Test
  public void testFileModification() throws IOException, InterruptedException {
    File file = new File(root, "foo.txt");
    file.createNewFile();

    watcher.watch();

    // Initial deployment
    waitUntil(() -> deploy.get() == 1);

    // Wait until the file monitoring is set up (ugly, but I don't know any way to detect this).
    Thread.sleep(500);
    // Simulate a 'touch'
    file.setLastModified(System.currentTimeMillis());

    // undeployment followed by redeployment
    waitUntil(() -> undeploy.get() == 1 && deploy.get() == 2);
  }

  @Test
  public void testFileDeletion() throws IOException, InterruptedException {
    File file = new File(root, "foo.txt");
    file.createNewFile();

    watcher.watch();

    // Initial deployment
    waitUntil(() -> deploy.get() == 1);

    // Wait until the file monitoring is set up (ugly, but I don't know any way to detect this).
    Thread.sleep(500);
    file.delete();

    // undeployment followed by redeployment
    waitUntil(() -> undeploy.get() == 1 && deploy.get() == 2);
  }

  @Test
  public void testFileAdditionAndModificationInDirectory() throws IOException, InterruptedException {
    watcher.watch();
    // Wait until the file monitoring is set up (ugly, but I don't know any way to detect this).
    Thread.sleep(500);

    // Initial deployment
    waitUntil(() -> deploy.get() == 1);

    File newDir = new File(root, "directory");
    newDir.mkdir();
    Thread.sleep(500);
    File file = new File(newDir, "foo.txt");
    file.createNewFile();

    // undeployment followed by redeployment
    waitUntil(() -> undeploy.get() == 1 && deploy.get() == 2);

    Thread.sleep(1000);

    // Update file
    // Simulate a 'touch'
    file.setLastModified(System.currentTimeMillis());

    // undeployment followed by redeployment
    waitUntil(() -> undeploy.get() == 2 && deploy.get() == 3);

    Thread.sleep(1000);

    // delete directory
    deleteRecursive(newDir);

    waitUntil(() -> undeploy.get() == 3 && deploy.get() == 4);
  }

  public static boolean deleteRecursive(File path) {
    if (!path.exists()) {
      return false;
    }
    boolean ret = true;
    if (path.isDirectory()) {
      File[] files = path.listFiles();
      if (files != null) {
        for (File f : files) {
          ret = ret && deleteRecursive(f);
        }
      }
    }
    return ret && path.delete();
  }

}