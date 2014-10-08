package io.vertx.spi.cluster.impl.zookeeper;

import com.google.common.collect.Maps;
import io.vertx.core.VertxException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
class ZKSyncMap<K, V> extends ZKMap<K, V> implements Map<K, V> {

  ZKSyncMap(CuratorFramework curator, String mapName) {
    super(curator, null, "syncMap", mapName);
  }

  @Override
  public int size() {
    try {
      checkState();
      return curator.getChildren().forPath(mapPath).size();
    } catch (Exception e) {
      throw new VertxException(e.getMessage());
    }
  }

  @Override
  public boolean isEmpty() {
    try {
      checkState();
      return curator.getChildren().forPath(mapPath).isEmpty();
    } catch (Exception e) {
      throw new VertxException(e.getMessage());
    }
  }

  @Override
  public boolean containsKey(Object key) {
    try {
      checkState();
      return curator.getChildren().forPath(mapPath).stream().anyMatch(e -> e.equals(key));
    } catch (Exception e) {
      throw new VertxException(e.getMessage());
    }
  }

  @Override
  public boolean containsValue(Object value) {
    try {
      checkState();
      return curator.getChildren().forPath(mapPath).stream().anyMatch(e -> {
        try {
          byte[] bytes = curator.getData().forPath(keyPath((K) e));
          KeyValue<K, V> keyValue = asObject(bytes, KeyValue.class);
          return keyValue.getValue().equals(value);
        } catch (Exception ex) {
          throw new VertxException(ex.getMessage());
        }
      });
    } catch (Exception e) {
      throw new VertxException(e.getMessage());
    }
  }

  @Override
  public V get(Object key) {
    try {
      checkState();
      String keyPath = keyPath((K) key);
      if (null == curator.checkExists().forPath(keyPath)) {
        return null;
      } else {
        KeyValue<K, V> keyValue = asObject(curator.getData().forPath(keyPath), KeyValue.class);
        return keyValue.getValue();
      }
    } catch (Exception e) {
      if (!(e instanceof KeeperException.NodeExistsException)) {
        throw new VertxException(e.getMessage());
      }
    }
    return null;
  }

  @Override
  public V put(K key, V value) {
    try {
      checkState();
      String keyPath = keyPath(key);
      KeyValue<K, V> keyValue = new KeyValue<>(key, value);
      byte[] valueBytes = asByte(keyValue);
      if (get(key) != null) {
        curator.setData().forPath(keyPath, valueBytes);
      } else {
        curator.create().creatingParentsIfNeeded().forPath(keyPath, valueBytes);
      }
      return value;
    } catch (Exception e) {
      e.printStackTrace();
      throw new VertxException(e.getMessage());
    }
  }

  @Override
  public V remove(Object key) {
    try {
      checkState();
      V result = get(key);
      if (result != null) curator.delete().deletingChildrenIfNeeded().forPath(keyPath((K) key));
      return result;
    } catch (Exception e) {
      throw new VertxException(e.getMessage());
    }
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    checkState();
    m.entrySet().stream().forEach(entry -> put(entry.getKey(), entry.getValue()));
  }

  @Override
  public void clear() {
    try {
      checkState();
      curator.delete().deletingChildrenIfNeeded().forPath(mapPath);
      curator.create().creatingParentsIfNeeded().forPath(mapPath);
    } catch (Exception e) {
      throw new VertxException(e.getMessage());
    }
  }

  @Override
  public Set<K> keySet() {
    try {
      checkState();
      return curator.getChildren().forPath(mapPath).stream().map(e -> {
        try {
          KeyValue<K, V> keyValue = asObject(curator.getData().forPath(keyPath((K) e)), KeyValue.class);
          return keyValue.getKey();
        } catch (Exception ex) {
          throw new VertxException(ex.getMessage());
        }
      }).collect(Collectors.toSet());
    } catch (Exception ex) {
      throw new VertxException(ex.getMessage());
    }
  }

  @Override
  public Collection<V> values() {
    try {
      checkState();
      return curator.getChildren().forPath(mapPath).stream()
          .map(e -> {
                try {
                  KeyValue<K, V> keyValue = asObject(curator.getData().forPath(keyPath((K) e)), KeyValue.class);
                  return keyValue.getValue();
                } catch (Exception ex) {
                  throw new VertxException(ex.getMessage());
                }
              }
          ).collect(Collectors.toSet());
    } catch (Exception e) {
      throw new VertxException(e.getMessage());
    }
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    checkState();
    return keySet().stream().map(k -> {
      V v = get(k);
      return Maps.immutableEntry(k, v);
    }).collect(Collectors.toSet());
  }

  private static class KeyValue<K, V> implements Serializable {
    private K key;
    private V value;

    private KeyValue(K key, V value) {
      this.key = key;
      this.value = value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }
  }

}
