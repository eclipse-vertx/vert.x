/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vertx.tests.core.http;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.*;
import org.vertx.java.core.http.impl.HttpHeadersAdapter;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.testframework.TestClientBase;
import org.vertx.java.testframework.TestUtils;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpTestClient extends TestClientBase {

  private HttpClient client;
  private HttpServer server;

  @Override
  public void start() {
    super.start();
    tu.appReady();
    client = vertx.createHttpClient().setHost("localhost").setPort(8080);
  }

  @Override
  public void stop() {
    client.close();
    if (server != null) {
      server.close(new AsyncResultHandler<Void>() {
        public void handle(AsyncResult<Void> result) {
          tu.checkThread();
          HttpTestClient.super.stop();
        }
      });
    } else {
      super.stop();
    }
  }

  private void startServer(Handler<HttpServerRequest> serverHandler, AsyncResultHandler<HttpServer> handler) {
    server = vertx.createHttpServer();
    server.requestHandler(serverHandler);
    server.listen(8080, "localhost", handler);
  }

  public void testListenInvalidPort() {
    server = vertx.createHttpServer();
    server.requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {

      }
    });
    server.listen(1128371831, new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.failed());
        tu.azzert(ar.cause() != null);
        tu.testComplete();
      }
    });
  }

  public void testListenInvalidHost() {
    server = vertx.createHttpServer();
    server.requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {

      }
    });
    server.listen(80, "iqwjdoqiwjdoiqwdiojwd", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.failed());
        tu.azzert(ar.cause() != null);
        tu.testComplete();
      }
    });
  }

  public void testPauseClientResponse() {

    final HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
      boolean paused;
      Buffer totBuff = new Buffer();
      public void handle(final HttpClientResponse resp) {
        tu.checkThread();
        resp.pause();
        paused = true;
        resp.dataHandler(new Handler<Buffer>() {
          @Override
          public void handle(Buffer chunk) {
            if (paused) {
              tu.azzert(false, "Shouldn't receive chunks when paused");
            } else {
              totBuff.appendBuffer(chunk);
            }
          }
        });
        resp.endHandler(new VoidHandler() {
          @Override
          protected void handle() {
            if (paused) {
              tu.azzert(false, "Shouldn't receive chunks when paused");
            } else {
              tu.azzert(totBuff.length() == 1000);
              tu.testComplete();
            }
          }
        });
        vertx.setTimer(500, new Handler<Long>() {
          @Override
          public void handle(Long event) {
            paused = false;
            resp.resume();
          }
        });
      }
    });

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        req.end();
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.response().setChunked(true);
        // Send back a big response in several chunks
        for (int i = 0; i < 10; i++) {
          req.response().write(TestUtils.generateRandomBuffer(100));
        }
        req.response().end();
      }
    }, handler);
  }

  public void testClientDefaults() {
    tu.azzert(!client.isSSL());
    tu.azzert(client.isVerifyHost());
    tu.azzert(client.getKeyStorePassword() == null);
    tu.azzert(client.getKeyStorePath() == null);
    tu.azzert(client.getTrustStorePassword() == null);
    tu.azzert(client.getTrustStorePath() == null);
    tu.testComplete();
  }

  public void testClientAttributes() {

    tu.azzert(client.setSSL(false) == client);
    tu.azzert(!client.isSSL());

    tu.azzert(client.setSSL(true) == client);
    tu.azzert(client.isSSL());

    tu.azzert(client.setVerifyHost(false) == client);
    tu.azzert(!client.isVerifyHost());

    tu.azzert(client.setVerifyHost(true) == client);
    tu.azzert(client.isVerifyHost());

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

    tu.azzert(client.setSoLinger(10) == client);
    tu.azzert(client.getSoLinger() == 10);

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
    HttpServer server = vertx.createHttpServer();
    tu.azzert(!server.isSSL());
    tu.azzert(server.getKeyStorePassword() == null);
    tu.azzert(server.getKeyStorePath() == null);
    tu.azzert(server.getTrustStorePassword() == null);
    tu.azzert(server.getTrustStorePath() == null);
    tu.azzert(server.isReuseAddress());
    server.close();
    tu.testComplete();
  }

  public void testServerAttributes() {

    HttpServer server = vertx.createHttpServer();

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

    tu.azzert(server.setSoLinger(10) == server);
    tu.azzert(server.getSoLinger() == 10);

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

  public void testClientChaining() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = client.put("someurl", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {

          }
        });
        tu.azzert(req.setChunked(true) == req);
        tu.azzert(req.sendHead() == req);
        tu.azzert(req.write("foo", "UTF-8") == req);
        tu.azzert(req.write("foo") == req);
        tu.azzert(req.write(new Buffer("foo")) == req);
        tu.testComplete();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {

      }
    }, handler);
  }

  public void testServerChainingSendFile() throws Exception {
    testServerChaining(true);
  }

  public void testServerChaining() throws Exception {
    testServerChaining(false);
  }

  private void testServerChaining(final boolean sendFile) throws Exception {
    final File file;
    if (sendFile) {
      file = setupFile("test-server-chaining.dat", "blah");
    } else {
      file = null;
    }
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = client.put("someurl", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
          }
        });
        req.end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        if (sendFile) {
          tu.azzert(req.response().sendFile(file.getAbsolutePath()) == req.response());
          file.delete();
        } else {
          tu.azzert(req.response().setChunked(true) == req.response());
          tu.azzert(req.response().write("foo", "UTF-8") == req.response());
          tu.azzert(req.response().write("foo") == req.response());
        }
        tu.testComplete();
      }
    }, handler);

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
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        getRequest(specificMethod, method, "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        }).end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        tu.azzert(req.method().equals(method));
        req.response().end();
      }
    }, handler);

  }

  public void testHeadNoBody() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        getRequest(true, "HEAD", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.azzert(Integer.valueOf(resp.headers().get("Content-Length")) == 41);
            resp.endHandler(new VoidHandler() {
              @Override
              protected void handle() {
                tu.testComplete();
              }
            });

          }
        }).end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        tu.azzert(req.method().equals("HEAD"));
        // Head never contains a body but it can contain a Content-Length header
        // Since headers from HEAD must correspond EXACTLY with corresponding headers for GET
        req.response().headers().set("Content-Length", String.valueOf(41));
        req.response().end();
      }
    }, handler);

  }

  public void testAbsoluteURI() {
    testURIAndPath("http://localhost:8080/this/is/a/path/foo.html", "/this/is/a/path/foo.html");
  }

  public void testRelativeURI() {
    testURIAndPath("/this/is/a/path/foo.html", "/this/is/a/path/foo.html");
  }

  private void testURIAndPath(final String uri, final String path) {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        getRequest(true, "GET", uri, new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        }).end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        tu.azzert(uri.equals(req.uri()));
        tu.azzert(path.equals(req.path()));
        req.response().end();
      }
    }, handler);

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
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        getRequest(true, "GET", "some-uri/?" + query, new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        }).end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        tu.azzert(query.equals(req.query()));
        tu.azzert(req.params().size() == params.size());
        for (Map.Entry<String, String> entry : req.params()) {
          tu.azzert(entry.getValue().equals(params.get(entry.getKey())));
        }
        req.response().end();
      }
    }, handler);
  }

  public void testNoParams() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        }).end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        tu.azzert(req.query() == null);
        tu.azzert(req.params().isEmpty());
        req.response().end();
      }
    }, handler);
  }

  public void testDefaultRequestHeaders() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        }).end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        tu.azzert(req.headers().size() == 1);
        tu.azzert(req.headers().get("host").equals("localhost:8080"));
        tu.azzert(req.headers().get("host").equals("localhost:8080"));
        req.response().end();
      }
    }, handler);

  }

  public void testRequestHeadersPutAll() {
    testRequestHeaders(false);
  }

  public void testRequestHeadersIndividually() {
    testRequestHeaders(true);
  }

  private void testRequestHeaders(final boolean individually) {
    final MultiMap headers = getHeaders(10);

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        });
        if (individually) {
          for (Map.Entry<String, String> header : headers) {
            req.headers().add(header.getKey(), header.getValue());
          }
        } else {
          req.headers().set(headers);
        }
        req.end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        tu.azzert(req.headers().size() == 1 + headers.size());
        for (Map.Entry<String, String> entry : headers) {
          tu.azzert(entry.getValue().equals(req.headers().get(entry.getKey())));
        }
        req.response().end();
      }
    }, handler);
  }

  public void testLowerCaseHeaders() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());

        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.azzert(resp.headers().get("Quux").equals("quux"));
            tu.azzert(resp.headers().get("quux").equals("quux"));
            tu.azzert(resp.headers().get("qUUX").equals("quux"));
            tu.azzert(resp.headers().contains("Quux"));
            tu.azzert(resp.headers().contains("quux"));
            tu.azzert(resp.headers().contains("qUUX"));
            tu.checkThread();
            tu.testComplete();
          }
        });
        req.putHeader("Foo", "foo");
        tu.azzert(req.headers().get("Foo").equals("foo"));
        tu.azzert(req.headers().get("foo").equals("foo"));
        tu.azzert(req.headers().get("fOO").equals("foo"));
        tu.azzert(req.headers().contains("Foo"));
        tu.azzert(req.headers().contains("foo"));
        tu.azzert(req.headers().contains("fOO"));
        req.end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        tu.azzert(req.headers().get("Foo").equals("foo"));
        tu.azzert(req.headers().get("foo").equals("foo"));
        tu.azzert(req.headers().get("fOO").equals("foo"));
        tu.azzert(req.headers().contains("Foo"));
        tu.azzert(req.headers().contains("foo"));
        tu.azzert(req.headers().contains("fOO"));
        req.response().putHeader("Quux", "quux");
        tu.azzert(req.response().headers().get("Quux").equals("quux"));
        tu.azzert(req.response().headers().get("quux").equals("quux"));
        tu.azzert(req.response().headers().get("qUUX").equals("quux"));
        tu.azzert(req.response().headers().contains("Quux"));
        tu.azzert(req.response().headers().contains("quux"));
        tu.azzert(req.response().headers().contains("qUUX"));
        req.response().end();
      }
    }, handler);
  }

  public void testRequestChaining() {
    // TODO
  }

  public void testRequestTimeoutExtendedWhenResponseChunksReceived() {
    final long timeout = 500;
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClientRequest req = getRequest(true, "GET", "timeoutTest", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.azzert(resp.statusCode() == 200);
            resp.endHandler(new Handler<Void>() {
              public void handle(Void event) {
                tu.testComplete();
              }
            });
          }
        });
        req.exceptionHandler(new Handler<Throwable>() {
          @Override
          public void handle(Throwable t) {
            tu.azzert(false, "Should not be called");
          }
        });
        req.setTimeout(timeout);
        req.end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      int numChunks = 10;
      int count = 0;
      long interval = timeout * 2 / numChunks;

      public void handle(final HttpServerRequest req) {
        //Send chunks so total request > timeout but each chunk < timeout
        req.response().setChunked(true);
        vertx.setPeriodic(interval, new Handler<Long>() {
          @Override
          public void handle(Long timerID) {
            req.response().write("foo");
            if (++count == numChunks) {
              req.response().end();
              vertx.cancelTimer(timerID);
            }
          }
        });
      }
    }, handler);
  }

  public void testRequestTimesoutWhenIndicatedPeriodExpiresWithoutAResponseFromRemoteServer() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClientRequest req = getRequest(true, "GET", "timeoutTest", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.azzert(false, "End should not be called because the request should timeout");
          }
        });
        req.exceptionHandler( new Handler<Throwable>() {
          @Override
          public void handle(Throwable t) {
            tu.azzert(t instanceof TimeoutException, "Expected to end with timeout exception but ended with other exception: " + t);
            tu.checkThread();
            tu.testComplete();
          }
        });
        req.setTimeout(1000);
        req.end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        // Don't answer the request, causing a timeout
      }
    }, handler);

  }

  public void testRequestTimeoutCanceledWhenRequestHasAnOtherError() {

    final AtomicReference<Throwable> exception = new AtomicReference<>();
    // There is no server running, should fail to connect
    final HttpClientRequest req = getRequest(true, "GET", "timeoutTest", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        tu.azzert(false, "End should not be called because the request should fail to connect");
      }
    });
    req.exceptionHandler( new Handler<Throwable>() {
      @Override
      public void handle(Throwable event) {
        exception.set(event);
      }
    });
    req.setTimeout(800);
    req.end();

    getVertx().setTimer(1500, new Handler<Long>() {
      @Override
      public void handle(Long event) {
        tu.azzert(exception.get() != null, "Expected an exception to be set");
        tu.azzert(!(exception.get() instanceof TimeoutException), 
        		"Expected to not end with timeout exception, but did: " + exception.get());
        tu.checkThread();
        tu.testComplete();
      }
    });
  }

  public void testRequestTimeoutCanceledWhenRequestEndsNormally() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());

        final AtomicReference<Throwable> exception = new AtomicReference<>();

        // There is no server running, should fail to connect
        final HttpClientRequest req = getRequest(true, "GET", "timeoutTest", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            // Don't do anything
          }
        });
        req.exceptionHandler( new Handler<Throwable>() {
          @Override
          public void handle(Throwable event) {
            exception.set(event);
          }
        });
        req.setTimeout(500);
        req.end();

        getVertx().setTimer(1000, new Handler<Long>() {
          @Override
          public void handle(Long event) {
            tu.azzert(exception.get() == null, "Did not expect any exception");
            tu.checkThread();
            tu.testComplete();
          }
        });
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response().setStatusCode(200);
        req.response().end("OK");
      }
    }, handler);
  }

  public void testRequestNotReceivedIfTimedout() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClientRequest req = getRequest(true, "GET", "timeoutTest", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.azzert(false, "Response should not be handled");
          }
        });
        req.exceptionHandler( new Handler<Throwable>() {
          @Override
          public void handle(Throwable event) {
            tu.azzert(event instanceof TimeoutException, "Expected to end with timeout exception but ended with other exception: " + event);
            //Delay a bit to let any response come back
            vertx.setTimer(500, new Handler<Long>() {
              public void handle(Long event) {
                tu.checkThread();
                tu.testComplete();
              }
            });
          }
        });
        req.setTimeout(100);
        req.end();
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        // Answer the request after a delay
        vertx.setTimer(500, new Handler<Long>() {
          public void handle(Long event) {
            req.response().setStatusCode(200);
            req.response().end("OK");
          }
        });
      }
    }, handler);

  }

  public void testUseRequestAfterComplete() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());

        HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
          }
        });

        req.end();

        Buffer buff = new Buffer();

        try {
          req.end();
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          req.continueHandler(new VoidHandler() {
            @Override
            protected void handle() {

            }
          });
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        try {
          req.drainHandler(new VoidHandler() {
            @Override
            protected void handle() {

            }
          });
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
          req.exceptionHandler(new Handler<Throwable>() {
            public void handle(Throwable t) {
            }
          });
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
          req.write(buff);
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
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
      }
    }, handler);
  }

  public void testRequestBodyBufferAtEnd() {
    final Buffer body = TestUtils.generateRandomBuffer(1000);
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());

        HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        });

        req.end(body);
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(body, buff));
            tu.testComplete();
          }
        });
      }
    }, handler);
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

  private void testRequestBodyStringAtEnd(final String encoding) {

    final String body = TestUtils.randomUnicodeString(1000);
    final Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = new Buffer(body);
    } else {
      bodyBuff = new Buffer(body, encoding);
    }

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        });

        if (encoding == null) {
          req.end(body);
        } else {
          req.end(body, encoding);
        }
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(bodyBuff, buff));
            tu.testComplete();
          }
        });
      }
    }, handler);
  }

  public void testRequestBodyWriteNonChunked() {
    final HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        tu.checkThread();
      }
    });

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        try {
          req.write("foo");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
        }
        tu.testComplete();
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
          }
        });
      }
    }, handler);

  }



  public void testRequestBodyWriteBufferChunked() {
    testRequestBodyWriteBuffer(true);
  }

  public void testRequestBodyWriteBufferNonChunked() {
    testRequestBodyWriteBuffer(false);
  }

  private void testRequestBodyWriteBuffer(final boolean chunked) {
    final Buffer body = new Buffer();
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
          }
        });

        final int numWrites = 10;
        final int chunkSize = 100;

        if (chunked) {
          req.setChunked(true);
        } else {
          req.headers().set("Content-Length", String.valueOf(numWrites * chunkSize));
        }
        for (int i = 0; i < numWrites; i++) {
          Buffer b = TestUtils.generateRandomBuffer(chunkSize);
          body.appendBuffer(b);
          req.write(b);
        }
        req.end();
      };
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(body, buff));
            tu.testComplete();
          }
        });
      }
    }, handler);
  }

  public void testRequestBodyWriteStringChunkedDefaultEncoding() {
    testRequestBodyWriteString(true, null);
  }

  public void testRequestBodyWriteStringChunkedUTF8() {
    testRequestBodyWriteString(true, "UTF-8");
  }

  public void testRequestBodyWriteStringChunkedUTF16() {
    testRequestBodyWriteString(true, "UTF-16");
  }

  public void testRequestBodyWriteStringNonChunkedDefaultEncoding() {
    testRequestBodyWriteString(false, null);
  }

  public void testRequestBodyWriteStringNonChunkedUTF8() {
    testRequestBodyWriteString(false, "UTF-8");
  }

  public void testRequestBodyWriteStringNonChunkedUTF16() {
    testRequestBodyWriteString(false, "UTF-16");
  }

  private void testRequestBodyWriteString(final boolean chunked, final String encoding) {

    final String body = TestUtils.randomUnicodeString(1000);
    final Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = new Buffer(body);
    } else {
      bodyBuff = new Buffer(body, encoding);
    }

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        });

        if (chunked) {
          req.setChunked(true);
        } else {
          req.headers().set("Content-Length", String.valueOf(bodyBuff.length()));
        }

        if (encoding == null) {
          req.write(body);
        } else {
          req.write(body, encoding);
        }
        req.end();

      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(bodyBuff, buff));
            tu.testComplete();
          }
        });
      }

    }, handler);
  }

  public void testRequestWriteBuffer() {
    final Buffer body = TestUtils.generateRandomBuffer(1000);

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.testComplete();
          }
        });
        req.setChunked(true);
        req.write(body);
        req.end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            tu.azzert(TestUtils.buffersEqual(body, buff));
            tu.testComplete();
          }
        });
      }
    }, handler);
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
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            if (code != -1) {
              tu.azzert(resp.statusCode() == code);
            } else {
              tu.azzert(resp.statusCode() == 200);
            }
            if (statusMessage != null) {
              tu.azzert(statusMessage.equals(resp.statusMessage()));
            }
            tu.testComplete();
          }
        });

        req.end();
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        if (code != -1) {
          req.response().setStatusCode(code);
        }
        if (statusMessage != null) {
          req.response().setStatusMessage(statusMessage);
        }
        req.response().end();
      }
    }, handler);
  }

  public void testResponseHeadersPutAll() {
    testResponseHeaders(false);
  }

  public void testResponseHeadersIndividually() {
    testResponseHeaders(true);
  }

  private void testResponseHeaders(final boolean individually) {
    final MultiMap headers = getHeaders(10);
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
            tu.azzert(resp.headers().size() == headers.size() + 1);
            for (Map.Entry<String, String> entry : headers) {
              tu.azzert(entry.getValue().equals(resp.headers().get(entry.getKey())));
            }
            tu.testComplete();
          }
        });

        req.end();
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        if (individually) {
          for (Map.Entry<String, String> header : headers) {
            req.response().headers().add(header.getKey(), header.getValue());
          }
        } else {
          req.response().headers().set(headers);
        }
        req.response().end();
      }
    }, handler);
  }

  public void testResponseTrailersPutAll() {
    testResponseTrailers(false);
  }

  public void testResponseTrailersPutIndividually() {
    testResponseTrailers(true);
  }

  private void testResponseTrailers(final boolean individually) {
    final MultiMap trailers = getHeaders(10);
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(final HttpClientResponse resp) {
            tu.checkThread();
            resp.endHandler(new VoidHandler() {
              public void handle() {
                tu.azzert(resp.trailers().size() == trailers.size());
                for (Map.Entry<String, String> entry : trailers) {
                  tu.azzert(entry.getValue().equals(resp.trailers().get(entry.getKey())));
                }
                tu.testComplete();
              }
            });
          }
        });

        req.end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.response().setChunked(true);
        if (individually) {
          for (Map.Entry<String, String> header : trailers) {
            req.response().trailers().add(header.getKey(), header.getValue());
          }
        } else {
          req.response().trailers().set(trailers);
        }
        req.response().end();
      }

    }, handler);

  }

  public void testResponseNoTrailers() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(final HttpClientResponse resp) {
            tu.checkThread();
            resp.endHandler(new VoidHandler() {
              public void handle() {
                tu.azzert(resp.trailers().isEmpty());
                tu.testComplete();
              }
            });
          }
        });
        req.end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.response().setChunked(true);
        req.response().end();
      }
    }, handler);
  }

  public void testResponseMultipleSetCookieInHeader() {
    testResponseMultipleSetCookie(true, false);
  }

  public void testResponseMultipleSetCookieInTrailer() {
    testResponseMultipleSetCookie(false, true);
  }

  public void testResponseMultipleSetCookieInHeaderAndTrailer() {
    testResponseMultipleSetCookie(true, true);
  }

  private void testResponseMultipleSetCookie(final boolean inHeader, final boolean inTrailer) {
    final List<String> cookies = new ArrayList<>();
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(final HttpClientResponse resp) {
            tu.checkThread();
            resp.endHandler(new VoidHandler() {
              public void handle() {
                tu.azzert(resp.cookies().size() == cookies.size());
                for (int i = 0; i < cookies.size(); ++i) {
                  tu.azzert(cookies.get(i).equals(resp.cookies().get(i)));
                }
                tu.testComplete();
              }
            });
          }
        });

        req.end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        if (inHeader) {
          final List<String> headers = new ArrayList<>();
          headers.add("h1=h1v1");
          headers.add("h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT");
          cookies.addAll(headers);
          req.response().headers().set("Set-Cookie", headers);
        }
        if (inTrailer) {
          req.response().setChunked(true);
          final List<String> trailers = new ArrayList<>();
          trailers.add("t1=t1v1");
          trailers.add("t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT");
          cookies.addAll(trailers);
          req.response().trailers().set("Set-Cookie", trailers);
        }
        req.response().end();
      }
    }, handler);
  }

  public void testUseResponseAfterComplete() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
          }
        });

        req.end();
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();

        Handler<Void> handler = new VoidHandler() {
          public void handle() {
          }
        };

        AsyncResultHandler<Void> writeHandler = new AsyncResultHandler<Void>() {
          public void handle(AsyncResult<Void> res) {
          }
        };
        
        Buffer buff = new Buffer();
        HttpServerResponse resp = req.response();
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
          resp.exceptionHandler(new Handler<Throwable>() {
            public void handle(Throwable t) {
            }
          });
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
          resp.write(buff);
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

        tu.testComplete();
      }
    }, handler);
  }

  public void testResponseBodyBufferAtEnd() {
    final Buffer body = TestUtils.generateRandomBuffer(1000);

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
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
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.response().end(body);
      }
    }, handler);

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
      bodyBuff = new Buffer(body);
    } else {
      bodyBuff = new Buffer(body, encoding);
    }

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
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
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();

        if (encoding == null) {
          req.response().end(body);
        } else {
          req.response().end(body, encoding);
        }
      }
    }, handler);

  }

  public void testResponseBodyWriteStringNonChunked() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
          }
        }).end();
      }
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        try {
          req.response().write("foo");
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //OK
          tu.testComplete();
        }
      }
    }, handler);

  }

  public void testResponseBodyWriteBufferChunked() {
    testResponseBodyWriteBuffer(true);
  }

  public void testResponseBodyWriteBufferNonChunked() {
    testResponseBodyWriteBuffer(false);
  }

  private void testResponseBodyWriteBuffer(final boolean chunked) {

    final Buffer body = new Buffer();

    final int numWrites = 10;
    final int chunkSize = 100;

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
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
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();

        if (chunked) {
          req.response().setChunked(true);
        } else {
          req.response().headers().set("Content-Length", String.valueOf(numWrites * chunkSize));
        }

        for (int i = 0; i < numWrites; i++) {
          Buffer b = TestUtils.generateRandomBuffer(chunkSize);
          body.appendBuffer(b);
          req.response().write(b);
        }
        req.response().end();

      }
    }, handler);

  }

  public void testResponseBodyWriteStringChunkedDefaultEncoding() {
    testResponseBodyWriteString(true, null);
  }

  public void testResponseBodyWriteStringChunkedUTF8() {
    testResponseBodyWriteString(true, "UTF-8");
  }

  public void testResponseBodyWriteStringChunkedUTF16() {
    testResponseBodyWriteString(true, "UTF-16");
  }

  public void testResponseBodyWriteStringNonChunkedDefaultEncoding() {
    testResponseBodyWriteString(false, null);
  }

  public void testResponseBodyWriteStringNonChunkedUTF8() {
    testResponseBodyWriteString(false, "UTF-8");
  }

  public void testResponseBodyWriteStringNonChunkedUTF16() {
    testResponseBodyWriteString(false, "UTF-16");
  }

  private void testResponseBodyWriteString(final boolean chunked, final String encoding) {

    final String body = TestUtils.randomUnicodeString(1000);
    final Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = new Buffer(body);
    } else {
      bodyBuff = new Buffer(body, encoding);
    }

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClientRequest req = getRequest(true, "GET", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
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
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        tu.checkThread();
        if (chunked) {
          req.response().setChunked(true);
        } else {
          req.response().headers().set("Content-Length", String.valueOf(bodyBuff.length()));
        }
        if (encoding == null) {
          req.response().write(body);
        } else {
          req.response().write(body, encoding);
        }
        req.response().end();
      }

    }, handler);

  }

  public void testResponseWriteBuffer() {
    final Buffer body = TestUtils.generateRandomBuffer(1000);

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = getRequest(true, "POST", "some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            tu.checkThread();
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
    };
    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        tu.checkThread();
        req.response().setChunked(true);
        req.response().write(body);
        req.response().end();
      }
    }, handler);
  }

  public void testPipelining() {
    final int requests = 100;

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        for (int count = 0; count < requests; count++) {
          final int theCount = count;
          HttpClientRequest req = client.request("POST", "some-uri", new Handler<HttpClientResponse>() {
            public void handle(final HttpClientResponse response) {
              tu.azzert(theCount == Integer.parseInt(response.headers().get("count")), theCount + ":" + response.headers().get("count"));
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
          req.headers().set("count", String.valueOf(count));
          req.write("This is content " + count);
          req.end();
        }
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      int count;

      public void handle(final HttpServerRequest req) {
        tu.azzert(count == Integer.parseInt(req.headers().get("count")));
        final int theCount = count;
        count++;
        req.response().setChunked(true);
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(final Buffer buff) {
            tu.azzert(("This is content " + theCount).equals(buff.toString()), buff.toString());
            //We write the response back after a random time to increase the chances of responses written in the
            //wrong order if we didn't implement pipelining correctly
            vertx.setTimer(1 + (long) (10 * Math.random()), new Handler<Long>() {
              public void handle(Long timerID) {
                req.response().headers().set("count", String.valueOf(theCount));
                req.response().write(buff);
                req.response().end();
              }
            });
          }
        });
      }
    }, handler);
  }

  public void testSendFile() throws Exception {
    final String content = TestUtils.randomUnicodeString(10000);
    final File file = setupFile("test-send-file.html", content);

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.getNow("some-uri", new Handler<HttpClientResponse>() {
          public void handle(final HttpClientResponse response) {
            tu.azzert(response.statusCode() == 200);
            tu.azzert(file.length() == Long.valueOf(response.headers().get("content-length")));
            tu.azzert("text/html".equals(response.headers().get("content-type")));
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
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response().sendFile(file.getAbsolutePath());
      }
    }, handler);
  }

  public void testSendFileNotFound() throws Exception {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.getNow("some-uri", new Handler<HttpClientResponse>() {
          public void handle(final HttpClientResponse response) {
            tu.azzert(response.statusCode() == 404);
            tu.azzert("text/html".equals(response.headers().get("content-type")));
            response.bodyHandler(new Handler<Buffer>() {
              public void handle(Buffer buff) {
                tu.azzert("<html><body>Resource not found</body><html>".equals(buff.toString()));
                tu.testComplete();
              }
            });
          }
        });
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response().sendFile("doesnotexist.html");
      }
    }, handler);
  }

  public void testSendFileNotFoundWith404Page() throws Exception {
    final String content = "<html><body>This is my 404 page</body></html>";
    final File file = setupFile("my-404-page.html", content);
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.getNow("some-uri", new Handler<HttpClientResponse>() {
          public void handle(final HttpClientResponse response) {
            tu.azzert(response.statusCode() == 404);
            tu.azzert("text/html".equals(response.headers().get("content-type")));
            response.bodyHandler(new Handler<Buffer>() {
              public void handle(Buffer buff) {
                tu.azzert(content.equals(buff.toString()));
                tu.testComplete();
              }
            });
          }
        });
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response().sendFile("doesnotexist.html", file.getAbsolutePath());
      }
    }, handler);
  }

  public void testSendFileOverrideHeaders() throws Exception {
    final String content = TestUtils.randomUnicodeString(10000);
    final File file = setupFile("test-send-file.html", content);

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.getNow("some-uri", new Handler<HttpClientResponse>() {
          public void handle(final HttpClientResponse response) {
            tu.azzert(response.statusCode() == 200);
            tu.azzert(file.length() == Long.valueOf(response.headers().get("content-length")));
            tu.azzert("wibble".equals(response.headers().get("content-type")));
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
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response().putHeader("Content-Type", "wibble");
        req.response().sendFile(file.getAbsolutePath());
      }
    }, handler);
  }

  private File setupFile(String fileName, String content) throws Exception {
    fileName = "./" + fileName;
    File file = new File(fileName);
    if (file.exists()) {
      file.delete();
    }
    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    out.write(content);
    out.close();
    return file;
  }

  public void testSetHandlersAfterListening() throws Exception {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        try {
          server.requestHandler(new Handler<HttpServerRequest>() {
            public void handle(HttpServerRequest req) {
            }
          });
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //Ok
        }
        try {
          server.websocketHandler(new Handler<ServerWebSocket>() {
            public void handle(ServerWebSocket ws) {
            }
          });
          tu.azzert(false, "Should throw exception");
        } catch (IllegalStateException e) {
          //Ok
        }
        tu.testComplete();
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
      }
    }, handler);
  }

  public void test100ContinueDefault() throws Exception {
    final Buffer toSend = TestUtils.generateRandomBuffer(1000);

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClientRequest req = client.put("someurl", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            resp.endHandler(new VoidHandler() {
              public void handle() {
                tu.checkThread();
                tu.testComplete();
              }
            });
          }
        });
        req.headers().set("Expect", "100-continue");
        req.setChunked(true);
        req.continueHandler(new VoidHandler() {
          public void handle() {
            tu.checkThread();
            req.write(toSend);
            req.end();
          }
        });
        req.sendHead();
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            tu.checkThread();
            tu.azzert(TestUtils.buffersEqual(toSend, data));
            req.response().end();
          }
        });
      }
    }, handler);
  }

  public void test100ContinueHandled() throws Exception {

    final Buffer toSend = TestUtils.generateRandomBuffer(1000);

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClientRequest req = client.put("someurl", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            resp.endHandler(new VoidHandler() {
              public void handle() {
                tu.checkThread();
                tu.testComplete();
              }
            });
          }
        });

        req.headers().set("Expect", "100-continue");
        req.setChunked(true);
        req.continueHandler(new VoidHandler() {
          public void handle() {
            tu.checkThread();
            req.write(toSend);
            req.end();
          }
        });
        req.sendHead();
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        req.response().headers().set("HTTP/1.1", "100 Continue");
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            tu.checkThread();
            tu.azzert(TestUtils.buffersEqual(toSend, data));
            req.response().end();
          }
        });
      }
    }, handler);
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
    vertx.setPeriodic(1, new Handler<Long>() {
      public void handle(Long id) {
        tu.checkThread();
        req.write(buff);
        if (req.writeQueueFull()) {
          vertx.cancelTimer(id);
          req.drainHandler(new VoidHandler() {
            public void handle() {
              tu.checkThread();
              tu.azzert(!req.writeQueueFull());
              tu.testComplete();
            }
          });

          // Tell the server to resume
          vertx.eventBus().send("server_resume", "");
        }
      }
    });
  }

  public void testServerDrainHandler() {
    final HttpClientRequest req = client.get("someurl", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse resp) {
        resp.pause();
        final Handler<Message<Buffer>> resumeHandler = new Handler<Message<Buffer>>() {
          public void handle(Message<Buffer> message) {
            tu.checkThread();
            resp.resume();
          }
        };
        vertx.eventBus().registerHandler("client_resume", resumeHandler);
        resp.endHandler(new VoidHandler() {
          public void handle() {
            tu.checkThread();
            vertx.eventBus().unregisterHandler("client_resume", resumeHandler);
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
    final AtomicInteger cnt = new AtomicInteger(0);
    for (int i = 0; i < numGets; i++) {
      final int theCount = i;
      HttpClientRequest req = client.get(path, new Handler<HttpClientResponse>() {
        public void handle(final HttpClientResponse response) {
          tu.azzert(response.statusCode() == 200);
          tu.azzert(theCount == Integer.parseInt(response.headers().get("count")));
          if (cnt.incrementAndGet() == numGets) {
            tu.testComplete();
          }
        }
      });
      req.headers().set("count", String.valueOf(i));
      req.end();
    }
  }

  public void testConnectionErrorsGetReportedToRequest() {

    final AtomicInteger clientExceptions = new AtomicInteger();
    final AtomicInteger req2Exceptions = new AtomicInteger();
    final AtomicInteger req3Exceptions = new AtomicInteger();

    final Handler<String> checkEndHandler = new Handler<String>() {
      public void handle(final String name) {
        if (clientExceptions.get() == 1 && req2Exceptions.get() ==1  && req3Exceptions.get() ==1) {
          tu.checkThread();
          tu.testComplete();
        }
      }
    };

    client.setPort(9998); // this simulates a connection error immediately
    client.exceptionHandler(new Handler<Throwable>() {
      public void handle(Throwable event) {
        tu.azzert(clientExceptions.incrementAndGet() == 1, "More than more call to client exception handler was not expected");
        checkEndHandler.handle("Client");
      }
    });

    // This one should cause an error in the Client Exception handler, because it hasno exception handler set specifically.
    final HttpClientRequest req1 = client.get("someurl1", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse response) {
        tu.azzert(false, "Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
      }
    });
    // No exception handler set on request!

    final HttpClientRequest req2 = client.get("someurl2", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse response) {
        tu.azzert(false, "Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
      }
    });
    req2.exceptionHandler(new Handler<Throwable>() {
      public void handle(Throwable event) {
        tu.azzert(req2Exceptions.incrementAndGet() == 1, "More than more call to req2 exception handler was not expected");
        checkEndHandler.handle("Request2");
      }
    });

    final HttpClientRequest req3 = client.get("someurl2", new Handler<HttpClientResponse>() {
      public void handle(final HttpClientResponse response) {
        tu.azzert(false, "Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
      }
    });
    req3.exceptionHandler(new Handler<Throwable>() {
      public void handle(Throwable event) {
        tu.azzert(req3Exceptions.incrementAndGet() == 1, "More than more call to req2 exception handler was not expected");
        checkEndHandler.handle("Request3");
      }
    });


    req1.end();
    req2.end();
    req3.end();
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
    TLSTestParams params = TLSTestParams.deserialize(vertx.sharedData().<String, byte[]>getMap("TLSTest").get("params"));

    client.setSSL(true);

    if (params.clientTrustAll) {
      client.setTrustAll(true);
    }

    if (params.clientTrust) {
      client.setTrustStorePath("./src/test/keystores/client-truststore.jks")
          .setTrustStorePassword("wibble");
    }
    if (params.clientCert) {
      client.setKeyStorePath("./src/test/keystores/client-keystore.jks")
          .setKeyStorePassword("wibble");
    }

    final boolean shouldPass = params.shouldPass;

    client.exceptionHandler(new Handler<Throwable>() {
      public void handle(Throwable t) {
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
        tu.checkThread();
        response.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            tu.azzert("bar".equals(data.toString()));
          }
        });
        tu.testComplete();
      }
    });
    // NOTE: If you set a request handler now and an error happens on the request, the error is reported to the
    // request handler and NOT the client handler. Only if no handler is set on the request, or an error happens
    // that is not in the context of a request will the client handler get called. I can't figure out why an empty
    // handler was specified here originally, but if we want the client handler (specified above) to fire, we should
    // not set an empty handler here. The alternative would be to move the logic
//    req.exceptionHandler(new Handler<Throwable>() {
//      public void handle(Throwable t) {
//      }
//    });
    req.end("foo");
  }

  public void testConnectInvalidPort() {
    client.exceptionHandler(createNoConnectHandler());
    client.setPort(9998);
    client.getNow("someurl", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        tu.azzert(false, "Connect should not be called");
      }
    });
  }

  public void testConnectInvalidHost() {
    client.exceptionHandler(createNoConnectHandler());
    client.setHost("wibble");
    client.getNow("someurl", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        tu.azzert(false, "Connect should not be called");
      }
    });
  }

  Handler<Throwable> createNoConnectHandler() {
    return new Handler<Throwable>() {
      public void handle(Throwable t) {
        tu.checkThread();
        tu.testComplete();
      }
    };
  }

  public void testSharedServersMultipleInstances1() {
    //Make sure connections aren't reused
    if (client.isKeepAlive()) {
      client.setKeepAlive(false);
    }
    // Make a bunch of requests
    final int numRequests = vertx.sharedData().<String, Integer>getMap("params").get("numRequests");
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

  public void testRemoteAddress() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.getNow("/", new Handler<HttpClientResponse>() {
          @Override
          public void handle(HttpClientResponse resp) {
            tu.testComplete();
          }
        });
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      @Override
      public void handle(HttpServerRequest request) {
        tu.azzert(request.remoteAddress().getHostName().startsWith("localhost"));
        request.response().end();
      }
    }, handler);

  }

  public void testGetAbsoluteURI() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.getNow("/foo", new Handler<HttpClientResponse>() {
          @Override
          public void handle(HttpClientResponse resp) {
            tu.testComplete();
          }
        });
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      @Override
      public void handle(HttpServerRequest request) {
        String uri = request.absoluteURI().toString();
        tu.azzert("http://localhost:8080/foo".equals(uri));
        request.response().end();
      }
    }, handler);

  }

  public void testHttpVersion() {
    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.getNow("/foo", new Handler<HttpClientResponse>() {
          @Override
          public void handle(HttpClientResponse resp) {
            tu.testComplete();
          }
        });
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      @Override
      public void handle(HttpServerRequest request) {
        tu.azzert(request.version() == HttpVersion.HTTP_1_1);
        request.response().end();
      }
    }, handler);
  }

  public void testFormUploadFile() throws Exception {
    final AtomicInteger attributeCount = new AtomicInteger();
    final String content = "Vert.x rocks!";
    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      @Override
      public void handle(final HttpServerRequest req) {
        if (req.uri().startsWith("/form")) {
          req.response().setChunked(true);
          req.uploadHandler(new Handler<HttpServerFileUpload>() {
            @Override
            public void handle(final HttpServerFileUpload event) {
              event.dataHandler(new Handler<Buffer>() {
                @Override
                public void handle(Buffer buffer) {
                  tu.azzert(content.equals(buffer.toString("UTF-8")));
                }
              });
              tu.azzert(event.name().equals("file"));
              tu.azzert(event.filename().equals("tmp-0.txt"));
              tu.azzert(event.contentType().equals("image/gif"));
            }
          });
          req.endHandler(new VoidHandler() {
            protected void handle() {
              MultiMap attrs = req.formAttributes();
              attributeCount.set(attrs.size());
              req.response().end();
            }
          });
        }
      }
    }).listen(8080, "0.0.0.0", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = vertx.createHttpClient().setPort(8080).post("/form", new Handler<HttpClientResponse>() {
          @Override
          public void handle(HttpClientResponse resp) {
            // assert the response
            tu.azzert(200 == resp.statusCode());
            resp.bodyHandler(new Handler<Buffer>() {
              public void handle(Buffer body) {
                tu.azzert(0 == body.length());
              }
            });
            tu.azzert(0 == attributeCount.get());
            tu.testComplete();
          }
        });

        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        Buffer buffer = new Buffer();
        final String body =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                        "Content-Type: image/gif\r\n" +
                        "\r\n" +
                        content + "\r\n" +
                        "--" + boundary + "--\r\n";

        buffer.appendString(body);
        req.headers().set("content-length", String.valueOf(buffer.length()));
        req.headers().set("content-type", "multipart/form-data; boundary=" + boundary);
        req.write(buffer).end();
      }
    });
  }

  public void testFormUploadAttributes() throws Exception {
    final AtomicInteger attributeCount = new AtomicInteger();
    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        if (req.uri().startsWith("/form")) {
          req.response().setChunked(true);
          req.uploadHandler(new Handler<HttpServerFileUpload>() {
            @Override
            public void handle(final HttpServerFileUpload event) {
              event.dataHandler(new Handler<Buffer>() {
                @Override
                public void handle(Buffer buffer) {
                  tu.azzert(false);
                }
              });
            }
          });
          req.endHandler(new VoidHandler() {
            protected void handle() {
              MultiMap attrs = req.formAttributes();
              attributeCount.set(attrs.size());
              System.out.println("attr is " + attrs.get("framework"));
              tu.azzert(attrs.get("framework").equals("vert x"));
              tu.azzert(attrs.get("runson").equals("jvm"));
              req.response().end();
            }
          });
        }
      }
    }).listen(8080, "0.0.0.0", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = vertx.createHttpClient().setPort(8080).post("/form", new Handler<HttpClientResponse>() {
          @Override
          public void handle(HttpClientResponse resp) {
            // assert the response
            tu.azzert(200 == resp.statusCode());
            resp.bodyHandler(new Handler<Buffer>() {
              public void handle(Buffer body) {
                tu.azzert(0 == body.length());
              }
            });
            tu.azzert(2 == attributeCount.get());
            tu.testComplete();
          }
        });
        try {
          Buffer buffer = new Buffer();
          // Make sure we have one param that needs url encoding
          buffer.appendString("framework=" + URLEncoder.encode("vert x", "UTF-8") + "&runson=jvm", "UTF-8");
          req.headers().set("content-length", String.valueOf(buffer.length()));
          req.headers().set("content-type", "application/x-www-form-urlencoded");
          req.write(buffer).end();
        } catch (UnsupportedEncodingException e) {
          tu.azzert(false, e.getMessage());
        }
      }
    });
  }

  public void testFormUploadAttributes2() throws Exception {
    final AtomicInteger attributeCount = new AtomicInteger();
    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        if (req.uri().startsWith("/form")) {
          req.response().setChunked(true);
          req.uploadHandler(new Handler<HttpServerFileUpload>() {
            @Override
            public void handle(final HttpServerFileUpload event) {
              event.dataHandler(new Handler<Buffer>() {
                @Override
                public void handle(Buffer buffer) {
                  tu.azzert(false);
                }
              });
            }
          });
          req.endHandler(new VoidHandler() {
            protected void handle() {
              MultiMap attrs = req.formAttributes();
              attributeCount.set(attrs.size());
              tu.azzert(attrs.get("origin").equals("junit-testUserAlias"));
              tu.azzert(attrs.get("login").equals("admin@foo.bar"));
              tu.azzert(attrs.get("pass word").equals("admin"));
              req.response().end();
            }
          });
        }
      }
    }).listen(8080, "0.0.0.0", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        HttpClientRequest req = vertx.createHttpClient().setPort(8080).post("/form", new Handler<HttpClientResponse>() {
          @Override
          public void handle(HttpClientResponse resp) {
            // assert the response
            tu.azzert(200 == resp.statusCode());
            resp.bodyHandler(new Handler<Buffer>() {
              public void handle(Buffer body) {
                tu.azzert(0 == body.length());
              }
            });
            tu.azzert(3 == attributeCount.get());
            tu.testComplete();
          }
        });
        Buffer buffer = new Buffer();
        buffer.appendString("origin=junit-testUserAlias&login=admin%40foo.bar&pass+word=admin");
        req.headers().set("content-length", String.valueOf(buffer.length()));
        req.headers().set("content-type", "application/x-www-form-urlencoded");
        req.write(buffer).end();
      }
    });
  }


  public void testAccessNetSocket() throws Exception {

    final Buffer toSend = TestUtils.generateRandomBuffer(1000);

    AsyncResultHandler<HttpServer> handler = new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClientRequest req = client.get("someurl", new Handler<HttpClientResponse>() {
          public void handle(final HttpClientResponse resp) {
            resp.endHandler(new Handler<Void>() {
              @Override
              public void handle(Void event) {
                tu.checkThread();

                NetSocket socket = resp.netSocket();
                tu.azzert(socket != null);
                tu.testComplete();
              }
            }) ;
          }
        });
        req.headers().set("content-length", String.valueOf(toSend.length()));
        req.write(toSend);
      }
    };

    startServer(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        req.response().headers().set("HTTP/1.1", "101 Upgrade");
        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            tu.checkThread();
            tu.azzert(TestUtils.buffersEqual(toSend, data));
            req.response().end();
          }
        });
      }
    }, handler);
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

  private static MultiMap getHeaders(int num) {
    Map<String, String> map = genMap(num);
    MultiMap headers = new HttpHeadersAdapter(new DefaultHttpHeaders());
    for (Map.Entry<String, String> entry: map.entrySet()) {
      headers.add(entry.getKey(), entry.getValue());
    }
    return headers;
  }

  private static Map<String, String> genMap(int num) {
    Map<String, String> map = new HashMap<String, String>();
    for (int i = 0; i < num; i++) {
      String key;
      do {
        key = TestUtils.randomAlphaString(1 + (int) ((19) * Math.random())).toLowerCase();
      } while (map.containsKey(key));
      map.put(key, TestUtils.randomAlphaString(1 + (int) ((19) * Math.random())));
    }
    return map;
  }

}


