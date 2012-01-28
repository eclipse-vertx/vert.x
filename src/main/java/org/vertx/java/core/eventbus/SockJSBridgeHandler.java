package org.vertx.java.core.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.ArrayList;
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
  private List<JsonObject> permitted = new ArrayList<>();

  public void addPermitted(JsonObject permitted) {
    this.permitted.add(permitted);
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
        if (checkMatches(address, jsonObject)) {
          eb.send(address, jsonObject, replyHandler);
        } else {
          log.trace("Message rejected");
        }
      }

      private void deliverMessage(String address, Message<JsonObject> jsonMessage) {
        JsonObject envelope = new JsonObject().putString("address", address).putObject("payload", jsonMessage.body);
        if (jsonMessage.replyAddress != null) {
          envelope.putString("replyAddress", jsonMessage.replyAddress);
        }
        sock.writeBuffer(Buffer.create(envelope.encode()));
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
            JsonObject body = getMandatoryObject(msg, "payload");
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
    for (JsonObject matchHolder: permitted) {
      String matchAddress = matchHolder.getString("address");
      if (matchAddress == null || matchAddress.equals(address)) {
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
}
