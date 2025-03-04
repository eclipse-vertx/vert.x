package io.vertx.tests.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.*;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.Utils;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.core.TestUtils;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static io.vertx.test.core.TestUtils.*;

public class Http2ServerTest extends HttpServerTest {


  @Override
  public void setUp() throws Exception {
    eventLoopGroups.clear();
    serverOptions = HttpOptionsFactory.createHttp2ServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
    clientOptions = HttpOptionsFactory.createHttp2ClientOptions();
    super.setUp();
  }

  @Test
  public void testBodyEndHandler() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      AtomicInteger count = new AtomicInteger();
      resp.bodyEndHandler(v -> {
        assertEquals(0, count.getAndIncrement());
        assertTrue(resp.ended());
      });
      resp.write("something");
      assertEquals(0, count.get());
      resp.end();
      assertEquals(1, count.get());
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testPost() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    Buffer expectedContent = TestUtils.randomBuffer(1000);
    Buffer postContent = Buffer.buffer();
    server.requestHandler(req -> {
      assertOnIOContext(ctx);
      req.handler(buff -> {
        assertOnIOContext(ctx);
        postContent.appendBuffer(buff);
      });
      req.endHandler(v -> {
        assertOnIOContext(ctx);
        req.response().putHeader("content-type", "text/plain").end("");
        assertEquals(expectedContent, postContent);
        testComplete();
      });
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/").set("content-type", "text/plain"), 0, false, request.context.newPromise());
      request.encoder.writeData(request.context, id, ((BufferInternal)expectedContent).getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testPostFileUpload() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      Buffer tot = Buffer.buffer();
      req.setExpectMultipart(true);
      req.uploadHandler(upload -> {
        assertOnIOContext(ctx);
        assertEquals("file", upload.name());
        assertEquals("tmp-0.txt", upload.filename());
        assertEquals("image/gif", upload.contentType());
        upload.handler(tot::appendBuffer);
        upload.endHandler(v -> {
          assertEquals(tot, Buffer.buffer("some-content"));
          testComplete();
        });
      });
      req.endHandler(v -> {
        assertEquals(0, req.formAttributes().size());
        req.response().putHeader("content-type", "text/plain").end("done");
      });
    });
    startServer(ctx);

    String contentType = "multipart/form-data; boundary=a4e41223-a527-49b6-ac1c-315d76be757e";
    String contentLength = "225";
    String body = "--a4e41223-a527-49b6-ac1c-315d76be757e\r\n" +
        "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
        "Content-Type: image/gif; charset=utf-8\r\n" +
        "Content-Length: 12\r\n" +
        "\r\n" +
        "some-content\r\n" +
        "--a4e41223-a527-49b6-ac1c-315d76be757e--\r\n";

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/form").
          set("content-type", contentType).set("content-length", contentLength), 0, false, request.context.newPromise());
      request.encoder.writeData(request.context, id, BufferInternal.buffer(body).getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testConnect() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.CONNECT, req.method());
      assertEquals("whatever.com", req.authority().host());
      assertNull(req.path());
      assertNull(req.query());
      assertNull(req.scheme());
      assertNull(req.uri());
      assertNull(req.absoluteURI());
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2Headers headers = new DefaultHttp2Headers().method("CONNECT").authority("whatever.com");
      request.encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testServerRequestPauseResume() throws Exception {
    testStreamPauseResume(req -> Future.succeededFuture(req));
  }

  private void testStreamPauseResume(Function<HttpServerRequest, Future<ReadStream<Buffer>>> streamProvider) throws Exception {
    Buffer expected = Buffer.buffer();
    String chunk = TestUtils.randomAlphaString(1000);
    AtomicBoolean done = new AtomicBoolean();
    AtomicBoolean paused = new AtomicBoolean();
    Buffer received = Buffer.buffer();
    server.requestHandler(req -> {
      Future<ReadStream<Buffer>> fut = streamProvider.apply(req);
      fut.onComplete(onSuccess(stream -> {
        vertx.setPeriodic(1, timerID -> {
          if (paused.get()) {
            vertx.cancelTimer(timerID);
            done.set(true);
            // Let some time to accumulate some more buffers
            vertx.setTimer(100, id -> {
              stream.resume();
            });
          }
        });
        stream.handler(received::appendBuffer);
        stream.endHandler(v -> {
          assertEquals(expected, received);
          testComplete();
        });
        stream.pause();
      }));
    });
    startServer();

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/form").
          set("content-type", "text/plain"), 0, false, request.context.newPromise());
      request.context.flush();
      Http2Stream stream = request.connection.stream(id);
      class Anonymous {
        void send() {
          boolean writable = request.encoder.flowController().isWritable(stream);
          if (writable) {
            Buffer buf = Buffer.buffer(chunk);
            expected.appendBuffer(buf);
            request.encoder.writeData(request.context, id, ((BufferInternal)buf).getByteBuf(), 0, false, request.context.newPromise());
            request.context.flush();
            request.context.executor().execute(this::send);
          } else {
            request.encoder.writeData(request.context, id, Unpooled.EMPTY_BUFFER, 0, true, request.context.newPromise());
            request.context.flush();
            paused.set(true);
          }
        }
      }
      new Anonymous().send();
    });
    fut.sync();
    await();
  }

  @Test
  public void testServerResponseWritability() throws Exception {
    testStreamWritability(req -> {
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.setChunked(true);
      return Future.succeededFuture(resp);
    });
  }

  private void testStreamWritability(Function<HttpServerRequest, Future<WriteStream<Buffer>>> streamProvider) throws Exception {
    Context ctx = vertx.getOrCreateContext();
    String content = TestUtils.randomAlphaString(1024);
    StringBuilder expected = new StringBuilder();
    Promise<Void> whenFull = Promise.promise();
    AtomicBoolean drain = new AtomicBoolean();
    server.requestHandler(req -> {
      Future<WriteStream<Buffer>> fut = streamProvider.apply(req);
      fut.onComplete(onSuccess(stream -> {
        vertx.setPeriodic(1, timerID -> {
          if (stream.writeQueueFull()) {
            stream.drainHandler(v -> {
              assertOnIOContext(ctx);
              expected.append("last");
              stream.end(Buffer.buffer("last"));
            });
            vertx.cancelTimer(timerID);
            drain.set(true);
            whenFull.complete();
          } else {
            expected.append(content);
            Buffer buf = Buffer.buffer(content);
            stream.write(buf);
          }
        });
      }));
    });
    startServer(ctx);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      AtomicInteger toAck = new AtomicInteger();
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {

        StringBuilder received = new StringBuilder();

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          received.append(data.toString(StandardCharsets.UTF_8));
          int delta = super.onDataRead(ctx, streamId, data, padding, endOfStream);
          if (endOfStream) {
            vertx.runOnContext(v -> {
              assertEquals(expected.toString(), received.toString());
              testComplete();
            });
            return delta;
          } else {
            if (drain.get()) {
              return delta;
            } else {
              toAck.getAndAdd(delta);
              return 0;
            }
          }
        }
      });
      whenFull.future().onComplete(ar -> {
        request.context.executor().execute(() -> {
          try {
            request.decoder.flowController().consumeBytes(request.connection.stream(id), toAck.intValue());
            request.context.flush();
          } catch (Http2Exception e) {
            e.printStackTrace();
            fail(e);
          }
        });
      });
    });

    fut.sync();

    await();
  }

  @Test
  public void testTrailers() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      resp.write("some-content");
      resp.putTrailer("Foo", "foo_value");
      resp.putTrailer("bar", "bar_value");
      resp.putTrailer("juu", (List<String>)Arrays.asList("juu_value_1", "juu_value_2"));
      resp.end();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        int count;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          switch (count++) {
            case 0:
              vertx.runOnContext(v -> {
                assertFalse(endStream);
              });
              break;
            case 1:
              vertx.runOnContext(v -> {
                assertEquals("foo_value", headers.get("foo").toString());
                assertEquals(1, headers.getAll("foo").size());
                assertEquals("foo_value", headers.getAll("foo").get(0).toString());
                assertEquals("bar_value", headers.getAll("bar").get(0).toString());
                assertEquals(2, headers.getAll("juu").size());
                assertEquals("juu_value_1", headers.getAll("juu").get(0).toString());
                assertEquals("juu_value_2", headers.getAll("juu").get(1).toString());
                assertTrue(endStream);
                testComplete();
              });
              break;
            default:
              vertx.runOnContext(v -> {
                fail();
              });
              break;
          }
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testServerResetClientStream1() throws Exception {
    server.requestHandler(req -> {
      req.handler(buf -> {
        req.response().reset(8);
      });
    });
    testServerResetClientStream(code -> {
      assertEquals(8, code);
      testComplete();
    }, false);
  }

  @Test
  public void testServerResetClientStream2() throws Exception {
    server.requestHandler(req -> {
      req.handler(buf -> {
        req.response().end();
        req.response().reset(8);
      });
    });
    testServerResetClientStream(code -> {
      assertEquals(8, code);
      testComplete();
    }, false);
  }

  @Test
  public void testServerResetClientStream3() throws Exception {
    server.requestHandler(req -> {
      req.endHandler(buf -> {
        req.response().reset(8);
      });
    });
    testServerResetClientStream(code -> {
      assertEquals(8, code);
      testComplete();
    }, true);
  }

  private void testServerResetClientStream(LongConsumer resetHandler, boolean end) throws Exception {
    startServer();

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
          vertx.runOnContext(v -> {
            resetHandler.accept(errorCode);
          });
        }
      });
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      encoder.writeData(request.context, id, BufferInternal.buffer("hello").getByteBuf(), 0, end, request.context.newPromise());
    });

    fut.sync();

    await();
  }

  @Test
  public void testClientResetServerStream() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    Promise<Void> bufReceived = Promise.promise();
    AtomicInteger resetCount = new AtomicInteger();
    server.requestHandler(req -> {
      req.handler(buf -> {
        bufReceived.complete();
      });
      req.exceptionHandler(err -> {
        assertOnIOContext(ctx);
        if (err instanceof StreamResetException) {
          assertEquals(10L, ((StreamResetException) err).getCode());
          assertEquals(0, resetCount.getAndIncrement());
        }
      });
      req.response().exceptionHandler(err -> {
        assertOnIOContext(ctx);
        if (err instanceof StreamResetException) {
          assertEquals(10L, ((StreamResetException) err).getCode());
          assertEquals(1, resetCount.getAndIncrement());
          testComplete();
        }
      });
    });
    startServer(ctx);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      encoder.writeData(request.context, id, BufferInternal.buffer("hello").getByteBuf(), 0, false, request.context.newPromise());
      bufReceived.future().onComplete(ar -> {
        encoder.writeRstStream(request.context, id, 10, request.context.newPromise());
        request.context.flush();
      });
    });

    fut.sync();

    await();
  }

  @Test
  public void testConnectionClose() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      conn.closeHandler(v -> {
        assertSame(ctx, Vertx.currentContext());
        testComplete();
      });
      req.response().putHeader("Content-Type", "text/plain").end();
    });
    startServer(ctx);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          request.context.close();
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testPushPromise() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, "/wibble").onComplete(handler);
    }, headers -> {
      assertEquals("GET", headers.method().toString());
      assertEquals("https", headers.scheme().toString());
      assertEquals("/wibble", headers.path().toString());
      assertEquals("whatever.com", headers.authority().toString());
    });
  }

  @Test
  public void testPushPromiseHeaders() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, "/wibble", HttpHeaders.
          set("foo", "foo_value").
          set("bar", Arrays.<CharSequence>asList("bar_value_1", "bar_value_2"))).onComplete(handler);
    }, headers -> {
      assertEquals("GET", headers.method().toString());
      assertEquals("https", headers.scheme().toString());
      assertEquals("/wibble", headers.path().toString());
      assertEquals("whatever.com", headers.authority().toString());
      assertEquals("foo_value", headers.get("foo").toString());
      assertEquals(Arrays.asList("bar_value_1", "bar_value_2"), headers.getAll("bar").stream().map(CharSequence::toString).collect(Collectors.toList()));
    });
  }

  @Test
  public void testPushPromiseNoAuthority() throws Exception {
    Http2Headers get = GET("/");
    get.remove("authority");
    testPushPromise(get, (resp, handler ) -> {
      resp.push(HttpMethod.GET, "/wibble").onComplete(handler);
    }, headers -> {
      assertEquals("GET", headers.method().toString());
      assertEquals("https", headers.scheme().toString());
      assertEquals("/wibble", headers.path().toString());
      assertNull(headers.authority());
    });
  }

  @Test
  public void testPushPromiseOverrideAuthority() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, HostAndPort.authority("override.com"), "/wibble").onComplete(handler);
    }, headers -> {
      assertEquals("GET", headers.method().toString());
      assertEquals("https", headers.scheme().toString());
      assertEquals("/wibble", headers.path().toString());
      assertEquals("override.com", headers.authority().toString());
    });
  }


  private void testPushPromise(Http2Headers requestHeaders,
                               BiConsumer<HttpServerResponse, Handler<AsyncResult<HttpServerResponse>>> pusher,
                               Consumer<Http2Headers> headerChecker) throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      Handler<AsyncResult<HttpServerResponse>> handler = ar -> {
        assertSameEventLoop(ctx, Vertx.currentContext());
        assertTrue(ar.succeeded());
        HttpServerResponse response = ar.result();
        response./*putHeader("content-type", "application/plain").*/end("the_content");
        assertIllegalStateException(() -> response.push(HttpMethod.GET, "/wibble2"));
      };
      pusher.accept(req.response(), handler);
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, requestHeaders, 0, true, request.context.newPromise());
      Map<Integer, Http2Headers> pushed = new HashMap<>();
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
          pushed.put(promisedStreamId, headers);
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          int delta = super.onDataRead(ctx, streamId, data, padding, endOfStream);
          String content = data.toString(StandardCharsets.UTF_8);
          vertx.runOnContext(v -> {
            assertEquals(Collections.singleton(streamId), pushed.keySet());
            assertEquals("the_content", content);
            Http2Headers pushedHeaders = pushed.get(streamId);
            headerChecker.accept(pushedHeaders);
            testComplete();
          });
          return delta;
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testResetActivePushPromise() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble").onComplete(onSuccess(response -> {
        assertOnIOContext(ctx);
        AtomicInteger resets = new AtomicInteger();
        response.exceptionHandler(err -> {
          if (err instanceof StreamResetException) {
            assertEquals(8, ((StreamResetException)err).getCode());
            resets.incrementAndGet();
          }
        });
        response.closeHandler(v -> {
          testComplete();
          assertEquals(1, resets.get());
        });
        response.setChunked(true).write("some_content");
      }));
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          request.encoder.writeRstStream(ctx, streamId, Http2Error.CANCEL.code(), ctx.newPromise());
          request.context.flush();
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testQueuePushPromise() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    int numPushes = 10;
    Set<String> pushSent = new HashSet<>();
    server.requestHandler(req -> {
      req.response().setChunked(true).write("abc");
      for (int i = 0; i < numPushes; i++) {
        int val = i;
        String path = "/wibble" + val;
        req.response().push(HttpMethod.GET, path).onComplete(onSuccess(response -> {
          assertSameEventLoop(ctx, Vertx.currentContext());
          pushSent.add(path);
          vertx.setTimer(10, id -> {
            response.end("wibble-" + val);
          });
        }));
      }
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(3);
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        int count = numPushes;
        Set<String> pushReceived = new HashSet<>();

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
          pushReceived.add(headers.path().toString());
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          if (count-- == 0) {
            vertx.runOnContext(v -> {
              assertEquals(numPushes, pushSent.size());
              assertEquals(pushReceived, pushSent);
              testComplete();
            });
          }
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testResetPendingPushPromise() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble").onComplete(onFailure(r -> {
        assertOnIOContext(ctx);
        testComplete();
      }));
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(0);
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
          request.encoder.writeRstStream(request.context, promisedStreamId, Http2Error.CANCEL.code(), request.context.newPromise());
          request.context.flush();
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testHostHeaderInsteadOfAuthorityPseudoHeader() throws Exception {
    // build the HTTP/2 headers, omit the ":authority" pseudo-header and include the "host" header instead
    Http2Headers headers = new DefaultHttp2Headers().method("GET").scheme("https").path("/").set("host", DEFAULT_HTTPS_HOST_AND_PORT);
    server.requestHandler(req -> {
      // validate that the authority is properly populated
      assertEquals(DEFAULT_HTTPS_HOST, req.authority().host());
      assertEquals(DEFAULT_HTTPS_PORT, req.authority().port());
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
    });
    fut.sync();
    await();
  }

  @Test
  public void testMissingMethodPseudoHeader() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().scheme("http").path("/"));
  }

  @Test
  public void testMissingSchemePseudoHeader() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").path("/"));
  }

  @Test
  public void testMissingPathPseudoHeader() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http"));
  }

  @Test
  public void testInvalidAuthority() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http").authority("foo@" + DEFAULT_HTTPS_HOST_AND_PORT).path("/"));
  }

  @Test
  public void testInvalidHost1() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http").authority(DEFAULT_HTTPS_HOST_AND_PORT).path("/").set("host", "foo@" + DEFAULT_HTTPS_HOST_AND_PORT));
  }

  @Test
  public void testInvalidHost2() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http").authority(DEFAULT_HTTPS_HOST_AND_PORT).path("/").set("host", "another-host:" + DEFAULT_HTTPS_PORT));
  }

  @Test
  public void testInvalidHost3() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http").authority(DEFAULT_HTTPS_HOST_AND_PORT).path("/").set("host", DEFAULT_HTTP_HOST));
  }

  @Test
  public void testConnectInvalidPath() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("CONNECT").path("/").authority(DEFAULT_HTTPS_HOST_AND_PORT));
  }

  @Test
  public void testConnectInvalidScheme() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("CONNECT").scheme("http").authority(DEFAULT_HTTPS_HOST_AND_PORT));
  }

  @Test
  public void testConnectInvalidAuthority() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("CONNECT").authority("foo@" + DEFAULT_HTTPS_HOST_AND_PORT));
  }

  private void testMalformedRequestHeaders(Http2Headers headers) throws Exception {
    server.requestHandler(req -> fail());
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
          vertx.runOnContext(v -> {
            testComplete();
          });
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testRequestHandlerFailure() throws Exception {
    testHandlerFailure(false, (err, server) -> {
      server.requestHandler(req -> {
        throw err;
      });
    });
  }

  @Test
  public void testRequestEndHandlerFailure() throws Exception {
    testHandlerFailure(false, (err, server) -> {
      server.requestHandler(req -> {
        req.endHandler(v -> {
          throw err;
        });
      });
    });
  }

  @Test
  public void testRequestEndHandlerFailureWithData() throws Exception {
    testHandlerFailure(true, (err, server) -> {
      server.requestHandler(req -> {
        req.endHandler(v -> {
          throw err;
        });
      });
    });
  }

  @Test
  public void testRequestDataHandlerFailure() throws Exception {
    testHandlerFailure(true, (err, server) -> {
      server.requestHandler(req -> {
        req.handler(buf -> {
          throw err;
        });
      });
    });
  }

  private void testHandlerFailure(boolean data, BiConsumer<RuntimeException, HttpServer> configurator) throws Exception {
    RuntimeException failure = new RuntimeException();
    io.vertx.core.http.Http2Settings settings = TestUtils.randomHttp2Settings();
    server.close();
    server = vertx.createHttpServer(serverOptions.setInitialSettings(settings));
    configurator.accept(failure, server);
    Context ctx = vertx.getOrCreateContext();
    ctx.exceptionHandler(err -> {
      assertSame(err, failure);
      testComplete();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, !data, request.context.newPromise());
      if (data) {
        request.encoder.writeData(request.context, id, BufferInternal.buffer("hello").getByteBuf(), 0, true, request.context.newPromise());
      }
    });
    fut.sync();
    await();
  }

  private static File createTempFile(Buffer buffer) throws Exception {
    File f = File.createTempFile("vertx", ".bin");
    f.deleteOnExit();
    try(FileOutputStream out = new FileOutputStream(f)) {
      out.write(buffer.getBytes());
    }
    return f;
  }

  @Test
  public void testSendFile() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1000 * 1000));
    File tmp = createTempFile(expected);
    testSendFile(expected, tmp.getAbsolutePath(), 0, expected.length());
  }

  @Test
  public void testSendFileRange() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1000 * 1000));
    File tmp = createTempFile(expected);
    int from = 200 * 1000;
    int to = 700 * 1000;
    testSendFile(expected.slice(from, to), tmp.getAbsolutePath(), from, to - from);
  }

  @Test
  public void testSendEmptyFile() throws Exception {
    Buffer expected = Buffer.buffer();
    File tmp = createTempFile(expected);
    testSendFile(expected, tmp.getAbsolutePath(), 0, expected.length());
  }

  private void testSendFile(Buffer expected, String path, long offset, long length) throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.sendFile(path, offset, length).onComplete(onSuccess(v -> {
        assertEquals(resp.bytesWritten(), length);
        complete();
      }));
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        Buffer buffer = Buffer.buffer();
        Http2Headers responseHeaders;
        private void endStream() {
          vertx.runOnContext(v -> {
            assertEquals("" + length, responseHeaders.get("content-length").toString());
            assertEquals(expected, buffer);
            complete();
          });
        }
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          responseHeaders = headers;
          if (endStream) {
            endStream();
          }
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          buffer.appendBuffer(BufferInternal.buffer(data.duplicate()));
          if (endOfStream) {
            endStream();
          }
          return data.readableBytes() + padding;
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testStreamError() throws Exception {
    waitFor(2);
    Promise<Void> when = Promise.promise();
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      AtomicInteger reqErrors = new AtomicInteger();
      req.exceptionHandler(err -> {
        // Called twice : reset + close
        assertOnIOContext(ctx);
        reqErrors.incrementAndGet();
      });
      AtomicInteger respErrors = new AtomicInteger();
      req.response().exceptionHandler(err -> {
        assertOnIOContext(ctx);
        respErrors.incrementAndGet();
      });
      req.response().closeHandler(v -> {
        assertOnIOContext(ctx);
        assertTrue("Was expecting reqErrors to be > 0", reqErrors.get() > 0);
        assertTrue("Was expecting respErrors to be > 0", respErrors.get() > 0);
        complete();
      });
      req.response().endHandler(v -> {
        assertOnIOContext(ctx);
        complete();
      });
      when.complete();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
      when.future().onComplete(ar -> {
        // Send a corrupted frame on purpose to check we get the corresponding error in the request exception handler
        // the error is : greater padding value 0c -> 1F
        // ChannelFuture a = encoder.frameWriter().writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 12, false, request.context.newPromise());
        // normal frame    : 00 00 12 00 08 00 00 00 03 0c 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        // corrupted frame : 00 00 12 00 08 00 00 00 03 1F 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        request.channel.write(BufferInternal.buffer(new byte[]{
            0x00, 0x00, 0x12, 0x00, 0x08, 0x00, 0x00, 0x00, (byte)(id & 0xFF), 0x1F, 0x68, 0x65, 0x6c, 0x6c,
            0x6f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        }).getByteBuf());
        request.context.flush();
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testPromiseStreamError() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    waitFor(2);
    Promise<Void> when = Promise.promise();
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble").onComplete(onSuccess(resp -> {
        assertOnIOContext(ctx);
        when.complete();
        AtomicInteger erros = new AtomicInteger();
        resp.exceptionHandler(err -> {
          assertOnIOContext(ctx);
          erros.incrementAndGet();
        });
        resp.closeHandler(v -> {
          assertOnIOContext(ctx);
          assertTrue("Was expecting errors to be > 0", erros.get() > 0);
          complete();
        });
        resp.endHandler(v -> {
          assertOnIOContext(ctx);
          complete();
        });
        resp.setChunked(true).write("whatever"); // Transition to half-closed remote
      }));
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
          when.future().onComplete(ar -> {
            Http2ConnectionEncoder encoder = request.encoder;
            encoder.frameWriter().writeHeaders(request.context, promisedStreamId, GET("/"), 0, false, request.context.newPromise());
            request.context.flush();
          });
        }
      });
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testConnectionDecodeError() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    waitFor(3);
    Promise<Void> when = Promise.promise();
    server.requestHandler(req -> {
      AtomicInteger reqFailures = new AtomicInteger();
      AtomicInteger respFailures = new AtomicInteger();
      req.exceptionHandler(err -> {
        assertOnIOContext(ctx);
        reqFailures.incrementAndGet();
      });
      req.response().exceptionHandler(err -> {
        assertOnIOContext(ctx);
        respFailures.incrementAndGet();
      });
      req.response().closeHandler(v -> {
        assertOnIOContext(ctx);
        complete();
      });
      req.response().endHandler(v -> {
        assertOnIOContext(ctx);
        assertTrue(reqFailures.get() > 0);
        assertTrue(respFailures.get() > 0);
        complete();
      });
      HttpConnection conn = req.connection();
      AtomicInteger connFailures = new AtomicInteger();
      conn.exceptionHandler(err -> {
        assertOnIOContext(ctx);
        connFailures.incrementAndGet();
      });
      conn.closeHandler(v -> {
        assertTrue(connFailures.get() > 0);
        assertOnIOContext(ctx);
        complete();
      });
      when.complete();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      when.future().onComplete(ar -> {
        // Send a stream ID that does not exists
        encoder.frameWriter().writeRstStream(request.context, 10, 0, request.context.newPromise());
        request.context.flush();
      });
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testServerSendGoAwayNoError() throws Exception {
    waitFor(2);
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    AtomicInteger closed = new AtomicInteger();
    AtomicBoolean done = new AtomicBoolean();
    Context ctx = vertx.getOrCreateContext();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          assertTrue(done.get());
        });
        req.response().exceptionHandler(err -> {
          assertTrue(done.get());
        });
      } else {
        assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().exceptionHandler(err -> {
          assertEquals(HttpClosedException.class, err.getClass());
          assertEquals(0, ((HttpClosedException)err).goAway().getErrorCode());
          closed.incrementAndGet();
        });
        HttpConnection conn = req.connection();
        conn.shutdownHandler(v -> {
          assertFalse(done.get());
        });
        conn.closeHandler(v -> {
          assertTrue(done.get());
        });
        ctx.runOnContext(v1 -> {
          conn.goAway(0, first.get().response().streamId());
          vertx.setTimer(300, timerID -> {
            assertEquals(1, status.getAndIncrement());
            done.set(true);
            complete();
          });
        });
      }
    };
    testServerSendGoAway(requestHandler, 0);
  }

  @Ignore
  @Test
  public void testServerSendGoAwayInternalError() throws Exception {
    waitFor(3);
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    AtomicInteger closed = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          fail();
        });
        req.response().closeHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().endHandler(err -> {
          closed.incrementAndGet();
        });
      } else {
        assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().closeHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().endHandler(err -> {
          closed.incrementAndGet();
        });
        HttpConnection conn = req.connection();
        conn.closeHandler(v -> {
          assertEquals(5, closed.get());
          assertEquals(1, status.get());
          complete();
        });
        conn.shutdownHandler(v -> {
          assertEquals(1, status.get());
          complete();
        });
        conn.goAway(2, first.get().response().streamId());
      }
    };
    testServerSendGoAway(requestHandler, 2);
  }

  @Test
  public void testShutdownWithTimeout() throws Exception {
    waitFor(2);
    AtomicInteger closed = new AtomicInteger();
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          fail();
        });
        req.response().closeHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().endHandler(err -> {
          closed.incrementAndGet();
        });
      } else {
        assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          fail();
        });
        req.response().closeHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().endHandler(err -> {
          closed.incrementAndGet();
        });
        HttpConnection conn = req.connection();
        conn.closeHandler(v -> {
          assertEquals(4, closed.get());
          assertEquals(1, status.getAndIncrement());
          complete();
        });
        conn.shutdown(300, TimeUnit.MILLISECONDS);
      }
    };
    testServerSendGoAway(requestHandler, 0);
  }

  @Test
  public void testShutdown() throws Exception {
    waitFor(2);
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          fail();
        });
        req.response().exceptionHandler(err -> {
          fail();
        });
      } else {
        assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          fail();
        });
        req.response().exceptionHandler(err -> {
          fail();
        });
        HttpConnection conn = req.connection();
        conn.closeHandler(v -> {
          assertEquals(2, status.getAndIncrement());
          complete();
        });
        conn.shutdown();
        vertx.setTimer(300, timerID -> {
          assertEquals(1, status.getAndIncrement());
          first.get().response().end();
          req.response().end();
        });
      }
    };
    testServerSendGoAway(requestHandler, 0);
  }

  private void testServerSendGoAway(Handler<HttpServerRequest> requestHandler, int expectedError) throws Exception {
    server.requestHandler(requestHandler);
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals(expectedError, errorCode);
            complete();
          });
        }
      });
      Http2ConnectionEncoder encoder = request.encoder;
      int id1 = request.nextStreamId();
      encoder.writeHeaders(request.context, id1, GET("/"), 0, true, request.context.newPromise());
      int id2 = request.nextStreamId();
      encoder.writeHeaders(request.context, id2, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();

    });
    fut.sync();
    await();
  }

  @Test
  public void testServerClose() throws Exception {
    waitFor(2);
    AtomicInteger status = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      conn.shutdownHandler(v -> {
        assertEquals(0, status.getAndIncrement());
      });
      conn.closeHandler(v -> {
        assertEquals(1, status.getAndIncrement());
        complete();
      });
      conn.close();
    };
    server.requestHandler(requestHandler);
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.channel.closeFuture().addListener(v1 -> {
        vertx.runOnContext(v2 -> {
          complete();
        });
      });
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals(0, errorCode);
          });
        }
      });
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();

    });
    fut.sync();
    await();
  }

  @Test
  public void testClientSendGoAwayNoError() throws Exception {
    Promise<Void> abc = Promise.promise();
    Context ctx = vertx.getOrCreateContext();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      AtomicInteger numShutdown = new AtomicInteger();
      AtomicBoolean completed = new AtomicBoolean();
      conn.shutdownHandler(v -> {
        assertOnIOContext(ctx);
        numShutdown.getAndIncrement();
        vertx.setTimer(100, timerID -> {
          // Delay so we can check the connection is not closed
          completed.set(true);
          testComplete();
        });
      });
      conn.goAwayHandler(ga -> {
        assertOnIOContext(ctx);
        assertEquals(0, numShutdown.get());
        req.response().end();
      });
      conn.closeHandler(v -> {
        assertTrue(completed.get());
      });
      abc.complete();
    };
    server.requestHandler(requestHandler);
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
      abc.future().onComplete(ar -> {
        encoder.writeGoAway(request.context, id, 0, Unpooled.EMPTY_BUFFER, request.context.newPromise());
        request.context.flush();
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testClientSendGoAwayInternalError() throws Exception {
    // On windows the client will close the channel immediately (since it's an error)
    // and the server might see the channel inactive without receiving the close frame before
    Assume.assumeFalse(Utils.isWindows());
    Promise<Void> abc = Promise.promise();
    Context ctx = vertx.getOrCreateContext();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      AtomicInteger status = new AtomicInteger();
      conn.goAwayHandler(ga -> {
        assertOnIOContext(ctx);
        assertEquals(0, status.getAndIncrement());
        req.response().end();
      });
      conn.shutdownHandler(v -> {
        assertOnIOContext(ctx);
        assertEquals(1, status.getAndIncrement());
      });
      conn.closeHandler(v -> {
        assertEquals(2, status.getAndIncrement());
        testComplete();
      });
      abc.complete();
    };
    server.requestHandler(requestHandler);
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
      abc.future().onComplete(ar -> {
        encoder.writeGoAway(request.context, id, 3, Unpooled.EMPTY_BUFFER, request.context.newPromise());
        request.context.flush();
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testShutdownOverride() throws Exception {
    AtomicLong shutdown = new AtomicLong();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      shutdown.set(System.currentTimeMillis());
      conn.shutdown(10, TimeUnit.SECONDS);
      vertx.setTimer(300, v -> {
        conn.shutdown(300, TimeUnit.MILLISECONDS);
      });
    };
    server.requestHandler(requestHandler);
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.channel.closeFuture().addListener(v1 -> {
        vertx.runOnContext(v2 -> {
          assertTrue(shutdown.get() - System.currentTimeMillis() < 1200);
          testComplete();
        });
      });
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testRequestResponseLifecycle() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.endHandler(v -> {
        assertIllegalStateException(() -> req.setExpectMultipart(false));
        assertIllegalStateException(() -> req.handler(buf -> {}));
        assertIllegalStateException(() -> req.uploadHandler(upload -> {}));
        assertIllegalStateException(() -> req.endHandler(v2 -> {}));
        complete();
      });
      HttpServerResponse resp = req.response();
      resp.setChunked(true).write(Buffer.buffer("whatever"));
      assertTrue(resp.headWritten());
      assertIllegalStateException(() -> resp.setChunked(false));
      assertIllegalStateException(() -> resp.setStatusCode(100));
      assertIllegalStateException(() -> resp.setStatusMessage("whatever"));
      assertIllegalStateException(() -> resp.putHeader("a", "b"));
      assertIllegalStateException(() -> resp.putHeader("a", (CharSequence) "b"));
      assertIllegalStateException(() -> resp.putHeader("a", (Iterable<String>)Arrays.asList("a", "b")));
      assertIllegalStateException(() -> resp.putHeader("a", (Arrays.<CharSequence>asList("a", "b"))));
      assertIllegalStateException(resp::writeContinue);
      resp.end();
      assertIllegalStateException(() -> resp.write("a"));
      assertIllegalStateException(() -> resp.write("a", "UTF-8"));
      assertIllegalStateException(() -> resp.write(Buffer.buffer("a")));
      assertIllegalStateException(resp::end);
      assertIllegalStateException(() -> resp.end("a"));
      assertIllegalStateException(() -> resp.end("a", "UTF-8"));
      assertIllegalStateException(() -> resp.end(Buffer.buffer("a")));
      assertIllegalStateException(() -> resp.sendFile("the-file.txt"));
      assertIllegalStateException(() -> resp.closeHandler(v -> {}));
      assertIllegalStateException(() -> resp.endHandler(v -> {}));
      assertIllegalStateException(() -> resp.drainHandler(v -> {}));
      assertIllegalStateException(() -> resp.exceptionHandler(err -> {}));
      assertIllegalStateException(resp::writeQueueFull);
      assertIllegalStateException(() -> resp.setWriteQueueMaxSize(100));
      assertIllegalStateException(() -> resp.putTrailer("a", "b"));
      assertIllegalStateException(() -> resp.putTrailer("a", (CharSequence) "b"));
      assertIllegalStateException(() -> resp.putTrailer("a", (Iterable<String>)Arrays.asList("a", "b")));
      assertIllegalStateException(() -> resp.putTrailer("a", (Arrays.<CharSequence>asList("a", "b"))));
      assertIllegalStateException(() -> resp.push(HttpMethod.GET, "/whatever"));
      complete();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testResponseCompressionDisabled() throws Exception {
    waitFor(2);
    String expected = TestUtils.randomAlphaString(1000);
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals(null, headers.get(HttpHeaderNames.CONTENT_ENCODING));
            complete();
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          String s = data.toString(StandardCharsets.UTF_8);
          vertx.runOnContext(v -> {
            assertEquals(expected, s);
            complete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testResponseCompressionEnabled() throws Exception {
    waitFor(2);
    String expected = TestUtils.randomAlphaString(1000);
    server.close();
    server = vertx.createHttpServer(serverOptions.setCompressionSupported(true));
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals("gzip", headers.get(HttpHeaderNames.CONTENT_ENCODING).toString());
            complete();
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          byte[] bytes = new byte[data.readableBytes()];
          data.readBytes(bytes);
          vertx.runOnContext(v -> {
            String decoded;
            try {
              GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes));
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              while (true) {
                int i = in.read();
                if (i == -1) {
                  break;
                }
                baos.write(i);
              }
              decoded = baos.toString();
            } catch (IOException e) {
              fail(e);
              return;
            }
            assertEquals(expected, decoded);
            complete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Override
  protected void configureDomainSockets() throws Exception {
    // Nope
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    for (EventLoopGroup eventLoopGroup : eventLoopGroups) {
      eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
    }
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return serverOptions;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return clientOptions;
  }
}
