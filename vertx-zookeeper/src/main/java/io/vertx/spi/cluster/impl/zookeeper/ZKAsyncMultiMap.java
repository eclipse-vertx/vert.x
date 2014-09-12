package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.state.ConnectionState;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
class ZKAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {

  private static final Logger log = LoggerFactory.getLogger(ZKAsyncMultiMap.class);
  private final CuratorFramework curator;
  private final String mapPath;
  private final Vertx vertx;
  private final AtomicBoolean nodeSplit = new AtomicBoolean(false);

  private Map<String, PathChildrenCache> pathCache = new ConcurrentHashMap<>();

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

  private <T> T asSet(ChildData childData, Class<T> clazz) throws Exception {
    ByteArrayInputStream byteIn = new ByteArrayInputStream(childData.getData());
    ObjectInputStream in = new ObjectInputStream(byteIn);
    T byteObject = (T) in.readObject();
    return byteObject == null ? clazz.newInstance() : byteObject;
  }

  @Override
  public void add(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      checkState();
      String keyPath = keyPath(k);
      PathChildrenCache pathChildrenCache = getPathChildrenCache();

      curator.checkExists().inBackground((client, event) -> {
        Set<V> set = new HashSet<>();
        if (event.getType() == CuratorEventType.EXISTS && event.getStat() == null) {
          //create
          set.add(v);
          curator.create().creatingParentsIfNeeded().inBackground((client1, event1) -> {
            if (event1.getType() == CuratorEventType.CREATE) {
              vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture()));
            }
          }).forPath(keyPath, asByte(set));
        } else {
          //setData
          set = asSet(pathChildrenCache.getCurrentData(keyPath), HashSet.class);
          set.add(v);
          curator.setData().inBackground((callbackClient, ea) -> {
            if (ea.getType() == CuratorEventType.SET_DATA) {
              vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture()));
            }
          }).forPath(keyPath, asByte(set));
        }
      }).forPath(keyPath);
    } catch (Exception e) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(e)));
    }
  }

  @Override
  public void get(K k, Handler<AsyncResult<ChoosableIterable<V>>> asyncResultHandler) {
    try {
      checkState();
      PathChildrenCache pathChildrenCache = getPathChildrenCache();
      ChildData childData = pathChildrenCache.getCurrentData(keyPath(k));
      if (childData != null) {
        HashSet result = asSet(childData, HashSet.class);
        ChoosableIterable<V> choosableIterable = new ChoosableSet<V>(result);
        vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(choosableIterable)));
      } else {
        vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(null)));
      }
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(e)));
    }
  }

  @Override
  public void remove(K k, V v, Handler<AsyncResult<Boolean>> completionHandler) {
    try {
      checkState();
      String keyPath = keyPath(k);
      PathChildrenCache pathChildrenCache = getPathChildrenCache();

      HashSet set = new HashSet<>();
      ChildData childData = pathChildrenCache.getCurrentData(keyPath);
      if (childData != null) {
        set = asSet(childData, HashSet.class);
      }

      if (set.contains(v)) {
        set.remove(v);
        curator.setData().inBackground((client, event) -> {
          if (event.getType() == CuratorEventType.SET_DATA) {
            vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture(Boolean.TRUE)));
          }
        }).forPath(keyPath, asByte(set));
      } else {
        vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture(Boolean.FALSE)));
      }
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(e)));
    }
  }

  @Override
  public void removeAllForValue(V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      checkState();
      PathChildrenCache pathChildrenCache = getPathChildrenCache();

      Map<String, Set<V>> updateResult = new HashMap<>();
      for (ChildData childData : pathChildrenCache.getCurrentData()) {
        HashSet result = asSet(childData, HashSet.class);
        if (result.contains(v)) {
          result.remove(v);
          updateResult.put(childData.getPath(), result);
        }
      }

      final int[] count = {updateResult.size()};
      for (Map.Entry<String, Set<V>> kv : updateResult.entrySet()) {
        curator.setData().inBackground((client, event) -> {
          if (event.getType() == CuratorEventType.SET_DATA && --count[0] == 0) {
            vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture()));
          }
        }).forPath(kv.getKey(), asByte(kv.getValue()));
      }
    } catch (Exception e) {
      log.error(e);
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(e)));
    }
  }

}
