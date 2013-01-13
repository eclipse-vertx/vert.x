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

package core.parsetools

import org.vertx.groovy.core.buffer.Buffer
import org.vertx.groovy.core.parsetools.RecordParser
import org.vertx.groovy.testframework.TestUtils

tu = new TestUtils(vertx)
tu.checkContext()

def testDelimited() {

  def lineCount = 0

  def numLines = 3

  def output = { line ->
    if (++lineCount == numLines) {
      tu.testComplete()
    }
  }

  def rp = RecordParser.newDelimited('\n', output)

  def input = "qwdqwdline1\nijijiline2\njline3\n"

  def buffer = new Buffer(input)

  rp.handle(buffer)
}

def testFixed() {

  def chunkCount = 0

  def numChunks = 3

  def chunkSize = 100

  def output = { chunk ->
    tu.azzert(chunk.length == chunkSize)
    if (++chunkCount == numChunks) {
      tu.testComplete()
    }
  }

  def rp = RecordParser.newFixed(chunkSize, output)

  def input = new Buffer(0)
  numChunks.times {
    def buff = tu.generateRandomBuffer(chunkSize)
    input.appendBuffer(buff)
  }

  rp.handle(input)
}


tu.registerTests(this)
tu.appReady()

void vertxStop() {
  tu.unregisterAll()
  tu.appStopped()
}

