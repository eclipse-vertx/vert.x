package org.vertx.java.core.eventbus.sockjsbridge;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSBridge {

  private static final Logger log = Logger.getLogger(SockJSBridge.class);

  private final SockJSServer sjsServer;

  private EventBus eb = EventBus.instance;

  public SockJSBridge(SockJSServer sjsServer) {
    this.sjsServer = sjsServer;
    final ObjectMapper mapper = new ObjectMapper();

    AppConfig config = new AppConfig().setPrefix("/sockjs-bridge");

    sjsServer.installApp(config, new Handler<SockJSSocket>() {

      public void handle(final SockJSSocket sock) {

        sock.dataHandler(new Handler<Buffer>() {

          Map<String, Handler<Message>> handlers = new HashMap<>();

          private void handleSend(String address, String body) {
            eb.send(new Message(address, Buffer.create(body)));
          }

          private void handleRegister(String address) {
            Handler<Message> handler = new Handler<Message>() {
              public void handle(Message msg) {
                StringBuilder sb = new StringBuilder("{\"address\":\"");
                sb.append(msg.address).append("\",");
                sb.append("\"body\":\"").append(msg.body.toString()).append("\"}");
                sock.writeBuffer(Buffer.create(sb.toString()));
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

          public void handle(Buffer data) {

            log.info("Incoming message " + data.toString());

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
                handleSend(address, body);
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
