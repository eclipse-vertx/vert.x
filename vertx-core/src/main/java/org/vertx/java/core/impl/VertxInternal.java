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


import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.util.Timer;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.impl.DefaultHttpServer;
import org.vertx.java.core.net.impl.DefaultNetServer;
import org.vertx.java.core.net.impl.ServerID;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * This class provides services for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class VertxInternal extends Vertx {

  public abstract NioServerBossPool getServerAcceptorPool();

  public abstract NioClientBossPool getClientAcceptorPool();

  public abstract ExecutorService getBackgroundPool();

  public abstract Context startOnEventLoop(Runnable runnable);

  public abstract Context startInBackground(Runnable runnable, boolean multiThreaded);

  public abstract Context getOrAssignContext();

  public abstract void reportException(Throwable t);

  public abstract Map<ServerID, DefaultHttpServer> sharedHttpServers();

  public abstract Map<ServerID, DefaultNetServer> sharedNetServers();

  public abstract Timer getTimer();

	/**
	 * Get the current context
	 * @return the context
	 */
	public abstract Context getContext();

	/**
	 * Set the current context
	 */
  public abstract void setContext(Context context);

  /**
   * @return event loop context
   */
  public abstract EventLoopContext createEventLoopContext();
}
