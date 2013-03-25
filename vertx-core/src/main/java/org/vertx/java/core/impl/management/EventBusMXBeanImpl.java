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

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author swilliams
 *
 */
public class EventBusMXBeanImpl implements EventBusMXBean {

  private int port;

  private String host;

  private String prefix;

  private ConcurrentMap<?, ?> connections;

  private ConcurrentMap<?, ?> handlerMap;

  private AtomicLong sentMessages;

  /**
   * @param host
   * @param port
   * @param prefix 
   * @param sentMessages 
   * @param handlerMap 
   * @param connections 
   */
  public EventBusMXBeanImpl(String host, int port, String prefix, AtomicLong sentMessages, ConcurrentMap<?, ?> connections, ConcurrentMap<?, ?> handlerMap) {
    this.host = host;
    this.port = port;
    this.prefix = prefix;
    this.sentMessages = sentMessages;
    this.connections = connections;
    this.handlerMap = handlerMap;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public String getPrefix() {
    return prefix;
  }

  @Override
  public long getSentCount() {
    return sentMessages.get();
  }

  @Override
  public int getConnectionCount() {
    return connections.size();
  }

  @Override
  public int getHandlerCount() {
    return handlerMap.size();
  }

}
