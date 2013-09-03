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

import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

import java.net.InetSocketAddress;

/**
 * A socket which allows to communicate via UDP.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface ConnectedDatagramChannel extends DatagramChannel<ConnectedDatagramChannel>, ReadStream<ConnectedDatagramChannel> , WriteStream<ConnectedDatagramChannel>{

  /**
   * Return the {@link InetSocketAddress} of the remote peer to which this {@link ConnectedDatagramChannel} is connected. If
   * the {@link ConnectedDatagramChannel} is not connected to a remote-peer it will return {@code null}.
   */
  InetSocketAddress remoteAddress();
}
