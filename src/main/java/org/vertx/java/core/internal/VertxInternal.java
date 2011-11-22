/*
 * Copyright 2011 the original author or authors.
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

package org.vertx.java.core.internal;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxApp;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * This class provides services for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers using vert.x
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface VertxInternal extends Vertx {

  static VertxInternal instance = (VertxInternal) Vertx.instance;

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

  void startApp(Class<? extends VertxApp> appClass, int instances);

  void stopApp(Class appClass, int instances);
}
