/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.proxy;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.test.http.HttpTestBase;

import java.net.UnknownHostException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Http Proxy for testing
 *
 * <p>
 * A simple Http proxy for testing http proxy functionality. HTTP server running on localhost allowing CONNECT and GET
 * requests only.
 * CONNECT is basically a socket forwarding protocol allowing to use the proxy server to connect to the internet,
 * e.g. CONNECT www.google.com:443 HTTP/1.1.
 * GET accepts an absolute url and gets the url from the origin server, e.g. GET http://www.google.de/ HTTP/1.1.
 * <p>
 * Usually the server will be started in @Before and stopped in @After for a unit test using HttpClient with the
 * setProxyXXX methods.
 * <p>
 * The proxy is not useful for anything except testing, since it lacks most security checks like client acls, however in a
 * test scenario it will bind to localhost only.
 * <p>
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
public class HttpProxy extends TestProxyBase<HttpProxy> {

  public static final int DEFAULT_PORT = 13128;

  private static final Logger log = LoggerFactory.getLogger(HttpProxy.class);

  private HttpServer server;
  private Map<HttpConnection, HttpClient> clientMap = new ConcurrentHashMap<>();
  private NetClient client;

  private int error = 0;

  private MultiMap lastRequestHeaders = null;
  private HttpMethod lastMethod;

  @Override
  public int defaultPort() {
    return DEFAULT_PORT;
  }

  protected Future<HttpServer> start0(Vertx vertx) {
    HttpServerOptions options = createHttpServerOptions();
    options.setHost("localhost").setPort(port);
    client = vertx.createNetClient(createNetClientOptions());
    server = vertx.createHttpServer(options);
    server.requestHandler(request -> {
      HttpMethod method = request.method();
      //TODO: Investigate why request.uri() is null while request.authority() works.
      String uri = request.uri();
      if (isHttp3()) {
        uri = request.authority().toString();
      }
      String username = nextUserName();
      if (username != null) {
        String auth = request.getHeader("Proxy-Authorization");
        String expected = "Basic " + Base64.getEncoder().encodeToString((username + ":" + username).getBytes());
        if (auth == null || !auth.equals(expected)) {
          request.response().setStatusCode(407).end("Proxy authentication failed");
          return;
        }
      }
      lastRequestHeaders = HttpHeaders.headers().addAll(request.headers());
      if (error != 0) {
        request.response().setStatusCode(error).end("Proxy request failed");
      } else if (method == HttpMethod.CONNECT) {
        if (!uri.contains(":")) {
          request.response().setStatusCode(403).end("Invalid request");
        } else {
          lastUri = uri;
          lastMethod = HttpMethod.CONNECT;
          if (forceUri != null) {
            uri = forceUri;
          }
          String[] split = uri.split(":");
          String host = split[0];
          int port;
          try {
            port = Integer.parseInt(split[1]);
          } catch (NumberFormatException ex) {
            port = 443;
          }
          // deny ports not considered safe to connect
          // this will deny access to e.g. smtp port 25 to avoid spammers
          if (port == HttpTestBase.DEFAULT_HTTP_PORT || port < 1024 && port != 443) {
            request.response().setStatusCode(403).end("access to port denied");
            return;
          }
          client.connect(port, host).onComplete(ar1 -> {
            if (ar1.succeeded()) {
              localAddresses.add(ar1.result().localAddress().toString());
              toNetSocket(vertx, request, ar1.result());
            } else {
              request.response().setStatusCode(403).end("request failed");
            }
          });
        }
      } else if (method == HttpMethod.GET) {
        lastUri = uri;
        lastMethod = HttpMethod.GET;
        if (forceUri != null) {
          uri = forceUri;
        }
        RequestOptions opts = new RequestOptions();
        opts.setAbsoluteURI(uri);
        HttpConnection serverConn = request.connection();
        HttpClient client = clientMap.get(serverConn);
        if (client == null) {
          client = vertx.httpClientBuilder()
            .with(new PoolOptions().setHttp1MaxSize(1))
            .withConnectHandler(conn -> localAddresses.add(conn.localAddress().toString()))
            .build();
          clientMap.put(serverConn, client);
          serverConn.closeHandler(v -> clientMap.remove(serverConn));
        }
        client.request(opts).compose(req -> {
          for (String name : request.headers().names()) {
            if (!name.equals("Proxy-Authorization")) {
              req.putHeader(name, request.headers().get(name));
            }
          }
          return req.send();
        }).onComplete(ar1 -> {
          if (ar1.succeeded()) {
            HttpClientResponse resp = ar1.result();
            for (String name : resp.headers().names()) {
              request.response().putHeader(name, resp.headers().getAll(name));
            }
            resp.body().onComplete(ar2 -> {
              if (ar2.succeeded()) {
                request.response().setStatusCode(resp.statusCode()).end(ar2.result());
              } else {
                request.response().setStatusCode(500).end(ar2.cause().toString() + " on client request");
              }
            });
          } else {
            Throwable e = ar1.cause();
            log.debug("exception", e);
            int status;
            if (e instanceof UnknownHostException) {
              status = 504;
            } else {
              status = 400;
            }
            request.response().setStatusCode(status).end(e.toString() + " on client request");
          }
        });
      } else {
        request.response().setStatusCode(405).end("method not supported");
      }
    });
    return server.listen();
  }

  private void toNetSocket(Vertx vertx, HttpServerRequest request, NetSocket clientSocket) {
    Future<HttpServerRequest> fut;
    if (successDelayMillis > 0) {
      fut = vertx.timer(successDelayMillis).map(v -> request);
    } else {
      fut = Future.succeededFuture(request);
    }
    fut.compose(req -> req.toNetSocket().onComplete(ar2 -> {
      if (ar2.succeeded()) {
        NetSocket serverSocket = ar2.result();
        serverSocket.closeHandler(v -> clientSocket.close());
        clientSocket.closeHandler(v -> serverSocket.close());
        serverSocket.pipeTo(clientSocket);
        clientSocket.pipeTo(serverSocket);
      } else {
        // Not handled
      }
    }));
  }

  /**
   * Stop the server.
   * <p>
   * Doesn't wait for the close operation to finish
   */
  @Override
  public void stop() {
    if (server != null) {
      server.close();
      server = null;
    }
    if (client != null) {
      client.close();
    }
  }

  @Override
  public HttpMethod getLastMethod() {
    return lastMethod;
  }

  @Override
  public MultiMap getLastRequestHeaders() {
    return lastRequestHeaders;
  }

  public HttpProxy setError(int error) {
    this.error = error;
    return this;
  }
}
