package org.vertx.java.tests.globalhandlers;

import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestBase;
import vertx.tests.globalhandlers.HandlerServer;
import vertx.tests.globalhandlers.TestClient;
import vertx.tests.globalhandlers.UnregisteringServer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaGlobalHandlerTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    SharedData.getSet("handlerids").clear();
    startApp(AppType.JAVA, TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testHandlers() throws Exception {
    int instanceCount = 10;
    for (int i = 0; i < instanceCount; i++) {
      startApp(AppType.JAVA, HandlerServer.class.getName());
    }
    startTest(getMethodName(), false);
    for (int i = 0; i < instanceCount; i++) {
      waitTestComplete();
    }
  }

  @Test
  // Make sure unregistered handlers don't receive messages
  public void testUnregisteredHandlers() throws Exception {
    int instanceCount = 10;
    for (int i = 0; i < instanceCount; i++) {
      startApp(AppType.JAVA, UnregisteringServer.class.getName());
    }
    startTest(getMethodName());
  }

  @Test
  public void testUnregisterFromDifferentContext() throws Exception {
    int instanceCount = 10;
    for (int i = 0; i < instanceCount; i++) {
      startApp(AppType.JAVA, HandlerServer.class.getName());
    }
    startTest(getMethodName());
  }

  @Test
  public void testSendDataTypes() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testSendToHandlerNoContext() throws Exception {
    try {
      Vertx.instance.sendToHandler(1234, "hello");
      fail("Should throw Exception");
    } catch (IllegalStateException e) {
      //OK
    }
  }

  @Test
  public void testRegisterHandlerNoContext() throws Exception {
    try {
      Vertx.instance.registerHandler(new Handler<String>() {
        public void handle(String message) {
        }
      });
      fail("Should throw Exception");
    } catch (IllegalStateException e) {
      //OK
    }
  }


}
