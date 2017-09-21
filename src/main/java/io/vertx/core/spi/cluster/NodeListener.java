/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.spi.cluster;

/**
 *
 * A NodeListener is called when nodes join or leave the cluster
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface NodeListener {

  /**
   * A node has been added
   * @param nodeID The unique ID of the node
   */
  void nodeAdded(String nodeID);

  /**
   * A node has left
   * @param nodeID  The unique ID of the node
   */
  void nodeLeft(String nodeID);
}
