package io.vertx.test.core;

import io.vertx.core.CaseSensitiveMultiMap;
import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class CaseSensitiveMultiMapTest extends MultiMapTest {

  protected MultiMap newMultiMap() {
    return new CaseSensitiveMultiMap();
  }

  @Test
  public void testCaseSensitiveHeaders()
      throws Exception {

    MultiMap result = newMultiMap();

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("", result.toString());
    
    result.add("testkey1", "testvalue1");
    result.add("TestKey1", "testvalue2");
    result.add("testkey2", "testvalue3");
    
    assertEquals(2, result.size());
    assertEquals(0, result.getAll("testkEy1").size());
    assertEquals(1, result.getAll("testkey1").size());
    assertEquals("testvalue1", result.getAll("testkey1").get(0));
    assertEquals("testvalue2", result.getAll("TestKey1").get(0));
    
    assertEquals(1, result.getAll("testkey2").size());
    assertEquals("testvalue3", result.getAll("testkey2").get(0));
  }

}
