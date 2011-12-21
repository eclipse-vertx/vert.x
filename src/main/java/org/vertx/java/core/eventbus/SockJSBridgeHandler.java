package org.vertx.java.core.eventbus;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.HashMap;
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

  private final EventBus eb = EventBus.instance;
  private final ObjectMapper mapper = new ObjectMapper();

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

      private void handleSend(String address, String body, final String messageID, final String replyAddress) {
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
        eb.send(new Message(messageID, address, Buffer.create(body)), replyHandler);
      }

      private void deliverMessage(Message msg) {
        sock.writeBuffer(Buffer.create(msg.toJSONString()));
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

      public void handle(Buffer data)  {

        Map<String, Object> msg;
        try {
          msg = mapper.readValue(data.toString(), Map.class);
        } catch (Exception e) {
          throw new IllegalStateException("Failed to parse JSON");
        }

        String type = (String)msg.get("type");
        if (type == null) {
          throw new IllegalStateException("type must be specified for message");
        }

        String address = (String)msg.get("address");
        if (address == null) {
          throw new IllegalStateException("address must be specified for message");
        }

        switch (type) {
          case "send":
            String body = (String)msg.get("body");
            if (body == null) {
              throw new IllegalStateException("body must be specified for message");
            }
            String replyAddress = (String)msg.get("replyAddress");
            String messageID = (String)msg.get("messageID");
            handleSend(address, body, messageID, replyAddress);
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
}
