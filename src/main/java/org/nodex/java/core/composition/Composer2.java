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

package org.nodex.java.core.composition;

/**
 * User: tim
 * Date: 17/08/11
 * Time: 18:41
 */
public class Composer2 {
  public Composable parallel(Composable... composables) {
    return null;
  }

  public Composable series(Composable... composables) {
    return null;
  }

  public Composable whilst(Predicate pred, Composable... composable) {
    return null;
  }

  public Composable until(Predicate pred, Composable... composable) {
    return null;
  }

  public Composable times(int times, Composable composable) {
    return null;
  }

  private interface Predicate {
    boolean test();
  }

  public void examples() {
    Composer2 comp = new Composer2();

    Composable par1 = null;
    Composable par2 = null;
    Composable par3 = null;

    Predicate pred1 = null;

    comp.whilst(pred1, comp.series(comp.parallel(par1, par2, par3), comp.parallel(par1, par2)));

    //Executes the composable 100 times
    comp.times(100, par1);

    //Now.. if we could have composables that return results, then we can do map, fold, sum etc

    /*
    e.g. comp.sum(array, callback<int>)

    which could sum the array in different parts on different threads using the background pool, and call the callback
    when it's done


     */
  }

  abstract class Action<T> {
    public abstract T execute();

    protected void setResult(T res) {

    }

    public void completionHandler(Runnable runnable) {

    }

  }
}
