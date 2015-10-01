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

import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check the redeployment behavior.
 */
public class RedeployTest extends CommandTestBase {

  private void waitForTermination() {
    waitUntil(() -> {
      try {
        RunCommandTest.getHttpCode();
        return false;
      } catch (IOException e) {
        return true;
      }
    });
  }

  @After
  public void tearDown() throws InterruptedException {
    super.tearDown();

    final RunCommand run = (RunCommand) cli.getExistingCommandInstance("run");
    if (run != null) {
      Vertx vertx = run.vertx;
      close(vertx);

      run.stopBackgroundApplication(null);
      run.shutdownRedeployment();
    }

    FakeClusterManager.reset();

    waitForTermination();
  }

  @Test
  public void testStartingApplicationInRedeployMode() {
    cli.dispatch(new Launcher(), new String[]{"run",
        HttpTestVerticle.class.getName(), "--redeploy=**" + File.separator + "*.txt",
        "--launcher-class=" + Launcher.class.getName()
    });
    waitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
  }

  @Test
  public void testStartingApplicationInRedeployModeWithCluster() throws IOException {
    cli.dispatch(new Launcher(), new String[]{"run",
        HttpTestVerticle.class.getName(), "--redeploy=**" + File.separator + "*.txt",
        "--launcher-class=" + Launcher.class.getName(),
        "--cluster"
    });
    waitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    assertThat(RunCommandTest.getContent().getBoolean("clustered")).isTrue();
  }

  @Test
  public void testRedeployment() throws IOException {
    cli.dispatch(new Launcher(), new String[]{"run",
        HttpTestVerticle.class.getName(), "--redeploy=**" + File.separator + "*.txt",
        "--launcher-class=" + Launcher.class.getName(),
    });
    waitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    long start1 = RunCommandTest.getContent().getLong("startTime");

    File file = new File("target/test-classes/foo.txt");
    if (file.exists()) {
      file.delete();
    }
    file.createNewFile();

    waitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200 && start1 != RunCommandTest.getContent().getLong("startTime");
      } catch (IOException e) {
        return false;
      }
    });
  }

}