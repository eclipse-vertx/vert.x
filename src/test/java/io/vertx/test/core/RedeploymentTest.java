/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.test.core;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Utils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedeploymentTest extends VertxTestBase {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  protected File tempDir;
  protected File cpDir;
  protected File jarFile;
  protected DeploymentOptions options;
  protected File projectBaseDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    tempDir = testFolder.newFolder();
    cpDir = new File(tempDir, "cpDir");
    assertTrue(cpDir.mkdir());
    // Copy one jar into that directory
    URLClassLoader urlc = (URLClassLoader)getClass().getClassLoader();
    int count = 0;
    for (URL url: urlc.getURLs()) {
      String surl = url.toString();
      if (surl.endsWith(".jar") && surl.startsWith("file:")) {
        File file = new File(url.toURI());
        File dest = new File(tempDir.getAbsoluteFile(), file.getName());
        jarFile = dest;
        Files.copy(file.toPath(), dest.toPath());
        assertTrue(dest.exists());
        if (++count == 2) {
          break;
        }
      }
    }
    assertNotNull(jarFile);
    options = new DeploymentOptions().setRedeploy(true);
    List<String> extraCP = new ArrayList<>();
    extraCP.add(jarFile.getAbsolutePath());    
    extraCP.add(cpDir.getAbsolutePath());
    options.setExtraClasspath(extraCP);
    String baseDir = System.getProperty("project.basedir");
    if (baseDir == null) {

    }
  }

  @Override
  public void tearDown() throws Exception {
    /* One of the temp files is a jar which is added to the classpath to test verticle redeploy on jar
     * changes.  This jar remains locked by the JVM until all references to this class loader have been
     * removed and garbage collected.  The following clean up statement fails because either there is
     * still some floating reference to the class loader or the JVM hasn't yet garbage collected it.
     */
    if(!Utils.isWindows())
      vertx.fileSystem().deleteRecursiveBlocking(tempDir.getPath(), true);
  }

  @Test
  public void testRedeployOnJarChanged() throws Exception {
    testRedeploy(() -> touchJar());
  }

  @Test
  public void testRedeployOnCreateFile() throws Exception {
    testRedeploy(() -> createFile(cpDir, "newfile.txt"));
  }

  @Test
  public void testRedeployOnTouchFile() throws Exception {
    createFile(cpDir, "newfile.txt");
    testRedeploy(() -> touchFile(cpDir, "newfile.txt"));
  }

  @Test
  public void testRedeployOnChangeFile() throws Exception {
    createFile(cpDir, "newfile.txt");
    testRedeploy(() -> modifyFile(cpDir, "newfile.txt"));
  }

  @Test
  public void testRedeployOnDeleteFile() throws Exception {
    createFile(cpDir, "newfile.txt");
    testRedeploy(() -> deleteFile(cpDir, "newfile.txt"));
  }

  @Test
  public void testRedeployOnCreateSubDir() throws Exception {
    testRedeploy(() -> createDirectory(cpDir, "subdir"));
  }

  @Test
  public void testRedeployOnCreateFileInSubDir() throws Exception {
    testRedeploy(() -> createDirectory(cpDir, "subdir"));
    testRedeploy(() -> createFile(new File(cpDir, "subdir"), "subfile.txt"));
  }

  @Test
  public void testRedeployOnTouchFileInSubDir() throws Exception {
    testRedeploy(() -> createDirectory(cpDir, "subdir"));
    testRedeploy(() -> createFile(new File(cpDir, "subdir"), "subfile.txt"));
    testRedeploy(() -> touchFile(new File(cpDir, "subdir"), "subfile.txt"));
  }

  @Test
  public void testRedeployOnChangeFileInSubDir() throws Exception {
    testRedeploy(() -> createDirectory(cpDir, "subdir"));
    testRedeploy(() -> createFile(new File(cpDir, "subdir"), "subfile.txt"));
    testRedeploy(() -> modifyFile(new File(cpDir, "subdir"), "subfile.txt"));
  }

  @Test
  public void testRedeployOnDeleteFileInSubDir() throws Exception {
    testRedeploy(() -> createDirectory(cpDir, "subdir"));
    testRedeploy(() -> createFile(new File(cpDir, "subdir"), "subfile.txt"));
    testRedeploy(() -> deleteFile(new File(cpDir, "subdir"), "subfile.txt"));
  }

  @Test
  public void testRedeployOnDeleteSubDir() throws Exception {
    testRedeploy(() -> createDirectory(cpDir, "subdir"));
    File subDir = new File(cpDir, "subdir");
    testRedeploy(() -> createFile(subDir, "subfile.txt"));
    testRedeploy(() -> deleteDirectory(subDir));
  }

  @Test
  public void testRedeployMultipleTimes() throws Exception {
    createFile(cpDir, "newfile.txt");
    testRedeploy(() -> touchFile(cpDir, "newfile.txt"), 5, 1500);
  }

  @Test
  public void testRedeploySourceVerticle() throws Exception {
    Files.copy(Paths.get(resolvePath("src/test/resources/redeployverticles/RedeploySourceVerticle.java")),
               new File(cpDir, "RedeploySourceVerticle.java").toPath());
    testRedeploy("java:RedeploySourceVerticle.java", () -> touchFile(cpDir, "RedeploySourceVerticle.java"), 1, 0, 1);
  }

  @Test
  public void testRedeployBrokenSourceVerticleThenFixIt() throws Exception {
    Files.copy(Paths.get(resolvePath("src/test/resources/redeployverticles/RedeploySourceVerticle.java")),
      new File(cpDir, "RedeploySourceVerticle.java").toPath());
    testRedeploy("java:RedeploySourceVerticle.java", () -> {
      // Replace with broken source flile
      try {
        Files.copy(Paths.get(resolvePath("src/test/resources/redeployverticles/BrokenRedeploySourceVerticle.java")),
          new File(cpDir, "RedeploySourceVerticle.java").toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
        throw new VertxException(e);
      }
      vertx.setTimer(2000, id -> {
        // Copy back the fixed file
        try {
          Files.copy(Paths.get(resolvePath("src/test/resources/redeployverticles/RedeploySourceVerticle.java")),
            new File(cpDir, "RedeploySourceVerticle.java").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
          throw new VertxException(e);
        }
      });
    }, 1, 0, 1);
  }

  @Test
  public void testRedeployFailedAndFixIt() throws Exception {

    AtomicInteger startCount = new AtomicInteger();
    // Only fail the second time it's started
    vertx.eventBus().<String>consumer("vertstartok").handler(res -> res.reply(startCount.incrementAndGet() != 2));

    testRedeploy(RedeployFailVerticle.class.getName(), () -> {
      touchJar();
      // Now wait a bit and touch again
      vertx.setTimer(2000, id -> touchJar());
    }, 1, 0, 1);
  }

  @Test
  public void testRedeployFailedAndFixItWithManuallyUndeploy() throws Exception {
    AtomicInteger startCount = new AtomicInteger();
    // Only fail the second time it's started
    vertx.eventBus().<String>consumer("vertstartok").handler(res -> res.reply(startCount.incrementAndGet() != 2));

    String depID = testRedeploy(RedeployFailVerticle.class.getName(), () -> {
      touchJar();
      // Now wait a bit and touch again
      vertx.setTimer(2000, id -> touchJar());
    }, 1, 0, 1);
    vertx.undeploy(depID, onSuccess(v -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testManualUndeployAfterRedeply() throws Exception {
    String depID = testRedeploy(() -> touchJar());
    vertx.undeploy(depID, onSuccess(v -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testDelayedRedeployDueToSequenceOfChanges() throws Exception {
    testRedeploy(RedeployVerticle.class.getName(), () -> touchJar(), 10, DeploymentOptions.DEFAULT_REDEPLOY_GRACE_PERIOD / 2, 1);
  }

  @Test
  public void testChangeAgainDuringRedeploy() throws Exception {
    testRedeploy(() -> {
      touchJar();
      vertx.setTimer(DeploymentOptions.DEFAULT_REDEPLOY_GRACE_PERIOD + 1, id -> {
        touchJar();
      });
    });
  }

  @Test
  public void testOnlyTopMostVerticleRedeployed() throws Exception {
    AtomicInteger startCount = new AtomicInteger();
    vertx.eventBus().<String>consumer("vertstarted").handler(msg -> {
      startCount.incrementAndGet();
    });

    // Top most verticle is set NOT to redeploy
    options.setRedeploy(false);
    CountDownLatch latch = new CountDownLatch(1);
    vertx.deployVerticle(RedeployVerticle2.class.getName(), options, onSuccess(depID -> {
      touchJar();
      latch.countDown();
    }));

    awaitLatch(latch);
    // Now wait a bit, the verticle should not be redeployed
    vertx.setTimer(2000, id -> {
      assertEquals(1, startCount.get());
      testComplete();
    });
    await();
  }

  private void incCount(Map<String, Integer> counts, String depID) {
    Integer in = counts.get(depID);
    if (in == null) {
      in = 0;
    }
    counts.put(depID, 1 + in);
  }

  private void waitUntilCount(Map<String, Integer> counts, String depID, int count) {
    waitUntil(() -> {
      Integer in = counts.get(depID);
      if (in != null) {
        return count == in;
      } else {
        return false;
      }
    }, 60000000);
  }

  protected String testRedeploy(Runnable runner) throws Exception {
    return testRedeploy(RedeployVerticle.class.getName(), runner, 1, 0, 1);
  }

  protected String testRedeploy(Runnable runner, int times, long delay) throws Exception {
    return testRedeploy(RedeployVerticle.class.getName(), runner, times, delay, times);
  }

  protected String testRedeploy(String verticleID, Runnable runner, int times, long delay, int expectedRedeploys) throws Exception {
    Map<String, Integer> startedCounts = new HashMap<>();
    vertx.eventBus().<String>consumer("vertstarted").handler(msg -> {
      String depID = msg.body();
      incCount(startedCounts, depID);
    });
    Map<String, Integer> stoppedCounts = new HashMap<>();
    vertx.eventBus().<String>consumer("vertstopped").handler(msg -> {
      String depID = msg.body();
      incCount(stoppedCounts, depID);
    });

    AtomicReference<String> depRef = new AtomicReference<>();
    CountDownLatch deployLatch = new CountDownLatch(1);

    vertx.deployVerticle(verticleID, options, onSuccess(depID -> {
      depRef.set(depID);
      deployLatch.countDown();
    }));

    awaitLatch(deployLatch);
    runRunner(runner, times, delay);

    waitUntilCount(startedCounts, depRef.get(), 1 + expectedRedeploys);
    waitUntilCount(stoppedCounts, depRef.get(), expectedRedeploys);

    return depRef.get();
  }

  private void runRunner(Runnable runner, int times, long delay) {
    runner.run();
    if (times > 1) {
      vertx.setTimer(delay, id -> runRunner(runner, times - 1, delay));
    }
  }


  protected void touchJar() {
    touchFile(jarFile);
  }

  protected void touchFile(File dir, String fileName) {
    File f = new File(dir, fileName);
    touchFile(f);
  }

  protected void touchFile(File file) {
    long lastMod = file.lastModified();
    assertTrue(file.setLastModified(lastMod + 1001));
  }

  protected void createFile(File dir, String fileName)  {
    File f = new File(dir, fileName);
    vertx.fileSystem().writeFileBlocking(f.getAbsolutePath(), Buffer.buffer(TestUtils.randomAlphaString(1000)));
  }

  protected void createFile(File dir, String fileName, String content)  {
    File f = new File(dir, fileName);
    vertx.fileSystem().writeFileBlocking(f.getAbsolutePath(), Buffer.buffer(content));
  }

  protected void modifyFile(File dir, String fileName)  {
    try {
      File f = new File(dir, fileName);
      FileWriter fw = new FileWriter(f, true);
      fw.write(TestUtils.randomAlphaString(500));
      fw.close();
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  protected void deleteFile(File dir, String fileName)  {
    File f = new File(dir, fileName);
    f.delete();
  }

  protected void createDirectory(File parent, String dirName)  {
    File f = new File(parent, dirName);
    vertx.fileSystem().mkdirBlocking(f.getAbsolutePath());
  }

  protected void deleteDirectory(File directory)  {
    vertx.fileSystem().deleteRecursiveBlocking(directory.getAbsolutePath(), true);
  }

  // Make sure resources are found when running in IDE and on command line
  protected String resolvePath(String path) {
    String buildDir = System.getProperty("buildDirectory");
    String prefix = buildDir != null ? buildDir + "/../" : "";
    return prefix + path;
  }

}

