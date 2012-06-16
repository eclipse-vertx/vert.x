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

import org.vertx.java.core.Handler

from core.buffer import Buffer
from core.javautils import map_from_java, map_to_java

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"            

class DoneHandler(org.vertx.java.core.Handler):
    """ Done handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, nothing=None):
        """ Call the handler when done """
        if self.handler != None:
            self.handler()

class ContinueHandler(org.vertx.java.core.Handler):
    """ Continue handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, nothing=None):
        """ Call the handler to continue """
        self.handler()        

class BufferHandler(org.vertx.java.core.Handler):
    def __init__(self, handler):
        self.handler = handler

    def handle(self, buffer):
        self.handler(Buffer(buffer))

class ClosedHandler(org.vertx.java.core.Handler):
    """ Closed connection handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, nothing=None):
        """ Call the handler when a connection is closed """
        self.handler()

class CloseHandler(org.vertx.java.core.Handler):
    """ Close connection handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, nothing=None):
        """ Call the handler when a connection is closed """
        if self.handler != None:
            self.handler()        

class ExceptionHandler(org.vertx.java.core.Handler):
    """ Exception handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, exception):
        """ Call the handler when there is an exception """
        self.handler(exception)        

class DrainHandler(org.vertx.java.core.Handler):
    """ Drain handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, nothing=None):
        """ Call the handler after stream has been drained"""
        self.handler()                

class StreamEndHandler(org.vertx.java.core.Handler):
    """ Stream End handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, stream):
        """ Call the handler after stream has been ended"""
        self.handler(stream)                

class TimerHandler(org.vertx.java.core.Handler):
    """ Timer handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, timer_id):
        """ Call the handler """
        if self.handler != None:
            self.handler(timer_id)
