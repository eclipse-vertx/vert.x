/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.spi.cluster;


import java.util.List;
import java.util.Map;

/**
 *
 * A cluster provider for Vert.x must implement this interface.
 *
 * In order for HA to work correctly, all implementations of this interface MUST be implemented such that:
 *
 * 1. Whenever a node joins or leaves the cluster the registered NodeListener (if any) MUST be called with the
 * appropriate join or leave event.
 * 2. For all nodes that are part of the cluster, the registered NodeListener MUST be called with the exact same
 * sequence of join and leave events on all nodes.
 * 3. For any particular join or leave event that is handled in any NodeListener, anywhere in the cluster, the List
 * of nodes returned by getNodes must be identical.
 * 4. All of the methods in the implementation must return immediately, i.e. they must not block while the operation
 * is being executed. If the underlying implementation does block, that can be wrapped in an
 * {@link org.vertx.java.core.spi.Action} instance and executed using the method
 * {@link org.vertx.java.core.spi.VertxSPI#executeBlocking(org.vertx.java.core.spi.Action, org.vertx.java.core.Handler)}
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ClusterManager {

  /**
   * Return an async multi-map for the given name
   */
  <K, V> AsyncMultiMap<K, V> getAsyncMultiMap(String name);

  /**
   * Return an async map for the given name
   */
  <K, V> AsyncMap<K, V> getAsyncMap(String name);

  /**
   * Return a synchronous map for the given name
   */
  <K, V> Map<K, V> getSyncMap(String name);

  /**
   * Return the unique node ID for this node
   */
  String getNodeID();

  /**
   * Return a list of node IDs corresponding to the nodes in the cluster
   *
   */
  List<String> getNodes();

  /**
   * Set a listener that will be called when a node joins or leaves the cluster.
   *
   * @param listener
   */
  void nodeListener(NodeListener listener);

  /**
   * Join the cluster
   */
  void join();

  /**
   * Leave the cluster
   */
  void leave();
}
