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

import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check the redeployment behavior.
 */
public class RedeployTest extends CommandTestBase {

  private void waitForTermination() {
    assertWaitUntil(() -> {
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
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
  }

  @Test
  public void testStartingApplicationInRedeployModeWithInlineConf() throws IOException {
    int random = (int) (Math.random() * 100);
    cli.dispatch(new Launcher(), new String[]{"run",
        HttpTestVerticle.class.getName(), "--redeploy=**" + File.separator + "*.txt",
        "--launcher-class=" + Launcher.class.getName(),
        "--conf", "{\"random\":" + random + "}"
    });
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });

    JsonObject conf = RunCommandTest.getContent().getJsonObject("conf");
    assertThat(conf).isNotNull().isNotEmpty();
    assertThat(conf.getInteger("random")).isEqualTo(random);
  }

  @Test
  public void testStartingApplicationInRedeployModeWithInlineConf2() throws IOException {
    int random = (int) (Math.random() * 100);
    cli.dispatch(new Launcher(), new String[]{"run",
        HttpTestVerticle.class.getName(), "--redeploy=**" + File.separator + "*.txt",
        "--launcher-class=" + Launcher.class.getName(),
        "--conf={\"random\":" + random + "}"
    });
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });

    JsonObject conf = RunCommandTest.getContent().getJsonObject("conf");
    assertThat(conf).isNotNull().isNotEmpty();
    assertThat(conf.getInteger("random")).isEqualTo(random);
  }

  @Test
  public void testStartingApplicationInRedeployModeWithFileConf() throws IOException {
    cli.dispatch(new Launcher(), new String[]{"run",
        HttpTestVerticle.class.getName(), "--redeploy=**" + File.separator + "*.txt",
        "--launcher-class=" + Launcher.class.getName(),
        "--conf", new File("src/test/resources/conf.json").getAbsolutePath()
    });
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });

    JsonObject conf = RunCommandTest.getContent().getJsonObject("conf");
    assertThat(conf).isNotNull().isNotEmpty();
    assertThat(conf.getString("name")).isEqualTo("vertx");
  }

  @Test
  public void testStartingApplicationInRedeployModeWithFileConf2() throws IOException {
    cli.dispatch(new Launcher(), new String[]{"run",
        HttpTestVerticle.class.getName(), "--redeploy=**" + File.separator + "*.txt",
        "--launcher-class=" + Launcher.class.getName(),
        "--conf=" + new File("src/test/resources/conf.json").getAbsolutePath()
    });
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });

    JsonObject conf = RunCommandTest.getContent().getJsonObject("conf");
    assertThat(conf).isNotNull().isNotEmpty();
    assertThat(conf.getString("name")).isEqualTo("vertx");
  }

  @Test
  public void testStartingApplicationInRedeployModeWithCluster() throws IOException {
    cli.dispatch(new Launcher(), new String[]{"run",
        HttpTestVerticle.class.getName(), "--redeploy=**" + File.separator + "*.txt",
        "--launcher-class=" + Launcher.class.getName(),
        "--cluster",
        ExecUtils.isWindows() ? "--redeploy-termination-period=3000" : ""
    });
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    }, 20000);
    assertThat(RunCommandTest.getContent().getBoolean("clustered")).isTrue();
  }

  @Test
  public void testRedeployment() throws IOException {
    cli.dispatch(new Launcher(), new String[]{"run",
        HttpTestVerticle.class.getName(), "--redeploy=**" + File.separator + "*.txt",
        "--launcher-class=" + Launcher.class.getName(),
        ExecUtils.isWindows() ? "--redeploy-termination-period=3000" : ""
    });
    assertWaitUntil(() -> {
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

    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200 && start1 != RunCommandTest.getContent().getLong("startTime");
      } catch (IOException e) {
        return false;
      }
    }, 20000);
  }

  @Test
  public void testRedeploymentWithSlash() throws IOException {
    cli.dispatch(new Launcher(), new String[]{"run",
        // Use "/", on windows it gets replaced.
        HttpTestVerticle.class.getName(), "--redeploy=**" + "/" + "*.txt",
        "--launcher-class=" + Launcher.class.getName(),
        ExecUtils.isWindows() ? "--redeploy-termination-period=3000" : ""
    });
    assertWaitUntil(() -> {
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

    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200 && start1 != RunCommandTest.getContent().getLong("startTime");
      } catch (IOException e) {
        return false;
      }
    }, 20000);
  }

}
