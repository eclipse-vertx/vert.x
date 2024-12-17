package io.vertx.core.shareddata;

import java.util.SortedMap;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.shareddata.impl.LocalSortedMapImpl;

/**
 * A {@link LocalMap} which is also sorted.
 */
@VertxGen
public interface LocalSortedMap<K extends Comparable<K>, V> extends LocalMap<K, V>, SortedMap<K, V> {

  /**
   * Creates a new {@link LocalSortedMapImpl} from this {@link LocalSortedMapImpl}
   * which
   * is
   * a view from the portion of this map whose keys range from {@code fromKey},
   * inclusive,
   * to {@code toKey}, exclusive - as defined by
   * {@link SortedMap#subMap(Object, Object)}.
   * 
   * <p>
   * Additionally, the returned map is added to this Vert.x instance's local maps.
   * Therefore,
   * a new name is computed by appending {@code fromKey} and {@code toKey} to this
   * map's name.
   * 
   * <pre>
   * String subMapName = map.name + "_" + fromKey.hashCode() + "-" + toKey.hashCode();
   * </pre>
   * 
   * <p>
   * All invocations with the same {@code fromKey} and {@code toKey} are
   * guaranteed
   * to return the same sub map instance.
   * 
   * @see SortedMap#subMap(Object, Object)
   */
  @Override
  public LocalSortedMap<K, V> subMap(K fromKey, K toKey);

  /**
   * Creates a new {@link LocalSortedMapImpl} from this {@link LocalSortedMapImpl}
   * which
   * is
   * a view from the portion of this map whose keys are strictly less than
   * {@code toKey} - as defined by {@link SortedMap#headMap(Object)}.
   * 
   * <p>
   * All invocations with the same {@code toKey} are guaranteed to return the same
   * sub map instance.
   * 
   * @see SortedMap#headMap(Object)
   * @see #subMap(Object, Object)
   */
  @Override
  public LocalSortedMap<K, V> headMap(K toKey);

  /**
   * Creates a new {@link LocalSortedMapImpl} from this {@link LocalSortedMapImpl}
   * which
   * is
   * a view from the portion of this map whose keys are greater than or equal
   * {@code fromKey} - as defined by {@link SortedMap#tailMap(Object)}.
   * 
   * <p>
   * All invocations with the same {@code fromKey} are guaranteed to return the
   * same sub map instance.
   * 
   * @see SortedMap#tailMap(Object)
   * @see #subMap(Object, Object)
   */
  @Override
  public LocalSortedMap<K, V> tailMap(K fromKey);

}
