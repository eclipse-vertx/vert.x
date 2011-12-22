/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.tests.core.http;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;
import org.vertx.tests.core.TestBase;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RouteMatcherTest extends TestBase {

  private static final Logger log = Logger.getLogger(RouteMatcherTest.class);

  @Test
  public void testRouteWithPattern() throws Exception {
    testRouteWithPattern("GET");
    testRouteWithPattern("PUT");
    testRouteWithPattern("POST");
    testRouteWithPattern("DELETE");
    testRouteWithPattern("HEAD");
    testRouteWithPattern("OPTIONS");
    testRouteWithPattern("TRACE");
    testRouteWithPattern("PATCH");
  }

  private void testRouteWithPattern(String method) throws Exception {
    Map<String, String> params = new HashMap<>();

    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "/:name/:version", params, method, "/foo/v0.1");

    params.clear();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version", params, method, "modules/foo/v0.1");

    params.clear();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version/", params, method, "modules/foo/v0.1/");

    params.clear();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version/whatever", params, method, "modules/foo/v0.1/whatever");

    params.clear();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/blah/:version/whatever", params, method, "modules/foo/blah/v0.1/whatever");

    params.clear();
    params.put("name", "foo");
    testRoute(false, "/:name/", params, method, "/foo/");

  }

  @Test
  public void testRouteWithRegEx() throws Exception {
    testRouteWithRegEx("GET");
    testRouteWithRegEx("PUT");
    testRouteWithRegEx("POST");
    testRouteWithRegEx("DELETE");
    testRouteWithRegEx("HEAD");
    testRouteWithRegEx("OPTIONS");
    testRouteWithRegEx("TRACE");
    testRouteWithRegEx("PATCH");
  }


  private void testRouteWithRegEx(String method) throws Exception {
    Map<String, String> params = new HashMap<>();
    params.put("param0", "foo");
    params.put("param1", "v0.1");
    String regex = "\\/([^\\/]+)\\/([^\\/]+)";
    testRoute(true, regex, params, method, "/foo/v0.1");
  }


  private void testRoute(final boolean regex, final String pattern, final Map<String, String> params,
                         final String method, final String uri) throws Exception {
    final String host = "localhost";
    final int port = 8181;

    final CountDownLatch latch = new CountDownLatch(1);

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        RouteMatcher matcher = new RouteMatcher();

        Handler<HttpServerRequest> handler = new Handler<HttpServerRequest>() {
          public void handle(HttpServerRequest req) {
            assert (req.getParams().size() == params.size());
            for (Map.Entry<String, String> entry : params.entrySet()) {
              assert (entry.getValue().equals(req.getParams().get(entry.getKey())));
            }
            req.response.end();
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
        }

        final HttpServer server = new HttpServer().requestHandler(matcher).listen(port, host);

        final HttpClient client = new HttpClient().setPort(port).setHost(host);

        Handler<HttpClientResponse> respHandler = new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            assert (200 == resp.statusCode);
            server.close(new SimpleHandler() {
              public void handle() {
                latch.countDown();
              }
            });
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
          default:
            throw new IllegalArgumentException("Invalid method");
        }

        req.end();
      }
    });

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

}
