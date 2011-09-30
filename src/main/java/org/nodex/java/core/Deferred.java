/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core;


/**
 * <p>Represents something that hasn't happened yet, but will occur when the {@link #execute} method is called.</p>
 * <p>Once it has been executed it behaves identically to a {@link Future}.</p>
 * <p>Instances of Deferred can represent all kinds of deferred operations, e.g. copying a file or getting a value from
 * a Redis server. Since the actual execution of the action is deferred until the execute method is called, it allows
 * multiple instances of Deferred to be composed together into more complex control flows, e.g. using the {@link org.nodex.java.core.composition.Composer} class.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Deferred<T> extends Future<T> {

  /**
   * Execute the deferred operation. If the Deferred has already been executed the call will be ignored.
   * Once the deferred has been executed it behaves like a {@link Future}.
   * @return a reference to this
   */
  Deferred<T> execute();

  /**
   * {@inheritDoc}
   */
  Deferred<T> handler(CompletionHandler<T> handler);
}
