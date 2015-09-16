/*
 *  Copyright (c) 2011-2013 The original author or authors
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

import io.vertx.core.Vertx;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;

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

    waitUntil(() -> error.toString().contains("A quorum has been obtained."));
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


    waitUntil(() -> error.toString().contains("A quorum has been obtained."));
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

    waitUntil(() -> error.toString().contains("A quorum has been obtained."));
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


    waitUntil(() -> error.toString().contains("A quorum has been obtained."));
    stop();

    assertThat(error.toString())
        .contains("Starting clustering...")
        .doesNotContain("No cluster-host specified")
        .contains("Any deploymentIDs waiting on a quorum will now be deployed");
  }


}