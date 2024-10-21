package io.vertx.tests.endpoint;

import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.endpoint.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class LoadBalancingCornerCasesTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {LoadBalancer.ROUND_ROBIN},
      {LoadBalancer.LEAST_REQUESTS},
      {LoadBalancer.RANDOM},
      {LoadBalancer.POWER_OF_TWO_CHOICES},
    });
  }
  private final LoadBalancer loadBalancer;

  public LoadBalancingCornerCasesTest(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  @Test
  public void testCornerCases() {
    List<ServerEndpoint> instances = new ArrayList<>();
    ServerSelector selector = loadBalancer.selector(instances);
    // Randomness is involved in some policies.
    for (int i = 0; i < 1000; i++) {
      assertEquals(-1, selector.select());
    }
    ServerEndpoint instance = new ServerEndpoint() {
      InteractionMetrics<?> metrics = loadBalancer.newMetrics();
      @Override
      public SocketAddress address() {
        return null;
      }
      @Override
      public Object unwrap() {
        return null;
      }
      @Override
      public String key() {
        return "";
      }
      @Override
      public InteractionMetrics<?> metrics() {
        return metrics;
      }
      @Override
      public ServerInteraction newInteraction() {
        return null;
      }
    };
    instances.add(instance);
    for (int i = 0; i < 1000; i++) {
      assertEquals(0, selector.select());
    }
  }
}
