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
import org.vertx.java.deploy.impl.VertxLocator
import core.tcp_support
import core.ssl_support
import core.buffer
import core.streams

from core.javautils import map_from_java, map_to_java
from core.handlers import CloseHandler, DoneHandler, ClosedHandler

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"

class NetServer(core.ssl_support.SSLSupport, core.tcp_support.TCPSupport, object):    
    """Represents a TCP or SSL Server

    When connections are accepted by the server
    they are supplied to the user in the form of a NetSocket instance that is passed via the handler
    set using connect_handler.
    """
    def __init__(self, kwargs={}):
        self.java_obj = org.vertx.java.deploy.impl.VertxLocator.vertx.createNetServer()
        for item in kwargs.keys():
           setattr(self, item, kwargs[item])

    @property
    def client_auth_required(self, val):
        """Client authentication is an extra level of security in SSL, and requires clients to provide client certificates.
        Those certificates must be added to the server trust store.
        val --  If true then the server will request client authentication from any connecting clients, if they
        do not authenticate then they will not make a connection.
        """
        self.java_obj.setClientAuthRequired(val)
        return self

    def connect_handler(self, handler):
        """Supply a connect handler for this server. The server can only have at most one connect handler at any one time.
        As the server accepts TCP or SSL connections it creates an instance of NetSocket and passes it to the
        connect handler.

        Keyword arguments
        handler -- connection handler

        returns a reference to self so invocations can be chained
        """
        self.java_obj.connectHandler(ConnectHandler(handler))
        return self


    def listen(self, port, host="0.0.0.0"):
        """Instruct the server to listen for incoming connections.

        Keyword arguments
        port -- The port to listen on.
        host -- The host name or ip address to listen on.

        returns a reference to self so invocations can be chained
        """    
        self.java_obj.listen(port, host)
        return self


    def close(self, handler):
        """Close the server. The handler will be called when the close is complete."""
        self.java_obj.close(CloseHandler(handler))



class NetClient(core.ssl_support.SSLSupport, core.tcp_support.TCPSupport, object):
    """NetClient is an asynchronous factory for TCP or SSL connections.

    Multiple connections to different servers can be made using the same instance.
    """
    def __init__(self, kwargs={}):
        self.java_obj = org.vertx.java.deploy.impl.VertxLocator.vertx.createNetClient()
        for item in kwargs.keys():
           setattr(self, item, kwargs[item])

    @property
    def trust_all(self, val):
        """Should the client trust ALL server certificates

        Keyword arguments
        val --  If val is set to true then the client will trust ALL server certificates and will not attempt to authenticate them
        against it's local client trust store. The default value is false.

        Use this method with caution!

        returns a reference to self so invocations can be chained
        """
        self.java_obj.setTrustAll(val)
        return self


    def connect(self, port, host, handler ):
        """Attempt to open a connection to a server. The connection is opened asynchronously and the result returned in the
        handler.

        Keyword arguments
        port -- The port to connect to.
        host -- The host or ip address to connect to.
        handler -- The connection handler
        returns a reference to self so invocations can be chained
        """
        self.java_obj.connect(port, host, ConnectHandler(handler))
        return self


    def close(self):
        """Close the NetClient. Any open connections will be closed."""
        self.java_obj.close()



class NetSocket(core.streams.ReadStream, core.streams.WriteStream, object):
    """NetSocket is a socket-like abstraction used for reading from or writing
    to TCP connections.
    """      
    def __init__(self, j_socket):
        self.java_obj = j_socket

        #@write_handler_id = EventBus.register_simple_handler  |msg|
        #write_buffer(msg.body)
      
        # self.java_obj.closedHandler(Proc.new 
        # EventBus.unregister_handler(@write_handler_id)
        # @closed_handler.call if @closed_handler
        # )


    def write_buffer(self, buffer, handler=None):
        """Write a Buffer to the socket. The handler will be called when the buffer has actually been written to the wire.

        Keyword arguments
        buffer -- The buffer to write.
        handler -- The handler to call on completion.
        """
        java_buffer = buffer._to_java_buffer()
        if handler is None:
            self.java_obj.write(java_buffer)
        else:
            self.java_obj.write(java_buffer, DoneHandler(handler))

    def write_str(self, str, enc="UTF-8", handler=None):
        """Write a String to the socket. The handler will be called when the string has actually been written to the wire.

        Keyword arguments
        str -- The string to write.
        enc -- The encoding to use.
        handler -- The handler to call on completion.
        """
        if handler is None:
            self.java_obj.write(str, enc)
        else:
            self.java_obj.write(str, enc, DoneHandler(handler))
      

    def closed_handler(self, handler):
        """Set a closed handler on the socket.

        Keyword arguments
        handler -- A block to be used as the handler
        """
        self.java_obj.closedHandler(ClosedHandler(handler))        


    def send_file(self, file_path):
        """Tell the kernel to stream a file directly from disk to the outgoing connection, bypassing userspace altogether
        (where supported by the underlying operating system. This is a very efficient way to stream files.

        Keyword arguments
        file_path -- Path to file to send.
        """
        self.java_obj.sendFile(file_path)

     
    def close(self):
        """Close the socket"""
        self.java_obj.close()

# def write_handler_id(self):
#     """When a NetSocket is created it automatically registers an event handler with the system. The address of that
#     handler is given by write_handler_id.
#     Given this ID, a different event loop can send a buffer to that event handler using the event bus. This
#     allows you to write data to other connections which are owned by different event loops.
#     """
#     @write_handler_id


class ConnectHandler(org.vertx.java.core.Handler):
    """ Connection handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, socket):
        """ Call the handler after connection is established"""
        self.handler(NetSocket(socket))    