package org.vertx.java.tests.core.filesystem;

import org.junit.Test;
import org.vertx.java.core.app.VerticleType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyFileSystemTest extends TestBase {

  private static final Logger log = Logger.getLogger(RubyFileSystemTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(VerticleType.RUBY, "core/filesystem/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void test_stats() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void test_async_file() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void test_async_file_streams() throws Exception {
    startTest(getMethodName());
  }


}
