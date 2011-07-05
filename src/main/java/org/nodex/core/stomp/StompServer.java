package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.net.Server;
import org.nodex.core.net.Socket;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: timfox
 * Date: 28/06/2011
 * Time: 00:19
 * <p/>
 * Simple STOMP 1.0 server implementation - doesn't currently handle transactions or acks and just does basic pub/sub
 */
public class StompServer {

  public static Server createServer() {

    return Server.createServer(new Callback<Socket>() {

      private ConcurrentMap<String, List<Connection>> subscriptions = new ConcurrentHashMap<String, List<Connection>>();

      private synchronized void subscribe(String dest, Connection conn) {
        List<Connection> conns = subscriptions.get(dest);
        if (conns == null) {
          conns = new CopyOnWriteArrayList<Connection>();
          subscriptions.put(dest, conns);
        }
        conns.add(conn);
      }

      private synchronized void unsubscribe(String dest, Connection conn) {
        List<Connection> conns = subscriptions.get(dest);
        if (conns == null) {
          conns.remove(conn);
          if (conns.isEmpty()) {
            subscriptions.remove(dest);
          }
        }
      }

      private void checkReceipt(Frame frame, Connection conn) {
        String receipt = frame.headers.get("receipt");
        if (receipt != null) {
          conn.write(Frame.receiptFrame(receipt));
        }
      }

      public void onEvent(final Socket sock) {
        final ServerConnection conn = new ServerConnection(sock);
        conn.frameHandler(new Callback<Frame>() {
          public void onEvent(Frame frame) {
            if ("CONNECT".equals(frame.command)) {
              conn.write(Frame.connectedFrame(UUID.randomUUID().toString()));
              return;
            }
            //The following can have optional receipt
            if ("SUBSCRIBE".equals(frame.command)) {
              String dest = frame.headers.get("destination");
              subscribe(dest, conn);
            } else if ("UNSUBSCRIBE".equals(frame.command)) {
              String dest = frame.headers.get("destination");
              unsubscribe(dest, conn);
            } else if ("SEND".equals(frame.command)) {
              String dest = frame.headers.get("destination");
              frame.command = "MESSAGE";
              List<Connection> conns = subscriptions.get(dest);
              if (conns != null) {
                for (Connection conn : conns) {
                  frame.headers.put("message-id", UUID.randomUUID().toString());
                  conn.write(frame);
                }
              }
            }
            checkReceipt(frame, conn);
          }
        });
      }
    });
  }
}
