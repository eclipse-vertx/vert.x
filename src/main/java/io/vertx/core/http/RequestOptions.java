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
@DataObject(generateConverter = true)
public class RequestOptions {

  /**
   * The default value for proxy options = {@code null}
   */
  public static final ProxyOptions DEFAULT_PROXY_OPTIONS = null;

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

  private ProxyOptions proxyOptions;
  private Address server;
  private HttpMethod method;
  private String host;
  private Integer port;
  private Boolean ssl;
  private ClientSSLOptions sslOptions;;
  private String uri;
  private MultiMap headers;
  private boolean followRedirects;
  private long timeout;
  private long connectTimeout;
  private long idleTimeout;
  private String traceOperation;

  /**
   * Default constructor
   */
  public RequestOptions() {
    proxyOptions = DEFAULT_PROXY_OPTIONS;
    server = DEFAULT_SERVER;
    method = DEFAULT_HTTP_METHOD;
    host = DEFAULT_HOST;
    port = DEFAULT_PORT;
    ssl = DEFAULT_SSL;
    sslOptions = null;
    uri = DEFAULT_URI;
    followRedirects = DEFAULT_FOLLOW_REDIRECTS;
    timeout = DEFAULT_TIMEOUT;
    connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
    traceOperation = null;
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public RequestOptions(RequestOptions other) {
    setProxyOptions(other.proxyOptions);
    setServer(other.server);
    setMethod(other.method);
    setHost(other.host);
    setPort(other.port);
    setSsl(other.ssl);
    sslOptions = other.sslOptions != null ? new ClientSSLOptions(other.sslOptions) : null;
    setURI(other.uri);
    setFollowRedirects(other.followRedirects);
    setIdleTimeout(other.idleTimeout);
    setConnectTimeout(other.connectTimeout);
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
    this();
    RequestOptionsConverter.fromJson(json, this);
    String method = json.getString("method");
    if (method != null) {
      setMethod(HttpMethod.valueOf(method));
    }
    JsonObject server = json.getJsonObject("server");
    if (server != null) {
      Integer port = server.getInteger("port", 80);
      String host = server.getString("host");
      String path = server.getString("path");
      if (host != null) {
        this.server = SocketAddress.inetSocketAddress(port, host);
      } else if (path != null) {
        this.server = SocketAddress.domainSocketAddress(path);
      }
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

  /**
   * Get the proxy options override for connections
   *
   * @return proxy options override
   */
  public ProxyOptions getProxyOptions() {
    return proxyOptions;
  }

  /**
   * Override the {@link HttpClientOptions#setProxyOptions(ProxyOptions)} proxy options
   * for connections.
   *
   * @param proxyOptions proxy options override object
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setProxyOptions(ProxyOptions proxyOptions) {
    this.proxyOptions = proxyOptions;
    return this;
  }

  /**
   * Get the server address to be used by the client request.
   *
   * @return the server address
   */
  public Address getServer() {
    return server;
  }

  /**
   * Set the server address to be used by the client request.
   *
   * <p> When the server address is {@code null}, the address will be resolved after the {@code host}
   * property by the Vert.x resolver.
   *
   * <p> Use this when you want to connect to a specific server address without name resolution.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setServer(Address server) {
    this.server = server;
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

  /**
   * Get the host name to be used by the client request.
   *
   * @return  the host name
   */
  public String getHost() {
    return host;
  }

  /**
   * Set the host name to be used by the client request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * Get the port to be used by the client request.
   *
   * @return  the port
   */
  public Integer getPort() {
    return port;
  }

  /**
   * Set the port to be used by the client request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setPort(Integer port) {
    this.port = port;
    return this;
  }

  /**
   * @return is SSL/TLS enabled?
   */
  public Boolean isSsl() {
    return ssl;
  }

  /**
   * Set whether SSL/TLS is enabled.
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setSsl(Boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  /**
   * @return the SSL options
   */
  public ClientSSLOptions getSslOptions() {
    return sslOptions;
  }

  /**
   * Set the SSL options to use.
   * <p>
   * When none is provided, the client SSL options will be used instead.
   * @param sslOptions the SSL options to use
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setSslOptions(ClientSSLOptions sslOptions) {
    this.sslOptions = sslOptions;
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

  /**
   * @return the amount of time after which, if the request is not obtained from the client within the timeout period,
   *         the {@code Future<HttpClientRequest>} obtained from the client is failed with a {@link java.util.concurrent.TimeoutException}
   */
  public long getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Sets the amount of time after which, if the request is not obtained from the client within the timeout period,
   * the {@code Future<HttpClientRequest>} obtained from the client is failed with a {@link java.util.concurrent.TimeoutException}.
   *
   * Note this is not related to the TCP {@link HttpClientOptions#setConnectTimeout(int)} option, when a request is made against
   * a pooled HTTP client, the timeout applies to the duration to obtain a connection from the pool to serve the request, the timeout
   * might fire because the server does not respond in time or the pool is too busy to serve a request.
   *
   * @param timeout the amount of time in milliseconds.
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setConnectTimeout(long timeout) {
    this.connectTimeout = timeout;
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
    this.uri = relativeUri;
    this.port = port;
    this.ssl = ssl;
    this.host = url.getHost();
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

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    RequestOptionsConverter.toJson(this, json);
    if (method != null) {
      json.put("method", method.name());
    }
    Address serverAddr = this.server;
    if (serverAddr instanceof SocketAddress) {
      SocketAddress socketAddr = (SocketAddress) serverAddr;
      JsonObject server = new JsonObject();
      if (socketAddr.isInetSocket()) {
        server.put("host", socketAddr.host());
        server.put("port", socketAddr.port());
      } else {
        server.put("path", socketAddr.path());
      }
      json.put("server", server);
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
