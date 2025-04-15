/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.http;

import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.VerticleBase;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.proxy.HAProxy;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Test extends HttpCommonTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createHttp2ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected NetClientOptions createNetClientOptions() {
    return HttpOptionsFactory.createH2NetClientOptions();
  }

  @Override
  protected NetServerOptions createNetServerOptions() {
    return HttpOptionsFactory.createH2NetServerOptions();
  }

  @Override
  protected HAProxy createHAProxy(SocketAddress remoteAddress, Buffer header) {
    return new HAProxy(remoteAddress, header);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createHttp2ClientOptions();
  }

  @Override
  protected HttpVersion clientAlpnProtocolVersion() {
    return HttpVersion.HTTP_1_1;
  }

  @Override
  protected HttpVersion serverAlpnProtocolVersion() {
    return HttpVersion.HTTP_2;
  }

  @Override
  protected void addMoreOptions(HttpServerOptions opts) {
  }

  @Override
  protected HttpServerOptions setMaxConcurrentStreamsSettings(HttpServerOptions options, int maxConcurrentStreams) {
    return options.setInitialSettings(new Http2Settings().setMaxConcurrentStreams(maxConcurrentStreams));
  }

  @Test
  public void testInitialMaxConcurrentStreamZero() throws Exception {
    waitFor(2);
    server.close();
    server =
      vertx.createHttpServer(createBaseServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(0)));
    server.requestHandler(req -> {
      req.response().end();
    });
    server.connectionHandler(conn -> {
      vertx.setTimer(500, id -> {
        conn.updateHttpSettings(new Http2Settings().setMaxConcurrentStreams(10));
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        assertEquals(0, ((Http2Settings) conn.remoteHttpSettings()).getMaxConcurrentStreams());
        conn.remoteHttpSettingsHandler(settings -> {
          assertEquals(10, ((Http2Settings) conn.remoteHttpSettings()).getMaxConcurrentStreams());
          complete();
        });
      })
      .build();
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> complete()));
    await();
  }

  @Test
  public void testMaxHaderListSize() throws Exception {
    server.close();
    server =
      vertx.createHttpServer(createBaseServerOptions().setInitialSettings(new Http2Settings().setMaxHeaderListSize(Integer.MAX_VALUE)));
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(Integer.MAX_VALUE,
          ((Http2Settings) resp.request().connection().remoteHttpSettings()).getMaxHeaderListSize());
        testComplete();
      }));
    await();
  }

  @Override
  protected StreamPriorityBase generateStreamPriority() {
    return new Http2StreamPriority()
      .setDependency(TestUtils.randomPositiveInt())
      .setWeight(TestUtils.randomShort())
      .setExclusive(TestUtils.randomBoolean());
  }

  @Override
  protected StreamPriorityBase defaultStreamPriority() {
    return new Http2StreamPriority()
      .setDependency(0)
      .setWeight(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT)
      .setExclusive(false);
  }

  @Test
  public void testDefaultStreamWeightAndDependency() throws Exception {
    int defaultStreamDependency = 0;
    short defaultStreamWeight = Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(defaultStreamWeight, req.streamPriority().getWeight());
      assertEquals(defaultStreamDependency, req.streamPriority().getDependency());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        assertEquals(defaultStreamWeight, req.getStreamPriority().getWeight());
        assertEquals(defaultStreamDependency, req.getStreamPriority().getDependency());
        complete();
      }));
    }));
    await();
  }

  @Test
  public void testStreamWeightAndDependencyPushPromise() throws Exception {
    int pushStreamDependency = 456;
    short pushStreamWeight = 14;
    waitFor(4);
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/pushpath").onComplete(onSuccess(pushedResp -> {
        pushedResp.setStreamPriority(new Http2StreamPriority()
          .setDependency(pushStreamDependency)
          .setWeight(pushStreamWeight)
          .setExclusive(false));
        pushedResp.end();
      }));
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .pushHandler(pushReq -> {
          complete();
          pushReq.response().onComplete(onSuccess(pushResp -> {
            assertEquals(pushStreamDependency, pushResp.request().getStreamPriority().getDependency());
            assertEquals(pushStreamWeight, pushResp.request().getStreamPriority().getWeight());
            complete();
          }));
        })
        .send().onComplete(onSuccess(resp -> {
          complete();
        }));
    }));
    await();
  }

  @Test
  public void testStreamWeightAndDependencyInheritancePushPromise() throws Exception {
    int reqStreamDependency = 556;
    short reqStreamWeight = 84;
    waitFor(4);
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/pushpath").onComplete(onSuccess(HttpServerResponse::end));
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .pushHandler(pushReq -> {
          complete();
          pushReq.response().onComplete(onSuccess(pushResp -> {
            assertEquals(reqStreamDependency, pushResp.request().getStreamPriority().getDependency());
            assertEquals(reqStreamWeight, pushResp.request().getStreamPriority().getWeight());
            complete();
          }));
        }).setStreamPriority(
          new Http2StreamPriority()
            .setDependency(reqStreamDependency)
            .setWeight(reqStreamWeight)
            .setExclusive(false))
        .send()
        .onComplete(onSuccess(resp -> {
          complete();
        }));
    }));
    await();
  }


  @Test
  public void testClearTextUpgradeWithBody() throws Exception {
    server.close();
    server = vertx.createHttpServer().requestHandler(req -> {
      req.bodyHandler(body -> req.response().end(body));
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2))
      .withConnectHandler(conn -> {
        conn.goAwayHandler(ga -> {
          assertEquals(0, ga.getErrorCode());
        });
      })
      .build();
    Buffer payload = Buffer.buffer("some-data");
    client.request(new RequestOptions(requestOptions).setSsl(false)).onComplete(onSuccess(req -> {
      req.response()
        .compose(HttpClientResponse::body)
        .onComplete(onSuccess(body -> {
          assertEquals(Buffer.buffer().appendBuffer(payload).appendBuffer(payload), body);
          testComplete();
        }));
      req.putHeader("Content-Length", "" + payload.length() * 2);
      req.exceptionHandler(this::fail);
      req.write(payload);
      vertx.setTimer(1000, id -> {
        req.end(payload);
      });
    }));
    await();
  }

  @Test
  public void testClearTextUpgradeWithBodyTooLongFrameResponse() throws Exception {
    server.close();
    Buffer buffer = TestUtils.randomBuffer(1024);
    server = vertx.createHttpServer().requestHandler(req -> {
      req.response().setChunked(true);
      vertx.setPeriodic(1, id -> {
        req.response().write(buffer);
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    client.request(new RequestOptions(requestOptions).setSsl(false)).onComplete(onSuccess(req -> {
      req.response().onComplete(onFailure(err -> {
      }));
      req.setChunked(true);
      req.exceptionHandler(err -> {
        if (err instanceof TooLongFrameException) {
          testComplete();
        }
      });
      req.sendHead();
    }));
    await();
  }

  @Ignore
  @Test
  public void testAppendToHttpChunks() throws Exception {
    // This test does not work on http/2
  }

  @Test
  public void testSslHandshakeTimeout() throws Exception {
    waitFor(2);
    HttpServerOptions opts = createBaseServerOptions()
      .setSslHandshakeTimeout(1234)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    server.close();
    server = vertx.createHttpServer(opts)
      .requestHandler(req -> fail("Should not be called"))
      .exceptionHandler(err -> {
        if (err instanceof SSLHandshakeException) {
          assertEquals("handshake timed out after 1234ms", err.getMessage());
          complete();
        }
      });
    startServer();
    vertx.createNetClient().connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST)
      .onFailure(this::fail)
      .onSuccess(so -> so.closeHandler(u -> complete()));
    await();
  }

  @Test
  public void testNonUpgradedH2CConnectionIsEvictedFromThePool() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(false));
    Promise<Void> p = Promise.promise();
    AtomicBoolean first = new AtomicBoolean(true);
    server.requestHandler(req -> {
      if (first.compareAndSet(true, false)) {
        HttpConnection conn = req.connection();
        p.future().onComplete(ar -> {
          conn.close();
        });
      }
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req1 -> {
      req1.connection().closeHandler(v1 -> {
        vertx.runOnContext(v2 -> {
          client.request(requestOptions).compose(req2 -> req2.send().compose(HttpClientResponse::body)).onComplete(onSuccess(b2 -> {
            testComplete();
          }));
        });
      });
      return req1.send().compose(resp -> {
        assertEquals(clientAlpnProtocolVersion(), resp.version());
        return resp.body();
      });
    }).onComplete(onSuccess(b -> {
      p.complete();
    }));
    await();
  }

  @Test
  public void testClientDoesNotSupportAlpn() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(clientAlpnProtocolVersion(), req.version());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client =
      vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(clientAlpnProtocolVersion()).setUseAlpn(false));
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(clientAlpnProtocolVersion(), resp.version());
        complete();
      }));
    await();
  }

  @Test
  public void testServerDoesNotSupportAlpn() throws Exception {
    waitFor(2);
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setUseAlpn(false));
    server.requestHandler(req -> {
      assertEquals(clientAlpnProtocolVersion(), req.version());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(clientAlpnProtocolVersion(), resp.version());
        complete();
      }));

    await();
  }

  @Repeat(times = 10)
  @Test
  public void testHttpClientDelayedWriteUponConnectionClose() throws Exception {

    int numVerticles = 5;
    int numWrites = 100;
    int delayCloseMS = 50;

    server.connectionHandler(conn -> {
      vertx.setTimer(delayCloseMS, id -> {
        conn.close();
      });
    });
    server.requestHandler(req -> {
      req.endHandler(v -> {
        req.response().end();
      });
    });

    startServer(testAddress);
    waitFor(numVerticles);
    vertx.deployVerticle(() -> new VerticleBase() {
      int requestCount;
      int ackCount;
      @Override
      public Future<?> start() throws Exception {
        request();
        return super.start();
      }
      private void request() {
        requestCount++;
        client.request(requestOptions)
          .compose(req -> {
            req.setChunked(true);
            for (int i = 0;i < numWrites;i++) {
              req.write("Hello").onComplete(ar -> {
                ackCount++;
              });
            }
            req.end();
            return req.response().compose(HttpClientResponse::body);
          })
          .onComplete(ar -> {
            if (ar.succeeded()) {
              request();
            } else {
              vertx.setTimer(100, id -> {
                assertEquals(requestCount * numWrites, ackCount);
                complete();
              });
            }
          });
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setInstances(numVerticles));

    await();
  }

}
