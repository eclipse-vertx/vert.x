package org.vertx.java.busmods.auth;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AuthManager extends BusModBase implements VertxApp  {

  private static final Logger log = Logger.getLogger(AuthManager.class);

  private static final long DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000; // 30 mins

  private Handler<Message> loginHandler;
  private Handler<Message> logoutHandler;
  private Handler<Message> validateHandler;

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
    loginHandler = new Handler<Message>() {
      public void handle(Message message) {
        Map<String, Object> json = helper.toJson(message);
        doLogin(message, json);
      }
    };
    eb.registerHandler(address + ".login", loginHandler);
    logoutHandler = new Handler<Message>() {
      public void handle(Message message) {
        Map<String, Object> json = helper.toJson(message);
        doLogout(message, json);
      }
    };
    eb.registerHandler(address + ".logout", logoutHandler);
    validateHandler = new Handler<Message>() {
      public void handle(Message message) {
        Map<String, Object> json = helper.toJson(message);
        doValidate(message, json);
      }
    };
    eb.registerHandler(address + ".validate", validateHandler);
  }

  public void stop() {
    eb.unregisterHandler(address + ".login", loginHandler);
    eb.unregisterHandler(address + ".logout", logoutHandler);
    eb.unregisterHandler(address + ".validate", validateHandler);
  }

  private void doLogin(final Message message, Map<String, Object> map) {
    final String username = (String)super.getMandatory("username", message, map);
    if (username == null) {
      return;
    }
    String password = (String)super.getMandatory("password", message, map);
    if (password == null) {
      return;
    }
    Map<String, Object> findMsg = new HashMap<>();
    findMsg.put("action", "findone");
    findMsg.put("collection", userCollection);
    Map<String, Object> matcher = new HashMap<>();
    matcher.put("username", username);
    matcher.put("password", password);
    findMsg.put("matcher", matcher);

    helper.sendJSON(persistorAddress, findMsg, new Handler<Message>() {
      public void handle(Message reply) {
        Map<String, Object> json = helper.toJson(reply);
        if (json.get("status").equals("ok")) {
          if (json.get("result") != null) {

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
            Map<String, Object> jsonReply = new HashMap<>();
            jsonReply.put("sessionID", sessionID);
            sendOK(message, jsonReply);
          } else {
            // Not found
            sendStatus("denied", message);
          }
        } else {
          log.error("Failed to execute login query: " + json.get("message"));
          sendError(message, "Failed to excecute login");
        }
      }
    });
  }

  private void doLogout(final Message message, Map<String, Object> map) {
    final String sessionID = (String)super.getMandatory("sessionID", message, map);
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

  private void doValidate(Message message, Map<String, Object> map) {
    String sessionID = (String)super.getMandatory("sessionID", message, map);
    if (sessionID == null) {
      return;
    }
    if (sessions.containsKey(sessionID)) {
      sendOK(message);
    } else {
      sendStatus("denied", message);
    }
  }


}
