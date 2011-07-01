package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.FutureAction;
import org.nodex.core.NoArgCallback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.net.Socket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: tim
 * Date: 27/06/11
 * Time: 18:31
 */
public class Connection {

  private final Socket socket;
  private Callback<Frame> errorCallback;
  private NoArgCallback connectCallback;
  protected boolean connected;
  private Map<String, MessageCallback> subscriptions = new HashMap<String, MessageCallback>();
  private Map<String, NoArgCallback> waitingReceipts = new ConcurrentHashMap<String, NoArgCallback>();

  protected Connection(Socket socket) {
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
    send(dest, new HashMap<String, String>(4), body, false);
  }

  // Send without receipt
  public void send(String dest, Map<String, String> headers, Buffer body) {
    send(dest, headers, body, false);
  }

  // Send with receipt
  public FutureAction send(String dest, Buffer body, boolean receipt) {
    return send(dest, new HashMap<String, String>(4), body, receipt);
  }

  // Send with receipt
  public FutureAction send(String dest, Map<String, String> headers, Buffer body, boolean receipt) {
    Frame frame = new Frame("SEND", headers, body);
    frame.headers.put("destination", dest);
    FutureAction fa = receipt ? addReceipt(frame) : null;
    write(frame);
    return fa;
  }

  // Subscribe without receipt
  public synchronized void subscribe(String dest, MessageCallback messageCallback) {
    subscribe(dest, messageCallback, false);
  }

  // Subscribe with receipt
  public synchronized FutureAction subscribe(String dest, MessageCallback messageCallback, boolean receipt) {
    if (subscriptions.containsKey(dest)) {
      throw new IllegalArgumentException("Already subscribed to " + dest);
    }
    subscriptions.put(dest, messageCallback);
    Frame frame = Frame.subscribeFrame(dest);
    FutureAction fa = receipt ? addReceipt(frame) : null;
    write(frame);
    return fa;
  }

  // Unsubscribe without receipt
  public synchronized void unsubscribe(String dest) {
    unsubscribe(dest, false);
  }

  //Unsubscribe with receipt
  public synchronized FutureAction unsubscribe(String dest, boolean receipt) {
    subscriptions.remove(dest);
    Frame frame = Frame.unsubscribeFrame(dest);
    FutureAction fa = receipt ? addReceipt(frame) : null;
    write(frame);
    return fa;
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

  private FutureAction addReceipt(Frame frame) {
    FutureAction fa = new FutureAction();
    addReceipt(frame, fa);
    return fa;
  }

  private synchronized void handleMessage(Frame msg) {
    String dest = msg.headers.get("destination");
    MessageCallback sub = subscriptions.get(dest);
    sub.onMessage(msg.headers, msg.body);
  }

  private void addReceipt(Frame frame, NoArgCallback callback) {
    String receipt = UUID.randomUUID().toString();
    frame.headers.put("receipt", receipt);
    waitingReceipts.put(receipt, callback);
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
