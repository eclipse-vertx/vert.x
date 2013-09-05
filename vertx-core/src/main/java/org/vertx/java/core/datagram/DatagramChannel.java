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
import org.vertx.java.core.streams.DrainSupport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface DatagramChannel<T extends DatagramChannel> extends DrainSupport<T> {

  /**
   * Return the {@link InetSocketAddress} to which this {@link ConnectedDatagramChannel} is bound too.
   */
  InetSocketAddress localAddress();

  /**
   * Close this {@link ConnectedDatagramChannel} and notify the given handler once done.
   */
  void close(Handler<AsyncResult<Void>> handler);

  void close();

  /**
   * Joins a multicast group and notifies the {@link io.netty.channel.ChannelFuture} once the operation completes.
   */
  T joinGroup(InetAddress multicastAddress, Handler<AsyncResult<T>> handler);


  /**
   * Joins the specified multicast group at the specified interface and notifies the {@link Handler}
   * once the operation completes.
   */
  T joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<T>> handler);


  /**
   * Joins the specified multicast group at the specified interface and notifies the {@link Handler}
   * once the operation completes.
   */
  T joinGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<T>> handler);

  /**
   * Leaves a multicast group and notifies the {@link Handler} once the operation completes.
   */
  T leaveGroup(InetAddress multicastAddress, Handler<AsyncResult<T>> handler);

  /**
   * Leaves a multicast group on a specified local interface and notifies the {@link Handler} once the
   * operation completes.
   */
  T leaveGroup(
          InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<T>> handler);


  /**
   * Leave the specified multicast group at the specified interface using the specified source and notifies
   * the {@link Handler} once the operation completes.
   */
  T leaveGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
          Handler<AsyncResult<T>> handler);

  /**
   * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface and notifies
   * the {@link Handler} once the operation completes.
   */
  T block(
          InetAddress multicastAddress, NetworkInterface networkInterface,
          InetAddress sourceToBlock, Handler<AsyncResult<T>> handler);


  /**
   * Block the given sourceToBlock address for the given multicastAddress and notifies the {@link Handler} once
   * the operation completes.
   */
  T block(
          InetAddress multicastAddress, InetAddress sourceToBlock, Handler<AsyncResult<T>> handler);
}
