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

import io.vertx.core.DeploymentOptions;
import io.vertx.core.spi.launcher.ExecutionContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;

/**
 * Check the behavior of the {@link ClasspathHandler} class.
 */
public class ClasspathHandlerTest extends CommandTestBase {

  public static final String VERTICLE = "io.vertx.core.externals.MyVerticle";
  RunCommand run;
  private BareCommand bare;

  @Before
  public void setUp() throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
    File output = new File("target/externals");
    output.mkdirs();
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(
        output));

    Iterable<? extends JavaFileObject> compilationUnits1 =
        fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(
            new File("src/test/externals/MyVerticle.java")));

    compiler.getTask(null, fileManager, null, null, null, compilationUnits1).call();
  }

  @After
  public void tearDown() {
    if (run != null) {
      run.vertx.close();
    }
    if (bare != null) {
      bare.vertx.close();
    }
  }

  @Test
  public void testCPInRunCommand() {
    run = new RunCommand();
    run.setExecutionContext(new ExecutionContext(run, null, null));
    run.setClasspath("." + File.pathSeparator + "target/externals");
    run.setMainVerticle(VERTICLE);
    run.setInstances(1);
    run.run();
    waitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
  }

  @Test
  public void testCPInBareCommand() {
    bare = new BareCommand();
    bare.setExecutionContext(new ExecutionContext(bare, null, null));
    bare.setClasspath("." + File.pathSeparator + "target/externals");
    bare.setQuorum(1);
    bare.run();

    waitUntil(() ->  bare.vertx != null);

    // Do reproduce the verticle fail-over, set the TCCL
    final ClassLoader originalClassloader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(bare.createClassloader());
      bare.vertx.deployVerticle(VERTICLE, new DeploymentOptions().setHa(true));
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassloader);
    }

    waitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        return false;
      }
    });
  }

  private int getHttpCode() throws IOException {
    return ((HttpURLConnection) new URL("http://localhost:8080")
        .openConnection()).getResponseCode();
  }

}