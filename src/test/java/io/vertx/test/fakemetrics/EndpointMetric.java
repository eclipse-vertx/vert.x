package io.vertx.test.fakemetrics;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EndpointMetric {

  public final AtomicInteger queueSize = new AtomicInteger();
  public final AtomicInteger connectionCount = new AtomicInteger();
  public final AtomicInteger requests = new AtomicInteger();

}
