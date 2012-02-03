package org.vertx.java.busmods.auth;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AuthManager extends BusModBase implements Verticle {

  private static final Logger log = Logger.getLogger(AuthManager.class);

  private static final long DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000; // 30 mins

  private Handler<Message<JsonObject>> loginHandler;
  private Handler<Message<JsonObject>> logoutHandler;
  private Handler<Message<JsonObject>> validateHandler;

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

  public void stop() {
    eb.unregisterHandler(address + ".login", loginHandler);
    eb.unregisterHandler(address + ".logout", logoutHandler);
    eb.unregisterHandler(address + ".validate", validateHandler);
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
          log.error("Failed to execute login query: " + reply.body.getString("message"));
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
      Vertx.instance.cancelTimer(info.timerID);
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
