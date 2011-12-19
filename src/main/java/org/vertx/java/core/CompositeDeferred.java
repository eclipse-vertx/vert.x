package org.vertx.java.core;

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

}
