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
import io.vertx.core.json.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
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
  }

  @Override
  public void tearDown() throws Exception {
    clearProperties();
    super.tearDown();
  }

  @Test
  public void testVersion() throws Exception {
    String[] args = new String[] {"-version"};
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
    String[] args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName(), "-instances", String.valueOf(instances)};
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == instances);
    assertTrue(t.isAlive()); // It's blocked
    List<String> processArgs = TestVerticle.processArgs;
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
  }

  @Test
  public void testRunVerticleClustered() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName(), "-cluster"};
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertTrue(t.isAlive()); // It's blocked
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
  }

  @Test
  public void testRunVerticleHA() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName(), "-ha"};
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertTrue(t.isAlive()); // It's blocked
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
  }


  @Test
  public void testRunVerticleWithMainVerticleInManifestNoArgs() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = new String[0];
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertTrue(t.isAlive()); // It's blocked
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
  }

  @Test
  public void testRunVerticleWithMainVerticleInManifestWithArgs() throws Exception {
    MyStarter starter = new MyStarter();
    String[] args = new String[] {"-cluster", "-worker"};
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertTrue(t.isAlive()); // It's blocked
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
  }

  @Test
  public void testRunVerticleWithConfString() throws Exception {
    MyStarter starter = new MyStarter();
    JsonObject conf = new JsonObject().put("foo", "bar").put("wibble", 123);
    String[] args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName(), "-conf", conf.encode()};
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertTrue(t.isAlive()); // It's blocked
    assertEquals(conf, TestVerticle.conf);
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
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
    String[] args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName(), "-conf", tempFile.toString()};
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertTrue(t.isAlive()); // It's blocked
    assertEquals(conf, TestVerticle.conf);
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
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

    System.setProperty(Starter.DEPLOYMENT_OPTIONS_PROP_PREFIX + "redeployScanPeriod", "612536253");

    MyStarter starter = new MyStarter();
    String[] args;
    if (clustered) {
      args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName(), "-cluster"};
    } else {
      args = new String[]{"run", "java:" + TestVerticle.class.getCanonicalName()};
    }
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = starter.getVertxOptions();

    assertEquals(123, opts.getEventLoopPoolSize(), 0);
    assertEquals(123767667l, opts.getMaxEventLoopExecuteTime());
    assertEquals(true, opts.getMetricsOptions().isEnabled());
    assertEquals("somegroup", opts.getHAGroup());

    DeploymentOptions depOptions = starter.getDeploymentOptions();

    assertEquals(612536253, depOptions.getRedeployScanPeriod());
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
    for (String propName: toClear) {
      System.clearProperty(propName);
    }
  }

  @Test
  public void testConfigureFromSystemPropertiesInvalidPropertyName() throws Exception {

    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "nosuchproperty", "123");

    // Should be ignored

    MyStarter starter = new MyStarter();
    String[] args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName()};
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = starter.getVertxOptions();
    assertEquals(new VertxOptions(), opts);

  }

  @Test
  public void testConfigureFromSystemPropertiesInvalidPropertyType() throws Exception {
    // One for each type that we support
    System.setProperty(Starter.VERTX_OPTIONS_PROP_PREFIX + "eventLoopPoolSize", "sausages");
    // Should be ignored

    MyStarter starter = new MyStarter();
    String[] args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName()};
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);

    VertxOptions opts = starter.getVertxOptions();
    assertEquals(new VertxOptions(), opts);
  }

  @Test
  public void testRunWithCommandLine() throws Exception {
    MyStarter starter = new MyStarter();
    int instances = 10;
    String cl = "run java:" + TestVerticle.class.getCanonicalName() + " -instances " + instances;
    Thread t = new Thread(() -> {
      starter.run(cl);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == instances);
    assertTrue(t.isAlive()); // It's blocked
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
  }

  class MyStarter extends Starter {
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
  }
}
