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
package io.vertx.core.datagram;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * A Datagram socket which can be used to send {@link DatagramPacket}'s to remote Datagram servers and receive {@link DatagramPacket}s .
 *
 * Usually you use a Datragram Client to send UDP over the wire. UDP is connection-less which means you are not connected
 * to the remote peer in a persistent way. Because of this you have to supply the address and port of the remote peer
 * when sending data.
 *
 * You can send data to ipv4 or ipv6 addresses, which also include multicast addresses.
 *
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@VertxGen
public interface DatagramSocket extends ReadStream<DatagramPacket>, Measured {

  /**
   * Write the given {@link io.vertx.core.buffer.Buffer} to the {@link io.vertx.core.net.SocketAddress}. The {@link io.vertx.core.Handler} will be notified once the
   * write completes.
   *
   *
   * @param packet    the {@link io.vertx.core.buffer.Buffer} to write
   * @param host      the host address of the remote peer
   * @param port      the host port of the remote peer
   * @param handler   the {@link io.vertx.core.Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  @Fluent
  DatagramSocket send(Buffer packet, int port, String host, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Returns a {@link io.vertx.core.datagram.PacketWritestream} able to send {@link Buffer} to the {@link io.vertx.core.net.SocketAddress}.
   *
   * @param port the host port of the remote peer
   * @param host the host address of the remote peer
   * @return the write stream for sending packets
   */
  PacketWritestream sender(int port, String host);

  /**
   * Write the given {@link String} to the {@link io.vertx.core.net.SocketAddress} using UTF8 encoding. The {@link Handler} will be notified once the
   * write completes.
   *
   *
   * @param str       the {@link String} to write
   * @param host      the host address of the remote peer
   * @param port      the host port of the remote peer
   * @param handler   the {@link io.vertx.core.Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  @Fluent
  DatagramSocket send(String str, int port, String host, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Write the given {@link String} to the {@link io.vertx.core.net.SocketAddress} using the given encoding. The {@link Handler} will be notified once the
   * write completes.
   *
   *
   * @param str       the {@link String} to write
   * @param enc       the charset used for encoding
   * @param host      the host address of the remote peer
   * @param port      the host port of the remote peer
   * @param handler   the {@link io.vertx.core.Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  @Fluent
  DatagramSocket send(String str, String enc, int port, String host, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Close the {@link DatagramSocket} implementation asynchronous and notifies the handler once done.
   */
  void close(Handler<AsyncResult<Void>> handler);

  /**
   * Close the {@link DatagramSocket} implementation asynchronous.
   */
  void close();

  /**
   * Return the {@link io.vertx.core.net.SocketAddress} to which this {@link DatagramSocket} is bound too.
   */
  @CacheReturn
  SocketAddress localAddress();

  /**
   * Joins a multicast group and so start listen for packets send to it. The {@link Handler} is notified once the operation completes.
   *
   *
   * @param   multicastAddress  the address of the multicast group to join
   * @param   handler           then handler to notify once the operation completes
   * @return  this              returns itself for method-chaining
   */
  @Fluent
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
  @Fluent
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
  @Fluent
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
  @Fluent
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
  @Fluent
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
  @Fluent
  DatagramSocket blockMulticastGroup(
    String multicastAddress, String networkInterface,
    String sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler);

  @Fluent
  DatagramSocket listen(int port, String host, Handler<AsyncResult<DatagramSocket>> handler);

  @Override
  DatagramSocket pause();

  @Override
  DatagramSocket resume();

  @Override
  DatagramSocket endHandler(Handler<Void> endHandler);

  @Override
  DatagramSocket handler(Handler<DatagramPacket> handler);

  @Override
  DatagramSocket exceptionHandler(Handler<Throwable> handler);

}
