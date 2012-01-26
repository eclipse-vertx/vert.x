package org.vertx.java.core.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * <p>A SockJSBridgeHandler plugs into a SockJS server and translates data received via SockJS into operations
 * to send messages and register and unregister handlers on the vert.x event bus. </p>
 *
 * <p>When used in conjunction with the vert.x client side JavaScript event bus api (vertxbus.js) this effectively
 * extends the reach of the vert.x event bus from vert.x server side applications to the browser as well. This
 * enables a truly transparent single event bus where client side JavaScript applications can play on the same
 * bus as server side application instances and services.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSBridgeHandler implements Handler<SockJSSocket> {

  private static final Logger log = Logger.getLogger(SockJSBridgeHandler.class);

  private final EventBus eb = EventBus.instance;
  private List<Map<String, Object>> permitted = new ArrayList<>();

  public void addPermitted(Map<String, Object> permitted) {
    this.permitted.add(permitted);
  }

  public void addPermitted(Map<String, Object>[] permittedArr) {
    permitted.addAll(Arrays.asList(permittedArr));
  }

  public void addPermitted(List<Map<String, Object>> permittedList) {
    permitted.addAll(permittedList);
  }

  public void handle(final SockJSSocket sock) {

    final Map<String, Handler<JsonMessage>> handlers = new HashMap<>();

    sock.endHandler(new SimpleHandler() {
      public void handle() {

        // On close unregister any handlers that haven't been unregistered
        for (Map.Entry<String, Handler<JsonMessage>> entry: handlers.entrySet()) {
          eb.unregisterJsonHandler(entry.getKey(), entry.getValue());
        }
      }
    });

    sock.dataHandler(new Handler<Buffer>() {

      private void handleSend(String address, JsonObject jsonObject, final String replyAddress) {
        Handler<JsonMessage> replyHandler;
        if (replyAddress != null) {
          replyHandler = new Handler<JsonMessage>() {
            public void handle(JsonMessage message) {
              deliverMessage(replyAddress, message);
            }
          };
        } else {
          replyHandler = null;
        }
        if (checkMatches(address, jsonObject)) {
          eb.sendJson(address, jsonObject, replyHandler);
        } else {
          log.trace("Message rejected");
        }
      }

      private void deliverMessage(String address, JsonMessage jsonMessage) {
        JsonObject envelope = new JsonObject();
        envelope.putString("address", address);
        if (jsonMessage.replyAddress != null) {
          envelope.putString("replyAddress", jsonMessage.replyAddress);
        }
        envelope.putObject("body", jsonMessage.jsonObject);
        sock.writeBuffer(Buffer.create(envelope.encode()));
      }

      private void handleRegister(final String address) {
        Handler<JsonMessage> handler = new Handler<JsonMessage>() {
          public void handle(JsonMessage msg) {
            deliverMessage(address, msg);
          }
        };

        handlers.put(address, handler);
        eb.registerJsonHandler(address, handler);
      }

      private void handleUnregister(String address) {
        Handler<JsonMessage> handler = handlers.remove(address);
        if (handler != null) {
          eb.unregisterJsonHandler(address, handler);
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
  private boolean checkMatches(String address, JsonObject message) {
    for (Map<String, Object> matchHolder: permitted) {
      String matchAddress = (String)matchHolder.get("address");
      if (matchAddress == null || matchAddress.equals(address)) {
        boolean matched = true;
        Map<String, Object> match = (Map<String, Object>)matchHolder.get("match");
        if (match != null) {
          for (Map.Entry<String, Object> matchEntry: match.entrySet()) {
            Object obj = message.getField(matchEntry.getKey());
            if (!matchEntry.getValue().equals(obj)) {
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
}
