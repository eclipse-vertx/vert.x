/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tests.core.buffer;

import org.nodex.core.buffer.Buffer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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
