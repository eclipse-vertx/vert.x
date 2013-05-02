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

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.vertx.java.core.CaseInsensitiveMultiMap;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpServerRequest implements HttpServerRequest {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpServerRequest.class);

  private final ServerConnection conn;
  private final HttpRequest request;
  private final HttpServerResponse response;

  private String method;
  private String uri;
  private String path;
  private String query;
  private URI juri;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;

  //Cache this for performance
  private MultiMap params;
  private MultiMap headers;
  private URI absoluteURI;

  DefaultHttpServerRequest(final ServerConnection conn,
                           final HttpRequest request,
                           final HttpServerResponse response) {
    this.conn = conn;
    this.request = request;
    this.response = response;
  }

  @Override
  public String method() {
    if (method == null) {
      method = request.getMethod().toString();
    }
    return method;
  }

  @Override
  public String uri() {
    if (uri == null) {
      uri = request.getUri();
    }
    return uri;
  }

  URI juri() {
    if (juri == null) {
      try {
        juri = new URI(uri());
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid uri " + uri()); //Should never happen
      }
    }
    return juri;
  }

  @Override
  public String path() {
    if (path == null) {
      path = juri().getPath();
    }
    return path;
  }

  @Override
  public String query() {
    if (query == null) {
      query = juri().getQuery();
    }
    return query;
  }

  @Override
  public HttpServerResponse response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    if (headers == null) {
      headers = new HttpHeadersAdapter(request.headers());
    }
    return headers;
  }

  @Override
  public MultiMap params() {
    if (params == null) {
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
      Map<String, List<String>> prms = queryStringDecoder.parameters();
      params = new CaseInsensitiveMultiMap();

      if (!prms.isEmpty()) {
        for (Map.Entry<String, List<String>> entry: prms.entrySet()) {
          params.add(entry.getKey(), entry.getValue());
        }
      }
    }
    return params;
  }

  @Override
  public HttpServerRequest dataHandler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
    return this;
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public HttpServerRequest pause() {
    conn.pause();
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    conn.resume();
    return this;
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  @Override
  public InetSocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public URI absoluteURI() {
    if (absoluteURI == null) {
      try {
        URI uri = juri();
        String scheme = uri.getScheme();
        if (scheme != null && (scheme.startsWith("http:") || scheme.startsWith("https"))) {
          absoluteURI = uri;
        } else {
          absoluteURI = new URI(conn.getServerOrigin() + uri);
        }
      } catch (URISyntaxException e) {
        log.error("Failed to create abs uri", e);
      }
    }
    return absoluteURI;
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.getPeerCertificateChain();
  }

  @Override
  public HttpServerRequest bodyHandler(final Handler<Buffer> bodyHandler) {
    final Buffer body = new Buffer();
    dataHandler(new Handler<Buffer>() {
      public void handle(Buffer buff) {
        body.appendBuffer(buff);
      }
    });
    endHandler(new VoidHandler() {
      public void handle() {
        bodyHandler.handle(body);
      }
    });
    return this;
  }

  public HttpRequest nettyRequest() {
    return request;
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

@Override
public String protocolVersion() {
    return nettyRequest().getProtocolVersion().text();
}

@Override
public int protocolMajorVersion() {
    return nettyRequest().getProtocolVersion().majorVersion();
}

@Override
public int protocolMinorVersion() {
    return nettyRequest().getProtocolVersion().minorVersion();
}
}
