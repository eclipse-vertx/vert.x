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

package org.vertx.java.core;

import org.jboss.netty.channel.socket.nio.NioWorker;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends BaseContext {

  private final NioWorker worker;

  public EventLoopContext(NioWorker worker) {
    this.worker = worker;
  }

  public void execute(Runnable task) {
    worker.scheduleOtherTask(wrapTask(task));
  }

  public NioWorker getWorker() {
    return worker;
  }
}
