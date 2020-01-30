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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

/**
 * Options describing how an {@link HttpClient} will connect to make a request.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class RequestOptions {

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
   * The default relative request URI = ""
   */
  public static final String DEFAULT_URI = "";

  /**
   * Follow redirection by default = {@code false}
   */
  public static final boolean DEFAULT_FOLLOW_REDIRECTS = false;

  /**
   * The default request timeout = {@code 0} (disabled)
   */
  public static final long DEFAULT_TIMEOUT = 0;

  private HttpMethod method;
  private String host;
  private Integer port;
  private Boolean ssl;
  private String uri;
  private MultiMap headers;
  private boolean followRedirects;
  private long timeout;

  /**
   * Default constructor
   */
  public RequestOptions() {
    method = DEFAULT_HTTP_METHOD;
    host = DEFAULT_HOST;
    port = DEFAULT_PORT;
    ssl = DEFAULT_SSL;
    uri = DEFAULT_URI;
    followRedirects = DEFAULT_FOLLOW_REDIRECTS;
    timeout = DEFAULT_TIMEOUT;
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public RequestOptions(RequestOptions other) {
    setMethod(other.method);
    setHost(other.host);
    setPort(other.port);
    setSsl(other.ssl);
    setURI(other.uri);
    setFollowRedirects(other.followRedirects);
    setTimeout(other.timeout);
    if (other.headers != null) {
      setHeaders(MultiMap.caseInsensitiveMultiMap().setAll(other.headers));
    }
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public RequestOptions(JsonObject json) {
    RequestOptionsConverter.fromJson(json, this);
  }

  /**
   * Get the HTTP method to be used by the client request.
   *
   * @return  the HTTP method
   */
  public HttpMethod getMethod() {
    return method;
  }

  /**
   * Set the HTTP method to be used by the client request.
   *
   * @return a reference to this, so the API can be used fluently
   */
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
   * Set whether SSL/TLS is enabled
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setSsl(Boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  /**
   * @return the request relative URI
   */
  public String getURI() {
    return uri;
  }

  /**
   * Set the request relative URI
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
   * @return the amount of time after which if the request does not return any data within the timeout period an
   *         {@link java.util.concurrent.TimeoutException} will be passed to the exception handler and
   *         the request will be closed.
   */
  public long getTimeout() {
    return timeout;
  }

  /**
   * Sets the amount of time after which if the request does not return any data within the timeout period an
   * {@link java.util.concurrent.TimeoutException} will be passed to the exception handler and
   * the request will be closed.
   *
   * @param timeout the amount of time in milliseconds.
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setTimeout(long timeout) {
    this.timeout = timeout;
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
    Boolean ssl = false;
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
        ssl = true;
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
      headers = MultiMap.caseInsensitiveMultiMap();
    }
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    RequestOptionsConverter.toJson(this, json);
    return json;
  }
}
