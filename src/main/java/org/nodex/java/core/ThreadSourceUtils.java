/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core;

import org.jboss.netty.channel.socket.nio.NioSocketChannel;

public class ThreadSourceUtils {

  /*
  Currently Netty does not provide all events for a connection on the same thread - e.g. connection open
  connection bound etc are provided on the acceptor thread.
  In node.x we must ensure all events are executed on the correct event loop for the context
  So for now we need to do this manually by checking the thread and executing it on the event loop
  thread if it's not the right one.
  This code will go away if Netty acts like a proper event loop.
   */
  public static void runOnCorrectThread(NioSocketChannel nch, Runnable runnable) {
    if (Thread.currentThread() != nch.getWorker().getThread()) {
      nch.getWorker().scheduleOtherTask(runnable);
    } else {
      runnable.run();
    }
  }
}
