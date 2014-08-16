package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalSharedDataTest extends VertxTestBase {

  private SharedData sharedData;

  @Before
  public void before() {    
    sharedData = vertx.sharedData();
  }

  @Test
  public void testMap() throws Exception {

    LocalMap<String, String> map = sharedData.getLocalMap("foo");
    LocalMap<String, String> map2 = sharedData.getLocalMap("foo");
    assertTrue(map == map2);
    LocalMap<String, String> map3 = sharedData.getLocalMap("bar");
    assertFalse(map3 == map2);
    map.close();
    LocalMap<String, String> map4 = sharedData.getLocalMap("foo");
    assertFalse(map4 == map3);
  }

  @Test
  public void testMapTypes() throws Exception {

    LocalMap map = sharedData.getLocalMap("foo");

    String key = "key";

    double d = new Random().nextDouble();
    map.put(key, d);
    assertEquals(d, map.get(key));

    float f = new Random().nextFloat();
    map.put(key, f);
    assertEquals(f, map.get(key));

    byte b = (byte)new Random().nextInt();
    map.put(key, b);
    assertEquals(b, map.get(key));

    short s = (short)new Random().nextInt();
    map.put(key, s);
    assertEquals(s, map.get(key));

    int i = new Random().nextInt();
    map.put(key, i);
    assertEquals(i, map.get(key));

    long l = new Random().nextLong();
    map.put(key, l);
    assertEquals(l, map.get(key));

    map.put(key, true);
    assertTrue((Boolean)map.get(key));

    map.put(key, false);
    assertFalse((Boolean) map.get(key));

    char c = (char)new Random().nextLong();
    map.put(key, c);
    assertEquals(c, map.get(key));

    Buffer buff = TestUtils.randomBuffer(100);
    map.put(key, buff);
    Buffer got1 = (Buffer)map.get(key);
    assertTrue(got1 != buff); // Make sure it's copied
    assertEquals(buff, map.get(key));
    Buffer got2 = (Buffer)map.get(key);
    assertTrue(got1 != got2); // Should be copied each time
    assertTrue(got2 != buff);
    assertEquals(buff, map.get(key));


    byte[] bytes = TestUtils.randomByteArray(100);
    map.put(key, bytes);
    byte[] bgot1 = (byte[]) map.get(key);
    assertTrue(bgot1 != bytes);
    assertArrayEquals(bytes, bgot1);
    byte[] bgot2 = (byte[]) map.get(key);
    assertTrue(bgot2 != bytes);
    assertTrue(bgot1 != bgot2);
    assertArrayEquals(bytes, bgot2);

    try {
      map.put(key, new SomeOtherClass());
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }
  }



  class SomeOtherClass {
  }

}
