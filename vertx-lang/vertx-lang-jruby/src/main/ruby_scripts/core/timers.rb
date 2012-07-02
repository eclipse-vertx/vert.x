# Copyright 2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

module Vertx

  @@j_vertx = org.vertx.java.deploy.impl.VertxLocator.vertx()

  # Sets a one-shot timer that will fire after a certain delay.
  # This method will accept either a Proc or a block.
  # @param [FixNum] delay the delay, in milliseconds
  # @param [Proc] proc a proc representing the code that will be run after the delay
  # @param [Block] hndlr a block representing the code that will be run after the delay
  # @return [FixNum] the unique id of the timer
  def Vertx.set_timer(delay, proc = nil, &hndlr)
    hndlr = proc if proc
    @@j_vertx.setTimer(delay, hndlr)
  end

  # Sets a periodic timer.
  # This method will accept either a Proc or a block.
  # @param [FixNum] delay the period of the timer, in milliseconds
  # @param [Proc] proc a proc representing the code that will be run when the timer fires
  # @param [Block] hndlr a block representing the code that will be when the timer fires
  # @return [FixNum] the unique id of the timer
  def Vertx.set_periodic(delay, proc = nil, &hndlr)
    hndlr = proc if proc
    @@j_vertx.setPeriodic(delay, hndlr)
  end

  # Cancels a timer.
  # @param [FixNum] id the id of the timer, as returned from {Vertx.set_timer} or {Vertx.set_periodic}
  # @return [Boolean] true if the timer was cancelled, false if it wasn't found.
  def Vertx.cancel_timer(id)
    @@j_vertx.cancelTimer(id)
  end

  # Put the handler on the event queue for this loop so it will be run asynchronously
  # ASAP after this event has been processed
  # @param [Proc] proc a proc representing the code that will be run ASAP
  # @param [Block] hndlr a block representing the code that will be run ASAP
  def Vertx.run_on_loop(proc = nil, &hndlr)
    hndlr = proc if proc
    @@j_vertx.runOnLoop(hndlr)
  end

end
