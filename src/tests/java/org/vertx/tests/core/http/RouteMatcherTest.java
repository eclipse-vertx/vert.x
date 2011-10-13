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
import org.vertx.java.core.VertxMain;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    Map<String, String> params = new HashMap<>();

    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "/:name/:version", params, "/foo/v0.1");

    params.clear();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version", params, "modules/foo/v0.1");

    params.clear();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version/", params, "modules/foo/v0.1/");

    params.clear();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version/whatever", params, "modules/foo/v0.1/whatever");

    params.clear();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/blah/:version/whatever", params, "modules/foo/blah/v0.1/whatever");

    params.clear();
    params.put("name", "foo");
    testRoute(false, "/:name/", params, "/foo/");

  }

  @Test
  public void testRouteWithRegEx() throws Exception {
    Map<String, String> params = new HashMap<>();
    params.put("param0", "foo");
    params.put("param1", "v0.1");
    String regex = "\\/([^\\/]+)\\/([^\\/]+)";
    testRoute(true, regex, params, "/foo/v0.1");
  }


  private void testRoute(final boolean regex, final String pattern, final Map<String, String> params, final String uri) throws Exception {
    final String host = "localhost";
    final int port = 8181;

    final CountDownLatch latch = new CountDownLatch(1);

    new VertxMain() {
      public void go() throws Exception {

        RouteMatcher matcher = new RouteMatcher();

        Handler<HttpServerRequest> handler = new Handler<HttpServerRequest>() {
          public void handle(HttpServerRequest req) {
            assert(req.getParams().size() == params.size());
            for (Map.Entry<String, String> entry: params.entrySet()) {
              assert(entry.getValue().equals(req.getParams().get(entry.getKey())));
            }
            System.out.println("Handled ok");
            req.response.end();
          }
        };

        if (regex) {
          matcher.getWithRegEx(pattern, handler);
        } else {
          matcher.get(pattern, handler);
        }

        final HttpServer server = new HttpServer().requestHandler(matcher).listen(port, host);

        final HttpClient client = new HttpClient().setPort(port).setHost(host);

        final HttpClientRequest req = client.get(uri, new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            assert (200 == resp.statusCode);
            server.close(new SimpleHandler() {
              public void handle() {
                latch.countDown();
              }
            });
          }
        });

        req.end();
      }
    }.run();

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

}
