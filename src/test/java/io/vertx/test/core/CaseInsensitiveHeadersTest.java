package io.vertx.test.core;

import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import org.junit.Test;

import static org.junit.Assert.*;

public class CaseInsensitiveHeadersTest extends MultiMapTest {

  @Override
  protected MultiMap newMultiMap() {
    return new CaseInsensitiveHeaders();
  }

  @Test
  public void testCaseInsensitiveHeaders()
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
    assertEquals(2, result.getAll("testkEy1").size());
    assertEquals("testvalue1", result.getAll("testkey1").get(0));
    assertEquals("testvalue2", result.getAll("testkey1").get(1));
    
    assertEquals(1, result.getAll("testkEy2").size());
    assertEquals("testvalue3", result.getAll("testkey2").get(0));
  }
}
