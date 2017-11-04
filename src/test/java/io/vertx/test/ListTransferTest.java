package io.vertx.test;

import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ListTransferTest {

  private static final int CONNECTING = 0;
  private static final int WRITING = 1;
  private static final int CONNECTED = 2;

  @Test
  public void doTheTest() throws Exception {

    AtomicInteger status = new AtomicInteger();
    ArrayList<Integer> array = new ArrayList<>();

    AtomicInteger expectedCount = new AtomicInteger();

    Thread t1 = new Thread(() -> {
      int count = 0;
      while (true) {
        switch (status.get()) {
          case CONNECTING:
            if (status.compareAndSet(CONNECTING, WRITING)) {
              array.add(count++);
              status.set(CONNECTING);
            }
            break;
          case CONNECTED: {
            expectedCount.set(count);
            return;
          }
        }
      }
    });

    AtomicReference<ArrayList<Integer>> expectedArray = new AtomicReference<>();
    AtomicInteger expectedSpin = new AtomicInteger();
    Thread t2 = new Thread(() -> {
      try {
        Thread.sleep(50);
      } catch (InterruptedException ignore) {
        return;
      }
      int spin = 0;
      while (true) {
        if (status.compareAndSet(CONNECTING, CONNECTED)) {
          expectedArray.set(new ArrayList<>(array));
          expectedSpin.set(spin);
          return;
        } else {
          spin ++;
          Thread.yield();
        }
      }
    });

    t1.start();
    t2.start();

    t1.join();
    t2.join();

    assertEquals(expectedCount.get(), expectedArray.get().size());

    System.out.println(expectedCount.get());
    System.out.println(expectedSpin.get());




  }


}
