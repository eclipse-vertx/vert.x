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
import org.vertx.java.core.streams.ReadSupport;

import java.net.InetSocketAddress;


/**
 * A Datagram socket which can be used to send {@link DatagramPacket}'s to remote Datagram servers and receive {@link DatagramPacket}s .
 *
 * Usually you use a Datragram Client to send UDP over the wire. UDP is connection-less which means you are not connected
 * to the remote peer in a persistent way. Because of this you have to supply the address and port of the remote peer
 * when sending data.
 *
 * You can send data to ipv4 or ipv6 addresses, which also include multicast addresses.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface DatagramSocket extends DrainSupport<DatagramSocket>, NetworkSupport<DatagramSocket>, ReadSupport<DatagramSocket, DatagramPacket> {

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
  DatagramSocket send(Buffer packet, String host, int port, Handler<AsyncResult<DatagramSocket>> handler);

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
  DatagramSocket send(String str, String host, int port, Handler<AsyncResult<DatagramSocket>> handler);

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
  DatagramSocket send(String str, String enc, String host, int port, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Gets the {@link java.net.StandardSocketOptions#SO_BROADCAST} option.
   */
  boolean isBroadcast();

  /**
   * Sets the {@link java.net.StandardSocketOptions#SO_BROADCAST} option.
   */
  DatagramSocket setBroadcast(boolean broadcast);

  /**
   * Gets the {@link java.net.StandardSocketOptions#IP_MULTICAST_LOOP} option.
   *
   * @return {@code true} if and only if the loopback mode has been disabled
   */
  boolean isMulticastLoopbackMode();

  /**
   * Sets the {@link java.net.StandardSocketOptions#IP_MULTICAST_LOOP} option.
   *
   * @param loopbackModeDisabled
   *        {@code true} if and only if the loopback mode has been disabled
   */
  DatagramSocket setMulticastLoopbackMode(boolean loopbackModeDisabled);

  /**
   * Gets the {@link java.net.StandardSocketOptions#IP_MULTICAST_TTL} option.
   */
  int getMulticastTimeToLive();

  /**
   * Sets the {@link java.net.StandardSocketOptions#IP_MULTICAST_TTL} option.
   */
  DatagramSocket setMulticastTimeToLive(int ttl);

  /**
   * Gets the {@link java.net.StandardSocketOptions#IP_MULTICAST_IF} option.
   */
  String getMulticastNetworkInterface();

  /**
   * Sets the {@link java.net.StandardSocketOptions#IP_MULTICAST_IF} option.
   */
  DatagramSocket setMulticastNetworkInterface(String iface);

  /**
   * Close the {@link DatagramSocket} implementation asynchronous and notifies the handler once done.
   */
  void close(Handler<AsyncResult<Void>> handler);

  /**
   * Close the {@link DatagramSocket} implementation asynchronous.
   */
  void close();

  /**
   * Return the {@link InetSocketAddress} to which this {@link DatagramSocket} is bound too.
   */
  InetSocketAddress localAddress();

  /**
   * Joins a multicast group and so start listen for packets send to it. The {@link Handler} is notified once the operation completes.
   *
   *
   * @param   multicastAddress  the address of the multicast group to join
   * @param   handler           then handler to notify once the operation completes
   * @return  this              returns itself for method-chaining
   */
  DatagramSocket listenMulticastGroup(String multicastAddress, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Joins a multicast group and so start listen for packets send to it on the given network interface.
   * The {@link Handler} is notified once the operation completes.
   *
   *
   * @param   multicastAddress  the address of the multicast group to join
   * @param   networkInterface  the network interface on which to listen for packets.
   * @param   source            the address of the source for which we will listen for mulicast packets
   * @param   handler           then handler to notify once the operation completes
   * @return  this              returns itself for method-chaining
   */
  DatagramSocket listenMulticastGroup(
          String multicastAddress, String networkInterface, String source, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Leaves a multicast group and so stop listen for packets send to it.
   * The {@link Handler} is notified once the operation completes.
   *
   *
   * @param   multicastAddress  the address of the multicast group to leave
   * @param   handler           then handler to notify once the operation completes
   * @return  this              returns itself for method-chaining
   */
  DatagramSocket unlistenMulticastGroup(String multicastAddress, Handler<AsyncResult<DatagramSocket>> handler);


  /**
   * Leaves a multicast group and so stop listen for packets send to it on the given network interface.
   * The {@link Handler} is notified once the operation completes.
   *
   *
   * @param   multicastAddress  the address of the multicast group to join
   * @param   networkInterface  the network interface on which to listen for packets.
   * @param   source            the address of the source for which we will listen for mulicast packets
   * @param   handler           then handler to notify once the operation completes
   * @return  this              returns itself for method-chaining
   */
  DatagramSocket unlistenMulticastGroup(
          String multicastAddress, String networkInterface, String source,
          Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Block the given sourceToBlock address for the given multicastAddress and notifies the {@link Handler} once
   * the operation completes.
   *
   *
   * @param   multicastAddress  the address for which you want to block the sourceToBlock
   * @param   sourceToBlock     the source address which should be blocked. You will not receive an multicast packets
   *                            for it anymore.
   * @param   handler           then handler to notify once the operation completes
   * @return  this              returns itself for method-chaining
   */
  DatagramSocket blockMulticastGroup(
          String multicastAddress, String sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Block the given sourceToBlock address for the given multicastAddress on the given network interface and notifies
   * the {@link Handler} once the operation completes.
   *
   *
   * @param   multicastAddress  the address for which you want to block the sourceToBlock
   * @param   networkInterface  the network interface on which the blocking should accour.
   * @param   sourceToBlock     the source address which should be blocked. You will not receive an multicast packets
   *                            for it anymore.
   * @param   handler           then handler to notify once the operation completes
   * @return  this              returns itself for method-chaining
   */
  DatagramSocket blockMulticastGroup(
          String multicastAddress, String networkInterface,
          String sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * @see #listen(java.net.InetSocketAddress, org.vertx.java.core.Handler)
   */
  DatagramSocket listen(String address, int port, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * @see #listen(java.net.InetSocketAddress, org.vertx.java.core.Handler)
   */
  DatagramSocket listen(int port, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Makes this {@link DatagramSocket} listen to the given {@link InetSocketAddress}. Once the operation completes
   * the {@link Handler} is notified.
   *
   * @param local     the {@link InetSocketAddress} on which the {@link DatagramSocket} will listen for {@link DatagramPacket}s.
   * @param handler   the {@link Handler} to notify once the operation completes
   * @return this     itself for method-chaining
   */
  DatagramSocket listen(InetSocketAddress local, Handler<AsyncResult<DatagramSocket>> handler);
}
