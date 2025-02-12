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
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.*;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.impl.utils.ConcurrentCyclicSequence;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.NetClientBuilder;
import io.vertx.core.net.impl.NetServerInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
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
public final class ClusteredEventBus extends EventBusImpl {

  private static final Logger log = LoggerFactory.getLogger(ClusteredEventBus.class);

  private final EventBusOptions options;
  private final ClusterManager clusterManager;
  private final NodeSelector nodeSelector;
  private final AtomicLong handlerSequence = new AtomicLong(0);
  private final NetClient client;

  private final ConcurrentMap<String, OutboundConnection> outboundConnections = new ConcurrentHashMap<>();
  private final ContextInternal context;

  private NodeInfo nodeInfo;
  private String nodeId;
  private NetServerInternal server;

  public ClusteredEventBus(VertxInternal vertx, VertxOptions options, ClusterManager clusterManager, NodeSelector nodeSelector) {
    super(vertx);

    NetClient client = createNetClient(vertx, new NetClientOptions(options.getEventBusOptions().toJson())
      .setHostnameVerificationAlgorithm("")
    );

    this.options = options.getEventBusOptions();
    this.clusterManager = clusterManager;
    this.nodeSelector = nodeSelector;
    this.context = vertx.createEventLoopContext(null, new CloseFuture(), null, Thread.currentThread().getContextClassLoader());
    this.client = client;
  }

  CodecManager codecManager() {
    return codecManager;
  }

  EventBusMetrics<?> metrics() {
    return metrics;
  }

  VertxInternal vertx() {
    return vertx;
  }

  EventBusOptions options() {
    return options;
  }

  /**
   * Pick a default address for clustered event bus when none was provided by either the user or the cluster manager.
   * 
   * This is used by Vert.x launcher.
   */
  public static String defaultAddress() {
    Enumeration<NetworkInterface> nets;
    try {
      nets = NetworkInterface.getNetworkInterfaces();
    } catch (SocketException e) {
      return null;
    }
    NetworkInterface netinf;
    while (nets.hasMoreElements()) {
      netinf = nets.nextElement();

      Enumeration<InetAddress> addresses = netinf.getInetAddresses();

      while (addresses.hasMoreElements()) {
        InetAddress address = addresses.nextElement();
        if (!address.isAnyLocalAddress() && !address.isMulticastAddress()
          && !(address instanceof Inet6Address)) {
          return address.getHostAddress();
        }
      }
    }
    return null;
  }

  private NetClient createNetClient(VertxInternal vertx, NetClientOptions clientOptions) {
    NetClientBuilder builder = new NetClientBuilder(vertx, clientOptions);
    VertxMetrics metricsSPI = vertx.metricsSPI();
    if (metricsSPI != null) {
      builder.metrics(metricsSPI.createNetClientMetrics(clientOptions));
    }
    return builder.build();
  }

  private NetServerOptions getServerOptions() {
    return new NetServerOptions(this.options.toJson());
  }

  @Override
  public void start(Promise<Void> promise) {
    NetServerOptions serverOptions = getServerOptions();
    server = vertx.createNetServer(serverOptions);
    server.connectHandler(socket -> {
      InboundConnection inboundConnection = new InboundConnection(this, socket);
      inboundConnection.handler(this::deliverMessageLocally);
      socket.handler(inboundConnection);
    });
    int port = getClusterPort();
    String host = getClusterHost();
    server
      .listen(context, SocketAddress.inetSocketAddress(port, host))
      .flatMap(s -> {
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
  }

  @Override
  public void close(Promise<Void> promise) {
    Promise<Void> parentClose = Promise.promise();
    super.close(parentClose);
    Future<Void> ret = parentClose
      .future()
      .eventually(client::close);
    if (server != null) {
      ret = ret.eventually(() -> server.close());
    }
    ret.onComplete(promise);
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
      if (message.isSend()) {
        nodeSelector.selectForSend(message.address(), (nodeId, failure) -> {
          if (failure == null) {
            sendToNode(nodeId, message, writePromise);
          } else {
            sendOrPublishFailed(writePromise, failure);
          }
        });
      } else {
        nodeSelector.selectForPublish(message.address(), (nodeIds, failure) -> {
          if (failure == null) {
            sendToNodes(nodeIds, message, writePromise);
          } else {
            sendOrPublishFailed(writePromise, failure);
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
    return defaultAddress();
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
    OutboundConnection outboundConnection = getOutboundConnection(remoteNodeId);
    outboundConnection.writeMessage(message, writePromise);
  }

  private OutboundConnection getOutboundConnection(String remoteNodeId) {
    OutboundConnection conn = outboundConnections.get(remoteNodeId);
    if (conn == null) {
      conn = new OutboundConnection(this, remoteNodeId);
      OutboundConnection prev = outboundConnections.putIfAbsent(remoteNodeId, conn);
      if (prev != null) {
        conn = prev;
      } else {
        connect(conn);
      }
    }
    return conn;
  }

  private void connect(OutboundConnection conn) {
    Promise<NodeInfo> promise = Promise.promise();
    clusterManager.getNodeInfo(conn.remoteNodeId(), promise);
    promise.future()
      .flatMap(info -> client.connect(info.port(), info.host()))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          NetSocket connection = ar.result();
          connection.handler(conn);
          connection.closeHandler(v -> {
            if (outboundConnections.remove(conn.remoteNodeId(), conn)) {
              if (log.isDebugEnabled()) {
                log.debug("Cluster connection closed for server " + conn.remoteNodeId());
              }
            }
            conn.handleClose(NetSocketInternal.CLOSED_EXCEPTION);
          });
          conn.connected(connection);
        } else {
          log.warn("Connecting to server " + conn.remoteNodeId() + " failed", ar.cause());
          conn.handleClose(ar.cause());
        }
      });
  }
}

