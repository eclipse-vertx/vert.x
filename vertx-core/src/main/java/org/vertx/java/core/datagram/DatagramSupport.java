/*
 * Copyright 2013 the original author or authors.
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
package org.vertx.java.core.datagram;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.NetworkSupport;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.DrainSupport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface DatagramSupport<T extends DatagramSupport> extends DrainSupport<T>, NetworkSupport<T> {

  /**
   * Write the given {@link org.vertx.java.core.buffer.Buffer} to the {@link java.net.InetSocketAddress}. The {@link org.vertx.java.core.Handler} will be notified once the
   * write completes.
   *
   *
   * @param packet    the {@link org.vertx.java.core.buffer.Buffer} to write
   * @param host      the host address of the remote peer
   * @param port      the host port of the remote peer
   * @param handler   the {@link org.vertx.java.core.Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  T send(Buffer packet, String host, int port, Handler<AsyncResult<T>> handler);

  /**
   * Write the given {@link String} to the {@link InetSocketAddress} using UTF8 encoding. The {@link Handler} will be notified once the
   * write completes.
   *
   *
   * @param str       the {@link String} to write
   * @param host      the host address of the remote peer
   * @param port      the host port of the remote peer
   * @param handler   the {@link org.vertx.java.core.Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  T send(String str, String host, int port, Handler<AsyncResult<T>> handler);

  /**
   * Write the given {@link String} to the {@link InetSocketAddress} using the given encoding. The {@link Handler} will be notified once the
   * write completes.
   *
   *
   * @param str       the {@link String} to write
   * @param enc       the charset used for encoding
   * @param host      the host address of the remote peer
   * @param port      the host port of the remote peer
   * @param handler   the {@link org.vertx.java.core.Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  T send(String str, String enc, String host, int port, Handler<AsyncResult<T>> handler);

  /**
   * Gets the {@link java.net.StandardSocketOptions#SO_BROADCAST} option.
   */
  boolean isBroadcast();

  /**
   * Sets the {@link java.net.StandardSocketOptions#SO_BROADCAST} option.
   */
  T setBroadcast(boolean broadcast);

  /**
   * Gets the {@link java.net.StandardSocketOptions#IP_MULTICAST_LOOP} option.
   *
   * @return {@code true} if and only if the loopback mode has been disabled
   */
  boolean isLoopbackModeDisabled();

  /**
   * Sets the {@link java.net.StandardSocketOptions#IP_MULTICAST_LOOP} option.
   *
   * @param loopbackModeDisabled
   *        {@code true} if and only if the loopback mode has been disabled
   */
  T setLoopbackModeDisabled(boolean loopbackModeDisabled);

  /**
   * Gets the {@link java.net.StandardSocketOptions#IP_MULTICAST_TTL} option.
   */
  int getTimeToLive();

  /**
   * Sets the {@link java.net.StandardSocketOptions#IP_MULTICAST_TTL} option.
   */
  T setTimeToLive(int ttl);

  /**
   * Gets the address of the network interface used for multicast packets.
   */
  InetAddress getInterface();

  /**
   * Sets the address of the network interface used for multicast packets.
   */
  T setInterface(InetAddress interfaceAddress);

  /**
   * Gets the {@link java.net.StandardSocketOptions#IP_MULTICAST_IF} option.
   */
  NetworkInterface getNetworkInterface();

  /**
   * Sets the {@link java.net.StandardSocketOptions#IP_MULTICAST_IF} option.
   */
  T setNetworkInterface(NetworkInterface networkInterface);


  void close(Handler<AsyncResult<Void>> handler);
  void close();

}
