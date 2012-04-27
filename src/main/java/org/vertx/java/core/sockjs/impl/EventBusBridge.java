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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 *
 * Bridges the event bus to the client side
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusBridge implements Handler<SockJSSocket> {
  
  private static final Logger log = LoggerFactory.getLogger(EventBusBridge.class);

  private static final String DEFAULT_LOGIN_ADDRESS = "vertx.bridge.login";
  private static final String DEFAULT_LOGOUT_ADDRESS = "vertx.bridge.logout";
  private static final long DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000;

  private Handler<Message<JsonObject>> loginHandler;
  private Handler<Message<JsonObject>> logoutHandler;

  private final Map<String, String> sessions = new HashMap<>();
  private final Map<String, LoginInfo> logins = new HashMap<>();

  private final List<JsonObject> permitted;
  private final String userCollection;
  private final String persistorAddress;
  private long sessionTimeout;
  private final Vertx vertx;
  private final EventBus eb;
  private final String loginAddress;
  private final String logoutAddress;

  private static final class LoginInfo {
    final long timerID;
    final String sessionID;

    private LoginInfo(long timerID, String sessionID) {
      this.timerID = timerID;
      this.sessionID = sessionID;
    }
  }

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
    this(vertx, sjsServer, sjsConfig, permitted, null, null, DEFAULT_SESSION_TIMEOUT);
  }

  EventBusBridge(Vertx vertx, SockJSServer sjsServer, JsonObject sjsConfig, JsonArray permitted,
                        String userCollection, String persistorAddress) {
    this(vertx, sjsServer, sjsConfig, permitted, userCollection, persistorAddress, DEFAULT_SESSION_TIMEOUT);
  }

  EventBusBridge(Vertx vertx, SockJSServer sjsServer, JsonObject sjsConfig, JsonArray permitted,
                        String userCollection, String persistorAddress, Long sessionTimeout) {
    this(vertx, sjsServer, sjsConfig, permitted, userCollection, persistorAddress, sessionTimeout,
        DEFAULT_LOGIN_ADDRESS, DEFAULT_LOGOUT_ADDRESS);
  }

  EventBusBridge(Vertx vertx, SockJSServer sjsServer, JsonObject sjsConfig, JsonArray permitted,
                        String userCollection, String persistorAddress, Long sessionTimeout,
                        String loginAddress, String logoutAddress) {
    this.vertx = vertx;
    this.eb = vertx.eventBus();
    this.permitted = convertArray(permitted);
    this.userCollection = userCollection;
    this.persistorAddress = persistorAddress;
    this.sessionTimeout = sessionTimeout;
    this.loginAddress = loginAddress;
    this.logoutAddress = logoutAddress;
    if (userCollection != null) {
      loginHandler = new Handler<Message<JsonObject>>() {
        public void handle(Message<JsonObject> message) {
          doLogin(message);
        }
      };
      eb.registerHandler(loginAddress, loginHandler);
      logoutHandler = new Handler<Message<JsonObject>>() {
        public void handle(Message<JsonObject> message) {
          doLogout(message);
        }
      };
      eb.registerHandler(logoutAddress, logoutHandler);
    }
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
      }
    });

    sock.dataHandler(new Handler<Buffer>() {

      private void handleSend(String address, JsonObject jsonObject, final String replyAddress) {

        if (log.isDebugEnabled()) {
          log.debug("Received msg from client in bridge. address:"  + address + " message:" + jsonObject.encode());
        }

        Handler<Message<JsonObject>> replyHandler;
        if (replyAddress != null) {
          replyHandler = new Handler<Message<JsonObject>>() {
            public void handle(Message<JsonObject> message) {
              deliverMessage(replyAddress, message);
            }
          };
        } else {
          replyHandler = null;
        }
        jsonObject = checkMatches(address, jsonObject);
        if (jsonObject != null) {
          eb.send(address, jsonObject, replyHandler);
        } else {
          log.debug("Message rejected because there is no permitted match");
        }
      }

      private void deliverMessage(String address, Message<JsonObject> jsonMessage) {
        JsonObject envelope = new JsonObject().putString("address", address).putObject("body", jsonMessage.body);
        if (jsonMessage.replyAddress != null) {
          envelope.putString("replyAddress", jsonMessage.replyAddress);
        }
        sock.writeBuffer(new Buffer(envelope.encode()));
      }

      private void handleRegister(final String address) {
        Handler<Message<JsonObject>> handler = new Handler<Message<JsonObject>>() {
          public void handle(Message<JsonObject> msg) {
            deliverMessage(address, msg);
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

      public void handle(Buffer data)  {

        JsonObject msg = new JsonObject(data.toString());

        String type = getMandatoryString(msg, "type");
        String address = getMandatoryString(msg, "address");
        switch (type) {
          case "send":
            JsonObject body = getMandatoryObject(msg, "body");
            String replyAddress = msg.getString("replyAddress");
            handleSend(address, body, replyAddress);
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

  /*
  Empty permitted means reject everything - this is the default.
  If at least one match is supplied and all the fields of any match match then the message permitted,
  this means that specifying one match with a JSON empty object means everything is accepted
   */
  private JsonObject checkMatches(String address, JsonObject message) {
    if (address.equals(loginAddress) || address.equals(logoutAddress)) {
      return message;
    }

    boolean authed = false;
    boolean doneAuth = false;

    for (JsonObject matchHolder: permitted) {
      String matchAddress = matchHolder.getString("address");
      if (matchAddress == null || matchAddress.equals(address)) {
        Boolean b = matchHolder.getBoolean("requires_auth");
        boolean requiresAuth = b != null && b.booleanValue();
        if (requiresAuth) {
          if (!doneAuth) {
            String sessionID = message.getString("sessionID");
            if (sessionID != null) {
              authed = sessions.containsKey(sessionID);
            }
          }
          if (!authed) {
            // No match since user is not authed
            log.debug("Rejecting match since user is not authenticated");
            break;
          }
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

  private void doLogin(final Message<JsonObject> message) {
    final String username = message.body.getString("username");
    if (username == null) {
      throw new IllegalArgumentException("Login message must have a username field");
    }
    String password = message.body.getString("password");
    if (password == null) {
      throw new IllegalArgumentException("Login message must have a password field");
    }

    if (log.isDebugEnabled()) {
      log.debug("Handling login for " + username);
    }

    JsonObject findMsg = new JsonObject().putString("action", "findone").putString("collection", userCollection);
    JsonObject matcher = new JsonObject().putString("username", username).putString("password", password);
    findMsg.putObject("matcher", matcher);

    eb.send(persistorAddress, findMsg, new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> reply) {

        if (reply.body.getString("status").equals("ok")) {
          if (reply.body.getObject("result") != null) {

            // Check if already logged in, if so logout of the old session
            LoginInfo info = logins.get(username);
            if (info != null) {
              logout(info.sessionID);
            }

            // Found
            final String sessionID = UUID.randomUUID().toString();
            long timerID = vertx.setTimer(sessionTimeout, new Handler<Long>() {
              public void handle(Long timerID) {
                sessions.remove(sessionID);
                logins.remove(username);
              }
            });
            sessions.put(sessionID, username);
            logins.put(username, new LoginInfo(timerID, sessionID));
            JsonObject jsonReply = new JsonObject().putString("sessionID", sessionID);
            sendOK(message, jsonReply);
            log.debug("Logged in ok");
          } else {
            // Not found
            sendStatus("denied", message);
            log.debug("Login failed");
          }
        } else {
          log.error("Failed to execute login query: " + reply.body.getString("message"));
          sendError(message, "Failed to excecute login");
        }
      }
    });
  }

  private void doLogout(final Message<JsonObject> message) {
    final String sessionID = message.body.getString("sessionID");
    if (sessionID == null) {
      throw new IllegalArgumentException("Logout message must contain sessionID field");
    }
    if (logout(sessionID)) {
      sendOK(message);
    } else {
      sendError(message, "Not logged in");
    }
  }

  private boolean logout(String sessionID) {
    String username = sessions.remove(sessionID);
    if (username != null) {
      LoginInfo info = logins.remove(username);
      vertx.cancelTimer(info.timerID);
      return true;
    } else {
      return false;
    }
  }

  private void sendOK(Message<JsonObject> message) {
    sendOK(message, null);
  }

  private void sendStatus(String status, Message<JsonObject> message) {
    sendStatus(status, message, null);
  }

  private void sendStatus(String status, Message<JsonObject> message, JsonObject json) {
    if (json == null) {
      json = new JsonObject();
    }
    json.putString("status", status);
    message.reply(json);
  }

  private void sendOK(Message<JsonObject> message, JsonObject json) {
    sendStatus("ok", message, json);
  }

  private void sendError(Message<JsonObject> message, String error) {
    sendError(message, error, null);
  }

  private void sendError(Message<JsonObject> message, String error, Exception e) {
    log.error(error, e);
    JsonObject json = new JsonObject().putString("status", "error").putString("message", error);
    message.reply(json);
  }
}
