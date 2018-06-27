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

package io.vertx.test.core;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Starter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.json.JsonObject;
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
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@SuppressWarnings("deprecation")
public class StarterTest extends VertxTestBase {

  Vertx vertx;

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

    if (vertx != null) {
      vertx.close();
    }
  }

  @Test
  public void testVersion() throws Exception {
    String[] args = {"-version"};
    MyStarter starter = new MyStarter();
    starter.run(args);
    assertEquals(System.getProperty("vertx.version"), starter.getVersion());
    cleanup(starter);
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
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == instances);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    starter.assertHooksInvoked();
    cleanup(starter);
  }

  @Test
  public void testRunVerticleClustered() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName(), "-cluster"};
    starter.run(args);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    starter.assertHooksInvoked();
    cleanup(starter);
  }

  @Test
  public void testRunVerticleHA() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName(), "-ha"};
    starter.run(args);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    starter.assertHooksInvoked();
    cleanup(starter);
  }


  @Test
  public void testRunVerticleWithMainVerticleInManifestNoArgs() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {};
    starter.run(args);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    cleanup(starter);
  }

  private void cleanup(MyStarter starter) {
    if (starter != null  && starter.getVertx() != null) {
      starter.getVertx().close();
    }
  }

  @Test
  public void testRunVerticleWithMainVerticleInManifestWithHA() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {"-ha"};
    starter.run(args);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    cleanup(starter);
  }

  @Test
  public void testRunVerticleWithMainVerticleInManifestWithArgs() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = {"-cluster", "-worker"};
    starter.run(args);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    cleanup(starter);
  }

  @Test
  public void testRunVerticleWithConfString() throws Exception {
    MyStarter starter = new MyStarter();
    JsonObject conf = new JsonObject().put("foo", "bar").put("wibble", 123);
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName(), "-conf", conf.encode()};
    starter.run(args);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(conf, TestVerticle.conf);
    cleanup(starter);
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
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertEquals(conf, TestVerticle.conf);
    cleanup(starter);
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
    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "maxEventLoopExecuteTimeUnit", "SECONDS");

    MyStarter starter = new MyStarter();
    String[] args;
    if (clustered) {
      args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName(), "-cluster"};
    } else {
      args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName()};
    }
    starter.run(args);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = starter.getVertxOptions();

    assertEquals(123, opts.getEventLoopPoolSize(), 0);
    assertEquals(123767667L, opts.getMaxEventLoopExecuteTime());
    assertEquals(true, opts.getMetricsOptions().isEnabled());
    assertEquals("somegroup", opts.getHAGroup());
    assertEquals(TimeUnit.SECONDS, opts.getMaxEventLoopExecuteTimeUnit());

    cleanup(starter);
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
    System.setProperty(Starter.METRICS_OPTIONS_PROP_PREFIX + "enabled", "true");
    System.setProperty(Starter.METRICS_OPTIONS_PROP_PREFIX + "customProperty", "customPropertyValue");
    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName()};
    ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(MetricsOptionsTest.createMetricsFromMetaInfLoader("io.vertx.test.core.CustomMetricsFactory"));
    try {
      starter.run(args);
    } finally {
      Thread.currentThread().setContextClassLoader(oldCL);
    }
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);
    VertxOptions opts = starter.getVertxOptions();
    CustomMetricsOptions custom = (CustomMetricsOptions) opts.getMetricsOptions();
    assertEquals("customPropertyValue", custom.getCustomProperty());

    cleanup(starter);
  }

  @Test
  public void testConfigureFromSystemPropertiesInvalidPropertyName() throws Exception {

    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "nosuchproperty", "123");

    // Should be ignored

    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName()};
    starter.run(args);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = starter.getVertxOptions();
    VertxOptions def = new VertxOptions();
    if (opts.getMetricsOptions().isEnabled()) {
      def.getMetricsOptions().setEnabled(true);
    }
    assertEquals(def, opts);
    cleanup(starter);
  }

  @Test
  public void testConfigureFromSystemPropertiesInvalidPropertyType() throws Exception {
    // One for each type that we support
    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "eventLoopPoolSize", "sausages");
    // Should be ignored

    MyStarter starter = new MyStarter();
    String[] args = {"run", "java:" + TestVerticle.class.getCanonicalName()};
    starter.run(args);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = starter.getVertxOptions();
    VertxOptions def = new VertxOptions();
    if (opts.getMetricsOptions().isEnabled()) {
      def.getMetricsOptions().setEnabled(true);
    }
    assertEquals(def, opts);
    cleanup(starter);
  }

  @Test
  public void testRunWithCommandLine() throws Exception {
    MyStarter starter = new MyStarter();
    int instances = 10;
    String cl = "run java:" + TestVerticle.class.getCanonicalName() + " -instances " + instances;
    starter.run(cl);
    assertWaitUntil(() -> TestVerticle.instanceCount.get() == instances);
    cleanup(starter);
  }

  class MyStarter extends Starter {
    boolean beforeStartingVertxInvoked = false;
    boolean afterStartingVertxInvoked = false;
    boolean beforeDeployingVerticle = false;

    public Vertx getVertx() {
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
      StarterTest.this.vertx = vertx;
      assertTrue(beforeStartingVertxInvoked);
      assertTrue(afterStartingVertxInvoked);
      assertTrue(beforeDeployingVerticle);
    }
  }
}
