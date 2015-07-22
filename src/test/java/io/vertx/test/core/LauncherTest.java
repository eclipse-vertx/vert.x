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

import io.vertx.core.*;
import io.vertx.core.cli.commands.RunCommand;
import io.vertx.core.cli.commands.VersionCommand;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.metrics.VertxMetrics;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class LauncherTest extends VertxTestBase {

  private String expectedVersion;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestVerticle.instanceCount.set(0);
    TestVerticle.processArgs = null;
    TestVerticle.conf = null;

    // Read the expected version from the vertx=version.txt
    final URL resource = this.getClass().getClassLoader().getResource("vertx-version.txt");
    if (resource == null) {
      throw new IllegalStateException("Cannot find the vertx-version.txt");
    } else {
      BufferedReader in = new BufferedReader(
          new InputStreamReader(resource.openStream()));
      expectedVersion = in.readLine();
      in.close();
    }
  }

  @Override
  public void tearDown() throws Exception {
    clearProperties();
    super.tearDown();
  }


  @Test
  public void testVersion() throws Exception {
    String[] args = new String[]{"-version"};
    MyLauncher launcher = new MyLauncher();
    launcher.dispatch(args);

    assertEquals(launcher.getVersion(), expectedVersion);
  }

  @Test
  public void testRunVerticle() throws Exception {
    testRunVerticleMultiple(1);
  }

  @Test
  public void testRunVerticleMultipleInstances() throws Exception {
    testRunVerticleMultiple(10);
  }

  public void testRunVerticleMultiple(int instances) throws Exception {
    MyLauncher launcher = new MyLauncher();
    String[] args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName(), "-instances", String.valueOf(instances)};
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == instances);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    launcher.assertHooksInvoked();
  }

  @Test
  public void testRunVerticleClustered() throws Exception {
    MyLauncher launcher = new MyLauncher();
    String[] args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName(), "-cluster"};
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    launcher.assertHooksInvoked();
  }

  @Test
  public void testRunVerticleHA() throws Exception {
    MyLauncher launcher = new MyLauncher();
    String[] args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName(), "-ha"};
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    launcher.assertHooksInvoked();
  }


  @Test
  public void testRunVerticleWithExtendedMainVerticleNoArgs() throws Exception {
    MySecondLauncher launcher = new MySecondLauncher();
    String[] args = new String[0];
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
  }

  @Test
  public void testRunVerticleWithExtendedMainVerticleWithArgs() throws Exception {
    MySecondLauncher launcher = new MySecondLauncher();
    String[] args = new String[]{"-cluster", "-worker"};
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
  }

  @Test
  public void testRunVerticleWithConfString() throws Exception {
    MyLauncher launcher = new MyLauncher();
    JsonObject conf = new JsonObject().put("foo", "bar").put("wibble", 123);
    String[] args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName(), "-conf", conf.encode()};
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(conf, TestVerticle.conf);
  }

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();


  @Test
  public void testRunVerticleWithConfFile() throws Exception {
    Path tempDir = testFolder.newFolder().toPath();
    Path tempFile = Files.createTempFile(tempDir, "conf", "json");
    MyLauncher launcher = new MyLauncher();
    JsonObject conf = new JsonObject().put("foo", "bar").put("wibble", 123);
    Files.write(tempFile, conf.encode().getBytes());
    String[] args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName(), "-conf", tempFile.toString()};
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(conf, TestVerticle.conf);
  }

  @Test
  public void testConfigureFromSystemProperties() throws Exception {
    testConfigureFromSystemProperties(false);
  }

  @Test
  public void testConfigureFromSystemPropertiesClustered() throws Exception {
    testConfigureFromSystemProperties(true);
  }

  private void testConfigureFromSystemProperties(boolean clustered) throws Exception {

    // One for each type that we support
    System.setProperty(RunCommand.VERTX_OPTIONS_PROP_PREFIX + "eventLoopPoolSize", "123");
    System.setProperty(RunCommand.VERTX_OPTIONS_PROP_PREFIX + "maxEventLoopExecuteTime", "123767667");
    System.setProperty(RunCommand.METRICS_OPTIONS_PROP_PREFIX + "enabled", "true");
    System.setProperty(RunCommand.VERTX_OPTIONS_PROP_PREFIX + "haGroup", "somegroup");

    MyLauncher launcher = new MyLauncher();
    String[] args;
    if (clustered) {
      args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName(), "-cluster"};
    } else {
      args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName()};
    }
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = launcher.getVertxOptions();

    assertEquals(123, opts.getEventLoopPoolSize(), 0);
    assertEquals(123767667l, opts.getMaxEventLoopExecuteTime());
    assertEquals(true, opts.getMetricsOptions().isEnabled());
    assertEquals("somegroup", opts.getHAGroup());

  }

  private void clearProperties() {
    Set<String> toClear = new HashSet<>();
    Enumeration e = System.getProperties().propertyNames();
    // Uhh, properties suck
    while (e.hasMoreElements()) {
      String propName = (String) e.nextElement();
      if (propName.startsWith("vertx.options")) {
        toClear.add(propName);
      }
    }
    toClear.forEach(System::clearProperty);
  }

  @Test
  public void testCustomMetricsOptions() throws Exception {
    try {
      ConfigurableMetricsFactory.delegate = new VertxMetricsFactory() {
        @Override
        public VertxMetrics metrics(Vertx vertx, VertxOptions options) {
          return new DummyVertxMetrics();
        }

        @Override
        public MetricsOptions newOptions() {
          return new CustomMetricsOptions();
        }
      };
      System.setProperty(RunCommand.METRICS_OPTIONS_PROP_PREFIX + "enabled", "true");
      System.setProperty(RunCommand.METRICS_OPTIONS_PROP_PREFIX + "customProperty", "customPropertyValue");
      MyLauncher launcher = new MyLauncher();
      String[] args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName()};
      launcher.dispatch(args);
      waitUntil(() -> TestVerticle.instanceCount.get() == 1);
      VertxOptions opts = launcher.getVertxOptions();
      CustomMetricsOptions custom = (CustomMetricsOptions) opts.getMetricsOptions();
      assertEquals("customPropertyValue", custom.getCustomProperty());
    } finally {
      ConfigurableMetricsFactory.delegate = null;
    }
  }

  @Test
  public void testConfigureFromSystemPropertiesInvalidPropertyName() throws Exception {

    System.setProperty(RunCommand.VERTX_OPTIONS_PROP_PREFIX + "nosuchproperty", "123");

    // Should be ignored

    MyLauncher launcher = new MyLauncher();
    String[] args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName()};
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = launcher.getVertxOptions();
    assertEquals(new VertxOptions(), opts);

  }

  @Test
  public void testConfigureFromSystemPropertiesInvalidPropertyType() throws Exception {
    // One for each type that we support
    System.setProperty(RunCommand.VERTX_OPTIONS_PROP_PREFIX + "eventLoopPoolSize", "sausages");
    // Should be ignored

    MyLauncher launcher = new MyLauncher();
    String[] args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName()};
    launcher.dispatch(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = launcher.getVertxOptions();
    assertEquals(new VertxOptions(), opts);
  }

  @Test
  public void testWhenPassingTheMainObject() throws Exception {
    MyLauncher launcher = new MyLauncher();
    int instances = 10;
    launcher.dispatch(launcher, new String[] {"run", "java:" + TestVerticle.class.getCanonicalName(),
        "-instances", "10"});
    waitUntil(() -> TestVerticle.instanceCount.get() == instances);
  }

  @Test
  public void testBare() throws Exception {
    MyLauncher launcher = new MyLauncher();
    launcher.dispatch(new String[] {"run", "-ha"});
    waitUntil(() -> launcher.afterStartingVertxInvoked);
  }

  class MyLauncher extends Launcher {
    boolean beforeStartingVertxInvoked = false;
    boolean afterStartingVertxInvoked = false;
    boolean beforeDeployingVerticle = false;

    Vertx vertx;
    VertxOptions options;
    DeploymentOptions deploymentOptions;

    public Vertx getVertx() {
      return vertx;
    }

    public VertxOptions getVertxOptions() {
      return options;
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
      beforeStartingVertxInvoked = true;
      this.options = options;
    }

    @Override
    public void afterStartingVertx(Vertx vertx) {
      afterStartingVertxInvoked = true;
      this.vertx = vertx;
    }

    @Override
    public void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {
      beforeDeployingVerticle = true;
      this.deploymentOptions = deploymentOptions;
    }

    public void assertHooksInvoked() {
      assertTrue(beforeStartingVertxInvoked);
      assertTrue(afterStartingVertxInvoked);
      assertTrue(beforeDeployingVerticle);
    }

    public String getVersion() {
      VersionCommand vc = (VersionCommand) getCommand("version");
      return vc.getVersion();
    }
  }

  class MySecondLauncher extends MyLauncher {

    @Override
    public String getMainVerticle() {
      return "java:io.vertx.test.core.TestVerticle";
    }
  }
}
