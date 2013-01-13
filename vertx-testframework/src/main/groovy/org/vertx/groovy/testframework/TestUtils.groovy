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
package org.vertx.groovy.testframework

import org.vertx.groovy.core.Vertx
import org.vertx.groovy.core.buffer.Buffer
import org.vertx.java.core.Handler

class TestUtils extends org.vertx.java.testframework.TestUtils {

  TestUtils(Vertx vertx) {
    super(vertx.toJavaVertx())
  }

  // Provide a version of register which takes a closure
  def register(testName, handler) {
    super.register(testName, handler as Handler)
  }

  static Buffer generateRandomBuffer(int length) {
    new Buffer(org.vertx.java.testframework.TestUtils.generateRandomBuffer(length, false, (byte) 0))
  }
}
