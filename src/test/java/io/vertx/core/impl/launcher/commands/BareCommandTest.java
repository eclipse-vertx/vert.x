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

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.Vertx;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

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
        .contains("No cluster-host specified")
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
        .contains("No cluster-host specified")
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


}
