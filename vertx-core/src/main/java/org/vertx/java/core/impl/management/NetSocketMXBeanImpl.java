/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.impl.management;

import org.vertx.java.core.net.impl.DefaultNetSocket;

/**
 * @author swilliams
 *
 */
public class NetSocketMXBeanImpl implements NetSocketMXBean {

  private DefaultNetSocket sock;

  /**
   * @param sock
   */
  public NetSocketMXBeanImpl(DefaultNetSocket sock) {
    this.sock = sock;
  }

  @Override
  public String getWriteHandlerId() {
    return sock.writeHandlerID;
  }

  public long getReceivedBytes() {
    return sock.getReceivedCount();
  }

  public long getSentBytes() {
    return sock.getSentCount();
  }

}
