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

package org.vertx.java.core.http;

import org.vertx.java.core.http.impl.HttpReadStreamBase;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.util.Map;

/**
 * Represents a server-side HTTP request.<p>
 * An instance of this class is created for each request that is handled by the server
 * and is passed to the user via the {@link org.vertx.java.core.Handler} instance
 * registered with the {@link HttpServer} using the method {@link HttpServer#requestHandler(org.vertx.java.core.Handler)}.<p>
 * Each instance of this class is associated with a corresponding {@link HttpServerResponse} instance via
 * the {@code response} field.<p>
 * It implements {@link org.vertx.java.core.streams.ReadStream} so it can be used with
 * {@link org.vertx.java.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class HttpServerRequest extends HttpReadStreamBase {

  private static final Logger log = LoggerFactory.getLogger(HttpServerRequest.class);

  protected HttpServerRequest(String method, String uri, String path, String query, HttpServerResponse response) {
    this.method = method;
    this.uri = uri;
    this.path = path;
    this.query = query;
    this.response = response;
  }

  /**
   * The HTTP method for the request. One of GET, PUT, POST, DELETE, TRACE, CONNECT, OPTIONS or HEAD
   */
  public final String method;

  /**
   * The uri of the request. For example
   * http://www.somedomain.com/somepath/somemorepath/somresource.foo?someparam=32&someotherparam=x
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
   * A map of all headers in the request, If the request contains multiple headers with the same key, the values
   * will be concatenated together into a single header with the same key value, with each value separated by a comma,
   * as specified <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">here</a>.
   * The headers will be automatically lower-cased when they reach the server
   */
  public abstract Map<String, String> headers();

  /**
   * Returns a map of all the parameters in the request
   */
  public abstract Map<String, String> params();

  /**
   * @return an array of the peer certificates.  Returns null if connection is
   *         not SSL.
   * @throws SSLPeerUnverifiedException SSL peer's identity has not been verified.
   */
  public abstract X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException;
}
