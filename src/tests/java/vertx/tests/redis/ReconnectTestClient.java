package vertx.tests.redis;

import org.vertx.java.addons.redis.RedisConnection;
import org.vertx.java.addons.redis.RedisPool;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.newtests.TestClientBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ReconnectTestClient extends TestClientBase {

  private RedisPool pool;
  private RedisConnection conn;

  @Override
  public void start() {
    super.start();
    pool = new RedisPool();
    pool.setMaxPoolSize(1);
    pool.setReconnectAttempts(1000);
    conn = pool.connection();
    tu.appReady();
  }

  @Override
  public void stop() {
    conn.close();
    pool.close();
    super.stop();
  }

  public void testConnectionFailure() {

    System.out.println("Executing a set");
    conn.set(Buffer.create("key1"), Buffer.create("val1")).handler(new CompletionHandler<Void>() {
      public void handle(Future<Void> future) {
        System.out.println("Result of set returned");
      }
    }).execute();

    System.out.println("Now sleeping. Please kill redis. Then restart it.");

    Vertx.instance.setTimer(10000, new Handler<Long>() {
      public void handle(Long timerID) {

        System.out.println("Executing a get");
        conn.get(Buffer.create("key1")).handler(new CompletionHandler<Buffer>() {
          public void handle(Future<Buffer> future) {
            System.out.println("Result of get returned " + future.result());
            tu.testComplete();
          }
        }).execute();
      }
    });
  }

  public void testConnectionFailureWhenInPool() {

    System.out.println("Executing a set");
    conn.set(Buffer.create("key1"), Buffer.create("val1")).handler(new CompletionHandler<Void>() {
      public void handle(Future<Void> future) {
        System.out.println("Result of set returned");
      }
    }).execute();

    conn.close();

    System.out.println("Now sleeping. Please kill redis. Then restart it.");

    Vertx.instance.setTimer(10000, new Handler<Long>() {
      public void handle(Long timerID) {

        final RedisConnection conn2 = pool.connection();

        System.out.println("Executing a get");
        conn2.get(Buffer.create("key1")).handler(new CompletionHandler<Buffer>() {
          public void handle(Future<Buffer> future) {
            System.out.println("Result of get returned " + future.result());

            tu.testComplete();
          }
        }).execute();
      }
    });
  }


}
