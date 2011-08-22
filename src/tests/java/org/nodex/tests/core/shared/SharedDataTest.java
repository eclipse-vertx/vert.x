package org.nodex.tests.core.shared;

import org.nodex.core.Immutable;
import org.nodex.core.NodexInternal;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.shared.SharedCounter;
import org.nodex.core.shared.SharedData;
import org.nodex.core.shared.SharedMap;
import org.nodex.core.shared.SharedQueue;
import org.nodex.core.shared.SharedSet;
import org.nodex.tests.Utils;
import org.testng.annotations.Test;
import org.nodex.tests.core.TestBase;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 15:45
 */
public class SharedDataTest extends TestBase {

  @Test
  public void testMap() throws Exception {

    SharedMap<String, String> map = SharedData.getMap("foo");

    SharedMap<String, String> map2 = SharedData.getMap("foo");

    assert(map == map2);

    SharedMap<String, String> map3 = SharedData.getMap("bar");

    assert(map3 != map2);

    assert(SharedData.removeMap("foo"));

    SharedMap<String, String> map4 = SharedData.getMap("foo");

    assert(map4 != map3);
  }

  @Test
  public void testMapTypes() throws Exception {

    SharedMap map = SharedData.getMap("foo");

    String key = "key";

    class MyImmutable implements Immutable {
    }

    class SomeOtherClass {
    }

    map.put(key, 1.2d);
    map.put(key, 3.2f);
    map.put(key, (byte)1);
    map.put(key, (short)23);
    map.put(key, 23);
    map.put(key, 123l);
    map.put(key, true);
    map.put(key, (char)12);
    map.put(key, new BigDecimal(32));
    map.put(key, new MyImmutable());
    Buffer buff = Buffer.create(0);
    map.put(key, buff);
    azzert(map.get(key) != buff); // Make sure it's copied
    byte[] bytes = Utils.generateRandomByteArray(100);
    map.put(key, bytes);
    byte[] got = (byte[])map.get(key);
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

    SharedSet<String> set = SharedData.getSet("foo");

    SharedSet<String> set2 = SharedData.getSet("foo");

    assert(set == set2);

    SharedSet<String> set3 = SharedData.getSet("bar");

    assert(set3 != set2);

    assert(SharedData.removeSet("foo"));

    SharedSet<String> set4 = SharedData.getSet("foo");

    assert(set4 != set3);
  }

  @Test
  public void testCounter() throws Exception {

    SharedCounter counter = SharedData.getCounter("foo");

    SharedCounter counter2 = SharedData.getCounter("foo");

    assert(counter == counter2);

    SharedCounter counter3 = SharedData.getCounter("bar");

    assert(counter3 != counter2);

    assert(SharedData.removeCounter("foo"));

    SharedCounter counter4 = SharedData.getCounter("foo");

    assert(counter4 != counter3);
  }

  @Test
  public void testQueue() throws Exception {

    SharedQueue<String> queue = SharedData.getQueue("foo");

    SharedQueue<String> queue2 = SharedData.getQueue("foo");

    assert(queue == queue2);

    SharedQueue<String> queue3 = SharedData.getQueue("bar");

    assert(queue3 != queue2);

    assert(SharedData.removeQueue("foo"));

    SharedQueue<String> queue4 = SharedData.getQueue("foo");

    assert(queue4 != queue3);
  }

}

