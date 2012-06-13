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

import org.vertx.java.deploy.impl.VertxLocator
import org.vertx.java.core.buffer
import org.vertx.java.core
import org.vertx.java.core.json
import java.lang

from core.javautils import map_to_java, map_from_java
from core.buffer import Buffer

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"            

class EventBus(object):
    """This class represents a distributed lightweight event bus which can encompass multiple vert.x instances.
    It is very useful for otherwise isolated vert.x application instances to communicate with each other.

    Messages sent over the event bus are JSON objects represented as Ruby Hash instances.

    The event bus implements a distributed publish / subscribe network.

    Messages are sent to an address.

    There can be multiple handlers registered against that address.
    Any handlers with a matching name will receive the message irrespective of what vert.x application instance and
    what vert.x instance they are located in.

    All messages sent over the bus are transient. On event of failure of all or part of the event bus messages
    may be lost. Applications should be coded to cope with lost messages, e.g. by resending them, and making application
    services idempotent.

    The order of messages received by any specific handler from a specific sender will match the order of messages
    sent from that sender.

    When sending a message, a reply handler can be provided. If so, it will be called when the reply from the receiver
    has been received.

    When receiving a message in a handler the received object is an instance of EventBus::Message - this contains
    the actual Hash of the message plus a reply method which can be used to reply to it.
    """
    handler_dict = {}

    @staticmethod
    def java_eventbus():
        return org.vertx.java.deploy.impl.VertxLocator.vertx.eventBus()

    @staticmethod
    def send(address, message, reply_handler=None):
        message = EventBus.convert_msg(message)
        if reply_handler != None:
            EventBus.java_eventbus().send(address, message, InternalHandler(reply_handler))
        else:
            EventBus.java_eventbus().send(address, message)

    @staticmethod
    def register_handler(address, local_only, handler):
        """ Register a handler.

        Keyword arguments
        address -- the address to register for. Any messages sent to that address will be
        received by the handler. A single handler can be registered against many addresses.
        local_only -- if True then handler won't be propagated across cluster
        handler -- The handler
        returns id of the handler which can be used in EventBus.unregister_handler
        """
        internal = InternalHandler(handler)
        if local_only:
          id = EventBus.java_eventbus().registerLocalHandler(address, internal)
        else:
          id = EventBus.java_eventbus().registerHandler(address, internal)

        EventBus.handler_dict[id] = internal
        return id

    @staticmethod
    def register_simple_handler(local_only, handler):
        """
        Registers a handler against a uniquely generated address, the address is returned as the id
        received by the handler. A single handler can be registered against many addresses.

        Keyword arguments
        local_only -- If Rrue then handler won't be propagated across cluster
        handler -- The handler

        returns id of the handler which can be used in EventBus.unregister_handler
        """
        internal = InternalHandler(handler)
        if local_only:
            id = EventBus.java_eventbus().registerLocalHandler(internal)
        else:
            id = EventBus.java_eventbus().registerHandler(internal)
        EventBus.handler_dict[id] = internal
        return id

    @staticmethod
    def unregister_handler(handler_id):
        """Unregisters a handler

        Keyword arguments
        handler_id -- the id of the handler to unregister. Returned from EventBus.register_handler
        """
        handler = EventBus.handler_dict.pop(handler_id)
        EventBus.java_eventbus().unregisterHandler(handler_id)

    @staticmethod
    def convert_msg(message):
        if isinstance(message, dict):
            message = org.vertx.java.core.json.JsonObject(map_to_java(message))
        elif isinstance(message, Buffer):
            message = message._to_java_buffer()
        elif isinstance(message, long):
            message = java.lang.Long(message)
        elif isinstance(message, float):
            message = java.lang.Double(message)
        return message
        
class InternalHandler(org.vertx.java.core.Handler):
    def __init__(self, handler):
        self.handler = handler

    def handle(self, message):
        self.handler(Message(message))
  
class Message(object):
    """Represents a message received from the event bus"""
    def __init__(self, message):
        self.java_obj = message
        if isinstance(message.body, org.vertx.java.core.json.JsonObject):
            self.body = map_from_java(message.body.toMap())
        elif isinstance(message.body, org.vertx.java.core.buffer.Buffer):
            self.body = Buffer(message.body) 
        else:
            self.body = message.body
    
    def reply(self, reply, handler=None):
        """Reply to this message. If the message was sent specifying a receipt handler, that handler will be
        called when it has received a reply. If the message wasn't sent specifying a receipt handler
        this method does nothing.
        Replying to a message this way is equivalent to sending a message to an address which is the same as the message id
        of the original message.

        Keyword arguments
        reply -- message to send as reply
        handler -- the reply handler 
        """
        reply = EventBus.convert_msg(reply)
        if reply_handler is None:
            self.java_obj.reply(reply)
        else:
            self.java_obj.reply(reply, InternalHandler(reply_handler))
      