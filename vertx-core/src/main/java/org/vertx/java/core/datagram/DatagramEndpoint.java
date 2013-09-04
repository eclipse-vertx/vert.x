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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface DatagramEndpoint {

  DatagramEndpoint bind(InetSocketAddress local, Handler<AsyncResult<BoundDatagramChannel>> handler);

  DatagramEndpoint connect(InetSocketAddress local, Handler<AsyncResult<ConnectedDatagramChannel>> handler);

  /**
   * Gets the {@link java.net.StandardSocketOptions#SO_SNDBUF} option.
   */
  int getSendBufferSize();

  /**
   * Sets the {@link java.net.StandardSocketOptions#SO_SNDBUF} option.
   */
  DatagramEndpoint setSendBufferSize(int sendBufferSize);

  /**
   * Gets the {@link java.net.StandardSocketOptions#SO_RCVBUF} option.
   */
  int getReceiveBufferSize();

  /**
   * Sets the {@link java.net.StandardSocketOptions#SO_RCVBUF} option.
   */
  DatagramEndpoint setReceiveBufferSize(int receiveBufferSize);

  /**
   * Gets the {@link java.net.StandardSocketOptions#IP_TOS} option.
   */
  int getTrafficClass();

  /**
   * Sets the {@link java.net.StandardSocketOptions#IP_TOS} option.
   */
  DatagramEndpoint setTrafficClass(int trafficClass);

  /**
   * Gets the {@link java.net.StandardSocketOptions#SO_REUSEADDR} option.
   */
  boolean isReuseAddress();

  /**
   * Gets the {@link java.net.StandardSocketOptions#SO_REUSEADDR} option.
   */
  DatagramEndpoint setReuseAddress(boolean reuseAddress);

  /**
   * Gets the {@link java.net.StandardSocketOptions#SO_BROADCAST} option.
   */
  boolean isBroadcast();

  /**
   * Sets the {@link java.net.StandardSocketOptions#SO_BROADCAST} option.
   */
  DatagramEndpoint setBroadcast(boolean broadcast);

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
  DatagramEndpoint setLoopbackModeDisabled(boolean loopbackModeDisabled);

  /**
   * Gets the {@link java.net.StandardSocketOptions#IP_MULTICAST_TTL} option.
   */
  int getTimeToLive();

  /**
   * Sets the {@link java.net.StandardSocketOptions#IP_MULTICAST_TTL} option.
   */
  DatagramEndpoint setTimeToLive(int ttl);

  /**
   * Gets the address of the network interface used for multicast packets.
   */
  InetAddress getInterface();

  /**
   * Sets the address of the network interface used for multicast packets.
   */
  DatagramEndpoint setInterface(InetAddress interfaceAddress);

  /**
   * Gets the {@link java.net.StandardSocketOptions#IP_MULTICAST_IF} option.
   */
  NetworkInterface getNetworkInterface();

  /**
   * Sets the {@link java.net.StandardSocketOptions#IP_MULTICAST_IF} option.
   */
  DatagramEndpoint setNetworkInterface(NetworkInterface networkInterface);

  StandardProtocolFamily getProtocolFamily();

  DatagramEndpoint setProtocolFamily(StandardProtocolFamily family);
}
