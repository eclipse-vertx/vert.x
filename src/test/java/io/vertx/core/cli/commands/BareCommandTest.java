package io.vertx.core.cli.commands;

import io.vertx.core.Vertx;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

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
    return ((BareCommand) cli.getCommand("bare")).vertx;
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
  public void testBareAlias() throws InterruptedException, IOException {
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


}