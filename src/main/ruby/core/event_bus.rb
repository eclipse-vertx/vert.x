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

require 'rubygems'
require 'json'

module Vertx

  # This class represents a distributed lightweight event bus which can encompass multiple vert.x instances.
  # It is very useful for otherwise isolated vert.x application instances to communicate with each other.
  #
  # Messages sent over the event bus are json objects represented as Ruby Hash instances.
  #
  # The event bus implements a distributed publish / subscribe network.
  #
  # Messages are sent to an address.
  #
  # There can be multiple handlers registered against that address.
  # Any handlers with a matching name will receive the message irrespective of what vert.x application instance and
  # what vert.x instance they are located in.
  #
  # All messages sent over the bus are transient. On event of failure of all or part of the event bus messages
  # may be lost. Applications should be coded to cope with lost messages, e.g. by resending them, and making application
  # services idempotent.
  #
  # The order of messages received by any specific handler from a specific sender will match the order of messages
  # sent from that sender.
  #
  # When sending a message, a reply handler can be provided. If so, it will be called when the reply from the receiver
  # has been received.
  #
  # When receiving a message in a handler the received object is an instance of EventBus::Message - this contains
  # the actual Hash of the message plus a reply method which can be used to reply to it.
  #
  # @author {http://tfox.org Tim Fox}
  class EventBus

    @@handler_map = {}

    # Send a message on the event bus
    # @param message [Hash] The message to send
    # @param reply_handler [Block] replyHandler An optional reply handler.
    # It will be called when the reply from a receiver is received.
    def EventBus.send(address, message, &reply_handler)
      raise "An address must be specified" if !address
      raise "A message must be specified" if message == nil
      message = convert_msg(message)
      if reply_handler != nil
        org.vertx.java.core.eventbus.EventBus.instance.send(address, message, InternalHandler.new(reply_handler))
      else
        org.vertx.java.core.eventbus.EventBus.instance.send(address, message)
      end
    end

    # Register a handler.
    # @param address [String] The address to register for. Any messages sent to that address will be
    # received by the handler. A single handler can be registered against many addresses.
    # @param message_hndlr [Block] The handler
    # @return [FixNum] id of the handler which can be used in {EventBus.unregister_handler}
    def EventBus.register_handler(address, &message_hndlr)
      raise "An address must be specified" if !address
      raise "A message handler must be specified" if !message_hndlr
      internal = InternalHandler.new(message_hndlr)
      id = org.vertx.java.core.eventbus.EventBus.instance.registerHandler(address, internal)
      @@handler_map[id] = internal
      id
    end

    def EventBus.register_simple_handler(&message_hndlr)
      raise "A message handler must be specified" if !message_hndlr
      internal = InternalHandler.new(message_hndlr)
      id = org.vertx.java.core.eventbus.EventBus.instance.registerHandler(internal)
      @@handler_map[id] = internal
      id
    end

    # Unregisters a handler
    # @param handler_id [FixNum] The id of the handler to unregister. Returned from {EventBus.register_handler}
    def EventBus.unregister_handler(handler_id)
      raise "A handler_id must be specified" if !handler_id
      handler = @@handler_map.delete(handler_id)
      raise "Cannot find handler for id #{handler_id}" if !handler
      org.vertx.java.core.eventbus.EventBus.instance.unregisterHandler(handler_id)
    end

    # @private
    def EventBus.convert_msg(message)
      if message.is_a? Hash
        message = org.vertx.java.core.json.JsonObject.new(JSON.generate(message))
      elsif message.is_a? Buffer
        message = message._to_java_buffer
      elsif message.is_a? Fixnum
        message = java.lang.Long.new(message)
      elsif message.is_a? Float
        message = java.lang.Double.new(message)
      end
      message
    end

  end

  # @private
  class InternalHandler
    include org.vertx.java.core.Handler

    def initialize(hndlr)
      @hndlr = hndlr
    end

    def handle(message)
      @hndlr.call(Message.new(message))
    end
  end

  # Represents a message received from the event bus
  # @author {http://tfox.org Tim Fox}
  class Message

    attr_reader :body

    # @private
    def initialize(message)

      @j_del = message
      if message.body.is_a? org.vertx.java.core.json.JsonObject
        @body = JSON.parse(message.body.encode)
      elsif message.body.is_a? org.vertx.java.core.buffer.Buffer
        @body = Buffer.new(message.body)
      else
        @body = message.body
      end
    end

    # Reply to this message. If the message was sent specifying a receipt handler, that handler will be
    # called when it has received a reply. If the message wasn't sent specifying a receipt handler
    # this method does nothing.
    # Replying to a message this way is equivalent to sending a message to an address which is the same as the message id
    # of the original message.
    # @param [Hash] Message send as reply
    def reply(reply, &reply_handler)
      raise "A reply message must be specified" if reply == nil
      reply = EventBus.convert_msg(reply)
      if reply_handler != nil
        @j_del.reply(reply, InternalHandler.new(reply_handler))
      else
        @j_del.reply(reply)
      end
    end

  end

  # A SockJSBridgeHandler plugs into a SockJS server and translates data received via SockJS into operations
  # to send messages and register and unregister handlers on the vert.x event bus.
  #
  # When used in conjunction with the vert.x client side JavaScript event bus api (vertxbus.js) this effectively
  # extends the reach of the vert.x event bus from vert.x server side applications to the browser as well. This
  # enables a truly transparent single event bus where client side JavaScript applications can play on the same
  # bus as server side application instances and services.
  #
  # @author {http://tfox.org Tim Fox}
  class SockJSBridgeHandler < org.vertx.java.core.eventbus.SockJSBridgeHandler
    def initialize
      super
    end

    # Call this handler - pretend to be a Proc
    def call(sock)
      # This is inefficient since we convert to a Ruby SockJSSocket and back again to a Java one
      handle(sock._to_java_socket)
    end

    def add_permitted(*permitted)
      permitted.each do |match|
        json_str = JSON.generate(match)
        j_json = org.vertx.java.core.json.JsonObject.new(json_str)
        addPermitted(j_json);
      end
    end

  end

  class SockJSBridge

    def initialize(http_server, sjs_config, permitted)
      @sjs_server = SockJSServer.new(http_server)
      handler = SockJSBridgeHandler.new
      handler.add_permitted(*permitted)
      @sjs_server.install_app(sjs_config, handler);
    end

  end

end

