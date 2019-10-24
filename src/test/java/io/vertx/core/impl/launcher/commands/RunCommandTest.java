/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check the run command behavior.
 */
public class RunCommandTest extends CommandTestBase {

  private File manifest = new File("target/test-classes/META-INF/MANIFEST.MF");

  @Before
  public void setUp() throws IOException {
    super.setUp();

    if (manifest.isFile()) {
      manifest.delete();
    }
  }

  @After
  public void tearDown() throws InterruptedException {
    super.tearDown();

    final RunCommand run = (RunCommand) cli.getExistingCommandInstance("run");
    if (run != null) {
      Vertx vertx = run.vertx;
      close(vertx);
    }

    FakeClusterManager.reset();
  }

  private void setManifest(String name) throws IOException {
    File source = new File("target/test-classes/META-INF/" + name);
    Files.copy(source.toPath(), manifest.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES);
  }

  @Test
  public void testDeploymentOfJavaVerticle() {
    cli.dispatch(new Launcher(), new String[] {"run", HttpTestVerticle.class.getName()});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
  }

  @Test
  public void testDeploymentOfJavaVerticleWithCluster() throws IOException {
    cli.dispatch(new Launcher(), new String[] {"run", HttpTestVerticle.class.getName(), "-cluster"});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    assertThat(getContent().getBoolean("clustered")).isTrue();
  }

  @Test
  public void testFatJarWithoutMainVerticle() throws IOException {
    setManifest("MANIFEST-Launcher-No-Main-Verticle.MF");
    record();
    cli.dispatch(new Launcher(), new String[0]);
    stop();
    assertThat(output.toString()).contains("Usage:");
  }

  @Test
  public void testFatJarWithMissingMainVerticle() throws IOException, InterruptedException {
    setManifest("MANIFEST-Launcher-Missing-Main-Verticle.MF");
    record();
    cli.dispatch(new Launcher(), new String[]{});
    assertWaitUntil(() -> error.toString().contains("ClassNotFoundException"));
    stop();
  }

  @Test
  public void testFatJarWithHTTPVerticle() throws IOException, InterruptedException {
    setManifest("MANIFEST-Launcher-Http-Verticle.MF");
    cli.dispatch(new Launcher(), new String[]{});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    assertThat(getContent().getBoolean("clustered")).isFalse();
  }

  @Test
  public void testFatJarWithHTTPVerticleWithCluster() throws IOException, InterruptedException {
    setManifest("MANIFEST-Launcher-Http-Verticle.MF");

    cli.dispatch(new Launcher(), new String[]{"-cluster"});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    assertThat(getContent().getBoolean("clustered")).isTrue();
  }

  @Test
  public void testThatHADeploysVerticleWhenCombinedWithCluster() throws IOException {
    setManifest("MANIFEST-Launcher-Http-Verticle.MF");
    cli.dispatch(new Launcher(), new String[] {"-ha", "-cluster"});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    assertThat(getContent().getBoolean("clustered")).isTrue();
  }

  @Test
  public void testThatHADeploysVerticle() throws IOException {
    setManifest("MANIFEST-Launcher-Http-Verticle.MF");
    cli.dispatch(new Launcher(), new String[] {"-ha", "-cluster"});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    assertThat(getContent().getBoolean("clustered")).isTrue();
  }

  @Test
  public void testWithConfProvidedInline() throws IOException {
    long someNumber = new Random().nextLong();
    setManifest("MANIFEST-Launcher-Http-Verticle.MF");
    cli.dispatch(new Launcher(), new String[] {"--conf={\"random\":" + someNumber + "}"});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    assertThat(getContent().getJsonObject("conf").getLong("random")).isEqualTo(someNumber);
  }

  @Test
  public void testWithBrokenConfProvidedInline() throws IOException {
    setManifest("MANIFEST-Launcher-Http-Verticle.MF");
    // There is a missing `}` in the json fragment. This is normal, as the test check that the configuration is not
    // read in this case.
    cli.dispatch(new Launcher(), new String[] {"--conf={\"name\":\"vertx\""});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    assertThat(getContent().getJsonObject("conf").toString()).isEqualToIgnoringCase("{}");
  }

  @Test
  public void testWithConfProvidedAsFile() throws IOException {
    setManifest("MANIFEST-Launcher-Http-Verticle.MF");
    cli.dispatch(new Launcher(), new String[] {"--conf", "target/test-classes/conf.json"});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    assertThat(getContent().getJsonObject("conf").getString("name")).isEqualToIgnoringCase("vertx");
  }

  @Test
  public void testMetricsEnabledFromCommandLine() throws IOException {
    setManifest("MANIFEST-Launcher-Http-Verticle.MF");
    cli.dispatch(new Launcher(), new String[] {"-Dvertx.metrics.options.enabled=true"});
    assertWaitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
    // Check that the metrics are enabled
    // We cannot use the response from the verticle as it uses the DymmyVertxMetrics (no metrics provider)
    assertThat(((RunCommand)cli.getExistingCommandInstance("run")).options.getMetricsOptions().isEnabled()).isTrue();
  }

  public static int getHttpCode() throws IOException {
    return ((HttpURLConnection) new URL("http://localhost:8080")
        .openConnection()).getResponseCode();
  }

  public static JsonObject getContent() throws IOException {
    URL url = new URL("http://localhost:8080");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.connect();
    InputStreamReader in = new InputStreamReader((InputStream) conn.getContent());
    BufferedReader buff = new BufferedReader(in);
    String line;
    StringBuilder builder = new StringBuilder();
    do {
      line = buff.readLine();
      builder.append(line).append("\n");
    } while (line != null);
    buff.close();
    return new JsonObject(builder.toString());
  }


}
