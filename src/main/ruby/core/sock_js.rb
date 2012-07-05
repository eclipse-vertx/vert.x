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

require 'core/streams'
require 'core/ssl_support'
module Vertx

  # This is an implementation of the server side part of {"https://github.com/sockjs"}
  #
  # SockJS enables browsers to communicate with the server using a simple WebSocket-like api for sending
  # and receiving messages. Under the bonnet SockJS chooses to use one of several protocols depending on browser
  # capabilities and what apppears to be working across the network.
  #
  # Available protocols include:
  #
  # WebSockets
  # xhr-polling
  # xhr-streaming
  # json-polling
  # event-source
  # html-file
  #
  # This means, it should just work irrespective of what browser is being used, and whether there are nasty
  # things like proxies and load balancers between the client and the server.
  #
  # For more detailed information on SockJS, see their website.
  #
  # On the server side, you interact using instances of {SockJSSocket} - this allows you to send data to the
  # client or receive data via the {ReadStream#data_handler}.
  #
  # You can register multiple applications with the same SockJSServer, each using different path prefixes, each
  # application will have its own handler, and configuration is described in a Hash.
  #
  # @author {http://tfox.org Tim Fox}
  class SockJSServer

    # Create a new SockJSServer
    # @param http_server [HttpServer] You must pass in an instance of {HttpServer}
    def initialize(http_server)
      @j_server = org.vertx.java.deploy.impl.VertxLocator.vertx.createSockJSServer(http_server._to_java_server)
    end

    # Install an application
    # @param config [Hash] Configuration for the application
    # @param proc [Proc] Proc representing the handler
    # @param hndlr [Block] Handler to call when a new {SockJSSocket is created}
    def install_app(config, proc = nil, &hndlr)
      hndlr = proc if proc
      j_config = org.vertx.java.core.json.JsonObject.new(config)
      @j_server.installApp(j_config) { |j_sock|
        hndlr.call(SockJSSocket.new(j_sock))
      }
    end

    def bridge(config, inbound_permitted, outbound_permitted, auth_timeout = 5 * 60 * 1000, auth_address = nil)
      j_inbound_permitted = org.vertx.java.core.json.JsonArray.new(inbound_permitted)
      j_outbound_permitted = org.vertx.java.core.json.JsonArray.new(outbound_permitted)
      @j_server.bridge(org.vertx.java.core.json.JsonObject.new(config), j_inbound_permitted,
                       j_outbound_permitted, auth_timeout, auth_address)
    end

  end

  # You interact with SockJS clients through instances of SockJS socket.
  # The API is very similar to {WebSocket}. It implements both
  # {ReadStream} and {WriteStream} so it can be used with {Pump} to enable
  # flow control.
  #
  # @author {http://tfox.org Tim Fox}
  class SockJSSocket

    include ReadStream, WriteStream

    # @private
    def initialize(j_sock)
      @j_del = j_sock
      @handler_id = EventBus.register_simple_handler { |msg|
        write_buffer(msg.body)
      }
    end

    # Close the socket
    def close
      EventBus.unregister_handler(@handler_id)
      @j_del.close
    end

    # When a SockJSSocket is created it automatically registers an event handler with the system, the ID of that
    # handler is given by {#handler_id}.
    # Given this ID, a different event loop can send a buffer to that event handler using the event bus. This
    # allows you to write data to other SockJSSockets which are owned by different event loops.
    def handler_id
      @handler_id
    end

    # @private
    def _to_java_socket
      @j_del
    end

  end

end