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

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author swilliams
 *
 */
public class HttpServerMXBeanImpl implements HttpServerMXBean {

  private String host;
  private int port;
  private AtomicLong received;

  /**
   * @param host
   * @param port
   * @param received 
   */
  public HttpServerMXBeanImpl(String host, int port, AtomicLong received) {
    this.host = host;
    this.port = port;
    this.received = received;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  public long getReceivedCount() {
    return received.get();
  }

}
