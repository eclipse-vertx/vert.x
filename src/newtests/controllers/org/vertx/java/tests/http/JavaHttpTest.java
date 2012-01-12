package org.vertx.java.tests.http;

import org.junit.Test;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.AppType;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.newtests.TestBase;

import org.vertx.java.newtests.TestUtils;
import vertx.tests.http.TestClient;
import vertx.tests.http.TestServer;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaHttpTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JAVA, TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testCreateServerNoContext() throws Exception {
    try {
      new HttpServer();
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCreateClientNoContext() throws Exception {
    try {
      new HttpClient();
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
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

  public void testResponseSetTrailerNonChunked() {
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

  public void test100ContinueDefault() {
    startTest(getMethodName());
  }

  public void test100ContinueHandled() {
    startTest(getMethodName());
  }

}
