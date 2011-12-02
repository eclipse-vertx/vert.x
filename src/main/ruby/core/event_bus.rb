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

  # This class represents a distributed lightweight event bus which can encompass multiple vert.x instances.
  # It is very useful for otherwise isolated vert.x application instances to communicate with each other.
  #
  # Messages sent over the event bus are represented by instances of the {Message} class.
  #
  # The event bus implements a distributed publish / subscribe network.
  #
  # Messages are sent to an address which is simply an arbitrary String.
  # There can be multiple handlers can be registered against that address.
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
  # When sending a message, a receipt can be request. If so, when the message has been received by all registered
  # matching handlers and the {Message#acknowledge} method has been called on each received message the receipt
  # handler will be called.
  #
  # @author {http://tfox.org Tim Fox}
  class EventBus

    @@handler_map = java.util.concurrent.ConcurrentHashMap.new
    @@handler_seq = java.util.concurrent.atomic.AtomicLong.new(0)

    # Send a message on the event bus
    # @param message [Message] The message to send
    # @param receipt_handler [Block] Optional receipt handler. If specified then when the message has reached all registered
    # handlers and each one has called {Message#acknowledge} then the handler will be called
    def EventBus.send(message, &receipt_handler)
      if receipt_handler != nil
        org.vertx.java.core.cluster.EventBus.instance.send(message._to_java_message, receipt_handler)
      else
        org.vertx.java.core.cluster.EventBus.instance.send(message._to_java_message)
      end
    end

    # Register a handler.
    # @param address [String] The address to register for. Any messages sent to that address will be
    # received by the handler. A single handler can be registered against many addresses.
    # @param message_hndlr [Block] The handler
    # @return [FixNum] id of the handler which can be used in {#unregister_handler}
    def EventBus.register_handler(address, &message_hndlr)
      internal = InternalHandler.new(address, message_hndlr)
      org.vertx.java.core.cluster.EventBus.instance.registerHandler(address, internal)
      id = @@handler_seq.incrementAndGet
      @@handler_map.put(id, internal)
      id
    end

    # Unregisters a handler
    # @param handler_id [FixNum] The id of the handler to unregister. Returned from {#register_handler}
    def EventBus.unregister_handler(handler_id)
      handler = @@handler_map.remove(handler_id)
      raise "Cannot find handler for id #{handler_id}" if !handler
      org.vertx.java.core.cluster.EventBus.instance.unregisterHandler(handler.address, handler)
    end

    # @private
    class InternalHandler
      include org.vertx.java.core.Handler

      attr_reader :address

      def initialize(address, hndlr)
        @address = address
        @hndlr = hndlr
      end

      def handle(j_msg)
        @hndlr.call(Message.create_from_j_msg(j_msg))
      end
    end

  end

  # Represents a message sent on the event bus
  # @author {http://tfox.org Tim Fox}
  class Message

    # @return [String] the address of the message
    attr_accessor :address

    # @return [Buffer] the body of the message
    attr_accessor :body

    # @return [String] the message id of the message. This is filled in by the server when sending
    attr_accessor :message_id

    #@private
    attr_accessor :j_del

    # Create a message
    # @param address [String] The address to send the message to
    # @param body [Buffer] Buffer representing body of message
    def initialize(address, body)
      raise "address parameter must be a String" if !address.is_a? String
      raise "body parameter must be a Buffer" if !body.is_a? Buffer
      @address = address
      @body = body
      @message_id = nil
      @j_del = nil
    end

    # Acknowledge receipt of this message. If the message was sent specifying a receipt handler, that handler will be
    # called when all receivers have called acknowledge. If the message wasn't sent specifying a receipt handler
    # this method does nothing.
    def acknowledge
      @j_del.acknowledge if @j_del
    end

    # @private
    def Message.create_from_j_msg(j_msg)
      msg = Message.new(j_msg.address, Buffer.new(j_msg.body))
      msg.message_id = j_msg.messageID
      msg.j_del = j_msg
      msg
    end

    # @private
    def _to_java_message
      org.vertx.java.core.cluster.Message.new(@address, @body._to_java_buffer)
    end

  end
end
