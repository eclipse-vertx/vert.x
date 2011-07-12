package org.nodex.tests.core.parsetools;

/**
 * User: tim
 * Date: 12/07/11
 * Time: 10:13
 */
import org.testng.annotations.Test;

public class TruncatedOutputTest {
    @Test
    public void truncatedOutput() {
      for (int i = 0; i<  100; i++) {
        System.out.println("This is line " + i);
      }
      assert false;
    }
}
