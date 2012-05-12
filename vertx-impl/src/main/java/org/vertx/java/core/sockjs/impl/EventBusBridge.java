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

  private final Map<String, Auth> authCache = new HashMap<>();
  private final Map<SockJSSocket, Set<String>> sockAuths = new HashMap<>();
  private final List<JsonObject> permitted;
  private final long authTimeout;
  private final String authAddress;
  private final Vertx vertx;
  private final EventBus eb;

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

  EventBusBridge(Vertx vertx, SockJSServer sjsServer, JsonObject sjsConfig, JsonArray permitted) {
    this(vertx, sjsServer, sjsConfig, permitted, DEFAULT_AUTH_TIMEOUT, null);
  }

  EventBusBridge(Vertx vertx, SockJSServer sjsServer, JsonObject sjsConfig, JsonArray permitted,
                 long authTimeout) {
    this(vertx, sjsServer, sjsConfig, permitted, authTimeout, null);
  }

  EventBusBridge(Vertx vertx, SockJSServer sjsServer, JsonObject sjsConfig, JsonArray permitted,
                 long authTimeout,
                 String authAddress) {
    this.vertx = vertx;
    this.eb = vertx.eventBus();
    this.permitted = convertArray(permitted);
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
          public void handle(Message<JsonObject> msg) {
            deliverMessage(sock, address, msg);
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
            JsonObject body = getMandatoryObject(msg, "body");
            String replyAddress = msg.getString("replyAddress");
            doSend(sock, address, body, replyAddress);
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
    });
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

  private void doSend(final SockJSSocket sock, final String address, final JsonObject jsonObject, final String replyAddress) {
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
            checkAndSend(address, jsonObject, res.result, sock, replyAddress);
          } else {
            log.error("Error in performing authorisation", res.exception);
          }
        }
      });
    } else {
      checkAndSend(address, jsonObject, false, sock, replyAddress);
    }
  }

  private void checkAndSend(String address, JsonObject jsonObject, boolean authed,
                            final SockJSSocket sock,
                            final String replyAddress) {
    jsonObject = checkMatches(address, jsonObject, authed);
    if (jsonObject != null) {
      final Handler<Message<JsonObject>> replyHandler;
      if (replyAddress != null) {
        replyHandler = new Handler<Message<JsonObject>>() {
          public void handle(Message<JsonObject> message) {
            deliverMessage(sock, replyAddress, message);
          }
        };
      } else {
        replyHandler = null;
      }
      eb.send(address, jsonObject, replyHandler);
    } else {
      log.debug("Message rejected because there is no permitted match");
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
  Empty permitted means reject everything - this is the default.
  If at least one match is supplied and all the fields of any match match then the message permitted,
  this means that specifying one match with a JSON empty object means everything is accepted
   */
  private JsonObject checkMatches(String address, JsonObject message, boolean authed) {
//    if (address.equals(loginAddress) || address.equals(logoutAddress)) {
//      return message;
//    }

    for (JsonObject matchHolder: permitted) {
      String matchAddress = matchHolder.getString("address");
      if (matchAddress == null || matchAddress.equals(address)) {
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
          return message;
        }
      }
    }
    return null;
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
