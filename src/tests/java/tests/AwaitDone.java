package tests;

import org.nodex.core.DoneHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: tim
 * Date: 12/07/11
 * Time: 15:40
 */
public class AwaitDone extends DoneHandler {
    private CountDownLatch latch = new CountDownLatch(1);
    public void onDone() {
      latch.countDown();
    }

    public boolean awaitDone(long timeout) {
      try {
        return latch.await(timeout, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        return false;
      }
    }
  }