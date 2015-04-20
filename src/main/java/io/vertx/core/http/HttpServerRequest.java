/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;

/**
 * Represents a server-side HTTP request.
 * <p>
 * Instances are created for each request and passed to the user via a handler.
 * <p>
 * Each instance of this class is associated with a corresponding {@link HttpServerResponse} instance via
 * {@link #response}.<p>
 * It implements {@link io.vertx.core.streams.ReadStream} so it can be used with
 * {@link io.vertx.core.streams.Pump} to pump data with flow control.
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
   * @return the URI of the request. This is usually a relative URI
   */
  String uri();

  /**
   * @return The path part of the uri. For example /somepath/somemorepath/someresource.foo
   */
  String path();

  /**
   * @return the query part of the uri. For example someparam=32&amp;someotherparam=x
   */
  String query();

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
  String getHeader(String headerName);

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
  String getParam(String paramName);


  /**
   * @return the remote (client side) address of the request
   */
  @CacheReturn
  SocketAddress remoteAddress();

  /**
   * @return the local (server side) address of the server that handles the request
   */
  @CacheReturn
  SocketAddress localAddress();

  /**
   * @return an array of the peer certificates. Returns null if connection is
   *         not SSL.
   * @throws javax.net.ssl.SSLPeerUnverifiedException SSL peer's identity has not been verified.
  */
  @GenIgnore
  X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException;

  /**
   * @return the absolute URI corresponding to the the HTTP request
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
  HttpServerRequest bodyHandler(Handler<Buffer> bodyHandler);

  /**
   * Get a net socket for the underlying connection of this request.
   * <p>
   * USE THIS WITH CAUTION!
   * <p>
   * Once you have called this method, you must handle writing to the connection yourself using the net socket,
   * the server request instance will no longer be usable as normal.
   * Writing to the socket directly if you don't know what you're doing can easily break the HTTP protocol.
   *
   * @return the net socket
   */
  @CacheReturn
  NetSocket netSocket();

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
  HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> uploadHandler);

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
  String getFormAttribute(String attributeName);

  /**
   * Upgrade the connection to a WebSocket connection.
   * <p>
   * This is an alternative way of handling WebSockets and can only be used if no websocket handlers are set on the
   * Http server, and can only be used during the upgrade request during the WebSocket handshake.
   *
   * @return  the WebSocket
   */
  ServerWebSocket upgrade();

  /**
   * Has the request ended? I.e. has the entire request, including the body been read?
   *
   * @return true if ended
   */
  boolean isEnded();

}
