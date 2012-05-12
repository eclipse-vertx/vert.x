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

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * @author pidster
 *
 */
public interface SharedData {

	/**
	 * Return a {@code Map} with the specific {@code name}. All invocations of this method with the same value of {@code name}
	 * are guaranteed to return the same {@code Map} instance. <p>
	 */
	public abstract <K, V> ConcurrentMap<K, V> getMap(String name);

	/**
	 * Return a {@code Set} with the specific {@code name}. All invocations of this method with the same value of {@code name}
	 * are guaranteed to return the same {@code Set} instance. <p>
	 */
	public abstract <E> Set<E> getSet(String name);

	/**
	 * Remove the {@code Map} with the specifiec {@code name}.
	 */
	public abstract boolean removeMap(Object name);

	/**
	 * Remove the {@code Set} with the specifiec {@code name}.
	 */
	public abstract boolean removeSet(Object name);

}