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
@server = HttpServer.new
@client = HttpClient.new
@client.port = 8080
@logger = Vertx.logger

# This is just a basic test. Most testing occurs in the Java tests

def test_get
  http_method(false, "GET", false)
end

def test_get_ssl
  http_method(true, "GET", false)
end

def test_put
  http_method(false, "PUT", false)
end

def test_put_ssl
  http_method(true, "PUT", false)
end

def test_post
  http_method(false, "POST", false)
end

def test_post_ssl
  http_method(true, "POST", false)
end

def test_head
  http_method(false, "HEAD", false)
end

def test_head_ssl
  http_method(true, "HEAD", false)
end

def test_options
  http_method(false, "OPTIONS", false)
end

def test_options_ssl
  http_method(true, "OPTIONS", false)
end

def test_delete
  http_method(false, "DELETE", false)
end

def test_delete_ssl
  http_method(true, "DELETE", false)
end

def test_trace
  http_method(false, "TRACE", false)
end

def test_trace_ssl
  http_method(true, "TRACE", false)
end

def test_connect
  http_method(false, "CONNECT", false)
end

def test_connect_ssl
  http_method(true, "CONNECT", false)
end

def test_patch
  http_method(false, "PATCH", false)
end

def test_patch_ssl
  http_method(true, "PATCH", false)
end




def test_get_chunked
  http_method(false, "GET", true)
end

def test_get_ssl_chunked
  http_method(true, "GET", true)
end

def test_put_chunked
  http_method(false, "PUT", true)
end

def test_put_ssl_chunked
  http_method(true, "PUT", true)
end

def test_post_chunked
  http_method(false, "POST", true)
end

def test_post_ssl_chunked
  http_method(true, "POST", true)
end

def test_head_chunked
  http_method(false, "HEAD", true)
end

def test_head_ssl_chunked
  http_method(true, "HEAD", true)
end

def test_options_chunked
  http_method(false, "OPTIONS", true)
end

def test_options_ssl_chunked
  http_method(true, "OPTIONS", true)
end

def test_delete_chunked
  http_method(false, "DELETE", true)
end

def test_delete_ssl_chunked
  http_method(true, "DELETE", true)
end

def test_trace_chunked
  http_method(false, "TRACE", true)
end

def test_trace_ssl_chunked
  http_method(true, "TRACE", true)
end

def test_connect_chunked
  http_method(false, "CONNECT", true)
end

def test_connect_ssl_chunked
  http_method(true, "CONNECT", true)
end

def test_patch_chunked
  http_method(false, "PATCH", true)
end

def test_patch_ssl_chunked
  http_method(true, "PATCH", true)
end

def http_method(ssl, method, chunked)

  # @logger.info("in http method #{method}")

  if ssl
    @server.ssl = true
    @server.key_store_path = './src/tests/keystores/server-keystore.jks'
    @server.key_store_password = 'wibble'
    @server.trust_store_path = './src/tests/keystores/server-truststore.jks'
    @server.trust_store_password = 'wibble'
    @server.client_auth_required = true
  end

  path = "/someurl/blah.html"
  query = "param1=vparam1&param2=vparam2"
  uri = "http://localhost:8080" + path + "?" + query;

  @server.request_handler do |req|
    @tu.check_context
    @tu.azzert(req.uri == uri)
    @tu.azzert(req.method == method)
    @tu.azzert(req.path == path)
    @tu.azzert(req.query == query)
    @tu.azzert(req.headers['header1'] == 'vheader1')
    @tu.azzert(req.headers['header2'] == 'vheader2')
    @tu.azzert(req.params['param1'] == 'vparam1')
    @tu.azzert(req.params['param2'] == 'vparam2')
    req.response.put_header('rheader1', 'vrheader1')
    req.response.put_header('rheader2', 'vrheader2')
    body = Buffer.create()
    req.data_handler do |data|
      @tu.check_context
      body.append_buffer(data)
    end
    req.response.chunked = chunked
    req.end_handler do
      @tu.check_context
      req.response.put_header('Content-Length', body.length()) if !chunked
      req.response.write_buffer(body)
      if chunked
        req.response.put_trailer('trailer1', 'vtrailer1')
        req.response.put_trailer('trailer2', 'vtrailer2')
      end
      req.response.end
    end
  end
  @server.listen(8080)

  if ssl
    @client.ssl = true
    @client.key_store_path = './src/tests/keystores/client-keystore.jks'
    @client.key_store_password = 'wibble'
    @client.trust_store_path = './src/tests/keystores/client-truststore.jks'
    @client.trust_store_password = 'wibble'
  end

  sent_buff = TestUtils.gen_buffer(1000)

  request = @client.request(method, uri) do |resp|
    @tu.check_context
    @tu.azzert(200 == resp.status_code)

    @tu.azzert('vrheader1' == resp.headers['rheader1'])
    @tu.azzert('vrheader2' == resp.headers['rheader2'])
    body = Buffer.create()
    resp.data_handler do |data|
      @tu.check_context
      body.append_buffer(data)
    end

    resp.end_handler do
      @tu.check_context
      @tu.azzert(TestUtils.buffers_equal(sent_buff, body))
      if chunked
        @tu.azzert('vtrailer1' == resp.trailers['trailer1'])
        @tu.azzert('vtrailer2' == resp.trailers['trailer2'])
      end
      @tu.test_complete
    end
  end

  request.chunked = chunked;
  request.put_header('header1', 'vheader1')
  request.put_header('header2', 'vheader2')
  request.put_header('Content-Length', sent_buff.length()) if !chunked

  request.write_buffer(sent_buff)

  request.end
end

def vertx_stop
  @tu.check_context
  @tu.unregister_all
  @client.close
  @server.close do
    @tu.app_stopped
  end
end

@tu.register_all(self)
@tu.app_ready

