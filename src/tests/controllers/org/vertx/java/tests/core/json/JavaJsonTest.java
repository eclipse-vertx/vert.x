package org.vertx.java.tests.core.json;

import org.junit.Test;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.newtests.TestBase;

/**
 *
 * TODO complete testing!!
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaJsonTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testJsonObject() throws Exception {
    JsonObject obj = new JsonObject();
    obj.putString("foo", "bar");
    String str = obj.encode();
    JsonObject obj2 = new JsonObject(str);
    assertEquals("bar", obj2.getString("foo"));
  }


}
