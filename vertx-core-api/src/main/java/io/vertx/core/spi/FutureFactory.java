package io.vertx.core.spi;

import io.vertx.core.*;

import java.util.concurrent.CompletionStage;

public interface FutureFactory {

  FutureFactory INSTANCE = ServiceHelper.loadFactory(FutureFactory.class);

  /**
   * Create a promise that hasn't completed yet
   *
   * @param <T>  the result type
   * @return  the promise
   */
  <T> Promise<T> promise();

  /**
   * Created a succeeded future with the specified result.
   *
   * @param result  the result
   * @param <T>  the result type
   * @return  the future
   */
  <T> Future<T> succeededFuture(T result);

  /**
   * Create a failed future with the specified failure cause.
   *
   * @param t  the failure cause as a Throwable
   * @param <T>  the result type
   * @return  the future
   */
  <T> Future<T> failedFuture(Throwable t);

  /**
   * Create a failed future with the specified failure message.
   *
   * @param failureMessage  the failure message
   * @param <T>  the result type
   * @return  the future
   */
  <T> Future<T> failedFuture(String failureMessage);

  CompositeFuture all(Future<?>... results);

  CompositeFuture any(Future<?>... results);

  CompositeFuture join(Future<?>... results);

  <T> Future<T> fromCompletionStage(CompletionStage<T> completionStage, Context context);

}
