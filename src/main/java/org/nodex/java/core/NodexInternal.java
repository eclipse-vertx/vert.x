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

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public interface NodexInternal extends Nodex {

  static NodexInternal instance = new NodexImpl();

  NioWorkerPool getWorkerPool();

  Executor getAcceptorPool();

  void executeOnContext(long contextID, Runnable runnable);

  long createAndAssociateContext();

  long associateContextWithWorker(NioWorker worker);

  boolean destroyContext(long contextID);

  void setContextID(long contextID);

  void executeInBackground(Runnable runnable);

  ExecutorService getBackgroundPool();

  NioWorker getWorkerForContextID(long contextID);

  int getCoreThreadPoolSize();
}
