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

package org.vertx.java.core.sockjs.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * Bridges the event bus to the client side
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusBridge implements Handler<SockJSSocket> {
  
  private static final Logger log = LoggerFactory.getLogger(EventBusBridge.class);

  private static final String DEFAULT_AUTH_ADDRESS = "vertx.basicauthmanager.authorise";
  private static final long DEFAULT_AUTH_TIMEOUT = 5 * 60 * 1000;
  private static final long DEFAULT_REPLY_TIMEOUT = 30 * 1000;

  private final Map<String, Auth> authCache = new HashMap<>();
  private final Map<SockJSSocket, Set<String>> sockAuths = new HashMap<>();
  private final List<JsonObject> inboundPermitted;
  private final List<JsonObject> outboundPermitted;
  private final long authTimeout;
  private final String authAddress;
  private final Vertx vertx;
  private final EventBus eb;
  private Set<String> acceptedReplyAddresses = new HashSet<>();
  private Map<String, Pattern> compiledREs = new HashMap<>();

  private List<JsonObject> convertArray(JsonArray permitted) {
    List<JsonObject> l = new ArrayList<>();
    for (Object elem: permitted) {
      if (!(elem instanceof JsonObject)) {
        throw new IllegalArgumentException("Permitted must only contain JsonObject");
      }
      l.add((JsonObject) elem);
    }
    return l;
  }

  EventBusBridge(Vertx vertx, SockJSServer sjsServer, JsonObject sjsConfig, JsonArray inboundPermitted,
                 JsonArray outboundPermitted) {
    this(vertx, sjsServer, sjsConfig, inboundPermitted, outboundPermitted, DEFAULT_AUTH_TIMEOUT, null);
  }

  EventBusBridge(Vertx vertx, SockJSServer sjsServer, JsonObject sjsConfig, JsonArray inboundPermitted,
                 JsonArray outboundPermitted,
                 long authTimeout) {
    this(vertx, sjsServer, sjsConfig, inboundPermitted, outboundPermitted, authTimeout, null);
  }

  EventBusBridge(Vertx vertx, SockJSServer sjsServer, JsonObject sjsConfig, JsonArray inboundPermitted,
                 JsonArray outboundPermitted,
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
    sjsServer.installApp(sjsConfig, this);
  }

  public void handle(final SockJSSocket sock) {

    final Map<String, Handler<Message<JsonObject>>> handlers = new HashMap<>();

    sock.endHandler(new SimpleHandler() {
      public void handle() {

        // On close unregister any handlers that haven't been unregistered
        for (Map.Entry<String, Handler<Message<JsonObject>>> entry: handlers.entrySet()) {
          eb.unregisterHandler(entry.getKey(), entry.getValue());
        }

        //Close any cached authorisations for this connection
        Set<String> auths = sockAuths.remove(sock);
        if (auths != null) {
          for (String sessionID: auths) {
            Auth auth = authCache.remove(sessionID);
            auth.cancel();
          }
        }
      }
    });

    sock.dataHandler(new Handler<Buffer>() {

      private void handleRegister(final String address) {
        Handler<Message<JsonObject>> handler = new Handler<Message<JsonObject>>() {
          public void handle(final Message<JsonObject> msg) {
            if (checkMatches(false, address, msg.body, false)) {
              checkAddAccceptedReplyAddress(msg);
              deliverMessage(sock, address, msg);
            } else {
              log.debug("Outbound message for address " + address + " rejected because there is no inbound match");
            }
          }
        };
        handlers.put(address, handler);
        eb.registerHandler(address, handler);
      }

      private void handleUnregister(String address) {
        Handler<Message<JsonObject>> handler = handlers.remove(address);
        if (handler != null) {
          eb.unregisterHandler(address, handler);
        }
      }

      public void handle(Buffer data)  {

        JsonObject msg = new JsonObject(data.toString());

        String type = getMandatoryString(msg, "type");
        String address = getMandatoryString(msg, "address");
        switch (type) {
          case "send":
            sendOrPub(true, msg, address);
            break;
          case "publish":
            sendOrPub(false, msg, address);
            break;
          case "register":
            handleRegister(address);
            break;
          case "unregister":
            handleUnregister(address);
            break;
          default:
            throw new IllegalStateException("Invalid type: " + type);
        }
      }

      private void sendOrPub(boolean send, JsonObject msg, String address) {
        JsonObject body = getMandatoryObject(msg, "body");
        String replyAddress = msg.getString("replyAddress");
        doSendOrPub(send, sock, address, body, replyAddress);
      }
    });
  }

  private void checkAddAccceptedReplyAddress(final Message<JsonObject> msg) {
    if (msg.replyAddress != null) {
      // This message has a reply address
      // When the reply comes through we want to accept it irrespective of its address
      // Since all replies are implicitly accepted if the original message was accepted
      // So we cache the reply address, so we can check against it
      acceptedReplyAddresses.add(msg.replyAddress);
      // And we remove after timeout in case the reply never comes
      vertx.setTimer(DEFAULT_REPLY_TIMEOUT, new Handler<Long>() {
        public void handle(Long id) {
          acceptedReplyAddresses.remove(msg.replyAddress);
        }
      });
    }
  }

  private String getMandatoryString(JsonObject json, String field) {
    String value = json.getString(field);
    if (value == null) {
      throw new IllegalStateException(field + " must be specified for message");
    }
    return value;
  }

  private JsonObject getMandatoryObject(JsonObject json, String field) {
    JsonObject value = json.getObject(field);
    if (value == null) {
      throw new IllegalStateException(field + " must be specified for message");
    }
    return value;
  }

  private void deliverMessage(SockJSSocket sock, String address, Message<JsonObject> jsonMessage) {
    JsonObject envelope = new JsonObject().putString("address", address).putObject("body", jsonMessage.body);
    if (jsonMessage.replyAddress != null) {
      envelope.putString("replyAddress", jsonMessage.replyAddress);
    }
    sock.writeBuffer(new Buffer(envelope.encode()));
  }

  private void doSendOrPub(final boolean send, final SockJSSocket sock, final String address,
                           final JsonObject jsonObject, final String replyAddress) {
    if (log.isDebugEnabled()) {
      log.debug("Received msg from client in bridge. address:"  + address + " message:" + jsonObject.encode());
    }

    final String sessionID = jsonObject.getString("sessionID");
    if (sessionID != null) {
      authorise(jsonObject, sessionID, new AsyncResultHandler<Boolean>() {
        public void handle(AsyncResult<Boolean> res) {
          if (res.succeeded()) {
            if (res.result) {
              cacheAuthorisation(sessionID, sock);
            }
            checkAndSend(send, address, jsonObject, res.result, sock, replyAddress);
          } else {
            log.error("Error in performing authorisation", res.exception);
          }
        }
      });
    } else {
      checkAndSend(send, address, jsonObject, false, sock, replyAddress);
    }
  }

  private void checkAndSend(boolean send, final String address, JsonObject jsonObject,
                            boolean authed,
                            final SockJSSocket sock,
                            final String replyAddress) {
    if (checkMatches(true, address, jsonObject, authed)) {
      final Handler<Message<JsonObject>> replyHandler;
      if (replyAddress != null) {
        replyHandler = new Handler<Message<JsonObject>>() {
          public void handle(Message<JsonObject> message) {
            // Note we don't check outbound matches for replies
            // Replies are always let through if the original message
            // was approved
            checkAddAccceptedReplyAddress(message);
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
        eb.send(address, jsonObject, replyHandler);
      } else {
        eb.publish(address, jsonObject);
      }
    } else {
      log.debug("Inbound message for address " + address + " rejected because there is no match");
    }
  }

  private void authorise(final JsonObject message, final String sessionID,
                         final AsyncResultHandler<Boolean> handler) {
    // If session id is in local cache we'll consider them authorised
    if (authCache.containsKey(sessionID)) {
      handler.handle(new AsyncResult<>(true));
    } else {
      eb.send(authAddress, message, new Handler<Message<JsonObject>>() {
        public void handle(Message<JsonObject> reply) {
          boolean authed = reply.body.getString("status").equals("ok");
          handler.handle(new AsyncResult<>(authed));
        }
      });
    }
  }

  /*
  Empty inboundPermitted means reject everything - this is the default.
  If at least one match is supplied and all the fields of any match match then the message inboundPermitted,
  this means that specifying one match with a JSON empty object means everything is accepted
   */
  private boolean checkMatches(boolean inbound, String address, JsonObject message, boolean authed) {

    if (inbound && acceptedReplyAddresses.remove(address)) {
      // This is an inbound reply, so we accept it
      return true;
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
        Boolean b = matchHolder.getBoolean("requires_auth");
        boolean requiresAuth = b != null && b;
        if (requiresAuth && !authed) {
          // No match since user is not authed
          log.debug("Rejecting match since user is not authorised");
          break;
        }
        boolean matched = true;
        JsonObject match = matchHolder.getObject("match");
        if (match != null) {
          for (String fieldName: match.getFieldNames()) {
            if (!match.getField(fieldName).equals(message.getField(fieldName))) {
              matched = false;
              break;
            }
          }
        }
        if (matched) {
          return true;
        }
      }
    }
    return false;
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

}
