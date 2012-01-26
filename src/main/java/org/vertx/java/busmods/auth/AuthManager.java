package org.vertx.java.busmods.auth;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.JsonMessage;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AuthManager extends BusModBase implements VertxApp  {

  private static final Logger log = Logger.getLogger(AuthManager.class);

  private static final long DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000; // 30 mins

  private Handler<JsonMessage> loginHandler;
  private Handler<JsonMessage> logoutHandler;
  private Handler<JsonMessage> validateHandler;

  private final String userCollection;
  private final String persistorAddress;
  private final Map<String, String> sessions = new HashMap<>();
  private final Map<String, LoginInfo> logins = new HashMap<>();
  private final long sessionTimeout;

  private static final class LoginInfo {
    final long timerID;
    final String sessionID;

    private LoginInfo(long timerID, String sessionID) {
      this.timerID = timerID;
      this.sessionID = sessionID;
    }
  }

  public AuthManager(final String address, final String userCollection, final String persistorAddress) {
    this(address, userCollection, persistorAddress, DEFAULT_SESSION_TIMEOUT);
  }

  public AuthManager(final String address, final String userCollection, final String persistorAddress,
                     final long sessionTimeout) {
    super(address, false);
    this.userCollection = userCollection;
    this.persistorAddress = persistorAddress;
    this.sessionTimeout = sessionTimeout;
  }

  public void start() {
    loginHandler = new Handler<JsonMessage>() {
      public void handle(JsonMessage message) {
        doLogin(message);
      }
    };
    eb.registerJsonHandler(address + ".login", loginHandler);
    logoutHandler = new Handler<JsonMessage>() {
      public void handle(JsonMessage message) {
        doLogout(message);
      }
    };
    eb.registerJsonHandler(address + ".logout", logoutHandler);
    validateHandler = new Handler<JsonMessage>() {
      public void handle(JsonMessage message) {
        doValidate(message);
      }
    };
    eb.registerJsonHandler(address + ".validate", validateHandler);
  }

  public void stop() {
    eb.unregisterJsonHandler(address + ".login", loginHandler);
    eb.unregisterJsonHandler(address + ".logout", logoutHandler);
    eb.unregisterJsonHandler(address + ".validate", validateHandler);
  }

  private void doLogin(final JsonMessage message) {

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

    eb.sendJson(persistorAddress, findMsg, new Handler<JsonMessage>() {
      public void handle(JsonMessage reply) {

        if (reply.jsonObject.getString("status").equals("ok")) {
          if (reply.jsonObject.getObject("result") != null) {

            // Check if already logged in, if so logout of the old session
            LoginInfo info = logins.get(username);
            if (info != null) {
              logout(info.sessionID);
            }

            // Found
            final String sessionID = UUID.randomUUID().toString();
            long timerID = Vertx.instance.setTimer(sessionTimeout, new Handler<Long>() {
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
          log.error("Failed to execute login query: " + reply.jsonObject.getString("message"));
          sendError(message, "Failed to excecute login");
        }
      }
    });
  }

  private void doLogout(final JsonMessage message) {
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
      Vertx.instance.cancelTimer(info.timerID);
      return true;
    } else {
      return false;
    }
  }

  private void doValidate(JsonMessage message) {
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
