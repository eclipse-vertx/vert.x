package org.vertx.java.newtests;

import junit.framework.TestCase;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.app.AppManager;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonMessage;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
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
  private static final int DEFAULT_TIMEOUT = 30;

  public static final String EVENTS_ADDRESS = "__test_events";

  private AppManager appManager;
  private BlockingQueue<JsonObject> events = new LinkedBlockingQueue<>();
  private ObjectMapper mapper = new ObjectMapper();
  private TestUtils tu = new TestUtils();
  private long contextID;
  private volatile Handler<JsonMessage> handler;
  private List<AssertHolder> failedAsserts = new ArrayList<>();
  private List<String> startedApps = new ArrayList<>();

  private class AssertHolder {
    final String message;
    final String stackTrace;

    private AssertHolder(String message, String stackTrace) {
      this.message = message;
      this.stackTrace = stackTrace;
    }
  }

  private void throwAsserts() {
    for (AssertHolder holder: failedAsserts) {
      fail(holder.message + "\n" + holder.stackTrace);
    }
    failedAsserts.clear();
  }

  @Override
  protected void setUp() throws Exception {

    appManager = AppManager.instance;

    final CountDownLatch latch = new CountDownLatch(1);

    contextID = VertxInternal.instance.startOnEventLoop(new Runnable() {
      public void run() {

        if (EventBus.instance == null) {
          // Start non clustered event bus
          EventBus bus = new EventBus() {
          };
          EventBus.initialize(bus);
        }

        handler = new Handler<JsonMessage>() {
          public void handle(JsonMessage message) {
            try {

              String type = message.jsonObject.getString("type");

              //log.info("******************* Got message: " + type);

              switch (type) {
                case EventFields.TRACE_EVENT:
                  log.trace(message.jsonObject.getString(EventFields.TRACE_MESSAGE_FIELD));
                  break;
                case EventFields.EXCEPTION_EVENT:
                  failedAsserts.add(new AssertHolder(message.jsonObject.getString(EventFields.EXCEPTION_MESSAGE_FIELD),
                      message.jsonObject.getString(EventFields.EXCEPTION_STACKTRACE_FIELD)));
                  break;
                case EventFields.ASSERT_EVENT:
                  boolean passed = EventFields.ASSERT_RESULT_VALUE_PASS.equals(message.jsonObject.getString(EventFields.ASSERT_RESULT_FIELD));
                  if (passed) {
                  } else {
                    failedAsserts.add(new AssertHolder(message.jsonObject.getString(EventFields.ASSERT_MESSAGE_FIELD),
                        message.jsonObject.getString(EventFields.ASSERT_STACKTRACE_FIELD)));
                  }
                  break;
                case EventFields.START_TEST_EVENT:
                  //Ignore
                  break;
                case EventFields.APP_STOPPED_EVENT:
                  events.add(message.jsonObject);
                  break;
                case EventFields.APP_READY_EVENT:
                  events.add(message.jsonObject);
                  break;
                case EventFields.TEST_COMPLETE_EVENT:
                  events.add(message.jsonObject);
                  break;
                default:
                  throw new IllegalArgumentException("Invalid type: " + type);
              }

            } catch (Exception e) {
              log.error("Failed to parse JSON", e);
            }
          }
        };

        EventBus.instance.registerJsonHandler(EVENTS_ADDRESS, handler);

        latch.countDown();
      }
    });
    latch.await(5, TimeUnit.SECONDS);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      throwAsserts();
    } finally {
      try {
        List<String> apps = new ArrayList<>(startedApps);
        for (String appName: apps) {
          stopApp(appName);
        }
        events.clear();
        final CountDownLatch latch = new CountDownLatch(1);
        VertxInternal.instance.executeOnContext(contextID, new Runnable() {
          public void run() {
            VertxInternal.instance.setContextID(contextID);
            EventBus.instance.unregisterJsonHandler(EVENTS_ADDRESS, handler);
            latch.countDown();
          }
        });
        latch.await(5, TimeUnit.SECONDS);
      } catch (Exception e) {
        e.printStackTrace();
        throw e;
      }
    }
  }

  protected String startApp(AppType type, String main) throws Exception {
    return startApp(false, type, main, true);
  }

  protected String startApp(AppType type, String main, boolean await) throws Exception {
    return startApp(false, type, main, 1, await);
  }

  protected String startApp(AppType type, String main, int instances) throws Exception {
    return startApp(false, type, main, instances, true);
  }

  protected String startApp(boolean worker, AppType type, String main) throws Exception {
    return startApp(worker, type, main, true);
  }

  protected String startApp(boolean worker, AppType type, String main, boolean await) throws Exception {
    return startApp(worker, type, main, 1, await);
  }

  protected String startApp(boolean worker, AppType type, String main, int instances) throws Exception {
    return startApp(worker, type, main, instances, true);
  }

  protected String startApp(boolean worker, AppType type, String main, int instances, boolean await) throws Exception {
    String appName = UUID.randomUUID().toString();

    URL url = null;
    if (type == AppType.JAVA) {
      String classDir = main.replace('.', '/') + ".class";
      url = getClass().getClassLoader().getResource(classDir);
      String surl = url.toString();
      String surlroot = surl.substring(0, surl.length() - classDir.length());
      url = new URL(surlroot);
    } else if (type == AppType.JS) {
      url = getClass().getClassLoader().getResource(main);
    } else if (type == AppType.RUBY) {
      url = getClass().getClassLoader().getResource(main);
    } else if (type == AppType.GROOVY) {
      url = getClass().getClassLoader().getResource(main);
    }

    if (url == null) {
      throw new IllegalArgumentException("Can't find main: " + main);
    }

    final CountDownLatch doneLatch = new CountDownLatch(1);

    Handler<Void> doneHandler = new SimpleHandler() {
      public void handle() {
        doneLatch.countDown();
      }
    };

    appManager.deploy(worker, type, appName, main, new URL[] { url }, instances, doneHandler);

    startedApps.add(appName);

    if (!doneLatch.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Timedout waiting for apps to start");
    }

    if (await) {
      for (int i = 0; i < instances; i++) {
        waitAppReady();
      }
    }

    return appName;
  }

  protected void stopApp(String appName) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    int instances = appManager.listInstances().get(appName);
    appManager.undeploy(appName, new SimpleHandler() {
      public void handle() {
        latch.countDown();
      }
    });
    if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timedout waiting for app to stop");
    }
    for (int i = 0; i < instances; i++) {
      waitAppStopped();
    }
    startedApps.remove(appName);
  }

  protected void startTest(String testName) {
    startTest(testName, true);
  }

  protected void startTest(String testName, boolean wait) {
    tu.startTest(testName);
    if (wait) {
      waitTestComplete();
    }
  }

  protected String getMethodName() {
    return Thread.currentThread().getStackTrace()[2].getMethodName();
  }

  protected void waitAppReady() {
    waitAppReady(DEFAULT_TIMEOUT);
  }

  protected void waitAppStopped() {
    waitAppStopped(DEFAULT_TIMEOUT);
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

  protected void waitEvent(String eventName) {
    waitEvent(5, eventName);
  }

  protected void waitEvent(int timeout, String eventName) {

    JsonObject message;
    while (true) {
      try {
        message = events.poll(timeout, TimeUnit.SECONDS);
        break;
      } catch (InterruptedException cont) {
      }
    }

    if (message == null) {
      throw new IllegalStateException("Timed out waiting for event");
    }

    if (!eventName.equals(message.getString("type"))) {
      throw new IllegalStateException("Expected event: " + eventName + " got: " + message.getString(EventFields.TYPE_FIELD));
    }
  }

  protected void waitTestComplete() {
    waitTestComplete(DEFAULT_TIMEOUT);
  }

  @Test
  protected void runTestInLoop(String testName, int iters) throws Exception {
    Method meth = getClass().getMethod(testName, (Class<?>[])null);
    for (int i = 0; i < iters; i++) {
      log.info("****************************** ITER " + i);
      meth.invoke(this);
      tearDown();
      if (i != iters - 1) {
        setUp();
      }
    }
  }


}
