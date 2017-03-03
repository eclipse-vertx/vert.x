/*
 * Copyright (c) 2011-2017 The original author or authors
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

import io.vertx.core.Launcher;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * @author Thomas Segismont
 */
public abstract class FaultToleranceTest extends VertxTestBase {

  private static final int NODE_COUNT = 3;
  private static final int ADDRESSES_COUNT = 10;

  private final List<Process> externalNodes = new ArrayList<>();
  private final AtomicLong externalNodesStarted = new AtomicLong();
  private final AtomicLong pongsReceived = new AtomicLong();
  private final AtomicLong noHandlersErrors = new AtomicLong();

  @Test
  public void testFaultTolerance() throws Exception {
    startNodes(1);
    VertxInternal vertx = (VertxInternal) vertices[0];

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
    }
    waitUntil(() -> externalNodesStarted.get() == NODE_COUNT, 60_000);

    JsonArray message1 = new JsonArray();
    IntStream.range(0, NODE_COUNT).forEach(message1::add);
    vertx.eventBus().publish("ping", message1);
    waitUntil(() -> pongsReceived.get() == NODE_COUNT * NODE_COUNT * ADDRESSES_COUNT, 60_000);

    for (int i = 0; i < NODE_COUNT - 1; i++) {
      externalNodes.get(i).destroyForcibly();
    }
    waitForClusterStability(vertx);

    pongsReceived.set(0);
    JsonArray message2 = new JsonArray().add(NODE_COUNT - 1);
    vertx.eventBus().publish("ping", message2);
    waitUntil(() -> pongsReceived.get() == ADDRESSES_COUNT, 60_000);

    JsonArray message3 = new JsonArray();
    IntStream.range(0, NODE_COUNT - 1).forEach(message3::add);
    vertx.eventBus().publish("ping", message3);
    waitUntil(() -> noHandlersErrors.get() == (NODE_COUNT - 1) * ADDRESSES_COUNT, 60_000);
  }

  protected void waitForClusterStability(VertxInternal vertx) throws Exception {
    ClusterManager clusterManager = vertx.getClusterManager();
    waitUntil(() -> clusterManager.getNodes().size() == 2, 60_000);
  }

  private Process startExternalNode(int id) throws Exception {
    String javaHome = System.getProperty("java.home");
    String classpath = System.getProperty("java.class.path");
    List<String> command = new ArrayList<>();
    command.add(javaHome + File.separator + "bin" + File.separator + "java");
    command.add("-classpath");
    command.add(classpath);
    command.addAll(getExternalNodeSystemProperties());
    command.add(Launcher.class.getName());
    command.add("run");
    command.add(FaultToleranceVerticle.class.getName());
    command.add("-cluster");
    command.add("-conf");
    command.add(new JsonObject().put("id", id).put("addressesCount", ADDRESSES_COUNT).encode());
    return new ProcessBuilder(command).inheritIO().start();
  }

  protected List<String> getExternalNodeSystemProperties() {
    return Collections.emptyList();
  }

  @Override
  protected void tearDown() throws Exception {
    externalNodes.forEach(Process::destroyForcibly);
    super.tearDown();
  }
}
