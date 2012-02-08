package org.vertx.java.tests.core.parsetools;

import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyRecordParserTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/parsetools/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_delimited() {
    startTest(getMethodName());
  }
}
