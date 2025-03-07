/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.eventbus;

import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Thomas Segismont
 */
public abstract class FaultToleranceTest extends VertxTestBase {

  protected static final int NODE_COUNT = 3;
  protected static final int ADDRESSES_COUNT = 10;

  protected final List<Process> externalNodes = new ArrayList<>();
  protected final AtomicLong externalNodesStarted = new AtomicLong();
  protected final AtomicLong pongsReceived = new AtomicLong();
  protected final AtomicLong noHandlersErrors = new AtomicLong();
  protected long timeoutMs = 60_000;
  protected VertxInternal vertx;

  @Test
  public void testFaultTolerance() throws Exception {
    startNodes(1);
    vertx = (VertxInternal) vertices[0];

    vertx.eventBus().<String>consumer("control", msg -> {
      switch (msg.body()) {
        case "start":
          externalNodesStarted.incrementAndGet();
          break;
        case "pong":
          pongsReceived.incrementAndGet();
          break;
        case "noHandlers":
          noHandlersErrors.incrementAndGet();
          break;
      }
    });

    for (int i = 0; i < NODE_COUNT; i++) {
      Process process = startExternalNode(i);
      externalNodes.add(process);
      afterNodeStarted(i, process);
    }
    afterNodesStarted();

    JsonArray message1 = new JsonArray();
    IntStream.range(0, NODE_COUNT).forEach(message1::add);
    vertx.eventBus().publish("ping", message1);
    assertEqualsEventually("All pongs", Long.valueOf(NODE_COUNT * NODE_COUNT * ADDRESSES_COUNT), pongsReceived::get);

    for (int i = 0; i < NODE_COUNT - 1; i++) {
      Process process = externalNodes.get(i);
      process.destroyForcibly();
      afterNodeKilled(i, process);
    }
    afterNodesKilled();

    pongsReceived.set(0);
    JsonArray message2 = new JsonArray().add(NODE_COUNT - 1);
    vertx.eventBus().publish("ping", message2);
    assertEqualsEventually("Survivor pongs", Long.valueOf(ADDRESSES_COUNT), pongsReceived::get);

    JsonArray message3 = new JsonArray();
    IntStream.range(0, NODE_COUNT - 1).forEach(message3::add);
    vertx.eventBus().publish("ping", message3);
    assertEqualsEventually("Dead errors", Long.valueOf((NODE_COUNT - 1) * ADDRESSES_COUNT), noHandlersErrors::get);
  }

  protected void afterNodeStarted(int i, Process process) throws Exception {
  }

  protected void afterNodesStarted() throws Exception {
    assertEqualsEventually("Nodes ready", Long.valueOf(NODE_COUNT), externalNodesStarted::get);
  }

  protected void afterNodeKilled(int i, Process process) throws Exception {
  }

  protected void afterNodesKilled() throws Exception {
    ClusterManager clusterManager = vertx.clusterManager();
    assertEqualsEventually("Remaining members", Integer.valueOf(2), () -> clusterManager.getNodes().size());
  }

  protected Process startExternalNode(int id) throws Exception {
    String javaHome = System.getProperty("java.home");
    String classpath = System.getProperty("java.class.path");
    List<String> command = new ArrayList<>();
    command.add(javaHome + File.separator + "bin" + File.separator + "java");
    command.add("-classpath");
    command.add(classpath);
    command.addAll(getExternalNodeSystemProperties());
    command.add(FaultToleranceVerticle.class.getName());
    command.add(new JsonObject().put("id", id).put("addressesCount", ADDRESSES_COUNT).encode());
    return new ProcessBuilder(command).inheritIO().start();
  }

  protected List<String> getExternalNodeSystemProperties() {
    return Collections.emptyList();
  }

  protected void assertEqualsEventually(String msg, Object expected, Supplier<Object> actual) {
    for (long start = System.currentTimeMillis(); System.currentTimeMillis() - start < timeoutMs; ) {
      Object act = actual.get();
      if (Objects.equals(expected, act)) {
        return;
      }
      try {
        MILLISECONDS.sleep(100);
      } catch (InterruptedException ignored) {
      }
    }
    assertEquals(msg, expected, actual.get());
  }

  @Override
  protected void tearDown() throws Exception {
    externalNodes.forEach(Process::destroyForcibly);
    super.tearDown();
  }
}
