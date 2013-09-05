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
import org.vertx.java.core.streams.ExceptionSupport;

import java.net.InetSocketAddress;


/**
 * A bound {@link DatagramChannel} which can be used for write and receive datagram packets.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface BoundDatagramChannel extends DatagramChannel<BoundDatagramChannel, DatagramPacket>, ExceptionSupport<BoundDatagramChannel> {

  /**
   * Write the given {@link Buffer} to the {@link InetSocketAddress}. The {@link Handler} will be notified once the
   * write completes.
   *
   * @param packet    the {@link Buffer} to write
   * @param remote    the {@link InetSocketAddress} which is the remote peer
   * @param handler   the {@link Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  BoundDatagramChannel write(Buffer packet, InetSocketAddress remote,  Handler<AsyncResult<BoundDatagramChannel>> handler);

  /**
   * Write the given {@link String} to the {@link InetSocketAddress} using UTF8 encoding. The {@link Handler} will be notified once the
   * write completes.
   *
   * @param str       the {@link String} to write
   * @param remote    the {@link InetSocketAddress} which is the remote peer
   * @param handler   the {@link Handler} to notify once the write completes.
   * @return self     itself for method chaining
   */
  BoundDatagramChannel write(String str, InetSocketAddress remote, Handler<AsyncResult<BoundDatagramChannel>> handler);
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
  BoundDatagramChannel write(String str, String enc, InetSocketAddress remote, Handler<AsyncResult<BoundDatagramChannel>> handler);
}
