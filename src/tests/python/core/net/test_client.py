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
server = None

class NetTest(object):

    def test_echo(self):
        global server, client
        server = vertx.create_net_server()
        @server.connect_handler
        def connect_handler(socket):
            tu.check_context()

            @socket.data_handler
            def data_handler(data):
                tu.check_context()
                socket.write_buffer(data) # Just echo it back

        server.listen(8080)

        client = vertx.create_net_client()
        def client_connect_handler(socket):
            tu.check_context()
            sends = 10
            size = 100

            sent = Buffer.create()
            received = Buffer.create()

            @socket.data_handler
            def data_handler(data):
                tu.check_context()
                received.append_buffer(data)
                if received.length == sends * size:
                    tu.azzert(TestUtils.buffers_equal(sent, received))
                    tu.test_complete()
            @socket.drain_handler
            def drain_handler(stream):
                tu.check_context()
                #print "drained\n"

            @socket.end_handler
            def end_handler(stream):
                tu.check_context()
                #print "end\n"

            socket.pause()
            socket.resume()
            socket.write_queue_full
            socket.write_queue_max_size = 100000

            for i in range(0, sends):
                data = TestUtils.gen_buffer(size)
                sent.append_buffer(data)
                socket.write_buffer(data)
            
        client.connect(8080, "localhost", client_connect_handler)


    def test_echo_ssl(self):
        global server, client
        # Let's do full SSL with client auth

        server = vertx.create_net_server()
        server.ssl = True
        server.key_store_path = './src/tests/keystores/server-keystore.jks'
        server.key_store_password = 'wibble'
        server.trust_store_path = './src/tests/keystores/server-truststore.jks'
        server.trust_store_password = 'wibble'
        server.client_auth_required = True
        
        @server.connect_handler
        def connect_handler(socket):
            tu.check_context()

            @socket.data_handler
            def data_handler(data):
                tu.check_context()
                socket.write_buffer(data) # Just echo it back
        
        server.listen(8080)

        client = vertx.create_net_client()
        client.ssl = True
        client.key_store_path = './src/tests/keystores/client-keystore.jks'
        client.key_store_password = 'wibble'
        client.trust_store_path = './src/tests/keystores/client-truststore.jks'
        client.trust_store_password = 'wibble'

        def client_connect_handler(socket):
            tu.check_context()
            sends = 10
            size = 100

            sent = Buffer.create()
            received = Buffer.create()

            @socket.data_handler
            def data_handler(data):
                tu.check_context()
                received.append_buffer(data)

                if received.length == sends * size:
                    tu.azzert(TestUtils.buffers_equal(sent, received))
                    tu.test_complete()

            #Just call the methods. Real testing is done in java tests
            @socket.drain_handler
            def drain_handler(stream): 
                tu.check_context()
                #print "drained\n"

            @socket.end_handler
            def end_handler(stream):
                tu.check_context()
                #print "end\n"

            @socket.closed_handler
            def closed_handler():
                tu.check_context()
                #print "closed\n"

            socket.pause()
            socket.resume()
            socket.write_queue_full
            socket.write_queue_max_size = 100000

            for i in range(0, sends):
                data = TestUtils.gen_buffer(size)
                sent.append_buffer(data)
                socket.write_buffer(data)

        client.connect(8080, "localhost", client_connect_handler)

    def test_write_str(self):
        global server, client
        server = vertx.create_net_server()

        @server.connect_handler
        def connect_handler(socket):
            tu.check_context()

            @socket.data_handler
            def data_handler(data):
                tu.check_context()
                socket.write_buffer(data) # Just echo it back
        
        server.listen(8080)

        client = vertx.create_net_client() 

        def client_connect_handler(socket):
            tu.check_context()
            sent = 'some-string'
            received = Buffer.create()

            @socket.data_handler
            def data_handler(data):
                tu.check_context()
                received.append_buffer(data)

                if received.length == len(sent):
                    tu.azzert(sent == received.to_string())
                    tu.test_complete()

            socket.write_str(sent)

        client.connect(8080, "localhost", client_connect_handler)

    # Basically we just need to touch all methods, the real testing occurs in the Java tests
    def test_methods(self):
        global server, client
        server = vertx.create_net_server()

        server.ssl=True
        server.key_store_path="foo.jks"
        server.key_store_password="blah"
        server.trust_store_path="bar.jks"
        server.trust_store_password="blah"
        server.send_buffer_size=123123
        server.receive_buffer_size=218123
        server.tcp_keep_alive=True
        server.reuse_address=True
        server.so_linger = True
        server.traffic_class=123

        @server.connect_handler
        def connect_handler(sock): pass

        server.close()

        client = vertx.create_net_client()

        client.ssl=True
        client.key_store_path="foo.jks"
        client.key_store_password="blah"
        client.trust_store_path="bar.jks"
        client.trust_store_password="blah"
        client.trust_all=True
        client.send_buffer_size=123123
        client.receive_buffer_size=218123
        client.tcp_keep_alive=True
        client.reuse_address=True
        client.so_linger = True
        client.traffic_class=123

        client.close()

        tu.test_complete()

def vertx_stop():
    tu.unregister_all()
    client.close()
    def close_handler():
        tu.app_stopped()
    server.close(close_handler)
    

tu.register_all(NetTest())
tu.app_ready()
