package tests.core.http;

import org.nodex.core.DoneHandler;
import org.nodex.core.Nodex;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.http.HttpClient;
import org.nodex.core.http.HttpClientConnectHandler;
import org.nodex.core.http.HttpClientConnection;
import org.nodex.core.http.HttpClientRequest;
import org.nodex.core.http.HttpClientResponse;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpResponseHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerConnectHandler;
import org.nodex.core.http.HttpServerConnection;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.http.HttpServerResponse;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tests.Utils;
import tests.core.TestBase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: timfox
 * Date: 23/07/2011
 * Time: 16:21
 */
public class HttpTest extends TestBase {
  @BeforeClass
  public void setUp() {
  }

  @AfterClass
  public void tearDown() {
  }

  private void testMethod(String method, boolean specificMethod) throws Exception {
    String path = "some/path/to/" + randomString(20);
    testHTTP(method, path, 404, 0, 0, false, 8181, specificMethod);
    testHTTP(method, path, 404, 0, 3, false, 8181, specificMethod);
    testHTTP(method, path, 200, 3, 0, false, 8181, specificMethod);
    testHTTP(method, path, 200, 3, 0, true, 8181, specificMethod);
    testHTTP(method, path, 200, 3, 3, false, 8181, specificMethod);
    testHTTP(method, path, 200, 3, 3, true, 8181, specificMethod);
  }

  @Test
  public void testGetNow() throws Exception {
    String path = "some/path/to/" + randomString(20);
    testHTTP("GETNOW", path, 200, 3, 0, true, 8181, false);
    testHTTP("GETNOW", path, 404, 0, 0, false, 8181, false);
  }

  @Test
  public void testGET() throws Exception {
    testMethod("GET", true);
    testMethod("GET", false);
  }

  @Test
  public void testPOST() throws Exception {
    testMethod("POST", true);
    testMethod("POST", false);
  }

  @Test
  public void testPUT() throws Exception {
    testMethod("PUT", true);
    testMethod("PUT", false);
  }

  @Test
  public void testHEAD() throws Exception {
    testMethod("HEAD", true);
    testMethod("HEAD", false);
  }

  @Test
  public void testOPTIONS() throws Exception {
    testMethod("OPTIONS", true);
    testMethod("OPTIONS", false);
  }

  @Test
  public void testDELETE() throws Exception {
    testMethod("DELETE", true);
    testMethod("DELETE", false);
  }

  @Test
  public void testTRACE() throws Exception {
    testMethod("TRACE", true);
    testMethod("TRACE", false);
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

    HttpServerConnectHandler serverH = new HttpServerConnectHandler() {
      public void onConnect(final HttpServerConnection conn) {
        conn.request(new HttpRequestHandler() {
          int count;
          public void onRequest(HttpServerRequest req, final HttpServerResponse resp) {
            assert method.equals(req.method);
            assert path.equals(req.path);
            assert count == Integer.parseInt(req.headers.get("count"));
            final int theCount = count;
            count++;
            final Buffer buff = Buffer.newDynamic(0);
            req.data(new DataHandler() {
              public void onData(Buffer data) {
                buff.append(data);
              }
            });
            req.end(new DoneHandler() {
              public void onDone() {
                assert ("This is content " + theCount).equals(buff.toString()): buff.toString();
                //We write the response back after a random time to increase the chances of responses written in the
                //wrong order if we didn't implement pipelining correctly
                Nodex.instance.setTimeout((long)(100 * Math.random()), new DoneHandler() {
                  public void onDone() {
                    resp.headers.put("count", String.valueOf(theCount));
                    resp.write(buff);
                    resp.end();
                  }
                });
              }
            });
          }
        });
      }
    };
    final CountDownLatch latch = new CountDownLatch(requests);

    HttpClientConnectHandler clientH = new HttpClientConnectHandler() {

      public void onConnect(HttpClientConnection conn) {
        for (int count = 0; count < requests; count++) {
          final int theCount = count;
          HttpClientRequest req = conn.request(method, path, new HttpResponseHandler() {
            public void onResponse(final HttpClientResponse response) {
              //dumpHeaders(response.headers);
              assert response.statusCode == statusCode;
              assert theCount == Integer.parseInt(response.headers.get("count")) : theCount + ":" + response.headers
                  .get("count");
              final Buffer buff = Buffer.newDynamic(0);
              response.data(new DataHandler() {
                public void onData(Buffer data) {
                  buff.append(data);
                }
              });
              response.end(new DoneHandler() {
                public void onDone() {
                  //System.out.println("expected:" + totResponseBody.toString());
                  //System.out.println("actual:" + buff.toString());
                  assert ("This is content " + theCount).equals(buff.toString());
                  latch.countDown();
                  System.out.println("Got " + theCount);
                }
              });
            }
          });
          req.headers.put("count", String.valueOf(count));
          req.write("This is content " + count);
          req.end();
        }
      }
    };

    run(host, port, keepAlive, serverH, clientH, latch);
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

  @Test
  public void testSendFile() throws Exception {
    final String host = "localhost";
    final boolean keepAlive = true;
    final String path = "foo.txt";
    final int port = 8181;

    final String content = randomString(10000);
    final File file = setupFile(path, content);

    HttpServerConnectHandler serverH = new HttpServerConnectHandler() {
      public void onConnect(final HttpServerConnection conn) {
        conn.request(new HttpRequestHandler() {
          public void onRequest(HttpServerRequest req, final HttpServerResponse resp) {
            assert path.equals(req.path);
            //Clearly in a real web server you'd do some safety checks on the path
            String fileName = "./" + req.path;
            resp.sendFile(fileName);
          }
        });
      }
    };
    final CountDownLatch latch = new CountDownLatch(1);

    HttpClientConnectHandler clientH = new HttpClientConnectHandler() {
      public void onConnect(HttpClientConnection conn) {
        conn.getNow(path, new HttpResponseHandler() {
          public void onResponse(final HttpClientResponse response) {
            dumpHeaders(response.headers);
            assert response.statusCode == 200;
            assert file.length() == Long.valueOf(response.headers.get("Content-Length"));
            assert "text/plain".equals(response.headers.get("Content-Type"));
            final Buffer buff = Buffer.newDynamic(0);
            response.data(new DataHandler() {
              public void onData(Buffer data) {
                buff.append(data);
              }
            });
            response.end(new DoneHandler() {
              public void onDone() {
                assert content.equals(buff.toString());
                latch.countDown();
              }
            });
          }
        });
      }
    };

    run(host, port, keepAlive, serverH, clientH, latch);
    file.delete();
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
    final Buffer totResponseBody = Buffer.newDynamic(0);
    for (int i = 0; i < numResponseChunks; i++) {
      Buffer buff = Utils.generateRandomBuffer(100);
      responseBody.add(buff);
      totResponseBody.append(buff);
    }

    final List<Buffer> requestBody = numRequestChunks == 0 ? null : new ArrayList<Buffer>();
    final Buffer totRequestBody = Buffer.newDynamic(0);
    for (int i = 0; i < numRequestChunks; i++) {
      Buffer buff = Utils.generateRandomBuffer(100);
      requestBody.add(buff);
      totRequestBody.append(buff);
    }

    final String host = "localhost";
    final boolean keepAlive = true;

    HttpServerConnectHandler serverH = new HttpServerConnectHandler() {
      public void onConnect(final HttpServerConnection conn) {
        conn.request(new HttpRequestHandler() {
          public void onRequest(HttpServerRequest req, final HttpServerResponse resp) {
            assert (method.equals("GETNOW") ? "GET" : method).equals(req.method): method + ":" + req.method;
            assert path.equals(req.path);
            assert (path + paramsString).equals(req.uri);
            for (Map.Entry<String, String> param : params.entrySet()) {
              assert req.getParam(param.getKey()).equals(param.getValue());
            }
            //dumpHeaders(req.headers);
            assertHeaders(requestHeaders, req.headers);
            assert req.headers.get("Host").equals(host + ":" + port);
            assert req.headers.get("Connection").equals(keepAlive ? "keep-alive" : "close");

            final Buffer buff = Buffer.newDynamic(0);
            req.data(new DataHandler() {
              public void onData(Buffer data) {
                buff.append(data);
              }
            });
            req.end(new DoneHandler() {
              public void onDone() {
                assert Utils.buffersEqual(totRequestBody, buff);
                for (Map.Entry<String, String> header : responseHeaders.entrySet()) {
                  resp.headers.put(header.getKey(), header.getValue());
                }
                resp.statusCode = statusCode;
                if (responseBody != null) {
                  for (Buffer chunk: responseBody) {
                   resp.write(chunk);
                  }
                  if (trailers != null) {
                    for (Map.Entry<String, String> trailer : trailers.entrySet()) {
                      resp.trailers.put(trailer.getKey(), trailer.getValue());
                    }
                  }
                }
                resp.end();
              }
            });
          }
        });
      }
    };

    final CountDownLatch latch = new CountDownLatch(1);

    HttpClientConnectHandler clientH = new HttpClientConnectHandler() {
      public void onConnect(HttpClientConnection conn) {

        HttpResponseHandler responseHandler =  new HttpResponseHandler() {
          public void onResponse(final HttpClientResponse response) {
            //dumpHeaders(response.headers);
            assert response.statusCode == statusCode;
            assertHeaders(responseHeaders, response.headers);
            final Buffer buff = Buffer.newDynamic(0);
            response.data(new DataHandler() {
              public void onData(Buffer data) {
                buff.append(data);
              }
            });
            response.end(new DoneHandler() {
              public void onDone() {
                //System.out.println("expected:" + totResponseBody.toString());
                //System.out.println("actual:" + buff.toString());
                assert Utils.buffersEqual(totResponseBody, buff);
                if (trailers != null) {
                  assertHeaders(trailers, response.trailers);
                }
                latch.countDown();
              }
            });
          }
        };

        HttpClientRequest req;
        if ("GETNOW".equals(method)) {
          conn.getNow(path + paramsString, requestHeaders, responseHandler);
        } else {
          req = getRequest(specificMethod, method, path, paramsString, responseHandler, conn);
          req.headers.putAll(requestHeaders);
          if (requestBody != null) {
            for (Buffer buff: requestBody) {
              req.write(buff);
            }
          }
          req.end();
        }
      }
    };

    run(host, port, keepAlive, serverH, clientH, latch);
  }
  
  private HttpClientRequest getRequest(boolean specificMethod, String method, String path, String paramsString,
                                       HttpResponseHandler responseHandler, HttpClientConnection conn) {
    HttpClientRequest req = null;
    if (specificMethod) {
      if ("GET".equals(method)) {
        req = conn.get(path + paramsString, responseHandler);
      } else if ("POST".equals(method)) {
        req = conn.post(path + paramsString, responseHandler);
      } else if ("PUT".equals(method)) {
        req = conn.put(path + paramsString, responseHandler);
      } else if ("HEAD".equals(method)) {
        req = conn.head(path + paramsString, responseHandler);
      } else if ("DELETE".equals(method)) {
        req = conn.delete(path + paramsString, responseHandler);
      } else if ("TRACE".equals(method)) {
        req = conn.trace(path + paramsString, responseHandler);
      } else if ("CONNECT".equals(method)) {
        req = conn.connect(path + paramsString, responseHandler);
      } else if ("OPTIONS".equals(method)) {
        req = conn.options(path + paramsString, responseHandler);
      } else if ("PATCH".equals(method)) {
        req = conn.patch(path + paramsString, responseHandler);
      }
    } else {
      req = conn.request(method, path + paramsString, responseHandler);
    }
    return req;
  }

  private void run(String host, int port, boolean keepAlive, HttpServerConnectHandler serverHandler,
                   HttpClientConnectHandler clientHandler,
                   CountDownLatch endLatch) throws Exception {
    HttpServer server = HttpServer.createServer(serverHandler).listen(port, host);

    HttpClient client = HttpClient.createClient().setKeepAlive(keepAlive).connect(port, host, clientHandler);

    assert endLatch.await(5, TimeUnit.SECONDS);

    awaitClose(server);
  }

  private Map<String, String> genMap(int num) {
    Map<String, String> map = new HashMap<String, String>();
    for (int i = 0; i < num; i++) {
      String key;
      do {
        key = randomString(1 + (int)((19) * Math.random()));
      } while (map.containsKey(key));
      map.put(key, randomString(1 + (int)((19) * Math.random())));
    }
    return map;
  }

  private String randomString(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char c = (char) (65 + 25 * Math.random());
      builder.append(c);
    }
    return builder.toString();
  }

  private void assertHeaders(Map<String, String> h1, Map<String, String> h2) {
    assert h1 != null;
    assert h2 != null;
    //Check that all of h1 are in h2
    for (Map.Entry<String, String> entry: h1.entrySet()) {
      assert entry.getValue().equals(h2.get(entry.getKey()));
    }
  }

  private void dumpHeaders(Map<String, String> headers) {
    for (Map.Entry<String, String> entry: headers.entrySet()) {
      System.out.println(entry.getKey() + ":" + entry.getValue());
    }
  }

  private String toParamsString(Map<String, String> headers) {
    StringBuilder builder = new StringBuilder();
    builder.append('?');
    boolean first = true;
    for (Map.Entry<String, String> entry: headers.entrySet()) {
      if (!first) builder.append('&');
      first = false;
      builder.append(entry.getKey()).append('=').append(entry.getValue());
    }
    return builder.toString();
  }
}
