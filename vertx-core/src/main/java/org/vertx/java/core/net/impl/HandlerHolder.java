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

package org.vertx.java.core.net.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.DefaultContext;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerHolder<T> {
  public final DefaultContext context;
  public final Handler<T> handler;

  HandlerHolder(DefaultContext context, Handler<T> handler) {
    this.context = context;
    this.handler = handler;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HandlerHolder that = (HandlerHolder) o;

    if (context != that.context) return false;
    if (handler != null ? !handler.equals(that.handler) : that.handler != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = context.hashCode();
    result = 31 * result + handler.hashCode();
    return result;
  }
}
