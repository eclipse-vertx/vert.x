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
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StarterTest extends VertxTestBase {

  public void setUp() throws Exception {
    super.setUp();
    TestVerticle.instanceCount.set(0);
    TestVerticle.processArgs = null;
    TestVerticle.conf = null;
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
}
