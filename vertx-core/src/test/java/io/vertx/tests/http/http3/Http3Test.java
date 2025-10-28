package io.vertx.tests.http.http3;

import io.vertx.test.core.LinuxOrOsx;
import io.vertx.tests.http.HttpTest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(LinuxOrOsx.class)
public class Http3Test extends HttpTest {

  public Http3Test() {
    super(Http3Config.INSTANCE);
  }

  @Ignore("Introduce stream cancellation")
  @Test
  @Override
  public void testResetClientRequestAwaitingResponse() {
  }

  @Ignore("Implement compression")
  @Test
  @Override
  public void testClientDecompressionError() {
  }

  @Ignore("Requires fixe of stream cancellation")
  @Test
  @Override
  public void testFollowRedirectPropagatesTimeout() {
  }

  @Ignore()
  @Test
  @Override
  public void testListenInvalidPort() {
  }

  @Ignore()
  @Test
  @Override
  public void testListenInvalidHost() {
  }

  @Ignore("Requires an HTTP/3 frame logger")
  @Test
  @Override
  public void testClientLogging() {
  }

  @Ignore("Requires an HTTP/3 frame logger")
  @Test
  @Override
  public void testServerLogging() {
  }

  @Ignore("Does it make sense for HTTP/3 ?")
  @Test
  @Override
  public void testCloseMulti() {
  }

  @Ignore("Is this test valid ?")
  @Test
  @Override
  public void testResetClientRequestResponseInProgress() throws Exception {
  }

  @Ignore("Does not pass, but should")
  @Test
  @Override
  public void testServerActualPortWhenZero() throws Exception {
  }

  @Ignore("Does not pass, but should")
  @Test
  @Override
  public void testServerActualPortWhenZeroPassedInListen() {
  }

  @Ignore("Requires to implement client local address")
  @Test
  @Override
  public void testClientLocalAddress() {
  }

  @Ignore("Missing feature")
  @Test
  @Override
  public void testDisableIdleTimeoutInPool() {
  }

  @Ignore("Cannot pass because stream channel does not detect the write failure")
  @Test
  @Override
  public void testCancelPartialClientRequest() throws Exception {
  }

  @Ignore("Cannot pass because stream channel does not detect the write failure")
  @Test
  @Override
  public void testCancelPartialServerResponse() throws Exception {
  }
}
