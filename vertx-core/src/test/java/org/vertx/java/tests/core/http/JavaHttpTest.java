/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.tests.core.http;

import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.http.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaHttpTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(HttpTestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testClientDefaults() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testClientAttributes() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testServerDefaults() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testServerAttributes() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testServerChaining() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testServerChainingSendFile() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testClientChaining() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testLowerCaseHeaders() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testNoContext() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);

    final Vertx vertx = Vertx.newVertx();

    final HttpServer server = vertx.createHttpServer();
    server.requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.end();
      }
    });
    server.listen(8080);

    final HttpClient client = vertx.createHttpClient().setPort(8080);
    client.getNow("some-uri", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        server.close(new SimpleHandler() {
          public void handle() {
            client.close();
            latch.countDown();
          }
        });
      }
    });

    assertTrue(latch.await(5, TimeUnit.SECONDS));
    vertx.stop();
  }

  public void testSimpleGET() {
    startTest(getMethodName());
  }

  public void testSimplePUT() {
    startTest(getMethodName());
  }

  public void testSimplePOST() {
    startTest(getMethodName());
  }

  public void testSimpleDELETE() {
    startTest(getMethodName());
  }

  public void testSimpleHEAD() {
    startTest(getMethodName());
  }

  public void testSimpleTRACE() {
    startTest(getMethodName());
  }

  public void testSimpleCONNECT() {
    startTest(getMethodName());
  }

  public void testSimpleOPTIONS() {
    startTest(getMethodName());
  }

  public void testSimplePATCH() {
    startTest(getMethodName());
  }

  public void testSimpleGETNonSpecific() {
    startTest(getMethodName());
  }

  public void testSimplePUTNonSpecific() {
    startTest(getMethodName());
  }

  public void testSimplePOSTNonSpecific() {
    startTest(getMethodName());
  }

  public void testSimpleDELETENonSpecific() {
    startTest(getMethodName());
  }

  public void testSimpleHEADNonSpecific() {
    startTest(getMethodName());
  }

  public void testSimpleTRACENonSpecific() {
    startTest(getMethodName());
  }

  public void testSimpleCONNECTNonSpecific() {
    startTest(getMethodName());
  }

  public void testSimpleOPTIONSNonSpecific() {
    startTest(getMethodName());
  }

  public void testSimplePATCHNonSpecific() {
    startTest(getMethodName());
  }

  public void testAbsoluteURI() {
    startTest(getMethodName());
  }

  public void testRelativeURI() {
    startTest(getMethodName());
  }

  public void testParamsAmpersand() {
    startTest(getMethodName());
  }

  /*
  Netty doesn't support semicolons!!

  public void testParamsSemiColon() {
    startTest(getMethodName());
  }
  */

  public void testNoParams() {
    startTest(getMethodName());
  }

  public void testDefaultRequestHeaders() {
    startTest(getMethodName());
  }

  public void testRequestHeadersPutAll() {
    startTest(getMethodName());
  }

  public void testRequestHeadersIndividually() {
    startTest(getMethodName());
  }

  public void testResponseHeadersPutAll() {
    startTest(getMethodName());
  }

  public void testResponseHeadersIndividually() {
    startTest(getMethodName());
  }

  public void testResponseMultipleSetCookieInHeader() {
    startTest(getMethodName());
  }

  public void testResponseMultipleSetCookieInTrailer() {
    startTest(getMethodName());
  }

  public void testResponseMultipleSetCookieInHeaderAndTrailer() {
    startTest(getMethodName());
  }

  public void testUseRequestAfterComplete() {
    startTest(getMethodName());
  }

  public void testRequestBodyBufferAtEnd() {
    startTest(getMethodName());
  }

  public void testRequestBodyStringDefaultEncodingAtEnd() {
    startTest(getMethodName());
  }

  public void testRequestBodyStringUTF8AtEnd() {
    startTest(getMethodName());
  }

  public void testRequestBodyStringUTF16AtEnd() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteBufferChunked() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteBufferNonChunked() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteBufferChunkedCompletion() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteBufferNonChunkedCompletion() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringChunkedDefaultEncoding() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringChunkedUTF8() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringChunkedUTF16() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringNonChunkedDefaultEncoding() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringNonChunkedUTF8() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringNonChunkedUTF16() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringChunkedDefaultEncodingCompletion() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringChunkedUTF8Completion() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringChunkedUTF16Completion() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringNonChunkedDefaultEncodingCompletion() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringNonChunkedUTF8Completion() {
    startTest(getMethodName());
  }

  public void testRequestBodyWriteStringNonChunkedUTF16Completion() {
    startTest(getMethodName());
  }

  public void testRequestWriteBuffer() {
    startTest(getMethodName());
  }

  public void testDefaultStatus() {
    startTest(getMethodName());
  }

  public void testOtherStatus() {
    startTest(getMethodName());
  }

  public void testStatusMessage() {
    startTest(getMethodName());
  }

  public void testResponseTrailersPutAll() {
    startTest(getMethodName());
  }

  public void testResponseTrailersPutIndividually() {
    startTest(getMethodName());
  }

  public void testResponseNoTrailers() {
    startTest(getMethodName());
  }

  public void testUseResponseAfterComplete() {
    startTest(getMethodName());
  }

  public void testResponseBodyBufferAtEnd() {
    startTest(getMethodName());
  }

  public void testResponseBodyStringDefaultEncodingAtEnd() {
    startTest(getMethodName());
  }

  public void testResponseBodyStringUTF8AtEnd() {
    startTest(getMethodName());
  }

  public void testResponseBodyStringUTF16AtEnd() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringNonChunked() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteBufferChunked() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteBufferNonChunked() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteBufferChunkedCompletion() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteBufferNonChunkedCompletion() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringChunkedDefaultEncoding() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringChunkedUTF8() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringChunkedUTF16() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringNonChunkedDefaultEncoding() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringNonChunkedUTF8() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringNonChunkedUTF16() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringChunkedDefaultEncodingCompletion() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringChunkedUTF8Completion() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringChunkedUTF16Completion() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringNonChunkedDefaultEncodingCompletion() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringNonChunkedUTF8Completion() {
    startTest(getMethodName());
  }

  public void testResponseBodyWriteStringNonChunkedUTF16Completion() {
    startTest(getMethodName());
  }

  public void testResponseWriteBuffer() {
    startTest(getMethodName());
  }

  public void testPipelining() {
    startTest(getMethodName());
  }

  public void testSendFile() {
    startTest(getMethodName());
  }

  public void testSendFileOverrideHeaders() {
    startTest(getMethodName());
  }

  public void test100ContinueDefault() {
    startTest(getMethodName());
  }

  public void test100ContinueHandled() {
    startTest(getMethodName());
  }

  public void testClientDrainHandler() throws Exception {
    startApp(PausingServer.class.getName());
    startTest(getMethodName());
  }

  public void testServerDrainHandler() throws Exception {
    startApp(DrainingServer.class.getName());
    startTest(getMethodName());
  }

  public void testPooling() throws Exception {
    startApp(CountServer.class.getName());
    startTest(getMethodName());
  }

  public void testPoolingNoKeepAlive() throws Exception {
    startApp(CountServer.class.getName());
    startTest(getMethodName());
  }

  public void testConnectionErrorsGetReportedToRequest() {
    startTest(getMethodName());
  }

  public void testRequestTimesoutWhenIndicatedPeriodExpiresWithoutAResponseFromRemoteServer() {
    startTest(getMethodName());
  }

  public void testRequestTimeoutCanceledWhenRequestHasAnOtherError() {
    startTest(getMethodName());
  }

  public void testRequestTimeoutCanceledWhenRequestEndsNormally() {
    startTest(getMethodName());
  }

  public void testRequestNotReceivedIfTimedout() {
    startTest(getMethodName());
  }

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll() throws Exception {
    testTLS(getMethodName(), false, false, true, false, false, true, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(getMethodName(), false, true, true, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(getMethodName(), false, false, true, false, false, false, false);
  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(getMethodName(), true, true, true, true, false, false, true);
  }

  @Test
  //Client specifies cert and it's not required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(getMethodName(), true, true, true, true, true, false, true);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(getMethodName(), false, true, true, true, true, false, false);
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(getMethodName(), true, true, true, false, true, false, false);
  }

  private void testTLS(String testName, boolean clientCert, boolean clientTrust,
               boolean serverCert, boolean serverTrust,
               boolean requireClientAuth, boolean clientTrustAll,
               boolean shouldPass) throws Exception {
    //Put the params in shared-data
    TLSTestParams params = new TLSTestParams(clientCert, clientTrust, serverCert, serverTrust,
        requireClientAuth, clientTrustAll, shouldPass);
    vertx.sharedData().getMap("TLSTest").put("params", params.serialize());
    startApp(TLSServer.class.getName());
    startTest(testName);
  }

  public void testConnectInvalidPort() {
    startTest(getMethodName());
  }

  public void testConnectInvalidHost() {
    startTest(getMethodName());
  }

  public void testSetHandlersAfterListening() {
    startTest(getMethodName());
  }

  @Test
  public void testSharedServersMultipleInstances1() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() * 2;
    sharedServers(getMethodName(), true, numInstances, 0, 0);
  }

  @Test
  public void testSharedServersMultipleInstances2() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() - 1;
    numInstances = numInstances > 0 ? numInstances : 1;
    sharedServers(getMethodName(), true, numInstances, 0, 0);
  }

  @Test
  public void testSharedServersMultipleInstances3() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() + 1;
    sharedServers(getMethodName(), true, numInstances, 0, 0);
  }

  @Test
  public void testSharedServersMultipleInstances1StartAllStopAll() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() * 2;
    sharedServers(getMethodName(), true, numInstances, numInstances, numInstances);
  }

  @Test
  public void testSharedServersMultipleInstances2StartAllStopAll() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() - 1;
    numInstances = numInstances > 0 ? numInstances : 1;
    sharedServers(getMethodName(), true, numInstances, numInstances, numInstances);
  }

  @Test
  public void testSharedServersMultipleInstances3StartAllStopAll() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() + 1;
    sharedServers(getMethodName(), true, numInstances, numInstances,
        numInstances);
  }

  @Test
  public void testSharedServersMultipleInstances1StartAllStopSome() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() * 2;
    sharedServers(getMethodName(), true, numInstances, numInstances, numInstances / 2);
  }

  @Test
  public void testSharedServersMultipleInstances2StartAllStopSome() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() - 1;
    numInstances = numInstances > 0 ? numInstances : 1;
    sharedServers(getMethodName(), true, numInstances, numInstances, numInstances / 2);
  }

  @Test
  public void testSharedServersMultipleInstances3StartAllStopSome() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() + 1;
    sharedServers(getMethodName(), true, numInstances, numInstances,
        numInstances / 2);
  }

  @Test
  public void testHeadNoBody() throws Exception {
    startTest(getMethodName());
  }

  void sharedServers(String testName, boolean multipleInstances, int numInstances, int initialServers, int initialToStop) throws Exception {

    //We initially start then stop them to make sure the shared server cleanup code works ok

    int numRequests = 100;

    if (initialServers > 0) {

      // First start some servers
      String[] appNames = new String[initialServers];
      for (int i = 0; i < initialServers; i++) {
        appNames[i] = startApp(InstanceCheckServer.class.getName(), 1);
      }

      vertx.sharedData().getSet("requests").clear();
      vertx.sharedData().getSet("servers").clear();
      vertx.sharedData().getSet("instances").clear();
      vertx.sharedData().getMap("params").put("numRequests", numRequests);

      startTest(testName);

      assertEquals(numRequests, vertx.sharedData().getSet("requests").size());
      // And make sure connection requests are distributed amongst them
      assertEquals(initialServers, vertx.sharedData().getSet("instances").size());

      // Then stop some

      for (int i = 0; i < initialToStop; i++) {
        stopApp(appNames[i]);
      }
    }

    vertx.sharedData().getSet("requests").clear();
    vertx.sharedData().getSet("servers").clear();
    vertx.sharedData().getSet("instances").clear();
    vertx.sharedData().getMap("params").put("numRequests", numRequests);

    //Now start some more

    if (multipleInstances) {
      startApp(InstanceCheckServer.class.getName(), numInstances);
    } else {
      for (int i = 0; i < numInstances; i++) {
        startApp(InstanceCheckServer.class.getName(), 1);
      }
    }

    startTest(testName);

    assertEquals(numRequests, vertx.sharedData().getSet("requests").size());
    // And make sure connection requests are distributed amongst them
    assertEquals(numInstances + initialServers - initialToStop, vertx.sharedData().getSet("instances").size());
  }


}
