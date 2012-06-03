# Copyright 2011-2012 the original author or authors.   
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
This module adds the http support to the python vert.x platform 
"""

import org.vertx.java.deploy.impl.VertxLocator
import org.vertx.java.core.Handler
import core.javautils

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"

class HttpServer:
    """ An HTTP and websockets server """
    def __init__(self):
        self.http_server = org.vertx.java.deploy.impl.VertxLocator.vertx.createHttpServer()

    def request_handler(self, handler):
        """Set the HTTP request handler for the server.
        As HTTP requests arrive on the server a new HttpServerRequest instance will be created and passed to the handler.

        Keyword arguments:
        handler -- the function used to handle the request. 

        """
        self.http_server.requestHandler(HttpServerRequestHandler(handler))

    def websocket_handler(self, handler):
        """Set the websocket handler for the server.
        As websocket requests arrive on the server a new ServerWebSocket instance will be created and passed to the handler.

        Keyword arguments:
        handler -- the function used to handle the request. 

        """
        self.http_server.websocketHandler(ServerWebSocketHandler(handler))

    def listen(self, port, host=None):
        """Instruct the server to listen for incoming connections. If host is None listens on all.

        Keyword arguments:
        port -- The port to listen on.
        host -- The host name or ip address to listen on. (default None)
    
        """
        if host is None:
            self.http_server.listen(port)
        else:
            self.http_server.listen(port, host)

    def client_auth_required(self, val):
        """ Client authentication is an extra level of security in SSL, and requires clients to provide client certificates.
        Those certificates must be added to the server trust store.

        Keyword arguments:
        val -- If true then the server will request client authentication from any connecting clients, if they 
        do not authenticate then they will not make a connection.

        """
        self.http_server.setClientAuthRequired(val)
        return self

    def close(self, handler=None):
        """ Close the server. Any open HTTP connections will be closed. This can be used as a decorator.

        Keyword arguments:
        handler -- a handler that is called when the connection is closed. The handler is wrapped in a ClosedHandler.

        """
        if handler is None:
            self.http_server.close()
        else:
            self.http_server.close(CloseHandler(handler))

class HttpServerRequest:
    """ Encapsulates a server-side HTTP request.
  
    An instance of this class is created for each request that is handled by the server and is passed to the user via the
    handler specified using HttpServer.request_handler.

    Each instance of this class is associated with a corresponding HttpServerResponse instance via the property response.
    """
    def __init__(self, http_server_request):
        self.http_server_request = http_server_request
        self.http_server_response = HttpServerResponse(http_server_request.response)
        self.header_dict = None
        self.params_dict = None
    
    @property
    def method(self):
        """ The HTTP method, one of HEAD, OPTIONS, GET, POST, PUT, DELETE, CONNECT, TRACE """
        return self.http_server_request.method

    @property
    def uri(self):
        """ The uri of the request. For example 'http://www.somedomain.com/somepath/somemorepath/somresource.foo?someparam=32&someotherparam=x """
        return self.http_server_request.uri   

    @property
    def path(self):
        """ The path part of the uri. For example /somepath/somemorepath/somresource.foo """
        return self.http_server_request.path    

    @property
    def query(self):
        """ The query part of the uri. For example someparam=32&someotherparam=x """
        return self.http_server_request.query

    @property
    def params(self):
        """ The request parameters as a dictionary """
        if not self.params_dict:
            self.params_dict = core.javautils.map_from_java(self.http_server_request.params())
        return self.params_dict

    @property
    def response(self):
        """ The response HttpServerResponse object."""
        return self.http_server_response

    @property
    def headers(self):
        """ The request headers as a dictionary """
        if not self.header_dict:
            self.header_dict = core.javautils.map_from_java(self.http_server_request.headers())
        return self.header_dict

    def body_handler(self, handler):
        """ Set the body handler for this request, the handler receives a single Buffer object as a parameter. 
        This can be used as a decorator. 

        Keyword arguments:
        handler -- a handler that is called when the body has been received. The handler is wrapped in a BufferHandler.

        """
        self.http_server_request.bodyHandler(BufferHandler(handler))

class HttpServerResponse:
    """Encapsulates a server-side HTTP response.

    An instance of this class is created and associated to every instance of HttpServerRequest that is created.

    It allows the developer to control the HTTP response that is sent back to the client for the corresponding HTTP
    request. It contains methods that allow HTTP headers and trailers to be set, and for a body to be written out
    to the response.

    It also allows a file to be streamed by the kernel directly from disk to the outgoing HTTP connection,
    bypassing user space altogether (where supported by the underlying operating system). This is a very efficient way of
    serving files from the server since buffers do not have to be read one by one from the file and written to the outgoing
    socket. 

    """
    def __init__(self, http_server_response):
        self.http_server_response = http_server_response

    def get_status_code(self):
        """ Get the status code of the response. """
        return self.http_server_response.statusCode

    def set_status_code(self, code):
        """ Set the status code of the response. Default is 200 """
        self.http_server_response.statusCode = code

    status_code = property(get_status_code, set_status_code)

    def get_status_message(self):
        """ Get the status message the goes with status code """
        return self.http_server_response.statusMessage

    def set_status_message(self, message):
        """ Set the status message for a response """
        self.http_server_response.statusMessage = message

    status_message = property(get_status_message, set_status_message)

    @property
    def headers(self):
        """ Get a copy of the reponse headers as a dictionary """
        return core.javautils.map_from_java(self.http_server_response.headers())

    def put_header(self, key, value):
        """ Inserts a header into the response.

        Keyword arguments
        key -- The header key
        value -- The header value.
        
        returns HttpServerResponse so multiple operations can be chained.
        """
        self.http_server_response.putHeader(key, value)
        return self

    @property
    def trailers(self):
        """ Get a copy of the trailers as a dictionary """
        return core.javautils.map_from_java(self.http_server_response.trailers())

    def put_trailer(self, key, value):
        """ Inserts a trailer into the response. 

        Keyword arguments
        key -- The header key
        value -- The header value.
        
        returns HttpServerResponse so multiple operations can be chained.

        """
        self.http_server_response.putTrailer(key, value)
        return self

    def write_buffer(self, buffer, handler=None):
        """ Write a buffer to the response. The handler (if supplied) will be called when the buffer has actually been written to the wire.

        Keyword arguments
        buffer -- The buffer to write
        handler -- The handler to be called when writing has been completed. It is wrapped in a DoneHandler (default None)
        
        returns a HttpServerResponse so multiple operations can be chained.
        """
        if handler is None:
            self.http_server_response.writeBuffer(buffer._to_java_buffer())
        else:
            self.http_server_response.writeBuffer(buffer._to_java_buffer(), DoneHandler(handler))
        return self

    def write_str(self, str, enc="UTF-8", handler=None):
        """ Write a String to the response. The handler will be called when the String has actually been written to the wire.

        Keyword arguments
        str -- The string to write
        enc -- Encoding to use.

        handler -- The handler to be called when writing has been completed. It is wrapped in a DoneHandler (default None)
        
        returns a HttpServerResponse so multiple operations can be chained.
        """
        if handler is None:
            self.http_server_response.write(str, enc)
        else:
            self.http_server_response.write(str, enc, DoneHandler(handler)) 
        return self

    def send_file(self, path):
        """ Tell the kernel to stream a file directly from disk to the outgoing connection, bypassing userspace altogether
        (where supported by the underlying operating system. This is a very efficient way to serve files.

        Keyword arguments
        path -- Path to file to send.

        returns a HttpServerResponse so multiple operations can be chained.
        """

        self.http_server_response.sendFile(path)
        return self

    def set_chunked(self, val):
        """ Sets whether this response uses HTTP chunked encoding or not.

        Keyword arguments
        val -- If val is true, this response will use HTTP chunked encoding, and each call to write to the body
        will correspond to a new HTTP chunk sent on the wire. If chunked encoding is used the HTTP header
        'Transfer-Encoding' with a value of 'Chunked' will be automatically inserted in the response.

        If chunked is false, this response will not use HTTP chunked encoding, and therefore if any data is written the
        body of the response, the total size of that data must be set in the 'Content-Length' header before any
        data is written to the response body.
        An HTTP chunked response is typically used when you do not know the total size of the request body up front.
        
        returns a HttpServerResponse so multiple operations can be chained.
        """
        self.http_server_response.setChunked(val)
        return self

    def get_chunked(self):
        """ Get whether this response uses HTTP chunked encoding or not. """
        return self.http_server_response.getChunked()        

    chunked = property(get_chunked, set_chunked)

    def end(self, data=None):
        """ Ends the response. If no data has been written to the response body, the actual response won't get written until this method gets called.
        Once the response has ended, it cannot be used any more, and if keep alive is true the underlying connection will
        be closed.

        Keywords arguments
        data -- Optional String or Buffer to write before ending the response

        """
        if data is None:
            self.http_server_response.end()
        else:
            self.http_server_response.end(data)

    def close(self):
        """ Close the underlying TCP connection """
        self.http_server_response.close()

class BufferHandler(org.vertx.java.core.Handler):
    def __init__(self, handler):
        self.handler = handler
        return

    def handle(self, buffer):
        self.handler(Buffer(buffer))

class HttpServerRequestHandler(org.vertx.java.core.Handler):
    """ A handler for Http Server Requests"""
    def __init__(self, handler):
        self.handler = handler
        return

    def handle(self, req):
        """ Called when a request is being handled. Argument is a HttpServerRequest object """
        self.handler(HttpServerRequest(req))

class ServerWebSocketHandler(org.vertx.java.core.Handler):
    """ A handler for WebSocket Server Requests"""
    def __init__(self, handler):
        self.handler = handler
        return

    def handle(self, req):
        """ Calls the Handler with the ServerWebSocket when connected """
        self.handler(ServerWebSocket(req))

class ClosedHandler(org.vertx.java.core.Handler):
    """ Closed connection handler """
    def __init__(self, handler):
        self.handler = handler
        return

    def handle(self):
        """ Call the handler when a connection is closed """
        self.handler()

class DoneHandler(org.vertx.java.core.Handler):
    """ Done handler """
    def __init__(self, handler):
        self.handler = handler
        return

    def handle(self):
        """ Call the handler when done """
        self.handler()

class WebSocket:
    """ Encapsulates an HTML 5 Websocket.

    Instances of this class are createde by an {HttpClient} instance when a client succeeds in a websocket handshake with a server.
    Once an instance has been obtained it can be used to send or receive buffers of data from the connection,
    a bit like a TCP socket.
    """
    def __init__(self, websocket):
        self.websocket = websocket

    def write_binary_frame(self, buffer):
        """ 
        Write data to the websocket as a binary frame 

        Keyword arguments
        buffer -- Buffer data to write to socket.

        """
        self.websocket.writeBinaryFrame(buffer)

    def write_text_frame(self, text):
        """ 
        Write data to the websocket as a text frame 

        Keyword arguments
        text -- text to write to socket
        """
        self.websocket.writeTextFrame(text)

    def close(self):
        """ Close the websocket """
        self.websocket.close()

    def closed_handler(self, handler):
        """ Set a closed handler on the connection, the handler receives a no parameters. 
        This can be used as a decorator. 

        Keyword arguments
        handler - The handler to be called when writing has been completed. It is wrapped in a ClosedHandler.
        """

        self.websocket.closedHandler(ClosedHandler(handler))

class ServerWebSocket(WebSocket):
    """ Instances of this class are created when a WebSocket is accepted on the server.
    It extends WebSocket and adds methods to reject the WebSocket and an
    attribute for the path.

    """
    def __init__(self, websocket):
        self.websocket = websocket

    def reject(self):
        """ Reject the WebSocket. Sends 404 to client """
        self.websocket.reject()

    @property
    def path(self):
        """ The path the websocket connect was attempted at. """
        return self.websocket.path



