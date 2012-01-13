package org.vertx.java.tests.http;

import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;
import vertx.tests.http.RouteMatcherTestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaRouteMatcherTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JAVA, RouteMatcherTestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testRouteWithPattern1GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern2GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern3GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern4GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern5GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern6GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternPUT() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternPOST() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternDELETE() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternHEAD() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternOPTIONS() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternTRACE() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternCONNECT() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternPATCH() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexGET() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexPUT() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexPOST() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexDELETE() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexHEAD() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexOPTIONS() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexTRACE() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexCONNECT() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexPATCH() {
    startTest(getMethodName());
  }

  public void testRouteNoMatchPattern() {
    startTest(getMethodName());
  }

  public void testRouteNoMatchRegex() {
    startTest(getMethodName());
  }
}
