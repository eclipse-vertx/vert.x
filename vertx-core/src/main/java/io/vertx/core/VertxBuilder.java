package io.vertx.core;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.transport.Transport;

/**
 * A builder for creating Vert.x instances, allowing to configure Vert.x plugins:
 *
 * <ul>
 *   <li>metrics</li>
 *   <li>tracing</li>
 *   <li>cluster manager</li>
 * </ul>
 *
 * Example usage:
 *
 * <pre><code>
 *   Vertx vertx = Vertx.builder().with(options).withMetrics(metricsFactory).build();
 * </code></pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface VertxBuilder {

  /**
   * Configure the Vert.x options.
   * @param options the Vert.x options
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  VertxBuilder with(VertxOptions options);

  /**
   * Programmatically set the metrics factory to be used when metrics are enabled.
   * <p>
   * Only valid if {@link MetricsOptions#isEnabled} = true.
   * <p>
   * Normally Vert.x will look on the classpath for a metrics factory implementation, but if you want to set one
   * programmatically you can use this method.
   *
   * @param factory the metrics factory
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  VertxBuilder withMetrics(VertxMetricsFactory factory);

  /**
   * Programmatically set the tracer factory to be used when tracing are enabled.
   * <p>
   * Normally Vert.x will look on the classpath for a tracer factory implementation, but if you want to set one
   * programmatically you can use this method.
   *
   * @param factory the tracer factory
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  VertxBuilder withTracer(VertxTracerFactory factory);

  /**
   * Programmatically set the transport, this overrides {@link VertxOptions#setPreferNativeTransport(boolean)}
   *
   * @param transport the transport
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  VertxBuilder withTransport(Transport transport);

  /**
   * Programmatically set the cluster manager to be used when clustering.
   * <p>
   * Only valid if clustered = true.
   * <p>
   * Normally Vert.x will look on the classpath for a cluster manager, but if you want to set one
   * programmatically you can use this method.
   *
   * @param clusterManager the cluster manager
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  VertxBuilder withClusterManager(ClusterManager clusterManager);

  /**
   * Creates a non clustered instance.
   *
   * @return the instance
   */
  Vertx build();

  /**
   * Creates a clustered instance.
   * <p>
   * The instance is created asynchronously and the returned future is completed with the result when it is ready.
   *
   * @return a future completed with the clustered vertx
   */
  Future<Vertx> buildClustered();

}
