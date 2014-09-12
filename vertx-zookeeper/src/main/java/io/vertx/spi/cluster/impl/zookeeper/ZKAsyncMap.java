package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.state.ConnectionState;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * There have async API in Curator, so we don't need VertxSPI.
 */
class ZKAsyncMap<K, V> implements AsyncMap<K, V> {

  private static final Logger log = LoggerFactory.getLogger(ZKAsyncMap.class);
  private final String mapPath;
  private final Vertx vertx;
  private final AtomicBoolean nodeSplit = new AtomicBoolean(false);
  private final CuratorFramework curator;
  private Map<String, PathChildrenCache> pathCache = new ConcurrentHashMap<>();

  ZKAsyncMap(Vertx vertx, CuratorFramework curator, String mapName) {
    this.vertx = vertx;
    this.curator = curator;
    this.mapPath = "/asyncMap/" + mapName;
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

  private PathChildrenCache getPathChildrenCache() throws Exception {
    PathChildrenCache pathChildrenCache = pathCache.get(mapPath);
    if (pathChildrenCache == null) {
      pathChildrenCache = new PathChildrenCache(curator, mapPath, true);
      pathCache.put(mapPath, pathChildrenCache);
      pathChildrenCache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
    }
    return pathChildrenCache;
  }

  private byte[] asByte(Object object) throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    new ObjectOutputStream(byteOut).writeObject(object);
    return byteOut.toByteArray();
  }

  //TODO change parameter to bytes
  private <T> T asSet(ChildData childData, Class<T> clazz) throws Exception {
    ByteArrayInputStream byteIn = new ByteArrayInputStream(childData.getData());
    ObjectInputStream in = new ObjectInputStream(byteIn);
    T byteObject = (T) in.readObject();
    return byteObject == null ? clazz.newInstance() : byteObject;
  }

  @Override
  public void get(K k, Handler<AsyncResult<V>> asyncResultHandler) {
    try {
      checkState();
      PathChildrenCache pathChildrenCache = getPathChildrenCache();
      ChildData childData = pathChildrenCache.getCurrentData(keyPath(k));
      if (childData != null) {
        V result = (V) asSet(childData, Object.class);
        vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(result)));
      } else {
        vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(null)));
      }
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(e)));
    }
  }

  @Override
  public void put(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      checkState();
      curator.create().creatingParentsIfNeeded().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.CREATE) {
          vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture()));
        }
      }).forPath(keyPath(k), asByte(v));
    } catch (Exception e) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(e)));
    }
  }

  @Override
  public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> completionHandler) {
    String keyPath = keyPath(k);
    try {
      checkState();
      curator.checkExists().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.EXISTS && event.getStat() == null) {
          curator.create().creatingParentsIfNeeded().inBackground((anotherClient, anotherEvent) -> {
            if (anotherEvent.getType() == CuratorEventType.CREATE) {
              vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture(v)));
            }
          }).forPath(keyPath, asByte(v));
        }
      }).forPath(keyPath);
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(e)));
    }
  }

  @Override
  public void remove(K k, Handler<AsyncResult<V>> asyncResultHandler) {
    try {
      checkState();
      get(k, e -> {
        if (e.succeeded()) {
          try {
            curator.delete().inBackground((client, event) -> {
              if (event.getType() == CuratorEventType.DELETE) {
                vertx.runOnContext(ea -> asyncResultHandler.handle(Future.completedFuture(e.result())));
              }
            }).forPath(keyPath(k));
          } catch (Exception ex) {
            vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(ex)));
          }
        } else {
          vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(e.cause())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(e)));
    }
  }

  @Override
  public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
    String keyPath = keyPath(k);
    try {
      checkState();
      get(k, event -> {
        if (event.succeeded()) {
          if (v.equals(event.result())) {
            try {
              curator.delete().inBackground((anotherClient, anotherEvent) -> {
                if (anotherEvent.getType() == CuratorEventType.DELETE) {
                  vertx.runOnContext(e -> resultHandler.handle(Future.completedFuture(Boolean.TRUE)));
                }
              }).forPath(keyPath);
            } catch (Exception ex) {
              vertx.runOnContext(e -> resultHandler.handle(Future.completedFuture(ex)));
            }
          } else {
            vertx.runOnContext(e -> resultHandler.handle(Future.completedFuture(Boolean.FALSE)));
          }
        } else {
          vertx.runOnContext(e -> resultHandler.handle(Future.completedFuture(event.cause())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> resultHandler.handle(Future.completedFuture(e)));
    }
  }

  @Override
  public void replace(K k, V v, Handler<AsyncResult<V>> asyncResultHandler) {
    String keyPath = keyPath(k);
    try {
      checkState();
      curator.create().creatingParentsIfNeeded().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.CREATE) {
          vertx.runOnContext(e -> asyncResultHandler.handle(Future.completedFuture(v)));
        }
      }).forPath(keyPath, asByte(v));
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(e)));
    }
  }

  @Override
  public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
    String keyPath = keyPath(k);
    try {
      checkState();
      curator.getData().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.GET_DATA) {
          ByteArrayInputStream byteIn = new ByteArrayInputStream(event.getData());
          ObjectInputStream in = new ObjectInputStream(byteIn);
          if (in.readObject().equals(oldValue)) {
            curator.setData().inBackground((anotherClient, anotherEvent) -> {
              if (anotherEvent.getType() == CuratorEventType.SET_DATA) {
                vertx.runOnContext(e -> resultHandler.handle(Future.completedFuture(Boolean.TRUE)));
              }
            }).forPath(keyPath, asByte(newValue));
          } else {
            vertx.runOnContext(e -> resultHandler.handle(Future.completedFuture(Boolean.FALSE)));
          }
        }
      }).forPath(keyPath);
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> resultHandler.handle(Future.completedFuture(e)));
    }
  }

  /**
   * just remove parent node which is map path
   */
  @Override
  public void clear(Handler<AsyncResult<Void>> resultHandler) {
    try {
      checkState();
      curator.delete().deletingChildrenIfNeeded().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.DELETE) {
          vertx.runOnContext(e -> resultHandler.handle(Future.completedFuture()));
        }
      }).forPath(mapPath);
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> resultHandler.handle(Future.completedFuture(e)));
    }
  }
}
