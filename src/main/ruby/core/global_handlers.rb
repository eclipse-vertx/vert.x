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

  # Registers a global event handler.
  # The handler can be invoked by calling the {Vertx#send_to_handler} method from any event loop.
  # The handler will always be called on the event loop that invoked the {Vertx#register_handler} method.
  # @param [Proc] proc a proc representing the handler
  # @param [Block] hndlr a block representing the handler
  # @return [FixNum] unique id of the handler
  def Vertx.register_handler(proc = nil, &hndlr)
    hndlr = proc if proc
    org.vertx.java.core.Vertx.instance.registerHandler(hndlr)
  end

  # Unregisters a global event handler.
  # @param [FixNum] handler_id the unique id of the handler to unregister.
  # @return [Boolean] true if the handler was successfully unregistered, false otherwise
  def Vertx.unregister_handler(handler_id)
    org.vertx.java.core.Vertx.instance.unregisterHandler(handler_id)
  end

  # Send a message to a global handler. This can be called from any event loop.
  # The message will always be delivered on the event loop that originally registered the handler.
  # @return [Boolean] true if the message was successfully sent, or false if no such handler exists.
  def Vertx.send_to_handler(handler_id, msg)
    msg = msg.copy if msg.is_a?(Buffer)
    org.vertx.java.core.Vertx.instance.sendToHandler(handler_id, msg)
  end

end