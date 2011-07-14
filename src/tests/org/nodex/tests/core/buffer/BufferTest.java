package org.nodex.tests.core.buffer;

import org.nodex.core.buffer.Buffer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * User: timfox
 * Date: 14/07/2011
 * Time: 07:02
 */
public class BufferTest {

  Buffer b;

  @BeforeClass
  public void setUp() {
    b = Buffer.newFixed(1000);
  }

  @AfterClass
  public void tearDown() {
  }

  @Test
  public void testAppend() throws Exception {
    Buffer b = Buffer.newFixed(100);
    assert b.capacity() == 100;
    assert b.length() == 0;

  }
}
