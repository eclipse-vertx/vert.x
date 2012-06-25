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

require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

@tu.check_context

def test_echo

  @server = NetServer.new.connect_handler { |socket|
    @tu.check_context
    socket.data_handler { |data|
      @tu.check_context
      socket.write_buffer(data) # Just echo it back
    }
  }.listen(8080)

  @client = NetClient.new.connect(8080, "localhost") { |socket|
    @tu.check_context
    sends = 10
    size = 100

    sent = Buffer.create()
    received = Buffer.create()

    socket.data_handler { |data|
      @tu.check_context
      received.append_buffer(data)

      if received.length == sends * size
        @tu.azzert(TestUtils::buffers_equal(sent, received))
        @tu.test_complete
      end
    }

    socket.drain_handler {
      @tu.check_context
      #puts "drained\n"
    }

    socket.end_handler {
      @tu.check_context
      #puts "end\n"
    }

    socket.pause
    socket.resume
    socket.write_queue_full?
    socket.write_queue_max_size=100000

    (1..sends).each { |i|
      data = TestUtils::gen_buffer(size)
      sent.append_buffer(data)
      socket.write_buffer(data)
    }
  }

end

def test_echo_ssl

  # Let's do full SSL with client auth

  @server = NetServer.new
  @server.ssl = true
  @server.key_store_path = './src/tests/keystores/server-keystore.jks'
  @server.key_store_password = 'wibble'
  @server.trust_store_path = './src/tests/keystores/server-truststore.jks'
  @server.trust_store_password = 'wibble'
  @server.client_auth_required = true

  @server.connect_handler { |socket|
    @tu.check_context
    socket.data_handler { |data|
      @tu.check_context
      socket.write_buffer(data) # Just echo it back
    }
  }.listen(8080)

  @client = NetClient.new
  @client.ssl = true
  @client.key_store_path = './src/tests/keystores/client-keystore.jks'
  @client.key_store_password = 'wibble'
  @client.trust_store_path = './src/tests/keystores/client-truststore.jks'
  @client.trust_store_password = 'wibble'

  @client.connect(8080, "localhost") { |socket|
    @tu.check_context
    sends = 10
    size = 100

    sent = Buffer.create()
    received = Buffer.create()

    socket.data_handler { |data|
      @tu.check_context
      received.append_buffer(data)

      if received.length == sends * size
        @tu.azzert(TestUtils::buffers_equal(sent, received))

        @tu.test_complete

      end
    }

    #Just call the methods. Real testing is done in java tests

    socket.drain_handler {
      @tu.check_context
      #puts "drained\n"
    }

    socket.end_handler {
      @tu.check_context
      #puts "end\n"
    }

    socket.closed_handler {
      @tu.check_context
      #puts "closed\n"
    }

    socket.pause
    socket.resume
    socket.write_queue_full?
    socket.write_queue_max_size=100000

    (1..sends).each { |i|
      data = TestUtils::gen_buffer(size)
      sent.append_buffer(data)
      socket.write_buffer(data)
    }
  }
end

def test_write_str

  @server = NetServer.new.connect_handler { |socket|
    @tu.check_context
    socket.data_handler { |data|
      @tu.check_context
      socket.write_buffer(data) # Just echo it back
    }
  }.listen(8080)

  @client = NetClient.new.connect(8080, "localhost") { |socket|
    @tu.check_context
    sent = 'some-string'
    received = Buffer.create()

    socket.data_handler { |data|
      @tu.check_context
      received.append_buffer(data)

      if received.length == sent.length
        @tu.azzert(sent == received.to_s)
        @tu.test_complete
      end
    }

    socket.write_str(sent)
  }

end

# Basically we just need to touch all methods, the real testing occurs in the Java tests
def test_methods
  @server = NetServer.new

  @server.ssl=true
  @server.key_store_path="foo.jks"
  @server.key_store_password="blah"
  @server.trust_store_path="bar.jks"
  @server.trust_store_password="blah"
  @server.send_buffer_size=123123
  @server.receive_buffer_size=218123
  @server.tcp_keep_alive=true
  @server.reuse_address=true
  @server.so_linger = true
  @server.traffic_class=123

  @server.connect_handler { |sock|}

  @server.close

  @client = NetClient.new

  @client.ssl=true
  @client.key_store_path="foo.jks"
  @client.key_store_password="blah"
  @client.trust_store_path="bar.jks"
  @client.trust_store_password="blah"
  @client.trust_all=true
  @client.send_buffer_size=123123
  @client.receive_buffer_size=218123
  @client.tcp_keep_alive=true
  @client.reuse_address=true
  @client.so_linger = true
  @client.traffic_class=123

  @client.close

  @tu.test_complete
end

def vertx_stop
  @tu.unregister_all
  @client.close
  @server.close do
    @tu.app_stopped
  end
end

@tu.register_all(self)
@tu.app_ready
