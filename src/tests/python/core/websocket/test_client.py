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

import vertx
from test_utils import TestUtils
from core.buffer import Buffer

tu = TestUtils()
tu.check_context()

server = vertx.create_http_server()
client = vertx.create_http_client()
client.port = 8080

class WebsocketTest(object):
    def test_echo_binary(self):
        self.echo(True)

    def test_echo_text(self):
        self.echo(False)

    def echo(self, binary):
        @server.websocket_handler
        def websocket_handler(ws):
            tu.check_context()
            @ws.data_handler
            def data_handler(buff):
                tu.check_context()
                ws.write_buffer(buff)
            
        server.listen(8080)

        if binary:
            self.buff = TestUtils.gen_buffer(1000)
        else:
            self.str_ = TestUtils.random_unicode_string(1000)

        def connect_handler(ws):
            tu.check_context()
            received = Buffer.create()

            @ws.data_handler
            def data_handler(buff):
                tu.check_context()
                received.append_buffer(buff)
                if received.length == buff.length:
                    tu.azzert(TestUtils.buffers_equal(buff, received))
                    tu.test_complete()
        
            if binary:
                ws.write_binary_frame(self.buff)
            else:
                ws.write_text_frame(self.str_)
        client.connect_web_socket("/someurl", connect_handler)


    def test_write_from_connect_handler(self):
        @server.websocket_handler
        def websocket_handler(ws):
            tu.check_context()
            ws.write_text_frame("foo")
  
        server.listen(8080)

        def connect_handler(ws):
            tu.check_context()

            @ws.data_handler
            def data_handler(buff):
                tu.check_context()
                tu.azzert("foo" == buff.to_string())
                tu.test_complete()
        client.connect_web_socket("/someurl", connect_handler)
    
    def test_close(self):
        @server.websocket_handler
        def websocket_handler(ws):
            tu.check_context()
            @ws.data_handler
            def data_handler(buff):
                ws.close()
    
        server.listen(8080)
        def connect_handler(ws):
            tu.check_context()
            @ws.closed_handler
            def closed_handler():
                tu.test_complete()
            ws.write_text_frame("foo")
        
        client.connect_web_socket("/someurl",connect_handler)
    
    def test_close_from_connect(self):
        @server.websocket_handler
        def websocket_handler(ws):
            tu.check_context()
            ws.close()

        server.listen(8080)
        def connect_handler(ws):
            tu.check_context()
            @ws.closed_handler
            def closed_handler():
                tu.test_complete()
        client.connect_web_socket("/someurl", connect_handler)

def vertx_stop():
    tu.check_context()
    tu.unregister_all()
    client.close()
    @server.close
    def close():
        tu.app_stopped()
  
tu.register_all(WebsocketTest())
tu.app_ready()
