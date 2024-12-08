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

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.TooLongHttpHeaderException;
import io.netty.handler.codec.http.TooLongHttpLineException;
import io.vertx.codegen.annotations.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;

import javax.net.ssl.SSLSession;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a server-side HTTP request.
 * <p>
 * Instances are created for each request and passed to the user via a handler.
 * <p>
 * Each instance of this class is associated with a corresponding {@link HttpServerResponse} instance via
 * {@link #response}.<p>
 * It implements {@link io.vertx.core.streams.ReadStream} so it can be used with
 * {@link io.vertx.core.streams.Pipe} to pipe data with flow control.
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpServerRequest extends ReadStream<Buffer> {

  /**
   * The default invalid request handler, it uses the {@link #decoderResult()} cause and the request information
   * to determine the status code of the response to be sent.
   *
   * <ul>
   *   <li>When the cause is an instance of {@code io.netty.handler.codec.TooLongFrameException} and the error message
   *   starts with <i>An HTTP line is larger than</i> the {@code REQUEST_URI_TOO_LONG} status is sent </li>
   *   <li>Otherwise when the cause is an instance of {@code io.netty.handler.codec.TooLongFrameException} and the error message
   *   starts with <i>HTTP header is larger than</i> the {@code REQUEST_HEADER_FIELDS_TOO_LARGE} status is sent</li>
   *   <li>Otherwise then {@code BAD_REQUEST} status is sent</li>
   * </ul>
   */
  @GenIgnore
  Handler<HttpServerRequest> DEFAULT_INVALID_REQUEST_HANDLER = request -> {
    DecoderResult result = request.decoderResult();
    Throwable cause = result.cause();
    HttpResponseStatus status = null;
    if (cause instanceof TooLongHttpLineException) {
      status = HttpResponseStatus.REQUEST_URI_TOO_LONG;
    } else if (cause instanceof TooLongHttpHeaderException) {
      status = HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE;
    }
    if (status == null) {
      status = HttpResponseStatus.BAD_REQUEST;
    }
    HttpServerResponse response = request.response();
    response.setStatusCode(status.code()).end();
    request.connection().close();
  };

  @Override
  HttpServerRequest exceptionHandler(Handler<Throwable> handler);

  @Override
  HttpServerRequest handler(Handler<Buffer> handler);

  @Override
  HttpServerRequest pause();

  @Override
  HttpServerRequest resume();

  @Override
  HttpServerRequest fetch(long amount);

  @Override
  HttpServerRequest endHandler(Handler<Void> endHandler);

  /**
   * @return the HTTP version of the request
   */
  HttpVersion version();

  /**
   * @return the HTTP method for the request.
   */
  HttpMethod method();

  /**
   * @return true if this {@link io.vertx.core.net.NetSocket} is encrypted via SSL/TLS
   */
  default boolean isSSL() {
    return connection().isSsl();
  }

  /**
   * @return the scheme of the request
   */
  @Nullable
  String scheme();

  /**
   * @return the URI of the request. This is usually a relative URI
   */
  String uri();

  /**
   * @return The path part of the uri. For example /somepath/somemorepath/someresource.foo
   */
  @Nullable
  String path();

  /**
   * @return the query part of the uri. For example someparam=32&amp;someotherparam=x
   */
  @Nullable
  String query();

  /**
   * @return the request authority. For HTTP/2 the {@literal :authority} pseudo header is returned, for HTTP/1.x the
   *         {@literal Host} header is returned or {@code null} when no such header is present. When the authority
   *         string does not carry a port, the {@link HostAndPort#port()} returns {@code -1} to indicate the
   *         scheme port is prevalent.
   */
  @Nullable
  HostAndPort authority();

  /**
   * @return the total number of bytes read for the body of the request.
   */
  long bytesRead();

  /**
   * @return the response. Each instance of this class has an {@link HttpServerResponse} instance attached to it. This is used
   * to send the response back to the client.
   */
  @CacheReturn
  HttpServerResponse response();

  /**
   * @return the headers in the request.
   */
  @CacheReturn
  MultiMap headers();

  /**
   * Return the first header value with the specified name
   *
   * @param headerName  the header name
   * @return the header value
   */
  @Nullable
  default String getHeader(String headerName) {
    return headers().get(headerName);
  }

  /**
   * Return the first header value with the specified name
   *
   * @param headerName  the header name
   * @return the header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default String getHeader(CharSequence headerName) {
    return headers().get(headerName);
  }

  /**
   * Override the charset to use for decoding the query parameter map, when none is set, {@code UTF8} is used.
   *
   * @param charset the charset to use for decoding query params
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerRequest setParamsCharset(String charset);

  /**
   * @return the charset used for decoding query parameters
   */
  String getParamsCharset();

  /**
   * @return the query parameters in the request
   */
  @CacheReturn
  default MultiMap params() { return params(false); }

  /**
   * @param semicolonIsNormalChar whether semicolon is treated as a normal character or a query parameter separator
   * @return the query parameters in the request
   */
  MultiMap params(boolean semicolonIsNormalChar);

  /**
   * Return the first param value with the specified name
   *
   * @param paramName  the param name
   * @return the param value
   */
  @Nullable
  default String getParam(String paramName) {
    return params().get(paramName);
  }

  /**
   * Return the first param value with the specified name or {@code defaultValue} when the query param is not present
   *
   * @param paramName    the param name
   * @param defaultValue the default value, must be non-null
   * @return the param value or {@code defaultValue} when not present
   */
  default String getParam(String paramName, String defaultValue) {
    Objects.requireNonNull(defaultValue, "defaultValue");
    final String paramValue = params().get(paramName);
    return paramValue != null ? paramValue : defaultValue;
  }

  /**
   * @return the remote address for this connection, possibly {@code null} (e.g a server bound on a domain socket).
   * If {@code useProxyProtocol} is set to {@code true}, the address returned will be of the actual connecting client.
   */
  @CacheReturn
  default SocketAddress remoteAddress() {
    return connection().remoteAddress();
  }

  /**
   * @return the local address for this connection, possibly {@code null} (e.g a server bound on a domain socket)
   * If {@code useProxyProtocol} is set to {@code true}, the address returned will be of the proxy.
   */
  @CacheReturn
  default SocketAddress localAddress() {
    return connection().localAddress();
  }

  /**
   * @return SSLSession associated with the underlying socket. Returns null if connection is
   *         not SSL.
   * @see javax.net.ssl.SSLSession
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default SSLSession sslSession() {
    return connection().sslSession();
  }

  /**
   * @return the absolute URI corresponding to the HTTP request
   */
  String absoluteURI();

  /**
   * Convenience method for receiving the entire request body in one piece.
   * <p>
   * This saves the user having to manually setting a data and end handler and append the chunks of the body until
   * the whole body received. Don't use this if your request body is large - you could potentially run out of RAM.
   *
   * @param bodyHandler This handler will be called after all the body has been received
   */
  @Fluent
  default HttpServerRequest bodyHandler(@Nullable Handler<Buffer> bodyHandler) {
    body().onSuccess(bodyHandler);
    return this;
  }

  /**
   * Convenience method for receiving the entire request body in one piece.
   * <p>
   * This saves you having to manually set a dataHandler and an endHandler and append the chunks of the body until
   * the whole body received. Don't use this if your request body is large - you could potentially run out of RAM.
   *
   * @return a future completed with the body result
   */
  Future<Buffer> body();

  /**
   * Returns a future signaling when the request has been fully received successfully or failed.
   *
   * @return a future completed with the body result
   */
  Future<Void> end();

  /**
   * Establish a TCP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.6">tunnel<a/> with the client.
   *
   * <p> This must be called only for {@code CONNECT} HTTP method or for HTTP connection upgrade, before any response is sent.
   *
   * <p> Calling this sends a {@code 200} response for a {@code CONNECT} or a {@code 101} for a connection upgrade wit
   * no {@code content-length} header set and then provides the {@code NetSocket} for handling the created tunnel.
   * Any HTTP header set on the response before calling this method will be sent.
   *
   * <pre>
   * server.requestHandler(req -> {
   *   if (req.method() == HttpMethod.CONNECT) {
   *     // Send a 200 response to accept the connect
   *     NetSocket socket = req.netSocket();
   *     socket.handler(buff -> {
   *       socket.write(buff);
   *     });
   *   }
   *   ...
   * });
   * </pre>
   *
   * @return a future notified with the upgraded socket
   */
  Future<NetSocket> toNetSocket();

  /**
   * Call this with true if you are expecting a multi-part body to be submitted in the request.
   * This must be called before the body of the request has been received
   *
   * @param expect  true - if you are expecting a multi-part body
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerRequest setExpectMultipart(boolean expect);

  /**
   * @return  true if we are expecting a multi-part body for this request. See {@link #setExpectMultipart}.
   */
  boolean isExpectMultipart();

  /**
   * Set an upload handler. The handler will get notified once a new file upload was received to allow you to deal
   * with the file upload.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerRequest uploadHandler(@Nullable Handler<HttpServerFileUpload> uploadHandler);

  /**
   * Returns a map of all form attributes in the request.
   * <p>
   * Be aware that the attributes will only be available after the whole body has been received, i.e. after
   * the request end handler has been called.
   * <p>
   * {@link #setExpectMultipart(boolean)} must be called first before trying to get the form attributes.
   *
   * @return the form attributes
   */
  @CacheReturn
  MultiMap formAttributes();

  /**
   * Return the first form attribute value with the specified name
   *
   * @param attributeName  the attribute name
   * @return the attribute value
   */
  @Nullable
  String getFormAttribute(String attributeName);

  /**
   * @return the id of the stream of this request, {@literal -1} when it is not yet determined, i.e
   *         the request has not been yet sent or it is not supported HTTP/1.x
   */
  @CacheReturn
  default int streamId() {
    return -1;
  }

  /**
   * @return whether this request can be upgraded to a WebSocket, implying it uses HTTP/1.x and presents
   * the correct characteristics for a proper upgrade.
   */
  default boolean canUpgradeToWebSocket() {
    return HttpUtils.canUpgradeToWebSocket(this);
  }

  /**
   * Upgrade the connection of the current request to a WebSocket.
   * <p>
   * This is an alternative way of handling WebSockets and can only be used if no WebSocket handler is set on the
   * {@code HttpServer}, and can only be used during the upgrade request during the WebSocket handshake.
   *
   * <p> Both {@link #handler(Handler)} and {@link #endHandler(Handler)} will be set to get the full body of the
   * request that is necessary to perform the WebSocket handshake.
   *
   * <p> If you need to do an asynchronous upgrade, i.e not performed immediately in your request handler,
   * you need to {@link #pause()} the request in order to not lose HTTP events necessary to upgrade the
   * request.
   *
   * @return a future notified with the upgraded WebSocket
   */
  Future<ServerWebSocket> toWebSocket();

  /**
   * Has the request ended? I.e. has the entire request, including the body been read?
   *
   * @return true if ended
   */
  boolean isEnded();

  /**
   * Set a custom frame handler. The handler will get notified when the http stream receives an custom HTTP/2
   * frame. HTTP/2 permits extension of the protocol.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerRequest customFrameHandler(Handler<HttpFrame> handler);

  /**
   * @return the {@link HttpConnection} associated with this request
   */
  @CacheReturn
  HttpConnection connection();

  /**
   * @return the priority of the associated HTTP/2 stream for HTTP/2 otherwise {@code null}
   */
  default StreamPriorityBase streamPriority() {
      return null;
  }

  /**
   * Set an handler for stream priority changes
   * <p>
   * This is not implemented for HTTP/1.x.
   *
   * @param handler the handler to be called when stream priority changes
   */
  @Fluent
  HttpServerRequest streamPriorityHandler(Handler<StreamPriorityBase> handler);

  /**
   * @return Netty's decoder result useful for handling invalid requests with {@link HttpServer#invalidRequestHandler}
   */
  @GenIgnore
  DecoderResult decoderResult();

  /**
   * Get the cookie with the specified name.
   *
   * NOTE: this will return just the 1st {@link Cookie} that matches the given name, to get all cookies for this name
   * see: {@link #cookies(String)}
   *
   * @param name  the cookie name
   * @return the cookie or {@code null} if not found.
   */
  @Nullable Cookie getCookie(String name);

  /**
   * Get the cookie with the specified {@code <name, domain, path>}.
   *
   * @param name  the cookie name
   * @param domain the cookie domain
   * @param path the cookie path
   * @return the cookie or {@code null} if not found.
   */
  @Nullable Cookie getCookie(String name, String domain, String path);

  /**
   * @return the number of cookies in the cookie jar.
   */
  default int cookieCount() {
    return cookies().size();
  }

  /**
   * @deprecated the implementation made a wrong assumption that cookies could be identified only by their name. The RFC
   * states that the tuple of {@code <name, domain, path>} is the unique identifier.
   *
   * When more than one cookie has the same name, the map will hold that lost one to be parsed and any previously parsed
   * value will be silently overwritten.
   *
   * @return a map of all the cookies.
   */
  @Deprecated
  default Map<String, Cookie> cookieMap() {
    return cookies()
      .stream()
      .collect(Collectors.toMap(Cookie::getName, cookie -> cookie));
  }

  /**
   * Returns a read only set of parsed cookies that match the given name, or an empty set. Several cookies may share the
   * same name but have different keys. A cookie is unique by its {@code <name, domain, path>} tuple.
   *
   * The set entries are references to the request original set. This means that performing property changes in the
   * cookie objects will affect the original object too.
   *
   * NOTE: the returned {@link Set} is read-only. This means any attempt to modify (add or remove to the set), will
   * throw {@link UnsupportedOperationException}.
   *
   * @param name the name to be matches
   * @return the matching cookies or empty set
   */
  Set<Cookie> cookies(String name);

  /**
   * Returns a modifiable set of parsed cookies from the {@code COOKIE} header. Several cookies may share the
   * same name but have different keys. A cookie is unique by its {@code <name, domain, path>} tuple.
   *
   * Request cookies are directly linked to response cookies. Any modification to a cookie object in the returned set
   * will mark the cookie to be included in the HTTP response. Removing a cookie from the set, will also mean that it
   * will be removed from the response, regardless if it was modified or not.
   *
   * @return a set with all cookies in the cookie jar.
   */
  Set<Cookie> cookies();

  /**
   * Marks this request as being routed to the given route. This is purely informational and is
   * being provided to metrics.
   *
   * @param route The route this request has been routed to.
   */
  @Fluent
  default HttpServerRequest routed(String route) {
    return this;
  }

}
