package org.vertx.java.newtests;

import junit.framework.TestCase;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
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

  private VerticleManager verticleManager;
  private BlockingQueue<JsonObject> events = new LinkedBlockingQueue<>();
  private TestUtils tu = new TestUtils();
  private long contextID;
  private volatile Handler<Message<JsonObject>> handler;
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

    verticleManager = VerticleManager.instance;

    final CountDownLatch latch = new CountDownLatch(1);

    contextID = VertxInternal.instance.startOnEventLoop(new Runnable() {
      public void run() {

        if (EventBus.instance == null) {
          // Start non clustered event bus
          EventBus bus = new EventBus() {
          };
          EventBus.initialize(bus);
        }

        handler = new Handler<Message<JsonObject>>() {
          public void handle(Message<JsonObject> message) {
            try {

              String type = message.body.getString("type");

              //System.out.println("******************* Got message: " + type);

              switch (type) {
                case EventFields.TRACE_EVENT:
                  log.trace(message.body.getString(EventFields.TRACE_MESSAGE_FIELD));
                  break;
                case EventFields.EXCEPTION_EVENT:
                  failedAsserts.add(new AssertHolder(message.body.getString(EventFields.EXCEPTION_MESSAGE_FIELD),
                      message.body.getString(EventFields.EXCEPTION_STACKTRACE_FIELD)));
                  break;
                case EventFields.ASSERT_EVENT:
                  boolean passed = EventFields.ASSERT_RESULT_VALUE_PASS.equals(message.body.getString(EventFields.ASSERT_RESULT_FIELD));
                  if (passed) {
                  } else {
                    failedAsserts.add(new AssertHolder(message.body.getString(EventFields.ASSERT_MESSAGE_FIELD),
                        message.body.getString(EventFields.ASSERT_STACKTRACE_FIELD)));
                  }
                  break;
                case EventFields.START_TEST_EVENT:
                  //Ignore
                  break;
                case EventFields.APP_STOPPED_EVENT:
                  events.add(message.body);
                  break;
                case EventFields.APP_READY_EVENT:
                  events.add(message.body);
                  break;
                case EventFields.TEST_COMPLETE_EVENT:
                  events.add(message.body);
                  break;
                default:
                  throw new IllegalArgumentException("Invalid type: " + type);
              }

            } catch (Exception e) {
              log.error("Failed to parse JSON", e);
            }
          }
        };

        EventBus.instance.registerHandler(EVENTS_ADDRESS, handler);

        latch.countDown();
      }
    });
    latch.await(10, TimeUnit.SECONDS);
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
            EventBus.instance.unregisterHandler(EVENTS_ADDRESS, handler);
            latch.countDown();
          }
        });
        latch.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        e.printStackTrace();
        throw e;
      }
    }
  }

  protected String startApp(String main) throws Exception {
    return startApp(false, main, true);
  }

  protected String startApp(String main, JsonObject config) throws Exception {
    return startApp(false, main, config, 1, true);
  }

  protected String startApp(String main, boolean await) throws Exception {
    return startApp(false, main, null, 1, await);
  }

  protected String startApp(String main, int instances) throws Exception {
    return startApp(false, main, null, instances, true);
  }

  protected String startApp(boolean worker, String main) throws Exception {
    return startApp(worker, main, true);
  }

  protected String startApp(boolean worker, String main, JsonObject config) throws Exception {
    return startApp(worker, main, config, 1, true);
  }

  protected String startApp(boolean worker, String main, boolean await) throws Exception {
    return startApp(worker, main, null, 1, await);
  }

  protected String startApp(boolean worker, String main, int instances) throws Exception {
    return startApp(worker, main, null, instances, true);
  }

  protected String startApp(boolean worker, String main, JsonObject config, int instances, boolean await) throws Exception {
    URL url;
    if (main.endsWith(".js") || main.endsWith(".rb") || main.endsWith(".groovy")) {
      url = getClass().getClassLoader().getResource(main);
    } else {
      String classDir = main.replace('.', '/') + ".class";
      url = getClass().getClassLoader().getResource(classDir);
      String surl = url.toString();
      String surlroot = surl.substring(0, surl.length() - classDir.length());
      url = new URL(surlroot);
    }

    if (url == null) {
      throw new IllegalArgumentException("Cannot find verticle: " + main);
    }

    final CountDownLatch doneLatch = new CountDownLatch(1);

    Handler<Void> doneHandler = new SimpleHandler() {
      public void handle() {
        doneLatch.countDown();
      }
    };

    String deploymentName = verticleManager.deploy(worker, null, main, config, new URL[] {url}, instances, doneHandler);

    startedApps.add(deploymentName);


    if (!doneLatch.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Timedout waiting for apps to start");
    }

    if (await) {
      for (int i = 0; i < instances; i++) {
        waitAppReady();
      }
    }

    return deploymentName;
  }

  protected void stopApp(String appName) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    int instances = verticleManager.listInstances().get(appName);
    verticleManager.undeploy(appName, new SimpleHandler() {
      public void handle() {
        latch.countDown();
      }
    });
    if (!latch.await(10, TimeUnit.SECONDS)) {
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
    log.info("Starting test: " + testName);
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
