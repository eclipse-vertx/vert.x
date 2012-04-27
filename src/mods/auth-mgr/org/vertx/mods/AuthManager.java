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

package org.vertx.mods;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication Manager Bus Module<p>
 * Please see the busmods manual for a full description<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AuthManager extends BusModBase {

  private Handler<Message<JsonObject>> loginHandler;
  private Handler<Message<JsonObject>> logoutHandler;
  private Handler<Message<JsonObject>> validateHandler;

  private final Map<String, String> sessions = new HashMap<>();
  private final Map<String, LoginInfo> logins = new HashMap<>();

  private String address;
  private String userCollection;
  private String persistorAddress;
  private long sessionTimeout;

  private static final class LoginInfo {
    final long timerID;
    final String sessionID;

    private LoginInfo(long timerID, String sessionID) {
      this.timerID = timerID;
      this.sessionID = sessionID;
    }
  }

  /**
   * Start the busmod
   */
  public void start() {
    super.start();

    this.address = getMandatoryStringConfig("address");
    this.userCollection = getMandatoryStringConfig("user_collection");
    this.persistorAddress = getMandatoryStringConfig("persistor_address");
    Number timeout = config.getNumber("session_timeout");
    if (timeout != null) {
      if (timeout instanceof Long) {
        this.sessionTimeout = (Long)timeout;
      } else if (timeout instanceof Integer) {
        this.sessionTimeout = (Integer)timeout;
      }
    } else {
      this.sessionTimeout = 30 * 60 * 1000;
    }

    loginHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        doLogin(message);
      }
    };
    eb.registerHandler(address + ".login", loginHandler);
    logoutHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        doLogout(message);
      }
    };
    eb.registerHandler(address + ".logout", logoutHandler);
    validateHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        doValidate(message);
      }
    };
    eb.registerHandler(address + ".validate", validateHandler);
  }

  private void doLogin(final Message<JsonObject> message) {

    final String username = getMandatoryString("username", message);
    if (username == null) {
      return;
    }
    String password = getMandatoryString("password", message);
    if (password == null) {
      return;
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
          } else {
            // Not found
            sendStatus("denied", message);
          }
        } else {
          logger.error("Failed to execute login query: " + reply.body.getString("message"));
          sendError(message, "Failed to excecute login");
        }
      }
    });
  }

  private void doLogout(final Message<JsonObject> message) {
    final String sessionID = getMandatoryString("sessionID", message);
    if (sessionID != null) {
      if (logout(sessionID)) {
        sendOK(message);
      } else {
        super.sendError(message, "Not logged in");
      }
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

  private void doValidate(Message<JsonObject> message) {
    String sessionID = getMandatoryString("sessionID", message);
    if (sessionID == null) {
      return;
    }
    String username = sessions.get(sessionID);
    if (username != null) {
      JsonObject reply = new JsonObject().putString("username", username);
      sendOK(message, reply);
    } else {
      sendStatus("denied", message);
    }
  }


}
