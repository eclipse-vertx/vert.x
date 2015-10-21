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

package io.vertx.core.impl.launcher;

import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.impl.launcher.commands.CommandTestBase;
import io.vertx.core.impl.launcher.commands.HttpTestVerticle;
import io.vertx.core.impl.launcher.commands.RunCommandTest;
import io.vertx.core.spi.launcher.CommandFactory;
import io.vertx.core.spi.launcher.DefaultCommand;
import io.vertx.core.spi.launcher.DefaultCommandFactory;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class LauncherExtensibilityTest extends CommandTestBase {

  static AtomicReference<Boolean> spy = new AtomicReference<>();

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
    waitUntil(() -> {
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
    waitUntil(spy::get);
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

  @Name("foo")
  public static class FooCommand extends DefaultCommand {

    @Override
    public void run() throws CLIException {
      spy.set(true);
    }
  }

}
