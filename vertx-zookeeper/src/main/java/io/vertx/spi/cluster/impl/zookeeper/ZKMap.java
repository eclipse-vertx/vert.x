package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.*;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Stream.Liu
 */
abstract class ZKMap<K, V> {

  protected final CuratorFramework curator;
  protected final Vertx vertx;
  protected final AtomicBoolean nodeSplit = new AtomicBoolean(false);
  protected final String mapPath;

  protected ZKMap(CuratorFramework curator, Vertx vertx, String mapType, String mapPath) {
    this.curator = curator;
    this.vertx = vertx;
    this.mapPath = "/" + mapType + "/" + mapPath;
    this.curator.getConnectionStateListenable().addListener((client, newState) -> {
      if (newState == ConnectionState.LOST || newState == ConnectionState.SUSPENDED) {
        nodeSplit.set(true);
      } else {
        nodeSplit.set(false);
      }
    });
  }

  protected String keyPath(K k) {
    if (k == null) {
      throw new NullPointerException("key should not be null.");
    }
    return mapPath + "/" + k.toString();
  }

  protected String keyPath(K k, Object v) {
    if (v == null) {
      throw new NullPointerException("value should not be null.");
    }
    return keyPath(k) + "/" + v.hashCode();
  }

  protected String keyPath(String partPath, Object v) {
    if (v == null) {
      throw new NullPointerException("value should not be null.");
    }
    return partPath + "/" + v.hashCode();
  }

  protected void checkState() throws IllegalStateException {
    if (nodeSplit.get()) {
      throw new IllegalStateException("this zookeeper node have detached from cluster");
    }
  }

  protected byte[] asByte(Object object) throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    new ObjectOutputStream(byteOut).writeObject(object);
    return byteOut.toByteArray();
  }

  protected  <T> T asObject(byte[] bytes, Class<T> clazz) throws Exception {
    ByteArrayInputStream byteIn = new ByteArrayInputStream(bytes);
    ObjectInputStream in = new ObjectInputStream(byteIn);
    T byteObject = (T) in.readObject();
    return byteObject == null ? clazz.newInstance() : byteObject;
  }

  protected <T, E> void forwardAsyncResult(Handler<AsyncResult<T>> completeHandler, AsyncResult<E> asyncResult) {
    if (asyncResult.succeeded()) {
      E result = asyncResult.result();
      if (result == null || result instanceof Void) {
        vertx.runOnContext(event -> completeHandler.handle(Future.completedFuture()));
      } else {
        vertx.runOnContext(event -> completeHandler.handle(Future.completedFuture((T) result)));
      }
    } else {
      vertx.runOnContext(aVoid -> completeHandler.handle(Future.completedFuture(asyncResult.cause())));
    }
  }

  protected <T, E> void forwardAsyncResult(Handler<AsyncResult<T>> completeHandler, AsyncResult<E> asyncResult, T result) {
    if (asyncResult.succeeded()) {
      vertx.runOnContext(event -> completeHandler.handle(Future.completedFuture(result)));
    } else {
      vertx.runOnContext(aVoid -> completeHandler.handle(Future.completedFuture(asyncResult.cause())));
    }
  }

  protected void checkExists(K k, AsyncResultHandler<Boolean> handler) {
    try {
      checkExists(keyPath(k), handler);
    } catch (Exception ex) {
      vertx.runOnContext(event -> handler.handle(Future.completedFuture(ex)));
    }
  }

  protected void checkExists(String path, AsyncResultHandler<Boolean> handler) {
    try {
      checkState();
      curator.checkExists().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.EXISTS) {
          if (event.getStat() == null) {
            vertx.runOnContext(aVoid -> handler.handle(Future.completedFuture(false)));
          } else {
            vertx.runOnContext(aVoid -> handler.handle(Future.completedFuture(true)));
          }
        }
      }).forPath(path);
    } catch (Exception e) {
      vertx.runOnContext(aVoid -> handler.handle(Future.completedFuture(e)));
    }
  }

  protected void create(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      create(keyPath(k), v, completionHandler);
    } catch (Exception ex) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
    }
  }

  protected void create(String path, V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      checkState();
      curator.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).inBackground((cl, el) -> {
        if (el.getType() == CuratorEventType.CREATE) {
          vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture()));
        }
      }).forPath(path, asByte(v));
    } catch (Exception ex) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
    }
  }

  protected void setData(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      setData(keyPath(k), v, completionHandler);
    } catch (Exception ex) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
    }
  }

  protected void setData(String path, V v, Handler<AsyncResult<Void>> completionHandler) {
    try {
      checkState();
      curator.setData().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.SET_DATA) {
          vertx.runOnContext(e -> completionHandler.handle(Future.completedFuture()));
        }
      }).forPath(path, asByte(v));
    } catch (Exception ex) {
      vertx.runOnContext(event -> completionHandler.handle(Future.completedFuture(ex)));
    }
  }

  protected void getData(K k, Class<V> clazz, AsyncResultHandler<V> asyncResultHandler) {
    try {
      getData(keyPath(k), clazz, asyncResultHandler);
    } catch (Exception ex) {
      vertx.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(ex)));
    }
  }

  protected void getData(String path, Class<V> clazz, AsyncResultHandler<V> asyncResultHandler) {
    try {
      checkState();
      curator.getData().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.GET_DATA) {
          if (event.getData() != null) {
            V result = asObject(event.getData(), clazz);
            vertx.runOnContext(handler -> asyncResultHandler.handle(Future.completedFuture(result)));
          } else {
            vertx.runOnContext(handler -> asyncResultHandler.handle(Future.completedFuture()));
          }
        }
      }).forPath(path);
    } catch (Exception e) {
      vertx.runOnContext(aVoid -> asyncResultHandler.handle(Future.completedFuture(e)));
    }
  }

  protected void delete(K k, V v, Handler<AsyncResult<V>> asyncResultHandler) {
    delete(keyPath(k), v, asyncResultHandler);
  }

  protected void delete(String path, V v, Handler<AsyncResult<V>> asyncResultHandler) {
    try {
      checkState();
      curator.delete().deletingChildrenIfNeeded().inBackground((client, event) -> {
        if (event.getType() == CuratorEventType.DELETE) {
          vertx.runOnContext(ea -> asyncResultHandler.handle(Future.completedFuture(v)));
        }
      }).forPath(path);
    } catch (Exception ex) {
      vertx.runOnContext(aVoid -> asyncResultHandler.handle(Future.completedFuture(ex)));
    }
  }

}
