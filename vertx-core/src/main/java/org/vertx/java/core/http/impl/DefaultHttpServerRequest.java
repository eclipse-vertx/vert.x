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

package org.vertx.java.core.http.impl;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpServerRequest extends HttpServerRequest {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpServerRequest.class);

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Exception> exceptionHandler;
  private final ServerConnection conn;
  private final HttpRequest request;
  //Cache this for performance
  private Map<String, String> params;
  private Map<String, String> headers;

  DefaultHttpServerRequest(ServerConnection conn,
                           String method, String uri, String path, String query,
                           HttpServerResponse response,
                           HttpRequest request) {
    super(method, uri, path, query, response);
    this.conn = conn;
    this.request = request;
  }

  public Map<String, String> headers() {
    if (headers == null) {
      headers = HeaderUtils.simplifyHeaders(request.getHeaders());
    }
    return headers;
  }

  public Map<String, String> params() {
    if (params == null) {
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
      Map<String, List<String>> prms = queryStringDecoder.getParameters();
      if (prms.isEmpty()) {
        params = new HashMap<>();
      } else {
        params = new HashMap<>(prms.size());
        for (Map.Entry<String, List<String>> entry: prms.entrySet()) {
          params.put(entry.getKey(), entry.getValue().get(0));
        }
      }
    }
    return params;
  }

  public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.getPeerCertificateChain();
  }

  public void dataHandler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void exceptionHandler(Handler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  public void pause() {
    conn.pause();
  }

  public void resume() {
    conn.resume();
  }

  public void endHandler(Handler<Void> handler) {
    this.endHandler = handler;
  }

  void handleData(Buffer data) {
    if (dataHandler != null) {
      dataHandler.handle(data);
    }
  }

  void handleEnd() {
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    }
  }
}
