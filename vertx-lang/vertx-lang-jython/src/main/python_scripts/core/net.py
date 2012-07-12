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

""" 
Net support to the python vert.x platform 
"""

import org.vertx.java.core.Handler
import org.vertx.java.deploy.impl.VertxLocator
import core.tcp_support
import core.ssl_support
import core.buffer
import core.streams

from core.javautils import map_from_java, map_to_java
from core.handlers import CloseHandler, DoneHandler, ClosedHandler
from core.event_bus import EventBus

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"
__credits__ = "Based entirely on work by Tim Fox http://tfox.org"

class NetServer(core.ssl_support.SSLSupport, core.tcp_support.TCPSupport):    
    """Represents a TCP or SSL Server

    When connections are accepted by the server
    they are supplied to the user in the form of a NetSocket instance that is passed via the handler
    set using connect_handler.
    """
    def __init__(self, **kwargs):
        self.java_obj = org.vertx.java.deploy.impl.VertxLocator.vertx.createNetServer()
        for item in kwargs.keys():
           setattr(self, item, kwargs[item])

    def set_client_auth_required(self, val):
        """Client authentication is an extra level of security in SSL, and requires clients to provide client certificates.
        Those certificates must be added to the server trust store.
        @param val:  If true then the server will request client authentication from any connecting clients, if they
        do not authenticate then they will not make a connection.
        """
        self.java_obj.setClientAuthRequired(val)
        return self
    client_auth_required = property(fset=set_client_auth_required)

    def connect_handler(self, handler):
        """Supply a connect handler for this server. The server can only have at most one connect handler at any one time.
        As the server accepts TCP or SSL connections it creates an instance of NetSocket and passes it to the
        connect handler.

        Keyword arguments:
        @param handler: connection handler
        
        @return: a reference to self so invocations can be chained
        """
        self.java_obj.connectHandler(ConnectHandler(handler))
        return self


    def listen(self, port, host="0.0.0.0"):
        """Instruct the server to listen for incoming connections.

        Keyword arguments:
        @param port: The port to listen on.
        @param host: The host name or ip address to listen on.
        
        @return: a reference to self so invocations can be chained
        """    
        self.java_obj.listen(port, host)
        return self


    def close(self, handler=None):
        """Close the server. The handler will be called when the close is complete."""
        self.java_obj.close(CloseHandler(handler))



class NetClient(core.ssl_support.SSLSupport, core.tcp_support.TCPSupport):
    """NetClient is an asynchronous factory for TCP or SSL connections.

    Multiple connections to different servers can be made using the same instance.
    """
    def __init__(self, **kwargs):
        self.java_obj = org.vertx.java.deploy.impl.VertxLocator.vertx.createNetClient()
        for item in kwargs.keys():
           setattr(self, item, kwargs[item])

    def set_trust_all(self, val):
        """Should the client trust ALL server certificates

        Keyword arguments:
        @param val:  If val is set to true then the client will trust ALL server certificates and will not attempt to authenticate them
        against it's local client trust store. The default value is false.

        Use this method with caution!
        
        @return: a reference to self so invocations can be chained
        """
        self.java_obj.setTrustAll(val)
        return self

    trust_all = property(fset=set_trust_all)

    def connect(self, port, host, handler):
        """Attempt to open a connection to a server. The connection is opened asynchronously and the result returned in the
        handler.

        Keyword arguments:
        @param port: The port to connect to.
        @param host: The host or ip address to connect to.
        @param handler: The connection handler

        @return: a reference to self so invocations can be chained
        """
        self.java_obj.connect(port, host, ConnectHandler(handler))
        return self

    def close(self):
        """Close the NetClient. Any open connections will be closed."""
        self.java_obj.close()

class NetSocket(core.streams.ReadStream, core.streams.WriteStream):
    """NetSocket is a socket-like abstraction used for reading from or writing
    to TCP connections.
    """      
    def __init__(self, j_socket):
        self.java_obj = j_socket

        def simple_handler(msg):
            self.write_buffer(msg.body)

        self.write_handler_id = EventBus.register_simple_handler(False, simple_handler)
        
        def wrapped_closed_handler():
            EventBus.unregister_handler(self.write_handler_id)
            if hasattr(self, "_closed_handler"):
                self._closed_handler()
        self.java_obj.closedHandler(ClosedHandler(wrapped_closed_handler))
        
    def write_buffer(self, buffer, handler=None):
        """Write a Buffer to the socket. The handler will be called when the buffer has actually been written to the wire.

        Keyword arguments:
        @param buffer: The buffer to write.
        @param handler: The handler to call on completion.
        """
        java_buffer = buffer._to_java_buffer()
        if handler is None:
            self.java_obj.write(java_buffer)
        else:
            self.java_obj.write(java_buffer, DoneHandler(handler))

    def write_str(self, str, enc="UTF-8", handler=None):
        """Write a String to the socket. The handler will be called when the string has actually been written to the wire.

        Keyword arguments:
        @param str: The string to write.
        @param enc: The encoding to use.
        @param handler: The handler to call on completion.
        """
        if handler is None:
            self.java_obj.write(str, enc)
        else:
            self.java_obj.write(str, enc, DoneHandler(handler))
      
    def closed_handler(self, handler):
        """Set a closed handler on the socket.

        Keyword arguments:
        @param handler: A block to be used as the handler
        """
        self._closed_handler = handler

    def send_file(self, file_path):
        """Tell the kernel to stream a file directly from disk to the outgoing connection, bypassing userspace altogether
        (where supported by the underlying operating system. This is a very efficient way to stream files.

        Keyword arguments:
        @param file_path: Path to file to send.
        """
        self.java_obj.sendFile(file_path)
     
    def close(self):
        """Close the socket"""
        self.java_obj.close()

class ConnectHandler(org.vertx.java.core.Handler):
    """ Connection handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, socket):
        """ Call the handler after connection is established"""
        self.handler(NetSocket(socket))
