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

package org.vertx.java.core.eventbus.impl;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ClusterManager {

	/**
	 * Every eventbus handler has an ID. SubsMap (subscriber map) is a MultiMap which 
	 * maps handler-IDs with server-IDs and thus allows the eventbus to determine where 
	 * to send messages.
	 * 
	 * @param name A unique name by which the the MultiMap can be identified within the cluster. 
	 * @return subscription map
	 */
  SubsMap getSubsMap(String name);

  /**
   * Shutdown the cluster manager and release all resources.
   */
  void close();
}
