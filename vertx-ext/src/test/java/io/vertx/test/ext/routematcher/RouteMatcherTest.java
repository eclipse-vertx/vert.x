/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.ext.routematcher;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.routematcher.RouteMatcher;
import io.vertx.test.core.HttpTestBase;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class RouteMatcherTest extends HttpTestBase {

  @Test
  public void testRouteWithPattern1GET() {
    testRouteWithPattern1("GET");
  }

  @Test
  public void testRouteWithPattern2GET() {
    testRouteWithPattern2("GET");
  }

  @Test
  public void testRouteWithPattern3GET() {
    testRouteWithPattern3("GET");
  }

  @Test
  public void testRouteWithPattern4GET() {
    testRouteWithPattern4("GET");
  }

  @Test
  public void testRouteWithPattern5GET() {
    testRouteWithPattern5("GET");
  }

  @Test
  public void testRouteWithPattern6GET() {
    testRouteWithPattern6("GET");
  }

  @Test
  public void testRouteWithPattern7GET() {
    testRouteWithPattern6("GET");
  }

  // There's no need to repeat patterns 1-6 for all HTTP methods

  @Test
  public void testRouteWithPatternPUT() {
    testRouteWithPattern1("PUT");
  }

  @Test
  public void testRouteWithPatternPOST() {
    testRouteWithPattern1("POST");
  }

  @Test
  public void testRouteWithPatternDELETE() {
    testRouteWithPattern1("DELETE");
  }

  @Test
  public void testRouteWithPatternHEAD() {
    testRouteWithPattern1("HEAD");
  }

  @Test
  public void testRouteWithPatternOPTIONS() {
    testRouteWithPattern1("OPTIONS");
  }

  @Test
  public void testRouteWithPatternTRACE() {
    testRouteWithPattern1("TRACE");
  }

  @Test
  public void testRouteWithPatternCONNECT() {
    testRouteWithPattern1("CONNECT");
  }

  @Test
  public void testRouteWithPatternPATCH() {
    testRouteWithPattern1("PATCH");
  }

  @Test
  public void testRouteWithRegexGET() {
    testRouteWithRegex("GET");
  }

  @Test
  public void testRouteWithRegexPUT() {
    testRouteWithRegex("PUT");
  }

  @Test
  public void testRouteWithRegexPOST() {
    testRouteWithRegex("POST");
  }

  @Test
  public void testRouteWithRegexDELETE() {
    testRouteWithRegex("DELETE");
  }

  @Test
  public void testRouteWithRegexHEAD() {
    testRouteWithRegex("HEAD");
  }

  @Test
  public void testRouteWithRegexOPTIONS() {
    testRouteWithRegex("OPTIONS");
  }

  @Test
  public void testRouteWithRegexTRACE() {
    testRouteWithRegex("TRACE");
  }

  @Test
  public void testRouteWithRegexCONNECT() {
    testRouteWithRegex("CONNECT");
  }

  @Test
  public void testRouteWithRegexPATCH() {
    testRouteWithRegex("PATCH");
  }

  @Test
  public void testRouteNoMatchPattern() {
    Map<String, String> params = new HashMap<>();
    testRoute(false, "foo", params, "GET", "bar", false, false);
  }

  @Test
  public void testRouteNoMatchRegex() {
    Map<String, String> params = new HashMap<>();
    testRoute(true, "foo", params, "GET", "bar", false, false);
  }

  @Test
  public void testRouteNoMatchHandlerPattern() {
    Map<String, String> params = new HashMap<>();
    testRoute(false, "foo", params, "GET", "bar", false, true);
  }

  @Test
  public void testRouteNoMatchHandlerRegex() {
    Map<String, String> params = new HashMap<>();
    testRoute(true, "foo", params, "GET", "bar", false, true);
  }

  //----------- Private non test method ----------------------------

  private void testRouteWithPattern1(String method) {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "/:name/:version", params, method, "/foo/v0.1");
  }

  private void testRouteWithPattern2(String method) {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version", params, method, "modules/foo/v0.1");
  }

  private void testRouteWithPattern3(String method) {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version/", params, method, "modules/foo/v0.1/");
  }

  private void testRouteWithPattern4(String method) {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/:version/whatever", params, method, "modules/foo/v0.1/whatever");
  }

  private void testRouteWithPattern5(String method) {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    params.put("version", "v0.1");
    testRoute(false, "modules/:name/blah/:version/whatever", params, method, "modules/foo/blah/v0.1/whatever");
  }

  private void testRouteWithPattern6(String method) {
    Map<String, String> params = new HashMap<>();
    params.put("name", "foo");
    testRoute(false, "/:name/", params, method, "/foo/");
  }

  private void testRouteWithRegex(String method) {
    Map<String, String> params = new HashMap<>();
    params.put("param0", "foo");
    params.put("param1", "v0.1");
    String regex = "\\/([^\\/]+)\\/([^\\/]+)";
    testRoute(true, regex, params, method, "/foo/v0.1");
  }


  private void testRoute(final boolean regex, final String pattern, final Map<String, String> params,
                         final String method, final String uri) {
    testRoute(regex, pattern, params, method, uri, true, false);
  }

  private void testRoute(final boolean regex, final String pattern, final Map<String, String> params,
                         final String method, final String uri, final boolean shouldPass, final boolean noMatchHandler) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    client = vertx.createHttpClient(new HttpClientOptions());

    RouteMatcher matcher = RouteMatcher.newRouteMatcher();

    Handler<HttpServerRequest> handler = req -> {
      assertEquals(params.size(), req.params().size());
      for (Map.Entry<String, String> entry : params.entrySet()) {
        assertEquals(entry.getValue(), req.params().get(entry.getKey()));
      }
      req.response().end();
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
      matcher.noMatch(req -> req.response().writeStringAndEnd(noMatchResponseBody));
    }

    server.requestHandler(matcher.requestHandler()).listen(onSuccess(s -> {
      Handler<HttpClientResponse> respHandler = resp -> {
        if (shouldPass) {
          assertEquals(200, resp.statusCode());
          testComplete();
        } else if (noMatchHandler) {
          assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            assertEquals(noMatchResponseBody, body.toString());
            testComplete();
          });
        } else {
          assertEquals(404, resp.statusCode());
          testComplete();
        }
      };

      final HttpClientRequest req;

      RequestOptions options = new RequestOptions().setRequestURI(uri).setPort(DEFAULT_HTTP_PORT);

      switch (method) {
        case "GET":
          req = client.get(options, respHandler);
          break;
        case "PUT":
          req = client.put(options, respHandler);
          break;
        case "POST":
          req = client.post(options, respHandler);
          break;
        case "DELETE":
          req = client.delete(options, respHandler);
          break;
        case "OPTIONS":
          req = client.options(options, respHandler);
          break;
        case "HEAD":
          req = client.head(options, respHandler);
          break;
        case "TRACE":
          req = client.trace(options, respHandler);
          break;
        case "PATCH":
          req = client.patch(options, respHandler);
          break;
        case "CONNECT":
          req = client.connect(options, respHandler);
          break;
        default:
          throw new IllegalArgumentException("Invalid method:" + method);
      }

      req.end();
    }));

    await();
  }

}
