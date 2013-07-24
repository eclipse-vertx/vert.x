/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 *
 */
package org.vertx.java.core.shareddata;

import java.util.concurrent.ConcurrentMap;

/**
 * ConcurrentSharedMap has very similar semantics as ConcurrentMap with the difference that any updates made to
 * the collections returned from keySet, valueSet and entrySet methods do not change the keys and values in the
 * underlying Map.<p>
 * This is because the Map can contain mutable data such as Buffer and byte[] objects so we must copy such elements
 * before they are returned to you. This prevents a situation where the same entry entry is being updated
 * concurrently by more than one thread, which could lead to race conditions.<p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ConcurrentSharedMap<K, V> extends ConcurrentMap<K, V> {
}
