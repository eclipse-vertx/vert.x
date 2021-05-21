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

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.*;

/**
 * Test the bare command.
 */
public class BareCommandTest extends CommandTestBase {

  @After
  public void tearDown() throws InterruptedException {
    super.tearDown();
    close(getVertx());

    FakeClusterManager.reset();

  }

  protected void assertWaitUntil(BooleanSupplier supplier) {
    // Extend to 20 seconds for CI
    assertWaitUntil(supplier, 20000);
  }

  public void assertThatVertxInstanceHasBeenCreated() {
    assertThat(getVertx()).isNotNull();
  }

  private Vertx getVertx() {
    return ((BareCommand) cli.getExistingCommandInstance("bare")).vertx;
  }

  @Test
  public void testRegularBareCommand() throws InterruptedException, IOException {
    record();

    cli.dispatch(new String[]{"bare"});

    assertWaitUntil(() -> error.toString().contains("A quorum has been obtained."));
    assertThatVertxInstanceHasBeenCreated();
    stop();

    assertThat(error.toString())
      .contains("Starting clustering...")
      .contains("Any deploymentIDs waiting on a quorum will now be deployed");
  }

  @Test
  public void testOldBare() throws InterruptedException, IOException {
    record();
    cli.dispatch(new String[]{"-ha"});


    assertWaitUntil(() -> error.toString().contains("A quorum has been obtained."));
    stop();

    assertThat(error.toString())
      .contains("Starting clustering...")
      .contains("Any deploymentIDs waiting on a quorum will now be deployed");
  }

  @Test
  public void testRegularBareCommandWithClusterHost() {
    record();

    cli.dispatch(new String[]{"bare", "-cluster-host", "127.0.0.1"});

    assertWaitUntil(() -> error.toString().contains("A quorum has been obtained."));
    assertThatVertxInstanceHasBeenCreated();
    stop();
    assertThat(error.toString())
      .contains("Starting clustering...")
      .doesNotContain("No cluster-host specified")
      .contains("Any deploymentIDs waiting on a quorum will now be deployed");
  }

  @Test
  public void testOldBareWithClusterHost() throws InterruptedException, IOException {
    record();
    cli.dispatch(new String[]{"-ha", "-cluster-host", "127.0.0.1"});


    assertWaitUntil(() -> error.toString().contains("A quorum has been obtained."));
    stop();

    assertThat(error.toString())
      .contains("Starting clustering...")
      .doesNotContain("No cluster-host specified")
      .contains("Any deploymentIDs waiting on a quorum will now be deployed");
  }

  @Test
  public void testOverrideOption() {
    record();
    System.setProperty("vertx.options.haGroup", "__VERTX__");
    System.setProperty("vertx.eventBus.options.host", "localhost");
    JsonObject json = new JsonObject().put("haGroup", "__XYZ__")
      .put("eventBusOptions", new JsonObject().put("port", 3333).put("clusterPublicHost", "172.0.0.1"));
    cli.dispatch(new String[]{"bare", "-cluster-port", "1234", "-cluster-host", "127.0.0.1", "--options", json.encode()});

    assertWaitUntil(() -> error.toString().contains("A quorum has been obtained."));
    BareCommand bare = (BareCommand) cli.getExistingCommandInstance("bare");
    assertThat(bare.options.getEventBusOptions().getPort()).isEqualTo(1234);
    assertThat(bare.options.getEventBusOptions().getHost()).isEqualTo("127.0.0.1");
    assertThat(bare.options.getEventBusOptions().getClusterPublicPort()).isEqualTo(1234);
    assertThat(bare.options.getEventBusOptions().getClusterPublicHost()).isEqualTo("172.0.0.1");
    assertThat(bare.options.getHAGroup()).isEqualTo("__VERTX__");
    assertThat(bare.haGroup).isEqualTo("__VERTX__");
    assertThat(bare.quorum).isEqualTo(1);
    assertThat(bare.clusterHost).isEqualTo("127.0.0.1");
    assertThat(bare.clusterPort).isEqualTo(1234);
    assertThat(bare.clusterPublicHost).isEqualTo("172.0.0.1");
    assertThat(bare.clusterPublicPort).isEqualTo(1234);
    assertThat(bare.quorum).isEqualTo(1);
    assertThatVertxInstanceHasBeenCreated();

    stop();
    assertThat(error.toString())
      .contains("Starting clustering...")
      .doesNotContain("No cluster-host specified")
      .contains("Any deploymentIDs waiting on a quorum will now be deployed");
    System.clearProperty("vertx.options.haGroup");
    System.clearProperty("vertx.eventBus.options.host");
  }
}
