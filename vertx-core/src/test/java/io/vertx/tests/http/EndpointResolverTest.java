package io.vertx.tests.http;

import io.vertx.core.http.impl.Origin;
import io.vertx.core.http.impl.OriginEndpoint;
import io.vertx.core.http.impl.OriginEndpointResolver;
import io.vertx.core.http.impl.OriginServer;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.endpoint.Endpoint;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.endpoint.ServerEndpoint;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.List;

public class EndpointResolverTest extends VertxTestBase {


  @Test
  public void testSome() {
    EndpointResolver resolver = new EndpointResolverImpl<>((VertxInternal) vertx, new OriginEndpointResolver<>((VertxInternal) vertx), LoadBalancer.ROUND_ROBIN, 10_000);

    Endpoint endpoint = resolver.resolveEndpoint(new Origin("http", "localhost", 8080)).await();

    List<ServerEndpoint> servers = endpoint.servers();

    System.out.println("servers = " + servers);

  }

}
