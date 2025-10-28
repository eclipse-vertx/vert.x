package io.vertx.tests.http.http3;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.QuicClientAddressValidation;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import io.vertx.tests.http.HttpTest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(LinuxOrOsx.class)
public class Http3Test extends HttpTest {

  public Http3Test() {
    super(Http3Config.INSTANCE);
  }

  @Ignore
  @Test
  @Override
  public void test100ContinueTimeout() {
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

  @Ignore("Requires connection keep alive")
  @Test
  @Override
  public void testMaxLifetime() {
  }

  @Ignore("Requires implementation of idle timeout")
  @Test
  @Override
  public void testFollowRedirectPropagatesTimeout() {
  }

  @Ignore("Requires DNS integration")
  @Test
  @Override
  public void testDnsClientSideLoadBalancingDisabled() {
  }

  @Ignore("Requires DNS integration")
  @Test
  @Override
  public void testDnsClientSideLoadBalancingEnabled() {
  }

  @Ignore("Requires keep alive timeout")
  @Test
  @Override
  public void testKeepAliveTimeout() {
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

  @Ignore("Requires activity logging")
  @Test
  @Override
  public void testClientLogging() {
  }

  @Ignore("Requires activity logging")
  @Test
  @Override
  public void testServerLogging() {
  }

  @Ignore("Requires quic connect timeout")
  @Test
  @Override
  public void testConnectTimeout() {
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

  @Ignore("Requires to implement keep alive timeout")
  @Test
  @Override
  public void testPoolNotExpiring1() {
  }

  @Ignore("Requires to implement keep alive timeout")
  @Test
  @Override
  public void testPoolNotExpiring2() {
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
