/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core.impl;


import io.netty.channel.EventLoopGroup;
import org.vertx.java.core.http.impl.DefaultHttpServer;
import org.vertx.java.core.net.impl.DefaultNetServer;
import org.vertx.java.core.net.impl.ServerID;
import org.vertx.java.core.spi.VertxSPI;
import org.vertx.java.core.spi.cluster.ClusterManager;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * This interface provides services for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface VertxInternal extends VertxSPI {

  EventLoopGroup getEventLoopGroup();

  ExecutorService getBackgroundPool();

  DefaultContext startOnEventLoop(Runnable runnable);

  DefaultContext startInBackground(Runnable runnable, boolean multiThreaded);

  DefaultContext getOrCreateContext();

  void reportException(Throwable t);

  Map<ServerID, DefaultHttpServer> sharedHttpServers();

  Map<ServerID, DefaultNetServer> sharedNetServers();

	/**
	 * Get the current context
	 * @return the context
	 */
	DefaultContext getContext();

	/**
	 * Set the current context
	 */
  void setContext(DefaultContext context);

  /**
   * @return event loop context
   */
  EventLoopContext createEventLoopContext();

  ClusterManager clusterManager();
}
