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
 * A {@link DatagramServer} which can be used for write and receive datagram packets.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface DatagramServer extends DatagramSupport<DatagramServer>, ExceptionSupport<DatagramServer> {

  /**
   * Return the {@link InetSocketAddress} to which this {@link DatagramServer} is bound too.
   */
  InetSocketAddress localAddress();

  /**
   * Joins a multicast group and notifies the {@link io.netty.channel.ChannelFuture} once the operation completes.
   */
  DatagramServer joinGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramServer>> handler);

  /**
   * Joins the specified multicast group at the specified interface and notifies the {@link Handler}
   * once the operation completes.
   */
  DatagramServer joinGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<DatagramServer>> handler);

  /**
   * Leaves a multicast group and notifies the {@link Handler} once the operation completes.
   */
  DatagramServer leaveGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramServer>> handler);

  /**
   * Leave the specified multicast group at the specified interface using the specified source and notifies
   * the {@link Handler} once the operation completes.
   */
  DatagramServer leaveGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
          Handler<AsyncResult<DatagramServer>> handler);

  /**
   * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface and notifies
   * the {@link Handler} once the operation completes.
   */
  DatagramServer block(
          InetAddress multicastAddress, NetworkInterface networkInterface,
          InetAddress sourceToBlock, Handler<AsyncResult<DatagramServer>> handler);

  /**
   * Block the given sourceToBlock address for the given multicastAddress and notifies the {@link Handler} once
   * the operation completes.
   */
  DatagramServer block(
          InetAddress multicastAddress, InetAddress sourceToBlock, Handler<AsyncResult<DatagramServer>> handler);

  /**
   * Set a data handler. As data is read, the handler will be called with the data.
   */
  DatagramServer dataHandler(Handler<DatagramPacket> packetHandler);


  /**
   * @see #listen(java.net.InetSocketAddress, org.vertx.java.core.Handler)
   */
  DatagramServer listen(String address, int port, Handler<AsyncResult<DatagramServer>> handler);

  /**
   * @see #listen(java.net.InetSocketAddress, org.vertx.java.core.Handler)
   */
  DatagramServer listen(int port, Handler<AsyncResult<DatagramServer>> handler);

  DatagramServer listen(InetSocketAddress local, Handler<AsyncResult<DatagramServer>> handler);
}
