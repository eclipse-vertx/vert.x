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

package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.AddressHelper;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.RegistrationInfo;

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

  private NodeInfo nodeInfo;
  private String nodeId;
  private NetServer server;

  public ClusteredEventBus(VertxInternal vertx, VertxOptions options, ClusterManager clusterManager, NodeSelector nodeSelector) {
    super(vertx);
    this.options = options.getEventBusOptions();
    this.clusterManager = clusterManager;
    this.nodeSelector = nodeSelector;
    this.client = vertx.createNetClient(new NetClientOptions(this.options.toJson()));
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
    server.listen(port, host).flatMap(v -> {
      int publicPort = getClusterPublicPort(server.actualPort());
      String publicHost = getClusterPublicHost(host);
      nodeInfo = new NodeInfo(publicHost, publicPort, options.getClusterNodeMetadata());
      nodeId = clusterManager.getNodeId();
      Promise<Void> setPromise = Promise.promise();
      clusterManager.setNodeInfo(nodeInfo, setPromise);
      return setPromise.future();
    }).onSuccess(v -> {
      started = true;
      nodeSelector.eventBusStarted();
    }).onComplete(promise);
  }

  @Override
  public void close(Promise<Void> promise) {
    Promise<Void> parentClose = Promise.promise();
    super.close(parentClose);
    parentClose.future().onComplete(ar -> {
      if (server != null) {
        server.close(serverClose -> {
          if (serverClose.failed()) {
            log.error("Failed to close server", serverClose.cause());
          }
          // Close all outbound connections explicitly - don't rely on context hooks
          for (ConnectionHolder holder : connections.values()) {
            holder.close();
          }
          promise.handle(serverClose);
        });
      } else {
        promise.handle(ar);
      }
    });
  }

  @Override
  public MessageImpl createMessage(boolean send, String address, MultiMap headers, Object body, String codecName) {
    Objects.requireNonNull(address, "no null address accepted");
    MessageCodec codec = codecManager.lookupCodec(body, codecName);
    @SuppressWarnings("unchecked")
    ClusteredMessage msg = new ClusteredMessage(nodeId, address, headers, body, codec, send, this);
    return msg;
  }

  @Override
  protected <T> void onLocalRegistration(HandlerHolder<T> handlerHolder, Promise<Void> promise) {
    if (!handlerHolder.isReplyHandler()) {
      RegistrationInfo registrationInfo = new RegistrationInfo(
        nodeId,
        handlerHolder.getSeq(),
        handlerHolder.isLocalOnly()
      );
      clusterManager.addRegistration(handlerHolder.getHandler().address, registrationInfo, Objects.requireNonNull(promise));
    } else if (promise != null) {
      promise.complete();
    }
  }

  @Override
  protected <T> HandlerHolder<T> createHandlerHolder(HandlerRegistration<T> registration, boolean replyHandler, boolean localOnly, ContextInternal context) {
    return new ClusteredHandlerHolder<>(registration, replyHandler, localOnly, context, handlerSequence.getAndIncrement());
  }

  @Override
  protected <T> void onLocalUnregistration(HandlerHolder<T> handlerHolder, Promise<Void> completionHandler) {
    if (!handlerHolder.isReplyHandler()) {
      RegistrationInfo registrationInfo = new RegistrationInfo(
        nodeId,
        handlerHolder.getSeq(),
        handlerHolder.isLocalOnly()
      );
      Promise<Void> promise = Promise.promise();
      clusterManager.removeRegistration(handlerHolder.getHandler().address, registrationInfo, promise);
      promise.future().onComplete(completionHandler);
    } else {
      completionHandler.complete();
    }
  }

  @Override
  protected <T> void sendOrPub(OutboundDeliveryContext<T> sendContext) {
    if (((ClusteredMessage) sendContext.message).getRepliedTo() != null) {
      clusteredSendReply(((ClusteredMessage) sendContext.message).getRepliedTo(), sendContext);
    } else if (sendContext.options.isLocalOnly()) {
      super.sendOrPub(sendContext);
    } else {
      Serializer serializer = Serializer.get(sendContext.ctx);
      if (sendContext.message.isSend()) {
        Promise<String> promise = sendContext.ctx.promise();
        serializer.queue(sendContext.message, nodeSelector::selectForSend, promise);
        promise.future().onComplete(ar -> {
          if (ar.succeeded()) {
            sendToNode(sendContext, ar.result());
          } else {
            sendOrPublishFailed(sendContext, ar.cause());
          }
        });
      } else {
        Promise<Iterable<String>> promise = sendContext.ctx.promise();
        serializer.queue(sendContext.message, nodeSelector::selectForPublish, promise);
        promise.future().onComplete(ar -> {
          if (ar.succeeded()) {
            sendToNodes(sendContext, ar.result());
          } else {
            sendOrPublishFailed(sendContext, ar.cause());
          }
        });
      }
    }
  }

  private void sendOrPublishFailed(OutboundDeliveryContext<?> sendContext, Throwable cause) {
    if (log.isDebugEnabled()) {
      log.error("Failed to send message", cause);
    }
    sendContext.written(cause);
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

  private <T> void sendToNode(OutboundDeliveryContext<T> sendContext, String nodeId) {
    if (nodeId != null && !nodeId.equals(this.nodeId)) {
      sendRemote(sendContext, nodeId, sendContext.message);
    } else {
      super.sendOrPub(sendContext);
    }
  }

  private <T> void sendToNodes(OutboundDeliveryContext<T> sendContext, Iterable<String> nodeIds) {
    boolean sentRemote = false;
    if (nodeIds != null) {
      for (String nid : nodeIds) {
        if (!sentRemote) {
          sentRemote = true;
        }
        sendToNode(sendContext, nid);
      }
    }
    if (!sentRemote) {
      super.sendOrPub(sendContext);
    }
  }

  private <T> void clusteredSendReply(String replyDest, OutboundDeliveryContext<T> sendContext) {
    MessageImpl message = sendContext.message;
    if (!replyDest.equals(nodeId)) {
      sendRemote(sendContext, replyDest, message);
    } else {
      super.sendOrPub(sendContext);
    }
  }

  private void sendRemote(OutboundDeliveryContext<?> sendContext, String remoteNodeId, MessageImpl message) {
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
    holder.writeMessage(sendContext);
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

