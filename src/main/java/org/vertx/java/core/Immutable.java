/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core;

/**
 * <p>This is a marker interface which, if implement by a class, allows instances of that class to be stored
 * in {@link org.vertx.java.core.shared.SharedData} structures.</p>
 *
 * <p>Use this with caution, only mark classes with {@code Immutable} if you really know they are safe to be used
 * concurrently by multiple threads. The shared data structures only take certain types for a reason - to stop you
 * having to worry about concurrency.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Immutable {
}
