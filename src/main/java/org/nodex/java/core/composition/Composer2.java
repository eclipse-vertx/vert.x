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
