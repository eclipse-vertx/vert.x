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

package org.nodex.tests.core.shared;

import org.nodex.java.core.Immutable;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.shared.SharedData;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SharedDataTest extends TestBase {

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
    map.put(key, new BigDecimal(32));
    map.put(key, new MyImmutable());
    Buffer buff = Buffer.create(0);
    map.put(key, buff);
    azzert(map.get(key) != buff); // Make sure it's copied
    byte[] bytes = Utils.generateRandomByteArray(100);
    map.put(key, bytes);
    byte[] got = (byte[]) map.get(key);
    azzert(got != bytes);
    azzert(Utils.byteArraysEqual(bytes, got));
    try {
      map.put(key, new SomeOtherClass());
      azzert(false, "Should throw exception");
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

//  @Test
//  public void testCounter() throws Exception {
//
//    SharedCounter counter = SharedData.getCounter("foo");
//
//    SharedCounter counter2 = SharedData.getCounter("foo");
//
//    assert (counter == counter2);
//
//    SharedCounter counter3 = SharedData.getCounter("bar");
//
//    assert (counter3 != counter2);
//
//    assert (SharedData.removeCounter("foo"));
//
//    SharedCounter counter4 = SharedData.getCounter("foo");
//
//    assert (counter4 != counter3);
//  }

//  @Test
//  public void testQueue() throws Exception {
//
//    SharedQueue<String> queue = SharedData.getQueue("foo");
//
//    SharedQueue<String> queue2 = SharedData.getQueue("foo");
//
//    assert (queue == queue2);
//
//    SharedQueue<String> queue3 = SharedData.getQueue("bar");
//
//    assert (queue3 != queue2);
//
//    assert (SharedData.removeQueue("foo"));
//
//    SharedQueue<String> queue4 = SharedData.getQueue("foo");
//
//    assert (queue4 != queue3);
//  }

}

