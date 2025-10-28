package io.vertx.tests.http.http3;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(LinuxOrOsx.class)
public class Http3ContextTest extends VertxTestBase {

  private HttpServer server;
  private Http3ClientOptions clientOptions;
  private HttpClientAgent client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Http3ServerOptions serverOptions = new Http3ServerOptions();
    serverOptions.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    clientOptions = new Http3ClientOptions();
    clientOptions.getSslOptions().setTrustOptions(Trust.SERVER_JKS.get());
    clientOptions.getSslOptions().setHostnameVerificationAlgorithm("");
    server = vertx.createHttpServer(serverOptions);
    client = vertx.createHttpClient(clientOptions);
  }

  @Override
  protected void tearDown() throws Exception {
    server.close().await();
    client.close().await();
    super.tearDown();
  }

  @Test
  public void testServerRequestEventLoopContext() {
    testServerRequestContext(ThreadingModel.EVENT_LOOP);
  }

  @Test
  public void testServerRequestWorkerContext() {
    testServerRequestContext(ThreadingModel.WORKER);
  }

  private void testServerRequestContext(ThreadingModel threadingModel) {

    server.requestHandler(req -> {
      Context ctx = Vertx.currentContext();
      assertEquals(threadingModel, ctx.threadingModel());
      assertIsDuplicate(req, ctx);
      Buffer body = Buffer.buffer();
      req.handler(chunk -> {
        assertSame(ctx, Vertx.currentContext());
        body.appendBuffer(chunk);
      });
      req.endHandler(v -> {
        assertSame(ctx, Vertx.currentContext());
        req.response().end(body);
      });
    });

    ContextInternal serverCtx = ((VertxInternal) vertx).createContext(threadingModel);
    Future.future(p -> serverCtx.runOnContext(vertx -> server.listen(8443, "localhost").onComplete(p))).await();

    Buffer response = client.request(HttpMethod.POST, 8443, "localhost", "/")
      .compose(request -> request
        .send(Buffer.buffer("payload"))
        .expecting(HttpResponseExpectation.SC_OK)
      )
      .compose(HttpClientResponse::body).await();

    assertEquals("payload", response.toString());
  }

  @Test
  public void testClientRequestEventLoopContext() {
    testClientRequestContext(ThreadingModel.EVENT_LOOP);
  }

  @Test
  public void testClientRequestWorkerContext() {
    testClientRequestContext(ThreadingModel.WORKER);
  }

  public void testClientRequestContext(ThreadingModel threadingModel) {

    server.requestHandler(req -> {
      req.response().end("Hello World");
    });

    server.listen(8443, "localhost").await();

    ContextInternal connectionCtx = ((VertxInternal) vertx).createContext(threadingModel);
    ContextInternal streamCtx = ((VertxInternal) vertx).createContext(threadingModel);

    HttpClientRequest request1 = Future.<HttpClientRequest>future(p -> connectionCtx.runOnContext(v -> client.request(HttpMethod.POST, 8443, "localhost", "/").onComplete(p))).await();

    Buffer body = request1.send().compose(response -> {
      assertSame(connectionCtx, Vertx.currentContext());
      return response.body();
    }).await();
    assertEquals("Hello World", body.toString());

    HttpClientRequest request2 = Future.<HttpClientRequest>future(p -> streamCtx.runOnContext(v -> client.request(HttpMethod.POST, 8443, "localhost", "/").onComplete(p))).await();
    body = request2.send().compose(response -> {
      assertSame(streamCtx, Vertx.currentContext());
      return response.body();
    }).await();
    assertEquals("Hello World", body.toString());

    assertSame(request1.connection(), request2.connection());
  }

  private void assertIsDuplicate(HttpServerRequest request, Context context) {
    assertIsDuplicate(request, (ContextInternal)context);
  }

  private void assertIsDuplicate(HttpServerRequest request, ContextInternal context) {
    assertTrue(context.isDuplicate());
    HttpServerConnection connection = (HttpServerConnection) request.connection();
    assertSame(connection.context(), context.unwrap());
  }
}
