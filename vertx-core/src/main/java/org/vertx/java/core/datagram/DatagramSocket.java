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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;

/**
 * A socket which allows to communicate via UDP.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface DatagramSocket {

  InetSocketAddress remoteAddress();
  InetSocketAddress localAddress();
  DatagramSocket write(Buffer buffer, InetSocketAddress remote, Handler<AsyncResult<DatagramSocket>> handler);
  DatagramSocket write(Buffer buffer, Handler<AsyncResult<DatagramSocket>> handler);

  DatagramSocket connect(InetSocketAddress remote, Handler<AsyncResult<DatagramSocket>> handler);

  DatagramSocket disconnect(Handler<AsyncResult<DatagramSocket>> handler);

  void close(Handler<AsyncResult<Void>> handler);

  /**
   * Return {@code true} if the {@link DatagramSocket} is connected to the remote peer. In this case it's possible to
   * use {@link #write(org.vertx.java.core.buffer.Buffer, org.vertx.java.core.Handler)} and so not need to specify
   * a remote address.
   */
  boolean isConnected();

  /**
   * Joins a multicast group and notifies the {@link io.netty.channel.ChannelFuture} once the operation completes.
   */
  DatagramSocket joinGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramSocket>> handler);


  /**
   * Joins the specified multicast group at the specified interface and notifies the {@link Handler}
   * once the operation completes.
   */
  DatagramSocket joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<DatagramSocket>> handler);


  /**
   * Joins the specified multicast group at the specified interface and notifies the {@link Handler}
   * once the operation completes.
   */
  DatagramSocket joinGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Leaves a multicast group and notifies the {@link Handler} once the operation completes.
   */
  DatagramSocket leaveGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Leaves a multicast group on a specified local interface and notifies the {@link Handler} once the
   * operation completes.
   */
  DatagramSocket leaveGroup(
          InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<DatagramSocket>> handler);


  /**
   * Leave the specified multicast group at the specified interface using the specified source and notifies
   * the {@link Handler} once the operation completes.
   */
  DatagramSocket leaveGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
          Handler<AsyncResult<DatagramSocket>> handler);

  /**
   * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface and notifies
   * the {@link Handler} once the operation completes.
   */
  DatagramSocket block(
          InetAddress multicastAddress, NetworkInterface networkInterface,
          InetAddress sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler);


  /**
   * Block the given sourceToBlock address for the given multicastAddress and notifies the {@link Handler} once
   * the operation completes.
   */
  DatagramSocket block(
          InetAddress multicastAddress, InetAddress sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler);

  DatagramSocket packetHandler(Handler<DatagramPacket> handler);

  DatagramSocket exceptionHandler(Handler<Throwable> handler);

  DatagramSocket closeHandler(Handler<Void> handler);

  /**
   * Set the maximum size of the write queue to {@code maxSize}. You will still be able to write to the stream even
   * if there is more than {@code maxSize} bytes in the write queue. This is used as an indicator by classes such as
   * {@code Pump} to provide flow control.
   */
  DatagramSocket setWriteQueueMaxSize(int maxSize);

  /**
   * This will return {@code true} if there are more bytes in the write queue than the value set using {@link
   * #setWriteQueueMaxSize}
   */
  boolean writeQueueFull();

  /**
   * Set a drain handler on the stream. If the write queue is full, then the handler will be called when the write
   * queue has been reduced to maxSize / 2. See {@link org.vertx.java.core.streams.Pump} for an example of this being used.
   */
  DatagramSocket drainHandler(Handler<Void> handler);
}
