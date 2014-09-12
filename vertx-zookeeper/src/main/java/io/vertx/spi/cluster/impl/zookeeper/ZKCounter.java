package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.spi.cluster.Action;
import io.vertx.core.spi.cluster.VertxSPI;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;

/**
 *
 */
class ZKCounter implements Counter {

  private final CuratorFramework curator;
  private final String nodeName;
  private final VertxSPI vertxSPI;
  private DistributedAtomicLong atomicLong;

  public ZKCounter(CuratorFramework curator, RetryPolicy retryPolicy, VertxSPI vertxSPI, String nodeName) {
    this.curator = curator;
    this.nodeName = nodeName;
    this.vertxSPI = vertxSPI;
    this.atomicLong = new DistributedAtomicLong(curator, "/counters/" + nodeName, retryPolicy);
  }

  @Override
  public void get(Handler<AsyncResult<Long>> resultHandler) {
    vertxSPI.executeBlocking(() -> {
      long result = 0;
      try {
        result = atomicLong.get().preValue();
      } catch (Exception e) {
        throw new RuntimeException(e.getMessage());
      }
      return result;
    }, resultHandler);
  }

  @Override
  public void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
    try {
      if (atomicLong.increment().succeeded()) {
        atomicLong.get().postValue();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {

  }

  @Override
  public void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {

  }

  @Override
  public void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {

  }

  @Override
  public void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {

  }

  @Override
  public void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {
    try {
      atomicLong.compareAndSet(expected, value).succeeded();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
