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

import java.util.Queue;

/**
 * @author swilliams
 *
 */
public class SockJSSocketMXBeanImpl implements SockJSSocketMXBean {

  private final String id;
  private Queue<String> pendingReads;
  private Queue<String> pendingWrites;

  /**
   * @param id
   * @param pendingWrites 
   * @param pendingReads 
   */
  public SockJSSocketMXBeanImpl(String id, Queue<String> pendingReads, Queue<String> pendingWrites) {
    this.id = id;
    this.pendingReads = pendingReads;
    this.pendingWrites = pendingWrites;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public int getPendingReads() {
    return pendingReads.size();
  }

  @Override
  public int getPendingWrites() {
    return pendingWrites.size();
  }

}
