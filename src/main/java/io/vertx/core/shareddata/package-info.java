/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/**
 * == Using Shared Data with Vert.x
 *
 * Shared data contains functionality that allows you to safely share data between different parts of your application,
 * or different applications in the same Vert.x instance or across a cluster of Vert.x instances.
 *
 * Shared data includes local shared maps, distributed, cluster-wide maps, asynchronous cluster-wide locks and
 * asynchronous cluster-wide counters.
 *
 * === Local shared maps
 *
 * {@link io.vertx.core.shareddata.LocalMap Local shared maps} allow you to share data safely between different event
 * loops (e.g. different verticles) in the same Vert.x instance.
 *
 * Local shared maps only allow certain data types to be used as keys and values. Those types must either be immutable,
 * or certain other types that can be copied like {@link io.vertx.core.buffer.Buffer}. In the latter case the key/value
 * will be copied before putting it in the map.
 *
 * This way we can ensure there is no _shared access to mutable state_ between different threads in your Vert.x application
 * so you don't have to worry about protecting that state by synchronising access to it.
 *
 * Here's an example of using a shared local map:
 *
 * [source,$lang]
 * ----
 * {@link examples.SharedDataExamples#example1}
 * ----
 *
 * === Cluster-wide asynchronous maps
 *
 * Cluster-wide asynchronous maps allow data to be put in the map from any node of the cluster and retrieved from any
 * other node.
 *
 * This makes them really useful for things like storing session state in a farm of servers hosting a Vert.x web
 * application.
 *
 * You get an instance of {@link io.vertx.core.shareddata.AsyncMap} with
 * {@link io.vertx.core.shareddata.SharedData#getClusterWideMap(java.lang.String, io.vertx.core.Handler)}.
 *
 * Getting the map is asynchronous and the result is returned to you in the handler that you specify. Here's an example:
 *
 * [source,$lang]
 * ----
 * {@link examples.SharedDataExamples#example2}
 * ----
 *
 * ==== Putting data in a map
 *
 * You put data in a map with {@link io.vertx.core.shareddata.AsyncMap#put(java.lang.Object, java.lang.Object, io.vertx.core.Handler)}.
 *
 * The actual put is asynchronous and the handler is notified once it is complete:
 *
 * [source,$lang]
 * ----
 * {@link examples.SharedDataExamples#example3}
 * ----
 *
 * ==== Getting data from a map
 *
 * You get data from a map with {@link io.vertx.core.shareddata.AsyncMap#get(java.lang.Object, io.vertx.core.Handler)}.
 *
 * The actual get is asynchronous and the handler is notified with the result some time later
 *
 * [source,$lang]
 * ----
 * {@link examples.SharedDataExamples#example4}
 * ----
 *
 * ===== Other map operations
 *
 * You can also remove entries from an asynchronous map, clear them and get the size.
 *
 * See the {@link io.vertx.core.shareddata.AsyncMap API docs} for more information.
 *
 * === Cluster-wide locks
 *
 * {@link io.vertx.core.shareddata.Lock Cluster wide locks} allow you to obtain exclusive locks across the cluster -
 * this is useful when you want to do something or access a resource on only one node of a cluster at any one time.
 *
 * Cluster wide locks have an asynchronous API unlike most lock APIs which block the calling thread until the lock
 * is obtained.
 *
 * To obtain a lock use {@link io.vertx.core.shareddata.SharedData#getLock(java.lang.String, io.vertx.core.Handler)}.
 *
 * This won't block, but when the lock is available, the handler will be called with an instance of {@link io.vertx.core.shareddata.Lock},
 * signifying that you now own the lock.
 *
 * While you own the lock no other caller, anywhere on the cluster will be able to obtain the lock.
 *
 * When you've finished with the lock, you call {@link io.vertx.core.shareddata.Lock#release()} to release it, so
 * another caller can obtain it.
 *
 * [source,$lang]
 * ----
 * {@link examples.SharedDataExamples#example5}
 * ----
 *
 * You can also get a lock with a timeout. If it fails to obtain the lock within the timeout the handler will be called
 * with a failure:
 *
 * [source,$lang]
 * ----
 * {@link examples.SharedDataExamples#example6}
 * ----
 *
 * === Cluster-wide counters
 *
 * It's often useful to maintain an atomic counter across the different nodes of your application.
 *
 * You can do this with {@link io.vertx.core.shareddata.Counter}.
 *
 * You obtain an instance with {@link io.vertx.core.shareddata.SharedData#getCounter(java.lang.String, io.vertx.core.Handler)}:
 *
 * [source,$lang]
 * ----
 * {@link examples.SharedDataExamples#example7}
 * ----
 *
 * Once you have an instance you can retrieve the current count, atomically increment it, decrement and add a value to
 * it using the various methods.
 *
 * See the {@link io.vertx.core.shareddata.Counter API docs} for more information.
 *
 *
 */
@Document(fileName = "shareddata.adoc")
package io.vertx.core.shareddata;

import io.vertx.docgen.Document;

