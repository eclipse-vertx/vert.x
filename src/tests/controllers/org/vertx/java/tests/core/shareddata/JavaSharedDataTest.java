/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.tests.core.shareddata;

import junit.framework.TestCase;
import org.junit.Test;
import org.vertx.java.core.Immutable;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaSharedDataTest extends TestCase {

  @Test
  public void testMap() throws Exception {

    Map<String, String> map = SharedData.getMap("foo");

    Map<String, String> map2 = SharedData.getMap("foo");

    assert (map == map2);

    Map<String, String> map3 = SharedData.getMap("bar");

    assert (map3 != map2);

    assert (SharedData.removeMap("foo"));

    Map<String, String> map4 = SharedData.getMap("foo");

    assert (map4 != map3);
  }

  @Test
  public void testMapTypes() throws Exception {

    Map map = SharedData.getMap("foo");

    String key = "key";

    class MyImmutable implements Immutable {
    }

    class SomeOtherClass {
    }

    map.put(key, 1.2d);
    map.put(key, 3.2f);
    map.put(key, (byte) 1);
    map.put(key, (short) 23);
    map.put(key, 23);
    map.put(key, 123l);
    map.put(key, true);
    map.put(key, (char) 12);
    map.put(key, new MyImmutable());
    Buffer buff = Buffer.create(0);
    map.put(key, buff);
    assertTrue(map.get(key) != buff); // Make sure it's copied
    byte[] bytes = TestUtils.generateRandomByteArray(100);
    map.put(key, bytes);
    byte[] got = (byte[]) map.get(key);
    assertTrue(got != bytes);
    assertTrue(TestUtils.byteArraysEqual(bytes, got));
    try {
      map.put(key, new SomeOtherClass());
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }
  }


  @Test
  public void testSet() throws Exception {

    Set<String> set = SharedData.getSet("foo");

    Set<String> set2 = SharedData.getSet("foo");

    assert (set == set2);

    Set<String> set3 = SharedData.getSet("bar");

    assert (set3 != set2);

    assert (SharedData.removeSet("foo"));

    Set<String> set4 = SharedData.getSet("foo");

    assert (set4 != set3);
  }



}

