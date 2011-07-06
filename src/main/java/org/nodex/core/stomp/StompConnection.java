package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.composition.Completion;
import org.nodex.core.net.NetSocket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: tim
 * Date: 27/06/11
 * Time: 18:31
 */
public class StompConnection {

  public static final String CORRELATION_ID_HEADER = "correlation-id";
  public static final String REPLY_TO_HEADER = "reply-to";

  private final NetSocket socket;
  private Callback<Frame> errorCallback;
  private NoArgCallback connectCallback;
  protected boolean connected;
  private Map<String, StompMsgCallback> subscriptions = new HashMap<String, StompMsgCallback>();
  private Map<String, NoArgCallback> waitingReceipts = new ConcurrentHashMap<String, NoArgCallback>();

  protected StompConnection(NetSocket socket) {
    this.socket = socket;
    socket.data(new Parser(new Callback<Frame>() {
      public void onEvent(Frame frame) {
        handleFrame(frame);
      }
    }));
  }

  public void error(Callback<Frame> errorCallback) {
    this.errorCallback = errorCallback;
  }

  public void close() {
    socket.close();
  }

  // Send without receipt
  public void send(String dest, Buffer body) {
    send(dest, new HashMap<String, String>(4), body, null);
  }

  // Send without receipt
  public void send(String dest, Map<String, String> headers, Buffer body) {
    send(dest, headers, body, null);
  }

  // Send with receipt
  public void send(String dest, Buffer body, NoArgCallback completeCallback) {
    send(dest, new HashMap<String, String>(4), body, completeCallback);
  }

  // Send with receipt
  public void send(String dest, Map<String, String> headers, Buffer body, NoArgCallback completeCallback) {
    Frame frame = new Frame("SEND", headers, body);
    frame.headers.put("destination", dest);
    addReceipt(frame, completeCallback);
    write(frame);
  }

  // HttpRequest-response pattern

  private Map<String, StompMsgCallback> callbacks = new ConcurrentHashMap<String, StompMsgCallback>();
  private volatile String responseQueue;

  private synchronized void setupResponseHandler() {
    if (responseQueue == null) {
      String queueName = UUID.randomUUID().toString();
      subscribe(queueName, new StompMsgCallback() {
        public void onMessage(Map<String, String> headers, Buffer body) {
          String cid = headers.get(CORRELATION_ID_HEADER);
          if (cid == null) {
            //TODO better error reporting
            System.err.println("No correlation id");
          } else {
            StompMsgCallback cb = callbacks.remove(cid);
            if (cb == null) {
              System.err.println("No STOMP callback for correlation id");
            } else {
              cb.onMessage(headers, body);
            }
          }
        }
      });
      responseQueue = queueName;
    }
  }

  // Request-response pattern
  public Completion request(String dest, Map<String, String> headers, Buffer body, final StompMsgCallback responseCallback) {
    final Completion c = new Completion();
    if (responseQueue == null) setupResponseHandler();
    String cid = UUID.randomUUID().toString();
    headers.put(CORRELATION_ID_HEADER, cid);
    headers.put(REPLY_TO_HEADER, responseQueue);
    StompMsgCallback cb = new StompMsgCallback() {
      public void onMessage(Map<String, String> headers, Buffer body) {
        responseCallback.onMessage(headers, body);
        c.complete();
      }
    };
    callbacks.put(cid, cb);
    send(dest, headers, body);
    return c;
  }

  // Subscribe without receipt
  public synchronized void subscribe(String dest, StompMsgCallback messageCallback) {
    subscribe(dest, messageCallback, null);
  }

  // Subscribe with receipt
  public synchronized void subscribe(String dest, StompMsgCallback messageCallback, NoArgCallback completeCallback) {
    if (subscriptions.containsKey(dest)) {
      throw new IllegalArgumentException("Already subscribed to " + dest);
    }
    subscriptions.put(dest, messageCallback);
    Frame frame = Frame.subscribeFrame(dest);
    addReceipt(frame, completeCallback);
    write(frame);
  }

  // Unsubscribe without receipt
  public synchronized void unsubscribe(String dest) {
    unsubscribe(dest, null);
  }

  //Unsubscribe with receipt
  public synchronized void unsubscribe(String dest, NoArgCallback completeCallback) {
    subscriptions.remove(dest);
    Frame frame = Frame.unsubscribeFrame(dest);
    addReceipt(frame, completeCallback);
    write(frame);
  }

  public void write(Frame frame) {
    //Need to duplicate the buffer since frame can be written to multiple connections concurrently
    //which will change the internal Netty readerIndex
    socket.write(frame.toBuffer().duplicate());
  }

  protected void connect(String username, String password, final NoArgCallback connectCallback) {
    this.connectCallback = connectCallback;
    write(Frame.connectFrame(username, password));
  }

  private synchronized void handleMessage(Frame msg) {
    String dest = msg.headers.get("destination");
    StompMsgCallback sub = subscriptions.get(dest);
    sub.onMessage(msg.headers, msg.body);
  }

  private void addReceipt(Frame frame, NoArgCallback callback) {
    if (callback != null) {
      String receipt = UUID.randomUUID().toString();
      frame.headers.put("receipt", receipt);
      waitingReceipts.put(receipt, callback);
    }
  }

  protected void handleFrame(Frame frame) {
    if (!connected) {
      if (!"CONNECTED".equals(frame.command)) {
        //FIXME - proper error handling
        throw new IllegalStateException("Expected CONNECTED frame, got: " + frame.command);
      }
      connected = true;
      connectCallback.onEvent();
    } else if ("MESSAGE".equals(frame.command)) {
      handleMessage(frame);
    } else if ("RECEIPT".equals(frame.command)) {
      String receipt = frame.headers.get("receipt-id");
      NoArgCallback callback = waitingReceipts.get(receipt);
      callback.onEvent();
    } else if ("ERROR".equals(frame.command)) {
      if (errorCallback != null) {
        errorCallback.onEvent(frame);
      }
    }
  }
}
