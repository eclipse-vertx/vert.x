package org.vertx.java.framework;

import org.junit.Assert;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.VerticleManager;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxTestFixture {
  private static final Logger log = LoggerFactory.getLogger(VertxTestFixture.class);
  static final int DEFAULT_TIMEOUT = Integer.parseInt(System.getProperty("vertx.test.timeout", "30"));

  public static final String EVENTS_ADDRESS = "__test_events";

  protected static VertxInternal vertx = new DefaultVertx();
  private static VerticleManager verticleManager = new VerticleManager(vertx);
  private BlockingQueue<JsonObject> events = new LinkedBlockingQueue<>();
  private volatile Handler<Message<JsonObject>> handler;
  private List<AssertHolder> failedAsserts = new ArrayList<>();
  private List<String> startedApps = new CopyOnWriteArrayList<>();
  private TestUtils tu = new TestUtils(vertx);


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
      Assert.fail(holder.message + "\n" + holder.stackTrace);
    }
    failedAsserts.clear();
  }

  protected void setUp() {
    handler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        try {

          String type = message.body.getString("type");

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

    vertx.eventBus().registerHandler(EVENTS_ADDRESS, handler);
  }

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
        vertx.eventBus().unregisterHandler(EVENTS_ADDRESS, handler);
      } catch (Exception e) {
        e.printStackTrace();
        throw e;
      }
    }
  }

  public String startApp(boolean worker, String main, JsonObject config, int instances, boolean await) throws Exception {
    if(Runtime.getRuntime().availableProcessors() < 2) {
      log.error("*** The test framework requires at least 2 processors ***");
      Assert.fail("The test framework requires at least 2 processors");
    }
    URL url;
    if (main.endsWith(".js") || main.endsWith(".rb") || main.endsWith(".groovy") || main.endsWith(".py")) {
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
    final AtomicReference<String> res = new AtomicReference<>(null);

    Handler<String> doneHandler = new Handler<String>() {
      public void handle(String deploymentName) {
        startedApps.add(deploymentName);
        res.set(deploymentName);
        doneLatch.countDown();
      }
    };

    verticleManager.deploy(worker, main, config, new URL[] {url}, instances, null, doneHandler);

    if (!doneLatch.await(30, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Timedout waiting for apps to start");
    }

    if (await) {
      for (int i = 0; i < instances; i++) {
        waitAppReady();
      }
    }

    return res.get();
  }

  public String startMod(String modName, JsonObject config, int instances, boolean await) throws Exception {

    final CountDownLatch doneLatch = new CountDownLatch(1);
    final AtomicReference<String> res = new AtomicReference<>(null);

    Handler<String> doneHandler = new Handler<String>() {
      public void handle(String deploymentName) {
        startedApps.add(deploymentName);
        res.set(deploymentName);
        doneLatch.countDown();
      }
    };

    verticleManager.deployMod(modName, config, instances, null, doneHandler);

    if (!doneLatch.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Timedout waiting for apps to start");
    }

    if (await) {
      for (int i = 0; i < instances; i++) {
        waitAppReady();
      }
    }

    return res.get();
  }

  protected void stopApp(String appName) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    int instances = verticleManager.listInstances().get(appName);
    verticleManager.undeploy(appName, new SimpleHandler() {
      public void handle() {
        latch.countDown();
      }
    });
    if (!latch.await(30, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Timedout waiting for app to stop");
    }
    for (int i = 0; i < instances; i++) {
      waitAppStopped();
    }
    startedApps.remove(appName);
  }

  protected void startTest(String testName, boolean wait) {
    log.info("Starting test: " + testName);
    tu.startTest(testName);
    if (wait) {
      waitTestComplete();
    }
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

  protected void waitTestComplete() {
    waitTestComplete(VertxTestFixture.DEFAULT_TIMEOUT);
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
}
