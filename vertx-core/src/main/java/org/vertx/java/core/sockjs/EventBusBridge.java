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

package org.vertx.java.core.sockjs;

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
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
  private static final long PING_INTERVAL = 10 * 1000;

  private final Map<String, Auth> authCache = new HashMap<>();
  private final Map<SockJSSocket, Set<String>> sockAuths = new HashMap<>();
  private final List<JsonObject> inboundPermitted;
  private final List<JsonObject> outboundPermitted;
  private final long authTimeout;
  private final String authAddress;
  private final Vertx vertx;
  private final EventBus eb;
  private final Set<String> acceptedReplyAddresses = new HashSet<>();
  private final Map<String, Pattern> compiledREs = new HashMap<>();
  private final Map<SockJSSocket, PingInfo> pingInfos = new HashMap<>();
  private EventBusBridgeHook hook;

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

  public EventBusBridge(Vertx vertx, JsonArray inboundPermitted, JsonArray outboundPermitted,
                        long authTimeout,
                        String authAddress) {
    this.vertx = vertx;
    this.eb = vertx.eventBus();
    this.inboundPermitted = convertArray(inboundPermitted);
    this.outboundPermitted = convertArray(outboundPermitted);
    if (authTimeout < 0) {
      throw new IllegalArgumentException("authTimeout < 0");
    }
    this.authTimeout = authTimeout;
    if (authAddress == null) {
      authAddress = DEFAULT_AUTH_ADDRESS;
    }
    this.authAddress = authAddress;
  }

  private void handleSocketClosed(SockJSSocket sock, Map<String, Handler<Message>> handlers) {
    // On close unregister any handlers that haven't been unregistered
    for (Map.Entry<String, Handler<Message>> entry: handlers.entrySet()) {
      //call hook
      handleUnregister(sock, entry.getKey());
      eb.unregisterHandler(entry.getKey(), entry.getValue());
    }

    //Close any cached authorisations for this connection
    Set<String> auths = sockAuths.remove(sock);
    if (auths != null) {
      for (String sessionID: auths) {
        Auth auth = authCache.remove(sessionID);
        if (auth != null) {
          auth.cancel();
        }
      }
    }
    PingInfo pingInfo = pingInfos.remove(sock);
    if (pingInfo != null) {
      vertx.cancelTimer(pingInfo.timerID);
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
        internalHandleRegister(sock, address, handlers);
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

  private void internalHandleRegister(final SockJSSocket sock, final String address, Map<String, Handler<Message>> handlers) {
    if (handlePreRegister(sock, address)) {
      Handler<Message> handler = new Handler<Message>() {
        public void handle(final Message msg) {
          Match curMatch = checkMatches(false, address, msg.body());
          if (curMatch.doesMatch) {
            if (curMatch.requiresAuth && sockAuths.get(sock) == null) {
              log.debug("Outbound message for address " + address + " rejected because auth is required and socket is not authed");
            } else {
              checkAddAccceptedReplyAddress(msg.replyAddress());
              deliverMessage(sock, address, msg);
            }
          } else {
            log.debug("Outbound message for address " + address + " rejected because there is no inbound match");
          }
        }
      };
      handlers.put(address, handler);
      eb.registerHandler(address, handler);
      handlePostRegister(sock, address);
    }
  }

  private void internalHandleUnregister(SockJSSocket sock, String address, Map<String,
      Handler<Message>> handlers) {
    if (handleUnregister(sock, address)) {
      Handler<Message> handler = handlers.remove(address);
      if (handler != null) {
        eb.unregisterHandler(address, handler);
      }
    }
  }

  private void internalHandlePing(final SockJSSocket sock) {
    PingInfo info = pingInfos.get(sock);
    if (info != null) {
      info.lastPing = System.currentTimeMillis();
    }
  }

  public void handle(final SockJSSocket sock) {

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
    pingInfo.timerID = vertx.setPeriodic(PING_INTERVAL, new Handler<Long>() {
      @Override
      public void handle(Long id) {
        if (System.currentTimeMillis() - pingInfo.lastPing >= PING_INTERVAL) {
          // We didn't receive a ping in time so close the socket
          sock.close();
        }
      }
    });
    pingInfos.put(sock, pingInfo);
  }

  private void checkAddAccceptedReplyAddress(final String replyAddress) {
    if (replyAddress != null) {
      // This message has a reply address
      // When the reply comes through we want to accept it irrespective of its address
      // Since all replies are implicitly accepted if the original message was accepted
      // So we cache the reply address, so we can check against it
      acceptedReplyAddresses.add(replyAddress);
      // And we remove after timeout in case the reply never comes
      vertx.setTimer(DEFAULT_REPLY_TIMEOUT, new Handler<Long>() {
        public void handle(Long id) {
          acceptedReplyAddresses.remove(replyAddress);
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

  private static void deliverMessage(SockJSSocket sock, String address, Message message) {
    JsonObject envelope = new JsonObject().putString("address", address).putValue("body", message.body());
    if (message.replyAddress() != null) {
      envelope.putString("replyAddress", message.replyAddress());
    }
    sock.write(new Buffer(envelope.encode()));
  }

  private void doSendOrPub(final boolean send, final SockJSSocket sock, final String address,
                           final JsonObject message) {
    final Object body = getMandatoryValue(message, "body");
    final String replyAddress = message.getString("replyAddress");
    if (log.isDebugEnabled()) {
      log.debug("Received msg from client in bridge. address:"  + address + " message:" + body);
    }
    Match curMatch = checkMatches(true, address, body);
    if (curMatch.doesMatch) {
      if (curMatch.requiresAuth) {
        final String sessionID = message.getString("sessionID");
        if (sessionID != null) {
          authorise(message, sessionID, new AsyncResultHandler<Boolean>() {
            public void handle(AsyncResult<Boolean> res) {
              if (res.succeeded()) {
                if (res.result()) {
                  cacheAuthorisation(sessionID, sock);
                  checkAndSend(send, address, body, sock, replyAddress);
                } else {
                  log.debug("Inbound message for address " + address + " rejected because sessionID is not authorised");
                }
              } else {
                log.error("Error in performing authorisation", res.cause());
              }
            }
          });
        } else {
          log.debug("Inbound message for address " + address + " rejected because it requires auth and sessionID is missing");
        }
      } else {
        checkAndSend(send, address, body, sock, replyAddress);
      }
    } else {
      log.debug("Inbound message for address " + address + " rejected because there is no match");
    }
  }

  private void checkAndSend(boolean send, final String address, Object body,
                            final SockJSSocket sock,
                            final String replyAddress) {
    final Handler<Message> replyHandler;
    if (replyAddress != null) {

      replyHandler = new Handler<Message>() {
        public void handle(Message message) {
          // Note we don't check outbound matches for replies
          // Replies are always let through if the original message
          // was approved
          checkAddAccceptedReplyAddress(message.replyAddress());
          deliverMessage(sock, replyAddress, message);
        }
      };
    } else {
      replyHandler = null;
    }
    if (log.isDebugEnabled()) {
      log.debug("Forwarding message to address " + address + " on event bus");
    }
    if (send) {
      eb.send(address, body, replyHandler);
    } else {
      eb.publish(address, body);
    }
  }

  private void authorise(final JsonObject message, final String sessionID,
                         final Handler<AsyncResult<Boolean>> handler) {
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

  /*
  Empty inboundPermitted means reject everything - this is the default.
  If at least one match is supplied and all the fields of any match match then the message inboundPermitted,
  this means that specifying one match with a JSON empty object means everything is accepted
   */
  private Match checkMatches(boolean inbound, String address, Object body) {

    if (inbound && acceptedReplyAddresses.remove(address)) {
      // This is an inbound reply, so we accept it
      return new Match(true, false);
    }

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
        boolean matched = true;
        // Can send message other than JSON too - in which case we can't do deep matching on structure of message
        if (body instanceof JsonObject) {
          JsonObject match = matchHolder.getObject("match");
          if (match != null) {
            for (String fieldName: match.getFieldNames()) {
              if (!match.getField(fieldName).equals(((JsonObject)body).getField(fieldName))) {
                matched = false;
                break;
              }
            }
          }
        }
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


  private void cacheAuthorisation(String sessionID, SockJSSocket sock) {
    authCache.put(sessionID, new Auth(sessionID, sock));
    Set<String> sesss = sockAuths.get(sock);
    if (sesss == null) {
      sesss = new HashSet<>();
      sockAuths.put(sock, sesss);
    }
    sesss.add(sessionID);
  }

  private void uncacheAuthorisation(String sessionID, SockJSSocket sock) {
    authCache.remove(sessionID);
    Set<String> sess = sockAuths.get(sock);
    if (sess != null) {
      sess.remove(sessionID);
      if (sess.isEmpty()) {
        sockAuths.remove(sock);
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

  private static final class PingInfo {
    long lastPing;
    long timerID;
  }

}
