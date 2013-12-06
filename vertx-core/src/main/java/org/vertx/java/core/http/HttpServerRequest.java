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

package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.streams.ReadStream;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Represents a server-side HTTP request.<p>
 * Instances are created for each request that is handled by the server
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
public interface HttpServerRequest extends ReadStream<HttpServerRequest> {

  /**
   * The HTTP version of the request
   */
  HttpVersion version();

  /**
   * The HTTP method for the request. One of GET, PUT, POST, DELETE, TRACE, CONNECT, OPTIONS or HEAD
   */
  String method();

  /**
   * The uri of the request. For example
   * http://www.somedomain.com/somepath/somemorepath/someresource.foo?someparam=32&amp;someotherparam=x
   */
  String uri();

  /**
   * The path part of the uri. For example /somepath/somemorepath/someresource.foo
   */
  String path();

  /**
   * The query part of the uri. For example someparam=32&amp;someotherparam=x
   */
  String query();

  /**
   * The response. Each instance of this class has an {@link HttpServerResponse} instance attached to it. This is used
   * to send the response back to the client.
   */
  HttpServerResponse response();

  /**
   * A map of all headers in the request, If the request contains multiple headers with the same key, the values
   * will be concatenated together into a single header with the same key value, with each value separated by a comma,
   * as specified <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">here</a>.
   * The headers will be automatically lower-cased when they reach the server
   */
  MultiMap headers();

  /**
   * Returns a map of all the parameters in the request
   */
  MultiMap params();

  /**
   * Return the remote (client side) address of the request
   */
  InetSocketAddress remoteAddress();

  /**
   * Return the local (server side) address of the server that handles the request
   */
  InetSocketAddress localAddress();

  /**
   * @return an array of the peer certificates.  Returns null if connection is
   *         not SSL.
   * @throws SSLPeerUnverifiedException SSL peer's identity has not been verified.
  */
  X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException;

  /**
   * Get the absolute URI corresponding to the the HTTP request
   * @return the URI
   */
  URI absoluteURI();

  /**
   * Convenience method for receiving the entire request body in one piece. This saves the user having to manually
   * set a data and end handler and append the chunks of the body until the whole body received.
   * Don't use this if your request body is large - you could potentially run out of RAM.
   *
   * @param bodyHandler This handler will be called after all the body has been received
   */
  HttpServerRequest bodyHandler(Handler<Buffer> bodyHandler);

  /**
   * Get a net socket for the underlying connection of this request. USE THIS WITH CAUTION!
   * Writing to the socket directly if you don't know what you're doing can easily break the HTTP protocol
   * @return the net socket
   */
  NetSocket netSocket();

  /**
   * Call this with true if you are expecting a multi-part form to be submitted in the request
   * This must be called before the body of the request has been received
   * @param expect
   */
  HttpServerRequest expectMultiPart(boolean expect);

  /**
   * Set the upload handler. The handler will get notified once a new file upload was received and so allow to
   * get notified by the upload in progress.
   */
  HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> uploadHandler);

  /**
   * Returns a map of all form attributes which was found in the request. Be aware that this message should only get
   * called after the endHandler was notified as the map will be filled on-the-fly.
   * {@link #expectMultiPart(boolean)} must be called first before trying to get the formAttributes
   */
  MultiMap formAttributes();

}
