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

import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.vertx.core.http.*;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3Test extends HttpCommonTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return Http3TestBase.createHttp3ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return Http3TestBase.createHttp3ClientOptions();
  }

  @Override
  protected HttpVersion clientAlpnProtocolVersion() {
    return HttpVersion.HTTP_3;
  }

  @Override
  protected HttpVersion serverAlpnProtocolVersion() {
    return HttpVersion.HTTP_3;
  }

  @Override
  protected void addMoreOptions(HttpServerOptions opts) {
    opts.setHttp3(true);

    opts.setAlpnVersions(List.of(
      HttpVersion.HTTP_3,
      HttpVersion.HTTP_3_27,
      HttpVersion.HTTP_3_29,
      HttpVersion.HTTP_3_30,
      HttpVersion.HTTP_3_31,
      HttpVersion.HTTP_3_32,
      HttpVersion.HTTP_2,
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_0
    ));

    opts
      .getSslOptions()
      .setApplicationLayerProtocols(
        List.of(Http3.supportedApplicationProtocols())
      );
  }

  @Override
  protected HttpServerOptions setMaxConcurrentStreamsSettings(HttpServerOptions options, int maxConcurrentStreams) {
    return options.setInitialHttp3Settings(new Http3Settings());
  }

  @Test
  @Ignore
  public void testClientDrainHandler() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testServerDrainHandler() throws Exception {
    //TODO: resolve this test issue.
  }

  @Ignore
  @Test
  public void testCloseMulti() {
    // This test is ignored because UDP is based on a single connectionless protocol.
  }

  @Ignore
  @Test
  public void testInitialMaxConcurrentStreamZero() throws Exception {
    waitFor(2);
    server.close();
    server =
      vertx.createHttpServer(createBaseServerOptions().setInitialHttp3Settings(new Http3Settings().setMaxFieldSectionSize(50000)));
    server.requestHandler(req -> {
      req.response().end();
    });
    server.connectionHandler(conn -> {
      vertx.setTimer(500, id -> {
        conn.updateHttpSettings(new Http3Settings().setMaxFieldSectionSize(10));
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        assertEquals(50000, ((Http3Settings) conn.remoteHttpSettings()).getMaxFieldSectionSize());
        conn.remoteHttpSettingsHandler(settings -> {
          assertEquals(10, ((Http3Settings) conn.remoteHttpSettings()).getMaxFieldSectionSize());
          complete();
        });
      })
      .build();
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> complete()));
    await();
  }

  @Ignore
  @Test
  public void testMaxHaderListSize() throws Exception {
    server.close();
    server =
      vertx.createHttpServer(createBaseServerOptions().setInitialHttp3Settings(new Http3Settings()));
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(Http3Settings.DEFAULT_MAX_FIELD_SECTION_SIZE,
          ((Http3Settings) (resp.request().connection().remoteHttpSettings())).getMaxFieldSectionSize());
        testComplete();
      }));
    await();
  }


  @Ignore
  @Test
  public void testStreamUrgencyAndIncremental() throws Exception {
    int requestStreamUrgency = 56;
    boolean requestStreamIncremental = true;
    int responseStreamUrgency = 98;
    boolean responseStreamIncremental = false;
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(requestStreamIncremental, req.streamPriority().isIncremental());
      assertEquals(requestStreamUrgency, req.streamPriority().urgency());
      req.response().setStreamPriority(new Http3StreamPriority(new QuicStreamPriority(responseStreamUrgency,
        responseStreamIncremental)));
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new Http3StreamPriority(new QuicStreamPriority(requestStreamUrgency,
          requestStreamIncremental)))
        .send().onComplete(onSuccess(resp -> {
          assertEquals(responseStreamIncremental, resp.request().getStreamPriority().isIncremental());
          assertEquals(responseStreamUrgency, resp.request().getStreamPriority().urgency());
          complete();
        }));
    }));
    await();
  }

  @Ignore
  @Test
  public void testStreamWeightAndDependencyChange() throws Exception {
    int requestStreamDependency = 56;
    short requestStreamWeight = 43;
    int requestStreamDependency2 = 157;
    short requestStreamWeight2 = 143;
    int responseStreamDependency = 98;
    short responseStreamWeight = 55;
    int responseStreamDependency2 = 198;
    short responseStreamWeight2 = 155;
    waitFor(4);
    server.requestHandler(req -> {
      req.streamPriorityHandler(sp -> {
        assertEquals(requestStreamWeight2, sp.getWeight());
        assertEquals(requestStreamDependency2, sp.getDependency());
        assertEquals(requestStreamWeight2, req.streamPriority().getWeight());
        assertEquals(requestStreamDependency2, req.streamPriority().getDependency());
        complete();
      });
      assertEquals(requestStreamWeight, req.streamPriority().getWeight());
      assertEquals(requestStreamDependency, req.streamPriority().getDependency());
      req.response().setStreamPriority(new Http3StreamPriority()
        .setDependency(responseStreamDependency)
        .setWeight(responseStreamWeight)
        .setExclusive(false));
      req.response().write("hello");
      req.response().setStreamPriority(new Http3StreamPriority()
        .setDependency(responseStreamDependency2)
        .setWeight(responseStreamWeight2)
        .setExclusive(false));
      req.response().drainHandler(h -> {
      });
      req.response().end("world");
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new Http3StreamPriority()
          .setDependency(requestStreamDependency)
          .setWeight(requestStreamWeight)
          .setExclusive(false))
        .response()
        .onComplete(onSuccess(resp -> {
          assertEquals(responseStreamWeight, resp.request().getStreamPriority().getWeight());
          assertEquals(responseStreamDependency, resp.request().getStreamPriority().getDependency());
          resp.streamPriorityHandler(sp -> {
            assertEquals(responseStreamWeight2, sp.getWeight());
            assertEquals(responseStreamDependency2, sp.getDependency());
            assertEquals(responseStreamWeight2, resp.request().getStreamPriority().getWeight());
            assertEquals(responseStreamDependency2, resp.request().getStreamPriority().getDependency());
            complete();
          });
          complete();
        }));
      req
        .sendHead()
        .onComplete(h -> {
          req.setStreamPriority(new Http3StreamPriority()
            .setDependency(requestStreamDependency2)
            .setWeight(requestStreamWeight2)
            .setExclusive(false));
          req.end();
        });
    }));
    await();
  }

  @Ignore
  @Test
  public void testServerStreamPriorityNoChange() throws Exception {
    int urgency = 25;
    boolean incremental = true;
    waitFor(2);
    server.requestHandler(req -> {
      req.streamPriorityHandler(sp -> {
        fail("Stream priority handler should not be called " + sp);
      });
      assertEquals(urgency, req.streamPriority().urgency());
      assertEquals(incremental, req.streamPriority().isIncremental());
      req.response().end();
      req.endHandler(v -> {
        complete();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response().onComplete(onSuccess(resp -> {
          resp.endHandler(v -> {
            complete();
          });
        }));
      req.setStreamPriority(new Http3StreamPriority(new QuicStreamPriority(urgency, incremental)));
      req
        .sendHead()
        .onComplete(h -> {
          req.setStreamPriority(new Http3StreamPriority(new QuicStreamPriority(urgency, incremental)));
          req.end();
        });
    }));
    await();
  }

  @Ignore
  @Test
  public void testClientStreamPriorityNoChange() throws Exception {
    int dependency = 98;
    short weight = 55;
    boolean exclusive = false;
    waitFor(2);
    server.requestHandler(req -> {
      req.response().setStreamPriority(new Http3StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      req.response().write("hello");
      req.response().setStreamPriority(new Http3StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      req.response().end("world");
      req.endHandler(v -> {
        complete();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .send()
        .onComplete(onSuccess(resp -> {
          assertEquals(weight, resp.request().getStreamPriority().getWeight());
          assertEquals(dependency, resp.request().getStreamPriority().getDependency());
          assertEquals(exclusive, resp.request().getStreamPriority().isExclusive());
          resp.streamPriorityHandler(sp -> {
            fail("Stream priority handler should not be called");
          });
          resp.endHandler(v -> {
            complete();
          });
        }));
    }));
    await();
  }

  @Ignore
  @Test
  public void testStreamWeightAndDependencyInheritance() throws Exception {
    int requestStreamDependency = 86;
    short requestStreamWeight = 53;
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(requestStreamWeight, req.streamPriority().getWeight());
      assertEquals(requestStreamDependency, req.streamPriority().getDependency());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new Http3StreamPriority()
          .setDependency(requestStreamDependency)
          .setWeight(requestStreamWeight)
          .setExclusive(false))
        .send()
        .onComplete(onSuccess(resp -> {
          assertEquals(requestStreamWeight, resp.request().getStreamPriority().getWeight());
          assertEquals(requestStreamDependency, resp.request().getStreamPriority().getDependency());
          complete();
        }));
    }));
    await();
  }

  @Ignore
  @Test
  public void testDefaultStreamWeightAndDependency() throws Exception {
    boolean defaultIncremental = true;
    int defaultUrgency = 0;
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(defaultUrgency, req.streamPriority().urgency());
      assertEquals(defaultIncremental, req.streamPriority().isIncremental());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        assertEquals(defaultUrgency, req.getStreamPriority().urgency());
        assertEquals(defaultIncremental, req.getStreamPriority().isIncremental());
        complete();
      }));
    }));
    await();
  }

  @Ignore
  @Test
  public void testStreamWeightAndDependencyPushPromise() throws Exception {
    int pushStreamDependency = 456;
    short pushStreamWeight = 14;
    waitFor(4);
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/pushpath").onComplete(onSuccess(pushedResp -> {
        pushedResp.setStreamPriority(new Http3StreamPriority()
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

  @Ignore
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
          new Http3StreamPriority()
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
  public void testAppendToHttpChunks() throws Exception {
    List<String> expected = Arrays.asList("chunk-1", "chunk-2", "chunk-3");
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      expected.forEach(resp::write);
      resp.end(); // Will end an empty chunk
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        List<String> chunks = new ArrayList<>();
        resp.handler(chunk -> {
          chunk.appendString("-suffix");
          chunks.add(chunk.toString());
        });
        resp.endHandler(v -> {
          assertEquals(Stream.concat(expected.stream(), Stream.of(""))
            .map(s -> s + "-suffix")
            .collect(Collectors.toList()), chunks);
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  @Ignore
  public void testDiscardConnectionWhenChannelBecomesInactive() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testRstFloodProtection() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testUnsupportedAlpnVersion() throws Exception {
    //TODO: resolve this test issue.
  }

  @Ignore
  @Test
  public void testConnectionCloseEvictsConnectionFromThePoolBeforeStreamsAreClosed() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDeliverPausedBufferWhenResume() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDeliverPausedBufferWhenResumeOnOtherThread() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testPausedHttpServerRequest() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientReadStreamInWorker() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDumpManyRequestsOnQueue() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testServerLogging() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientLogging() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientDecompressionError() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDisableIdleTimeoutInPool() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testNetSocketConnectSuccessClientInitiatesCloseImmediately() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testNetSocketConnectSuccessServerInitiatesCloseOnReply() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testResetClientRequestResponseInProgress() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientRequestWithLargeBodyInSmallChunksChunked() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientRequestWithLargeBodyInSmallChunksChunkedWithHandler() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolIdleTimeout() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion1TCP4() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion1TCP6() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion1Unknown() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2TCP4() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2TCP6() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2UnixSocket() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2Unknown() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2UDP4() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2UDP6() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2UnixDataGram() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolEmptyHeader() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolIllegalHeader() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDnsClientSideLoadBalancingDisabled() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDnsClientSideLoadBalancingEnabled() throws Exception {
    //TODO: resolve this test issue.
  }
}
