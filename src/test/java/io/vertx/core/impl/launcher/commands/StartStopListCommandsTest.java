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
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check the behavior of the start, stop and list commands.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class StartStopListCommandsTest extends CommandTestBase {

  @Before
  public void setUp() throws IOException {
    File manifest = new File("target/test-classes/META-INF/MANIFEST.MF");
    if (manifest.isFile()) {
      manifest.delete();
    }

    super.setUp();
  }

  @Test
  public void testStartListStop() throws InterruptedException {
    record();

    cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
        "--launcher-class", Launcher.class.getName()});

    waitForStartup();
    assertThat(output.toString()).contains("Starting vert.x application");

    output.reset();
    cli.dispatch(new String[]{"list"});
    assertThat(output.toString()).hasLineCount(2);

    // Extract id.
    String[] lines = output.toString().split(System.lineSeparator());
    String id = lines[1];
    output.reset();
    cli.dispatch(new String[]{"stop", id});
    assertThat(output.toString())
        .contains("Stopping vert.x application '" + id + "'")
        .contains("Application '" + id + "' stopped");

    waitForShutdown();

    waitUntil(() -> {
      output.reset();
      cli.dispatch(new String[]{"list"});
      return !output.toString().contains(id);
    });

    assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
  }

  @Test
  public void testStartListStopWithJVMOptions() throws InterruptedException, IOException {
    record();

    cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
        "--launcher-class", Launcher.class.getName(), "--java-opts=-Dfoo=bar -Dbaz=bar", "--redirect-output"});

    waitForStartup();
    assertThat(output.toString()).contains("Starting vert.x application");

    JsonObject content = RunCommandTest.getContent();
    assertThat(content.getString("foo")).isEqualToIgnoringCase("bar");
    assertThat(content.getString("baz")).isEqualToIgnoringCase("bar");

    output.reset();
    cli.dispatch(new String[]{"list"});
    assertThat(output.toString()).hasLineCount(2);

    // Extract id.
    String[] lines = output.toString().split(System.lineSeparator());
    String id = lines[1];
    output.reset();
    cli.dispatch(new String[]{"stop", id});
    assertThat(output.toString())
        .contains("Stopping vert.x application '" + id + "'")
        .contains("Application '" + id + "' stopped");

    waitForShutdown();

    waitUntil(() -> {
      output.reset();
      cli.dispatch(new String[]{"list"});
      return !output.toString().contains(id);
    });

    assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
  }

  private void waitForShutdown() {
    waitUntil(() -> {
      try {
        getHttpCode();
      } catch (IOException e) {
        return true;
      }
      return false;
    });
  }

  private void waitForStartup() {
    waitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        // Ignore it.
      }
      return false;
    });
  }

  @Test
  public void testStartListStopWithId() throws InterruptedException, IOException {
    record();

    cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
        "--launcher-class", Launcher.class.getName(), "--vertx-id=hello"});
    waitForStartup();
    assertThat(output.toString()).contains("Starting vert.x application").contains("hello");

    output.reset();
    cli.dispatch(new String[]{"list"});
    assertThat(output.toString()).hasLineCount(2).contains("hello");

    // Extract id.
    String[] lines = output.toString().split(System.lineSeparator());
    String id = lines[1];
    assertThat(id).isEqualToIgnoringCase("hello");
    output.reset();
    cli.dispatch(new String[]{"stop", id});
    assertThat(output.toString())
        .contains("Stopping vert.x application '" + id + "'")
        .contains("Application '" + id + "' stopped");

    waitForShutdown();

    waitUntil(() -> {
      output.reset();
      cli.dispatch(new String[]{"list"});
      return !output.toString().contains(id);
    });

    assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
  }

  @Test
  public void testStartListStopWithIdAndAnotherArgument() throws InterruptedException, IOException {
    record();

    cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
        "--launcher-class", Launcher.class.getName(), "--vertx-id=hello", "-cluster"});

    waitForStartup();
    assertThat(output.toString()).contains("Starting vert.x application").contains("hello");
    assertThat(RunCommandTest.getContent().getBoolean("clustered")).isTrue();

    output.reset();
    cli.dispatch(new String[]{"list"});
    assertThat(output.toString()).hasLineCount(2).contains("hello");

    // Extract id.
    String[] lines = output.toString().split(System.lineSeparator());
    String id = lines[1];
    assertThat(id).isEqualToIgnoringCase("hello");
    output.reset();
    cli.dispatch(new String[]{"stop", id});
    assertThat(output.toString())
        .contains("Stopping vert.x application '" + id + "'")
        .contains("Application '" + id + "' stopped");

    waitForShutdown();

    waitUntil(() -> {
      output.reset();
      cli.dispatch(new String[]{"list"});
      return !output.toString().contains(id);
    });

    assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
  }

  @Test
  public void testStartListStopWithIdAndAnotherArgumentBeforeId() throws InterruptedException, IOException {
    record();

    cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
        "--launcher-class", Launcher.class.getName(), "-cluster", "--vertx-id=hello"});

    waitForStartup();
    assertThat(output.toString()).contains("Starting vert.x application").contains("hello");
    assertThat(RunCommandTest.getContent().getBoolean("clustered")).isTrue();

    output.reset();
    cli.dispatch(new String[]{"list"});
    assertThat(output.toString()).hasLineCount(2).contains("hello");

    // Extract id.
    String[] lines = output.toString().split(System.lineSeparator());
    String id = lines[1];
    assertThat(id).isEqualToIgnoringCase("hello");
    output.reset();
    cli.dispatch(new String[]{"stop", id});
    assertThat(output.toString())
        .contains("Stopping vert.x application '" + id + "'")
        .contains("Application '" + id + "' stopped");

    waitForShutdown();

    waitUntil(() -> {
      output.reset();
      cli.dispatch(new String[]{"list"});
      return !output.toString().contains(id);
    });

    assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
  }

  private int getHttpCode() throws IOException {
    return ((HttpURLConnection) new URL("http://localhost:8080")
        .openConnection()).getResponseCode();
  }

}