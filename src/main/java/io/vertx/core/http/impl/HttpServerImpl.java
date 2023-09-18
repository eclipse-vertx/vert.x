/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.streams.ReadStream;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerImpl extends TCPServerBase implements HttpServer, Closeable, MetricsProvider {

  static final Logger log = LoggerFactory.getLogger(HttpServerImpl.class);

  private static final Handler<Throwable> DEFAULT_EXCEPTION_HANDLER = t -> log.trace("Connection failure", t);

  private static final String FLASH_POLICY_HANDLER_PROP_NAME = "vertx.flashPolicyHandler";
  private static final String DISABLE_WEBSOCKETS_PROP_NAME = "vertx.disableWebsockets";
  private static final String DISABLE_H2C_PROP_NAME = "vertx.disableH2c";

  static final boolean USE_FLASH_POLICY_HANDLER = Boolean.getBoolean(FLASH_POLICY_HANDLER_PROP_NAME);
  static final boolean DISABLE_WEBSOCKETS = Boolean.getBoolean(DISABLE_WEBSOCKETS_PROP_NAME);

  final HttpServerOptions options;
  private final boolean disableH2c;
  private final HttpStreamHandler<ServerWebSocket> wsStream = new HttpStreamHandler<>();
  private final HttpStreamHandler<HttpServerRequest> requestStream = new HttpStreamHandler<>();
  private Handler<HttpServerRequest> invalidRequestHandler;
  private Handler<HttpConnection> connectionHandler;

  private Handler<Throwable> exceptionHandler;

  public HttpServerImpl(VertxInternal vertx, HttpServerOptions options) {
    super(vertx, options);
    this.options = new HttpServerOptions(options);
    this.disableH2c = Boolean.getBoolean(DISABLE_H2C_PROP_NAME) || options.isSsl();
  }

  @Override
  protected TCPMetrics<?> createMetrics(SocketAddress localAddress) {
    VertxMetrics vertxMetrics = vertx.metricsSPI();
    if (vertxMetrics != null) {
      return vertxMetrics.createHttpServerMetrics(options, localAddress);
    } else {
      return null;
    }
  }

  @Override
  public synchronized HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    requestStream.handler(handler);
    return this;
  }

  @Override
  public ReadStream<HttpServerRequest> requestStream() {
    return requestStream;
  }

  @Override
  public HttpServer webSocketHandler(Handler<ServerWebSocket> handler) {
    webSocketStream().handler(handler);
    return this;
  }

  @Override
  public Handler<HttpServerRequest> requestHandler() {
    return requestStream.handler();
  }

  @Override
  public HttpServer invalidRequestHandler(Handler<HttpServerRequest> handler) {
    invalidRequestHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpServer connectionHandler(Handler<HttpConnection> handler) {
    if (isListening()) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    connectionHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpServer exceptionHandler(Handler<Throwable> handler) {
    if (isListening()) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    exceptionHandler = handler;
    return this;
  }

  @Override
  public Handler<ServerWebSocket> webSocketHandler() {
    return wsStream.handler();
  }

  @Override
  public ReadStream<ServerWebSocket> webSocketStream() {
    return wsStream;
  }

  @Override
  public Future<HttpServer> listen() {
    return listen(options.getPort(), options.getHost());
  }

  @Override
  protected BiConsumer<Channel, SslChannelProvider> childHandler(ContextInternal context, SocketAddress address, GlobalTrafficShapingHandler trafficShapingHandler) {
    ContextInternal connContext;
    if (context.isEventLoopContext()) {
      connContext = context;
    } else {
      connContext = vertx.createEventLoopContext(context.nettyEventLoop(), context.workerPool(), context.classLoader());
    }
    String host = address.isInetSocket() ? address.host() : "localhost";
    int port = address.port();
    String serverOrigin = (options.isSsl() ? "https" : "http") + "://" + host + ":" + port;
    HttpServerConnectionHandler hello = new HttpServerConnectionHandler(this, requestStream.handler, invalidRequestHandler, wsStream.handler, connectionHandler, exceptionHandler == null ? DEFAULT_EXCEPTION_HANDLER : exceptionHandler);
    Supplier<ContextInternal> streamContextSupplier = context::duplicate;
    return new HttpServerWorker(
      connContext,
      streamContextSupplier,
      this,
      vertx,
      options,
      serverOrigin,
      disableH2c,
      hello,
      hello.exceptionHandler,
      trafficShapingHandler);
  }

  @Override
  protected SSLHelper createSSLHelper() {
    return new SSLHelper(options, options
      .getAlpnVersions()
      .stream()
      .map(HttpVersion::alpnName)
      .collect(Collectors.toList()));
  }

  @Override
  public synchronized Future<HttpServer> listen(SocketAddress address) {
    if (requestStream.handler() == null && wsStream.handler() == null) {
      throw new IllegalStateException("Set request or WebSocket handler first");
    }
    return bind(address).map(this);
  }

  @Override
  public Future<Void> close() {
    ContextInternal context = vertx.getOrCreateContext();
    PromiseInternal<Void> promise = context.promise();
    close(promise);
    return promise.future();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> done) {
    ContextInternal context = vertx.getOrCreateContext();
    PromiseInternal<Void> promise = context.promise();
    close(promise);
    if (done != null) {
      promise.future().onComplete(done);
    }
  }

  public synchronized void close(Promise<Void> completion) {
    if (wsStream.endHandler() != null || requestStream.endHandler() != null) {
      Handler<Void> wsEndHandler = wsStream.endHandler();
      wsStream.endHandler(null);
      Handler<Void> requestEndHandler = requestStream.endHandler();
      requestStream.endHandler(null);
      completion.future().onComplete(ar -> {
        if (wsEndHandler != null) {
          wsEndHandler.handle(null);
        }
        if (requestEndHandler != null) {
          requestEndHandler.handle(null);
        }
      });
    }
    super.close(completion);
  }

  public boolean isClosed() {
    return !isListening();
  }

  boolean requestAccept() {
    return requestStream.accept();
  }

  boolean wsAccept() {
    return wsStream.accept();
  }

  /*
    Needs to be protected using the HttpServerImpl monitor as that protects the listening variable
    In practice synchronized overhead should be close to zero assuming most access is from the same thread due
    to biased locks
  */
  class HttpStreamHandler<C extends ReadStream<Buffer>> implements ReadStream<C> {

    private Handler<C> handler;
    private long demand = Long.MAX_VALUE;
    private Handler<Void> endHandler;

    Handler<C> handler() {
      synchronized (HttpServerImpl.this) {
        return handler;
      }
    }

    boolean accept() {
      synchronized (HttpServerImpl.this) {
        boolean accept = demand > 0L;
        if (accept && demand != Long.MAX_VALUE) {
          demand--;
        }
        return accept;
      }
    }

    Handler<Void> endHandler() {
      synchronized (HttpServerImpl.this) {
        return endHandler;
      }
    }

    @Override
    public ReadStream handler(Handler<C> handler) {
      synchronized (HttpServerImpl.this) {
        if (isListening()) {
          throw new IllegalStateException("Please set handler before server is listening");
        }
        this.handler = handler;
        return this;
      }
    }

    @Override
    public ReadStream pause() {
      synchronized (HttpServerImpl.this) {
        demand = 0L;
        return this;
      }
    }

    @Override
    public ReadStream fetch(long amount) {
      if (amount > 0L) {
        demand += amount;
        if (demand < 0L) {
          demand = Long.MAX_VALUE;
        }
      }
      return this;
    }

    @Override
    public ReadStream resume() {
      synchronized (HttpServerImpl.this) {
        demand = Long.MAX_VALUE;
        return this;
      }
    }

    @Override
    public ReadStream endHandler(Handler<Void> endHandler) {
      synchronized (HttpServerImpl.this) {
        this.endHandler = endHandler;
        return this;
      }
    }

    @Override
    public ReadStream exceptionHandler(Handler<Throwable> handler) {
      // Should we use it in the server close exception handler ?
      return this;
    }
  }

}
