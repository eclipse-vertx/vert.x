package org.nodex.examples.stomp;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.stomp.Client;
import org.nodex.core.stomp.Connection;
import org.nodex.core.stomp.Frame;

import java.util.HashMap;
import java.util.Map;

/**
 * User: tfox
 * Date: 28/06/11
 * Time: 12:37
 */
public class StompClient {

  public static void main(String[] args) throws Exception {

    Client.connect(8080, new Callback<Connection>() {
      public void onEvent(Connection conn) {
        System.out.println("Connected");
        conn.data(new Callback<Frame>() {
          public void onEvent(Frame frame) {
            System.out.println("received message command: " + frame.command + " body: " + frame.body.toString());
          }
        });

        conn.write(createConnectFrame());
        System.out.println("Sent connect");

        conn.write(createSubscribeFrame());
        System.out.println("Sent subscribe");

        for (int i = 0; i < 5; i++) {
          conn.write(createSendFrame("message " + i));
        }

      }
    });

    System.out.println("Any key to exit");
    System.in.read();
  }

  private static Frame createSendFrame(String str) {
    Map<String, String> headers = new HashMap<String, String>();
    headers.put("destination", "test-topic");
    Buffer body = Buffer.fromString(str);
    Frame frame = new Frame("SEND", headers, body);
    //System.out.println("Send frame:\n" + frame.toString());
    return frame;
  }

  private static Frame createSubscribeFrame() {
    Map<String, String> headers = new HashMap<String, String>();
    headers.put("destination", "test-topic");
    Buffer body = Buffer.fromString("this-is-the-subscribe-body");
    Frame frame = new Frame("SUBSCRIBE", headers, body);
    //System.out.println("Subscribe frame:\n" + frame.toString());
    return frame;
  }

  private static Frame createConnectFrame() {
    Map<String, String> headers = new HashMap<String, String>();
    headers.put("login", "joe-bloggs");
    headers.put("passcode", "password");
    Buffer body = Buffer.fromString("this-is-the-connect-body");
    Frame frame = new Frame("CONNECT", headers, body);
    //System.out.println("Connect frame:\n" + frame.toString());
    return frame;
  }


}
