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

package org.vertx.java.core.shareddata;

/**
 * Marker interface.
 *
 * If a class implements this it means it can be put into shared data maps
 * and sets.
 *
 * All classes that implement this MUST be threadsafe.
 *
 * Use this class with caution.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Shareable {
}
