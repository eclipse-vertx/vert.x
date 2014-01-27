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

package org.vertx.java.platform.impl;

import io.netty.channel.EventLoopGroup;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Context;
import org.vertx.java.core.Handler;
import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.datagram.InternetProtocolFamily;
import org.vertx.java.core.dns.DnsClient;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.impl.DefaultHttpServer;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.EventLoopContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.impl.DefaultNetServer;
import org.vertx.java.core.net.impl.ServerID;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.spi.Action;
import org.vertx.java.core.spi.cluster.ClusterManager;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class WrappedVertx implements VertxInternal {

  private final VertxInternal vertx;

  public WrappedVertx(VertxInternal vertx) {
    this.vertx = vertx;
  }

  /*
  We override this to prevent any new contexts being created from within Verticles
  This is important e.g. if a user is using a 3rd party library which returns a result on it's own thread,
  we don't then want a new context being created if the user calls runOnContext
   */
  @Override
  public void runOnContext(final Handler<Void> task) {
    DefaultContext context = getContext();
    if (context == null) {
      throw new IllegalStateException("Not on a Vert.x context");
    }
    context.runOnContext(task);
  }

  @Override
  public boolean isEventLoop() {
    return vertx.isEventLoop();
  }

  @Override
  public boolean isWorker() {
    return vertx.isWorker();
  }

  @Override
  public void stop() {
    vertx.stop();
  }

  @Override
  public EventLoopGroup getEventLoopGroup() {
    return vertx.getEventLoopGroup();
  }

  @Override
  public ExecutorService getBackgroundPool() {
    return vertx.getBackgroundPool();
  }

  @Override
  public DefaultContext startOnEventLoop(Runnable runnable) {
    return vertx.startOnEventLoop(runnable);
  }

  @Override
  public DefaultContext startInBackground(Runnable runnable, boolean multiThreaded) {
    return vertx.startInBackground(runnable, multiThreaded);
  }

  @Override
  public DefaultContext getOrCreateContext() {
    return vertx.getOrCreateContext();
  }

  @Override
  public void reportException(Throwable t) {
    vertx.reportException(t);
  }

  @Override
  public Map<ServerID, DefaultHttpServer> sharedHttpServers() {
    return vertx.sharedHttpServers();
  }

  @Override
  public Map<ServerID, DefaultNetServer> sharedNetServers() {
    return vertx.sharedNetServers();
  }

  @Override
  public DefaultContext getContext() {
    return vertx.getContext();
  }

  @Override
  public void setContext(DefaultContext context) {
    vertx.setContext(context);
  }

  @Override
  public EventLoopContext createEventLoopContext() {
    return vertx.createEventLoopContext();
  }

  @Override
  public ClusterManager clusterManager() {
    return vertx.clusterManager();
  }

  @Override
  public NetServer createNetServer() {
    return vertx.createNetServer();
  }

  @Override
  public NetClient createNetClient() {
    return vertx.createNetClient();
  }

  @Override
  public HttpServer createHttpServer() {
    return vertx.createHttpServer();
  }

  @Override
  public HttpClient createHttpClient() {
    return vertx.createHttpClient();
  }

  @Override
  public SockJSServer createSockJSServer(HttpServer httpServer) {
    return vertx.createSockJSServer(httpServer);
  }

  @Override
  public FileSystem fileSystem() {
    return vertx.fileSystem();
  }

  @Override
  public EventBus eventBus() {
    return vertx.eventBus();
  }

  @Override
  public SharedData sharedData() {
    return vertx.sharedData();
  }

  @Override
  public long setTimer(long delay, Handler<Long> handler) {
    return vertx.setTimer(delay, handler);
  }

  @Override
  public long setPeriodic(long delay, Handler<Long> handler) {
    return vertx.setPeriodic(delay, handler);
  }

  @Override
  public boolean cancelTimer(long id) {
    return vertx.cancelTimer(id);
  }

  @Override
  public Context currentContext() {
    return vertx.currentContext();
  }

  @Override
  public DnsClient createDnsClient(InetSocketAddress... dnsServers) {
    return vertx.createDnsClient(dnsServers);
  }

  @Override
  public <T> void executeBlocking(Action<T> action, Handler<AsyncResult<T>> resultHandler) {
    vertx.executeBlocking(action, resultHandler);
  }

  @Override
  public DatagramSocket createDatagramSocket(InternetProtocolFamily family) {
    return vertx.createDatagramSocket(family);
  }
}
