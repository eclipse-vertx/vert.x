package org.vertx.java.core.cluster.spi;

import org.vertx.java.core.CompletionHandler;

import java.util.Collection;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 *
 */
public interface AsyncMultiMap<K, V> {

  void put(K k, V v, CompletionHandler<Void> completionHandler);

  void get(K k, CompletionHandler<Collection<V>> completionHandler);

  void remove(K k, V v, CompletionHandler<Boolean> completionHandler);
}
