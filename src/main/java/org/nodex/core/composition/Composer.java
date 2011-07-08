package org.nodex.core.composition;

import org.nodex.core.DoneHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

  public Composer when(final DoneHandler handler) {
    Deferred deff = new Deferred(handler);
    return when(deff);
  }

  public Composer when(final Completion completion) {
    return when(new Completion[] { completion });
  }

  public Composer when(final Completion... completions) {
    Runnable run = new Runnable() {
      public void run() {
        final AtomicInteger countDown = new AtomicInteger(completions.length);
        DoneHandler cb = new DoneHandler() {
          public void onDone() {
            if (countDown.decrementAndGet() == 0) {
              next();
            }
          }
        };
        for (Completion c: completions) {
          c.execute();
          c.onComplete(cb);
        }
      }
    };
    runList.add(run);
    return this;
  }

  public Composer afterDelay(long delay, final Completion completion) {
    return this;
  }

  private void next() {
    pos++;
    if (pos < runList.size()) {
      runCurrent();
    }
  }

  private void runCurrent() {
    Runnable run = runList.get(pos);
    run.run();
  }

  public void end() {
    pos = 0;
    runCurrent();
  }

}
