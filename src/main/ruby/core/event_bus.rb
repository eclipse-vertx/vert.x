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
  # Messages sent over the event bus are JSON objects represented as Ruby Hash instances.
  #
  # The event bus implements both publish / subscribe network and point to point messaging.
  #
  # For publish / subscribe, messages can be published to an address using one of the publish methods. An
  # address is a simple String instance. Handlers are registered against an address. There can be multiple handlers
  # registered against each address, and a particular handler can be registered against multiple addresses.
  # The event bus will route a sent message to all handlers which are registered against that address.

  # For point to point messaging, messages can be sent to an address using the send method.
  # The messages will be delivered to a single handler, if one is registered on that address. If more than one
  # handler is registered on the same address, Vert.x will choose one and deliver the message to that. Vert.x will
  # aim to fairly distribute messages in a round-robin way, but does not guarantee strict round-robin under all
  # circumstances.
  #
  # All messages sent over the bus are transient. On event of failure of all or part of the event bus messages
  # may be lost. Applications should be coded to cope with lost messages, e.g. by resending them,
  # and making application services idempotent.
  #
  # The order of messages received by any specific handler from a specific sender should match the order of messages
  # sent from that sender.
  #
  # When sending a message, a reply handler can be provided. If so, it will be called when the reply from the receiver
  # has been received. Reply messages can also be replied to, etc, ad infinitum.
  #
  # Different event bus instances can be clustered together over a network, to give a single logical event bus.
  #
  # When receiving a message in a handler the received object is an instance of EventBus::Message - this contains
  # the actual Hash of the message plus a reply method which can be used to reply to it.
  #
  # @author {http://tfox.org Tim Fox}
  class EventBus

    @@handler_map = {}

    @@j_eventbus = org.vertx.java.deploy.impl.VertxLocator.vertx.eventBus()

    # Send a message on the event bus
    # @param message [Hash] The message to send
    # @param reply_handler [Block] replyHandler An optional reply handler.
    # It will be called when the reply from a receiver is received.
    def EventBus.send(address, message, &reply_handler)
      EventBus.send_or_pub(true, address, message, reply_handler)
    end

    # Publish a message on the event bus
    # @param message [Hash] The message to publish
    def EventBus.publish(address, message)
      EventBus.send_or_pub(false, address, message)
    end

    # @private
    def EventBus.send_or_pub(send, address, message, reply_handler = nil)
      raise "An address must be specified" if !address
      raise "A message must be specified" if message == nil
      message = convert_msg(message)
      if send
        if reply_handler != nil
          @@j_eventbus.send(address, message, InternalHandler.new(reply_handler))
        else
          @@j_eventbus.send(address, message)
        end
      else
        @@j_eventbus.publish(address, message)
      end
    end

    # Register a handler.
    # @param address [String] The address to register for. Any messages sent to that address will be
    # received by the handler. A single handler can be registered against many addresses.
    # @param local_only [Boolean] If true then handler won't be propagated across cluster
    # @param message_hndlr [Block] The handler
    # @return [FixNum] id of the handler which can be used in {EventBus.unregister_handler}
    def EventBus.register_handler(address, local_only = false, &message_hndlr)
      raise "An address must be specified" if !address
      raise "A message handler must be specified" if !message_hndlr
      internal = InternalHandler.new(message_hndlr)
      if local_only
        id = @@j_eventbus.registerLocalHandler(address, internal)
      else
        id = @@j_eventbus.registerHandler(address, internal)
      end
      @@handler_map[id] = internal
      id
    end

    # Registers a handler against a uniquely generated address, the address is returned as the id
    # received by the handler. A single handler can be registered against many addresses.
    # @param local_only [Boolean] If true then handler won't be propagated across cluster
    # @param message_hndlr [Block] The handler
    # @return [FixNum] id of the handler which can be used in {EventBus.unregister_handler}
    def EventBus.register_simple_handler(local_only = false, &message_hndlr)
      raise "A message handler must be specified" if !message_hndlr
      internal = InternalHandler.new(message_hndlr)
      if local_only
        id = @@j_eventbus.registerLocalHandler(internal)
      else
        id = @@j_eventbus.registerHandler(internal)
      end
      @@handler_map[id] = internal
      id
    end

    # Unregisters a handler
    # @param handler_id [FixNum] The id of the handler to unregister. Returned from {EventBus.register_handler}
    def EventBus.unregister_handler(handler_id)
      raise "A handler_id must be specified" if !handler_id
      handler = @@handler_map.delete(handler_id)
      raise "Cannot find handler for id #{handler_id}" if !handler
      @@j_eventbus.unregisterHandler(handler_id)
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

end

