/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
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
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.CodecManager;
import io.vertx.core.eventbus.impl.EventBusImpl;
import io.vertx.core.eventbus.impl.HandlerHolder;
import io.vertx.core.eventbus.impl.MessageImpl;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.HAManager;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import io.vertx.core.spi.cluster.ClusterManager;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

/**
 * An event bus implementation that clusters with other Vert.x nodes
 *
 * @author <a href="http://tfox.org">Tim Fox</a>   7                                                                                     T
 */
public class ClusteredEventBus extends EventBusImpl {

  private static final Logger log = LoggerFactory.getLogger(ClusteredEventBus.class);

  public static final String CLUSTER_PUBLIC_HOST_PROP_NAME = "vertx.cluster.public.host";
  public static final String CLUSTER_PUBLIC_PORT_PROP_NAME = "vertx.cluster.public.port";

  private static final Buffer PONG = Buffer.buffer(new byte[]{(byte) 1});
  private static final String SERVER_ID_HA_KEY = "server_id";
  private static final String SUBS_MAP_NAME = "__vertx.subs";

  private final ClusterManager clusterManager;
  private final ConcurrentMap<ServerID, ConnectionHolder> connections = new ConcurrentHashMap<>();
  private final Context sendNoContext;

  private EventBusOptions options;
  private AsyncMultiMap<String, ClusterNodeInfo> subs;
  private Set<String> ownSubs = new ConcurrentHashSet<>();
  private ServerID serverID;
  private ClusterNodeInfo nodeInfo;
  private NetServer server;

  public ClusteredEventBus(VertxInternal vertx,
                           VertxOptions options,
                           ClusterManager clusterManager) {
    super(vertx);
    this.options = options.getEventBusOptions();
    this.clusterManager = clusterManager;
    this.sendNoContext = vertx.getOrCreateContext();
  }

  private NetServerOptions getServerOptions() {
    NetServerOptions serverOptions = new NetServerOptions(this.options.toJson());
    setCertOptions(serverOptions, options.getKeyCertOptions());
    setTrustOptions(serverOptions, options.getTrustOptions());

    return serverOptions;
  }

  static void setCertOptions(TCPSSLOptions options, KeyCertOptions keyCertOptions) {
    if (keyCertOptions == null) {
      return;
    }
    if (keyCertOptions instanceof JksOptions) {
      options.setKeyStoreOptions((JksOptions) keyCertOptions);
    } else if (keyCertOptions instanceof PfxOptions) {
      options.setPfxKeyCertOptions((PfxOptions) keyCertOptions);
    } else {
      options.setPemKeyCertOptions((PemKeyCertOptions) keyCertOptions);
    }
  }

  static void setTrustOptions(TCPSSLOptions sslOptions, TrustOptions options) {
    if (options == null) {
      return;
    }

    if (options instanceof JksOptions) {
      sslOptions.setTrustStoreOptions((JksOptions) options);
    } else if (options instanceof PfxOptions) {
      sslOptions.setPfxTrustOptions((PfxOptions) options);
    } else {
      sslOptions.setPemTrustOptions((PemTrustOptions) options);
    }
  }

  @Override
  public void start(Handler<AsyncResult<Void>> resultHandler) {
    // Get the HA manager, it has been constructed but it's not yet initialized
    HAManager haManager = vertx.haManager();
    setClusterViewChangedHandler(haManager);
    clusterManager.<String, ClusterNodeInfo>getAsyncMultiMap(SUBS_MAP_NAME, ar1 -> {
      if (ar1.succeeded()) {
        subs = ar1.result();
        server = vertx.createNetServer(getServerOptions());

        server.connectHandler(getServerHandler());
        server.listen(asyncResult -> {
          if (asyncResult.succeeded()) {
            int serverPort = getClusterPublicPort(options, server.actualPort());
            String serverHost = getClusterPublicHost(options);
            serverID = new ServerID(serverPort, serverHost);
            nodeInfo = new ClusterNodeInfo(clusterManager.getNodeID(), serverID);
            vertx.executeBlocking(fut -> {
              haManager.addDataToAHAInfo(SERVER_ID_HA_KEY, new JsonObject().put("host", serverID.host).put("port", serverID.port));
              fut.complete();
            }, false, ar2 -> {
              if (ar2.succeeded()) {
                started = true;
                resultHandler.handle(Future.succeededFuture());
              } else {
                resultHandler.handle(Future.failedFuture(ar2.cause()));
              }
            });
          } else {
            resultHandler.handle(Future.failedFuture(asyncResult.cause()));
          }
        });
      } else {
        resultHandler.handle(Future.failedFuture(ar1.cause()));
      }
    });
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    super.close(ar1 -> {
      if (server != null) {
        server.close(ar -> {
          if (ar.failed()) {
            log.error("Failed to close server", ar.cause());
          }
          // Close all outbound connections explicitly - don't rely on context hooks
          for (ConnectionHolder holder : connections.values()) {
            holder.close();
          }
          if (completionHandler != null) {
            completionHandler.handle(ar);
          }
        });
      } else {
        if (completionHandler != null) {
          completionHandler.handle(ar1);
        }
      }
    });

  }

  @Override
  protected MessageImpl createMessage(boolean send, String address, MultiMap headers, Object body, String codecName) {
    Objects.requireNonNull(address, "no null address accepted");
    MessageCodec codec = codecManager.lookupCodec(body, codecName);
    @SuppressWarnings("unchecked")
    ClusteredMessage msg = new ClusteredMessage(serverID, address, null, headers, body, codec, send, this);
    return msg;
  }

  @Override
  protected <T> void addRegistration(boolean newAddress, String address,
                                     boolean replyHandler, boolean localOnly,
                                     Handler<AsyncResult<Void>> completionHandler) {
    if (newAddress && subs != null && !replyHandler && !localOnly) {
      // Propagate the information
      subs.add(address, nodeInfo, completionHandler);
      ownSubs.add(address);
    } else {
      completionHandler.handle(Future.succeededFuture());
    }
  }

  @Override
  protected <T> void removeRegistration(HandlerHolder<T> lastHolder, String address,
                                        Handler<AsyncResult<Void>> completionHandler) {
    if (lastHolder != null && subs != null && !lastHolder.isLocalOnly()) {
      ownSubs.remove(address);
      removeSub(address, nodeInfo, completionHandler);
    } else {
      callCompletionHandlerAsync(completionHandler);
    }
  }

  @Override
  protected <T> void sendReply(OutboundDeliveryContext<T> sendContext, MessageImpl replierMessage) {
    clusteredSendReply(((ClusteredMessage) replierMessage).getSender(), sendContext);
  }

  @Override
  protected <T> void sendOrPub(OutboundDeliveryContext<T> sendContext) {
    if (sendContext.options.isLocalOnly()) {
      if (metrics != null) {
        metrics.messageSent(sendContext.message.address(), !sendContext.message.isSend(), true, false);
      }
      deliverMessageLocally(sendContext);
    } else if (Vertx.currentContext() == null) {
      // Guarantees the order when there is no current context
      sendNoContext.runOnContext(v -> {
        subs.get(sendContext.message.address(), ar -> onSubsReceived(ar, sendContext));
      });
    } else {
      subs.get(sendContext.message.address(), ar -> onSubsReceived(ar, sendContext));
    }
  }

  private <T> void onSubsReceived(AsyncResult<ChoosableIterable<ClusterNodeInfo>> asyncResult, OutboundDeliveryContext<T> sendContext) {
    if (asyncResult.succeeded()) {
      ChoosableIterable<ClusterNodeInfo> serverIDs = asyncResult.result();
      if (serverIDs != null && !serverIDs.isEmpty()) {
        sendToSubs(serverIDs, sendContext);
      } else {
        if (metrics != null) {
          metrics.messageSent(sendContext.message.address(), !sendContext.message.isSend(), true, false);
        }
        deliverMessageLocally(sendContext);
      }
    } else {
      log.error("Failed to send message", asyncResult.cause());
    }
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

  private void setClusterViewChangedHandler(HAManager haManager) {
    haManager.setClusterViewChangedHandler(members -> {
      ownSubs.forEach(address -> {
        subs.add(address, nodeInfo, addResult -> {
          if (addResult.failed()) {
            log.warn("Failed to update subs map with self", addResult.cause());
          }
        });
      });

      subs.removeAllMatching((Serializable & Predicate<ClusterNodeInfo>) ci -> !members.contains(ci.nodeId), removeResult -> {
        if (removeResult.failed()) {
          log.warn("Error removing subs", removeResult.cause());
        }
      });
    });
  }

  private int getClusterPublicPort(EventBusOptions options, int actualPort) {
    // We retain the old system property for backwards compat
    int publicPort = Integer.getInteger(CLUSTER_PUBLIC_PORT_PROP_NAME, options.getClusterPublicPort());
    if (publicPort == -1) {
      // Get the actual port, wildcard port of zero might have been specified
      publicPort = actualPort;
    }
    return publicPort;
  }

  private String getClusterPublicHost(EventBusOptions options) {
    // We retain the old system property for backwards compat
    String publicHost = System.getProperty(CLUSTER_PUBLIC_HOST_PROP_NAME, options.getClusterPublicHost());
    if (publicHost == null) {
      publicHost = options.getHost();
    }
    return publicHost;
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
            ClusteredMessage received = new ClusteredMessage();
            received.readFromWire(buff, codecManager);
            if (metrics != null) {
              metrics.messageRead(received.address(), buff.length());
            }
            parser.fixedSizeMode(4);
            size = -1;
            if (received.codec() == CodecManager.PING_MESSAGE_CODEC) {
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

  private <T> void sendToSubs(ChoosableIterable<ClusterNodeInfo> subs, OutboundDeliveryContext<T> sendContext) {
    String address = sendContext.message.address();
    if (sendContext.message.isSend()) {
      // Choose one
      ClusterNodeInfo ci = subs.choose();
      ServerID sid = ci == null ? null : ci.serverID;
      if (sid != null && !sid.equals(serverID)) {  //We don't send to this node
        if (metrics != null) {
          metrics.messageSent(address, false, false, true);
        }
        sendRemote(sid, sendContext.message);
      } else {
        if (metrics != null) {
          metrics.messageSent(address, false, true, false);
        }
        deliverMessageLocally(sendContext);
      }
    } else {
      // Publish
      boolean local = false;
      boolean remote = false;
      for (ClusterNodeInfo ci : subs) {
        if (!ci.serverID.equals(serverID)) {  //We don't send to this node
          remote = true;
          sendRemote(ci.serverID, sendContext.message);
        } else {
          local = true;
        }
      }
      if (metrics != null) {
        metrics.messageSent(address, true, local, remote);
      }
      if (local) {
        deliverMessageLocally(sendContext);
      }
    }
  }

  private <T> void clusteredSendReply(ServerID replyDest, OutboundDeliveryContext<T> sendContext) {
    MessageImpl message = sendContext.message;
    String address = message.address();
    if (!replyDest.equals(serverID)) {
      if (metrics != null) {
        metrics.messageSent(address, false, false, true);
      }
      sendRemote(replyDest, message);
    } else {
      if (metrics != null) {
        metrics.messageSent(address, false, true, false);
      }
      deliverMessageLocally(sendContext);
    }
  }

  private void sendRemote(ServerID theServerID, MessageImpl message) {
    // We need to deal with the fact that connecting can take some time and is async, and we cannot
    // block to wait for it. So we add any sends to a pending list if not connected yet.
    // Once we connect we send them.
    // This can also be invoked concurrently from different threads, so it gets a little
    // tricky
    ConnectionHolder holder = connections.get(theServerID);
    if (holder == null) {
      // When process is creating a lot of connections this can take some time
      // so increase the timeout
      holder = new ConnectionHolder(this, theServerID, options);
      ConnectionHolder prevHolder = connections.putIfAbsent(theServerID, holder);
      if (prevHolder != null) {
        // Another one sneaked in
        holder = prevHolder;
      } else {
        holder.connect();
      }
    }
    holder.writeMessage((ClusteredMessage) message);
  }

  private void removeSub(String subName, ClusterNodeInfo node, Handler<AsyncResult<Void>> completionHandler) {
    subs.remove(subName, node, ar -> {
      if (!ar.succeeded()) {
        log.error("Failed to remove sub", ar.cause());
      } else {
        if (ar.result()) {
          if (completionHandler != null) {
            completionHandler.handle(Future.succeededFuture());
          }
        } else {
          if (completionHandler != null) {
            completionHandler.handle(Future.failedFuture("sub not found"));
          }
        }

      }
    });
  }

  ConcurrentMap<ServerID, ConnectionHolder> connections() {
    return connections;
  }

  VertxInternal vertx() {
    return vertx;
  }

  EventBusOptions options() {
    return options;
  }
}

