package tests.core.http;

import org.nodex.core.DoneHandler;
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

  /*
  Also need to test
  pipelining
  multiple connections
  getnow
  specific request method get, post etc
   */

  private void testMethod(String method) throws Exception {
    String path = "some/path/to/" + randomString(20);
    testHTTP(method, path, 404, 0, 0, false, 8181);
    testHTTP(method, path, 404, 0, 3, false, 8181);
    testHTTP(method, path, 200, 3, 0, false, 8181);
    testHTTP(method, path, 200, 3, 0, true, 8181);
    testHTTP(method, path, 200, 3, 3, false, 8181);
    testHTTP(method, path, 200, 3, 3, true, 8181);
  }

  @Test
  public void testGET() throws Exception {
    testMethod("GET");
  }

  @Test
  public void testPOST() throws Exception {
    testMethod("POST");
  }

  @Test
  public void testPUT() throws Exception {
    testMethod("PUT");
  }

  @Test
  public void testHEAD() throws Exception {
    testMethod("HEAD");
  }

  @Test
  public void testOPTIONS() throws Exception {
    testMethod("OPTIONS");
  }

  @Test
  public void testDELETE() throws Exception {
    testMethod("DELETE");
  }

  @Test
  public void testTRACE() throws Exception {
    testMethod("TRACE");
  }

  private void testHTTP(final String method, final String path, final int statusCode, final int numResponseChunks,
                        final int numRequestChunks, final boolean setTrailers, final int port) throws Exception {
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
            assert method.equals(req.method);
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
        HttpClientRequest req = conn.request(method, path + paramsString, new HttpResponseHandler() {
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
        });
        req.headers.putAll(requestHeaders);
        if (requestBody != null) {
          for (Buffer buff: requestBody) {
            req.write(buff);
          }
        }
        req.end();
      }
    };

    run(host, port, keepAlive, serverH, clientH, latch);
  }

  private void run(String host, int port, boolean keepAlive, HttpServerConnectHandler serverHandler,
                   HttpClientConnectHandler clientHandler,
                   CountDownLatch endLatch) throws Exception {
    HttpServer server = HttpServer.createServer(serverHandler).listen(port, host);

    HttpClient client = HttpClient.createClient().setKeepAlive(keepAlive).connect(port, host, clientHandler);

    assert endLatch.await(5, TimeUnit.SECONDS);

    awaitClose(server);
  }
}
