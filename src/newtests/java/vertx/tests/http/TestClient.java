package vertx.tests.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestClientBase;
import org.vertx.java.newtests.TestUtils;
import org.vertx.java.tests.TLSTestParams;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private HttpClient client;
  private HttpServer server;
  private ContextChecker check;


  @Override
  public void start() {
    super.start();
    tu.appReady();
    client = new HttpClient().setHost("localhost").setPort(8080);
    check = new ContextChecker(tu);
  }

  @Override
  public void stop() {
    client.close();
    if (server != null) {
      server.close(new SimpleHandler() {
        public void handle() {
          check.check();
          TestClient.super.stop();
        }
      });
    } else {
      super.stop();
    }
  }

  private void startServer(Handler<HttpServerRequest> serverHandler) {
    server = new HttpServer();
    server.requestHandler(serverHandler);
    server.listen(8080, "localhost");
  }

  public void testClientDefaults() {
    tu.azzert(!client.isSSL());
    tu.azzert(client.getKeyStorePassword() == null);
    tu.azzert(client.getKeyStorePath() == null);
    tu.azzert(client.getTrustStorePassword() == null);
    tu.azzert(client.getTrustStorePath() == null);
    tu.azzert(client.isReuseAddress() == null);
    tu.azzert(client.isSoLinger() == null);
    tu.azzert(client.isTCPKeepAlive());
    tu.azzert(client.isTCPNoDelay());
    tu.azzert(client.getReceiveBufferSize() == null);
    tu.azzert(client.getSendBufferSize() == null);
    tu.azzert(client.getTrafficClass() == null);
    tu.testComplete();
  }

  public void testClientAttributes() {

    tu.azzert(client.setSSL(false) == client);
    tu.azzert(!client.isSSL());

    tu.azzert(client.setSSL(true) == client);
    tu.azzert(client.isSSL());

    String pwd = TestUtils.randomUnicodeString(10);
    tu.azzert(client.setKeyStorePassword(pwd) == client);
    tu.azzert(client.getKeyStorePassword().equals(pwd));

    String path = TestUtils.randomUnicodeString(10);
    tu.azzert(client.setKeyStorePath(path) == client);
    tu.azzert(client.getKeyStorePath().equals(path));

    pwd = TestUtils.randomUnicodeString(10);
    tu.azzert(client.setTrustStorePassword(pwd) == client);
    tu.azzert(client.getTrustStorePassword().equals(pwd));

    path = TestUtils.randomUnicodeString(10);
    tu.azzert(client.setTrustStorePath(path) == client);
    tu.azzert(client.getTrustStorePath().equals(path));

    tu.azzert(client.setReuseAddress(true) == client);
    tu.azzert(client.isReuseAddress());
    tu.azzert(client.setReuseAddress(false) == client);
    tu.azzert(!client.isReuseAddress());

    tu.azzert(client.setSoLinger(true) == client);
    tu.azzert(client.isSoLinger());
    tu.azzert(client.setSoLinger(false) == client);
    tu.azzert(!client.isSoLinger());

    tu.azzert(client.setTCPKeepAlive(true) == client);
    tu.azzert(client.isTCPKeepAlive());
    tu.azzert(client.setTCPKeepAlive(false) == client);
    tu.azzert(!client.isTCPKeepAlive());

    tu.azzert(client.setTCPNoDelay(true) == client);
    tu.azzert(client.isTCPNoDelay());
    tu.azzert(client.setTCPNoDelay(false) == client);
    tu.azzert(!client.isTCPNoDelay());

    int rbs = new Random().nextInt(1024 * 1024) + 1;
    tu.azzert(client.setReceiveBufferSize(rbs) == client);
    tu.azzert(client.getReceiveBufferSize() == rbs);

    try {
      client.setReceiveBufferSize(0);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setReceiveBufferSize(-1);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int sbs = new Random().nextInt(1024 * 1024);
    tu.azzert(client.setSendBufferSize(sbs) == client);
    tu.azzert(client.getSendBufferSize() == sbs);

    try {
      client.setSendBufferSize(0);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setSendBufferSize(-1);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int trafficClass = new Random().nextInt(10000000);
    tu.azzert(client.setTrafficClass(trafficClass) == client);
    tu.azzert(client.getTrafficClass() == trafficClass);

    tu.testComplete();

  }

  public void testServerDefaults() {
    NetServer server = new NetServer();
    tu.azzert(!server.isSSL());
    tu.azzert(server.getKeyStorePassword() == null);
    tu.azzert(server.getKeyStorePath() == null);
    tu.azzert(server.getTrustStorePassword() == null);
    tu.azzert(server.getTrustStorePath() == null);
    tu.azzert(server.isReuseAddress());
    tu.azzert(server.isSoLinger() == null);
    tu.azzert(server.isTCPKeepAlive());
    tu.azzert(server.isTCPNoDelay());
    tu.azzert(server.getReceiveBufferSize() == null);
    tu.azzert(server.getSendBufferSize() == null);
    tu.azzert(server.getTrafficClass() == null);
    tu.testComplete();
  }

  public void testServerAttributes() {

    HttpServer server = new HttpServer();

    tu.azzert(server.setSSL(false) == server);
    tu.azzert(!server.isSSL());

    tu.azzert(server.setSSL(true) == server);
    tu.azzert(server.isSSL());


    String pwd = TestUtils.randomUnicodeString(10);
    tu.azzert(server.setKeyStorePassword(pwd) == server);
    tu.azzert(server.getKeyStorePassword().equals(pwd));

    String path = TestUtils.randomUnicodeString(10);
    tu.azzert(server.setKeyStorePath(path) == server);
    tu.azzert(server.getKeyStorePath().equals(path));

    pwd = TestUtils.randomUnicodeString(10);
    tu.azzert(server.setTrustStorePassword(pwd) == server);
    tu.azzert(server.getTrustStorePassword().equals(pwd));

    path = TestUtils.randomUnicodeString(10);
    tu.azzert(server.setTrustStorePath(path) == server);
    tu.azzert(server.getTrustStorePath().equals(path));

    tu.azzert(server.setReuseAddress(true) == server);
    tu.azzert(server.isReuseAddress());
    tu.azzert(server.setReuseAddress(false) == server);
    tu.azzert(!server.isReuseAddress());

    tu.azzert(server.setSoLinger(true) == server);
    tu.azzert(server.isSoLinger());
    tu.azzert(server.setSoLinger(false) == server);
    tu.azzert(!server.isSoLinger());

    tu.azzert(server.setTCPKeepAlive(true) == server);
    tu.azzert(server.isTCPKeepAlive());
    tu.azzert(server.setTCPKeepAlive(false) == server);
    tu.azzert(!server.isTCPKeepAlive());

    tu.azzert(server.setTCPNoDelay(true) == server);
    tu.azzert(server.isTCPNoDelay());
    tu.azzert(server.setTCPNoDelay(false) == server);
    tu.azzert(!server.isTCPNoDelay());

    int rbs = new Random().nextInt(1024 * 1024) + 1;
    tu.azzert(server.setReceiveBufferSize(rbs) == server);
    tu.azzert(server.getReceiveBufferSize() == rbs);

    try {
      server.setReceiveBufferSize(0);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      server.setReceiveBufferSize(-1);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int sbs = new Random().nextInt(1024 * 1024);
    tu.azzert(server.setSendBufferSize(sbs) == server);
    tu.azzert(server.getSendBufferSize() == sbs);

    try {
      server.setSendBufferSize(0);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      server.setSendBufferSize(-1);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int trafficClass = new Random().nextInt(10000000);
    tu.azzert(server.setTrafficClass(trafficClass) == server);
    tu.azzert(server.getTrafficClass() == trafficClass);

    tu.testComplete();

  }

  public void testSimpleGET() {
    testSimpleRequest("GET", true);
  }

  public void testSimplePUT() {
    testSimpleRequest("PUT", true);
  }

  public void testSimplePOST() {
    testSimpleRequest("POST", true);
  }

  public void testSimpleDELETE() {
    testSimpleRequest("DELETE", true);
  }

  public void testSimpleHEAD() {
    testSimpleRequest("HEAD", true);
  }

  public void testSimpleTRACE() {
    testSimpleRequest("TRACE", true);
  }

  public void testSimpleCONNECT() {
    testSimpleRequest("CONNECT", true);
  }

  public void testSimpleOPTIONS() {
    testSimpleRequest("OPTIONS", true);
  }

  public void testSimplePATCH() {
    testSimpleRequest("PATCH", true);
  }

  public void testSimpleGETNonSpecific() {
    testSimpleRequest("GET", false);
  }

  public void testSimplePUTNonSpecific() {
    testSimpleRequest("PUT", false);
  }

  public void testSimplePOSTNonSpecific() {
    testSimpleRequest("POST", false);
  }

  public void testSimpleDELETENonSpecific() {
    testSimpleRequest("DELETE", false);
  }

  public void testSimpleHEADNonSpecific() {
    testSimpleRequest("HEAD", false);
  }

  public void testSimpleTRACENonSpecific() {
    testSimpleRequest("TRACE", false);
  }

  public void testSimpleCONNECTNonSpecific() {
    testSimpleRequest("CONNECT", false);
  }

  public void testSimpleOPTIONSNonSpecific() {
    testSimpleRequest("OPTIONS", false);
  }

  public void testSimplePATCHNonSpecific() {
    testSimpleRequest("PATCH", false);
  }

  private void testSimpleRequest(final String method, final boolean specificMethod) {
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        tu.azzert(req.method.equals(method));
        req.response.end();
      }
    });

    getRequest(specificMethod, method, "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    }).end();
  }

  public void testAbsoluteURI() {
    testURIAndPath("http://localhost:8080/this/is/a/path/foo.html", "/this/is/a/path/foo.html");
  }

  public void testRelativeURI() {
    testURIAndPath("/this/is/a/path/foo.html", "/this/is/a/path/foo.html");
  }

  private void testURIAndPath(final String uri, final String path) {
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        tu.azzert(uri.equals(req.uri));
        tu.azzert(path.equals(req.path));
        req.response.end();
      }
    });

    getRequest(true, "GET", uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    }).end();
  }

  public void testParamsAmpersand() {
    testParams('&');
  }

  public void testParamsSemiColon() {
    testParams(';');
  }

  private void testParams(char delim) {
    final Map<String, String> params = genMap(10);
    final String query = generateQueryString(params, delim);
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        tu.azzert(query.equals(req.query));
        tu.azzert(req.getParams().size() == params.size());
        for (Map.Entry<String, String> entry : req.getParams().entrySet()) {
          tu.azzert(entry.getValue().equals(params.get(entry.getKey())));
        }
        req.response.end();
      }
    });

    getRequest(true, "GET", "some-uri/?" + query, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    }).end();
  }

  public void testNoParams() {
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        tu.azzert(req.query == null);
        tu.azzert(req.getParams().isEmpty());
        req.response.end();
      }
    });

    getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    }).end();
  }

  public void testDefaultRequestHeaders() {
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        tu.azzert(req.getHeaders().size() == 1);
        tu.azzert(req.getHeader("Host").equals("localhost:8080"));
        tu.azzert(req.getHeaders().get("Host").equals("localhost:8080"));
        req.response.end();
      }
    });

    getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    }).end();
  }

  public void testRequestHeadersPutAll() {
    testRequestHeaders(false);
  }

  public void testRequestHeadersIndividually() {
    testRequestHeaders(true);
  }

  private void testRequestHeaders(boolean individually) {
    final Map<String, String> headers = genMap(10);
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        tu.azzert(req.getHeaders().size() == 1 + headers.size());
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          tu.azzert(entry.getValue().equals(req.getHeader(entry.getKey())));
        }
        req.response.end();
      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    });
    if (individually) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        req.putHeader(header.getKey(), header.getValue());
      }
    } else {
      req.putAllHeaders(headers);
    }
    req.end();
  }

  public void testRequestChaining() {
    // TODO
  }

  public void testUseRequestAfterComplete() {

    final Buffer body = TestUtils.generateRandomBuffer(1000);

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
      }
    });

    HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
      }
    });

    req.end();

    Handler<Void> handler = new SimpleHandler() {
      public void handle() {

      }
    };
    Buffer buff = Buffer.create(0);
    Map<String, String> map = new HashMap<>();

    try {
      req.end();
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.continueHandler(handler);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.drainHandler(handler);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.end("foo");
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.end(buff);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.end("foo", "UTF-8");
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.exceptionHandler(new Handler<Exception>() {
        public void handle(Exception e) {
        }
      });
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.putAllHeaders(map);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.putHeader("foo", "bar");
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.sendHead();
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.setChunked(false);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.setWriteQueueMaxSize(123);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.write(buff);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.write("foo");
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.write("foo", "UTF-8");
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    try {
      req.write(buff, handler);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }

    try {
      req.write("foo", handler);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }

    try {
      req.write("foo", "UTF-8", handler);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }

    try {
      req.writeBuffer(buff);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }

    try {
      req.writeQueueFull();
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }

    tu.testComplete();
  }

  public void testRequestBodyBufferAtEnd() {

    final Buffer body = TestUtils.generateRandomBuffer(1000);

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(body, buff));
            tu.testComplete();
          }
        });
      }
    });

    HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    });

    req.end(body);
  }


  public void testRequestBodyStringDefaultEncodingAtEnd() {
    testRequestBodyStringAtEnd(null);
  }

  public void testRequestBodyStringUTF8AtEnd() {
    testRequestBodyStringAtEnd("UTF-8");
  }

  public void testRequestBodyStringUTF16AtEnd() {
    testRequestBodyStringAtEnd("UTF-16");
  }

  private void testRequestBodyStringAtEnd(String encoding) {

    final String body = TestUtils.randomUnicodeString(1000);
    final Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = Buffer.create(body);
    } else {
      bodyBuff = Buffer.create(body, encoding);
    }

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(bodyBuff, buff));
            tu.testComplete();
          }
        });
      }
    });

    HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    });

    if (encoding == null) {
      req.end(body);
    } else {
      req.end(body, encoding);
    }
  }

  public void testRequestBodyWriteNonChunked() {

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
          }
        });
      }
    });

    HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    });

    try {
      req.write("foo");
      tu.azzert(false, "Should throw exception");
    } catch (IllegalStateException e) {
      //OK
    }
    tu.testComplete();
  }

  public void testRequestBodyWriteBufferChunked() {
    testRequestBodyWriteBuffer(true, false);
  }

  public void testRequestBodyWriteBufferNonChunked() {
    testRequestBodyWriteBuffer(false, false);
  }

  public void testRequestBodyWriteBufferChunkedCompletion() {
    testRequestBodyWriteBuffer(true, true);
  }

  public void testRequestBodyWriteBufferNonChunkedCompletion() {
    testRequestBodyWriteBuffer(false, true);
  }

  private void testRequestBodyWriteBuffer(boolean chunked, boolean waitCompletion) {

    final Buffer body = Buffer.create(0);

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(body, buff));
            tu.testComplete();
          }
        });
      }
    });

    HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
      }
    });

    final int numWrites = 10;
    final int chunkSize = 100;

    if (chunked) {
      req.setChunked(true);
    } else {
      req.putHeader("Content-Length", numWrites * chunkSize);
    }
    if (waitCompletion) {
      writeChunk(numWrites, chunkSize, req, body);
    } else {
      for (int i = 0; i < numWrites; i++) {
        Buffer b = TestUtils.generateRandomBuffer(chunkSize);
        body.appendBuffer(b);
        req.write(b);
      }
      req.end();
    }

  }

  private void writeChunk(final int remaining, final int chunkSize, final HttpClientRequest req, final Buffer totBuffer) {
    if (remaining > 0) {
      Buffer b = TestUtils.generateRandomBuffer(chunkSize);
      totBuffer.appendBuffer(b);
      req.write(b, new SimpleHandler() {
        public void handle() {
          writeChunk(remaining - 1, chunkSize, req, totBuffer);
        }
      });
    } else {
      req.end();
    }
  }

  public void testRequestBodyWriteStringChunkedDefaultEncoding() {
    testRequestBodyWriteString(true, false, null);
  }

  public void testRequestBodyWriteStringChunkedUTF8() {
    testRequestBodyWriteString(true, false, "UTF-8");
  }

  public void testRequestBodyWriteStringChunkedUTF16() {
    testRequestBodyWriteString(true, false, "UTF-16");
  }

  public void testRequestBodyWriteStringNonChunkedDefaultEncoding() {
    testRequestBodyWriteString(false, false, null);
  }

  public void testRequestBodyWriteStringNonChunkedUTF8() {
    testRequestBodyWriteString(false, false, "UTF-8");
  }

  public void testRequestBodyWriteStringNonChunkedUTF16() {
    testRequestBodyWriteString(false, false, "UTF-16");
  }

  public void testRequestBodyWriteStringChunkedDefaultEncodingCompletion() {
    testRequestBodyWriteString(true, true, null);
  }

  public void testRequestBodyWriteStringChunkedUTF8Completion() {
    testRequestBodyWriteString(true, true, "UTF-8");
  }

  public void testRequestBodyWriteStringChunkedUTF16Completion() {
    testRequestBodyWriteString(true, true, "UTF-16");
  }

  public void testRequestBodyWriteStringNonChunkedDefaultEncodingCompletion() {
    testRequestBodyWriteString(false, true, null);
  }

  public void testRequestBodyWriteStringNonChunkedUTF8Completion() {
    testRequestBodyWriteString(false, true, "UTF-8");
  }

  public void testRequestBodyWriteStringNonChunkedUTF16Completion() {
    testRequestBodyWriteString(false, true, "UTF-16");
  }

  private void testRequestBodyWriteString(boolean chunked, boolean waitCompletion, String encoding) {

    String body = TestUtils.randomUnicodeString(1000);
    final Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = Buffer.create(body);
    } else {
      bodyBuff = Buffer.create(body, encoding);
    }

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(bodyBuff, buff));
            tu.testComplete();
          }
        });
      }
    });

    final HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    });

    if (chunked) {
      req.setChunked(true);
    } else {
      req.putHeader("Content-Length", bodyBuff.length());
    }
    if (waitCompletion) {
      Handler<Void> doneHandler = new SimpleHandler() {
        public void handle() {
          req.end();
        }
      };
      if (encoding == null) {
        req.write(body, doneHandler);
      } else {
        req.write(body, encoding, doneHandler);
      }
    } else {
      if (encoding == null) {
        req.write(body);
      } else {
        req.write(body, encoding);
      }
      req.end();
    }
  }


  public void testRequestWriteBuffer() {

    final Buffer body = TestUtils.generateRandomBuffer(1000);

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(body, buff));
            tu.testComplete();
          }
        });
      }
    });

    HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    });
    req.setChunked(true);
    req.writeBuffer(body);
    req.end();
  }

  // Response

  public void testDefaultStatus() {
    testStatusCode(-1, null);
  }

  public void testOtherStatus() {
    // Doesn't really matter which one we choose
    testStatusCode(405, null);
  }

  public void testStatusMessage() {
    testStatusCode(404, "some message");
  }

  private void testStatusCode(final int code, final String statusMessage) {
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        if (code != -1) {
          req.response.statusCode = code;
        }
        if (statusMessage != null) {
          req.response.statusMessage = statusMessage;
        }
        req.response.end();
      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        if (code != -1) {
          tu.azzert(resp.statusCode == code);
        } else {
          tu.azzert(resp.statusCode == 200);
        }
        if (statusMessage != null) {
          tu.azzert(statusMessage.equals(resp.statusMessage));
        }
        tu.testComplete();
      }
    });

    req.end();
  }

  public void testResponseHeadersPutAll() {
    testResponseHeaders(false);
  }

  public void testResponseHeadersIndividually() {
    testResponseHeaders(true);
  }

  private void testResponseHeaders(final boolean individually) {
    final Map<String, String> headers = genMap(10);
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        if (individually) {
          for (Map.Entry<String, String> header : headers.entrySet()) {
            req.response.putHeader(header.getKey(), header.getValue());
          }
        } else {
          req.response.putAllHeaders(headers);
        }
        req.response.end();
      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.azzert(resp.getHeaders().size() == headers.size() + 1);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          tu.azzert(entry.getValue().equals(resp.getHeader(entry.getKey())));
        }
        tu.testComplete();
      }
    });

    req.end();
  }

  public void testResponseTrailersPutAll() {
    testResponseTrailers(false);
  }

  public void testResponseTrailersPutIndividually() {
    testResponseTrailers(true);
  }

  private void testResponseTrailers(final boolean individually) {
    final Map<String, String> trailers = genMap(10);
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.response.setChunked(true);
        if (individually) {
          for (Map.Entry<String, String> header : trailers.entrySet()) {
            req.response.putTrailer(header.getKey(), header.getValue());
          }
        } else {
          req.response.putAllTrailers(trailers);
        }
        req.response.end();
      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse resp) {
        check.check();
        resp.endHandler(new SimpleHandler() {
          public void handle() {
            tu.azzert(resp.getTrailers().size() == trailers.size());
            for (Map.Entry<String, String> entry : trailers.entrySet()) {
              tu.azzert(entry.getValue().equals(resp.getTrailer(entry.getKey())));
            }
            tu.testComplete();
          }
        });
      }
    });

    req.end();
  }

  public void testResponseNoTrailers() {
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.response.setChunked(true);
        req.response.end();
      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse resp) {
        check.check();
        resp.endHandler(new SimpleHandler() {
          public void handle() {
            tu.azzert(resp.getTrailers().isEmpty());
            tu.testComplete();
          }
        });
      }
    });
    req.end();
  }

  public void testResponseSetTrailerNonChunked() {
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        try {
          req.response.putTrailer("foo", "bar");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          req.response.putAllTrailers(new HashMap<String, Object>());
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        tu.testComplete();
      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse resp) {
      }
    });
    req.end();
  }


  public void testUseResponseAfterComplete() {

    final Buffer body = TestUtils.generateRandomBuffer(1000);

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();

        Handler<Void> handler = new SimpleHandler() {
          public void handle() {

          }
        };
        Buffer buff = Buffer.create(0);
        Map<String, String> map = new HashMap<>();

        HttpServerResponse resp = req.response;

        resp.end();

        try {
          resp.drainHandler(handler);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }

        try {
          resp.end();
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.end(true);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.end("foo");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.end(buff);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.end("foo", "UTF-8");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.end("foo", true);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.end(buff, true);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.end("foo", "UTF-8", true);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.exceptionHandler(new Handler<Exception>() {
            public void handle(Exception e) {
            }
          });
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.putAllHeaders(map);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.putHeader("foo", "bar");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.setChunked(false);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.setWriteQueueMaxSize(123);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.write(buff);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.write("foo");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.write("foo", "UTF-8");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          resp.write(buff, handler);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }

        try {
          resp.write("foo", handler);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }

        try {
          resp.write("foo", "UTF-8", handler);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }

        try {
          resp.writeBuffer(buff);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }

        try {
          resp.writeQueueFull();
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }

        try {
          resp.sendFile("asokdasokd");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }

        try {
          resp.putAllTrailers(map);
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }

        tu.testComplete();

      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
      }
    });

    req.end();


  }

  public void testResponseBodyBufferAtEnd() {

    final Buffer body = TestUtils.generateRandomBuffer(1000);

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.response.end(body);
      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        resp.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(body, buff));
            tu.testComplete();
          }
        });
      }
    });

    req.end();
  }


  public void testResponseBodyStringDefaultEncodingAtEnd() {
    testResponseBodyStringAtEnd(null);
  }

  public void testResponseBodyStringUTF8AtEnd() {
    testResponseBodyStringAtEnd("UTF-8");
  }

  public void testResponseBodyStringUTF16AtEnd() {
    testResponseBodyStringAtEnd("UTF-16");
  }

  private void testResponseBodyStringAtEnd(final String encoding) {

    final String body = TestUtils.randomUnicodeString(1000);
    final Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = Buffer.create(body);
    } else {
      bodyBuff = Buffer.create(body, encoding);
    }

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();

        if (encoding == null) {
          req.response.end(body);
        } else {
          req.response.end(body, encoding);
        }
      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        resp.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(bodyBuff, buff));
            tu.testComplete();
          }
        });
      }
    });

    req.end();
  }

  public void testResponseBodyWriteStringNonChunked() {

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        try {
          req.response.write("foo");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
          tu.testComplete();
        }
      }
    });

    getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        tu.testComplete();
      }
    }).end();
  }

  public void testResponseBodyWriteBufferChunked() {
    testResponseBodyWriteBuffer(true, false);
  }

  public void testResponseBodyWriteBufferNonChunked() {
    testResponseBodyWriteBuffer(false, false);
  }

  public void testResponseBodyWriteBufferChunkedCompletion() {
    testResponseBodyWriteBuffer(true, true);
  }

  public void testResponseBodyWriteBufferNonChunkedCompletion() {
    testResponseBodyWriteBuffer(false, true);
  }

  private void testResponseBodyWriteBuffer(final boolean chunked, final boolean waitCompletion) {

    final Buffer body = Buffer.create(0);

    final int numWrites = 10;
    final int chunkSize = 100;

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();

        if (chunked) {
          req.response.setChunked(true);
        } else {
          req.response.putHeader("Content-Length", numWrites * chunkSize);
        }
        if (waitCompletion) {
          writeChunk(numWrites, chunkSize, req.response, body);
        } else {
          for (int i = 0; i < numWrites; i++) {
            Buffer b = TestUtils.generateRandomBuffer(chunkSize);
            body.appendBuffer(b);
            req.response.write(b);
          }
          req.response.end();
        }
      }
    });

    HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        resp.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(body, buff));
            tu.testComplete();
          }
        });
      }
    });
    req.end();
  }

  private void writeChunk(final int remaining, final int chunkSize, final HttpServerResponse resp, final Buffer totBuffer) {
    if (remaining > 0) {
      Buffer b = TestUtils.generateRandomBuffer(chunkSize);
      totBuffer.appendBuffer(b);
      resp.write(b, new SimpleHandler() {
        public void handle() {
          writeChunk(remaining - 1, chunkSize, resp, totBuffer);
        }
      });
    } else {
      resp.end();
    }
  }

  public void testResponseBodyWriteStringChunkedDefaultEncoding() {
    testResponseBodyWriteString(true, false, null);
  }

  public void testResponseBodyWriteStringChunkedUTF8() {
    testResponseBodyWriteString(true, false, "UTF-8");
  }

  public void testResponseBodyWriteStringChunkedUTF16() {
    testResponseBodyWriteString(true, false, "UTF-16");
  }

  public void testResponseBodyWriteStringNonChunkedDefaultEncoding() {
    testResponseBodyWriteString(false, false, null);
  }

  public void testResponseBodyWriteStringNonChunkedUTF8() {
    testResponseBodyWriteString(false, false, "UTF-8");
  }

  public void testResponseBodyWriteStringNonChunkedUTF16() {
    testResponseBodyWriteString(false, false, "UTF-16");
  }

  public void testResponseBodyWriteStringChunkedDefaultEncodingCompletion() {
    testResponseBodyWriteString(true, true, null);
  }

  public void testResponseBodyWriteStringChunkedUTF8Completion() {
    testResponseBodyWriteString(true, true, "UTF-8");
  }

  public void testResponseBodyWriteStringChunkedUTF16Completion() {
    testResponseBodyWriteString(true, true, "UTF-16");
  }

  public void testResponseBodyWriteStringNonChunkedDefaultEncodingCompletion() {
    testResponseBodyWriteString(false, true, null);
  }

  public void testResponseBodyWriteStringNonChunkedUTF8Completion() {
    testResponseBodyWriteString(false, true, "UTF-8");
  }

  public void testResponseBodyWriteStringNonChunkedUTF16Completion() {
    testResponseBodyWriteString(false, true, "UTF-16");
  }

  private void testResponseBodyWriteString(final boolean chunked, final boolean waitCompletion, final String encoding) {

    final String body = TestUtils.randomUnicodeString(1000);
    final Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = Buffer.create(body);
    } else {
      bodyBuff = Buffer.create(body, encoding);
    }

    startServer(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        check.check();
        if (chunked) {
          req.response.setChunked(true);
        } else {
          req.response.putHeader("Content-Length", bodyBuff.length());
        }
        if (waitCompletion) {
          Handler<Void> doneHandler = new SimpleHandler() {
            public void handle() {
              req.response.end();
            }
          };
          if (encoding == null) {
            req.response.write(body, doneHandler);
          } else {
            req.response.write(body, encoding, doneHandler);
          }
        } else {
          if (encoding == null) {
            req.response.write(body);
          } else {
            req.response.write(body, encoding);
          }
          req.response.end();
        }
      }
    });

    final HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        resp.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(bodyBuff, buff));
            tu.testComplete();
          }
        });
      }
    });
    req.end();

  }


  public void testResponseWriteBuffer() {

    final Buffer body = TestUtils.generateRandomBuffer(1000);

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();
        req.response.setChunked(true);
        req.response.writeBuffer(body);
        req.response.end();
      }
    });

    HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        check.check();
        resp.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(body, buff));
            tu.testComplete();
          }
        });
      }
    });
    req.end();
  }

  public void testPipelining() {

    final int requests = 100;

    startServer(new Handler<HttpServerRequest>() {
      int count;
      public void handle(final HttpServerRequest req) {
        tu.azzert(count == Integer.parseInt(req.getHeader("count")));
        final int theCount = count;
        count++;
        req.response.setChunked(true);
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(final Buffer buff) {
            tu.azzert(("This is content " + theCount).equals(buff.toString()), buff.toString());
            //We write the response back after a random time to increase the chances of responses written in the
            //wrong order if we didn't implement pipelining correctly
            Vertx.instance.setTimer((long) (10 * Math.random()), new Handler<Long>() {
              public void handle(Long timerID) {
                req.response.putHeader("count", String.valueOf(theCount));
                req.response.write(buff);
                req.response.end();
              }
            });
          }
        });
      }
    });

    for (int count = 0; count < requests; count++) {
      final int theCount = count;
      HttpClientRequest req = client.request("POST", "some-uri", new Handler<HttpClientResponse>() {
        public void handle(final HttpClientResponse response) {
          tu.azzert(theCount == Integer.parseInt(response.getHeader("count")), theCount + ":" + response.getHeader
              ("count"));
          response.bodyHandler(new Handler<Buffer>() {
            public void handle(Buffer buff) {
              tu.azzert(("This is content " + theCount).equals(buff.toString()));
              if (theCount == requests - 1) {
                tu.testComplete();
              }
            }
          });
        }
      });
      req.setChunked(true);
      req.putHeader("count", String.valueOf(count));
      req.write("This is content " + count);
      req.end();
    }
  }

  public void testSendFile() throws Exception {
    final String content = TestUtils.randomUnicodeString(10000);
    final File file = setupFile("test-send-file.dat", content);

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.sendFile(file.getAbsolutePath());
      }
    });

    client.getNow("some-uri", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse response) {
        tu.azzert(response.statusCode == 200);
        tu.azzert(file.length() == Long.valueOf(response.getHeader("Content-Length")));
        response.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(content.equals(buff.toString()));
            file.delete();
            tu.testComplete();
          }
        });
      }
    });
  }

  private File setupFile(String fileName, String content) throws Exception {
    fileName = "./" + fileName;
    File file = new File(fileName);
    if (file.exists()) {
      file.delete();
    }
    BufferedWriter out = new BufferedWriter(new FileWriter(file));
    out.write(content);
    out.close();
    return file;
  }

  public void test100ContinueDefault() throws Exception {
    final Buffer toSend = TestUtils.generateRandomBuffer(1000);
    startServer(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            check.check();
            tu.azzert(TestUtils.buffersEqual(toSend, data));
            req.response.end();
          }
        });
      }
    });
    final HttpClientRequest req = client.put("someurl", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        resp.endHandler(new SimpleHandler() {
          public void handle() {
            check.check();
            tu.testComplete();
          }
        });
      }
    });
    req.putHeader("Expect", "100-continue");
    req.setChunked(true);
    req.continueHandler(new SimpleHandler() {
      public void handle() {
        check.check();
        req.write(toSend);
        req.end();
      }
    });
    req.sendHead();
  }

  public void test100ContinueHandled() throws Exception {

    final Buffer toSend = TestUtils.generateRandomBuffer(1000);

    startServer(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        req.response.putHeader("HTTP/1.1", "100 Continue");
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            check.check();
            tu.azzert(TestUtils.buffersEqual(toSend, data));
            req.response.end();
          }
        });
      }
    });

    final HttpClientRequest req = client.put("someurl", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        resp.endHandler(new SimpleHandler() {
          public void handle() {
            check.check();
            tu.testComplete();
          }
        });
      }
    });

    req.putHeader("Expect", "100-continue");
    req.setChunked(true);
    req.continueHandler(new SimpleHandler() {
      public void handle() {
        check.check();
        req.write(toSend);
        req.end();
      }
    });
    req.sendHead();
  }

  public void testClientDrainHandler() {
    final HttpClientRequest req = client.get("someurl", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
      }
    });
    req.setChunked(true);
    tu.azzert(!req.writeQueueFull());
    req.setWriteQueueMaxSize(1000);
    final Buffer buff = TestUtils.generateRandomBuffer(10000);
    Vertx.instance.setPeriodic(0, new Handler<Long>() {
      public void handle(Long id) {
        check.check();
        req.write(buff);
        if (req.writeQueueFull()) {
          Vertx.instance.cancelTimer(id);
          req.drainHandler(new SimpleHandler() {
            public void handle() {
              check.check();
              tu.azzert(!req.writeQueueFull());
              tu.testComplete();
            }
          });

          // Tell the server to resume
          EventBus.instance.send(new Message("server_resume"));
        }
      }
    });
  }

  public void testServerDrainHandler() {
    final HttpClientRequest req = client.get("someurl", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse resp) {
        resp.pause();
        final Handler<Message> resumeHandler = new Handler<Message>() {
          public void handle(Message message) {
            check.check();
            resp.resume();
          }
        };
        EventBus.instance.registerHandler("client_resume", resumeHandler);
        resp.endHandler(new SimpleHandler() {
          public void handle() {
            check.check();
            EventBus.instance.unregisterHandler("client_resume", resumeHandler);
          }
        });
        resp.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
          }
        });
      }
    });
    req.end();
  }

  public void testPooling() throws Exception {
    testPooling(true);
  }

  public void testPoolingNoKeepAlive() throws Exception {
    testPooling(false);
  }

  private void testPooling(final boolean keepAlive) throws Exception {
    final String path = "foo.txt";
    final int numGets = 1000;
    int maxPoolSize = 10;
    client.setKeepAlive(keepAlive).setMaxPoolSize(maxPoolSize);
    for (int i = 0; i < numGets; i++) {
      final int theCount = i;
      HttpClientRequest req = client.get(path, new Handler<HttpClientResponse>() {
        public void handle(final HttpClientResponse response) {
          tu.azzert(response.statusCode == 200);
          tu.azzert(theCount == Integer.parseInt(response.getHeader("count")));
          if (theCount == numGets - 1) {
            tu.testComplete();
          }
        }
      });
      req.putHeader("count", i);
      req.end();
    }
  }

  public void testTLSClientTrustAll() {
    tls();
  }

  public void testTLSClientTrustServerCert() {
    tls();
  }

  public void testTLSClientUntrustedServer() {
    tls();
  }

  public void testTLSClientCertNotRequired() {
    tls();
  }

  public void testTLSClientCertRequired() {
    tls();
  }

  public void testTLSClientCertRequiredNoClientCert() {
    tls();
  }

  public void testTLSClientCertClientNotTrusted() {
    tls();
  }

  private void tls() {
    TLSTestParams params = SharedData.<String, TLSTestParams>getMap("TLSTest").get("params");

    client.setSSL(true);

    if (params.clientTrustAll) {
      client.setTrustAll(true);
    }

    if (params.clientTrust) {
      client.setTrustStorePath("./src/tests/keystores/client-truststore.jks")
          .setTrustStorePassword("wibble");
    }
    if (params.clientCert) {
      client.setKeyStorePath("./src/tests/keystores/client-keystore.jks")
          .setKeyStorePassword("wibble");
    }

    final ContextChecker check = new ContextChecker(tu);

    final boolean shouldPass = params.shouldPass;

    client.exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
        if (shouldPass) {
          tu.azzert(false, "Should not throw exception");
        } else {
          tu.testComplete();
        }
      }
    });

    client.setPort(4043);

    HttpClientRequest req = client.get("someurl", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse response) {
        check.check();
        response.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            tu.azzert("bar".equals(data.toString()));
          }
        });
        tu.testComplete();
      }
    });
    req.exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
        System.out.println("Request exception handler called");
      }
    });
    req.end("foo");
  }

  public void testConnectInvalidPort() {
    final ContextChecker check = new ContextChecker(tu);
    client.exceptionHandler(createNoConnectHandler(check));
    client.setPort(9998);
    client.getNow("someurl", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        tu.azzert(false, "Connect should not be called");
      }
    });
  }

  public void testConnectInvalidHost() {
    final ContextChecker check = new ContextChecker(tu);
    client.exceptionHandler(createNoConnectHandler(check));
    client.setHost("wibble");
    client.getNow("someurl", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        tu.azzert(false, "Connect should not be called");
      }
    });
  }

  Handler<Exception> createNoConnectHandler(final ContextChecker check) {
    return new Handler<Exception>() {
      public void handle(Exception e) {
        check.check();
        tu.testComplete();
      }
    };
  }

  public void testSharedServersMultipleInstances1() {
    //Make sure connections aren't reused
    client.setKeepAlive(false);
    // Make a bunch of requests
    final int numRequests = SharedData.<String, Integer>getMap("params").get("numRequests");
    final AtomicInteger counter = new AtomicInteger(0);
    for (int i = 0; i < numRequests; i++) {

      client.getNow("someurl", new Handler<HttpClientResponse>() {
        public void handle(HttpClientResponse resp) {
          int count = counter.incrementAndGet();
          if (count == numRequests) {
            tu.testComplete();
          }
        }
      });
    }
  }

  public void testSharedServersMultipleInstances2() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances3() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances1StartAllStopAll() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances2StartAllStopAll() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances3StartAllStopAll() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances1StartAllStopSome() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances2StartAllStopSome() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances3StartAllStopSome() {
    testSharedServersMultipleInstances1();
  }


  // -------------------------------------------------------------------------------------------

  private String generateQueryString(Map<String, String> params, char delim) {
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (Map.Entry<String, String> param : params.entrySet()) {
      sb.append(param.getKey()).append("=").append(param.getValue());
      if (++count != params.size()) {
        sb.append(delim);
      }
    }
    return sb.toString();
  }

  private HttpClientRequest getRequest(boolean specificMethod, String method, String uri,
                                       Handler<HttpClientResponse> responseHandler) {
    HttpClientRequest req = null;
    if (specificMethod) {
      if ("GET".equals(method)) {
        req = client.get(uri, responseHandler);
      } else if ("POST".equals(method)) {
        req = client.post(uri, responseHandler);
      } else if ("PUT".equals(method)) {
        req = client.put(uri, responseHandler);
      } else if ("HEAD".equals(method)) {
        req = client.head(uri, responseHandler);
      } else if ("DELETE".equals(method)) {
        req = client.delete(uri, responseHandler);
      } else if ("TRACE".equals(method)) {
        req = client.trace(uri, responseHandler);
      } else if ("CONNECT".equals(method)) {
        req = client.connect(uri, responseHandler);
      } else if ("OPTIONS".equals(method)) {
        req = client.options(uri, responseHandler);
      } else if ("PATCH".equals(method)) {
        req = client.patch(uri, responseHandler);
      }
    } else {
      req = client.request(method, uri, responseHandler);
    }
    return req;
  }

  private Map<String, String> genMap(int num) {
    Map<String, String> map = new HashMap<String, String>();
    for (int i = 0; i < num; i++) {
      String key;
      do {
        key = TestUtils.randomAlphaString(1 + (int) ((19) * Math.random()));
      } while (map.containsKey(key));
      map.put(key, TestUtils.randomAlphaString(1 + (int) ((19) * Math.random())));
    }
    return map;
  }

}

