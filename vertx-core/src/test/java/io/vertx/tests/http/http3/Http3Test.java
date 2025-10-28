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
  public void test100ContinueTimeout() {
  }

  @Ignore("Introduce stream cancellation")
  @Test
  public void testResetClientRequestAwaitingResponse() {
  }

  @Ignore("Implement compression")
  @Test
  public void testClientDecompressionError() {
  }

  @Ignore("Requires connection keep alive")
  @Override
  public void testMaxLifetime() {
  }

  @Ignore("Requires implementation of idle timeout")
  @Test
  public void testFollowRedirectPropagatesTimeout() {
  }

  @Ignore("Requires DNS integration")
  @Test
  public void testDnsClientSideLoadBalancingDisabled() {
  }

  @Ignore("Requires DNS integration")
  @Test
  public void testDnsClientSideLoadBalancingEnabled() {
  }

  @Ignore("Requires keep alive timeout")
  @Test
  public void testKeepAliveTimeout() {
  }

  @Ignore()
  @Test
  public void testListenInvalidPort() {
  }

  @Ignore()
  @Override
  public void testListenInvalidHost() {
  }

  @Ignore("Requires activity logging")
  @Test
  public void testClientLogging() {
  }

  @Ignore("Requires activity logging")
  @Test
  public void testServerLogging() {
  }

  @Ignore("Requires quic connect timeout")
  @Test
  public void testConnectTimeout() {
  }

  @Ignore("Does it make sense for HTTP/3 ?")
  @Test
  public void testCloseMulti() {
  }

  @Ignore("Is this test valid ?")
  @Test
  public void testResetClientRequestResponseInProgress() throws Exception {
  }

  @Ignore("Does not pass, but should")
  @Override
  public void testServerActualPortWhenZero() throws Exception {
  }

  @Ignore("Does not pass, but should")
  @Override
  public void testServerActualPortWhenZeroPassedInListen() {
  }

  @Ignore("Requires to implement keep alive timeout")
  @Test
  public void testPoolNotExpiring1() {
  }

  @Ignore("Requires to implement keep alive timeout")
  @Test
  public void testPoolNotExpiring2() {
  }

  @Ignore("Requires to implement client local address")
  @Override
  public void testClientLocalAddress() {
  }

  @Ignore("Missing feature")
  @Test
  public void testDisableIdleTimeoutInPool() {
  }

}
