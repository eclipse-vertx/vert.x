/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.AddressHelper;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.CodecManager;
import io.vertx.core.eventbus.impl.EventBusImpl;
import io.vertx.core.eventbus.impl.HandlerHolder;
import io.vertx.core.eventbus.impl.HandlerRegistration;
import io.vertx.core.eventbus.impl.MessageImpl;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.impl.utils.ConcurrentCyclicSequence;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetClientBuilder;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.impl.NodeSelector;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An event bus implementation that clusters with other Vert.x nodes
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredEventBus extends EventBusImpl {

  private static final Logger log = LoggerFactory.getLogger(ClusteredEventBus.class);

  private static final Buffer PONG = Buffer.buffer(new byte[]{(byte) 1});

  private final EventBusOptions options;
  private final ClusterManager clusterManager;
  private final NodeSelector nodeSelector;
  private final AtomicLong handlerSequence = new AtomicLong(0);
  private final NetClient client;

  private final ConcurrentMap<String, ConnectionHolder> connections = new ConcurrentHashMap<>();
  private final ContextInternal ebContext;

  private NodeInfo nodeInfo;
  private String nodeId;
  private NetServer server;

  public ClusteredEventBus(VertxInternal vertx, VertxOptions options, ClusterManager clusterManager, NodeSelector nodeSelector) {
    super(vertx);

    NetClient client = createNetClient(vertx, new NetClientOptions(options.getEventBusOptions().toJson())
      .setHostnameVerificationAlgorithm("")
    );

    this.options = options.getEventBusOptions();
    this.clusterManager = clusterManager;
    this.nodeSelector = nodeSelector;
    this.ebContext = vertx.createEventLoopContext(null, new CloseFuture(), null, Thread.currentThread().getContextClassLoader());
    this.client = client;
  }

  private NetClient createNetClient(VertxInternal vertx, NetClientOptions clientOptions) {
    NetClientBuilder builder = new NetClientBuilder(vertx, clientOptions);
    VertxMetrics metricsSPI = vertx.metricsSPI();
    if (metricsSPI != null) {
      builder.metrics(metricsSPI.createNetClientMetrics(clientOptions));
    }
    return builder.build();
  }

  NetClient client() {
    return client;
  }

  private NetServerOptions getServerOptions() {
    return new NetServerOptions(this.options.toJson());
  }

  @Override
  public void start(Promise<Void> promise) {
    NetServerOptions serverOptions = getServerOptions();
    server = vertx.createNetServer(serverOptions);
    server.connectHandler(getServerHandler());
    int port = getClusterPort();
    String host = getClusterHost();
    ebContext.runOnContext(v -> {
      server.listen(port, host).flatMap(v2 -> {
        int publicPort = getClusterPublicPort(server.actualPort());
        String publicHost = getClusterPublicHost(host);
        nodeInfo = new NodeInfo(publicHost, publicPort, options.getClusterNodeMetadata());
        nodeId = clusterManager.getNodeId();
        Promise<Void> setPromise = Promise.promise();
        clusterManager.setNodeInfo(nodeInfo, setPromise);
        return setPromise.future();
      }).andThen(ar -> {
        if (ar.succeeded()) {
          started = true;
          nodeSelector.eventBusStarted();
        }
      }).onComplete(promise);
    });
  }

  @Override
  public void close(Promise<Void> promise) {
    Promise<Void> parentClose = Promise.promise();
    super.close(parentClose);
    parentClose.future()
      .transform(ar -> client.close())
      .andThen(ar -> {
        if (server != null) {
          // TODO CLOSE SERVER TOO
          // Close all outbound connections explicitly - don't rely on context hooks
          for (ConnectionHolder holder : connections.values()) {
            holder.close();
          }
        }
      })
      .onComplete(promise);
  }

  @Override
  public MessageImpl createMessage(boolean send, boolean local, String address, MultiMap headers, Object body, String codecName) {
    Objects.requireNonNull(address, "no null address accepted");
    MessageCodec codec = codecManager.lookupCodec(body, codecName, local);
    @SuppressWarnings("unchecked")
    ClusteredMessage msg = new ClusteredMessage(nodeId, address, headers, body, codec, send, this);
    return msg;
  }

  @Override
  protected <T> void onLocalRegistration(HandlerHolder<T> handlerHolder, Promise<Void> promise) {
    RegistrationInfo registrationInfo = new RegistrationInfo(
      nodeId,
      handlerHolder.getSeq(),
      handlerHolder.isLocalOnly()
    );
    clusterManager.addRegistration(handlerHolder.getHandler().address(), registrationInfo, Objects.requireNonNull(promise));
  }

  @Override
  protected <T> HandlerHolder<T> createHandlerHolder(HandlerRegistration<T> registration, boolean localOnly, ContextInternal context) {
    return new ClusteredHandlerHolder<>(registration, localOnly, context, handlerSequence.getAndIncrement());
  }

  @Override
  protected <T> void onLocalUnregistration(HandlerHolder<T> handlerHolder, Promise<Void> completionHandler) {
    RegistrationInfo registrationInfo = new RegistrationInfo(
      nodeId,
      handlerHolder.getSeq(),
      handlerHolder.isLocalOnly()
    );
    Promise<Void> promise = Promise.promise();
    clusterManager.removeRegistration(handlerHolder.getHandler().address(), registrationInfo, promise);
    promise.future().onComplete(completionHandler);
  }

  @Override
  protected <T> void sendOrPub(ContextInternal ctx, MessageImpl<?, T> message, DeliveryOptions options, Promise<Void> writePromise) {
    if (((ClusteredMessage) message).getRepliedTo() != null) {
      clusteredSendReply(message, writePromise, ((ClusteredMessage) message).getRepliedTo());
    } else if (options.isLocalOnly()) {
      sendLocally(message, writePromise);
    } else {
      Serializer serializer = Serializer.get(ctx);
      if (message.isSend()) {
        Promise<String> promise = ctx.promise();
        serializer.queue(message, nodeSelector::selectForSend, promise);
        promise.future().onComplete(ar -> {
          if (ar.succeeded()) {
            sendToNode(ar.result(), message, writePromise);
          } else {
            sendOrPublishFailed(writePromise, ar.cause());
          }
        });
      } else {
        Promise<Iterable<String>> promise = ctx.promise();
        serializer.queue(message, nodeSelector::selectForPublish, promise);
        promise.future().onComplete(ar -> {
          if (ar.succeeded()) {
            sendToNodes(ar.result(), message, writePromise);
          } else {
            sendOrPublishFailed(writePromise, ar.cause());
          }
        });
      }
    }
  }

  private void sendOrPublishFailed(Promise<Void> promise, Throwable cause) {
    if (log.isDebugEnabled()) {
      log.error("Failed to send message", cause);
    }
    promise.tryFail(cause);
  }

  @Override
  protected String generateReplyAddress() {
    // The address is a cryptographically secure id that can't be guessed
    return "__vertx.reply." + UUID.randomUUID().toString();
  }

  @Override
  protected boolean isMessageLocal(MessageImpl msg) {
    ClusteredMessage clusteredMessage = (ClusteredMessage) msg;
    return !clusteredMessage.isFromWire();
  }

  @Override
  protected HandlerHolder nextHandler(ConcurrentCyclicSequence<HandlerHolder> handlers, boolean messageLocal) {
    HandlerHolder handlerHolder = null;
    if (messageLocal) {
      handlerHolder = handlers.next();
    } else {
      Iterator<HandlerHolder> iterator = handlers.iterator(false);
      while (iterator.hasNext()) {
        HandlerHolder next = iterator.next();
        if (!next.isLocalOnly()) {
          handlerHolder = next;
          break;
        }
      }
    }
    return handlerHolder;
  }

  private int getClusterPort() {
    return options.getPort();
  }

  private String getClusterHost() {
    String host;
    if ((host = options.getHost()) != null) {
      return host;
    }
    if ((host = clusterManager.clusterHost()) != null) {
      return host;
    }
    return AddressHelper.defaultAddress();
  }

  private int getClusterPublicPort(int actualPort) {
    int publicPort = options.getClusterPublicPort();
    return publicPort > 0 ? publicPort : actualPort;
  }

  private String getClusterPublicHost(String host) {
    String publicHost;
    if ((publicHost = options.getClusterPublicHost()) != null) {
      return publicHost;
    }
    if ((publicHost = options.getHost()) != null) {
      return publicHost;
    }
    if ((publicHost = clusterManager.clusterPublicHost()) != null) {
      return publicHost;
    }
    return host;
  }

  private Handler<NetSocket> getServerHandler() {
    return socket -> {
      RecordParser parser = RecordParser.newFixed(4);
      Handler<Buffer> handler = new Handler<Buffer>() {
        int size = -1;

        public void handle(Buffer buff) {
          if (size == -1) {
            size = buff.getInt(0);
            parser.fixedSizeMode(size);
          } else {
            ClusteredMessage received = new ClusteredMessage(ClusteredEventBus.this);
            received.readFromWire(buff, codecManager);
            if (metrics != null) {
              metrics.messageRead(received.address(), buff.length());
            }
            parser.fixedSizeMode(4);
            size = -1;
            if (received.hasFailure()) {
              received.internalError();
            } else if (received.codec() == CodecManager.PING_MESSAGE_CODEC) {
              // Just send back pong directly on connection
              socket.write(PONG);
            } else {
              deliverMessageLocally(received);
            }
          }
        }
      };
      parser.setOutput(handler);
      socket.handler(parser);
    };
  }

  private <T> void sendToNode(String nodeId, MessageImpl<?, T> message, Promise<Void> writePromise) {
    if (nodeId != null && !nodeId.equals(this.nodeId)) {
      sendRemote(nodeId, message, writePromise);
    } else {
      sendLocally(message, writePromise);
    }
  }

  private <T> void sendToNodes(Iterable<String> nodeIds, MessageImpl<?, T> message, Promise<Void> writePromise) {
    boolean sentRemote = false;
    if (nodeIds != null) {
      for (String nid : nodeIds) {
        if (!sentRemote) {
          sentRemote = true;
        }
        // Write promise might be completed several times!!!!
        sendToNode(nid, message, writePromise);
      }
    }
    if (!sentRemote) {
      sendLocally(message, writePromise);
    }
  }

  private <T> void clusteredSendReply(MessageImpl<?, T> message, Promise<Void> writePromise, String replyDest) {
    if (!replyDest.equals(nodeId)) {
      sendRemote(replyDest, message, writePromise);
    } else {
      sendLocally(message, writePromise);
    }
  }

  private void sendRemote(String remoteNodeId, MessageImpl<?, ?> message, Promise<Void> writePromise) {
    // We need to deal with the fact that connecting can take some time and is async, and we cannot
    // block to wait for it. So we add any sends to a pending list if not connected yet.
    // Once we connect we send them.
    // This can also be invoked concurrently from different threads, so it gets a little
    // tricky
    ConnectionHolder holder = connections.get(remoteNodeId);
    if (holder == null) {
      // When process is creating a lot of connections this can take some time
      // so increase the timeout
      holder = new ConnectionHolder(this, remoteNodeId);
      ConnectionHolder prevHolder = connections.putIfAbsent(remoteNodeId, holder);
      if (prevHolder != null) {
        // Another one sneaked in
        holder = prevHolder;
      } else {
        holder.connect();
      }
    }
    holder.writeMessage(message, writePromise);
  }

  ConcurrentMap<String, ConnectionHolder> connections() {
    return connections;
  }

  VertxInternal vertx() {
    return vertx;
  }

  EventBusOptions options() {
    return options;
  }
}

