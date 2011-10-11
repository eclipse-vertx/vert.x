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

require 'test/unit'
require 'vertx'
require 'utils'
include Vertx

class HttpTest < Test::Unit::TestCase

  def test_get
    http_method(false, "GET", 0, 3)
    http_method(true, "GET", 0, 3)
  end

  def test_put
    http_method(false, "PUT", 3, 3)
    http_method(true, "PUT", 3, 3)
  end

  def test_post
    http_method(false, "POST", 3, 3)
    http_method(true, "POST", 3, 3)
  end

  def test_head
    http_method(false, "POST", 3, 3)
    http_method(true, "POST", 3, 3)
  end

  def test_options
    http_method(false, "OPTIONS", 0, 0)
    http_method(true, "OPTIONS", 0, 0)
  end

  def test_delete
    http_method(false, "DELETE", 3, 0)
    http_method(true, "DELETE", 3, 0)
  end

  def test_trace
    http_method(false, "TRACE", 0, 0)
    http_method(true, "TRACE", 0, 0)
  end

  # TODO non-chunked, this currently only tests chunked
  def http_method(ssl, method, client_chunks, server_chunks)

    latch1 = Utils::Latch.new(1)

    Vertx::go {
      server = HttpServer.new
      if ssl
        server.ssl = true
        server.key_store_path = '../resources/keystores/server-keystore.jks'
        server.key_store_password = 'wibble'
        server.trust_store_path = '../resources/keystores/server-truststore.jks'
        server.trust_store_password = 'wibble'
        server.client_auth_required = true
      end

      server.request_handler { |req|
        assert(req.headers['foo'] == 'bar')
        req.response.put_header('wibble', 'quark')
        chunk_count = 0
        req.data_handler { |data|
          assert("client-chunk-#{chunk_count}" == data.to_s)
          chunk_count += 1
        }
        req.response.chunked = true
        req.end_handler {
          for i in 0..server_chunks - 1 do
            req.response.write_str("server-chunk-#{i}")
          end
          req.response.end
        }
      }
      server.listen(8080)

      client = HttpClient.new
      client.port = 8080;
      if ssl
        client.ssl = true
        client.key_store_path = '../resources/keystores/client-keystore.jks'
        client.key_store_password = 'wibble'
        client.trust_store_path = '../resources/keystores/client-truststore.jks'
        client.trust_store_password = 'wibble'
      end

      request = client.request(method, "/someurl") { |resp|
        assert(200 == resp.status_code)
        assert('quark' == resp.headers['wibble'])
        chunk_count = 0
        resp.data_handler { |data|
          assert("server-chunk-#{chunk_count}" == data.to_s)
          chunk_count += 1
        }

        resp.end_handler {
          assert(chunk_count = server_chunks)
          server.close {
            client.close
            latch1.countdown
          }
        }
      }

      request.chunked = true
      request.put_header('foo', 'bar')

      for i in 0..client_chunks - 1 do
        request.write_str("client-chunk-#{i}")
      end

      request.end
    }

    assert(latch1.await(5))

  end
end