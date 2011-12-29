package org.vertx.java.newtests;

import junit.framework.TestCase;
import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.app.AppManager;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.ServerID;

import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestBase extends TestCase {

  private static final Logger log = Logger.getLogger(TestBase.class);

  private AppManager appManager;
  private BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
  private ObjectMapper mapper = new ObjectMapper();
  private TestUtils tu = new TestUtils();

  private static final int DEFAULT_TIMEOUT = 5;

  public static final String EVENTS_ADDRESS = "__test_events";


  @Override
  protected void setUp() throws Exception {

    appManager = AppManager.instance;

    //Ugly ugly

    final CountDownLatch latch = new CountDownLatch(1);
    VertxInternal.instance.go(new Runnable() {
      public void run() {
        // Start non clustered event bus
        ServerID defaultServerID = new ServerID(2550, "localhost");
        EventBus bus = new EventBus(defaultServerID) {};
        EventBus.initialize(bus);

        EventBus.instance.registerHandler(EVENTS_ADDRESS, new Handler<Message>() {
          public void handle(Message message)  {
            try {

              Map<String, Object> map = mapper.readValue(message.body.toString(), Map.class);

              String type = (String)map.get("type");
              switch (type) {
                case EventFields.TRACE_EVENT:
                  log.trace(map.get(EventFields.TRACE_MESSAGE_FIELD));
                  break;
                case EventFields.EXCEPTION_EVENT:
                  log.error(map.get(EventFields.EXCEPTION_MESSAGE_FIELD));
                  log.error(map.get(EventFields.EXCEPTION_STACKTRACE_FIELD));
                  break;
                case EventFields.ASSERT_EVENT:
                  boolean passed = EventFields.ASSERT_RESULT_VALUE_PASS.equals(map.get(EventFields.ASSERT_RESULT_FIELD));
                  if (passed) {
                    log.info("assert passed");
                  } else {
                    log.info("remote assert failed");
                    TestBase.assertEquals((String)map.get(EventFields.ASSERT_MESSAGE_FIELD), true, false);
                  }
                  break;
                case EventFields.START_TEST_EVENT:
                  //Ignore
                  break;
                case EventFields.APP_READY_EVENT:
                case EventFields.APP_STOPPED_EVENT:
                case EventFields.TEST_COMPLETE_EVENT:
                  events.add(map);
                  break;
                default:
                  throw new IllegalArgumentException("Invalid type: " + type);
              }

            } catch (Exception e) {
              log.error("Failed to parse JSON", e);
            }
          }
        });

        latch.countDown();
      }
    });
    latch.await(5, TimeUnit.SECONDS);
  }

  @Override
  protected void tearDown() throws Exception {

  }

  protected String startApp(AppType type, String main) throws Exception {
    String appName = startApp(type, main, 1);
    waitAppReady();
    return appName;
  }

  protected String startApp(AppType type, String main, int instances) throws Exception {

    String appName = UUID.randomUUID().toString();

    String classFile = main.replace('.', '/') + ".class";
    URL url = getClass().getClassLoader().getResource(classFile);
    String surl = url.toString();
    String surlroot = surl.substring(0, surl.length() - classFile.length());
    url = new URL(surlroot);

    appManager.deploy(type, appName, main, new URL[] { url }, instances);

    return appName;
  }

  protected void stopApp(String appName) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    appManager.undeploy(appName, new SimpleHandler() {
      public void handle() {
        latch.countDown();
      }
    });
    latch.await(5, TimeUnit.SECONDS);
    waitAppStopped();
  }

  protected void startTest(String testName) {
    tu.startTest(testName);
  }

  protected void waitAppReady() {
    waitAppReady(DEFAULT_TIMEOUT);
  }

  protected void waitAppStopped() {
    waitAppStopped(DEFAULT_TIMEOUT);
  }

  protected void waitTestComplete() {
    waitTestComplete(DEFAULT_TIMEOUT);
  }

  protected void waitAppReady(int timeout) {
    waitEvent(timeout, EventFields.APP_READY_EVENT);
  }

  protected void waitAppStopped(int timeout) {
    waitEvent(timeout, EventFields.APP_STOPPED_EVENT);
  }

  protected void waitTestComplete(int timeout) {
    waitEvent(timeout, EventFields.TEST_COMPLETE_EVENT);
  }

  protected void waitEvent(int timeout, String eventName) {
    Map<String, Object> message;
    while (true) {
      try {
        message = events.poll(timeout, TimeUnit.SECONDS);
        break;
      } catch (InterruptedException cont) {
      }
    }

    if (!eventName.equals(message.get("type"))) {
      throw new IllegalStateException("Expected event: " + eventName + " got: " + message.get(EventFields.TYPE_FIELD));
    }
  }


}
