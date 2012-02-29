/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.tests.old.composition;

import junit.framework.TestCase;
import org.junit.Test;
import org.vertx.java.core.composition.Composer;
import org.vertx.java.core.impl.DeferredAction;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaComposerTest extends TestCase {


  @Test
  public void testParallel() throws Exception {

    Composer c = new Composer();

    MyDeferred d1 = new MyDeferred();
    c.parallel(d1);
    assertFalse(d1.executed());

    MyDeferred d2 = new MyDeferred();
    c.parallel(d2);
    assertFalse(d2.executed());

    c.execute();

    assertTrue(d1.executed());
    assertTrue(d2.executed());
  }

  @Test
  public void testSimpleSeries() throws Exception {

    Composer c = new Composer();

    MyDeferred d1 = new MyDeferred();
    c.series(d1);
    assertFalse(d1.executed());

    MyDeferred d2 = new MyDeferred();
    c.series(d2);
    assertFalse(d2.executed());

    MyDeferred d3 = new MyDeferred();
    c.series(d3);
    assertFalse(d3.executed());

    c.execute();

    assertTrue(d1.executed());
    assertFalse(d2.executed());
    assertFalse(d3.executed());

    d1.setResult();

    assertTrue(d2.executed());
    assertFalse(d3.executed());

    d2.setResult();

    assertTrue(d3.executed());
  }

  @Test
  public void testMixedSeries() throws Exception {

    Composer c = new Composer();

    MyDeferred d1_1 = new MyDeferred();
    c.parallel(d1_1);
    assertFalse(d1_1.executed());
    MyDeferred d1_2 = new MyDeferred();
    c.parallel(d1_2);
    assertFalse(d1_2.executed());
    MyDeferred d1_3 = new MyDeferred();
    c.parallel(d1_3);
    assertFalse(d1_3.executed());

    MyDeferred d2_1 = new MyDeferred();
    c.series(d2_1);
    assertFalse(d2_1.executed());
    MyDeferred d2_2 = new MyDeferred();
    c.parallel(d2_2);
    assertFalse(d2_2.executed());
    MyDeferred d2_3 = new MyDeferred();
    c.parallel(d2_3);
    assertFalse(d2_3.executed());


    MyDeferred d3_1 = new MyDeferred();
    c.series(d3_1);
    assertFalse(d3_1.executed());
    MyDeferred d3_2 = new MyDeferred();
    c.parallel(d3_2);
    assertFalse(d3_2.executed());
    MyDeferred d3_3 = new MyDeferred();
    c.parallel(d3_3);
    assertFalse(d3_3.executed());

    c.execute();

    assertTrue(d1_1.executed());
    assertTrue(d1_2.executed());
    assertTrue(d1_3.executed());
    assertFalse(d2_1.executed());
    assertFalse(d2_2.executed());
    assertFalse(d2_3.executed());
    assertFalse(d3_1.executed());
    assertFalse(d3_2.executed());
    assertFalse(d3_3.executed());

    d1_1.setResult();

    assertFalse(d2_1.executed());
    assertFalse(d2_2.executed());
    assertFalse(d2_3.executed());
    assertFalse(d3_1.executed());
    assertFalse(d3_2.executed());
    assertFalse(d3_3.executed());

    d1_2.setResult();

    assertFalse(d2_1.executed());
    assertFalse(d2_2.executed());
    assertFalse(d2_3.executed());
    assertFalse(d3_1.executed());
    assertFalse(d3_2.executed());
    assertFalse(d3_3.executed());

    d1_3.setResult();
    assertTrue(d2_1.executed());
    assertTrue(d2_2.executed());
    assertTrue(d2_3.executed());
    assertFalse(d3_1.executed());
    assertFalse(d3_2.executed());
    assertFalse(d3_3.executed());

    d2_1.setResult();

    assertFalse(d3_1.executed());
    assertFalse(d3_2.executed());
    assertFalse(d3_3.executed());

    d2_2.setResult();

    assertFalse(d3_1.executed());
    assertFalse(d3_2.executed());
    assertFalse(d3_3.executed());

    d2_3.setResult();
    assertTrue(d3_1.executed());
    assertTrue(d3_2.executed());
    assertTrue(d3_3.executed());
  }

  @Test
  public void testCantCallSeriesAfterExecuted() {
    Composer c = new Composer();
    c.execute();
    MyDeferred d = new MyDeferred();
    try {
      c.series(d);
      fail("Should throw exception");
    }
    catch (IllegalStateException e) {
      //OK
    }
  }

  @Test
  public void testCantCallParallelAfterExecuted() {
    Composer c = new Composer();
    c.execute();
    MyDeferred d = new MyDeferred();
    try {
      c.parallel(d);
      fail("Should throw exception");
    }
    catch (IllegalStateException e) {
      //OK
    }
  }

  class MyDeferred extends DeferredAction<Void> {

    @Override
    protected void run() {
    }

    public boolean executed() {
      return executed;
    }

    public void setResult() {
      setResult(null);
    }
  }

}
