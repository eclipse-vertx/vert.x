package io.vertx.core.loadbalancing;

import io.vertx.core.spi.loadbalancing.Endpoint;
import io.vertx.core.spi.loadbalancing.EndpointSelector;
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
    EndpointSelector selector = loadBalancer.selector();
    List<Endpoint<?>>  metrics = new ArrayList<>();
    // Randomness is involved in some policies.
    for (int i = 0; i < 1000; i++) {
      assertEquals(-1, selector.selectEndpoint(metrics));
    }
    metrics.add(selector.endpointOf(null));
    for (int i = 0; i < 1000; i++) {
      assertEquals(0, selector.selectEndpoint(metrics));
    }
  }
}
