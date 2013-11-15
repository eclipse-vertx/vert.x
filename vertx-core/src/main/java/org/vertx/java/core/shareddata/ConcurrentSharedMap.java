/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
