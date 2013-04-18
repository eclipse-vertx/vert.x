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

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.testframework.TestClientBase;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RouteMatcherTestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testRouteWithPattern1GET() {
    testRouteWithPattern1("GET");
  }

  public void testRouteWithPattern2GET() {
    testRouteWithPattern2("GET");
  }

  public void testRouteWithPattern3GET() {
    testRouteWithPattern3("GET");
  }

  public void testRouteWithPattern4GET() {
    testRouteWithPattern4("GET");
  }

  public void testRouteWithPattern5GET() {
    testRouteWithPattern5("GET");
  }

  public void testRouteWithPattern6GET() {
    testRouteWithPattern6("GET");
  }

  public void testRouteWithPattern7GET() {
    testRouteWithPattern6("GET");
  }

  // There's no need to repeat patterns 1-6 for all HTTP methods

  public void testRouteWithPatternPUT() {
    testRouteWithPattern1("PUT");
  }
  
  public void testRouteWithPatternPOST() {
    testRouteWithPattern1("POST");
  }
  
  public void testRouteWithPatternDELETE() {
    testRouteWithPattern1("DELETE");
  }
  
  public void testRouteWithPatternHEAD() {
    testRouteWithPattern1("HEAD");
  }
  
  public void testRouteWithPatternOPTIONS() {
    testRouteWithPattern1("OPTIONS");
  }
  
  public void testRouteWithPatternTRACE() {
    testRouteWithPattern1("TRACE");
  }
  
  public void testRouteWithPatternCONNECT() {
    testRouteWithPattern1("CONNECT");
  }
  
  public void testRouteWithPatternPATCH() {
    testRouteWithPattern1("PATCH");
  }
  
  public void testRouteWithRegexGET() {
    testRouteWithRegex("GET");  
  }
  
  public void testRouteWithRegexPUT() {
    testRouteWithRegex("PUT");  
  }
  
  public void testRouteWithRegexPOST() {
    testRouteWithRegex("POST");  
  }
  
  public void testRouteWithRegexDELETE() {
    testRouteWithRegex("DELETE");  
  }
  
  public void testRouteWithRegexHEAD() {
    testRouteWithRegex("HEAD");  
  }
  
  public void testRouteWithRegexOPTIONS() {
    testRouteWithRegex("OPTIONS");  
  }
  
  public void testRouteWithRegexTRACE() {
    testRouteWithRegex("TRACE");  
  }
  
  public void testRouteWithRegexCONNECT() {
    testRouteWithRegex("CONNECT");  
  }
  
  public void testRouteWithRegexPATCH() {
    testRouteWithRegex("PATCH");  
  }

  private void testRouteWithPattern1(String method)  {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "/:name/:version", params, method, "/foo/v0.1");
  }

  private void testRouteWithPattern2(String method)  {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version", params, method, "modules/foo/v0.1");
  }

  private void testRouteWithPattern3(String method)  {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version/", params, method, "modules/foo/v0.1/");
  }

  private void testRouteWithPattern4(String method)  {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version/whatever", params, method, "modules/foo/v0.1/whatever");
  }

  private void testRouteWithPattern5(String method)  {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/blah/:version/whatever", params, method, "modules/foo/blah/v0.1/whatever");
  }

  private void testRouteWithPattern6(String method)  {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    testRoute(false, "/:name/", params, method, "/foo/");
  }

  private void testRouteWithPattern7(String method)  {
    Map<String, String> params = new HashMap<>();
    params.put("my_name", "foo");
    testRoute(false, "/:my_name/", params, method, "/foo/");
  }

  private void testRouteWithRegex(String method) {
    Map<String, String> params = new HashMap<>();
    params.put("param0", "foo");
    params.put("param1", "v0.1");
    String regex = "\\/([^\\/]+)\\/([^\\/]+)";
    testRoute(true, regex, params, method, "/foo/v0.1");
  }

  public void testRouteNoMatchPattern() {
    Map<String, String> params = new HashMap<>();
    testRoute(false, "foo", params, "GET", "bar", false, false);
  }

  public void testRouteNoMatchRegex() {
    Map<String, String> params = new HashMap<>();
    testRoute(true, "foo", params, "GET", "bar", false, false);
  }

  public void testRouteNoMatchHandlerPattern() {
    Map<String, String> params = new HashMap<>();
    testRoute(false, "foo", params, "GET", "bar", false, true);
  }

  public void testRouteNoMatchHandlerRegex() {
    Map<String, String> params = new HashMap<>();
    testRoute(true, "foo", params, "GET", "bar", false, true);
  }


  private void testRoute(final boolean regex, final String pattern, final Map<String, String> params,
                         final String method, final String uri)  {
    testRoute(regex, pattern, params, method, uri, true, false);
  }

  private void testRoute(final boolean regex, final String pattern, final Map<String, String> params,
                         final String method, final String uri, final boolean shouldPass, final boolean noMatchHandler)  {

    RouteMatcher matcher = new RouteMatcher();

    Handler<HttpServerRequest> handler = new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        assert (req.params().size() == params.size());
        for (Map.Entry<String, String> entry : params.entrySet()) {
          assert (entry.getValue().equals(req.params().get(entry.getKey())));
        }
        req.response().end();
      }
    };

    switch (method) {
      case "GET":
        if (regex) {
          matcher.getWithRegEx(pattern, handler);
        } else {
          matcher.get(pattern, handler);
        }
        break;
      case "PUT":
        if (regex) {
          matcher.putWithRegEx(pattern, handler);
        } else {
          matcher.put(pattern, handler);
        }
        break;
      case "POST":
        if (regex) {
          matcher.postWithRegEx(pattern, handler);
        } else {
          matcher.post(pattern, handler);
        }
        break;
      case "DELETE":
        if (regex) {
          matcher.deleteWithRegEx(pattern, handler);
        } else {
          matcher.delete(pattern, handler);
        }
        break;
      case "OPTIONS":
        if (regex) {
          matcher.optionsWithRegEx(pattern, handler);
        } else {
          matcher.options(pattern, handler);
        }
        break;
      case "HEAD":
        if (regex) {
          matcher.headWithRegEx(pattern, handler);
        } else {
          matcher.head(pattern, handler);
        }
        break;
      case "TRACE":
        if (regex) {
          matcher.traceWithRegEx(pattern, handler);
        } else {
          matcher.trace(pattern, handler);
        }
        break;
      case "PATCH":
        if (regex) {
          matcher.patchWithRegEx(pattern, handler);
        } else {
          matcher.patch(pattern, handler);
        }
        break;
      case "CONNECT":
        if (regex) {
          matcher.connectWithRegEx(pattern, handler);
        } else {
          matcher.connect(pattern, handler);
        }
        break;
    }

    final String noMatchResponseBody = "oranges";

    if (noMatchHandler) {
      matcher.noMatch(new Handler<HttpServerRequest>() {
        public void handle(HttpServerRequest req) {
          req.response().end(noMatchResponseBody);
        }
      });
    }

    final HttpServer server = vertx.createHttpServer();
    server.requestHandler(matcher);
    server.listen(8080, "localhost", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClient client = vertx.createHttpClient().setPort(8080).setHost("localhost");

        Handler<HttpClientResponse> respHandler = new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            if (shouldPass) {
              tu.azzert(200 == resp.statusCode());
              closeClientAndServer(client, server);
            } else if (noMatchHandler) {
              tu.azzert(200 == resp.statusCode());
              resp.bodyHandler(new Handler<Buffer>() {
                public void handle(Buffer body) {
                  tu.azzert(noMatchResponseBody.equals(body.toString()));
                  closeClientAndServer(client, server);
                }
              });
            } else {
              tu.azzert(404 == resp.statusCode());
              closeClientAndServer(client, server);
            }
          }
        };

        final HttpClientRequest req;

        switch (method) {
          case "GET":
            req = client.get(uri, respHandler);
            break;
          case "PUT":
            req = client.put(uri, respHandler);
            break;
          case "POST":
            req = client.post(uri, respHandler);
            break;
          case "DELETE":
            req = client.delete(uri, respHandler);
            break;
          case "OPTIONS":
            req = client.options(uri, respHandler);
            break;
          case "HEAD":
            req = client.head(uri, respHandler);
            break;
          case "TRACE":
            req = client.trace(uri, respHandler);
            break;
          case "PATCH":
            req = client.patch(uri, respHandler);
            break;
          case "CONNECT":
            req = client.connect(uri, respHandler);
            break;
          default:
            throw new IllegalArgumentException("Invalid method:" + method);
        }

        req.end();
      }
    });
  }

  private void closeClientAndServer(HttpClient client, HttpServer server) {
    client.close();
    server.close(new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> result) {
        tu.testComplete();
      }
    });
  }
}
