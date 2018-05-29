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

package io.vertx.core.impl.launcher;

import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.impl.launcher.commands.BareCommand;
import io.vertx.core.impl.launcher.commands.CommandTestBase;
import io.vertx.core.impl.launcher.commands.HttpTestVerticle;
import io.vertx.core.impl.launcher.commands.RunCommandTest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.launcher.DefaultCommand;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class LauncherExtensibilityTest extends CommandTestBase {

  private static AtomicReference<Boolean> spy = new AtomicReference<>();

  private Vertx vertx;

  @After
  public void tearDown() throws InterruptedException {
    spy.set(false);
    super.tearDown();
    close(vertx);
  }

  @Test
  public void testExtendingMainVerticle() {
    Launcher myLauncher = new Launcher() {
      @Override
      protected String getMainVerticle() {
        return HttpTestVerticle.class.getName();
      }

      @Override
      public void afterStartingVertx(Vertx vertx) {
        LauncherExtensibilityTest.this.vertx = vertx;
      }
    };

    myLauncher.dispatch(new String[0]);
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
  }

  @Test
  public void testThatALauncherCanAddACommand() {
    Launcher myLauncher = new Launcher() {

      @Override
      protected void load() {
        super.load();
        register(FooCommand.class);
      }
    };

    myLauncher.dispatch(new String[]{"foo"});
    assertThat(myLauncher.getCommandNames()).contains("foo");
    assertWaitUntil(spy::get);
  }

  @Test
  public void testThatALauncherCanHideACommand() {
    Launcher myLauncher = new Launcher() {

      @Override
      protected void load() {
        super.load();
        unregister("start");
      }
    };

    record();
    myLauncher.dispatch(new String[]{"start"});
    stop();
    assertThat(output.toString()).contains("The command 'start' is not a valid command.");
    assertThat(myLauncher.getCommandNames()).doesNotContain("start");
  }

  @Test
  public void testThatCustomLauncherCanCustomizeTheClusteredOption() throws InterruptedException {

    AtomicBoolean asv = new AtomicBoolean();
    AtomicBoolean bsv = new AtomicBoolean();

    Launcher myLauncher = new Launcher() {
      @Override
      protected String getMainVerticle() {
        return HttpTestVerticle.class.getName();
      }

      @Override
      public void afterStartingVertx(Vertx vertx) {
        LauncherExtensibilityTest.this.vertx = vertx;
      }

      @Override
      public void beforeStartingVertx(VertxOptions options) {
        options.setClustered(true);
      }

      @Override
      public void afterStoppingVertx() {
        asv.set(true);
      }

      @Override
      public void beforeStoppingVertx(Vertx vertx) {
        bsv.set(vertx != null);
      }
    };

    myLauncher.dispatch(new String[0]);
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });

    assertThat(this.vertx.isClustered()).isTrue();

    BareCommand.getTerminationRunnable(vertx, LoggerFactory.getLogger("foo"), () -> asv.set(true)).run();

    assertThat(bsv.get()).isTrue();
    assertThat(asv.get()).isTrue();
    vertx = null;
  }

  @Test
  public void testThatCustomLauncherCanUpdateConfigurationWhenNoneArePassed() throws IOException {
    long time = System.nanoTime();
    Launcher myLauncher = new Launcher() {
      @Override
      protected String getMainVerticle() {
        return HttpTestVerticle.class.getName();
      }

      @Override
      public void afterStartingVertx(Vertx vertx) {
        LauncherExtensibilityTest.this.vertx = vertx;
      }

      @Override
      public void afterConfigParsed(JsonObject config) {
        config.put("time", time);
      }
    };

    myLauncher.dispatch(new String[0]);
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });

    assertThat(RunCommandTest.getContent().getJsonObject("conf").getLong("time")).isEqualTo(time);
  }

  @Test
  public void testThatCustomLauncherCanUpdateConfiguration() throws IOException {
    long time = System.nanoTime();
    Launcher myLauncher = new Launcher() {
      @Override
      protected String getMainVerticle() {
        return HttpTestVerticle.class.getName();
      }

      @Override
      public void afterStartingVertx(Vertx vertx) {
        LauncherExtensibilityTest.this.vertx = vertx;
      }

      @Override
      public void afterConfigParsed(JsonObject config) {
        config.put("time", time);
      }
    };

    myLauncher.dispatch(new String[] {"-conf=\"{\"time\":345667}"});
    assertWaitUntil(() -> {
      try {
        return RunCommandTest.getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });

    assertThat(RunCommandTest.getContent().getJsonObject("conf").getLong("time")).isEqualTo(time);
  }

  @Name("foo")
  public static class FooCommand extends DefaultCommand {

    @Override
    public void run() throws CLIException {
      spy.set(true);
    }
  }

}
