/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.launcher.commands;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

  protected Watcher watcher;
  protected AtomicInteger deploy;
  protected AtomicInteger undeploy;
  protected File root;

  @Before
  public void prepare() {
    root = new File("target/junk/watcher");
    deleteRecursive(root);
    root.mkdirs();

    deploy = new AtomicInteger();
    undeploy = new AtomicInteger();

    watcher = new Watcher(root, Collections.unmodifiableList(
        Arrays.asList("**" + File.separator + "*.txt", "windows\\*.win", "unix/*.nix", "FOO.bar")), next -> {
      deploy.incrementAndGet();
      if (next != null) {
        next.handle(null);
      }
    }, next -> {
      undeploy.incrementAndGet();
      if (next != null) {
        next.handle(null);
      }
    }, null, 10, 10);
  }

  @After
  public void close() {
    watcher.close();
  }

  @Test
  public void testFileAddition() throws IOException {
    watcher.watch();

    // Initial deployment
    assertWaitUntil(() -> deploy.get() == 1);

    File file = new File(root, "foo.txt");
    file.createNewFile();

    // undeployment followed by redeployment
    assertWaitUntil(() -> undeploy.get() == 1 && deploy.get() == 2);
  }

  @Test
  public void testWithANonMatchingFile() throws IOException, InterruptedException {
    watcher.watch();

    // Initial deployment
    assertWaitUntil(() -> deploy.get() == 1);

    File file = new File(root, "foo.nope");
    file.createNewFile();

    Thread.sleep(500);
    assertThat(undeploy.get()).isEqualTo(0);
    assertThat(deploy.get()).isEqualTo(1);
  }

  @Test
  public void testFileModification() throws IOException, InterruptedException {
    File file = new File(root, "foo.txt");
    watcher.watch();

    // Initial deployment
    assertWaitUntil(() -> deploy.get() == 1);

    file.createNewFile();

    assertWaitUntil(() -> deploy.get() == 2);

    // Simulate a 'touch', we wait more than a second to avoid being in the same second as the creation. This is because
    // some FS return the last modified date with a second of precision.
    Thread.sleep(1500);
    file.setLastModified(System.currentTimeMillis());

    // undeployment followed by redeployment
    assertWaitUntil(() -> undeploy.get() == 2 && deploy.get() == 3);
  }

  @Test
  public void testFileDeletion() throws IOException, InterruptedException {
    File file = new File(root, "foo.txt");
    file.createNewFile();

    watcher.watch();

    // Initial deployment
    assertWaitUntil(() -> deploy.get() == 1);

    // Wait until the file monitoring is set up (ugly, but I don't know any way to detect this).
    Thread.sleep(500);
    file.delete();

    // undeployment followed by redeployment
    assertWaitUntil(() -> undeploy.get() == 1 && deploy.get() == 2);
  }

  @Test
  public void testFileAdditionAndModificationInDirectory() throws IOException, InterruptedException {
    watcher.watch();
    // Wait until the file monitoring is set up (ugly, but I don't know any way to detect this).
    Thread.sleep(500);

    // Initial deployment
    assertWaitUntil(() -> deploy.get() == 1);

    File newDir = new File(root, "directory");
    newDir.mkdir();
    Thread.sleep(500);
    File file = new File(newDir, "foo.txt");
    file.createNewFile();

    // undeployment followed by redeployment
    assertWaitUntil(() -> undeploy.get() == 1 && deploy.get() == 2);

    Thread.sleep(1000);

    // Update file
    // Simulate a 'touch'
    file.setLastModified(System.currentTimeMillis());

    // undeployment followed by redeployment
    assertWaitUntil(() -> undeploy.get() == 2 && deploy.get() == 3);

    Thread.sleep(1000);

    // delete directory
    deleteRecursive(newDir);

    assertWaitUntil(() -> undeploy.get() == 3 && deploy.get() == 4);
  }

  @Test
  public void testOSSpecificIncludePaths() throws IOException, InterruptedException {
    watcher.watch();

    // Initial deployment
    assertWaitUntil(() -> deploy.get() == 1);

    // create a file to be matched using windows style include pattern
    File winDir = new File(root, "windows");
    winDir.mkdir();
    Thread.sleep(500);
    File winFile = new File(winDir, "foo.win");
    winFile.createNewFile();

    // undeployment followed by redeployment
    assertWaitUntil(() -> undeploy.get() == 1 && deploy.get() == 2);
    Thread.sleep(1000);

    // create a file to be matched using *nix style include pattern
    File nixDir = new File(root, "unix");
    nixDir.mkdir();
    Thread.sleep(500);
    File nixFile = new File(nixDir, "foo.nix");
    nixFile.createNewFile();

    // undeployment followed by redeployment
    assertWaitUntil(() -> undeploy.get() == 2 && deploy.get() == 3);
  }

  @Test
  public void testCaseSensitivity() throws IOException, InterruptedException {
    watcher.watch();

    // Initial deployment
    assertWaitUntil(() -> deploy.get() == 1);

    // create a file to be matched against "FOO.bar" pattern
    File file = new File(root, "fOo.BAr");
    file.createNewFile();

    if (ExecUtils.isWindows()) {
      // undeployment followed by redeployment. Windows is not case sensitive
      assertWaitUntil(() -> undeploy.get() == 1 && deploy.get() == 2, "expected undeploy " + undeploy.get() + " == 1 and deploy " + deploy.get() + " == 2");
    } else {
      // It depends on the file system, we can't really test anything in a reliable way.
    }
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

  @Test
  public void testWatcherPerformances() throws IOException, InterruptedException {
    List<File> files = new ArrayList<>();
    // Create structure - 3 sub-dir with each 10 sub dir, wich another level of directory with each 50 files
    for (int i = 0; i < 3; i++) {
      File dir = new File(root, Integer.toString(i));
      dir.mkdirs();
      for (int j = 0; j < 10; j++) {
        File sub = new File(root, Integer.toString(j));
        sub.mkdirs();
        File subsub = new File(sub, "sub");
        subsub.mkdirs();

        for (int k = 0; k < 1000; k++) {
          File txt = new File(subsub, k + ".txt");
          files.add(txt);
          Files.write(txt.toPath(), Integer.toString(k).getBytes());

          File java = new File(subsub, k + ".java");
          Files.write(java.toPath(), Integer.toString(k).getBytes());
        }
      }
    }

    System.out.println("Environment setup");
    watcher.watch();
    assertWaitUntil(() -> deploy.get() == 1);

    long begin = System.currentTimeMillis();
    Path path = files.get(0).toPath();
    Thread.sleep(1000); // Need to wait to be sure we are not in the same second as the previous write
    Files.write(path, "booooo".getBytes());
    assertWaitUntil(() -> undeploy.get() == 1);
    assertWaitUntil(() -> deploy.get() == 2);
    long end = System.currentTimeMillis();
    System.out.println("Update change applied in " + (end - begin) + " ms");

    begin = System.currentTimeMillis();
    files.get(1).delete();
    assertWaitUntil(() -> undeploy.get() == 2);
    assertWaitUntil(() -> deploy.get() == 3);
    end = System.currentTimeMillis();
    System.out.println("Deletion change applied in " + (end - begin) + " ms");

    begin = System.currentTimeMillis();
    files.get(1).delete();
    File newFile = new File(root, "test.txt");
    Files.write(newFile.toPath(), "I'm a new file".getBytes());
    assertWaitUntil(() -> undeploy.get() == 3);
    assertWaitUntil(() -> deploy.get() == 4);
    end = System.currentTimeMillis();
    System.out.println("Creation change applied in " + (end - begin) + " ms");
  }

  @Test
  public void testRootExtraction() {
    List<String> patterns = new ArrayList<>();
    patterns.add("src/main/java/**/*.java");
    List<File> results = Watcher.extractRoots(root, patterns);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getAbsolutePath()).contains(root.getAbsolutePath());


    patterns.clear();
    patterns.add(root.getParentFile().getAbsolutePath());
    results = Watcher.extractRoots(root, patterns);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getAbsolutePath()).contains(root.getParentFile().getAbsolutePath());

    patterns.clear();
    patterns.add("**/*.java");
    results = Watcher.extractRoots(root, patterns);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getAbsolutePath()).contains(root.getAbsolutePath());

    patterns.clear();
    patterns.add(root.getParentFile().getAbsolutePath() + "/**/*.java");
    results = Watcher.extractRoots(root, patterns);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getAbsolutePath()).contains(root.getParentFile().getAbsolutePath());

    patterns.clear();
    patterns.add(root.getParentFile().getAbsolutePath() + "/foo.txt");
    results = Watcher.extractRoots(root, patterns);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getAbsolutePath()).contains(root.getParentFile().getAbsolutePath());

    patterns.clear();
    patterns.add("foo.txt");
    results = Watcher.extractRoots(root, patterns);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getAbsolutePath()).contains(root.getParentFile().getAbsolutePath());

  }

}
