/*
 * Copyright 2011-2012 the original author or authors.
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

package org.vertx.java.core.impl;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxThreadFactory implements ThreadFactory {

  private final String prefix;
  private final AtomicInteger threadCount = new AtomicInteger(0);

  VertxThreadFactory(String prefix) {
    this.prefix = prefix;
  }

  public Thread newThread(Runnable runnable) {
    Thread t = new Thread(runnable, prefix + threadCount.getAndIncrement());
    // All vert.x threads are daemons
    t.setDaemon(true);
    return t;
  }
}
