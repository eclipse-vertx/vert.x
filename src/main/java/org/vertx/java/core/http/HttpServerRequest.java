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

package org.vertx.java.core.http;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>Encapsulates a server-side HTTP request.</p>
 * <p/>
 * <p>An instance of this class is created for each request that is handled by the server and is passed to the user via the
 * {@link org.vertx.java.core.Handler} instance registered with the {@link HttpServer} using the method
 * {@link HttpServer#requestHandler(org.vertx.java.core.Handler)}.<p>
 * <p/>
 * <p>On creation a new execution context is assigned to each instance and an event loop is allocated to it from one
 * of the available ones. The instance must only be called from that event loop.</p>
 * <p/>
 * <p>Each instance of this class is associated with a corresponding {@link HttpServerResponse} instance via the
 * {@code response} field.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerRequest extends HttpReadStreamBase {

  private static final Logger log = LoggerFactory.getLogger(HttpServerRequest.class);

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Exception> exceptionHandler;
  private final ServerConnection conn;
  private final HttpRequest request;
  private Map<String, String> params;
  //Cache this for performance
  private Map<String, String> headers;

  HttpServerRequest(ServerConnection conn,
                    HttpRequest request) {
    this.method = request.getMethod().toString();
    URI theURI;
    try {
      theURI = new URI(request.getUri());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid uri " + request.getUri()); //Should never happen
    }
    this.uri = request.getUri();
    this.path = theURI.getPath();
    this.query = theURI.getQuery();
    this.conn = conn;
    this.request = request;
    this.response = new HttpServerResponse(conn);
  }

  /**
   * The HTTP method for the request. One of GET, PUT, POST, DELETE, TRACE, CONNECT, OPTIONS, HEAD
   */
  public final String method;

  /**
   * The uri of the request. For example http://www.somedomain.com/somepath/somemorepath/somresource.foo?someparam=32&someotherparam=x
   */
  public final String uri;

  /**
   * The path part of the uri. For example /somepath/somemorepath/somresource.foo
   */
  public final String path;

  /**
   * The query part of the uri. For example someparam=32&someotherparam=x
   */
  public final String query;

  /**
   * The response. Each instance of this class has an {@link HttpServerResponse} instance attached to it. This is used
   * to send the response back to the client.
   */
  public final HttpServerResponse response;

  /**
   * Return the HTTP request header with the name {@code key} from this request, or null if there is no such header.
   */
  public String getHeader(String key) {
    return request.getHeader(key);
  }

  /**
   * Return a set of all the HTTP header names in this request
   */
  public Set<String> getHeaderNames() {
    return request.getHeaderNames();
  }

  /**
   * Returns a map of all headers in the request, If the request contains multiple headers with the same key, the values
   * will be concatenated together into a single header with the same key value, with each value separated by a comma, as specified
   * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">here</a>.
   */
  public Map<String, String> getAllHeaders() {
    if (headers == null) {
      headers = HeaderUtils.simplifyHeaders(request.getHeaders());
    }
    return headers;
  }

  /**
   * Returns a map of all the parameters in the request
   */
  public Map<String, String> getAllParams() {
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

  /**
   * Specify a data handler for the request. If the request has a body, the {@code dataHandler} will get called when some of the request body has
   * been read from the wire. If the request is chunked, then it will be called once for each HTTP chunk, otherwise it
   * will be called one or more times until the full request body has been received.<p>
   * If the request has no body it will not be called at all.
   *
   * @param dataHandler
   */
  public void dataHandler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
  }

  /**
   * Specify an exception handler for the request. The {@code exceptionHandler} is called if an exception occurs
   * when handling the request.
   */
  public void exceptionHandler(Handler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  /**
   * Pause the request. Once the request has been paused, the system will stop reading any more chunks of the request
   * from the wire until it is resumed.<p>
   * Pause is often used in conjunction with a {@link org.vertx.java.core.streams.Pump} to pump data between streams and implement flow control.
   */
  public void pause() {
    conn.pause();
  }

  /**
   * Resume a paused request. The request will resume receiving chunks of the request body from the wire.<p>
   * Resume is often used in conjunction with a {@link org.vertx.java.core.streams.Pump} to pump data between streams and implement flow control.
   */
  public void resume() {
    conn.resume();
  }

  /**
   * Specify an end handler for the request. The {@code endHandler} is called once the entire request has been read.
   */
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
