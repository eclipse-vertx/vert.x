require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

def test_echo

  @server = NetServer.new.connect_handler { |socket|
    socket.data_handler { |data|
      socket.write_buffer(data) # Just echo it back
    }
  }.listen(8080)

  @client = NetClient.new.connect(8080, "localhost") { |socket|

    sends = 10
    size = 100

    sent = Buffer.create(0)
    received = Buffer.create(0)

    socket.data_handler { |data|

      received.append_buffer(data)

      if received.length == sends * size
        @tu.azzert(TestUtils::buffers_equal(sent, received))
        @tu.test_complete
      end
    }

    socket.drain_handler {
      #puts "drained\n"
    }

    socket.end_handler {
      #puts "end\n"
    }

    (1..sends).each { |i|
      data = TestUtils::gen_buffer(size)
      sent.append_buffer(data)
      socket.write_buffer(data)
    }
  }

end

def test_echo_ssl

  # Let's do full SSL with client auth

  @server = NetServer.new;
  @server.ssl = true
  @server.key_store_path = './src/newtests/keystores/server-keystore.jks'
  @server.key_store_password = 'wibble'
  @server.trust_store_path = './src/newtests/keystores/server-truststore.jks'
  @server.trust_store_password = 'wibble'
  @server.client_auth_required = true

  @server.connect_handler { |socket|
    socket.data_handler { |data|
      socket.write_buffer(data) # Just echo it back
    }
  }.listen(8080)

  @client = NetClient.new
  @client.ssl = true
  @client.key_store_path = './src/newtests/keystores/client-keystore.jks'
  @client.key_store_password = 'wibble'
  @client.trust_store_path = './src/newtests/keystores/client-truststore.jks'
  @client.trust_store_password = 'wibble'

  @client.connect(8080, "localhost") { |socket|

    sends = 10
    size = 100

    sent = Buffer.create(0)
    received = Buffer.create(0)

    socket.data_handler { |data|

      received.append_buffer(data)

      if received.length == sends * size
        @tu.azzert(TestUtils::buffers_equal(sent, received))

        @tu.test_complete

      end
    }

    #Just call the methods. Real testing is done in java tests

    socket.drain_handler {
      #puts "drained\n"
    }

    socket.end_handler {
      #puts "end\n"
    }

    socket.closed_handler {
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
