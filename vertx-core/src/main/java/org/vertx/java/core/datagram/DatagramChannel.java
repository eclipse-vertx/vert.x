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
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.DrainSupport;
import org.vertx.java.core.streams.ExceptionSupport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;



/**
 * A bound {@link DatagramChannel} which can be used for write and receive datagram packets.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface DatagramChannel extends DrainSupport<DatagramChannel>, ExceptionSupport<DatagramChannel> {

  /**
   * Return the {@link InetSocketAddress} to which this {@link DatagramChannel} is bound too.
   */
  InetSocketAddress localAddress();

  /**
   * Close this {@link DatagramChannel} and notify the given handler once done.
   */
  void close(Handler<AsyncResult<Void>> handler);

  /**
   * Close this {@link DatagramChannel} in an async fashion.
   */
  void close();

  /**
   * Joins a multicast group and notifies the {@link io.netty.channel.ChannelFuture} once the operation completes.
   */
  DatagramChannel joinGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Joins the specified multicast group at the specified interface and notifies the {@link Handler}
   * once the operation completes.
   */
  DatagramChannel joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Joins the specified multicast group at the specified interface and notifies the {@link Handler}
   * once the operation completes.
   */
  DatagramChannel joinGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Leaves a multicast group and notifies the {@link Handler} once the operation completes.
   */
  DatagramChannel leaveGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Leaves a multicast group on a specified local interface and notifies the {@link Handler} once the
   * operation completes.
   */
  DatagramChannel leaveGroup(
          InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Leave the specified multicast group at the specified interface using the specified source and notifies
   * the {@link Handler} once the operation completes.
   */
  DatagramChannel leaveGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
          Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface and notifies
   * the {@link Handler} once the operation completes.
   */
  DatagramChannel block(
          InetAddress multicastAddress, NetworkInterface networkInterface,
          InetAddress sourceToBlock, Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Block the given sourceToBlock address for the given multicastAddress and notifies the {@link Handler} once
   * the operation completes.
   */
  DatagramChannel block(
          InetAddress multicastAddress, InetAddress sourceToBlock, Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Set a data handler. As data is read, the handler will be called with the data.
   */
  DatagramChannel dataHandler(Handler<DatagramPacket> packetHandler);

  /**
   * Write the given {@link org.vertx.java.core.buffer.Buffer} to the {@link InetSocketAddress}. The {@link Handler} will be notified once the
   * write completes.
   *
   * @param packet    the {@link org.vertx.java.core.buffer.Buffer} to write
   * @param remote    the {@link InetSocketAddress} which is the remote peer
   * @param handler   the {@link Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  DatagramChannel write(Buffer packet, InetSocketAddress remote,  Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Write the given {@link String} to the {@link InetSocketAddress} using UTF8 encoding. The {@link Handler} will be notified once the
   * write completes.
   *
   * @param str       the {@link String} to write
   * @param remote    the {@link InetSocketAddress} which is the remote peer
   * @param handler   the {@link Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  DatagramChannel write(String str, InetSocketAddress remote, Handler<AsyncResult<DatagramChannel>> handler);

  /**
   * Write the given {@link String} to the {@link InetSocketAddress} using the given encoding. The {@link Handler} will be notified once the
   * write completes.
   *
   * @param str       the {@link String} to write
   * @param enc       the charset used for encoding
   * @param remote    the {@link InetSocketAddress} which is the remote peer
   * @param handler   the {@link Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  DatagramChannel write(String str, String enc, InetSocketAddress remote, Handler<AsyncResult<DatagramChannel>> handler);
}
