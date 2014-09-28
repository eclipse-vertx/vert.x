package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.framework.state.ConnectionState;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 *
 */
class ZKAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {

  private final CuratorFramework curator;
  private final String mapPath;
  private final Vertx vertx;
  private final AtomicBoolean nodeSplit = new AtomicBoolean(false);

  ZKAsyncMultiMap(Vertx vertx, CuratorFramework curator, String mapName) {
    this.vertx = vertx;
    this.mapPath = "/asyncMultiMap/" + mapName;
    this.curator = curator;
    this.curator.getConnectionStateListenable().addListener((client, newState) -> {
      if (newState == ConnectionState.LOST || newState == ConnectionState.SUSPENDED) {
        nodeSplit.set(true);
      } else {
        nodeSplit.set(false);
      }
    });
  }

  private String keyPath(K k) {
    return mapPath + "/" + k.toString();
  }

  private void checkState() throws IllegalStateException {
    if (nodeSplit.get()) {
      throw new IllegalStateException("this zookeeper node have detached from cluster");
    }
  }

  private byte[] asByte(Object object) throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    new ObjectOutputStream(byteOut).writeObject(object);
    return byteOut.toByteArray();
  }

  private <T> T asObject(byte[] bytes, Class<T> clazz) throws Exception {
    ByteArrayInputStream byteIn = new ByteArrayInputStream(bytes);
    ObjectInputStream in = new ObjectInputStream(byteIn);
    T byteObject = (T) in.readObject();
    return byteObject == null ? clazz.newInstance() : byteObject;
  }

  private void checkExists(String path, Handler<Boolean> handler) throws Exception {
    curator.checkExists().inBackground((client, event) -> {
      if (event.getType() == CuratorEventType.EXISTS) {
        if (event.getStat() == null) {
          vertx.runOnContext(aVoid -> handler.handle(false));
        } else {
          vertx.runOnContext(aVoid -> handler.handle(true));
        }
      }
    }).forPath(path);
  }


  private void ensureData(String keyPath, Handler<AsyncResult<Void>> completionHandler) {
    try {
      curator.getData().inBackground((client2, event2) -> {
        if (event2.getData() != null)
          vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture()));
        else
          ensureData(keyPath, completionHandler);
      }).forPath(keyPath);
    } catch (Exception ex) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
    }
  }

  @Override
  public void add(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      checkState();
      String keyPath = keyPath(k);
      curator.checkExists().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.EXISTS) {
          if (event.getStat() != null) {
            curator.getData().inBackground((c, el) -> {
              if (el.getType() == CuratorEventType.GET_DATA) {
                Set<V> set = asObject(el.getData(), HashSet.class);
                set.add(v);
                curator.setData().inBackground((callbackClient, ea) -> {
                  if (ea.getType() == CuratorEventType.SET_DATA) {
                    vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture()));
                  }
                }).forPath(keyPath, asByte(set));
              }
            }).forPath(keyPath);
          } else {
            Set<V> set = new HashSet<>();
            set.add(v);
            byte[] bytes = asByte(set);
            curator.create().creatingParentsIfNeeded().inBackground((client1, event1) -> {
              if (event1.getType() == CuratorEventType.CREATE) {
                vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture()));
              }
            }).forPath(keyPath, bytes);
          }
        }
      }).forPath(keyPath);
    } catch (Exception ex) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
    }
  }


  private void delayGet(String keyPath, int count, Handler<AsyncResult<ChoosableIterable<V>>> asyncResultHandler) {
    if (count <= 5) {
      vertx.setTimer(20 * count, timerEvent1 -> {
        try {
          checkState();
          checkExists(keyPath, exist -> {
            if (exist) {
              try {
                curator.getData().inBackground((client, event) -> {
                  HashSet set = asObject(event.getData(), HashSet.class);
                  ChoosableIterable<V> choosableIterable = new ChoosableSet<V>(set);
                  vertx.runOnContext(e -> asyncResultHandler.handle(Future.completedFuture(choosableIterable)));
                }).forPath(keyPath);
              } catch (Exception ex) {
                vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(ex)));
              }
            } else {
              delayGet(keyPath, count + 1, asyncResultHandler);
            }
          });
        } catch (Exception e) {
          vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(e)));
        }
      });
    } else {
      vertx.runOnContext(e -> asyncResultHandler.handle(Future.completedFuture()));
    }
  }

  @Override
  public void get(K k, Handler<AsyncResult<ChoosableIterable<V>>> asyncResultHandler) {
    //since data which be created could be delay in zk cluster, so we try to get it with timer.
    delayGet(keyPath(k), 1, asyncResultHandler);
  }


  @Override
  public void remove(K k, V v, Handler<AsyncResult<Boolean>> completionHandler) {
    checkState();
    String keyPath = keyPath(k);
    try {
      checkExists(keyPath, exist -> {
        if (exist) {
          try {
            curator.getData().inBackground((client, event) -> {
              HashSet set = asObject(event.getData(), HashSet.class);
              if (set.contains(v)) {
                set.remove(v);
                curator.setData().inBackground((c, ev) -> {
                  if (ev.getType() == CuratorEventType.SET_DATA) {
                    vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture(Boolean.TRUE)));
                  }
                }).forPath(keyPath, asByte(set));
              } else {
                vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture(Boolean.FALSE)));
              }
            }).forPath(keyPath);
          } catch (Exception ex) {
            vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
          }
        } else {
          vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture(Boolean.FALSE)));
        }
      });
    } catch (Exception ex) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
    }
  }

  @Override
  public void removeAllForValue(V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      checkState();
      curator.getChildren().inBackground((client, event) -> {
        final int[] count = {event.getChildren().size()};
        for (String path : event.getChildren()) {
          String keyPath = keyPath((K) path);
          try {
            curator.getData().inBackground((c, el) -> {
              if (el.getType() == CuratorEventType.GET_DATA) {
                count[0]--;
                HashSet result = asObject(el.getData(), HashSet.class);
                if (result.contains(v)) {
                  result.remove(v);
                  //update
                  curator.setData().inBackground((anotherC, ele) -> {
                    if (ele.getType() == CuratorEventType.SET_DATA && count[0] == 0) {
                      vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture()));
                    }
                  }).forPath(keyPath, asByte(result));
                }
              }
            }).forPath(keyPath);
          } catch (Exception ex) {
            vertx.runOnContext(handler -> completionHandler.handle(Future.completedFuture(ex)));
          }
        }
      }).forPath(mapPath);
    } catch (Exception e) {
      vertx.runOnContext(handler -> completionHandler.handle(Future.completedFuture(e)));
    }
  }

}
