/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.tests.core.composition;

import org.nodex.java.core.SimpleDeferred;
import org.nodex.java.core.composition.Composer;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ComposerTest extends TestBase {


  @Test
  public void testParallel() throws Exception {

    Composer c = new Composer();

    MyDeferred d1 = new MyDeferred();
    c.parallel(d1);
    azzert(!d1.executed());

    MyDeferred d2 = new MyDeferred();
    c.parallel(d2);
    azzert(!d2.executed());

    c.execute();

    azzert(d1.executed());
    azzert(d2.executed());
  }

  @Test
  public void testSimpleSeries() throws Exception {

    Composer c = new Composer();

    MyDeferred d1 = new MyDeferred();
    c.series(d1);
    azzert(!d1.executed());

    MyDeferred d2 = new MyDeferred();
    c.series(d2);
    azzert(!d2.executed());

    MyDeferred d3 = new MyDeferred();
    c.series(d3);
    azzert(!d3.executed());

    c.execute();

    azzert(d1.executed());
    azzert(!d2.executed());
    azzert(!d3.executed());

    d1.setResult();

    azzert(d2.executed());
    azzert(!d3.executed());

    d2.setResult();

    azzert(d3.executed());
  }

  @Test
  public void testMixedSeries() throws Exception {

    Composer c = new Composer();

    MyDeferred d1_1 = new MyDeferred();
    c.parallel(d1_1);
    azzert(!d1_1.executed());
    MyDeferred d1_2 = new MyDeferred();
    c.parallel(d1_2);
    azzert(!d1_2.executed());
    MyDeferred d1_3 = new MyDeferred();
    c.parallel(d1_3);
    azzert(!d1_3.executed());

    MyDeferred d2_1 = new MyDeferred();
    c.series(d2_1);
    azzert(!d2_1.executed());
    MyDeferred d2_2 = new MyDeferred();
    c.parallel(d2_2);
    azzert(!d2_2.executed());
    MyDeferred d2_3 = new MyDeferred();
    c.parallel(d2_3);
    azzert(!d2_3.executed());


    MyDeferred d3_1 = new MyDeferred();
    c.series(d3_1);
    azzert(!d3_1.executed());
    MyDeferred d3_2 = new MyDeferred();
    c.parallel(d3_2);
    azzert(!d3_2.executed());
    MyDeferred d3_3 = new MyDeferred();
    c.parallel(d3_3);
    azzert(!d3_3.executed());

    c.execute();

    azzert(d1_1.executed());
    azzert(d1_2.executed());
    azzert(d1_3.executed());
    azzert(!d2_1.executed());
    azzert(!d2_2.executed());
    azzert(!d2_3.executed());
    azzert(!d3_1.executed());
    azzert(!d3_2.executed());
    azzert(!d3_3.executed());

    d1_1.setResult();

    azzert(!d2_1.executed());
    azzert(!d2_2.executed());
    azzert(!d2_3.executed());
    azzert(!d3_1.executed());
    azzert(!d3_2.executed());
    azzert(!d3_3.executed());

    d1_2.setResult();

    azzert(!d2_1.executed());
    azzert(!d2_2.executed());
    azzert(!d2_3.executed());
    azzert(!d3_1.executed());
    azzert(!d3_2.executed());
    azzert(!d3_3.executed());

    d1_3.setResult();
    azzert(d2_1.executed());
    azzert(d2_2.executed());
    azzert(d2_3.executed());
    azzert(!d3_1.executed());
    azzert(!d3_2.executed());
    azzert(!d3_3.executed());

    d2_1.setResult();

    azzert(!d3_1.executed());
    azzert(!d3_2.executed());
    azzert(!d3_3.executed());

    d2_2.setResult();

    azzert(!d3_1.executed());
    azzert(!d3_2.executed());
    azzert(!d3_3.executed());

    d2_3.setResult();
    azzert(d3_1.executed());
    azzert(d3_2.executed());
    azzert(d3_3.executed());
  }

  @Test
  public void testCantCallSeriesAfterExecuted() {
    Composer c = new Composer();
    c.execute();
    MyDeferred d = new MyDeferred();
    try {
      c.series(d);
      azzert(false, "Should throw exception");
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
      azzert(false, "Should throw exception");
    }
    catch (IllegalStateException e) {
      //OK
    }
  }

  class MyDeferred extends SimpleDeferred<Void> {

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
