package io.vertx.tests.http;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertFalse;

public class Http1xClientTest {

  @Test
  public void testHttp1xClientLocal() throws TimeoutException {
    Vertx vertx = Vertx.vertx();

    Promise<String> promise = Promise.promise();

    vertx.createHttpServer().requestHandler(req -> req.response().end("Hello World!")).listen(8080);

    vertx.createHttpClient().request(new RequestOptions().setMethod(HttpMethod.GET).setPort(8080).setURI("/"))
      .compose(HttpClientRequest::send)
      .compose(HttpClientResponse::body)
      .onSuccess(resp -> promise.complete(resp.toString()))
      .onFailure(promise::fail);

    String body = promise.future().await(10, TimeUnit.SECONDS);

    assertFalse("Response body should not be empty", body.isEmpty());
  }

  @Test
  public void testHttp1xClient() throws TimeoutException {
    Vertx vertx = Vertx.vertx();

    Promise<String> promise = Promise.promise();

    vertx.createHttpClient().request(new RequestOptions().setMethod(HttpMethod.GET).setAbsoluteURI("http://meowfacts.herokuapp.com/"))
      .compose(HttpClientRequest::send)
      .compose(HttpClientResponse::body)
      .onSuccess(resp -> promise.complete(resp.toString()))
      .onFailure(promise::fail);

    String body = promise.future().await(10, TimeUnit.SECONDS);

    assertFalse("Response body should not be empty", body.isEmpty());
  }
}
