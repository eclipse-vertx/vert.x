package org.vertx.java.core.cluster.spi.hazelcast;

import org.vertx.java.core.BlockingAction;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Deferred;
import org.vertx.java.core.Future;
import org.vertx.java.core.cluster.EventBus;
import org.vertx.java.core.cluster.spi.AsyncMultiMap;
import org.vertx.java.core.net.ServerID;

import java.util.Collection;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {

  private final com.hazelcast.core.MultiMap<K, V> map;

  public HazelcastAsyncMultiMap(com.hazelcast.core.MultiMap<K, V> map) {
    this.map = map;
  }

  @Override
  public void put(final K k, final V v, CompletionHandler<Void> completionHandler) {

    Deferred<Void> action = new BlockingAction<Void>() {
      public Void action() throws Exception {
        map.put(k, v);
        return null;
      }
    };
    action.handler(completionHandler);
    action.execute();
  }

  @Override
  public void get(final K k, CompletionHandler<Collection<V>> completionHandler) {
    Deferred<Collection<V>> action = new BlockingAction<Collection<V>>() {
      public Collection<V> action() throws Exception {
        return map.get(k);
      }
    };
    action.handler(completionHandler);
    action.execute();
  }

  @Override
  public void remove(final K k, final V v, CompletionHandler<Boolean> completionHandler) {
    Deferred<Boolean> action = new BlockingAction<Boolean>() {
      public Boolean action() throws Exception {
        return map.remove(k, v);
      }
    };
    action.handler(completionHandler);
    action.execute();
  }


}
