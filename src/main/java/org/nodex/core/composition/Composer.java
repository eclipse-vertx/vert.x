package org.nodex.core.composition;

import java.util.ArrayList;
import java.util.List;

/**
 * User: timfox
 * Date: 02/07/2011
 * Time: 18:47
 */
public class Composer {

  public static Composer compose() {
    return new Composer();
  }

  private List<Runnable> runList = new ArrayList<Runnable>();
  private int pos;

  private Composer() {
  }

  public Composer parallel(Deferred... deferred) {
    return this;
  }

  public Composer then(Deferred d) {
    return this;
  }

  public void run() {
  }

}
