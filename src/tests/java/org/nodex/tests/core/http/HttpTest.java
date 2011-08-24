/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.tests.core.http;

import org.nodex.core.Nodex;
import org.nodex.core.NodexMain;
import org.nodex.core.TimerHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.http.HttpClient;
import org.nodex.core.http.HttpClientRequest;
import org.nodex.core.http.HttpClientResponse;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpResponseHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerRequest;
import org.omg.CORBA.portable.ResponseHandler;
import org.testng.annotations.Test;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class HttpTest extends TestBase {

  @Test
  public void testGetNow() throws Exception {
    String path = "some/path/to/" + Utils.randomAlphaString(20);
    testHTTP("GETNOW", path, 200, 3, 0, true, 8181, false);
    testHTTP("GETNOW", path, 404, 0, 0, false, 8181, false);
    throwAssertions();
  }

  @Test
  public void testGET() throws Exception {
    testMethod("GET", true);
   // testMethod("GET", false);
    throwAssertions();
  }

  @Test
  public void testPOST() throws Exception {
    testMethod("POST", true);
    testMethod("POST", false);
    throwAssertions();
  }

  @Test
  public void testPUT() throws Exception {
    testMethod("PUT", true);
    testMethod("PUT", false);
    throwAssertions();
  }

  @Test
  public void testHEAD() throws Exception {
    testMethod("HEAD", true);
    testMethod("HEAD", false);
    throwAssertions();
  }

  @Test
  public void testOPTIONS() throws Exception {
    testMethod("OPTIONS", true);
    testMethod("OPTIONS", false);
    throwAssertions();
  }

  @Test
  public void testDELETE() throws Exception {
    testMethod("DELETE", true);
    testMethod("DELETE", false);
    throwAssertions();
  }

  @Test
  public void testTRACE() throws Exception {
    testMethod("TRACE", true);
    testMethod("TRACE", false);
    throwAssertions();
  }

  //@Test
  public void testPipelining() throws Exception {
    final String host = "localhost";
    final boolean keepAlive = true;
    final String method = "GET";
    final String path = "/some/path/foo.html";
    final int statusCode = 200;
    final int port = 8181;
    final int requests = 100;

    final CountDownLatch latch = new CountDownLatch(requests);

    new NodexMain() {
      public void go() throws Exception {

        final HttpServer server = new HttpServer(new HttpRequestHandler() {
          int count;

          public void onRequest(final HttpServerRequest req) {
            azzert(method.equals(req.method));
            azzert(path.equals(req.path));
            azzert(count == Integer.parseInt(req.getHeader("count")));
            final int theCount = count;
            count++;
            final Buffer buff = Buffer.create(0);
            req.dataHandler(new DataHandler() {
              public void onData(Buffer data) {
                buff.appendBuffer(data);
              }
            });
            req.endHandler(new Runnable() {
              public void run() {
                azzert(("This is content " + theCount).equals(buff.toString()), buff.toString());
                //We write the response back after a random time to increase the chances of responses written in the
                //wrong order if we didn't implement pipelining correctly
                Nodex.instance.setTimeout((long) (100 * Math.random()), new TimerHandler() {
                  public void onTimer(long timerID) {
                    req.response.putHeader("count", String.valueOf(theCount));
                    req.response.write(buff);
                    req.response.end();
                  }
                });
              }
            });
          }
        }).listen(port, host);

        for (int count = 0; count < requests; count++) {
          final int theCount = count;
          HttpClient client = new HttpClient().setKeepAlive(keepAlive).setPort(port).setHost(host);
          HttpClientRequest req = client.request(method, path, new HttpResponseHandler() {
            public void onResponse(final HttpClientResponse response) {
              //dumpHeaders(response.headers);
              azzert(response.statusCode == statusCode);
              azzert(theCount == Integer.parseInt(response.getHeader("count")), theCount + ":" + response.getHeader
                  ("count"));
              final Buffer buff = Buffer.create(0);
              response.dataHandler(new DataHandler() {
                public void onData(Buffer data) {
                  buff.appendBuffer(data);
                }
              });
              response.endHandler(new Runnable() {
                public void run() {
                  azzert(("This is content " + theCount).equals(buff.toString()));
                  server.close(new Runnable() {
                    public void run() {
                      latch.countDown();
                    }
                  });
                }
              });
            }
          });
          req.putHeader("count", String.valueOf(count));
          req.write("This is content " + count);
          req.end();
          client.close();
        }

      }
    }.run();
    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

  @Test
  public void testSendFile() throws Exception {
    final String host = "localhost";
    final boolean keepAlive = true;
    final String path = "foo.txt";
    final int port = 8181;

    final String content = Utils.randomAlphaString(10000);
    final File file = setupFile(path, content);

    final CountDownLatch latch = new CountDownLatch(1);

    new NodexMain() {
      public void go() throws Exception {

        final HttpServer server = new HttpServer(new HttpRequestHandler() {
          public void onRequest(HttpServerRequest req) {
            azzert(path.equals(req.path));
            //Clearly in a real web server you'd do some safety checks on the path
            String fileName = "./" + req.path;
            req.response.sendFile(fileName);
          }
        }).listen(port, host);

        HttpClient client = new HttpClient().setKeepAlive(keepAlive).setPort(port).setHost(host);

        client.getNow(path, new HttpResponseHandler() {
          public void onResponse(final HttpClientResponse response) {
            dumpHeaders(response);
            azzert(response.statusCode == 200);
            azzert(file.length() == Long.valueOf(response.getHeader("Content-Length")));
            final Buffer buff = Buffer.create(0);
            response.dataHandler(new DataHandler() {
              public void onData(Buffer data) {
                buff.appendBuffer(data);
              }
            });
            response.endHandler(new Runnable() {
              public void run() {
                azzert(content.equals(buff.toString()));
                server.close(new Runnable() {
                  public void run() {
                    latch.countDown();
                  }
                });
              }
            });
          }
        });

        client.close();
      }
    }.run();

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

  @Test
  public void test100Continue() throws Exception {
    final String host = "localhost";
    final int port = 8181;

    final Buffer toSend = Utils.generateRandomBuffer(1000);

    final CountDownLatch latch = new CountDownLatch(1);

    new NodexMain() {
      public void go() throws Exception {

        final HttpServer server = new HttpServer(new HttpRequestHandler() {
          final Buffer received = Buffer.create(0);
          public void onRequest(final HttpServerRequest req) {
            req.dataHandler(new DataHandler() {
              public void onData(Buffer data) {
                received.appendBuffer(data);
                if (received.length() == toSend.length()) {
                  assert(Utils.buffersEqual(toSend, received));
                  req.response.end();
                }
              }
            });
          }
        }).listen(port, host);

        final HttpClient client = new HttpClient().setPort(port).setHost(host);

        final HttpClientRequest req = client.put("someurl", new HttpResponseHandler() {
          public void onResponse(HttpClientResponse resp) {
            assert(200 == resp.statusCode);

            resp.endHandler(new Runnable() {
               public void run() {
                 server.close(new Runnable() {
                   public void run() {
                     client.close();
                     latch.countDown();
                   }
                 });
               }
            });
          }
        });

        req.putHeader("Expect", "100-continue");

        req.continueHandler(new Runnable() {
          public void run() {
            req.write(toSend);
            req.end();
          }
        });

        req.sendHead();
      }
    }.run();

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

  @Test
  public void testPooling() throws Exception {
    testPooling(true);
    testPooling(false);
  }

  private void testPooling(boolean keepAlive) throws Exception {
    testPooling(1, 1, 100, keepAlive);
    testPooling(1000, 1, 100, keepAlive);
    testPooling(10, 1, 100, keepAlive);
    testPooling(10, 10, 100, keepAlive);
  }

  private void testPooling(final int maxPoolSize, final int numContexts, final int numGets, final boolean keepAlive) throws Exception {
    final String host = "localhost";
    final String path = "foo.txt";
    final int port = 8181;

    final CountDownLatch latch = new CountDownLatch(numContexts);

    for (int j = 0; j < numContexts; j++) {
      final int thePort = port + j;
      new NodexMain() {
        public void go() throws Exception {

          final HttpServer server = new HttpServer(new HttpRequestHandler() {
            public void onRequest(HttpServerRequest req) {
              req.response.putHeader("count", req.getHeader("count"));
              req.response.end();
            }
          }).listen(thePort, host);

          final HttpClient client = new HttpClient().setKeepAlive(keepAlive).setPort(thePort).setHost(host).setMaxPoolSize(maxPoolSize);

          for (int i = 0; i < numGets; i++) {
            final int theCount = i;
            HttpClientRequest req = client.get(path, new HttpResponseHandler() {
              public void onResponse(final HttpClientResponse response) {
                azzert(response.statusCode == 200);
                azzert(theCount == Integer.parseInt(response.getHeader("count")));
                if (theCount == numGets - 1) {
                  server.close(new Runnable() {
                    public void run() {
                      latch.countDown();
                    }
                  });
                  client.close();
                }
              }
            });
            req.putHeader("count", i);
            req.end();
          }
        }
      }.run();
    }

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

  private void testMethod(String method, boolean specificMethod) throws Exception {
    String path = "some/path/to/" + Utils.randomAlphaString(20);
    testHTTP(method, path, 404, 0, 0, false, 8181, specificMethod);
    testHTTP(method, path, 404, 0, 3, false, 8181, specificMethod);
    testHTTP(method, path, 200, 3, 0, false, 8181, specificMethod);
    testHTTP(method, path, 200, 3, 0, true, 8181, specificMethod);
    testHTTP(method, path, 200, 3, 3, false, 8181, specificMethod);
    testHTTP(method, path, 200, 3, 3, true, 8181, specificMethod);
  }

  private void testHTTP(final String method, final String path, final int statusCode, final int numResponseChunks,
                        final int numRequestChunks, final boolean setTrailers, final int port,
                        final boolean specificMethod) throws Exception {
    final Map<String, String> requestHeaders = genMap(10);
    final Map<String, String> responseHeaders = genMap(10);
    final Map<String, String> trailers = setTrailers ? genMap(10) : null;
    final Map<String, String> params = genMap(10);
    final String paramsString = toParamsString(params);

    final List<Buffer> responseBody = numResponseChunks == 0 ? null : new ArrayList<Buffer>();
    final Buffer totResponseBody = Buffer.create(0);
    for (int i = 0; i < numResponseChunks; i++) {
      Buffer buff = Utils.generateRandomBuffer(100);
      responseBody.add(buff);
      totResponseBody.appendBuffer(buff);
    }

    final List<Buffer> requestBody = numRequestChunks == 0 ? null : new ArrayList<Buffer>();
    final Buffer totRequestBody = Buffer.create(0);
    for (int i = 0; i < numRequestChunks; i++) {
      Buffer buff = Utils.generateRandomBuffer(100);
      requestBody.add(buff);
      totRequestBody.appendBuffer(buff);
    }

    final String host = "localhost";
    final boolean keepAlive = true;

    final CountDownLatch latch = new CountDownLatch(1);

    final AtomicReference<HttpServer> server;
    final AtomicReference<HttpClient> client;

    new NodexMain() {
      public void go() throws Exception {
        final HttpServer server = new HttpServer(new HttpRequestHandler() {
          public void onRequest(final HttpServerRequest req) {
            azzert((method.equals("GETNOW") ? "GET" : method).equals(req.method), method + ":" + req.method);
            azzert(path.equals(req.path));
            azzert((path + paramsString).equals(req.uri));
            for (Map.Entry<String, String> param : params.entrySet()) {
              azzert(req.getParam(param.getKey()).equals(param.getValue()));
            }
            //dumpHeaders(req.headers);
            assertHeaders(requestHeaders, req);
            azzert(req.getHeader("Host").equals(host + ":" + port));
            azzert(req.getHeader("Connection").equals(keepAlive ? "keep-alive" : "close"));

            final Buffer buff = Buffer.create(0);
            req.dataHandler(new DataHandler() {
              public void onData(Buffer data) {
                buff.appendBuffer(data);
              }
            });
            req.endHandler(new Runnable() {
              public void run() {
                azzert(Utils.buffersEqual(totRequestBody, buff));
                req.response.putAllHeaders(responseHeaders);
                req.response.statusCode = statusCode;
                if (responseBody != null) {
                  for (Buffer chunk : responseBody) {
                    req.response.write(chunk);
                  }
                  if (trailers != null) {
                    req.response.putAllTrailers(trailers);
                  }
                }
                req.response.end();
              }
            });
          }
        }).listen(port, host);

        HttpResponseHandler responseHandler = new HttpResponseHandler() {
          public void onResponse(final HttpClientResponse response) {
            //dumpHeaders(response.headers);
            azzert(response.statusCode == statusCode);
            assertHeaders(responseHeaders, response);
            final Buffer buff = Buffer.create(0);
            response.dataHandler(new DataHandler() {
              public void onData(Buffer data) {
                buff.appendBuffer(data);
              }
            });
            response.endHandler(new Runnable() {
              public void run() {
                azzert(Utils.buffersEqual(totResponseBody, buff));
                if (trailers != null) {
                  assertTrailers(trailers, response);
                }
                server.close(new Runnable() {
                  public void run() {
                    latch.countDown();
                  }
                });
              }
            });
          }
        };

        HttpClientRequest req;
        HttpClient client = new HttpClient().setHost(host).setPort(port).setKeepAlive(keepAlive);
        if ("GETNOW".equals(method)) {
           client.getNow(path + paramsString, requestHeaders, responseHandler);
        } else {
          req = getRequest(specificMethod, method, path, paramsString, responseHandler, client);
          req.putAllHeaders(requestHeaders);
          if (requestBody != null) {
            for (Buffer buff : requestBody) {
              req.write(buff);
            }
          }
          req.end();
        }
        client.close();
      }
    }.run();

    azzert(latch.await(5, TimeUnit.SECONDS));
    ///awaitClose(server);
    //client.close();

    throwAssertions();

  }

  private HttpClientRequest getRequest(boolean specificMethod, String method, String path, String paramsString,
                                       HttpResponseHandler responseHandler, HttpClient client) {
    HttpClientRequest req = null;
    if (specificMethod) {
      if ("GET".equals(method)) {
        req = client.get(path + paramsString, responseHandler);
      } else if ("POST".equals(method)) {
        req = client.post(path + paramsString, responseHandler);
      } else if ("PUT".equals(method)) {
        req = client.put(path + paramsString, responseHandler);
      } else if ("HEAD".equals(method)) {
        req = client.head(path + paramsString, responseHandler);
      } else if ("DELETE".equals(method)) {
        req = client.delete(path + paramsString, responseHandler);
      } else if ("TRACE".equals(method)) {
        req = client.trace(path + paramsString, responseHandler);
      } else if ("CONNECT".equals(method)) {
        req = client.connect(path + paramsString, responseHandler);
      } else if ("OPTIONS".equals(method)) {
        req = client.options(path + paramsString, responseHandler);
      }
    } else {
      req = client.request(method, path + paramsString, responseHandler);
    }
    return req;
  }

  private Map<String, String> genMap(int num) {
    Map<String, String> map = new HashMap<String, String>();
    for (int i = 0; i < num; i++) {
      String key;
      do {
        key = Utils.randomAlphaString(1 + (int) ((19) * Math.random()));
      } while (map.containsKey(key));
      map.put(key, Utils.randomAlphaString(1 + (int) ((19) * Math.random())));
    }
    return map;
  }

  private void assertHeaders(Map<String, String> h1, HttpServerRequest request) {
    azzert(h1 != null);
    azzert(request != null);
    //Check that all of h1 are in h2
    for (Map.Entry<String, String> entry : h1.entrySet()) {
      azzert(entry.getValue().equals(request.getHeader(entry.getKey())));
    }
  }

  private void assertHeaders(Map<String, String> h1, HttpClientResponse response) {
    azzert(h1 != null);
    azzert(response != null);
    //Check that all of h1 are in h2
    for (Map.Entry<String, String> entry : h1.entrySet()) {
      azzert(entry.getValue().equals(response.getHeader(entry.getKey())));
    }
  }

  private void assertTrailers(Map<String, String> h1, HttpClientResponse response) {
    azzert(h1 != null);
    azzert(response != null);
    //Check that all of h1 are in h2
    for (Map.Entry<String, String> entry : h1.entrySet()) {
      azzert(entry.getValue().equals(response.getTrailer(entry.getKey())));
    }
  }

  private void dumpHeaders(HttpClientResponse response) {
    for (String key : response.getHeaderNames()) {
      System.out.println(key + ":" + response.getHeader(key));
    }
  }

  private String toParamsString(Map<String, String> headers) {
    StringBuilder builder = new StringBuilder();
    builder.append('?');
    boolean first = true;
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (!first) builder.append('&');
      first = false;
      builder.append(entry.getKey()).append('=').append(entry.getValue());
    }
    return builder.toString();
  }
}
