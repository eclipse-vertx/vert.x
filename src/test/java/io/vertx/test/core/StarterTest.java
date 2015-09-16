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
import io.vertx.core.Starter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.metrics.VertxMetrics;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StarterTest extends VertxTestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestVerticle.instanceCount.set(0);
    TestVerticle.processArgs = null;
    TestVerticle.conf = null;

    VertxCommandLauncher.resetProcessArguments();

    File manifest = new File("target/test-classes/META-INF/MANIFEST-Starter.MF");
    if (!manifest.isFile()) {
      throw new IllegalStateException("Cannot find the MANIFEST-Starter.MF file");
    }
    File target = new File("target/test-classes/META-INF/MANIFEST.MF");
    Files.copy(manifest.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  public void tearDown() throws Exception {
    clearProperties();
    super.tearDown();
  }

  @Test
  public void testVersion() throws Exception {
    String[] args = {"-version"};
    MyStarter starter = new MyStarter();
    starter.run(args);
    // TODO some way of getting this from the version in pom.xml
    assertEquals(System.getProperty("vertxVersion"), starter.getVersion());
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
    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName(), "-instances", String.valueOf(instances)};
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == instances);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    starter.assertHooksInvoked();
  }

  @Test
  public void testRunVerticleClustered() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName(), "-cluster"};
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    starter.assertHooksInvoked();
  }

  @Test
  public void testRunVerticleHA() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName(), "-ha"};
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    starter.assertHooksInvoked();
  }


  @Test
  public void testRunVerticleWithMainVerticleInManifestNoArgs() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {};
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
  }

  @Test
  public void testRunVerticleWithMainVerticleInManifestWithHA() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {"-ha"};
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
  }

  @Test
  public void testRunVerticleWithMainVerticleInManifestWithArgs() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {"-cluster", "-worker"};
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
  }

  @Test
  public void testRunVerticleWithConfString() throws Exception {
    MyStarter starter = new MyStarter();
    JsonObject conf = new JsonObject().put("foo", "bar").put("wibble", 123);
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName(), "-conf", conf.encode()};
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(conf, TestVerticle.conf);
  }

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();


  @Test
  public void testRunVerticleWithConfFile() throws Exception {
    Path tempDir = testFolder.newFolder().toPath();
    Path tempFile = Files.createTempFile(tempDir, "conf", "json");
    MyStarter starter = new MyStarter();
    JsonObject conf = new JsonObject().put("foo", "bar").put("wibble", 123);
    Files.write(tempFile, conf.encode().getBytes());
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName(), "-conf", tempFile.toString()};
    starter.run(args);
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
    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "eventLoopPoolSize", "123");
    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "maxEventLoopExecuteTime", "123767667");
    System.setProperty(Starter.METRICS_OPTIONS_PROP_PREFIX + "enabled", "true");
    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "haGroup", "somegroup");

    MyStarter starter = new MyStarter();
    String[] args;
    if (clustered) {
      args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName(), "-cluster"};
    } else {
      args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName()};
    }
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = starter.getVertxOptions();

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
    for (String propName : toClear) {
      System.clearProperty(propName);
    }
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
      System.setProperty(Starter.METRICS_OPTIONS_PROP_PREFIX + "enabled", "true");
      System.setProperty(Starter.METRICS_OPTIONS_PROP_PREFIX + "customProperty", "customPropertyValue");
      MyStarter starter = new MyStarter();
      String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName()};
      starter.run(args);
      waitUntil(() -> TestVerticle.instanceCount.get() == 1);
      VertxOptions opts = starter.getVertxOptions();
      CustomMetricsOptions custom = (CustomMetricsOptions) opts.getMetricsOptions();
      assertEquals("customPropertyValue", custom.getCustomProperty());
    } finally {
      ConfigurableMetricsFactory.delegate = null;
    }
  }

  @Test
  public void testConfigureFromSystemPropertiesInvalidPropertyName() throws Exception {

    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "nosuchproperty", "123");

    // Should be ignored

    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName()};
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = starter.getVertxOptions();
    VertxOptions def = new VertxOptions();
    if (opts.getMetricsOptions().isEnabled()) {
      def.getMetricsOptions().setEnabled(true);
    }
    assertEquals(def, opts);
  }

  @Test
  public void testConfigureFromSystemPropertiesInvalidPropertyType() throws Exception {
    // One for each type that we support
    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "eventLoopPoolSize", "sausages");
    // Should be ignored

    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName()};
    starter.run(args);
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = starter.getVertxOptions();
    VertxOptions def = new VertxOptions();
    if (opts.getMetricsOptions().isEnabled()) {
      def.getMetricsOptions().setEnabled(true);
    }
    assertEquals(def, opts);
  }

  @Test
  public void testRunWithCommandLine() throws Exception {
    MyStarter starter = new MyStarter();
    int instances = 10;
    String cl = "run java:" + TestVerticle.class.getCanonicalName() + " -instances " + instances;
    starter.run(cl);
    waitUntil(() -> TestVerticle.instanceCount.get() == instances);
  }

  class MyStarter extends Starter {
    boolean beforeStartingVertxInvoked = false;
    boolean afterStartingVertxInvoked = false;
    boolean beforeDeployingVerticle = false;

    public Vertx getVert() {
      return vertx;
    }

    public VertxOptions getVertxOptions() {
      return options;
    }

    public DeploymentOptions getDeploymentOptions() {
      return deploymentOptions;
    }

    @Override
    public void run(String[] sargs) {
      super.run(sargs);
    }

    @Override
    public void run(String commandLine) {
      super.run(commandLine);
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
      beforeStartingVertxInvoked = true;
    }

    @Override
    public void afterStartingVertx() {
      afterStartingVertxInvoked = true;
    }

    @Override
    protected void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {
      beforeDeployingVerticle = true;
    }

    public void assertHooksInvoked() {
      assertTrue(beforeStartingVertxInvoked);
      assertTrue(afterStartingVertxInvoked);
      assertTrue(beforeDeployingVerticle);
    }
  }
}
