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
    sendMessage(map);
  }

  public void appReady() {
    sendEvent(EventFields.APP_READY_EVENT);
  }

  public void appStopped() {
    sendEvent(EventFields.APP_STOPPED_EVENT);
  }

  public void testComplete(String testName) {
    Map<String, String> map = new HashMap<>();
    map.put(EventFields.TYPE_FIELD, EventFields.TEST_COMPLETE_EVENT);
    map.put(EventFields.TEST_COMPLETE_NAME_FIELD, testName);
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
    Writer result = new StringWriter();
    PrintWriter printWriter = new PrintWriter(result);
    t.printStackTrace(printWriter);
    map.put(EventFields.EXCEPTION_STACKTRACE_FIELD, result.toString());
    sendMessage(map);
  }

  public void trace(String message) {
    Map<String, String> map = new HashMap<>();
    map.put(EventFields.TYPE_FIELD, EventFields.TRACE_EVENT);
    map.put(EventFields.TRACE_MESSAGE_FIELD, message);
    sendMessage(map);
  }

  public void register(final String testName, final Handler<Void> handler) {
    EventBus.instance.registerHandler(TestBase.EVENTS_ADDRESS, new Handler<Message>() {
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
    });
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


}
