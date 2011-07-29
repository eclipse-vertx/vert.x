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

module Net

  class Server

    def Server.create_server(proc = nil, &connect_hdlr)
      connect_hdlr = proc if proc
      Server.new(connect_hdlr)
    end

    def initialize(connect_hdlr)
      @j_server = org.nodex.core.net.NetServer.createServer { |j_socket|
        sock = Socket.new(j_socket)
        connect_hdlr.call(sock)
      }
    end

    def listen(port, host = "0.0.0.0")
      @j_server.listen(port, host)
      self
    end

    def close
      @j_server.close
    end

    private :initialize
  end

  class Client

    def Client.create_client
      Client.new
    end

    def connect(port, host = "localhost", proc = nil, &connect_hdlr)
      connect_hdlr = proc if proc
      @j_client.connect(port, host) { |j_socket|
        sock = Socket.new(j_socket)
        connect_hdlr.call(sock)
      }
    end

    def initialize
      @j_client = org.nodex.core.net.NetClient.createClient;
    end

    private :initialize
  end

  class Socket

    def initialize(j_socket)
      @j_socket = j_socket
    end

    def write(data)
      @j_socket.write(data._to_java_buffer)
    end

    def data(proc = nil, &data_hdlr)
      data_hdlr = proc if proc
      @j_socket.data{ |j_buff|
        buf = Buffer.new(j_buff)
        data_hdlr.call(buf)
      }
    end
  end
end

