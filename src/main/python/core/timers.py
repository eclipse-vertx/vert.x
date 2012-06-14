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

import org.vertx.java.deploy.impl
from core.handlers import DoneHandler

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"

def java_vertx():
  return org.vertx.java.deploy.impl.VertxLocator.vertx

def set_timer(delay, handler):
    """Sets a one-shot timer that will fire after a certain delay.
    This method will accept either a Proc or a block.

    Keyword arguments
    delay -- the delay, in milliseconds
    handler -- a block representing the code that will be run after the delay the unique id of the timer
    """
    java_vertx().setTimer(delay, DoneHandler(handler))

def set_periodic(delay, handler):
    """Sets a periodic timer.

    Keyword arguments
    delay -- the period of the timer, in milliseconds
    handler -- a block representing the code that will be when the timer fires the unique id of the timer
    """
    java_vertx().setPeriodic(delay, DoneHandler(handler))


def cancel_timer(id):
    """Cancels a timer.

    Keyword arguments
    id -- the id of the timer, as returned from set_timer or set_periodic

    returns true if the timer was cancelled, false if it wasn't found.
    """
    java_vertx().cancelTimer(id)


def run_on_loop(handler):
    """Put the handler on the event queue for this loop so it will be run asynchronously
    ASAP after this event has been processed

    Keyword arguments
    handler -- a block representing the code that will be run ASAP
    """
    java_vertx().runOnLoop(DoneHandler(handler))
