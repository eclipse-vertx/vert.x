require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new
@tu.check_context
@server = HttpServer.new
@client = HttpClient.new
@client.port = 8080

# This is just a basic test. Most testing occurs in the Java tests

def test_get
  http_method(false, "GET", 0, 3)
end

def test_get_ssl
  http_method(true, "GET", 0, 3)
end

def test_put
  http_method(false, "PUT", 3, 3)
end

def test_put_ssl
  http_method(true, "PUT", 3, 3)
end

def test_post
  http_method(false, "POST", 3, 3)
end

def test_post_ssl
  http_method(true, "POST", 3, 3)
end

def test_head
  http_method(false, "HEAD", 3, 3)
end

def test_head_ssl
  http_method(true, "HEAD", 3, 3)
end

def test_options
  http_method(false, "OPTIONS", 0, 0)
end

def test_options_ssl
  http_method(true, "OPTIONS", 0, 0)
end

def test_delete
  http_method(false, "DELETE", 3, 0)
end

def test_delete_ssl
  http_method(true, "DELETE", 3, 0)
end

def test_trace
  http_method(false, "TRACE", 0, 0)
end

def test_trace_ssl
  http_method(true, "TRACE", 0, 0)
end

def test_connect
  http_method(false, "CONNECT", 0, 0)
end

def test_connect_ssl
  http_method(true, "CONNECT", 0, 0)
end

def test_patch
  http_method(false, "PATCH", 0, 0)
end

def test_patch_ssl
  http_method(true, "PATCH", 0, 0)
end

def http_method(ssl, method, client_chunks, server_chunks)

  if ssl
    @server.ssl = true
    @server.key_store_path = './src/tests/keystores/server-keystore.jks'
    @server.key_store_password = 'wibble'
    @server.trust_store_path = './src/tests/keystores/server-truststore.jks'
    @server.trust_store_password = 'wibble'
    @server.client_auth_required = true
  end

  @server.request_handler do |req|
    @tu.check_context
    @tu.azzert(req.headers['foo'] == 'bar')
    req.response.put_header('wibble', 'quark')
    chunk_count = 0
    req.data_handler do |data|
      @tu.check_context
      @tu.azzert("client-chunk-#{chunk_count}" == data.to_s)
      chunk_count += 1
    end
    req.response.chunked = true
    req.end_handler do
      @tu.check_context
      for i in 0..server_chunks - 1 do
        req.response.write_str("@server-chunk-#{i}")
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

  request = @client.request(method, "/someurl") do |resp|
    @tu.check_context
    @tu.azzert(200 == resp.status_code)
    @tu.azzert('quark' == resp.headers['wibble'])
    chunk_count = 0
    resp.data_handler do |data|
      @tu.check_context
      @tu.azzert("@server-chunk-#{chunk_count}" == data.to_s)
      chunk_count += 1
    end

    resp.end_handler do
      @tu.check_context
      @tu.azzert(chunk_count == server_chunks)
      @tu.test_complete
    end
  end

  request.chunked = true
  request.put_header('foo', 'bar')

  for i in 0..client_chunks - 1 do
    request.write_str("client-chunk-#{i}")
  end

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
