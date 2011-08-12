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
require "buffer"

module Http
  class HttpServer

    def HttpServer.create_server(proc = nil, &connect_block)
      connect_block = proc if proc
      HttpServer.new(connect_block)
    end

    class ConnectCallback < org.nodex.core.http.HttpConnectHandler

      def initialize(connect_block)
        super()
        @connect_block = connect_block
      end

      def onConnect(java_connection)
        conn = Connection.new(java_connection)
        @connect_block.call(conn)
      end
    end

    def initialize(connect_block)
      @connect_block = connect_block
      @java_server = org.nodex.core.http.HttpServer.createServer(ConnectCallback.new(@connect_block))
    end

    def listen(port, host = "0.0.0.0")
      @java_server.listen(port, host)
    end

    def stop
      @java_server.stop
    end

    private :initialize

  end

  class Connection

    class RequestCallback < org.nodex.core.http.HttpCallback
      def initialize(request_block)
        super()
        @request_block = request_block
      end

      def onRequest(req, resp)
        @request_block.call(Request.new(req), Response.new(resp))
      end

      private :initialize
    end

    def initialize(java_connection)
      super()
      @java_conn = java_connection
    end

    def request(proc = nil, &request_callback)
      request_callback = proc if proc
      @java_conn.request(RequestCallback.new(request_callback))
    end

    protected :initialize

  end

  class Request

    attr_reader :method, :uri, :headers

    class DataCallback < org.nodex.core.buffer.DataHandler
      def initialize(data_block)
        super()
        @data_block = data_block
      end

      def onData(buffer)
        @data_block.call(Buffer.new(buffer))
      end

      private :initialize
    end

    def initialize(java_req)
      @java_req = java_req
      @method = java_req.method
      @uri = java_req.uri
      @headers = java_req.headers
    end

    def data(proc = nil, &data_block)
      data_block = proc if proc
      @java_req.data(DataCallback.new(data_block))
    end

    def get_param(param)
      @java_req.getParam(param)
    end

    protected :initialize

  end

  class Response
    attr_reader :headers
    attr_accessor :status_code

    def initialize(java_resp)
      @java_resp = java_resp
      @headers = java_resp.headers
    end

    def write_str(str, enc)
      @java_resp.write(str, enc)
      self
    end

    def write_buffer(buffer)
      @java_resp.write(buffer._to_java_buffer)
      self
    end

    def end
      @java_resp.end
    end

    protected :initialize

  end
end