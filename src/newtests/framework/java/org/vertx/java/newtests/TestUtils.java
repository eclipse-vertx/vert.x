package org.vertx.java.newtests;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestUtils {

  private static final Logger log = Logger.getLogger(TestUtils.class);

  private ObjectMapper mapper = new ObjectMapper();

  public void azzert(boolean result) {
    azzert(result, null);
  }

  public void azzert(boolean result, String message) {
    Map<String, String> map = new HashMap<>();
    map.put(EventFields.TYPE_FIELD, EventFields.ASSERT_EVENT);
    map.put(EventFields.ASSERT_RESULT_FIELD, result ? EventFields.ASSERT_RESULT_VALUE_PASS : EventFields.ASSERT_RESULT_VALUE_FAIL);
    if (message != null) {
      map.put(EventFields.ASSERT_MESSAGE_FIELD, message);
    }
    if (!result) {
      map.put(EventFields.ASSERT_STACKTRACE_FIELD, getStackTrace(new Exception()));
    }
    sendMessage(map);
  }

  public void appReady() {
    sendEvent(EventFields.APP_READY_EVENT);
  }

  public void appStopped() {
    sendEvent(EventFields.APP_STOPPED_EVENT);
  }

  public void testComplete() {
    Map<String, String> map = new HashMap<>();
    map.put(EventFields.TYPE_FIELD, EventFields.TEST_COMPLETE_EVENT);
    map.put(EventFields.TEST_COMPLETE_NAME_FIELD, "unused");
    sendMessage(map);
  }

  public void startTest(String testName) {
    Map<String, String> map = new HashMap<>();
    map.put(EventFields.TYPE_FIELD, EventFields.START_TEST_EVENT);
    map.put(EventFields.START_TEST_NAME_FIELD, testName);
    sendMessage(map);
  }

  public void exception(Throwable t, String message) {
    Map<String, String> map = new HashMap<>();
    map.put(EventFields.TYPE_FIELD, EventFields.EXCEPTION_EVENT);
    map.put(EventFields.EXCEPTION_MESSAGE_FIELD, message);
    map.put(EventFields.EXCEPTION_STACKTRACE_FIELD, getStackTrace(t));
    sendMessage(map);
  }

  public void trace(String message) {
    Map<String, String> map = new HashMap<>();
    map.put(EventFields.TYPE_FIELD, EventFields.TRACE_EVENT);
    map.put(EventFields.TRACE_MESSAGE_FIELD, message);
    sendMessage(map);
  }

  private Map<String, Handler<Message>> handlers = new HashMap<>();

  public void register(final String testName, final Handler<Void> handler) {
    Handler<Message> h = new Handler<Message>() {
      public void handle(Message msg) {
        try {
          Map<String, Object> map = mapper.readValue(msg.body.toString(), Map.class);
          if (EventFields.START_TEST_EVENT.equals(map.get(EventFields.TYPE_FIELD)) && testName.equals(map.get(EventFields.START_TEST_NAME_FIELD))) {
            handler.handle(null);
          }
        } catch (Exception e) {
          log.error("Failed to parse JSON", e);
        }
      }
    };
    EventBus.instance.registerHandler(TestBase.EVENTS_ADDRESS, h);
    handlers.put(testName, h);
  }

  public void unregisterAll() {
    for (Handler<Message> handler: handlers.values()) {
      EventBus.instance.unregisterHandler(TestBase.EVENTS_ADDRESS, handler);
    }
    handlers.clear();
  }

  private void sendMessage(Map<String, String> msg) {
    try {
      String json = mapper.writeValueAsString(msg);
      EventBus.instance.send(new Message(TestBase.EVENTS_ADDRESS, Buffer.create(json)));
    } catch (Exception e) {
      log.error("Failed to send message", e);
    }
  }

  private void sendEvent(String eventName) {
    Map<String, String> map = new HashMap<>();
    map.put(EventFields.TYPE_FIELD, eventName);
    sendMessage(map);
  }

  private String getStackTrace(Throwable t) {
    Writer result = new StringWriter();
    PrintWriter printWriter = new PrintWriter(result);
    t.printStackTrace(printWriter);
    return result.toString();
  }

  public static Buffer generateRandomBuffer(int length) {
    return generateRandomBuffer(length, false, (byte) 0);
  }

  public static byte[] generateRandomByteArray(int length) {
    return generateRandomByteArray(length, false, (byte) 0);
  }

  public static byte[] generateRandomByteArray(int length, boolean avoid, byte avoidByte) {
    byte[] line = new byte[length];
    for (int i = 0; i < length; i++) {
      //Choose a random byte - if we're generating delimited lines then make sure we don't
      //choose first byte of delim
      byte rand;
      do {
        rand = (byte) ((int) (Math.random() * 255) - 128);
      } while (avoid && rand == avoidByte);

      line[i] = rand;
    }
    return line;
  }

  public static Buffer generateRandomBuffer(int length, boolean avoid, byte avoidByte) {
    byte[] line = generateRandomByteArray(length, avoid, avoidByte);
    return Buffer.create(line);
  }

  public static String randomUnicodeString(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char c;
      do {
        c = (char) (0xFFFF * Math.random());
      } while ((c >= 0xFFFE && c <= 0xFFFF) || (c >= 0xD800 && c <= 0xDFFF)); //Illegal chars
      builder.append(c);
    }
    return builder.toString();
  }

  public static boolean buffersEqual(Buffer b1, Buffer b2) {
    if (b1.length() != b2.length()) return false;
    for (int i = 0; i < b1.length(); i++) {
      if (b1.getByte(i) != b2.getByte(i)) return false;
    }
    return true;
  }

  public static boolean byteArraysEqual(byte[] b1, byte[] b2) {
    if (b1.length != b2.length) return false;
    for (int i = 0; i < b1.length; i++) {
      if (b1[i] != b2[i]) return false;
    }
    return true;
  }

}
