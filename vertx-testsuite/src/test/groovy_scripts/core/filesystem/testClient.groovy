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

package core.filesystem

import org.vertx.groovy.core.streams.Pump
import org.vertx.groovy.testframework.TestUtils

tu = new TestUtils(vertx)
tu.checkContext()

fs = vertx.fileSystem

fileDir = "js-test-output"

// More tests needed

def testCopy() {
  def from = fileDir + "/foo.tmp"
  def to = fileDir + "/bar.tmp"
  def content = "some-data"
  fs.writeFile(from, content, {
    fs.copy(from, to, { ares ->
      tu.azzert(ares.exception == null)
      fs.readFile(to, { ares2 ->
        tu.azzert(ares2.exception == null)
        tu.azzert(ares2.result.toString() == content)
        tu.testComplete()
      })
    })
  })
}

def testMove() {
  def from = fileDir + "/foo.tmp"
  def to = fileDir + "/bar.tmp"
  def content = "some-data"
  fs.writeFile(from, content, {
    fs.move(from, to, { ares->
      tu.azzert(ares.exception == null)
      fs.readFile(to, { ares2 ->
        tu.azzert(ares2.exception == null)
        tu.azzert(ares2.result.toString() == content)
        fs.exists(from, { ares3 ->
          tu.azzert(ares3.exception == null)
          tu.azzert(!ares.result)
          tu.testComplete()
        })
      })
    })
  })
}

def testReadDir() {
  def file1 = fileDir + "/foo.tmp"
  def file2 = fileDir + "/bar.tmp"
  def file3 = fileDir + "/baz.tmp"
  def content = "some-data"
  fs.writeFile(file1, content, {
    fs.writeFile(file2, content, {
      fs.writeFile(file3, content, {
        fs.readDir(fileDir, { ares ->
          tu.azzert(ares.exception == null)
          tu.azzert(ares.result.length == 3)
          tu.testComplete()
        })
      })
    })
  })
}

def testProps() {
  def file = fileDir + "/foo.tmp"
  def content = "some-data"
  fs.writeFile(file, content, {
    fs.props(file, { ares ->
      tu.azzert(ares.exception == null)
      tu.azzert(ares.result.isRegularFile)
      tu.testComplete()
    })
  })
}

def testPumpFile() {
  def from = fileDir + "/foo.tmp"
  def to = fileDir + "/bar.tmp"
  def content = tu.generateRandomBuffer(10000)
  fs.writeFile(from, content, {
    fs.open(from, { ares1 ->
      tu.azzert(ares1.exception == null)
      fs.open(to, { ares2 ->
        tu.azzert(ares2.exception == null)
        def rs = ares1.result.getReadStream()
        def ws = ares2.result.getWriteStream()
        def pump = Pump.createPump(rs, ws)
        pump.start()
        rs.endHandler {
          ares1.result.close {
            ares2.result.close {
              fs.readFile(to, { ares3 ->
                tu.azzert(ares3.exception == null)
                tu.azzert(tu.buffersEqual(content, ares3.result))
                tu.testComplete()
              })
            }
          }
        }
      })
    })
  })
}

def setup(doneHandler) {
  fs.exists(fileDir, { ares1 ->
    if (ares1.result) {
      fs.delete(fileDir, true) {
        fs.mkdir(fileDir) {
          doneHandler()
        }
      }
    } else {
      fs.mkdir(fileDir, {
        doneHandler()
      })
    }
  })
}

def teardown(doneHandler) {
  fs.delete(fileDir, true, {
    doneHandler()
  })
}

tu.registerTests(this)

setup({ tu.appReady() })

def vertxStop() {
  teardown{
    tu.unregisterAll()
    tu.appStopped()
  }
}
