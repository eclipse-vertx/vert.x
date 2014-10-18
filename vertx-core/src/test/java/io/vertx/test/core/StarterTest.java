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

import io.vertx.core.Starter;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

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
    Starter starter = new Starter();
    starter.run(args);
    // TODO some way of getting this from the version in pom.xml
    assertEquals("3.0.0-SNAPSHOT", starter.getVersion());
  }

  @Test
  public void testRunVerticle() throws Exception {
    Starter starter = new Starter();
    String[] args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName()};
    Thread t = new Thread(() -> {
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertTrue(t.isAlive()); // It's blocked
    List<String> processArgs = TestVerticle.processArgs;
    assertEquals(Arrays.asList(args), TestVerticle.processArgs);
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
  }

  @Test
  public void testRunVerticleClustered() throws Exception {
    Starter starter = new Starter();
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
  public void testRunVerticleWithMainVerticleInManifestNoArgs() throws Exception {
    Starter starter = new Starter();
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
    Starter starter = new Starter();
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
    Starter starter = new Starter();
    JsonObject conf = new JsonObject().putString("foo", "bar").putNumber("wibble", 123);
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

  @Test
  public void testRunVerticleWithConfFile() throws Exception {
    Path tempDir = Files.createTempDirectory("conf");
    Path tempFile = Files.createTempFile(tempDir, "conf", "json");
    try {
      Starter starter = new Starter();
      JsonObject conf = new JsonObject().putString("foo", "bar").putNumber("wibble", 123);
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
    } finally {
      Files.delete(tempFile);
      Files.delete(tempDir);
    }
  }

  @Test
  public void testConfigureFromSystemProperties() throws Exception {
    VertxOptions opts = new VertxOptions();

    // One for each type that we support
    System.setProperty("vertx.options.eventLoopPoolSize", "123");
    System.setProperty("vertx.options.maxEventLoopExecuteTime", "123767667");
    System.setProperty("vertx.options.clustered", "true");
    System.setProperty("vertx.options.clusterHost", "foohost");

    Starter.configureFromSystemProperties(opts, "vertx.options.");
    assertEquals(123, opts.getEventLoopPoolSize(), 0);
    assertEquals(123767667l, opts.getMaxEventLoopExecuteTime());
    assertEquals(true, opts.isClustered());
    assertEquals("foohost", opts.getClusterHost());
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

    VertxOptions opts = new VertxOptions();
    System.setProperty("vertx.options.nosuchproperty", "123");
    // Should be ignored
    Starter.configureFromSystemProperties(opts, "vertx.options.");
    assertEquals(new VertxOptions(), opts);
  }

  @Test
  public void testConfigureFromSystemPropertiesInvalidPropertyType() throws Exception {
    VertxOptions opts = new VertxOptions();
    // One for each type that we support
    System.setProperty("vertx.options.eventLoopPoolSize", "sausages");
    // Should be ignored
    Starter.configureFromSystemProperties(opts, "vertx.options.");
    assertEquals(new VertxOptions(), opts);
  }
}
