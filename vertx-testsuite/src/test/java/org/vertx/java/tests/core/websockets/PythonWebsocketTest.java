package org.vertx.java.tests.core.websockets;

import org.vertx.java.framework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonWebsocketTest extends TestBase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      startApp("core/websocket/test_client.py");
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    public void test_echo_binary() {
      startTest(getMethodName());
    }

    public void test_echo_text() {
      startTest(getMethodName());
    }

    public void test_write_from_connect_handler() {
      startTest(getMethodName());
    }

    public void test_close() {
      startTest(getMethodName());
    }

    public void test_close_from_connect() {
      startTest(getMethodName());
    }

}
