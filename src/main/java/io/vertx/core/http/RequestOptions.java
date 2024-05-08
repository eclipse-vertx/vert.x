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

package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE;

/**
 * Options describing how an {@link HttpClient} will connect to make a request.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class RequestOptions extends HttpConnectOptions {

  /**
   * The default value for server method = {@code null}
   */
  public static final SocketAddress DEFAULT_SERVER = null;

  /**
   * The default value for HTTP method = {@link HttpMethod#GET}
   */
  public static final HttpMethod DEFAULT_HTTP_METHOD = HttpMethod.GET;

  /**
   * The default value for host name = {@code null}
   */
  public static final String DEFAULT_HOST = null;

  /**
   * The default value for port = {@code null}
   */
  public static final Integer DEFAULT_PORT = null;

  /**
   * The default value for SSL = {@code null}
   */
  public static final Boolean DEFAULT_SSL = null;

  /**
   * The default request URI = {@code "/"}
   */
  public static final String DEFAULT_URI = "/";

  /**
   * Follow redirection by default = {@code false}
   */
  public static final boolean DEFAULT_FOLLOW_REDIRECTS = false;

  /**
   * The default request timeout = {@code -1L} (disabled)
   */
  public static final long DEFAULT_TIMEOUT = -1L;

  /**
   * The default connect timeout = {@code -1L} (disabled)
   */
  public static final long DEFAULT_CONNECT_TIMEOUT = -1L;

  /**
   * The default idle timeout = {@code -1L} (disabled)
   */
  public static final long DEFAULT_IDLE_TIMEOUT = -1L;

  private HttpMethod method;
  private String uri;
  private MultiMap headers;
  private boolean followRedirects;
  private long timeout;
  private long idleTimeout;
  private String traceOperation;
  private String routingKey;

  /**
   * Default constructor
   */
  public RequestOptions() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public RequestOptions(RequestOptions other) {
    super(other);
    setMethod(other.method);
    setURI(other.uri);
    setFollowRedirects(other.followRedirects);
    setIdleTimeout(other.idleTimeout);
    setTimeout(other.timeout);
    if (other.headers != null) {
      setHeaders(MultiMap.caseInsensitiveMultiMap().setAll(other.headers));
    }
    setTraceOperation(other.traceOperation);
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public RequestOptions(JsonObject json) {
    super(json);
    RequestOptionsConverter.fromJson(json, this);
    String method = json.getString("method");
    if (method != null) {
      setMethod(HttpMethod.valueOf(method));
    }
    JsonObject headers = json.getJsonObject("headers");
    if (headers != null) {
      for (Map.Entry<String, Object> entry : headers) {
        Object value = entry.getValue();
        if (value instanceof String) {
          this.addHeader(entry.getKey(), (String) value);
        } else if (value instanceof Iterable) {
          for (Object subValue : ((Iterable<?>) value)) {
            if (subValue instanceof String) {
              this.addHeader(entry.getKey(), (String) subValue);
            }
          }
        }
      }
    }
  }

  @Override
  protected void init() {
    super.init();
    method = DEFAULT_HTTP_METHOD;
    uri = DEFAULT_URI;
    followRedirects = DEFAULT_FOLLOW_REDIRECTS;
    timeout = DEFAULT_TIMEOUT;
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
    traceOperation = null;
  }

  public RequestOptions setProxyOptions(ProxyOptions proxyOptions) {
    super.setProxyOptions(proxyOptions);
    return this;
  }

  public RequestOptions setServer(Address server) {
    super.setServer(server);
    return this;
  }

  /**
   * Get the HTTP method to be used by the client request.
   *
   * @return  the HTTP method
   */
  @GenIgnore
  public HttpMethod getMethod() {
    return method;
  }

  /**
   * Set the HTTP method to be used by the client request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public RequestOptions setMethod(HttpMethod method) {
    this.method = method;
    return this;
  }

  public RequestOptions setHost(String host) {
    super.setHost(host);
    return this;
  }

  public RequestOptions setPort(Integer port) {
    super.setPort(port);
    return this;
  }

  public RequestOptions setSsl(Boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  public RequestOptions setSslOptions(ClientSSLOptions sslOptions) {
    super.setSslOptions(sslOptions);
    return this;
  }

  /**
   * @return the request relative URI
   */
  public String getURI() {
    return uri;
  }

  /**
   * Set the request relative URI.
   *
   * @param uri  the relative uri
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setURI(String uri) {
    this.uri = uri;
    return this;
  }

  /**
   * @return {@code true} when the client should follow redirection
   */
  public Boolean getFollowRedirects() {
    return followRedirects;
  }

  /**
   * Set whether to follow HTTP redirect
   *
   * @param followRedirects  whether to follow redirect
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setFollowRedirects(Boolean followRedirects) {
    this.followRedirects = followRedirects;
    return this;
  }

  /**
   * @see #setTimeout(long)
   */
  public long getTimeout() {
    return timeout;
  }

  /**
   * Sets both connect and idle timeouts for the request
   *
   * <ul>
   *   <li><i>connect timeout</i>: if the request is not obtained from the client within the timeout period, the {@code Future<HttpClientRequest>}
   *   obtained from the client is failed with a {@link java.util.concurrent.TimeoutException}.</li>
   *   <li><i>idle timeout</i>: if the request does not return any data within the timeout period, the request/response is closed and the
   *   related futures are failed with a {@link java.util.concurrent.TimeoutException}, e.g. {@code Future<HttpClientResponse>}
   *   or {@code Future<Buffer>} response body.</li>
   * </ul>
   *
   * The connect and idle timeouts can be set separately using {@link #setConnectTimeout(long)} and {@link #setIdleTimeout(long)}
   */
  public RequestOptions setTimeout(long timeout) {
    this.timeout = timeout;
    return this;
  }

  public RequestOptions setConnectTimeout(long timeout) {
    super.setConnectTimeout(timeout);
    return this;
  }

  /**
   * @return the amount of time after which, if the request does not return any data within the timeout period,
   *         the request/response is closed and the related futures are failed with a {@link java.util.concurrent.TimeoutException}
   */
  public long getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * Sets the amount of time after which, if the request does not return any data within the timeout period,
   * the request/response is closed and the related futures are failed with a {@link java.util.concurrent.TimeoutException},
   * e.g. {@code Future<HttpClientResponse>} or {@code Future<Buffer>} response body.
   *
   * <p/>The timeout starts after a connection is obtained from the client, similar to calling
   * {@link HttpClientRequest#idleTimeout(long)}.
   *
   * @param timeout the amount of time in milliseconds.
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setIdleTimeout(long timeout) {
    this.idleTimeout = timeout;
    return this;
  }

  private URL parseUrl(String surl) {
    // Note - parsing a URL this way is slower than specifying host, port and relativeURI
    try {
      return new URL(surl);
    } catch (MalformedURLException e) {
      throw new VertxException("Invalid url: " + surl, e);
    }
  }

  /**
   * Parse an absolute URI to use, this will update the {@code ssl}, {@code host},
   * {@code port} and {@code uri} fields.
   *
   * @param absoluteURI the uri to use
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setAbsoluteURI(String absoluteURI) {
    Objects.requireNonNull(absoluteURI, "Cannot set a null absolute URI");
    URL url = parseUrl(absoluteURI);
    return setAbsoluteURI(url);
  }

  /**
   * Like {@link #setAbsoluteURI(String)} but using an {@link URL} parameter.
   *
   * @param url the uri to use
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(PERMITTED_TYPE)
  public RequestOptions setAbsoluteURI(URL url) {
    Objects.requireNonNull(url, "Cannot set a null absolute URI");
    Boolean ssl = Boolean.FALSE;
    int port = url.getPort();
    String relativeUri = url.getPath().isEmpty() ? "/" + url.getFile() : url.getFile();
    String protocol = url.getProtocol();
    switch (protocol) {
      case "http":
        if (port == -1) {
          port = 80;
        }
        break;
      case "https": {
        ssl = Boolean.TRUE;
        if (port == -1) {
          port = 443;
        }
        break;
      }
      default:
        throw new IllegalArgumentException();
    }
    setURI(relativeUri);
    setPort(port);
    setSsl(ssl);
    setHost(url.getHost());
    return this;
  }

  /**
   * Add a request header.
   *
   * @param key  the header key
   * @param value  the header value
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public RequestOptions addHeader(String key, String value) {
    return addHeader((CharSequence) key, value);
  }

  /**
   * Add a request header.
   *
   * @param key  the header key
   * @param value  the header value
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public RequestOptions addHeader(CharSequence key, CharSequence value) {
    checkHeaders();
    Objects.requireNonNull(key, "no null key accepted");
    Objects.requireNonNull(value, "no null value accepted");
    headers.add(key, value);
    return this;
  }

  @GenIgnore
  public RequestOptions addHeader(CharSequence key, Iterable<CharSequence> values) {
    checkHeaders();
    Objects.requireNonNull(key, "no null key accepted");
    Objects.requireNonNull(values, "no null values accepted");
    headers.add(key, values);
    return this;
  }

  /**
   * Set a request header.
   *
   * @param key  the header key
   * @param value  the header value
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public RequestOptions putHeader(String key, String value) {
    return putHeader((CharSequence) key, value);
  }

  /**
   * Set a request header.
   *
   * @param key  the header key
   * @param value  the header value
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public RequestOptions putHeader(CharSequence key, CharSequence value) {
    checkHeaders();
    headers.set(key, value);
    return this;
  }

  @GenIgnore
  public RequestOptions putHeader(CharSequence key, Iterable<CharSequence> values) {
    checkHeaders();
    headers.set(key, values);
    return this;
  }

  /**
   * Add a request header.
   *
   * @param key  the header key
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public RequestOptions removeHeader(String key) {
    return removeHeader((CharSequence) key);
  }

  /**
   * Add a request header.
   *
   * @param key  the header key
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public RequestOptions removeHeader(CharSequence key) {
    if (headers != null) {
      headers.remove(key);
    }
    return this;
  }

  /**
   * Set request headers from a multi-map.
   *
   * @param headers  the headers
   * @return  a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public RequestOptions setHeaders(MultiMap headers) {
    this.headers = headers;
    return this;
  }

  /**
   * Get the request headers
   *
   * @return  the headers
   */
  @GenIgnore
  public MultiMap getHeaders() {
    return headers;
  }

  private void checkHeaders() {
    if (headers == null) {
      headers = HttpHeaders.headers();
    }
  }

  /**
   * @return the trace operation override
   */
  public String getTraceOperation() {
    return traceOperation;
  }

  /**
   * Override the operation the tracer use for this request. When no operation is set, the HTTP method is used
   * instead.
   *
   * @param op the override
   * @return  a reference to this, so the API can be used fluently
   */
  public RequestOptions setTraceOperation(String op) {
    this.traceOperation = op;
    return this;
  }

  /**
   * Return the routing key, the routing key can be used by a Vert.x client side sticky load balancer to
   * pin the request to a remote HTTP server.
   *
   * @return the routing key
   */
  public String getRoutingKey() {
    return routingKey;
  }

  /**
   * Set the routing key, the routing key can be used by a Vert.x client side sticky load balancer
   * to pin the request to a remote HTTP server.
   *
   * @param key the routing key
   * @return  a reference to this, so the API can be used fluently
   */
  public RequestOptions setRoutingKey(String key) {
    this.routingKey = key;
    return this;
  }

  public JsonObject toJson() {
    JsonObject json = super.toJson();
    RequestOptionsConverter.toJson(this, json);
    if (method != null) {
      json.put("method", method.name());
    }
    if (this.headers != null) {
      JsonObject headers = new JsonObject();
      for (String name : this.headers.names()) {
        List<String> values = this.headers.getAll(name);
        if (values.size() == 1) {
          headers.put(name, values.iterator().next());
        } else {
          headers.put(name, values);
        }
      }
      json.put("headers", headers);
    }
    return json;
  }
}
