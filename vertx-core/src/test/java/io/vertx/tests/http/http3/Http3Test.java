package io.vertx.tests.http.http3;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.QuicClientAddressValidation;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import io.vertx.tests.http.HttpTest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(LinuxOrOsx.class)
public class Http3Test extends HttpTest {

  @Override
  protected HttpServer createHttpServer(HttpServerOptions options) {
    Http3ServerOptions serverOptions = new Http3ServerOptions();
    serverOptions.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    serverOptions.setHandle100ContinueAutomatically(options.isHandle100ContinueAutomatically());
//    serverOptions.setClientAddressValidation(QuicClientAddressValidation.NONE);
//    serverOptions.setKeyLogFile("/Users/julien/keylogfile.txt");
    return vertx.createHttpServer(serverOptions);
  }

  @Override
  protected HttpClientAgent createHttpClient(HttpClientOptions options) {
    Http3ClientOptions clientOptions = new Http3ClientOptions();
    clientOptions.getSslOptions().setTrustOptions(Trust.SERVER_JKS.get());
    clientOptions.getSslOptions().setHostnameVerificationAlgorithm("");
    return vertx.createHttpClient(clientOptions);
  }

  @Override
  protected HttpClientAgent createHttpClient(HttpClientOptions options, PoolOptions pool) {
    Http3ClientOptions clientOptions = new Http3ClientOptions();
    clientOptions.getSslOptions().setTrustOptions(Trust.SERVER_JKS.get());
    clientOptions.getSslOptions().setHostnameVerificationAlgorithm("");
    return vertx.httpClientBuilder().with(clientOptions).with(pool).build();
  }

  @Override
  protected HttpClientBuilder httpClientBuilder(Vertx vertx, HttpClientOptions options) {
    Http3ClientOptions clientOptions = new Http3ClientOptions();
    clientOptions.getSslOptions().setTrustOptions(Trust.SERVER_JKS.get());
    clientOptions.getSslOptions().setHostnameVerificationAlgorithm("");
    return vertx.httpClientBuilder().with(clientOptions);
  }

  @Override
  protected HttpClientBuilder httpClientBuilder(HttpClientOptions options) {
    return super.httpClientBuilder(options);
  }

  @Ignore
  @Test
  public void test100ContinueTimeout() throws Exception {
  }

  @Ignore("Introduce stream cancellation")
  @Test
  public void testResetClientRequestAwaitingResponse() throws Exception {
  }

  @Ignore("Implement compression")
  @Test
  public void testClientDecompressionError() throws Exception {
  }

  @Ignore("Requires connection keep alive")
  @Override
  public void testMaxLifetime() throws Exception {
  }

  @Ignore("Requires implementation of idle timeout")
  @Test
  public void testFollowRedirectPropagatesTimeout() throws Exception {
  }

  @Ignore("Requires DNS integration")
  @Test
  public void testDnsClientSideLoadBalancingDisabled() throws Exception {
  }

  @Ignore("Requires DNS integration")
  @Test
  public void testDnsClientSideLoadBalancingEnabled() throws Exception {
  }

  @Ignore("Requires keep alive timeout")
  @Test
  public void testKeepAliveTimeout() throws Exception {
  }

  @Ignore()
  @Test
  public void testListenInvalidPort() throws Exception {
  }

  @Ignore()
  @Override
  public void testListenInvalidHost() {
  }

  @Ignore("Requires activity logging")
  @Test
  public void testClientLogging() throws Exception {
  }

  @Ignore("Requires activity logging")
  @Test
  public void testServerLogging() throws Exception {
  }

  @Ignore("Requires quic connect timeout")
  @Test
  public void testConnectTimeout() {
  }

  @Ignore("Does it make sense for HTTP/3 ?")
  @Test
  public void testCloseMulti() throws Exception {
  }

  @Ignore("Is this test valid ?")
  @Test
  public void testResetClientRequestResponseInProgress() throws Exception {
  }

  @Ignore("Does not pass, but should")
  @Override
  public void testServerActualPortWhenZero() throws Exception {
    super.testServerActualPortWhenZero();
  }

  @Ignore("Does not pass, but should")
  @Override
  public void testServerActualPortWhenZeroPassedInListen() throws Exception {
    super.testServerActualPortWhenZeroPassedInListen();
  }
}
