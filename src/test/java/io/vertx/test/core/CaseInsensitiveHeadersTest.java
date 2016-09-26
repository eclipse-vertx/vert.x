package io.vertx.test.core;

import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import org.junit.Before;

public class CaseInsensitiveHeadersTest extends MultiMapTest {

  @Before
  public void setUp() {
    mmap = createMap();
  }

  protected MultiMap createMap() {
    return new CaseInsensitiveHeaders();
  }
}

