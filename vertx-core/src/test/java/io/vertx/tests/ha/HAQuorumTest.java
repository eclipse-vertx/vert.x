package io.vertx.tests.ha;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class HAQuorumTest extends VertxTestBase {
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager() {
      @Override
      public void nodeListener(NodeListener listener) {
        //do nothing
      }
    };
  }

  @Test
  public void quorumIsObtainedOnNodeInfoPutThatDoneLaterThanClusterWasCreated() throws Exception {
    //given
    final Vertx vertx1 = startVertx(2);
    final DeploymentOptions options = new DeploymentOptions().setHa(true);
    final JsonObject config = new JsonObject().put("foo", "bar");
    options.setConfig(config);

    final Map<String, String> haInfoMap = getClusterManager().getSyncMap("__vertx.haInfo");
    assertEquals(1, haInfoMap.size());
    final Map.Entry<String, String> vertx1HaInfo = haInfoMap.entrySet().iterator().next();
    haInfoMap.remove(vertx1HaInfo.getKey());

    final Vertx vertx2 = startVertx(2);

    vertx2.deployVerticle("java:" + HAVerticle2.class.getName(), options).onComplete(onSuccess(id -> {
      assertTrue(vertx2.deploymentIDs().contains(id));
      testComplete();
    }));

    assertWaitUntil(() -> vertx2.deploymentIDs().isEmpty());

    //when
    haInfoMap.put(vertx1HaInfo.getKey(), vertx1HaInfo.getValue());

    // then
    await();

    closeVertices(vertx1, vertx2);
  }

  private Vertx startVertx(int quorumSize) throws Exception {
    final VertxOptions options = new VertxOptions().setHAEnabled(true);
    options.getEventBusOptions().setHost("localhost");
    options.setQuorumSize(quorumSize);
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Vertx> vertxRef = new AtomicReference<>();
    Vertx.builder()
      .with(options)
      .withClusterManager(getClusterManager())
      .buildClustered()
      .onComplete(onSuccess(vertx -> {
        vertxRef.set(vertx);
        latch.countDown();
      }));
    latch.await(2, TimeUnit.MINUTES);
    return vertxRef.get();
  }

  private void closeVertices(Vertx... vertices) throws Exception {
    CountDownLatch latch = new CountDownLatch(vertices.length);
    for (Vertx vertex : vertices) {
      if (vertex != null) {
        vertex.close().onComplete(onSuccess(res -> {
          latch.countDown();
        }));
      } else {
        latch.countDown();
      }
    }
    awaitLatch(latch, 2, TimeUnit.MINUTES);
  }
}
