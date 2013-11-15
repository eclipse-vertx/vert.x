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

package org.vertx.java.core;

import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.datagram.InternetProtocolFamily;
import org.vertx.java.core.dns.DnsClient;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.core.sockjs.SockJSServer;

import java.net.InetSocketAddress;

/**
 * The control centre of the Vert.x Core API.<p>
 * You should normally only use a single instance of this class throughout your application. If you are running in the
 * Vert.x container an instance will be provided to you.<p>
 * If you are using Vert.x embedded, you can create an instance using one of the static {@code VertxFactory.newVertx}
 * methods.<p>
 * This class acts as a factory for TCP/SSL and HTTP/HTTPS servers and clients, SockJS servers, and provides an
 * instance of the event bus, file system and shared data classes, as well as methods for setting and cancelling
 * timers.<p>
 * Instances of this class are thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Vertx {

  /**
   * Create a TCP/SSL server
   */
  NetServer createNetServer();

  /**
   * Create a TCP/SSL client
   */
  NetClient createNetClient();

  /**
   * Create an HTTP/HTTPS server
   */
  HttpServer createHttpServer();

  /**
   * Create a HTTP/HTTPS client
   */
  HttpClient createHttpClient();

  /**
   * Create a new {@link DatagramSocket}.
   *
   * @param family  use {@link InternetProtocolFamily} to use for multicast. If {@code null} is used it's up to the
   *                operation system to detect it's default.
   * @return socket the created {@link DatagramSocket}.
   */
  DatagramSocket createDatagramSocket(InternetProtocolFamily family);

  /**
   * Create a SockJS server that wraps an HTTP server
   */
  SockJSServer createSockJSServer(HttpServer httpServer);

  /**
   * The File system object
   */
  FileSystem fileSystem();

  /**
   * The event bus
   */
  EventBus eventBus();

  /**
   * Return the {@link DnsClient}
   */
  DnsClient createDnsClient(InetSocketAddress... dnsServers);

  /**
   * The shared data object
   */
  SharedData sharedData();

  /**
   * Set a one-shot timer to fire after {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  long setTimer(long delay, Handler<Long> handler);

  /**
   * Set a periodic timer to fire every {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  long setPeriodic(long delay, Handler<Long> handler);

  /**
   * Cancel the timer with the specified {@code id}. Returns {@code} true if the timer was successfully cancelled, or
   * {@code false} if the timer does not exist.
   */
  boolean cancelTimer(long id);

  /**
   * @return The current context
   */
  Context currentContext();

  /**
   * Put the handler on the event queue for the current loop (or worker context) so it will be run asynchronously ASAP after this event has
   * been processed
   */
  void runOnContext(Handler<Void> action);

  /**
   * Is the current thread an event loop thread?
   * @return true if current thread is an event loop thread
   */
  boolean isEventLoop();

  /**
   * Is the current thread an worker thread?
   * @return true if current thread is an worker thread
   */
  boolean isWorker();

  /**
	 * Stop the eventbus and any resource managed by the eventbus.
	 */
	void stop();
}
