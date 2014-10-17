package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import org.apache.curator.framework.CuratorFramework;

/**
 * Created by Stream.Liu
 */
class ZKAsyncMap<K, V> extends ZKMap<K, V> implements AsyncMap<K, V> {

  ZKAsyncMap(Vertx vertx, CuratorFramework curator, String mapName) {
    super(curator, vertx, ZK_PATH_ASYNC_MAP, mapName);
  }

  @Override
  public void get(K k, Handler<AsyncResult<V>> asyncResultHandler) {
    checkExists(k, existEvent -> {
      if (existEvent.succeeded()) {
        if (existEvent.result()) {
          getData(k, (Class<V>) Object.class, getDataEvent -> forwardAsyncResult(asyncResultHandler, getDataEvent));
        } else {
          vertx.runOnContext(handler -> asyncResultHandler.handle(Future.completedFuture()));
        }
      } else {
        vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(existEvent.cause())));
      }
    });
  }

  @Override
  public void put(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    checkExists(k, existEvent -> {
      if (existEvent.succeeded()) {
        if (existEvent.result()) {
          setData(k, v, setDataEvent -> forwardAsyncResult(completionHandler, setDataEvent));
        } else {
          create(k, v, completionHandler);
        }
      } else {
        vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(existEvent.cause())));
      }
    });
  }

  @Override
  public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> completionHandler) {
    get(k, getEvent -> {
      if (getEvent.succeeded()) {
        if (getEvent.result() == null) {
          put(k, v, putEvent -> forwardAsyncResult(completionHandler, putEvent));
        } else {
          vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(getEvent.result())));
        }
      } else {
        vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(getEvent.cause())));
      }
    });
  }

  @Override
  public void remove(K k, Handler<AsyncResult<V>> asyncResultHandler) {
    get(k, getEvent -> {
      if (getEvent.succeeded()) {
        delete(k, getEvent.result(), asyncResultHandler);
      } else {
        vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(getEvent.cause())));
      }
    });
  }

  @Override
  public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
    get(k, getEvent -> {
      if (getEvent.succeeded()) {
        if (v.equals(getEvent.result())) {
          delete(k, v, deleteEvent -> forwardAsyncResult(resultHandler, deleteEvent, true));

        } else {
          vertx.runOnContext(event -> resultHandler.handle(Future.completedFuture(false)));
        }
      } else {
        vertx.runOnContext(event -> resultHandler.handle(Future.completedFuture(getEvent.cause())));
      }
    });
  }

  @Override
  public void replace(K k, V v, Handler<AsyncResult<V>> asyncResultHandler) {
    get(k, getEvent -> {
      if (getEvent.succeeded()) {
        final V oldValue = getEvent.result();
        if (oldValue != null) {
          put(k, v, putEvent -> forwardAsyncResult(asyncResultHandler, putEvent, oldValue));
        } else {
          vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture()));
        }
      } else {
        vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(getEvent.cause())));
      }
    });
  }

  @Override
  public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
    get(k, getEvent -> {
      if (getEvent.succeeded()) {
        if (getEvent.result().equals(oldValue)) {
          setData(k, newValue, setEvent -> forwardAsyncResult(resultHandler, setEvent, true));
        } else {
          vertx.runOnContext(e -> resultHandler.handle(Future.completedFuture(false)));
        }
      } else {
        vertx.runOnContext(event -> resultHandler.handle(Future.completedFuture(getEvent.cause())));
      }
    });
  }

  /**
   * just remove parent node which is map path
   */
  @Override
  public void clear(Handler<AsyncResult<Void>> resultHandler) {
    delete(mapPath, null, deleteEvent -> forwardAsyncResult(resultHandler, deleteEvent, null));
  }
}
