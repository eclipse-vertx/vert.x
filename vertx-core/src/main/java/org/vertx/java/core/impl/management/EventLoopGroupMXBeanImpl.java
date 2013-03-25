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

import io.netty.channel.nio.NioEventLoopGroup;

/**
 * @author swilliams
 *
 */
public class EventLoopGroupMXBeanImpl implements EventLoopGroupMXBean {

  private NioEventLoopGroup loop;

  public EventLoopGroupMXBeanImpl(NioEventLoopGroup loop) {
    this.loop = loop;
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.EventLoopGroupMXBean#isShutdown()
   */
  @Override
  public boolean isShutdown() {
    return loop.isShutdown();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.EventLoopGroupMXBean#isTerminated()
   */
  @Override
  public boolean isTerminated() {
    return loop.isTerminated();
  }

}
