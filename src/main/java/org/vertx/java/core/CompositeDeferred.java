package org.vertx.java.core;

import org.vertx.java.core.composition.Composer;
import org.vertx.java.core.file.FileSystem;

/**
 *
 * A Deferred in the body of the run() of which you can get and pass back another future the result of which will
 * be wired into this deferred.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class CompositeDeferred<T> extends DeferredAction<T> {

  @Override
  protected void run() {
    Future<T> def = doRun();
    def.handler(new CompletionHandler<T>() {
      public void handle(Future<T> future) {
        if (future.succeeded()) {
          setResult(future.result());
        } else {
          setException(future.exception());
        }
      }
    });
    if (def instanceof Deferred) {
      ((Deferred)def).execute();
    }
  }

  protected abstract Future<T> doRun();

  private static void example() {
    Composer comp = new Composer();
    final Future<Boolean> fut1 = comp.series(FileSystem.instance.existsDeferred("foo"));
    Future<Boolean> fut2 = comp.series(new CompositeDeferred<Boolean>() {
      public Future<Boolean> doRun() {
        if (fut1.result()) {
          return FileSystem.instance.exists("bar");
        } else {
          return FileSystem.instance.exists("quux");
        }
      }
    });
  }
}
