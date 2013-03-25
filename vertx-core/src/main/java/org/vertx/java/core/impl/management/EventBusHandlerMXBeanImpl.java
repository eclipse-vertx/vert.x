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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author swilliams
 *
 */
public class EventBusHandlerMXBeanImpl implements EventBusHandlerMXBean {

  private String address;
  private boolean localOnly;
  private AtomicLong received;
  private List<?> holders;

  /**
   * @param address
   * @param holders 
   * @param localOnly
   * @param received
   */
  public EventBusHandlerMXBeanImpl(String address, List<?> holders, boolean localOnly, AtomicLong received) {
    this.address = address;
    this.holders = holders;
    this.localOnly = localOnly;
    this.received = received;
  }

  @Override
  public String getAddress() {
    return address;
  }

  @Override
  public boolean isLocalOnly() {
    return localOnly;
  }

  @Override
  public int getActualHandlerCount() {
    return holders.size();
  }

  @Override
  public long getReceived() {
    return received.get();
  }

}
