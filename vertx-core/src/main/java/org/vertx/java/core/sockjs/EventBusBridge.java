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

package org.vertx.java.core.sockjs;

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * Bridges the event bus to the client side.<p>
 * Instances of this class are not thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusBridge implements Handler<SockJSSocket> {

  private static final Logger log = LoggerFactory.getLogger(EventBusBridge.class);

  private static final String DEFAULT_AUTH_ADDRESS = "vertx.basicauthmanager.authorise";
  private static final long DEFAULT_AUTH_TIMEOUT = 5 * 60 * 1000;
  private static final long DEFAULT_REPLY_TIMEOUT = 30 * 1000;
  private static final int DEFAULT_MAX_ADDRESS_LENGTH = 200;
  private static final int DEFAULT_MAX_HANDLERS_PER_SOCKET = 1000;
  private static final long DEFAULT_PING_TIMEOUT = 10 * 1000;

  private final Map<String, Auth> authCache = new HashMap<>();
  private final Map<SockJSSocket, SockInfo> sockInfos = new HashMap<>();
  private final List<JsonObject> inboundPermitted;
  private final List<JsonObject> outboundPermitted;
  private final long authTimeout;
  private final String authAddress;
  private final int maxAddressLength;
  private final int maxHandlersPerSocket;
  private final long pingTimeout;
  private final long replyTimeout;
  private final Vertx vertx;
  private final EventBus eb;
  private final Map<String, Message> messagesAwaitingReply = new HashMap<>();
  private final Map<String, Pattern> compiledREs = new HashMap<>();
  private EventBusBridgeHook hook;

  private static final class SockInfo {
    Set<String> sockAuths;
    int handlerCount;
    PingInfo pingInfo;
  }

  private static List<JsonObject> convertArray(JsonArray permitted) {
    List<JsonObject> l = new ArrayList<>();
    for (Object elem: permitted) {
      if (!(elem instanceof JsonObject)) {
        throw new IllegalArgumentException("Permitted must only contain JsonObject: " + elem);
      }
      l.add((JsonObject) elem);
    }
    return l;
  }

  public EventBusBridge(Vertx vertx, JsonArray inboundPermitted, JsonArray outboundPermitted) {
    this(vertx, inboundPermitted, outboundPermitted, DEFAULT_AUTH_TIMEOUT, null);
  }

  public EventBusBridge(Vertx vertx, JsonArray inboundPermitted, JsonArray outboundPermitted,
                        long authTimeout) {
    this(vertx, inboundPermitted, outboundPermitted, authTimeout, null);
  }

  /*
  Old constructor -we keep this for backward compatibility
   */
  public EventBusBridge(Vertx vertx, JsonArray inboundPermitted, JsonArray outboundPermitted,
                        long authTimeout,
                        String authAddress) {
    this(vertx, inboundPermitted, outboundPermitted,
        new JsonObject().putNumber("auth_timeout", authTimeout).putString("auth_address", authAddress));
  }

  public EventBusBridge(Vertx vertx, JsonArray inboundPermitted, JsonArray outboundPermitted,
                        JsonObject conf) {
    this.vertx = vertx;
    this.eb = vertx.eventBus();
    this.inboundPermitted = convertArray(inboundPermitted);
    this.outboundPermitted = convertArray(outboundPermitted);
    long authTimeout = conf.getLong("auth_timeout", DEFAULT_AUTH_TIMEOUT);
    if (authTimeout < 0) {
      throw new IllegalArgumentException("authTimeout < 0");
    }
    this.authTimeout = authTimeout;
    this.authAddress = conf.getString("auth_address", DEFAULT_AUTH_ADDRESS);
    this.maxAddressLength = conf.getInteger("max_address_length", DEFAULT_MAX_ADDRESS_LENGTH);
    this.maxHandlersPerSocket = conf.getInteger("max_handlers_per_socket", DEFAULT_MAX_HANDLERS_PER_SOCKET);
    this.pingTimeout = conf.getLong("ping_interval", DEFAULT_PING_TIMEOUT);
    this.replyTimeout = conf.getLong("reply_timeout", DEFAULT_REPLY_TIMEOUT);
  }

  private void handleSocketClosed(SockJSSocket sock, Map<String, Handler<Message>> handlers) {
    // On close unregister any handlers that haven't been unregistered
    for (Map.Entry<String, Handler<Message>> entry: handlers.entrySet()) {
      //call hook
      handleUnregister(sock, entry.getKey());
      eb.unregisterHandler(entry.getKey(), entry.getValue());
    }

    //Close any cached authorisations for this connection
    SockInfo info = sockInfos.remove(sock);
    if (info != null) {
      Set<String> auths = info.sockAuths;
      if (auths != null) {
        for (String sessionID: auths) {
          Auth auth = authCache.remove(sessionID);
          if (auth != null) {
            auth.cancel();
          }
        }
      }
      PingInfo pingInfo = info.pingInfo;
      if (pingInfo != null) {
        vertx.cancelTimer(pingInfo.timerID);
      }
    }

    handleSocketClosed(sock);
  }

  private void handleSocketData(SockJSSocket sock, Buffer data, Map<String, Handler<Message>> handlers) {
    JsonObject msg = new JsonObject(data.toString());

    String type = getMandatoryString(msg, "type");
    switch (type) {
      case "send":
        String address = getMandatoryString(msg, "address");
        internalHandleSendOrPub(sock, true, msg, address);
        break;
      case "publish":
        address = getMandatoryString(msg, "address");
        internalHandleSendOrPub(sock, false, msg, address);
        break;
      case "register":
        address = getMandatoryString(msg, "address");
        internalHandleRegister(sock, msg, address, handlers);
        break;
      case "unregister":
        address = getMandatoryString(msg, "address");
        internalHandleUnregister(sock, address, handlers);
        break;
      case "ping":
        internalHandlePing(sock);
        break;
      default:
        throw new IllegalStateException("Invalid type: " + type);
    }
  }

  private void internalHandleSendOrPub(SockJSSocket sock, boolean send, JsonObject msg, String address) {
    if (handleSendOrPub(sock, send, msg, address)) {
      doSendOrPub(send, sock, address, msg);
    }
  }

  private boolean checkMaxHandlers(SockInfo info) {
    if (info.handlerCount == maxHandlersPerSocket) {
      log.error("Refusing to register as max_handlers_per_socket reached already");
      return false;
    } else {
      return true;
    }
  }

  private void internalHandleRegister(final SockJSSocket sock, JsonObject message, final String address, Map<String, Handler<Message>> handlers) {
    if (address.length() > maxAddressLength) {
      log.error("Refusing to register as address length > max_address_length");
      return;
    }
    final SockInfo info = sockInfos.get(sock);
    if (!checkMaxHandlers(info)) {
      return;
    }
    if (handlePreRegister(sock, address)) {
      final boolean debug = log.isDebugEnabled();
      Match match = checkMatches(false, address, message);
      if (match.doesMatch) {
        Handler<Message> handler = new Handler<Message>() {
          public void handle(final Message msg) {
            Match curMatch = checkMatches(false, address, msg.body());
            if (curMatch.doesMatch) {
              Set<String> sockAuths = info.sockAuths;
              if (curMatch.requiresAuth && sockAuths == null) {
                if (debug) {
                  log.debug("Outbound message for address " + address + " rejected because auth is required and socket is not authed");
                }
              } else {
                checkAddAccceptedReplyAddress(msg);
                deliverMessage(sock, address, msg);
              }
            } else {
              // outbound match failed
              if (debug) {
                log.debug("Outbound message for address " + address + " rejected because there is no inbound match");
              }
            }
          }
        };
        handlers.put(address, handler);
        eb.registerHandler(address, handler);
        handlePostRegister(sock, address);
        info.handlerCount++;
      } else {
        // inbound match failed
        if (debug) {
          log.debug("Cannot register handler for address " + address + " because there is no inbound match");
        }
      }
    }
  }

  private void internalHandleUnregister(SockJSSocket sock, String address, Map<String,
      Handler<Message>> handlers) {
    if (handleUnregister(sock, address)) {
      Handler<Message> handler = handlers.remove(address);
      if (handler != null) {
        eb.unregisterHandler(address, handler);
        SockInfo info = sockInfos.get(sock);
        info.handlerCount--;
      }
    }
  }

  private void internalHandlePing(final SockJSSocket sock) {
    SockInfo info = sockInfos.get(sock);
    if (info != null) {
      info.pingInfo.lastPing = System.currentTimeMillis();
    }
  }

  public void handle(final SockJSSocket sock) {
    if (!handleSocketCreated(sock)) {
      sock.close();
    } else {
      final Map<String, Handler<Message>> handlers = new HashMap<>();

      sock.endHandler(new VoidHandler() {
        public void handle() {
          handleSocketClosed(sock, handlers);
        }
      });

      sock.dataHandler(new Handler<Buffer>() {
        public void handle(Buffer data)  {
          handleSocketData(sock, data, handlers);
        }
      });

      // Start a checker to check for pings
      final PingInfo pingInfo = new PingInfo();
      pingInfo.timerID = vertx.setPeriodic(pingTimeout, new Handler<Long>() {
        @Override
        public void handle(Long id) {
          if (System.currentTimeMillis() - pingInfo.lastPing >= pingTimeout) {
            // We didn't receive a ping in time so close the socket
            sock.close();
          }
        }
      });
      SockInfo sockInfo = new SockInfo();
      sockInfo.pingInfo = pingInfo;
      sockInfos.put(sock, sockInfo);
    }
  }

  private void checkAddAccceptedReplyAddress(final Message message) {
    final String replyAddress = message.replyAddress();
    if (replyAddress != null) {
      // This message has a reply address
      // When the reply comes through we want to accept it irrespective of its address
      // Since all replies are implicitly accepted if the original message was accepted
      // So we cache the reply address, so we can check against it
      // We also need to cache the message so we can actually call reply() on it - we need the actual message
      // as the original sender could be on a different node so we need the replyDest (serverID) too otherwise
      // the message won't be routed to the node.
      messagesAwaitingReply.put(replyAddress, message);
      // And we remove after timeout in case the reply never comes
      vertx.setTimer(replyTimeout, new Handler<Long>() {
        public void handle(Long id) {
          messagesAwaitingReply.remove(replyAddress);
        }
      });
    }
  }

  private static String getMandatoryString(JsonObject json, String field) {
    String value = json.getString(field);
    if (value == null) {
      throw new IllegalStateException(field + " must be specified for message");
    }
    return value;
  }

  private static JsonObject getMandatoryObject(JsonObject json, String field) {
    JsonObject value = json.getObject(field);
    if (value == null) {
      throw new IllegalStateException(field + " must be specified for message");
    }
    return value;
  }

  private static Object getMandatoryValue(JsonObject json, String field) {
    Object value = json.getValue(field);
    if (value == null) {
      throw new IllegalStateException(field + " must be specified for message");
    }
    return value;
  }

  private static void replyStatus(SockJSSocket sock, String replyAddress, String status) {
    JsonObject body = new JsonObject().putString("status", status);
    JsonObject envelope = new JsonObject().putString("address", replyAddress).putValue("body", body);
    sock.write(new Buffer(envelope.encode()));
  }

  private static void deliverMessage(SockJSSocket sock, String address, Message message) {
    JsonObject envelope = new JsonObject().putString("address", address).putValue("body", message.body());
    if (message.replyAddress() != null) {
      envelope.putString("replyAddress", message.replyAddress());
    }
    sock.write(new Buffer(envelope.encode()));
  }

  private void doSendOrPub(final boolean send, final SockJSSocket sock, final String address,
                           final JsonObject message) {
    final Object body = message.getValue("body");
    final String replyAddress = message.getString("replyAddress");
    // Sanity check reply address is not too big, to avoid DoS
    if (replyAddress != null && replyAddress.length() > 36) {
      // vertxbus.js ids are always 36 chars
      log.error("Will not send message, reply address is > 36 chars");
      return;
    }
    final boolean debug = log.isDebugEnabled();
    if (debug) {
      log.debug("Received msg from client in bridge. address:"  + address + " message:" + body);
    }
    final Message awaitingReply = messagesAwaitingReply.remove(address);
    Match curMatch;
    if (awaitingReply != null) {
      curMatch = new Match(true, false);
    } else {
      curMatch = checkMatches(true, address, body);
    }
    if (curMatch.doesMatch) {
      if (curMatch.requiresAuth) {
        final String sessionID = message.getString("sessionID");
        if (sessionID != null) {
          authorise(message, sessionID, new AsyncResultHandler<Boolean>() {
            public void handle(AsyncResult<Boolean> res) {
              if (res.succeeded()) {
                if (res.result()) {
                  cacheAuthorisation(sessionID, sock);
                  checkAndSend(send, address, body, sock, replyAddress, null);
                } else {
                  // invalid session id
                  replyStatus(sock, replyAddress, "access_denied");
                  if (debug) {
                    log.debug("Inbound message for address " + address + " rejected because sessionID is not authorised");
                  }
                }
              } else {
                replyStatus(sock, replyAddress, "auth_error");
                log.error("Error in performing authorisation", res.cause());
              }
            }
          });
        } else {
          // session id null - authentication is required
          replyStatus(sock, replyAddress, "auth_required");
          if (debug) {
            log.debug("Inbound message for address " + address + " rejected because it requires auth and sessionID is missing");
          }
        }
      } else {
        checkAndSend(send, address, body, sock, replyAddress, awaitingReply);
      }
    } else {
      // inbound match failed
      replyStatus(sock, replyAddress, "access_denied");
      if (debug) {
        log.debug("Inbound message for address " + address + " rejected because there is no match");
      }
    }
  }

  private void checkAndSend(boolean send, final String address, Object body,
                            final SockJSSocket sock,
                            final String replyAddress,
                            final Message awaitingReply) {
    final SockInfo info = sockInfos.get(sock);
    if (replyAddress != null && !checkMaxHandlers(info)) {
      return;
    }
    final Handler<AsyncResult<Message<Object>>> replyHandler;
    if (replyAddress != null) {
      replyHandler = new Handler<AsyncResult<Message<Object>>>() {
        public void handle(AsyncResult<Message<Object>> result) {
          if (result.succeeded()) {
            Message message = result.result();
            // Note we don't check outbound matches for replies
            // Replies are always let through if the original message
            // was approved


            // Now - the reply message might itself be waiting for a reply - which would be inbound -so we need
            // to add the message to the messages awaiting reply so it can be let through
            checkAddAccceptedReplyAddress(message);
            deliverMessage(sock, replyAddress, message);
          } else {
            ReplyException cause = (ReplyException) result.cause();
            JsonObject envelope =
                new JsonObject().putString("address", replyAddress).putNumber("failureCode",
                    cause.failureCode()).putString("failureType", cause.failureType().name())
                    .putString("message", cause.getMessage());
            sock.write(new Buffer(envelope.encode()));
          }
          info.handlerCount--;
        }
      };
    } else {
      replyHandler = null;
    }
    if (log.isDebugEnabled()) {
      log.debug("Forwarding message to address " + address + " on event bus");
    }
    if (send) {
      if (replyAddress != null) {
        if (awaitingReply != null) {
          // This is a reply
          awaitingReply.reply(body, replyHandler);
        } else {
          eb.sendWithTimeout(address, body, replyTimeout, replyHandler);
        }
        info.handlerCount++;
      } else {
        if (awaitingReply != null) {
          // This is a reply
          awaitingReply.reply(body);
        } else {
          eb.send(address, body);
        }
      }
    } else {
      eb.publish(address, body);
    }
  }

  private void authorise(final JsonObject message, final String sessionID,
                           final Handler<AsyncResult<Boolean>> handler) {
    if (!handleAuthorise(message, sessionID, handler)) {
      // If session id is in local cache we'll consider them authorised
      final DefaultFutureResult<Boolean> res = new DefaultFutureResult<>();
      if (authCache.containsKey(sessionID)) {
        res.setResult(true).setHandler(handler);
      } else {
        eb.send(authAddress, message, new Handler<Message<JsonObject>>() {
          public void handle(Message<JsonObject> reply) {
            boolean authed = reply.body().getString("status").equals("ok");
            res.setResult(authed).setHandler(handler);
          }
        });
      }
    }
  }

  /*
  Empty inboundPermitted means reject everything - this is the default.
  If at least one match is supplied and all the fields of any match match then the message inboundPermitted,
  this means that specifying one match with a JSON empty object means everything is accepted
   */
  private Match checkMatches(boolean inbound, String address, Object body) {

    List<JsonObject> matches = inbound ? inboundPermitted : outboundPermitted;

    for (JsonObject matchHolder: matches) {
      String matchAddress = matchHolder.getString("address");
      String matchRegex;
      if (matchAddress == null) {
        matchRegex = matchHolder.getString("address_re");
      } else {
        matchRegex = null;
      }

      boolean addressOK;
      if (matchAddress == null) {
        if (matchRegex == null) {
          addressOK = true;
        } else {
          addressOK = regexMatches(matchRegex, address);
        }
      } else {
        addressOK = matchAddress.equals(address);
      }

      if (addressOK) {
        boolean matched = structureMatches(matchHolder.getObject("match"), body);
        if (matched) {
          Boolean b = matchHolder.getBoolean("requires_auth");
          return new Match(true, b != null && b);
        }
      }
    }
    return new Match(false, false);
  }

  private boolean regexMatches(String matchRegex, String address) {
    Pattern pattern = compiledREs.get(matchRegex);
    if (pattern == null) {
      pattern = Pattern.compile(matchRegex);
      compiledREs.put(matchRegex, pattern);
    }
    Matcher m = pattern.matcher(address);
    return m.matches();
  }

  private static boolean structureMatches(JsonObject match, Object bodyObject) {
    if (match == null) return true;
    if (bodyObject == null) return false;

    // Can send message other than JSON too - in which case we can't do deep matching on structure of message
    if (bodyObject instanceof JsonObject) {
      JsonObject body = (JsonObject) bodyObject;
      for (String fieldName : match.getFieldNames()) {
        Object mv = match.getField(fieldName);
        Object bv = body.getField(fieldName);
        // Support deep matching
        if (mv instanceof JsonObject) {
          if (!structureMatches((JsonObject) mv, bv)) {
            return false;
          }
        } else if (!match.getField(fieldName).equals(body.getField(fieldName))) {
          return false;
        }
      }
      return true;
    }

    return false;
  }

  private void cacheAuthorisation(String sessionID, SockJSSocket sock) {
    if (!authCache.containsKey(sessionID)) {
      authCache.put(sessionID, new Auth(sessionID, sock));
    }
    SockInfo sockInfo = sockInfos.get(sock);
    Set<String> sess = sockInfo.sockAuths;
    if (sess == null) {
      sess = new HashSet<>();
      sockInfo.sockAuths = sess;
    }
    sess.add(sessionID);
  }

  private void uncacheAuthorisation(String sessionID, SockJSSocket sock) {
    authCache.remove(sessionID);
    SockInfo sockInfo = sockInfos.get(sock);
    Set<String> sess = sockInfo.sockAuths;
    if (sess != null) {
      sess.remove(sessionID);
      if (sess.isEmpty()) {
        sockInfo.sockAuths = null;
      }
    }
  }
  
  private static class Match {
    public final boolean doesMatch;
    public final boolean requiresAuth;

    Match(final boolean doesMatch, final boolean requiresAuth) {
      this.doesMatch = doesMatch;
      this.requiresAuth = requiresAuth;
    }

  }

  private class Auth {
    private final long timerID;

    Auth(final String sessionID, final SockJSSocket sock) {
      timerID = vertx.setTimer(authTimeout, new Handler<Long>() {
        public void handle(Long id) {
          uncacheAuthorisation(sessionID, sock);
        }
      });
    }

    void cancel() {
      vertx.cancelTimer(timerID);
    }

  }

  // Hook
  // ==============================

  public void setHook(EventBusBridgeHook hook) {
    this.hook = hook;
  }
  
  public EventBusBridgeHook getHook() {
    return hook;
  }
  
  // Override these to get hooks into the bridge events
  // ==================================================

  /**
   * The socket has been created
   * @param sock The socket
   */
  protected boolean handleSocketCreated(SockJSSocket sock) {
    if (hook != null) {
      return hook.handleSocketCreated(sock);
    } else {
      return true;
    }
  }

  /**
   * The socket has been closed
   * @param sock The socket
   */
  protected void handleSocketClosed(SockJSSocket sock) {
    if (hook != null) {
      hook.handleSocketClosed(sock);
    }
  }

  /**
   * Client is sending or publishing on the socket
   * @param sock The sock
   * @param send if true it's a send else it's a publish
   * @param msg The message
   * @param address The address the message is being sent/published to
   * @return true To allow the send/publish to occur, false otherwise
   */
  protected boolean handleSendOrPub(SockJSSocket sock, boolean send, JsonObject msg, String address) {
    if (hook != null) {
      return hook.handleSendOrPub(sock, send, msg, address);
    }
    return true;
  }

  /**
   * Client is about to register a handler
   * @param sock The socket
   * @param address The address
   * @return true to let the registration occur, false otherwise
   */
  protected boolean handlePreRegister(SockJSSocket sock, String address) {
    if (hook != null) {
      return hook.handlePreRegister(sock, address);
	  }
    return true;
  }

  /**
   * Called after client has registered
   * @param sock The socket
   * @param address The address
   */
  protected void handlePostRegister(SockJSSocket sock, String address) {
    if (hook != null) {
      hook.handlePostRegister(sock, address);
    }
  }

  /**
   * Client is unregistering a handler
   * @param sock The socket
   * @param address The address
   */
  protected boolean handleUnregister(SockJSSocket sock, String address) {
    if (hook != null) {
      return hook.handleUnregister(sock, address);
    }
    return true;
  }

  /**
   * Called before authorisation
   * You can use this to override default authorisation
   * @return true to handle authorisation yourself
   */
  protected boolean handleAuthorise(JsonObject message, final String sessionID,
                                    Handler<AsyncResult<Boolean>> handler) {
    if (hook != null) {
      return hook.handleAuthorise(message, sessionID, handler);
    } else {
      return false;
    }
  }


  private static final class PingInfo {
    long lastPing;
    long timerID;
  }

}
