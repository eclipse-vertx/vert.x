package org.vertx.java.core.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
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
  private final JsonHelper helper = new JsonHelper();
  private List<Map<String, Object>> matches = new ArrayList<>();

  public void addMatch(Map<String, Object> match) {
    matches.add(match);
  }

  public void addMatches(Map<String, Object>[] matchesArr) {
    matches.addAll(Arrays.asList(matchesArr));
  }

  public void addMatches(List<Map<String, Object>> matchesList) {
    matches.addAll(matchesList);
  }

  public void handle(final SockJSSocket sock) {

    final Map<String, Handler<Message>> handlers = new HashMap<>();

    sock.endHandler(new SimpleHandler() {
      public void handle() {

        // On close unregister any handlers that haven't been unregistered
        for (Map.Entry<String, Handler<Message>> entry: handlers.entrySet()) {
          eb.unregisterHandler(entry.getKey(), entry.getValue());
        }
      }
    });

    sock.dataHandler(new Handler<Buffer>() {

      private void handleSend(String address, Map<String, Object> body, final String replyAddress) {
        Handler<Message> replyHandler;
        if (replyAddress != null) {
          replyHandler = new Handler<Message>() {
            public void handle(Message message) {
              message.address = replyAddress;
              deliverMessage(message);
            }
          };
        } else {
          replyHandler = null;
        }
        if (checkMatches(address, body)) {
          helper.sendJSON(address, body, replyHandler);
        } else {
          log.info("Message rejected");
        }
      }

      private void deliverMessage(Message msg) {
        Map<String, Object> json = helper.toJson(msg);
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("address", msg.address);
        if (msg.replyAddress != null) {
          envelope.put("replyAddress", msg.replyAddress);
        }
        envelope.put("body", json);
        sock.writeBuffer(Buffer.create(helper.jsonToString(envelope)));
      }

      private void handleRegister(String address) {
        Handler<Message> handler = new Handler<Message>() {
          public void handle(Message msg) {
            deliverMessage(msg);
          }
        };

        handlers.put(address, handler);
        eb.registerHandler(address, handler);
      }

      private void handleUnregister(String address) {
        Handler<Message> handler = handlers.remove(address);
        if (handler != null) {
          eb.unregisterHandler(address, handler);
        }
      }

      private Object getMandatory(Map<String, Object> json, String field) {
        Object value = json.get(field);
        if (value == null) {
          throw new IllegalStateException(field + " must be specified for message");
        }
        return value;
      }

      public void handle(Buffer data)  {

        Map<String, Object> msg = helper.stringToJson(data.toString());

        String type = (String)getMandatory(msg, "type");
        String address = (String)getMandatory(msg, "address");
        switch (type) {
          case "send":
            Map<String, Object> body = (Map<String, Object>)getMandatory(msg, "body");
            String replyAddress = (String)msg.get("replyAddress");
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
  Empty matches means reject everything - this is the default.
  If at least one match is supplied and all the fields of any match match then the message matches,
  this means that specifying one match with a JSON empty object means everything is accepted
   */
  private boolean checkMatches(String address, Map<String, Object> message) {
    for (Map<String, Object> matchHolder: matches) {
      String matchAddress = (String)matchHolder.get("address");
      if (matchAddress == null || matchAddress.equals(address)) {
        boolean matched = true;
        Map<String, Object> match = (Map<String, Object>)matchHolder.get("match");
        if (match != null) {
          for (Map.Entry<String, Object> matchEntry: match.entrySet()) {
            Object obj = message.get(matchEntry.getKey());
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
