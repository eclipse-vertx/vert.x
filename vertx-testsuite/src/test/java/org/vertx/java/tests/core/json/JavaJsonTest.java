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

package org.vertx.java.tests.core.json;

import org.junit.Test;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.framework.TestBase;

/**
 *
 * TODO complete testing!!
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaJsonTest extends TestBase {

  private static final Logger log = LoggerFactory.getLogger(JavaJsonTest.class);

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
    JsonObject obj = new JsonObject().putString("foo", "bar");
    String str = obj.encode();
    JsonObject obj2 = new JsonObject(str);
    assertEquals("bar", obj2.getString("foo"));
  }

  @Test
  public void testJsonArrayToClone() {
    JsonArray array = new JsonArray();
    array.add("test");
    JsonObject object = new JsonObject();
    object.putArray("array", array);

    //want to clone
    JsonObject object2 = new JsonObject(object.toMap());
    //this shouldn't throw an exception, it does before patch
    JsonArray array2 = object2.getArray("array");
  }

  @Test
  public void testRetrieveArrayItemByIndex() {
    JsonArray arr = new JsonArray();
    
    arr.addString("foo");
    arr.addObject(new JsonObject().putString("bar", "baz"));
    arr.addString("bap");
    
    assertEquals("baz", ((JsonObject) arr.get(1)).getString("bar"));
  }

  @Test
  public void testPutsNullObjectWithoutException() {
    log.debug(
      new JsonObject()
        .putObject("null", null) // this shouldn't cause a NullPointerException
        .encode()
    );
    
    log.debug(
      new JsonObject()
        .putObject("null", new JsonObject().putString("foo", "bar"))
        .encode()
    );
  }
}
