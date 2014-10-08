package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import org.apache.curator.framework.CuratorFramework;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Stream.Liu
 */
class ZKAsyncMultiMap<K, V> extends ZKMap<K, V> implements AsyncMultiMap<K, V> {

  ZKAsyncMultiMap(Vertx vertx, CuratorFramework curator, String mapName) {
    super(curator, vertx, "asyncMultiMap", mapName);
  }

  @Override
  public void add(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    checkExists(k, existEvent -> {
          if (existEvent.succeeded()) {
            try {
              String path = keyPath(k, v);
              if (existEvent.result()) {
                setData(path, v, completionHandler);
              } else {
                create(path, v, completionHandler);
              }
            } catch (NullPointerException ex) {
              vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
            }
          } else {
            vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(existEvent.cause())));
          }
        }
    );
  }

  @Override
  public void get(K k, Handler<AsyncResult<ChoosableIterable<V>>> asyncResultHandler) {
    //Since data which be created could be delay in zk cluster, so we try to get it with timer.
    //Could someone give a more graceful idea ?
    vertx.setTimer(50, timerEvent -> {
      try {
        String keyPath = keyPath(k);
        curator.getChildren().inBackground((client, event) -> {
          ChoosableSet<V> choosableSet = new ChoosableSet<>(event.getChildren().size());
          AtomicInteger size = new AtomicInteger(event.getChildren().size());
          if (size.get() == 0) {
            vertx.runOnContext(aVoid -> asyncResultHandler.handle(Future.completedFuture(choosableSet)));
          } else {
            event.getChildren().forEach(partPath -> {
              String valuePath = keyPath + "/" + partPath;
              getData(valuePath, (Class<V>) Object.class, getDataEvent -> {
                if (getDataEvent.succeeded()) {
                  choosableSet.add(getDataEvent.result());
                  if (size.decrementAndGet() == 0) {
                    vertx.runOnContext(aVoid -> asyncResultHandler.handle(Future.completedFuture(choosableSet)));
                  }
                } else {
                  vertx.runOnContext(aVoid -> asyncResultHandler.handle(Future.completedFuture(getDataEvent.cause())));
                }
              });
            });
          }
        }).forPath(keyPath);
      } catch (Exception ex) {
        vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(ex)));
      }
    });
  }

  @Override
  public void remove(K k, V v, Handler<AsyncResult<Boolean>> completionHandler) {
    try {
      String valuePath = keyPath(k, v);
      remove(valuePath, completionHandler);
    } catch (NullPointerException ex) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
    }
  }

  private void remove(String valuePath, Handler<AsyncResult<Boolean>> completionHandler) {
    checkExists(valuePath, existEvent -> {
      if (existEvent.succeeded()) {
        if (existEvent.result()) {
          delete(valuePath, null, deleteEvent -> forwardAsyncResult(completionHandler, deleteEvent, true));
        } else {
          vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(false)));
        }
      } else {
        vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(existEvent.cause())));
      }
    });
  }

  @Override
  public void removeAllForValue(V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      curator.getChildren().inBackground((client, event) -> {
        AtomicInteger size = new AtomicInteger(event.getChildren().size());
        if (event.getChildren().size() == 0) {
          vertx.runOnContext(aVoid -> completionHandler.handle(Future.completedFuture()));
        } else {
          event.getChildren().forEach(partPath -> {
            String valuePath = keyPath(mapPath + "/" + partPath, v);
            remove(valuePath, removeEvent -> {
              if (removeEvent.succeeded() && size.decrementAndGet() == 0) {
                vertx.runOnContext(aVoid -> completionHandler.handle(Future.completedFuture()));
              } else {
                vertx.runOnContext(aVoid -> completionHandler.handle(Future.completedFuture(removeEvent.cause())));
              }
            });
          });
        }
      }).forPath(mapPath);
    } catch (Exception ex) {
      vertx.runOnContext(handler -> completionHandler.handle(Future.completedFuture(ex)));
    }
  }

}
