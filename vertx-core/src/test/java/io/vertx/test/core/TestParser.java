package io.vertx.test.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@FunctionalInterface
public interface TestParser {

  int parse(String s, int from, int to);

  default void assertParse(String s) {
    int res = parse(s, 0, s.length());
    assertEquals(s.length(), res);
  }

  default void assertFailParse(String s) {
    int res = parse(s, 0, s.length());
    if (res == s.length()) {
      fail();
    }
  }
}
