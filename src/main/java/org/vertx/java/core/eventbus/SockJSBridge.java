package org.vertx.java.core.eventbus;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>A SockJSBridge extends the vert.x event bus to client side JavaScript</p>
 *
 * <p>When a SockJSBridge is running, you use the vert.x client side JavaScript event bus API to
 * register and unregister handlers and send messages in much the same way you do using the server side
 * event bus API. See vertxbus.js for more information.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSBridge {

  private static final Logger log = Logger.getLogger(SockJSBridge.class);

  private EventBus eb = EventBus.instance;

  /**
   * Create a new SockJSBridge
   * @param sjsServer The SockJS server to use
   * @param config Sock JS application config to use
   */
  public SockJSBridge(SockJSServer sjsServer, AppConfig config) {
    final ObjectMapper mapper = new ObjectMapper();

    sjsServer.installApp(config, new Handler<SockJSSocket>() {

      public void handle(final SockJSSocket sock) {

        sock.dataHandler(new Handler<Buffer>() {

          Map<String, Handler<Message>> handlers = new HashMap<>();

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
    });
  }
}
