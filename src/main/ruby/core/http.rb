# Copyright 2002-2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use
# this file except in compliance with the License. You may obtain a copy of the
# License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.

include Java
require "core/buffer"
java_import org.nodex.core.http.HttpServer
java_import org.nodex.core.http.HttpClient
java_import org.nodex.core.http.HttpRequestHandler


module Http
  class Server

    def initialize(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_server = HttpServer.new
      request_handler(hndlr)
    end

    def request_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_server.requestHandler{ |j_req| hndlr.call(ServerRequest.new(j_req)) }
    end

    def websocket_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_server.websocketHandler{ |ws| hndlr.call(Websocket.new(ws)) }
    end

    def listen(port, host = "0.0.0.0")
      @j_server.listen(port, host)
    end

    def ssl=(val)
      @j_server.setSSL(val)
    end

    def key_store_path=(val)
      @j_server.setKeyStorePath(val)
    end

    def key_store_password=(val)
      @j_server.setKeyStorePassword(val)
    end

    def trust_store_path=(val)
      @j_server.setTrustStorePath(val)
    end

    def trust_store_password=(val)
      @j_server.setTrustStorePassword(val)
    end

    def client_auth_required=(val)
      @j_server.setClientAuthRequired(val)
    end

    def close(&hndlr)
      @j_server.close(hndlr)
    end
  end

  class Client
    def initialize
      @j_client = HttpClient.new
    end

    def exception_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_client.exceptionHandler(hndlr)
    end

    def max_pool_size=(val)
      @j_client.setMaxPoolSize(val)
    end

    def max_pool_size
      @j_client.getMaxPoolSize
    end

    def keep_alive=(val)
      @j_client.setKeepAlive(val)
    end

    def ssl=(val)
      @j_client.setSSL(val)
    end

    def key_store_path=(val)
      @j_client.setKeyStorePath(val)
    end

    def key_store_password=(val)
      @j_client.setKeyStorePassword(val)
    end

    def trust_store_path=(val)
      @j_client.setTrustStorePath(val)
    end

    def trust_store_password=(val)
      @j_client.setTrustStorePassword(val)
    end

    def trust_all=(val)
      @j_client.setTrustAll(val)
    end

    def port=(val)
      @j_client.setPort(val)
    end

    def host=(val)
      @j_client.setHost(val)
    end

    def connect_web_socket(uri, headers = nil, &hndlr)
      @j_client.connectWebsocket(uri, headers) { |j_ws| hndlr.call(Websocket.new(j_ws)) }
    end

    def get_now(uri, headers = nil, &hndlr)
      @j_client.getNow(uri, headers, resp_handler(hndlr))
    end

    def options(uri, &hndlr)
      ClientRequest.new(@j_client.options(uri, resp_handler(hndlr)))
    end

    def get(uri, &hndlr)
      ClientRequest.new(@j_client.get(uri, resp_handler(hndlr)))
    end

    def head(uri, &hndlr)
      ClientRequest.new(@j_client.head(uri, resp_handler(hndlr)))
    end

    def post(uri, &hndlr)
      ClientRequest.new(@j_client.post(uri, resp_handler(hndlr)))
    end

    def put(uri, &hndlr)
      ClientRequest.new(@j_client.put(uri, resp_handler(hndlr)))
    end

    def delete(uri, &hndlr)
      ClientRequest.new(@j_client.delete(uri, resp_handler(hndlr)))
    end

    def trace(uri, &hndlr)
      ClientRequest.new(@j_client.trace(uri, resp_handler(hndlr)))
    end

    def connect(uri, &hndlr)
      ClientRequest.new(@j_client.connect(uri, resp_handler(hndlr)))
    end

    def request(method, uri, &hndlr)
      ClientRequest.new(@j_client.request(method, uri, resp_handler(hndlr)))
    end

    def close
      @j_client.close
    end

    def resp_handler(hndlr)
      Proc.new{ |j_resp| hndlr.call(ClientResponse.new(j_resp)) }
    end

    private :resp_handler

  end

  class ClientRequest
    def initialize(j_req)
      @j_req = j_req
    end

    private :initialize

    def put_header(key, value)
      @j_req.putHeader(key, value)
      self
    end

    def add_header(key, value)
      @j_req.addHeader(key, value)
      self
    end

    def put_all_headers(hash)
      @j_req.putAllHeaders(hash)
      self
    end

    def get_header(key)
      @j_req.getHeader(key)
    end

    def get_headers(key)
      #TODO
    end

    def get_header_names
      names = @j_req.getHeaderNames
      iter = names.iterator
      rset = Set.new
      while iter.hasNext
        name = iter.next
        rset.add(name)
      end
      rset
    end

    def write_buffer(chunk, &hndlr)
      @j_req.writeBuffer(chunk._to_java_buffer)
      self
    end

    def write_str(str, enc = nil, &hndlr)
      if enc == nil
        @j_req.write(str)
      else
        @j_req.write(str, enc)
      end
      self
    end

    def write_queue_max_size=(val)
      @j_req.setWriteQueueMaxSize(val)
      self
    end

    def write_queue_full?
      @j_req.writeQueueFull
    end

    def drain_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_req.drainHandler(hndlr)
    end

    def exception_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_req.exceptionHandler(hndlr)
    end

    def send_head
      @j_req.sendHead
    end

    def end
      @j_req.end
    end

    def chunked=(val)
      @j_req.setChunked(val)
    end

    def content_length=(val)
      @j_req.setContentLength(val)
    end

  end

  class ClientResponse
    def initialize(j_resp)
      @j_resp = j_resp
      @status_code = j_resp.statusCode
    end

    def status_code
      @status_code
    end

    def get_header(key)
      @j_resp.getHeader(key)
    end

    def get_headers(key)
      # TODO
    end

    def get_header_names
      names = @j_resp.getHeaderNames
      iter = names.iterator
      rset = Set.new
      while iter.hasNext
        name = iter.next
        rset.add(name)
      end
      rset
    end

    def get_trailer(key)
      @j_resp.getTrailer(key)
    end

    def get_trailers(key)
      # TODO
    end

    def get_trailer_names
      names = @j_resp.getTrailerNames
      iter = names.iterator
      rset = Set.new
      while iter.hasNext
        name = iter.next
        rset.add(name)
      end
      rset
    end

    def data_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_resp.dataHandler{ |j_buff| hndlr.call(Buffer.new(j_buff)) }
    end

    def end_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_resp.endHandler(hndlr)
    end

    def exception_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_resp.exceptionHandler(hndlr)
    end

    def continue_handler
      hndlr = proc if proc
      @j_resp.continueHandler(hndlr)
    end

    def pause
      @j_resp.pause
    end

    def resume
      @j_resp.resume
    end

    private :initialize
  end

  class ServerRequest
    def initialize(j_req)
      @j_req = j_req
      @resp = ServerResponse.new(@j_req.response)
    end

    def response
      @resp
    end

    def get_header(key)
      @j_req.getHeader(key)
    end

    def get_headers(key)
      # TODO
    end

    def get_header_names
      names = @j_req.getHeaderNames
      iter = names.iterator
      rset = Set.new
      while iter.hasNext
        name = iter.next
        rset.add(name)
      end
      rset
    end

    def data_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_req.dataHandler{ |j_buff| hndlr.call(Buffer.new(j_buff)) }
    end

    def end_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_req.endHandler(hndlr)
    end

    def exception_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_req.exceptionHandler(hndlr)
    end

    def pause
      @j_req.pause
    end

    def resume
      @j_req.resume
    end

    private :initialize
  end

  class ServerResponse
    def initialize(j_resp)
      @j_resp = j_resp
    end

    def chunked=(val)
      @j_resp.setChunked(val)
    end

    def content_length=(val)
      @j_resp.setContentLength(val)
    end

    def status_code=(val)
      @j_resp.statusCode = val
    end

    def put_header(key, value)
      @j_resp.putHeader(key, value)
      self
    end

    def add_header(key, value)
      @j_resp.addHeader(key, value)
      self
    end

    def put_all_headers(hash)
      @j_resp.putAllHeaders(hash)
      self
    end

    def put_trailer(key, value)
      @j_resp.putTrailer(key, value)
      self
    end

    def add_trailer(key, value)
      @j_resp.addTrailer(key, value)
      self
    end

    def put_all_trailers(hash)
      @j_resp.putAllTrailers(hash)
      self
    end

    def write_queue_max_size=(val)
      @j_resp.setWriteQueueMaxSize(val)
      self
    end

    def write_queue_full?
      @j_resp.writeQueueFull
    end

    def drain_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_resp.drainHandler(hndlr)
    end

    def exception_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_resp.exceptionHandler(hndlr)
    end

    def write_buffer(chunk, &hndlr)
      @j_resp.writeBuffer(chunk._to_java_buffer)
      self
    end

    def write_str(str, enc = nil, &hndlr)
      @j_resp.write(str, enc)
      self
    end

    def end
      @j_resp.end
    end

    def send_file(path)
      @j_resp.sendFile(path)
    end

    private :initialize

  end

  def Websocket
    def initialize(j_ws)
      @j_ws = j_ws
    end

    def write_binary_frame(buffer)
      @j_ws.writeBinaryFrame(buffer._to_java_buffer)
    end

    def write_text_frame(str)
      @j_ws.writeTextFrame(str)
    end

    def data_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_ws.dataHandler{ |j_buff| hndlr.call(Buffer.new(j_buff)) }
    end

    def exception_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_ws.exceptionHandler(hndlr)
    end

    def drain_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_ws.drainHandlerHandler(hndlr)
    end

    def pause
      @j_ws.pause
    end

    def resume
      @j_ws.resume
    end

    def write_queue_max_size=(val)
      @j_resp.setWriteQueueMaxSize(val)
      self
    end

    def write_queue_full?
      @j_resp.writeQueueFull
    end

    def write_buffer(buffer)
      write_binary_frame(buffer)
    end

    def close
      @j_resp.close
    end

    private :initialize

  end
end