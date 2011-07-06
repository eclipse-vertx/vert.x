package org.nodex.core.composition;

import org.nodex.core.Callback;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: timfox
 * Date: 03/07/2011
 * Time: 09:11
 */
public class Example {
  public static class Redis {

    private static void do_lookup(final int key, Callback<Integer> cb) {
      //This would do the actual redis lookup and cb would be called with the result
    }

    public static Completion lookup(final int key, final Callback<Integer> cb) {
      return new Completion() {
        public void perform() {
          Redis.do_lookup(key, new Callback<Integer>() {
            public void onEvent(Integer res) {
              cb.onEvent(res);
              complete();
            }
          });
        }
      };
    }

  }

  public static void example() {

    final AtomicInteger res1 = new AtomicInteger(0);

    final Completion d1 = Redis.lookup(1234, new Callback<Integer>() {
      public void onEvent(Integer res) {
        res1.set(res);
      }
    });

    final AtomicInteger res2 = new AtomicInteger(0);

    final Completion d2 = Redis.lookup(7263, new Callback<Integer>() {
      public void onEvent(Integer res) {
        res2.set(res);
      }
    });

    final Completion after = new Completion() {
      public void perform() {
        System.out.println("Total is " + (res1.get() + res2.get()));
      }
    };

    Composer.compose().parallel(d1, d2).then(after).run();
  }
}
