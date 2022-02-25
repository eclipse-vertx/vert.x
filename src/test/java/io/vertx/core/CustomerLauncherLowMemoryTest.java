/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static io.vertx.test.core.AsyncTestBase.assertWaitUntil;
import static org.junit.Assert.assertTrue;

public class CustomerLauncherLowMemoryTest {

  private static final String MSG_READY = CustomerLauncherLowMemoryTest.class.getSimpleName() + "-ready";
  private static final String MSG_HOOK = CustomerLauncherLowMemoryTest.class.getSimpleName() + "-hook";

  private Process process;
  private File output;

  @Before
  public void setUp() throws Exception {
    output = File.createTempFile(CustomerLauncherLowMemoryTest.class.getSimpleName(), ".txt");
    output.deleteOnExit();
  }

  @After
  public void tearDown() throws Exception {
    if (process != null) {
      process.destroyForcibly();
    }
  }

  @Test
  public void testCloseHookInvoked() throws Exception {
    startExternalProcess();
    assertWaitUntil(() -> outputContains(MSG_READY), 10000, "Verticle failed to deploy");
    stopExternalProcess();
    assertTrue("Hook not invoked", outputContains(MSG_HOOK));
  }

  private void startExternalProcess() throws IOException {
    String javaHome = System.getProperty("java.home");
    String classpath = System.getProperty("java.class.path");

    List<String> command = new ArrayList<>();
    command.add(javaHome + File.separator + "bin" + File.separator + "java");
    command.add("-Xms100M");
    command.add("-Xmx100M");
    command.add("-classpath");
    command.add(classpath);
    command.add(Launcher.class.getName());
    command.add("run");
    command.add(Verticle.class.getName());

    process = new ProcessBuilder(command)
      .redirectOutput(output)
      .redirectErrorStream(true)
      .start();
  }

  private void stopExternalProcess() throws InterruptedException {
    process.destroy();
    process.waitFor();
  }

  private boolean outputContains(String line) {
    try {
      return Files.readAllLines(output.toPath()).contains(line);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class Launcher extends io.vertx.core.Launcher {

    public static void main(String[] args) {
      new Launcher().dispatch(args);
    }

    @Override
    public void beforeStoppingVertx(Vertx vertx) {
      System.out.println(MSG_HOOK);
    }
  }

  public static class Verticle extends AbstractVerticle {

    private final Runtime runtime;
    @SuppressWarnings("unused")
    private List<byte[]> arrays;

    public Verticle() {
      runtime = Runtime.getRuntime();
    }

    @Override
    public void start() throws Exception {
      vertx.<List<byte[]>>executeBlocking(prom -> {
        List<byte[]> res = new ArrayList<>();
        long l;
        do {
          res.add(new byte[5 * 1024]);
          l = runtime.freeMemory();
        } while (l > 15 * 1024 * 1024);
        runtime.gc();
        try {
          Thread.sleep(100);
          prom.complete(res);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          prom.fail(e);
        }
      }, ar -> {
        if (ar.succeeded()) {
          arrays = ar.result();
          System.out.println(MSG_READY);
        } else {
          ar.cause().printStackTrace();
        }
      });
    }
  }
}
