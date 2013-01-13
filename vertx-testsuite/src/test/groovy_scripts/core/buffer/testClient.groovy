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

package core.buffer

import org.vertx.groovy.core.buffer.Buffer
import org.vertx.groovy.testframework.TestUtils

tu = new TestUtils(vertx)

def testPutAtGetAtByte() {
  def buff = new Buffer()
  def b = (byte)12
  buff[10] = b
  tu.azzert(buff[10] == b)
  tu.testComplete()
}

def testPutAtInt() {
  def buff = new Buffer()
  def i = 1233
  buff[10] = i
  tu.azzert(buff.getInt(10) == i)
  tu.testComplete()
}

def testPutAtLong() {
  def buff = new Buffer()
  def l = 1233l
  buff[10] = l
  tu.azzert(buff.getLong(10) == l)
  tu.testComplete()
}

def testPutAtShort() {
  def buff = new Buffer()
  def s = (short)123
  buff[10] = s
  tu.azzert(buff.getShort(10) == s)
  tu.testComplete()
}

def testPutAtDouble() {
  def buff = new Buffer()
  def d = 123.123d
  buff[10] = d
  tu.azzert(buff.getDouble(10) == d)
  tu.testComplete()
}

def testPutAtFloat() {
  def buff = new Buffer()
  def f = 123.123f
  buff[10] = f
  tu.azzert(buff.getFloat(10) == f)
  tu.testComplete()
}

def testPutAtBuffer() {
  def buff = new Buffer()
  def b = TestUtils.generateRandomBuffer(10)
  buff[10] = b
  tu.azzert(TestUtils.buffersEqual(buff.getBuffer(10, 20), b))
  tu.testComplete()
}

tu.registerTests(this)
tu.appReady()

void vertxStop() {
  tu.unregisterAll()
  tu.appStopped()
}