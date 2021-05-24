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

import io.vertx.codegen.annotations.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.util.Map;

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
   * @return the request host. For HTTP2 it returns the {@literal :authority} pseudo header otherwise it returns the {@literal Host} header
   */
  @Nullable
  String host();

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
   * @return the query parameters in the request
   */
  @CacheReturn
  MultiMap params();

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
   * Note: Java SE 5+ recommends to use javax.net.ssl.SSLSession#getPeerCertificates() instead of
   * of javax.net.ssl.SSLSession#getPeerCertificateChain() which this method is based on. Use {@link #sslSession()} to
   * access that method.
   *
   * @return an ordered array of the peer certificates. Returns null if connection is
   *         not SSL.
   * @throws javax.net.ssl.SSLPeerUnverifiedException SSL peer's identity has not been verified.
   * @see javax.net.ssl.SSLSession#getPeerCertificateChain()
   * @see #sslSession()
   */
  @GenIgnore
  X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException;

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
   * Same as {@link #body()} but with an {@code handler} called when the operation completes
   */
  default HttpServerRequest body(Handler<AsyncResult<Buffer>> handler) {
    body().onComplete(handler);
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
   * Same as {@link #end()} but with an {@code handler} called when the operation completes
   */
  default void end(Handler<AsyncResult<Void>> handler) {
    end().onComplete(handler);
  }

  /**
   * Returns a future signaling when the request has been fully received successfully or failed.
   *
   * @return a future completed with the body result
   */
  Future<Void> end();

  /**
   * Establish a TCP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.6">tunnel<a/> with the client.
   *
   * <p> This must be called only for {@code CONNECT} HTTP method and before any response is sent.
   *
   * <p> Calling this sends a {@code 200} response with no {@code content-length} header set and
   * then provides the {@code NetSocket} for handling the created tunnel. Any HTTP header set on the
   * response before calling this method will be sent.
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
   * @param handler the completion handler
   */
  default void toNetSocket(Handler<AsyncResult<NetSocket>> handler) {
    Future<NetSocket> fut = toNetSocket();
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  /**
   * Like {@link #toNetSocket(Handler)} but returns a {@code Future} of the asynchronous result
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
   * @param handler the completion handler
   */
  default void toWebSocket(Handler<AsyncResult<ServerWebSocket>> handler) {
    Future<ServerWebSocket> fut = toWebSocket();
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  /**
   * Like {@link #toWebSocket(Handler)} but returns a {@code Future} of the asynchronous result
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
  default StreamPriority streamPriority() {
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
  HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler);

  /**
   * Get the cookie with the specified name.
   *
   * @param name  the cookie name
   * @return the cookie
   */
  default @Nullable Cookie getCookie(String name) {
    return cookieMap().get(name);
  }

  /**
   * @return the number of cookieMap.
   */
  default int cookieCount() {
    return cookieMap().size();
  }

  /**
   * @return a map of all the cookies.
   */
  Map<String, Cookie> cookieMap();

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
