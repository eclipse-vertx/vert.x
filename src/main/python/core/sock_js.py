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

import core.streams
import core.ssl_support
import org.vertx.java.core
import org.vertx.java.core.json
import org.vertx.java.deploy.impl

from core.javautils import map_to_java

class SockJSServer(object):
    """This is an implementation of the server side part of https://github.com/sockjs

    SockJS enables browsers to communicate with the server using a simple WebSocket-like api for sending
    and receiving messages. Under the bonnet SockJS chooses to use one of several protocols depending on browser
    capabilities and what apppears to be working across the network.

    Available protocols include:

    WebSockets
    xhr-polling
    xhr-streaming
    json-polling
    event-source
    html-file

    This means, it should just work irrespective of what browser is being used, and whether there are nasty
    things like proxies and load balancers between the client and the server.

    For more detailed information on SockJS, see their website.

    On the server side, you interact using instances of SockJSSocket - this allows you to send data to the
    client or receive data via the ReadStream data_handler.

    You can register multiple applications with the same SockJSServer, each using different path prefixes, each
    application will have its own handler, and configuration is described in a Hash.
    """

    def __init__(self, http_server):
      self.java_obj = org.vertx.java.deploy.impl.VertxLocator.vertx.createSockJSServer(http_server._to_java_server())

    def install_app(self, config, handler):
        """Install an application

        Keyword arguments
        config -- Configuration for the application
        proc -- Proc representing the handler
        handler -- Handler to call when a new SockJSSocket is created
        """
        java_config = org.vertx.java.core.json.JsonObject(map_to_java(config))
        self.java_obj.installApp(java_config, SockJSSocketHandler(handler))

    def bridge(self, config, permitted, auth_timeout=5*60*1000, auth_address=None):
      a_json = org.vertx.java.core.json.JsonArray(map_to_java(permitted))
      java_obj.bridge(org.vertx.java.core.json.JsonObject.new(config), a_json, auth_timeout, auth_address)

class SockJSSocket(core.streams.ReadStream, core.streams.WriteStream):
    """You interact with SockJS clients through instances of SockJS socket.
    The API is very similar to WebSocket. It implements both
    ReadStream and WriteStream so it can be used with Pump to enable
    flow control.
    """
    
    def __init__(self, java_sock):
      self.java_obj = java_sock
      #@handler_id = EventBus.register_simple_handler { |msg|
      #  write_buffer(msg.body)
      #}

    
    def close():
        """Close the socket"""
        #EventBus.unregister_handler(@handler_id)
        self.java_obj.close()

    def handler_id(self):
        """When a SockJSSocket is created it automatically registers an event handler with the system, the ID of that
        handler is given by handler_id.
        Given this ID, a different event loop can send a buffer to that event handler using the event bus. This
        allows you to write data to other SockJSSockets which are owned by different event loops.
        """
        return self.handler_id

    # @private
    def _to_java_socket(self):
      return self.java_obj

class SockJSSocketHandler(org.vertx.java.core.Handler):
    """ SockJS Socket handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, sock):
        """ Call the handler after SockJS Socket is ready"""
        self.handler(SockJSSocket(sock))
