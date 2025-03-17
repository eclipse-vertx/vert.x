package io.vertx.tests.jdbc;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.http.HttpTestBase;
import org.junit.ClassRule;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.*;

public class JdbcTest extends VertxTestBase {

  @ClassRule
  public static final ContainerPgRule rule = new ContainerPgRule();

  @Test
  public void testDatabaseInteraction() {
    Callable<Connection> fact = rule.connectionFactory();
    ConcurrentMap<HttpConnection, Connection> pool = new ConcurrentHashMap<>();
    DeploymentOptions opts = new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD_MOUNTED_ON_EVENT_LOOP);
    vertx.deployVerticle(ctx -> vertx.createHttpServer()
        .requestHandler(req -> {
          Connection conn = pool.computeIfAbsent(req.connection(), ignore -> {
            try {
              return fact.call();
            } catch(Exception e) {
              return null;
            }
          });
          try {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT pg_sleep(1)");
            while (rs.next()) {
              //
            }
            req.response().end();
          } catch (SQLException e) {
            fail(e);
          }
        }).listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST), opts)
      .await();

    int numConnections = 20;
    int numRequests = 10;
    waitFor(numConnections * numRequests);
    HttpClientAgent client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(numConnections));
    RequestOptions requestOpts = new RequestOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST);

    // Tests should take at most 10 seconds since request are concurrent
    for (int i = 0;i < numRequests * numConnections;i++) {
      client.request(requestOpts)
        .compose(req -> req.send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(resp -> resp.body()))
        .onComplete(onSuccess(body -> {
          complete();
        }));
    }

    await();
  }
}
