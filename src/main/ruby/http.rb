include Java
require "buffer"

module Http
  class Server

    def Server.create_server(proc = nil, &connect_block)
      connect_block = proc if proc
      Server.new(connect_block)
    end

    class ConnectCallback < org.nodex.core.Callback

      def initialize(connect_block)
        super()
        @connect_block = connect_block
      end

      def onEvent(java_connection)
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

    class DataCallback < org.nodex.core.Callback
      def initialize(data_block)
        super()
        @data_block = data_block
      end

      def onEvent(buffer)
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