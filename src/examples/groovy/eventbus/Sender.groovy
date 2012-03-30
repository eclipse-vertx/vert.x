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
import org.vertx.groovy.core.Vertx
import org.vertx.groovy.core.eventbus.EventBus

eb = EventBus.instance

address = 'example.address'

def count = 0

// Send a message every 2 seconds

Vertx.instance.setPeriodic(2000) {
  def msg = "some-message-${count++}"
  eb.send(address, msg)
  println "Sent message $msg"
}
