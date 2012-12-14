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

package core.timer

import org.vertx.groovy.framework.TestUtils

tu = new TestUtils(vertx)
tu.checkContext()

def testOneOff() {
  def count = 0
  def id
  id = vertx.setTimer(1, { timerID ->
    tu.checkContext()
    tu.azzert(id == timerID)
    tu.azzert(count == 0)
    count++
    setEndTimer()
  })
}

def testPeriodic() {
  def numFires = 10
  def delay = 100
  def count = 0
  def id
  id = vertx.setPeriodic(delay, { timerID ->
    tu.checkContext()
    tu.azzert(id == timerID)
    count++
    if (count == numFires) {
      vertx.cancelTimer(timerID)
      setEndTimer()
    }
    if (count > numFires) {
      tu.azzert(false, "Fired too many times")
    }
  })
}

def testCancelledOK() {
  def id
  id = vertx.setTimer(100, { timerID ->
    tu.azzert(false, "Should not be called")
  })
  def cancelled = vertx.cancelTimer(id)
  tu.azzert(cancelled)
  tu.testComplete()
}

def testCancelledNotOK() {
  def id
  id = vertx.setTimer(1, { timerID ->
    tu.azzert(id == timerID)
  })
  vertx.setTimer(100, {
    def cancelled = vertx.cancelTimer(id)
    tu.azzert(!cancelled)
    tu.testComplete()
  })

}

def setEndTimer() {
  vertx.setTimer(10, {
    tu.testComplete()
  })
}

tu.registerTests(this)
tu.appReady()

void vertxStop() {
  tu.unregisterAll()
  tu.appStopped()
}

