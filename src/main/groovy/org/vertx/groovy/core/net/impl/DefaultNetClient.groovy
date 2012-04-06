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

package org.vertx.groovy.core.net.impl

import org.vertx.groovy.core.net.NetClient
import org.vertx.java.core.impl.VertxInternal

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class DefaultNetClient extends NetClient {

  DefaultNetClient(VertxInternal vertx, Map props = null) {
    jClient = new org.vertx.java.core.net.impl.DefaultNetClient(vertx)
    if (props != null) {
      props.each { k, v ->
        setProperty(k, v)
      }
    }
  }
}
