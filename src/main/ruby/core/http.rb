# Copyright 2011 VMware, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'core/streams'
require 'core/ssl_support'

module Nodex

  class HttpServer

    include SSLSupport

    def initialize
      @j_del = org.nodex.java.core.http.HttpServer.new
    end

    def request_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_del.requestHandler { |j_del| hndlr.call(HttpServerRequest.new(j_del)) }
    end

    def websocket_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_del.websocketHandler { |ws| hndlr.call(Websocket.new(ws)) }
    end

    def listen(port, host = "0.0.0.0")
      @j_del.listen(port, host)
    end

    def client_auth_required=(val)
      @j_del.setClientAuthRequired(val)
    end

    def close(&hndlr)
      @j_del.close(hndlr)
    end
  end

  class HttpClient

    include SSLSupport

    def initialize
      @j_del = org.nodex.java.core.http.HttpClient.new
    end

    def exception_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_del.exceptionHandler(hndlr)
    end

    def max_pool_size=(val)
      @j_del.setMaxPoolSize(val)
    end

    def max_pool_size
      @j_del.getMaxPoolSize
    end

    def keep_alive=(val)
      @j_del.setKeepAlive(val)
    end

    def trust_all=(val)
      @j_del.setTrustAll(val)
    end

    def port=(val)
      @j_del.setPort(val)
    end

    def host=(val)
      @j_del.setHost(val)
    end

    def connect_web_socket(uri, headers = nil, &hndlr)
      @j_del.connectWebsocket(uri, headers) { |j_ws| hndlr.call(Websocket.new(j_ws)) }
    end

    def get_now(uri, headers = nil, &hndlr)
      @j_del.getNow(uri, headers, resp_handler(hndlr))
    end

    def options(uri, &hndlr)
      HttpClientRequest.new(@j_del.options(uri, resp_handler(hndlr)))
    end

    def get(uri, &hndlr)
      HttpClientRequest.new(@j_del.get(uri, resp_handler(hndlr)))
    end

    def head(uri, &hndlr)
      HttpClientRequest.new(@j_del.head(uri, resp_handler(hndlr)))
    end

    def post(uri, &hndlr)
      HttpClientRequest.new(@j_del.post(uri, resp_handler(hndlr)))
    end

    def put(uri, &hndlr)
      HttpClientRequest.new(@j_del.put(uri, resp_handler(hndlr)))
    end

    def delete(uri, &hndlr)
      HttpClientRequest.new(@j_del.delete(uri, resp_handler(hndlr)))
    end

    def trace(uri, &hndlr)
      HttpClientRequest.new(@j_del.trace(uri, resp_handler(hndlr)))
    end

    def connect(uri, &hndlr)
      HttpClientRequest.new(@j_del.connect(uri, resp_handler(hndlr)))
    end

    def request(method, uri, &hndlr)
      HttpClientRequest.new(@j_del.request(method, uri, resp_handler(hndlr)))
    end

    def close
      @j_del.close
    end

    def resp_handler(hndlr)
      Proc.new { |j_del| hndlr.call(HttpClientResponse.new(j_del)) }
    end

    private :resp_handler

  end

  class HttpClientRequest

    include WriteStream

    def initialize(j_del)
      @j_del = j_del
    end

    def put_header(key, value)
      @j_del.putHeader(key, value)
      self
    end

    def put_all_headers(headers)
      headers.each_pair do |k, v|
        @j_del.putHeader(k, v)
      end
      self
    end

    def write_buffer(chunk, &hndlr)
      @j_del.writeBuffer(chunk._to_java_buffer)
      self
    end

    def write_str(str, enc = nil, &hndlr)
      if enc == nil
        @j_del.write(str)
      else
        @j_del.write(str, enc)
      end
      self
    end

    def send_head
      @j_del.sendHead
      self
    end

    def end
      @j_del.end
    end

    def chunked=(val)
      @j_del.setChunked(val)
      self
    end

  end

  class HttpClientResponse

    include ReadStream

    def initialize(j_del)
      @j_del = j_del
      @status_code = j_del.statusCode
    end

    def status_code
      @status_code
    end

    def header(key)
      @j_del.getHeader(key)
    end

    def headers
      if @headers == nil
        hdrs = @j_del.getHeaders
        iter = hdrs.entrySet.iterator
        @headers = {}
        while iter.hasNext
          entry = iter.next
          @headers[entry.getKey] = entry.getValue
        end
      end
      @headers
    end

    def header_names
      if @header_names == nil
        names = @j_del.getHeaderNames
        iter = names.iterator
        @header_names = Set.new
        while iter.hasNext
          name = iter.next
          @header_names.add(name)
        end
      end
      @header_names
    end

    def trailer(key)
      @j_del.getTrailer(key)
    end

    def trailers
      if @trailers == nil
        hdrs = @j_del.getHeaders
        iter = hdrs.iterator
        @trailers = {}
        while iter.hasNext
          entry = iter.next
          @trailers[entry.getkey] = entry.getValue
        end
      end
      @trailers
    end

    def trailer_names
      if @trailer_names == nil
        names = @j_del.getTrailerNames
        iter = names.iterator
        @trailer_names = Set.new
        while iter.hasNext
          name = iter.next
          @trailer_names.add(name)
        end
      end
      @trailer_names
    end

    def continue_handler
      hndlr = proc if proc
      @j_del.continueHandler(hndlr)
    end

  end

  class HttpServerRequest

    include ReadStream

    def initialize(j_del)
      @j_del = j_del
      @resp = HttpServerResponse.new(@j_del.response)
    end

    def method
      @j_del.method
    end

    def uri
      @j_del.uri
    end

    def response
      @resp
    end

    def header(key)
      @j_del.getHeader(key)
    end

    def headers
      if (@headers == nil)
        hdrs = @j_del.getHeaders
        iter = hdrs.entrySet.iterator
        @headers = {}
        while iter.hasNext
          entry = iter.next
          @headers[entry.getKey] = entry.getValue
        end
      end
      @headers
    end

    def header_names
      if (@header_names == nil)
        names = @j_del.getHeaderNames
        iter = names.iterator
        @header_names = Set.new
        while iter.hasNext
          name = iter.next
          @header_names.add(name)
        end
      end
      @header_names
    end

  end

  class HttpServerResponse

    include WriteStream

    def initialize(j_del)
      @j_del = j_del
    end

    def status_code=(val)
      @j_del.statusCode = val
    end

    def put_header(key, value)
      @j_del.putHeader(key, value)
      self
    end

    def put_all_headers(headers)
      headers.each_pair do |k, v|
        @j_del.putHeader(k, v)
      end
      self
    end

    def put_trailer(key, value)
      @j_del.putTrailer(key, value)
      self
    end

    def put_all_trailers(headers)
      trailers.each_pair do |k, v|
        @j_del.putTrailer(k, v)
      end
      self
    end

    def write_buffer(chunk, &hndlr)
      @j_del.writeBuffer(chunk._to_java_buffer)
      self
    end

    def write_str(str, enc = nil, &hndlr)
      @j_del.write(str, enc)
      self
    end

    def end
      @j_del.end
    end

    def send_file(path)
      @j_del.sendFile(path)
    end

    def chunked=(val)
      @j_del.setChunked(val)
      self
    end

  end

  class Websocket

    include ReadStream, WriteStream

    def uri
      @j_del.uri
    end

    def initialize(j_ws)
      @j_del = j_ws
    end

    def write_binary_frame(buffer)
      @j_del.writeBinaryFrame(buffer._to_java_buffer)
    end

    def write_text_frame(str)
      @j_del.writeTextFrame(str)
    end

    def close
      @j_del.close
    end

  end
end